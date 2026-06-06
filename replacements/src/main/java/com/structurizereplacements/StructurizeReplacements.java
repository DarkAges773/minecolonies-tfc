package com.structurizereplacements;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.AddType;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.GeneralBlockPlacementHandler;
import com.mojang.logging.LogUtils;
import com.structurizereplacements.integration.minecolonies.MineColoniesIntegration;
import com.structurizereplacements.network.Network;
import com.structurizereplacements.placement.TwoTallPlantPlacementHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Standalone Structurize add-on: datapack-driven, tag- and family-based block substitution applied
 * while placing blueprints. Works with just Structurize; <b>MineColonies is an optional dependency</b> —
 * when present, the builder/Build-Options integration (per-building choices) activates. Any mod or
 * datapack can ship rules under {@code data/<namespace>/block_substitutions/}.
 */
@Mod(StructurizeReplacements.MODID)
public class StructurizeReplacements
{
    public static final String MODID = "structurizereplacements";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StructurizeReplacements(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Network.register();
        // Place two-tall plants (TFC tall flowers etc.) atomically — both halves in one call, like vanilla's
        // DoublePlantPlacementHandler — so the builder's per-tick placement doesn't leave a self-destructing
        // lone half. Inserted before the catch-all GeneralBlockPlacementHandler. (Structurize is always present.)
        PlacementHandlers.add(new TwoTallPlantPlacementHandler(), GeneralBlockPlacementHandler.class, AddType.BEFORE);
        // Optional MineColonies integration — touched only when MineColonies is loaded, so its
        // MineColonies-referencing classes are never classloaded in the standalone case.
        if (ModList.get().isLoaded("minecolonies"))
        {
            MineColoniesIntegration.init();
            LOGGER.info("Structurize Replacements: MineColonies integration enabled.");
        }
        LOGGER.info("Structurize Replacements loaded.");
    }
}
