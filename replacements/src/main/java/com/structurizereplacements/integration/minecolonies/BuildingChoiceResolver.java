package com.structurizereplacements.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Bridges the substitution engine's structure handlers to a MineColonies building or decoration's
 * replacement choices.
 *
 * <p>The engine can't look up MineColonies objects, so its structure handler asks {@link ChoiceResolver}
 * when it has no choices of its own:
 * <ul>
 *   <li><b>building (hut)</b> — server: find the building at the position and return its persisted choices
 *       ({@code MixinAbstractBuilding}); if it has none yet, adopt any choices the placing player staged at
 *       placement ({@link StagedChoices}, captured by {@code MixinBlueprintPlacementHandling}) and persist
 *       them (markDirty, which re-syncs the view). Client: read the building <i>view</i>'s synced choices
 *       ({@code MixinAbstractBuildingView}) so Build Options reflect them. Read-only client-side.</li>
 *   <li><b>decoration</b> (placed without a hut block) — has no {@code AbstractBuilding} and no
 *       player-accessible controller, but its <b>work order</b> exists for the whole build and its
 *       {@code getLocation()} == {@code worldPos} (the structure handler is built from it). Server-side we
 *       find that work order and return its choices ({@code MixinAbstractWorkOrder}, which adopted the
 *       staged picks at {@code onAdded}). The build is server-driven, so no client decoration lookup is
 *       needed.</li>
 * </ul>
 *
 * <p>The lookup also works for the build wand's preview: there's no building or decoration work order at the
 * preview position, so the resolver returns {@code null} and the handler falls back to the global session
 * picks.
 *
 * <p>Server-side this is called once per builder structure handler (the handler caches the result), so the
 * colony / work-order lookup is not per-block.
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
        // getColonyByPosFromWorld relies on is not reliably synced client-side). The view only mirrors what
        // the server synced; never adopt/persist here. Return a non-null map (empty when the building has no
        // override) so the caller knows a building exists here and must NOT fall back to the session picks.
        if (world.isClientSide)
        {
            final IBuildingView view = IColonyManager.getInstance().getBuildingView(world.dimension(), worldPos);
            if (view instanceof PlacementChoiceHolder holder)
            {
                final Map<Block, Block> choices = holder.getReplacementChoices();
                return choices == null ? Map.of() : choices;
            }
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, worldPos);
        final ICommonBuilding building = colony == null ? null : colony.getCommonBuildingManager().getBuilding(worldPos);
        if (building instanceof PlacementChoiceHolder holder)
        {
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

        // No building here — a decoration or a miner mineshaft node. Its work order (which exists for the
        // whole build) is at worldPos (getLocation() == worldPos).
        if (colony != null)
        {
            for (final IServerWorkOrder wo : colony.getWorkManager().getWorkOrders().values())
            {
                if (!worldPos.equals(wo.getLocation()))
                {
                    continue;
                }
                // Miner mineshaft node: the work order itself has no player picks (the miner AI created it).
                // Use the MINESHAFT palette of the hut that claimed it (its own building, the miner hut) so
                // the player's mineshaft picks apply to every tunnel/shaft.
                if (wo instanceof WorkOrderMiner)
                {
                    final ICommonBuilding hut = colony.getCommonBuildingManager().getBuilding(wo.getClaimedBy());
                    return hut instanceof MineshaftChoiceHolder holder ? holder.getMineshaftChoices() : null;
                }
                // Plain decoration: its work order carries the placing player's choices, adopted at
                // MixinAbstractWorkOrder#onAdded.
                if (wo instanceof PlacementChoiceHolder holder)
                {
                    return holder.getReplacementChoices();
                }
            }
        }
        return null;
    }
}
