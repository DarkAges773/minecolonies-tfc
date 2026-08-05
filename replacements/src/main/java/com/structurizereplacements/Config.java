package com.structurizereplacements;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Common config. Master switch for the substitution feature.
 */
@Mod.EventBusSubscriber(modid = StructurizeReplacements.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLE_SUBSTITUTION = BUILDER
            .comment("Enable block substitution when placing Structurize blueprints.")
            .define("enableSubstitution", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // volatile: written on the config-load thread, read from server/render/netty threads.
    public static volatile boolean enableSubstitution;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        enableSubstitution = ENABLE_SUBSTITUTION.get();
    }
}
