package com.mctfc;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

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

    private static final ForgeConfigSpec.DoubleValue TFC_FOOD_SATURATION_MODIFIER = BUILDER
            .comment("Balance multiplier for the saturation TFC food gives MineColonies citizens (1.0 = 100%, the default). The bridged value (TFC hunger x quality) is scaled by this — raise it if TFC food feels too weak, lower it if too strong. Applied live (re-read on config reload).")
            .defineInRange("tfcFoodSaturationModifier", 1.0, 0.0, 10.0);

    private static final ForgeConfigSpec.BooleanValue KEEP_COLONY_LIGHTS_LIT = BUILDER
            .comment("Stop TFC light sources (metal lamps, torches, candles/candle cakes, jack-o'-lanterns) from burning out / running out of fuel while they are inside a MineColonies colony's claimed area, so colonies stay lit. Only freezes the burn-out of already-lit sources (it won't relight one that's gone out or fuel an unlit lamp); light outside any colony decays normally. Set false to let TFC light burnout apply everywhere.")
            .define("keepColonyLightsLit", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> FURNACE_FUEL_TEMP_BONUS_BY_LEVEL = BUILDER
            .comment("Extra effective fuel temperature (in C) added by building level for TFC furnace workers (e.g. the Smelter), one concrete entry per level: index 0 = level 1, index 1 = level 2, and so on. A fuel can run an operation only when its temperature plus this bonus reaches what the operation needs, so higher-level huts melt hotter metals with the same fuel. Default [0, 15, 30, 45, 60]: coal (1415C) reaches nickel (1453C) at level 4 (+45), charcoal (1350C) never does, and wood never reaches copper. A building level beyond the list uses the last entry; an empty list disables the bonus.")
            .defineList("furnaceFuelTempBonusByLevel", List.of(0, 15, 30, 45, 60), o -> o instanceof Integer i && i >= 0);

    private static final ForgeConfigSpec.DoubleValue DINING_HALL_STOCK_PER_CITIZEN = BUILDER
            .comment("How many of EACH menu food the dining hall (Restaurant) tries to keep in stock, per colony citizen. Vanilla MineColonies targets maxStackSize x building level (e.g. 160 of every dish), which makes the Chef bulk-cook far more perishable TFC food than the colony eats — so it rots. This scales the target to the number of eaters instead. The per-item target is clamped to [diningHallStockMin, diningHallStockMax]. Read live (config reload).")
            .defineInRange("diningHallStockPerCitizen", 0.5, 0.0, 16.0);

    private static final ForgeConfigSpec.IntValue DINING_HALL_STOCK_MIN = BUILDER
            .comment("Lower bound on the dining hall's per-menu-item food target, so even a tiny colony keeps a small buffer.")
            .defineInRange("diningHallStockMin", 4, 0, 1024);

    private static final ForgeConfigSpec.IntValue DINING_HALL_STOCK_MAX = BUILDER
            .comment("Upper bound on the dining hall's per-menu-item food target, so a large colony never hoards a rotting pile of any single dish.")
            .defineInRange("diningHallStockMax", 16, 1, 1024);

    private static final ForgeConfigSpec.IntValue DINING_HALL_WORKER_CARRY = BUILDER
            .comment("How much food the dining hall's Waiter pulls from storage into its own inventory per trip (vanilla grabs a full stack of 64). The Waiter's inventory is not colony storage, so it is NOT covered by the colony food-preservation trait — anything it over-carries decays in hand. Keep this modest.")
            .defineInRange("diningHallWorkerCarry", 16, 1, 64);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Fertilize once the crop's primary nutrient is below this (0..1). */
    public static float fertilizeBelow = 0.4f;

    /** Top up until the nutrient reaches at least this (0..1). */
    public static float fertilizeTarget = 0.9f;

    /** Block player interaction with vanilla furnace/smoker/blast furnace (TFC-smelting bypass fix). Set to false for the dev phase. */
    public static boolean decorativeVanillaFurnaces = false;

    /** Decay-rate multiplier for TFC food in colony-owned storage (0 = frozen, 1 = normal). Read live by the food trait. */
    public static float foodColonyStorageDecay = 0.25f;

    /** Balance multiplier for the saturation TFC food gives citizens (1.0 = 100%). Read live by the food-value bridge. */
    public static float tfcFoodSaturationModifier = 1.0f;

    /** Keep TFC light sources inside a colony from burning out / running out of fuel. Read live by the light mixins. */
    public static boolean keepColonyLightsLit = true;

    /** Effective-fuel-temperature bonus (C) per building level, index 0 = level 1. Read live by {@link com.mctfc.furnace.FurnaceFuel}. */
    public static List<Integer> furnaceFuelTempBonusByLevel = List.of(0, 15, 30, 45, 60);

    /** Dining hall per-menu-item food stock target, per colony citizen (clamped to [min,max]). Read by {@code MixinRestaurantMenuModule}. */
    public static double diningHallStockPerCitizen = 0.5;

    /** Lower bound on the dining hall's per-menu-item food target. */
    public static int diningHallStockMin = 4;

    /** Upper bound on the dining hall's per-menu-item food target. */
    public static int diningHallStockMax = 16;

    /** How much food the Waiter carries from storage per trip (vs vanilla's full stack). Read by {@code MixinEntityAIWorkCook}. */
    public static int diningHallWorkerCarry = 16;

    /**
     * The effective-temperature bonus for a furnace worker's building level (1-based), from
     * {@link #furnaceFuelTempBonusByLevel}; a level beyond the list uses the last entry, an empty list 0.
     */
    public static int furnaceFuelTempBonus(final int buildingLevel)
    {
        if (furnaceFuelTempBonusByLevel.isEmpty())
        {
            return 0;
        }
        final int index = Math.max(0, Math.min(buildingLevel - 1, furnaceFuelTempBonusByLevel.size() - 1));
        return furnaceFuelTempBonusByLevel.get(index);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        fertilizeBelow = FERTILIZE_BELOW.get().floatValue();
        fertilizeTarget = FERTILIZE_TARGET.get().floatValue();
        decorativeVanillaFurnaces = DECORATIVE_VANILLA_FURNACES.get();
        foodColonyStorageDecay = FOOD_COLONY_STORAGE_DECAY.get().floatValue();
        tfcFoodSaturationModifier = TFC_FOOD_SATURATION_MODIFIER.get().floatValue();
        keepColonyLightsLit = KEEP_COLONY_LIGHTS_LIT.get();
        furnaceFuelTempBonusByLevel = new ArrayList<>(FURNACE_FUEL_TEMP_BONUS_BY_LEVEL.get());
        diningHallStockPerCitizen = DINING_HALL_STOCK_PER_CITIZEN.get();
        diningHallStockMin = DINING_HALL_STOCK_MIN.get();
        diningHallStockMax = DINING_HALL_STOCK_MAX.get();
        diningHallWorkerCarry = DINING_HALL_WORKER_CARRY.get();
    }
}
