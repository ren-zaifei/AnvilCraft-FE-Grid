package com.renzaifei.anvilcraftfegrid.power;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * 单个电网的 FE 桥接状态。
 */
public final class FEGridState {
    /** 缓存目标 */
    @Nullable
    List<FETarget> targets;
    /** 上次扫描时的游戏刻 */
    long lastScanTick = Long.MIN_VALUE;
    /** 上次计算时的游戏刻 */
    long lastDemandTick = Long.MIN_VALUE;
    /** 本电力刻的FE需求，单位为kW */
    int demand;
    /** 上次的需求量，用于判断是否需要同步 */
    int lastReportedDemand = -1;
    /** 各目标本刻想要的功率（kW） */
    final List<Want> wants = new ArrayList<>();

    /**
     * 一个目标本刻的需求。
     *
     * @param target 目标
     * @param kw     需求功率
     */
    record Want(FETarget target, int kw) {
    }
}