package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.ServerPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Client → server: the player's current replacement choices, as block-id pairs. The server resolves
 * them to blocks and stores them per-player in {@link ServerPlacementChoices}.
 */
public class SyncReplacementChoicesMessage implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<SyncReplacementChoicesMessage> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StructurizeReplacements.MODID, "replacement_choices"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncReplacementChoicesMessage> STREAM_CODEC =
            StreamCodec.ofMember(SyncReplacementChoicesMessage::encode, SyncReplacementChoicesMessage::new);

    /** Decode cap — a blueprint holds at most a few hundred distinct blocks; reject anything absurd. */
    private static final int MAX_CHOICES = 4096;

    private final Map<ResourceLocation, ResourceLocation> choices;

    public SyncReplacementChoicesMessage(final Map<Block, Block> blockChoices)
    {
        this.choices = new HashMap<>();
        blockChoices.forEach((from, to) -> {
            final ResourceLocation fromId = BuiltInRegistries.BLOCK.getKey(from);
            final ResourceLocation toId = BuiltInRegistries.BLOCK.getKey(to);
            if (fromId != null && toId != null)
            {
                this.choices.put(fromId, toId);
            }
        });
    }

    public SyncReplacementChoicesMessage(final RegistryFriendlyByteBuf buf)
    {
        final int size = buf.readVarInt();
        if (size < 0 || size > MAX_CHOICES)
        {
            throw new DecoderException("Replacement-choices count " + size + " out of range (max " + MAX_CHOICES + ")");
        }
        this.choices = new HashMap<>(size);
        for (int i = 0; i < size; i++)
        {
            this.choices.put(buf.readResourceLocation(), buf.readResourceLocation());
        }
    }

    public void encode(final RegistryFriendlyByteBuf buf)
    {
        buf.writeVarInt(choices.size());
        choices.forEach((from, to) -> {
            buf.writeResourceLocation(from);
            buf.writeResourceLocation(to);
        });
    }

    @Override
    public CustomPacketPayload.Type<SyncReplacementChoicesMessage> type()
    {
        return TYPE;
    }

    /**
     * NeoForge hands the handler a context instead of a {@code Supplier<NetworkEvent.Context>}, and there is
     * no {@code setPacketHandled} — a payload that returns normally counts as handled. {@code context.player()}
     * is the sender on a server-bound payload (the registrar already guarantees the direction).
     */
    public void handle(final IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender))
            {
                return;
            }
            final Map<Block, Block> resolved = new HashMap<>();
            choices.forEach((fromId, toId) -> {
                final Block from = block(fromId);
                final Block to = block(toId);
                // Validate against the server's candidate pools — drop any pick a modified client forged outside
                // what the GUI could offer, so it can't substitute an arbitrary block.
                if (from != null && to != null && BlockSubstitutions.isAllowedChoice(from, to))
                {
                    resolved.put(from, to);
                }
            });
            ServerPlacementChoices.set(sender.getUUID(), resolved);
        });
    }

    private static Block block(final ResourceLocation id)
    {
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }
}
