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

    static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Fertilize once the crop's primary nutrient is below this (0..1). */
    public static float fertilizeBelow = 0.4f;

    /** Top up until the nutrient reaches at least this (0..1). */
    public static float fertilizeTarget = 0.9f;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        fertilizeBelow = FERTILIZE_BELOW.get().floatValue();
        fertilizeTarget = FERTILIZE_TARGET.get().floatValue();
    }
}
