package com.structurizereplacements.event;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.network.Network;
import com.structurizereplacements.placement.ServerPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutionReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus event handlers. Registers the datapack-driven substitution loader so its rules reload
 * with the rest of the server's datapacks (including on {@code /reload}), and pushes the loaded
 * ruleset to clients so remote preview/GUI work on dedicated servers.
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

    /**
     * Push the active substitution ruleset to clients — fires per player on join and for all players after
     * {@code /reload} (the same triggers vanilla uses for tags/recipes, and conveniently <i>after</i> our
     * reload listener has applied, so the snapshot is always current). Without this, a dedicated server's
     * clients have no rules and the preview/GUI silently do nothing.
     */
    @SubscribeEvent
    public static void onDatapackSync(final OnDatapackSyncEvent event)
    {
        Network.sendRulesTo(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        ServerPlacementChoices.clear(event.getEntity().getUUID());
    }
}
