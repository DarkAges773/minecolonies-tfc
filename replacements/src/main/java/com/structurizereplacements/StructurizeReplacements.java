package com.structurizereplacements;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.AddType;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.GeneralBlockPlacementHandler;
import com.mojang.logging.LogUtils;
import com.structurizereplacements.data.DefaultRulesDataPack;
import com.structurizereplacements.integration.minecolonies.MineColoniesBridge;
import com.structurizereplacements.network.Network;
import com.structurizereplacements.placement.TwoTallPlantPlacementHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

    /**
     * NeoForge injects the mod's event bus and container straight into the ctor (Forge handed you a
     * {@code FMLJavaModLoadingContext} to pull both off instead), and config registration moved onto the
     * {@link ModContainer}.
     */
    public StructurizeReplacements(final IEventBus modBus, final ModContainer container)
    {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Optional, opt-in built-in datapack of ready-made candidate-pool rules (wood/wool/terracotta/glass/
        // concrete/beds/flowers). Disabled by default (required=false) so the published library stays inert;
        // a player running the mod standalone can enable it in the world's Data Packs screen for an instant
        // "Replace" picker. See DefaultRulesDataPack.
        modBus.addListener(DefaultRulesDataPack::onAddPackFinders);
        Network.register(modBus);
        // Place two-tall plants (TFC tall flowers etc.) atomically — both halves in one call, like vanilla's
        // DoublePlantPlacementHandler — so the builder's per-tick placement doesn't leave a self-destructing
        // lone half. Inserted before the catch-all GeneralBlockPlacementHandler. (Structurize is always present.)
        PlacementHandlers.add(new TwoTallPlantPlacementHandler(), GeneralBlockPlacementHandler.class, AddType.BEFORE);
        // Optional colony-mod integration: the shared logic lives in integration.colony; the fork contributes
        // only a ColonyBridge (the sole non-mixin class touching that fork's types), so nothing fork-referencing
        // is classloaded in the standalone case.
        //
        // NOTE (1.21.1 branch): the SlimColonies arm that used to follow this one is PARKED — SlimColonies has
        // no 1.21.1 build, so its bridge/mixins are excluded from the source set (see replacements/build.gradle).
        // Restore the `else if (ModList.get().isLoaded("slimcolonies"))` branch together with those excludes.
        if (ModList.get().isLoaded("minecolonies"))
        {
            MineColoniesBridge.init(modBus);
            LOGGER.info("Structurize Replacements: MineColonies integration enabled.");
        }
        LOGGER.info("Structurize Replacements loaded.");
    }
}
