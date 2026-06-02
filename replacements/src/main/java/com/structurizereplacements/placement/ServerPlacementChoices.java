package com.structurizereplacements.placement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side per-player store of replacement choices, populated by the sync packet
 * ({@link com.structurizereplacements.network.SyncReplacementChoicesMessage}) and read at placement
 * time via {@link PlacementChoices#forPlayer}.
 */
public final class ServerPlacementChoices
{
    private ServerPlacementChoices() {}

    private static final Map<UUID, Map<Block, Block>> BY_PLAYER = new ConcurrentHashMap<>();

    public static void set(final UUID playerId, final Map<Block, Block> choices)
    {
        BY_PLAYER.put(playerId, Map.copyOf(choices));
    }

    public static Map<Block, Block> forPlayer(final Player player)
    {
        return player == null ? Map.of() : BY_PLAYER.getOrDefault(player.getUUID(), Map.of());
    }

    public static void clear(final UUID playerId)
    {
        BY_PLAYER.remove(playerId);
    }
}
