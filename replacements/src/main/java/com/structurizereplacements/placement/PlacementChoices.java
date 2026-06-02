package com.structurizereplacements.placement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Facade the mixins use to read per-placement choices: {@link #forPlayer} on the server (placement),
 * {@link #client} on the client (preview). Backed by {@link ServerPlacementChoices} (synced per-player)
 * and {@link ClientPlacementChoices} (the locally-chosen map).
 */
public final class PlacementChoices
{
    private PlacementChoices() {}

    /** Server side: choices for the placing player. */
    public static Map<Block, Block> forPlayer(final Player player)
    {
        return ServerPlacementChoices.forPlayer(player);
    }

    /** Client side: choices for the current preview. */
    public static Map<Block, Block> client()
    {
        return ClientPlacementChoices.current();
    }
}
