package com.renzaifei.anvilcraftfegrid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.renzaifei.anvilcraftfegrid.power.FEGridBridge;
import com.renzaifei.anvilcraftfegrid.power.FEGridHolder;
import com.renzaifei.anvilcraftfegrid.power.FEGridState;

import dev.dubhe.anvilcraft.api.power.PowerGrid;

/**
 * 自动识别范围内的FE元件并将其纳入电网系统中
 */
@Mixin(value = PowerGrid.class, remap = false)
public abstract class PowerGridMixin implements FEGridHolder {
    @Shadow
    private int consume;

    @Unique
    private FEGridState anvilcraftfegrid$state;

    @Override
    public FEGridState anvilcraftfegrid$feState() {
        if (this.anvilcraftfegrid$state == null) {
            this.anvilcraftfegrid$state = new FEGridState();
        }
        return this.anvilcraftfegrid$state;
    }

    /**
     * 统计时添加FE元件
     */
    @Inject(method = "flush", at = @At("TAIL"))
    private void anvilcraftfegrid$appendDemand(CallbackInfoReturnable<Boolean> cir) {
        PowerGrid grid = (PowerGrid) (Object) this;
        int demand = FEGridBridge.computeDemand(grid, this.anvilcraftfegrid$feState());
        if (demand > 0) this.consume = (int) Math.min(Integer.MAX_VALUE, (long) this.consume + demand);
    }

    /**
     * 转换为FE进行输出。
     *
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void anvilcraftfegrid$distribute(CallbackInfo ci) {
        FEGridBridge.distribute((PowerGrid) (Object) this, this.anvilcraftfegrid$feState());
    }
}