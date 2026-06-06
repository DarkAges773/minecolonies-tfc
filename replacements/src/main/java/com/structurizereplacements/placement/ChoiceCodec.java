package com.structurizereplacements.placement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    /**
     * Write a choice map under {@code key} as a list of {@code {from,to}} block-id entries. Removes the key
     * when the map is empty/null (so a cleared map doesn't leave a stale tag). Same on-disk shape used by
     * the MineColonies building NBT, so the two stores are interchangeable.
     */
    public static void writeNbt(final CompoundTag tag, final String key, final Map<Block, Block> choices)
    {
        if (choices == null || choices.isEmpty())
        {
            tag.remove(key);
            return;
        }
        final ListTag list = new ListTag();
        choices.forEach((from, to) -> {
            final ResourceLocation f = ForgeRegistries.BLOCKS.getKey(from);
            final ResourceLocation t = ForgeRegistries.BLOCKS.getKey(to);
            if (f != null && t != null)
            {
                final CompoundTag entry = new CompoundTag();
                entry.putString("from", f.toString());
                entry.putString("to", t.toString());
                list.add(entry);
            }
        });
        if (list.isEmpty())
        {
            tag.remove(key);
        }
        else
        {
            tag.put(key, list);
        }
    }

    /** Read a choice map written by {@link #writeNbt}; {@code null} when absent or empty. */
    public static Map<Block, Block> readNbt(final CompoundTag tag, final String key)
    {
        if (!tag.contains(key, Tag.TAG_LIST))
        {
            return null;
        }
        final Map<Block, Block> map = new HashMap<>();
        final ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            final ResourceLocation f = ResourceLocation.tryParse(entry.getString("from"));
            final ResourceLocation t = ResourceLocation.tryParse(entry.getString("to"));
            final Block from = (f != null && ForgeRegistries.BLOCKS.containsKey(f)) ? ForgeRegistries.BLOCKS.getValue(f) : null;
            final Block to = (t != null && ForgeRegistries.BLOCKS.containsKey(t)) ? ForgeRegistries.BLOCKS.getValue(t) : null;
            if (from != null && to != null)
            {
                map.put(from, to);
            }
        }
        return map.isEmpty() ? null : map;
    }
}
