package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Optional;

/**
 * Mod network channel. Carries the player's per-placement replacement choices from client to server
 * (so the server placement / {@code handleBlockPlacement} can apply them), and the active substitution
 * ruleset from server to client (so remote clients' preview/GUI work — rules load server-side only).
 */
public final class Network
{
    private Network() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructurizeReplacements.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncReplacementChoicesMessage.class,
                SyncReplacementChoicesMessage::encode,
                SyncReplacementChoicesMessage::new,
                SyncReplacementChoicesMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SyncSubstitutionRulesMessage.class,
                SyncSubstitutionRulesMessage::encode,
                SyncSubstitutionRulesMessage::new,
                SyncSubstitutionRulesMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SyncBuiltinPresetsMessage.class,
                SyncBuiltinPresetsMessage::encode,
                SyncBuiltinPresetsMessage::new,
                SyncBuiltinPresetsMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Server → client: push the active ruleset snapshot to one player (join) or, when null, to everyone (reload). */
    public static void sendRulesTo(final ServerPlayer player)
    {
        final SyncSubstitutionRulesMessage message =
                new SyncSubstitutionRulesMessage(BlockSubstitutions.rules(), BlockSubstitutions.candidates());
        if (player != null)
        {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
        else
        {
            CHANNEL.send(PacketDistributor.ALL.noArg(), message);
        }
    }

    /** Server → client: push the built-in presets to one player (join) or, when null, to everyone (reload). */
    public static void sendPresetsTo(final ServerPlayer player)
    {
        final SyncBuiltinPresetsMessage message = new SyncBuiltinPresetsMessage(BuiltinPresets.all());
        if (player != null)
        {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
        else
        {
            CHANNEL.send(PacketDistributor.ALL.noArg(), message);
        }
    }

    /** Client → server: send the current choice map (source block → chosen target). */
    public static void sendChoicesToServer(final Map<Block, Block> choices)
    {
        CHANNEL.sendToServer(new SyncReplacementChoicesMessage(choices));
    }
}
