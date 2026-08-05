package com.structurizereplacements;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Common config. Master switch for the substitution feature.
 */
@EventBusSubscriber(modid = StructurizeReplacements.MODID)
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_SUBSTITUTION = BUILDER
            .comment("Enable block substitution when placing Structurize blueprints.")
            .define("enableSubstitution", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    // volatile: written on the config-load thread, read from server/render/netty threads.
    public static volatile boolean enableSubstitution;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        enableSubstitution = ENABLE_SUBSTITUTION.get();
    }
}
