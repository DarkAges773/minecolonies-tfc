package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;

/**
 * Mod network channel. Carries the player's per-placement replacement choices from client to server
 * (so the server placement / {@code handleBlockPlacement} can apply them), and the active substitution
 * ruleset from server to client (so remote clients' preview/GUI work — rules load server-side only).
 *
 * <p>NeoForge replaced Forge's {@code SimpleChannel} with a payload registrar: each message is a
 * {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload} carrying its own {@code TYPE}
 * (the channel id) and {@code STREAM_CODEC}, registered here per direction. The direction is enforced by
 * the registrar — {@code playToServer}/{@code playToClient} — which is the discipline the old
 * {@code NetworkDirection} argument gave us. {@code versioned} keeps the old protocol-version handshake:
 * a client and server on different revisions refuse to connect rather than mis-decode.
 */
public final class Network
{
    private Network() {}

    private static final String PROTOCOL = "1";

    /** Called from the mod ctor: on NeoForge, payload registration is a mod-bus event, not an immediate call. */
    public static void register(final IEventBus modBus)
    {
        modBus.addListener(Network::onRegisterPayloads);
    }

    private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar(StructurizeReplacements.MODID).versioned(PROTOCOL);
        registrar.playToServer(SyncReplacementChoicesMessage.TYPE, SyncReplacementChoicesMessage.STREAM_CODEC,
                SyncReplacementChoicesMessage::handle);
        registrar.playToClient(SyncSubstitutionRulesMessage.TYPE, SyncSubstitutionRulesMessage.STREAM_CODEC,
                SyncSubstitutionRulesMessage::handle);
        registrar.playToClient(SyncBuiltinPresetsMessage.TYPE, SyncBuiltinPresetsMessage.STREAM_CODEC,
                SyncBuiltinPresetsMessage::handle);
    }

    /** Server → client: push the active ruleset snapshot to one player (join) or, when null, to everyone (reload). */
    public static void sendRulesTo(final ServerPlayer player)
    {
        final SyncSubstitutionRulesMessage message =
                new SyncSubstitutionRulesMessage(BlockSubstitutions.rules(), BlockSubstitutions.candidates());
        if (player != null)
        {
            PacketDistributor.sendToPlayer(player, message);
        }
        else
        {
            PacketDistributor.sendToAllPlayers(message);
        }
    }

    /** Server → client: push the built-in presets to one player (join) or, when null, to everyone (reload). */
    public static void sendPresetsTo(final ServerPlayer player)
    {
        final SyncBuiltinPresetsMessage message = new SyncBuiltinPresetsMessage(BuiltinPresets.all());
        if (player != null)
        {
            PacketDistributor.sendToPlayer(player, message);
        }
        else
        {
            PacketDistributor.sendToAllPlayers(message);
        }
    }

    /** Client → server: send the current choice map (source block → chosen target). */
    public static void sendChoicesToServer(final Map<Block, Block> choices)
    {
        PacketDistributor.sendToServer(new SyncReplacementChoicesMessage(choices));
    }
}
