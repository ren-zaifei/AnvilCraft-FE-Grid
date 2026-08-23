package com.renzaifei.anvilcraftfegrid.power;

import java.util.ArrayList;
import java.util.HashSet;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 电网桥接工具类
 */
public final class FEGridBridge {
    /** 扫描间隔（游戏刻） */
    private static final int RESCAN_INTERVAL = 100;
    /** FE 数值上限，避免转换时溢出 */
    private static final int FE_LIMIT = Integer.MAX_VALUE / 2;
    /** 需求上限（kW），留出余量避免叠加进电网时溢出 */
    private static final int DEMAND_LIMIT = Integer.MAX_VALUE / 4;
    /**
     * 探测顺序
     */
    private static final Direction[] SIDES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null,
    };

    /** 屏蔽列表的配置来源，用于判断缓存是否过期 */
    @Nullable
    private static List<String> blockedSource;
    /** 屏蔽的方块 id */
    private static Set<String> blockedIds = Set.of();
    /** 屏蔽的命名空间 */
    private static Set<String> blockedNamespaces = Set.of();

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
        int loadPerMachine = loadPerMachine(config);
        int probe = Math.max(1, kwToFe(loadPerMachine, fePerKw));
        // 扫描FE元件
        if (state.targets == null || time - state.lastScanTick >= RESCAN_INTERVAL) {
            state.targets = scanTargets(grid, level, probe);
            state.lastScanTick = time;
        }
        state.wants.clear();
        long total = 0;
        // 用电量计算与遍历
        for (FETarget target : state.targets) {
            IEnergyStorage storage = target.resolve(level);
            if (storage == null) continue;
            int room = storage.receiveEnergy(probe, true);
            if (room <= 0) continue;
            int kw = Math.min(feToKw(room, fePerKw), loadPerMachine);
            if (kw <= 0) continue;
            state.wants.add(new FEGridState.Want(target, kw));
            total += kw;
        }
        int demand = (int) Math.min(total, DEMAND_LIMIT);
        state.demand = demand;
        if (state.lastReportedDemand != demand) {
            state.lastReportedDemand = demand;
            grid.markChanged();
        }
        return demand;
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
    private static List<FETarget> scanTargets(PowerGrid grid, Level level, int probe) {
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
                    if (isBlocked(blockEntity.getBlockState())) continue;
                    FETarget target = resolveTarget(level, pos, probe);
                    if (target != null) targets.add(target);
                }
            }
        }
        return targets;
    }

    /**
     * 找出该位置可接收能量的访问面
     * @param probe 模拟插入用的探测量（FE）
     * @return 目标，无可用接口时为 null
     */
    @Nullable
    private static FETarget resolveTarget(Level level, BlockPos pos, int probe) {
        FETarget fallback = null;
        for (Direction side : SIDES) {
            FETarget target = new FETarget(pos.immutable(), side);
            IEnergyStorage storage = target.resolve(level);
            if (storage == null) continue;
            if (storage.receiveEnergy(probe, true) > 0) return target;
            if (fallback == null) fallback = target;
        }
        return fallback;
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

    /**
     * 判断方块是否被配置屏蔽。
     *
     * <p>线缆、导管一类的方块会把整段管网的缓冲都算成用电需求，需要排除
     *
     * @param state 方块状态
     * @return 是否屏蔽
     */
    private static boolean isBlocked(BlockState state) {
        List<String> configured = AnvilCraftFEGrid.CONFIG.blockedBlocks;
        if (configured != blockedSource) rebuildBlocked(configured);
        if (blockedIds.isEmpty() && blockedNamespaces.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockedNamespaces.contains(id.getNamespace()) || blockedIds.contains(id.toString());
    }

    /**
     * 重建屏蔽列表缓存。
     *
     * @param configured 配置中的原始条目
     */
    private static void rebuildBlocked(List<String> configured) {
        Set<String> ids = new HashSet<>();
        Set<String> namespaces = new HashSet<>();
        for (String raw : configured) {
            if (raw == null) continue;
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.endsWith(":*")) {
                namespaces.add(entry.substring(0, entry.length() - 2));
            } else {
                ids.add(entry.indexOf(':') < 0 ? "minecraft:" + entry : entry);
            }
        }
        blockedIds = ids;
        blockedNamespaces = namespaces;
        blockedSource = configured;
    }

    /** 1 kW 对应的 FE，已计入转换损耗 */
    private static double fePerKw(AnvilCraftFEGridConfig config) {
        double efficiency = 1.0 - Mth.clamp(config.loss, 0.0, 0.99);
        return Math.max(1, config.transducers) * efficiency;
    }

    /** 单台元件每刻可从电网抽取的功率（kW） */
    private static int loadPerMachine(AnvilCraftFEGridConfig config) {
        return config.maxLoadPerMachine;
    }

    /**
     * kW转换为FE
     */
    private static int kwToFe(int kw, double fePerKw) {
        return (int) Math.min(FE_LIMIT, Math.floor(kw * fePerKw));
    }

    /**
     * FE转换为kW
     */
    private static int feToKw(int fe, double fePerKw) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(fe / fePerKw));
    }
}
