package com.mctfc;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.AddType;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.BlockGrassPathPlacementHandler;
import com.mctfc.cook.CookBehavior;
import com.mctfc.furnace.FurnaceBehaviors;
import com.mctfc.settings.BeeFrameSetting;
import com.mctfc.settings.BeeFrameSettingFactory;
import com.mctfc.settings.BuildingSettings;
import com.mctfc.settings.BuildingStockSeeds;
import com.mctfc.smelter.SmelterBehavior;
import com.mctfc.smelter.SmelterRecipes;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSmeltery;
import com.minecolonies.core.entity.ai.workers.crafting.EntityAIWorkSmelter;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import com.mctfc.data.AfcDataPack;
import com.mctfc.data.BeneathDataPack;
import com.mctfc.data.FirmaLifeDataPack;
import com.mctfc.food.FoodPreservation;
import com.mctfc.forge.HeatForgeBlocks;
import com.mctfc.herding.TfcHerd;
import com.mctfc.inventory.ModMenus;
import com.mctfc.network.ComposeDishNetwork;
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
        // The TFC heat-forge multiblock block + its block entity (the growing furnace replacement — see
        // docs/tfc-forge-multiblock.md). Registered here; discovery/substitution/tending land in later slices.
        HeatForgeBlocks.init(modBus);
        // The bloomery wand (mctfc:bloomery_scepter) — taken from the Smeltery GUI to mark player-built TFC bloomeries
        // for the Smelter to tend for iron (docs/tfc-bloomery-smelter.md). Marking/module/AI land in the same feature.
        com.mctfc.item.ModItems.init(modBus);
        // TFC furnace workers now run on the heat-forge multiblock (docs/tfc-forge-multiblock.md): the
        // furnace→heat_forge substitution swaps blueprint furnaces to forges, which self-process; the behaviors
        // below are pure tend loops installed per-AI by MixinAbstractEntityAIUsesFurnace. (The Chef, a request
        // crafter the dispatcher can't reach, is driven directly by MixinAbstractEntityAIRequestSmelter.)
        // TFC smelter: melt ore into molds (cast metals) on the hut's forges; other furnace workers stay vanilla.
        FurnaceBehaviors.register(EntityAIWorkSmelter.class, SmelterBehavior::new);
        // TFC cook (dining hall): cook raw TFC food → cooked (decay preserved, gated to the restaurant menu) on the
        // hut's forges. Its food-serving duties are left untouched.
        FurnaceBehaviors.register(EntityAIWorkCook.class, CookBehavior::new);
        // Add our per-hut settings to the relevant buildings' Settings tab (MixinAbstractBuildingModule grafts
        // these onto each building's SettingsModule as it's built). Smeltery: the ore-restock low-water threshold.
        BuildingSettings.register(b -> b instanceof BuildingSmeltery, SmelterBehavior.ORE_THRESHOLD,
          () -> new IntSetting(SmelterBehavior.ORE_THRESHOLD_DEFAULT));
        // Seed a default minimum-stock of 1 stack of ingot molds on a fresh Smeltery (molds stack to 16), so it
        // keeps molds for casting out of the box. MixinAbstractBuilding applies this at first build (level 1).
        BuildingStockSeeds.register(b -> b instanceof BuildingSmeltery, SmelterRecipes::defaultMoldStack, 1);
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
        // Register herder interaction validators (e.g. the "more than one species in this pen" worker warning).
        modBus.addListener(TfcHerd::onCommonSetup);
        // Register the wax-frame setting's (de)serialization factory so a Beekeeper carrying it saves/syncs.
        // Unconditional (the setting names no FirmaLife type) so a world that loses FirmaLife can still load.
        modBus.addListener(MineColoniesTFC::onCommonSetup);
        // FirmaLife Beekeeper: give the hut a "wax frames" setting (which of the 4 hive frame slots the worker
        // scrapes for beeswax). FirmaLife-gated — base TFC has no beekeeping, so a non-FirmaLife world omits it.
        if (net.minecraftforge.fml.ModList.get().isLoaded("firmalife"))
        {
            BuildingSettings.register(b -> b instanceof BuildingBeekeeper, BeeFrameSetting.KEY, () -> new BeeFrameSetting(0));
        }
        // Network channel for the farming bridge (per-field harvest-mode toggle from the field GUI).
        McFarmingNetwork.register();
        // The Chef dish-teaching menu (compose TFC salads/soups) + its open/teach network channel. The screen is
        // bound to the menu type client-side in ClientSetup (FMLClientSetupEvent).
        ModMenus.register(modBus);
        ComposeDishNetwork.register();
        // Let the builder place substituted TFC grass / grass-path by requesting the matching TFC dirt
        // (suppliable) instead of the grass/path itself — mirroring vanilla's grass/path handling. Registered
        // before Structurize's BlockGrassPathPlacementHandler, which would otherwise place a vanilla path for
        // TFC's PathBlock (a DirtPathBlock subclass).
        PlacementHandlers.add(new TfcSoilPlacementHandler(), BlockGrassPathPlacementHandler.class, AddType.BEFORE);
        LOGGER.info("MineColonies x TerraFirmaCraft bridge loaded.");
    }

    private static void onCommonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)
    {
        StandardFactoryController.getInstance().registerNewFactory(new BeeFrameSettingFactory());
        // The "marked bloomery is built wrong" Smelter warning — auto-clears once every marked bloomery is a formed
        // multiblock again (mirrors the herder's mixed-species validator). Triggered from the building's colony tick.
        event.enqueueWork(() -> com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry.registerStandardPredicate(
          net.minecraft.network.chat.Component.translatable(com.mctfc.bloomery.BloomeryUserModule.MALFORMED_INTERACTION),
          citizen -> com.mctfc.bloomery.BloomeryUserModule.hasMalformedMarked(citizen.getWorkBuilding())));
    }
}
