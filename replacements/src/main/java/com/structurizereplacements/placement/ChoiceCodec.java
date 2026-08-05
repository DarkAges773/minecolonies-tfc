package com.structurizereplacements.placement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

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

    /** NBT key for the per-building / work-order hut choice map. <b>Save format — do not change the value.</b> */
    public static final String CHOICES_KEY = "structurizereplacements_choices";

    /** NBT key for the miner's mineshaft choice map. <b>Save format — do not change the value.</b> */
    public static final String MINESHAFT_CHOICES_KEY = "structurizereplacements_mineshaft_choices";

    public static void write(final FriendlyByteBuf buf, final Map<Block, Block> choices)
    {
        final Map<ResourceLocation, ResourceLocation> ids = new HashMap<>();
        if (choices != null)
        {
            choices.forEach((from, to) -> {
                final ResourceLocation f = BuiltInRegistries.BLOCK.getKey(from);
                final ResourceLocation t = BuiltInRegistries.BLOCK.getKey(to);
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

    /**
     * Read a choice map. <b>Underflow-safe by design</b>: when used as a buffer <i>trailer</i> (the
     * MineColonies building-view sync appends it after MineColonies' own bytes), the writer and reader
     * mixins live in a {@code required:false} config and can apply independently — a MineColonies update
     * renaming only the writer's method would leave this reader running against a buffer that ends right
     * where our trailer should begin. Rather than read past the end and crash <i>every</i> building-view
     * sync (a client crash on colony load), degrade to whatever parsed cleanly: bail when the buffer is
     * already drained, and stop on any truncation/misalignment. Harmless for the well-formed edit-packet
     * path, which always carries the full count.
     */
    public static Map<Block, Block> read(final FriendlyByteBuf buf)
    {
        final Map<Block, Block> map = new HashMap<>();
        if (!buf.isReadable())
        {
            return map;
        }
        try
        {
            final int count = buf.readVarInt();
            for (int i = 0; i < count && buf.isReadable(); i++)
            {
                final ResourceLocation fromId = buf.readResourceLocation();
                final ResourceLocation toId = buf.readResourceLocation();
                final Block from = BuiltInRegistries.BLOCK.containsKey(fromId) ? BuiltInRegistries.BLOCK.get(fromId) : null;
                final Block to = BuiltInRegistries.BLOCK.containsKey(toId) ? BuiltInRegistries.BLOCK.get(toId) : null;
                if (from != null && to != null)
                {
                    map.put(from, to);
                }
            }
        }
        catch (final RuntimeException ex)
        {
            // Truncated/misaligned trailer (e.g. asymmetric mixin application) — keep what parsed cleanly.
            return map;
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
            final ResourceLocation f = BuiltInRegistries.BLOCK.getKey(from);
            final ResourceLocation t = BuiltInRegistries.BLOCK.getKey(to);
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
            final Block from = (f != null && BuiltInRegistries.BLOCK.containsKey(f)) ? BuiltInRegistries.BLOCK.get(f) : null;
            final Block to = (t != null && BuiltInRegistries.BLOCK.containsKey(t)) ? BuiltInRegistries.BLOCK.get(t) : null;
            if (from != null && to != null)
            {
                map.put(from, to);
            }
        }
        return map.isEmpty() ? null : map;
    }
}
