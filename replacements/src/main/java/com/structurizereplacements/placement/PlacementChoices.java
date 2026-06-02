package com.structurizereplacements.placement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Source of per-placement replacement choices (source block → chosen target).
 *
 * <p><b>Phase 1 (plumbing proof):</b> this returns a HARD-CODED choice for everyone so we can verify
 * the choice reaches both server placement and the client preview. Phase 2 replaces {@link #forPlayer}
 * with a per-player server-side store populated by a sync packet from the GUI, and {@link #client}
 * with the locally-chosen map from the build-tool window.
 */
public final class PlacementChoices
{
    private PlacementChoices() {}

    /** Server side: choices for the placing player. */
    public static Map<Block, Block> forPlayer(final Player player)
    {
        return hardCoded();
    }

    /** Client side: choices for the current preview. */
    public static Map<Block, Block> client()
    {
        return hardCoded();
    }

    // TODO Phase 2: replace with real per-player / per-blueprint choices from the GUI.
    private static Map<Block, Block> hardCoded()
    {
        final Block from = block("minecraft:oak_planks");
        final Block to = block("minecraft:dark_oak_planks");
        return (from != null && to != null) ? Map.of(from, to) : Map.of();
    }

    private static Block block(final String id)
    {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        return (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) ? ForgeRegistries.BLOCKS.getValue(rl) : null;
    }
}
