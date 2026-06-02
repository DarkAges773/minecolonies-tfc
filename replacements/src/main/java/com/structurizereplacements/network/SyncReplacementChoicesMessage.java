package com.structurizereplacements.network;

import com.structurizereplacements.placement.ServerPlacementChoices;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → server: the player's current replacement choices, as block-id pairs. The server resolves
 * them to blocks and stores them per-player in {@link ServerPlacementChoices}.
 */
public class SyncReplacementChoicesMessage
{
    private final Map<ResourceLocation, ResourceLocation> choices;

    public SyncReplacementChoicesMessage(final Map<Block, Block> blockChoices)
    {
        this.choices = new HashMap<>();
        blockChoices.forEach((from, to) -> {
            final ResourceLocation fromId = ForgeRegistries.BLOCKS.getKey(from);
            final ResourceLocation toId = ForgeRegistries.BLOCKS.getKey(to);
            if (fromId != null && toId != null)
            {
                this.choices.put(fromId, toId);
            }
        });
    }

    public SyncReplacementChoicesMessage(final FriendlyByteBuf buf)
    {
        final int size = buf.readVarInt();
        this.choices = new HashMap<>(size);
        for (int i = 0; i < size; i++)
        {
            this.choices.put(buf.readResourceLocation(), buf.readResourceLocation());
        }
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeVarInt(choices.size());
        choices.forEach((from, to) -> {
            buf.writeResourceLocation(from);
            buf.writeResourceLocation(to);
        });
    }

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer sender = ctx.get().getSender();
            if (sender == null)
            {
                return;
            }
            final Map<Block, Block> resolved = new HashMap<>();
            choices.forEach((fromId, toId) -> {
                final Block from = block(fromId);
                final Block to = block(toId);
                if (from != null && to != null)
                {
                    resolved.put(from, to);
                }
            });
            ServerPlacementChoices.set(sender.getUUID(), resolved);
        });
        ctx.get().setPacketHandled(true);
    }

    private static Block block(final ResourceLocation id)
    {
        return ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
    }
}
