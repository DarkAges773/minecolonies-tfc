package com.mctfc;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Common config for the MineColonies&times;TFC bridge. Currently the farmer's soil-fertilizing thresholds
 * (see {@link com.mctfc.farming.FertilizerHelper}).
 */
@Mod.EventBusSubscriber(modid = MineColoniesTFC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue FERTILIZE_BELOW = BUILDER
            .comment("The colony farmer tops up a field's crop-specific soil nutrient once it drops below this fraction (0..1).")
            .defineInRange("fertilizeBelow", 0.4, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FERTILIZE_TARGET = BUILDER
            .comment("When topping up, the farmer keeps applying fertilizer until the nutrient reaches at least this fraction (0..1).")
            .defineInRange("fertilizeTarget", 0.9, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue DECORATIVE_VANILLA_FURNACES = BUILDER
            .comment("Make the vanilla furnace, smoker and blast furnace decorative: block the player from opening their GUI so vanilla smelting/cooking can't bypass TFC's mechanics. The blocks stay placed (still breakable, and blocks can be placed against them while sneaking); MineColonies worker automation is unaffected. Set false to restore normal vanilla furnace use.")
            .define("decorativeVanillaFurnaces", true);

    private static final ForgeConfigSpec.DoubleValue FOOD_COLONY_STORAGE_DECAY = BUILDER
            .comment("Decay-rate multiplier for TFC food while it is stored in a colony-owned rack/warehouse (0.0 = frozen, 1.0 = normal speed). Player-placed racks are unaffected, and food reverts to normal decay once withdrawn. Applied live via the food trait (re-read on config reload).")
            .defineInRange("foodColonyStorageDecay", 0.25, 0.0, 1.0);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Fertilize once the crop's primary nutrient is below this (0..1). */
    public static float fertilizeBelow = 0.4f;

    /** Top up until the nutrient reaches at least this (0..1). */
    public static float fertilizeTarget = 0.9f;

    /** Block player interaction with vanilla furnace/smoker/blast furnace (TFC-smelting bypass fix). */
    public static boolean decorativeVanillaFurnaces = true;

    /** Decay-rate multiplier for TFC food in colony-owned storage (0 = frozen, 1 = normal). Read live by the food trait. */
    public static float foodColonyStorageDecay = 0.25f;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        fertilizeBelow = FERTILIZE_BELOW.get().floatValue();
        fertilizeTarget = FERTILIZE_TARGET.get().floatValue();
        decorativeVanillaFurnaces = DECORATIVE_VANILLA_FURNACES.get();
        foodColonyStorageDecay = FOOD_COLONY_STORAGE_DECAY.get().floatValue();
    }
}
