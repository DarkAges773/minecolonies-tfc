package com.mctfc.builder;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.mctfc.MineColoniesTFC;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.ServerChoiceResolver;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Bridges the builder's structure handlers to the MineColonies building's persisted replacement choices.
 *
 * <p>The (Structurize-only) {@code structurizereplacements} mod can't look up a MineColonies building, so
 * its structure handler asks {@link ServerChoiceResolver} for server-side choices when it has none of its
 * own. We register this resolver: given the build position, find the building, return its persisted
 * choices. If the building has none yet, adopt any choices the placing player staged at hut placement
 * ({@link StagedChoices}, captured by {@code MixinBlueprintPlacementHandling}) — persisting them on the
 * building so they survive upgrades/rebuilds/restarts.
 *
 * <p>Called once per builder structure handler (the handler caches the result), so the colony lookup is
 * not per-block.
 */
public final class BuildingChoiceResolver
{
    private BuildingChoiceResolver() {}

    public static void register()
    {
        ServerChoiceResolver.set(BuildingChoiceResolver::resolve);
    }

    private static Map<Block, Block> resolve(final net.minecraft.world.level.Level world,
                                             final net.minecraft.core.BlockPos worldPos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, worldPos);
        final ICommonBuilding building = colony == null ? null : colony.getCommonBuildingManager().getBuilding(worldPos);
        if (!(building instanceof PlacementChoiceHolder holder))
        {
            return null;
        }

        Map<Block, Block> choices = holder.getReplacementChoices();
        if (choices == null || choices.isEmpty())
        {
            final Map<Block, Block> staged = StagedChoices.take(worldPos);
            if (staged != null && !staged.isEmpty())
            {
                holder.setReplacementChoices(staged);
                if (building instanceof IBuilding persistent)
                {
                    persistent.markDirty();
                }
                choices = staged;
            }
        }

        if (choices != null && !choices.isEmpty())
        {
            MineColoniesTFC.LOGGER.debug("[mctfc] builder at {} using {} replacement choice(s)", worldPos, choices.size());
        }
        return choices;
    }
}
