package com.structurizereplacements.integration.slimcolonies;

import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.api.colony.IColony;
import no.monopixel.slimcolonies.api.colony.IColonyManager;
import no.monopixel.slimcolonies.api.colony.buildings.IBuilding;
import no.monopixel.slimcolonies.api.colony.buildings.views.IBuildingView;
import no.monopixel.slimcolonies.api.colony.workorders.IServerWorkOrder;
import no.monopixel.slimcolonies.core.colony.workorders.WorkOrderMiner;

import java.util.Map;

/**
 * SlimColonies twin of the MineColonies {@code BuildingChoiceResolver} — see that class for the full
 * design rationale (hut/decoration/mineshaft resolution, staged-choice adoption, client view lookup).
 * Identical flow; only the fork's API differs: {@code IColony#getBuildingManager().getBuilding(pos)}
 * returns {@link IBuilding} directly (SlimColonies predates MineColonies' {@code ICommonBuilding} split),
 * so no separate persistent-building cast is needed before {@code markDirty()}.
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
        final IBuilding building = colony == null ? null : colony.getBuildingManager().getBuilding(worldPos);
        if (building instanceof PlacementChoiceHolder holder)
        {
            Map<Block, Block> choices = holder.getReplacementChoices();
            if (choices == null || choices.isEmpty())
            {
                final Map<Block, Block> staged = StagedChoices.take(world.dimension(), worldPos);
                if (staged != null && !staged.isEmpty())
                {
                    holder.setReplacementChoices(staged);
                    building.markDirty();
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
                    final IBuilding hut = colony.getBuildingManager().getBuilding(wo.getClaimedBy());
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
