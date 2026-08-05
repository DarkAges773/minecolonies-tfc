package com.structurizereplacements.event;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.network.Network;
import com.structurizereplacements.placement.ServerPlacementChoices;
import com.structurizereplacements.placement.StagedChoices;
import com.structurizereplacements.preset.BuiltinPresetReloadListener;
import com.structurizereplacements.substitution.BlockSubstitutionReloadListener;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
        event.addListener(new BuiltinPresetReloadListener());
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
        Network.sendPresetsTo(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        ServerPlacementChoices.clear(event.getEntity().getUUID());
    }

    /**
     * Registry tags rebind AFTER the reload-listener phase where {@code setRules} already cleared the memo
     * cache — a placement in between memoizes {@code from_tag} matches against the old tag contents. This
     * event fires on both sides (server rebind and client tag sync), closing that window everywhere.
     */
    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event)
    {
        BlockSubstitutions.clearCache();
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event)
    {
        StagedChoices.clear();
    }
}
