package com.renzaifei.anvilcraftfegrid.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.renzaifei.anvilcraftfegrid.AnvilCraftFEGrid;
import com.renzaifei.anvilcraftfegrid.config.AnvilCraftFEGridConfig;

import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.block.entity.FeCollectorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 电网桥接工具类
 */
public final class FEGridBridge {
    /** 扫描间隔（游戏刻） */
    private static final int RESCAN_INTERVAL = 100;
    /** 无上限时的限流，避免溢出 */
    private static final int PROBE_LIMIT = Integer.MAX_VALUE / 2;
    /** 探测顺序 */
    private static final Direction[] SIDES = {
        null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
    };

    private FEGridBridge() {
    }

    /**
     * 计算本刻电网范围内所有FE元件的用电量。
     *
     * <p>同一游戏刻内重复调用时会复用结果
     *
     * @param grid  电网
     * @param state 桥接状态
     * @return 需求功率
     */
    public static int computeDemand(PowerGrid grid, FEGridState state) {
        Level level = grid.getLevel();
        if (level.isClientSide()) return 0;
        // 复用逻辑
        long time = level.getGameTime();
        if (state.lastDemandTick == time) return state.demand;
        state.lastDemandTick = time;

        AnvilCraftFEGridConfig config = AnvilCraftFEGrid.CONFIG;
        double fePerKw = fePerKw(config);
        // 扫描FE元件
        if (state.targets == null || time - state.lastScanTick >= RESCAN_INTERVAL) {
            state.targets = scanTargets(grid, level);
            state.lastScanTick = time;
        }
        int probe = config.maxLoadPerMachine > 0 ? kwToFe(config.maxLoadPerMachine, fePerKw) : PROBE_LIMIT;
        state.wants.clear();
        int total = 0;
        // 用电量计算与遍历
        for (FETarget target : state.targets) {
            IEnergyStorage storage = target.resolve(level);
            if (storage == null) continue;
            int room = storage.receiveEnergy(probe, true);
            if (room <= 0) continue;
            int kw = feToKw(room, fePerKw);
            if (config.maxLoadPerMachine > 0) kw = Math.min(kw, config.maxLoadPerMachine);
            if (kw <= 0) continue;
            state.wants.add(new FEGridState.Want(target, kw));
            total += kw;
        }
        state.demand = total;
        if (state.lastReportedDemand != total) {
            state.lastReportedDemand = total;
            grid.markChanged();
        }
        return total;
    }

    /**
     * 把电网电力转换成FE输送给各目标。
     *
     * @param grid  电网
     * @param state 桥接状态
     */
    public static void distribute(PowerGrid grid, FEGridState state) {
        if (grid.isMarkedRemoval()) return;
        if (state.demand <= 0 || state.wants.isEmpty()) return;
        Level level = grid.getLevel();
        if (level.isClientSide()) return;
        AnvilCraftFEGridConfig config = AnvilCraftFEGrid.CONFIG;
        int budget;
        if (grid.isHasInfinitePower() || grid.isWorking()) {
            budget = state.demand;
        } else if (config.feedWhenOverloaded) {
            int headroom = grid.getGenerate() - (grid.getConsume() - state.demand);
            budget = Mth.clamp(headroom, 0, state.demand);
        } else {
            return;
        }
        if (budget <= 0) return;

        double fePerKw = fePerKw(config);
        int count = state.wants.size();
        int[] used = new int[count];
        int remaining = budget;
        // 首轮按用电比例分配
        for (int i = 0; i < count && remaining > 0; i++) {
            FEGridState.Want want = state.wants.get(i);
            int grant = (int) ((long) budget * want.kw() / state.demand);
            grant = Math.min(grant, Math.min(want.kw(), remaining));
            if (grant <= 0) continue;
            int spent = push(level, want.target(), grant, fePerKw);
            used[i] = spent;
            remaining -= spent;
        }
        // 次轮补充
        for (int i = 0; i < count && remaining > 0; i++) {
            FEGridState.Want want = state.wants.get(i);
            int grant = Math.min(want.kw() - used[i], remaining);
            if (grant <= 0) continue;
            remaining -= push(level, want.target(), grant, fePerKw);
        }
    }

    /**
     * 向单个元件输送电力。
     *
     * @return 实际消耗的电网功率（kW）
     */
    private static int push(Level level, FETarget target, int kw, double fePerKw) {
        IEnergyStorage storage = target.resolve(level);
        if (storage == null) return 0;
        int fe = kwToFe(kw, fePerKw);
        if (fe <= 0) return 0;
        int accepted = storage.receiveEnergy(fe, false);
        if (accepted <= 0) return 0;
        return Math.min(kw, feToKw(accepted, fePerKw));
    }

    /**
     * 扫描电网范围内所有可接收 FE 的方块实体。
     *
     * <p>只遍历已加载区块中已有的方块实体
     */
    private static List<FETarget> scanTargets(PowerGrid grid, Level level) {
        List<FETarget> targets = new ArrayList<>();
        AABB range = gridRange(grid);
        if (range == null) return targets;

        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(range.minX));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(range.maxX));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(range.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(range.maxZ));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (Map.Entry<BlockPos, BlockEntity> entry : List.copyOf(chunk.getBlockEntities().entrySet())) {
                    BlockPos pos = entry.getKey();
                    if (!grid.inRangeFast(pos.getCenter())) continue;
                    BlockEntity blockEntity = entry.getValue();
                    if (blockEntity.isRemoved()) continue;
                    if (blockEntity instanceof IPowerComponent) continue;
                    if (blockEntity instanceof FeCollectorBlockEntity) continue;
                    FETarget target = resolveTarget(level, pos);
                    if (target != null) targets.add(target);
                }
            }
        }
        return targets;
    }

    /**
     * 找出该位置第一个可接收能量的访问面。
     *
     * @return 目标，不可接收时为 null
     */
    @Nullable
    private static FETarget resolveTarget(Level level, BlockPos pos) {
        for (Direction side : SIDES) {
            FETarget target = new FETarget(pos.immutable(), side);
            if (target.resolve(level) != null) return target;
        }
        return null;
    }

    /**
     * 电网范围
     *
     * @return 电网为空时为 null
     */
    @Nullable
    public static AABB gridRange(PowerGrid grid) {
        Set<IPowerComponent> components = grid.getComponents();
        AABB range = null;
        synchronized (components) {
            for (IPowerComponent component : components) {
                AABB shape = component.getShape();
                range = range == null ? shape : range.minmax(shape);
            }
        }
        return range;
    }

    /** 1 kW 对应的 FE，已计入转换损耗 */
    private static double fePerKw(AnvilCraftFEGridConfig config) {
        double efficiency = 1.0 - Mth.clamp(config.loss, 0.0, 0.99);
        return Math.max(1, config.transducers) * efficiency;
    }

    /**
     * kW转换为FE
     */
    private static int kwToFe(int kw, double fePerKw) {
        return (int) Math.min(PROBE_LIMIT, Math.floor(kw * fePerKw));
    }

    /**
     * FE转换为kW
     */
    private static int feToKw(int fe, double fePerKw) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(fe / fePerKw));
    }
}
