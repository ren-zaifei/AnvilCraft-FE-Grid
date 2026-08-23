package com.renzaifei.anvilcraftfegrid.config;

import java.util.ArrayList;
import java.util.List;

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

    @Comment("Maximum grid load (kW) a single FE machine may draw")
    @BoundedDiscrete(min = 1, max = 100000)
    public int maxLoadPerMachine = 1024;

    @Comment("Feed FE machines even while the grid is overloaded (generation < consumption)")
    public boolean feedWhenOverloaded = false;

    @Comment("Blocks that are never treated as FE machines, e.g. cables and conduits."
        + " Accepts block ids and namespace wildcards such as \"mekanism:*\"")
    public List<String> blockedBlocks = new ArrayList<>(List.of(
        "mekanism:basic_universal_cable",
        "mekanism:advanced_universal_cable",
        "mekanism:elite_universal_cable",
        "mekanism:ultimate_universal_cable"
    ));
}
