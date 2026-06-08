package com.mctfc;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.AddType;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.BlockGrassPathPlacementHandler;
import com.mctfc.block.MortaredCobbleRegistry;
import com.mctfc.data.AfcDataPack;
import com.mctfc.data.BeneathDataPack;
import com.mctfc.data.FirmaLifeDataPack;
import com.mctfc.data.MortaredCobbleData;
import com.mctfc.food.FoodPreservation;
import com.mctfc.network.McFarmingNetwork;
import com.mctfc.placement.TfcSoilPlacementHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Compatibility bridge between MineColonies and TerraFirmaCraft (Forge 1.20.1).
 *
 * <p>The Structurize block-substitution engine <i>and</i> its (optional) MineColonies builder/Build-Options
 * integration both live in the standalone {@code structurizereplacements} mod (a dependency). This mod
 * supplies TFC-specific substitution rules as a datapack ({@code data/mctfc/block_substitutions/}) and will
 * house the rest of the MineColonies&lt;-&gt;TFC bridging (food/nutrition, farming, smithing, …) — including
 * its own mixins, which is why the MixinGradle setup and {@code mctfc.mixins.json} are kept.
 */
@Mod(MineColoniesTFC.MODID)
public class MineColoniesTFC
{
    public static final String MODID = "mctfc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineColoniesTFC(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        final IEventBus modBus = context.getModEventBus();
        // Scan the block registry and register a non-falling twin for every cobble block (so TFC's
        // collapsing cobble doesn't wreck MineColonies builds). Substitution targets these via the
        // mctfc:mortared_cobblestone tag.
        modBus.addListener(MortaredCobbleRegistry::onRegister);
        // Creative tab holding the mortared-cobble twins. Beyond being grabbable, this is what makes them
        // discoverable by MineColonies (its item pickers — incl. the miner fill-block setting — only see
        // items that appear in some creative tab).
        MctfcCreativeTab.TABS.register(modBus);
        // Runtime data pack: the mctfc:mortared_cobblestone tag (every twin) + a mortar recipe per twin.
        modBus.addListener(MortaredCobbleData::onAddPackFinders);
        // Optional built-in datapack: enabled only when the 'beneath' mod is present (Beneath-specific rules).
        modBus.addListener(BeneathDataPack::onAddPackFinders);
        // Optional built-in datapack: enabled only when ArborFirmaCraft ('afc') is present — AFC wood overrides
        // (priority 1, so they beat the base TFC wood mapping) + AFC woods joining the candidate pools.
        modBus.addListener(AfcDataPack::onAddPackFinders);
        // Optional built-in datapack: enabled only when FirmaLife ('firmalife') is present — FirmaLife carved/lit
        // pumpkin (jack o'lantern) variants (priority 1).
        modBus.addListener(FirmaLifeDataPack::onAddPackFinders);
        // Register the colony-storage food-preservation trait (TFC food decays slower in colony-owned racks).
        modBus.addListener(FoodPreservation::onCommonSetup);
        // Network channel for the farming bridge (per-field harvest-mode toggle from the field GUI).
        McFarmingNetwork.register();
        // Let the builder place substituted TFC grass / grass-path by requesting the matching TFC dirt
        // (suppliable) instead of the grass/path itself — mirroring vanilla's grass/path handling. Registered
        // before Structurize's BlockGrassPathPlacementHandler, which would otherwise place a vanilla path for
        // TFC's PathBlock (a DirtPathBlock subclass).
        PlacementHandlers.add(new TfcSoilPlacementHandler(), BlockGrassPathPlacementHandler.class, AddType.BEFORE);
        LOGGER.info("MineColonies x TerraFirmaCraft bridge loaded.");
    }
}
