package com.structurizereplacements.event;

import com.ldtteam.structurize.client.BlueprintHandler;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Client Forge-bus handlers. On disconnect, clears the per-session client state so nothing from one
 * server leaks into the next session:
 * <ul>
 *   <li><b>Substitution ruleset</b> — but only on a <i>remote</i> disconnect. In single-player the rule
 *       statics are shared with the integrated server in the same JVM, and the server's final ticks run
 *       <i>after</i> this event — clearing here would let a builder write unsubstituted blocks into the
 *       saved world. The next world/server repopulates them anyway (single-player via the local reload
 *       listener, dedicated via the join-time {@code SyncSubstitutionRulesMessage}).</li>
 *   <li><b>Placement choices</b> — always, and locally only (no sync packet; the connection is gone, and
 *       the server clears its per-player copy itself on logout).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = StructurizeReplacements.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents
{
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        final Connection connection = event.getConnection();
        if (connection == null || !connection.isMemoryConnection())
        {
            BlockSubstitutions.setRules(List.of(), List.of());
        }
        ClientPlacementChoices.clearLocal();
        // The hologram is baked in BlueprintRenderer#init with the choices/rules CURRENT AT BAKE TIME and
        // cached across sessions (the build tool remembers its preview). Without this re-bake, the preview
        // keeps showing the pre-disconnect substitutions that the just-cleared state no longer applies —
        // the exact desync the clears above are meant to prevent. Same invalidation the GUI uses per edit.
        BlueprintHandler.getInstance().clearCache();
    }
}
