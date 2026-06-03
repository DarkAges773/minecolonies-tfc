package com.mctfc.builder;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Bridges Structurize's structure handlers to the MineColonies building's replacement choices.
 *
 * <p>The (Structurize-only) {@code structurizereplacements} mod can't look up a MineColonies building, so
 * its structure handler asks {@link ChoiceResolver} when it has no choices of its own — on both sides:
 * <ul>
 *   <li><b>server</b>: find the building at the position and return its persisted choices. If it has none
 *       yet, adopt any choices the placing player staged at hut placement ({@link StagedChoices}, captured
 *       by {@code MixinBlueprintPlacementHandling}) — persisting them on the building (markDirty, which
 *       re-syncs the view) so they survive upgrades/rebuilds/restarts;</li>
 *   <li><b>client</b>: read the building <i>view</i>'s synced choices ({@code MixinAbstractBuildingView})
 *       so the Build Options material list/preview reflect that building. Read-only.</li>
 * </ul>
 *
 * <p>The lookup also works for the build wand's preview: there's no building at the preview position, so
 * the resolver returns {@code null} and the handler falls back to the global session picks.
 *
 * <p>Server-side this is called once per builder structure handler (the handler caches the result), so the
 * colony lookup is not per-block.
 */
public final class BuildingChoiceResolver
{
    private BuildingChoiceResolver() {}

    public static void register()
    {
        ChoiceResolver.set(BuildingChoiceResolver::resolve);
    }

    private static Map<Block, Block> resolve(final Level world, final BlockPos worldPos)
    {
        // Client: look up the building VIEW directly (the chunk owning-colony cap that
        // getColonyByPosFromWorld relies on is not reliably synced client-side). The view only mirrors
        // what the server synced; never adopt/persist here. Return a non-null map (empty when the building
        // has no override) so the caller knows a building exists here and must NOT fall back to the global
        // session picks.
        if (world.isClientSide)
        {
            final IBuildingView view = IColonyManager.getInstance().getBuildingView(world.dimension(), worldPos);
            if (!(view instanceof PlacementChoiceHolder holder))
            {
                return null;
            }
            final Map<Block, Block> choices = holder.getReplacementChoices();
            return choices == null ? Map.of() : choices;
        }

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
        return choices;
    }
}
