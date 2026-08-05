package com.structurizereplacements.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side staging for replacement choices made when a structure is placed, keyed by dimension +
 * position. Used by integrations that attach choices to something created asynchronously from the
 * placement (e.g. a MineColonies building, registered a tick or more after the hut block is placed): the
 * placement hook stashes the placing player's choices here, and the integration adopts them once its
 * target exists.
 *
 * <p>Lifecycle: adoption happens within a tick or two of placement, so entries {@linkplain #EXPIRY_MS
 * expire} shortly after — an unconsumed entry (creative paste, non-colony structure) must not linger and
 * be silently adopted by an unrelated structure placed at the same coordinates later. The whole store is
 * also {@linkplain #clear() dropped on server stop} so nothing survives into the next world of the same
 * JVM (single-player world switching).
 *
 * <p>MC-free on purpose — lives in the standalone engine; only optional integrations consume it.
 */
public final class StagedChoices
{
    private StagedChoices() {}

    /** Generous vs. the tick-or-two adoption window, small vs. a play session. */
    private static final long EXPIRY_MS = 30_000;

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private record Entry(Map<Block, Block> choices, long stagedAtMs) {}

    private static final Map<Key, Entry> STAGED = new ConcurrentHashMap<>();

    public static void stage(final ResourceKey<Level> dimension, final BlockPos pos, final Map<Block, Block> choices)
    {
        if (dimension == null || pos == null || choices == null || choices.isEmpty())
        {
            return;
        }
        final long now = System.currentTimeMillis();
        STAGED.values().removeIf(entry -> now - entry.stagedAtMs() > EXPIRY_MS);
        STAGED.put(new Key(dimension, pos.immutable()), new Entry(Map.copyOf(choices), now));
    }

    /** Returns and removes any staged choices for the position; {@code null} when absent or expired. */
    public static Map<Block, Block> take(final ResourceKey<Level> dimension, final BlockPos pos)
    {
        if (dimension == null || pos == null)
        {
            return null;
        }
        final Entry entry = STAGED.remove(new Key(dimension, pos.immutable()));
        if (entry == null || System.currentTimeMillis() - entry.stagedAtMs() > EXPIRY_MS)
        {
            return null;
        }
        return entry.choices();
    }

    /** Drop all staged entries — on server stop, so nothing carries into another world. */
    public static void clear()
    {
        STAGED.clear();
    }
}
