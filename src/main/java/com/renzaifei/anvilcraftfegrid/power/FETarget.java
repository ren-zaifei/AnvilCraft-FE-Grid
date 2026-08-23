package com.renzaifei.anvilcraftfegrid.power;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 接收FE目标
 *
 * @param pos  方块位置
 * @param side 访问面，null 表示不限制面
 */
public record FETarget(BlockPos pos, @Nullable Direction side) {
    /**
     * 解析接收FE的接口
     *
     * @param level 所在世界
     * @return 不可用时为 null
     */
    @Nullable
    public IEnergyStorage resolve(Level level) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, this.pos, this.side);
        return storage != null && storage.canReceive() ? storage : null;
    }
}