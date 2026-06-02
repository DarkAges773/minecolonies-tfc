package com.structurizereplacements.placement;

import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Mixed into Structurize's {@code StructurePlacer} so a placement carries the placing player's
 * per-placement replacement choices (source block → chosen target). Set when the
 * {@code PlaceStructureOperation} is created (player known there); read in {@code handleBlockPlacement}.
 */
public interface PlacementChoiceHolder
{
    void setReplacementChoices(Map<Block, Block> choices);

    Map<Block, Block> getReplacementChoices();
}
