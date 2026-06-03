package com.structurizereplacements.placement;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Buffer (de)serialization for a replacement choice map (source block → target block), shared by
 * integrations that sync or send choice maps (e.g. the MineColonies building-view sync and the
 * per-building edit packet).
 *
 * <p>Self-describing: always writes a count first (0 when empty), so read/write stay symmetric — needed
 * when appended to a buffer another mod also writes. Unknown block ids are skipped on read but their
 * bytes are always consumed, keeping the buffer aligned. MC-free.
 */
public final class ChoiceCodec
{
    private ChoiceCodec() {}

    public static void write(final FriendlyByteBuf buf, final Map<Block, Block> choices)
    {
        final Map<ResourceLocation, ResourceLocation> ids = new HashMap<>();
        if (choices != null)
        {
            choices.forEach((from, to) -> {
                final ResourceLocation f = ForgeRegistries.BLOCKS.getKey(from);
                final ResourceLocation t = ForgeRegistries.BLOCKS.getKey(to);
                if (f != null && t != null)
                {
                    ids.put(f, t);
                }
            });
        }
        buf.writeVarInt(ids.size());
        ids.forEach((f, t) -> {
            buf.writeResourceLocation(f);
            buf.writeResourceLocation(t);
        });
    }

    public static Map<Block, Block> read(final FriendlyByteBuf buf)
    {
        final int count = buf.readVarInt();
        final Map<Block, Block> map = new HashMap<>();
        for (int i = 0; i < count; i++)
        {
            final ResourceLocation fromId = buf.readResourceLocation();
            final ResourceLocation toId = buf.readResourceLocation();
            final Block from = ForgeRegistries.BLOCKS.containsKey(fromId) ? ForgeRegistries.BLOCKS.getValue(fromId) : null;
            final Block to = ForgeRegistries.BLOCKS.containsKey(toId) ? ForgeRegistries.BLOCKS.getValue(toId) : null;
            if (from != null && to != null)
            {
                map.put(from, to);
            }
        }
        return map;
    }
}
