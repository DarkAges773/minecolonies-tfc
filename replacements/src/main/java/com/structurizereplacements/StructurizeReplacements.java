package com.structurizereplacements;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.AddType;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.GeneralBlockPlacementHandler;
import com.mojang.logging.LogUtils;
import com.structurizereplacements.data.DefaultRulesDataPack;
import com.structurizereplacements.integration.minecolonies.MineColoniesBridge;
import com.structurizereplacements.integration.slimcolonies.SlimColoniesBridge;
import com.structurizereplacements.network.Network;
import com.structurizereplacements.placement.TwoTallPlantPlacementHandler;
import net.minecraftforge.eventbus.api.IEventBus;
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
        final IEventBus modBus = context.getModEventBus();
        // Optional, opt-in built-in datapack of ready-made candidate-pool rules (wood/wool/terracotta/glass/
        // concrete/beds/flowers). Disabled by default (required=false) so the published library stays inert;
        // a player running the mod standalone can enable it in the world's Data Packs screen for an instant
        // "Replace" picker. See DefaultRulesDataPack.
        modBus.addListener(DefaultRulesDataPack::onAddPackFinders);
        Network.register();
        // Place two-tall plants (TFC tall flowers etc.) atomically — both halves in one call, like vanilla's
        // DoublePlantPlacementHandler — so the builder's per-tick placement doesn't leave a self-destructing
        // lone half. Inserted before the catch-all GeneralBlockPlacementHandler. (Structurize is always present.)
        PlacementHandlers.add(new TwoTallPlantPlacementHandler(), GeneralBlockPlacementHandler.class, AddType.BEFORE);
        // Optional colony-mod integration: the shared logic lives in integration.colony; each fork
        // contributes only a ColonyBridge (the sole non-mixin class touching that fork's types), so nothing
        // fork-referencing is classloaded in the standalone case. else-if: the integration is single-slot,
        // and the two forks can't coexist at runtime anyway — if both are somehow present, MineColonies wins.
        if (ModList.get().isLoaded("minecolonies"))
        {
            MineColoniesBridge.init();
            LOGGER.info("Structurize Replacements: MineColonies integration enabled.");
        }
        else if (ModList.get().isLoaded("slimcolonies"))
        {
            SlimColoniesBridge.init();
            LOGGER.info("Structurize Replacements: SlimColonies integration enabled.");
        }
        LOGGER.info("Structurize Replacements loaded.");
    }
}
