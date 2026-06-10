package com.structurizereplacements.event;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Client Forge-bus handlers. Clears the synced substitution ruleset on disconnect so rules from one server
 * never leak into the next session — the next world/server repopulates them (single-player via the local
 * reload listener, dedicated via the join-time {@code SyncSubstitutionRulesMessage}).
 */
@Mod.EventBusSubscriber(modid = StructurizeReplacements.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents
{
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        BlockSubstitutions.setRules(List.of(), List.of());
    }
}
