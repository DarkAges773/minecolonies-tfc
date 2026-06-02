package com.structurizereplacements.event;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.substitution.BlockSubstitutionReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus event handlers. Registers the datapack-driven substitution loader so its rules reload
 * with the rest of the server's datapacks (including on {@code /reload}).
 */
@Mod.EventBusSubscriber(modid = StructurizeReplacements.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModEvents
{
    private ModEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(final AddReloadListenerEvent event)
    {
        event.addListener(new BlockSubstitutionReloadListener());
    }
}
