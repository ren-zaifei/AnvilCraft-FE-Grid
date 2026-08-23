package com.renzaifei.anvilcraftfegrid.config;

import com.renzaifei.anvilcraftfegrid.AnvilCraftFEGrid;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;

@Config(name = AnvilCraftFEGrid.MOD_ID)
public class AnvilCraftFEGridConfig {
    @Comment("1kW = ?FE")
    @BoundedDiscrete(min = 1, max = 10000)
    public int transducers = 1000;

    @Comment("Power loss when converting grid power into FE (0.0 = lossless, 0.1 = 10% lost)")
    public double loss = 0.05;

    @Comment("Maximum grid load (kW) a single FE machine may draw (0 = no limits)")
    @BoundedDiscrete(min = 0, max = 100000)
    public int maxLoadPerMachine = 128;

    @Comment("Feed FE machines even while the grid is overloaded (generation < consumption)")
    public boolean feedWhenOverloaded = false;
}
