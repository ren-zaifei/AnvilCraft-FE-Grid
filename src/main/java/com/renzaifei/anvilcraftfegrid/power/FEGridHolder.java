package com.renzaifei.anvilcraftfegrid.power;

/**
 * 由 {@code PowerGrid} 通过 mixin 实现，持有该电网的 FE 桥接状态。
 */
public interface FEGridHolder {
    FEGridState anvilcraftfegrid$feState();
}