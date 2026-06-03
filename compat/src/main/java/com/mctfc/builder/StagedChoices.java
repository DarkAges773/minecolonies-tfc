package com.mctfc.builder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side staging for replacement choices made when a hut is placed, keyed by the hut/building
 * position. The building is created asynchronously (hut block → colony registers it), so we stash the
 * placing player's choices here at placement and the building adopts them when its
 * {@code BuildingStructureHandler} is first set up (then persists them on the building).
 *
 * <p>Transient: choices not yet adopted onto a building are lost on server restart (that first build
 * falls back to datapack rules). Once adopted, they persist via the building's NBT.
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
