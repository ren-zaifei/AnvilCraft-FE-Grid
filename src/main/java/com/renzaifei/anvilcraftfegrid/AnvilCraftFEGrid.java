package com.renzaifei.anvilcraftfegrid;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.renzaifei.anvilcraftfegrid.config.AnvilCraftFEGridConfig;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import net.neoforged.fml.common.Mod;

@Mod(AnvilCraftFEGrid.MOD_ID)
public class AnvilCraftFEGrid {
    public static final String MOD_ID = "anvilcraftfegrid";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final AnvilCraftFEGridConfig CONFIG =
        ConfigManager.register(AnvilCraftFEGrid.MOD_ID, AnvilCraftFEGridConfig::new);


}
