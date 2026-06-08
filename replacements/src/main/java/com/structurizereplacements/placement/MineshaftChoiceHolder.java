package com.structurizereplacements.placement;

import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * A second, independent replacement-choice map carried alongside {@link PlacementChoiceHolder}'s main map,
 * used for the MineColonies miner's <b>mineshaft</b> schematics (the underground tunnels/shafts the miner
 * builds itself, sourced from {@code infrastructure/mineshafts/*} in the hut's structure pack).
 *
 * <p>Implemented only by the MineColonies building and its client view (via the optional integration
 * mixins): the miner hut stores its mineshaft palette here, separate from the hut-building palette
 * ({@link PlacementChoiceHolder}), so the tunnels can be styled independently of the hut. The per-placement
 * structure handler still carries a single resolved map — {@code BuildingChoiceResolver} decides which map
 * (hut vs mineshaft) to feed it based on what is being placed. MC-free interface.
 */
public interface MineshaftChoiceHolder
{
    void setMineshaftChoices(Map<Block, Block> choices);

    Map<Block, Block> getMineshaftChoices();
}
