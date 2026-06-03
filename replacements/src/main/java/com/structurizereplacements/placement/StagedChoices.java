package com.structurizereplacements.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side staging for replacement choices made when a structure is placed, keyed by position. Used
 * by integrations that attach choices to something created asynchronously from the placement (e.g. a
 * MineColonies building, registered a tick or more after the hut block is placed): the placement hook
 * stashes the placing player's choices here, and the integration adopts them once its target exists.
 *
 * <p>MC-free on purpose — lives in the standalone engine; only optional integrations consume it.
 */
public final class StagedChoices
{
    private StagedChoices() {}

    private static final Map<BlockPos, Map<Block, Block>> STAGED = new ConcurrentHashMap<>();

    public static void stage(final BlockPos pos, final Map<Block, Block> choices)
    {
        if (pos != null && choices != null && !choices.isEmpty())
        {
            STAGED.put(pos.immutable(), Map.copyOf(choices));
        }
    }

    /** Returns and removes any staged choices for the position. */
    public static Map<Block, Block> take(final BlockPos pos)
    {
        return pos == null ? null : STAGED.remove(pos.immutable());
    }
}
