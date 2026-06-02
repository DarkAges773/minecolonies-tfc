package com.structurizereplacements.placement;

import com.structurizereplacements.network.Network;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Client-side store of the player's current replacement choices (source block → chosen target), read
 * by the preview mixins via {@link PlacementChoices#client}. Setting choices also syncs them to the
 * server so placement matches the preview.
 *
 * <p>Phase 2: the GUI calls {@link #set}. (Per-blueprint session memory is managed by the GUI, which
 * swaps the current map when the loaded blueprint changes.)
 */
public final class ClientPlacementChoices
{
    private ClientPlacementChoices() {}

    private static volatile Map<Block, Block> current = Map.of();

    public static Map<Block, Block> current()
    {
        return current;
    }

    /** Replace the current choices and sync them to the server. */
    public static void set(final Map<Block, Block> choices)
    {
        current = (choices == null) ? Map.of() : Map.copyOf(choices);
        Network.sendChoicesToServer(current);
    }
}
