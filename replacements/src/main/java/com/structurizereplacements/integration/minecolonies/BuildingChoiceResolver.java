package com.structurizereplacements.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

/**
 * Bridges the substitution engine's structure handlers to a MineColonies building's replacement choices.
 *
 * <p>The engine can't look up a MineColonies building, so its structure handler asks {@link ChoiceResolver}
 * when it has no choices of its own — on both sides:
 * <ul>
 *   <li><b>server</b>: find the building at the position and return its persisted choices. If it has none
 *       yet, adopt any choices the placing player staged at hut placement ({@link StagedChoices}, captured
 *       by {@code MixinBlueprintPlacementHandling}) — persisting them on the building (markDirty, which
 *       re-syncs the view) so they survive upgrades/rebuilds/restarts;</li>
 *   <li><b>client</b>: read the building <i>view</i>'s synced choices ({@code MixinAbstractBuildingView})
 *       so the Build Options material list/preview reflect that building. Read-only.</li>
 * </ul>
 *
 * <p><b>Decorations</b> (placed without a hut block) have no {@code AbstractBuilding}, but the build origin
 * carries a {@code TileEntityDecorationController} block entity ({@code workOrder.getLocation()} ==
 * {@code worldPos} == the controller's position). So when no building is found we fall through to the block
 * entity at {@code worldPos}: if it is a {@link PlacementChoiceHolder} (the controller, via
 * {@code MixinTileEntityDecorationController}) we use its choices. Server-side we likewise adopt the staged
 * placing-player choices on first resolve and persist them (the controller's NBT — which the standard block
 * entity update packet also syncs to the client). This is the same flow as buildings with a cleaner,
 * position-co-located store.
 *
 * <p>The lookup also works for the build wand's preview: there's no building or controller at the preview
 * position, so the resolver returns {@code null} and the handler falls back to the global session picks.
 *
 * <p>Server-side this is called once per builder structure handler (the handler caches the result), so the
 * colony / block-entity lookup is not per-block.
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
            if (view instanceof PlacementChoiceHolder holder)
            {
                final Map<Block, Block> choices = holder.getReplacementChoices();
                return choices == null ? Map.of() : choices;
            }
            // No building view here — maybe a decoration controller (its choices are synced via the block
            // entity update packet). Read-only on the client.
            final BlockEntity be = world.getBlockEntity(worldPos);
            if (be instanceof PlacementChoiceHolder holder)
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

        // No building here — a decoration. The decoration's work order (which exists for the whole build,
        // unlike its controller block entity, placed mid-build) carries the placing player's choices, adopted
        // at MixinAbstractWorkOrder#onAdded. Its getLocation() == worldPos.
        if (colony != null)
        {
            for (final IServerWorkOrder wo : colony.getWorkManager().getWorkOrders().values())
            {
                if (worldPos.equals(wo.getLocation()) && wo instanceof PlacementChoiceHolder holder)
                {
                    return holder.getReplacementChoices();
                }
            }
        }

        // No active work order — a built decoration being re-resolved. Read the controller block entity, which
        // the work order copies its choices onto at build completion (MixinAbstractWorkOrder#onRemoved).
        final BlockEntity be = world.getBlockEntity(worldPos);
        if (be instanceof PlacementChoiceHolder holder)
        {
            return holder.getReplacementChoices();
        }
        return null;
    }
}
