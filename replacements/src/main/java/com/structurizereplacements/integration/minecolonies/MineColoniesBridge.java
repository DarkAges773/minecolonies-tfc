package com.structurizereplacements.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.util.MineNode;
import com.structurizereplacements.integration.colony.ColonyBridge;
import com.structurizereplacements.integration.colony.ColonyIntegration;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ColonyBridge} for MineColonies — the only non-mixin class that touches MineColonies types.
 * {@code StructurizeReplacements} calls {@link #init()} only when {@code minecolonies} is loaded, so
 * nothing here is classloaded in the standalone case. All integration logic lives in the shared
 * {@code integration.colony} package; this adapter just navigates the MineColonies API.
 */
public final class MineColoniesBridge implements ColonyBridge
{
    public static void init(final net.neoforged.bus.api.IEventBus modBus)
    {
        ColonyIntegration.init(new MineColoniesBridge(), modBus);
    }

    @Override
    public Object buildingAt(final Level world, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
        return colony == null ? null : colony.getCommonBuildingManager().getBuilding(pos);
    }

    @Override
    public void markDirty(final Object building)
    {
        // getCommonBuildingManager returns ICommonBuilding; only the persistent kind can markDirty.
        if (building instanceof IBuilding persistent)
        {
            persistent.markDirty();
        }
    }

    @Override
    public Map<Block, Block> workOrderChoicesAt(final Level world, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
        if (colony == null)
        {
            return null;
        }
        for (final IServerWorkOrder wo : colony.getWorkManager().getWorkOrders().values())
        {
            if (!pos.equals(wo.getLocation()))
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
        return null;
    }

    @Override
    public boolean canEdit(final ServerPlayer player, final BlockPos buildingPos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.serverLevel(), buildingPos);
        return colony != null && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
    }

    @Override
    public Object buildingViewAt(final Level world, final BlockPos pos)
    {
        return IColonyManager.getInstance().getBuildingView(world.dimension(), pos);
    }

    @Override
    public BlockPos viewPosition(final Object view)
    {
        return ((IBuildingView) view).getPosition();
    }

    @Override
    public String viewStructurePack(final Object view)
    {
        return ((IBuildingView) view).getStructurePack();
    }

    @Override
    public String viewStructurePath(final Object view)
    {
        return ((IBuildingView) view).getStructurePath();
    }

    @Override
    public int viewBuildingLevel(final Object view)
    {
        return ((IBuildingView) view).getBuildingLevel();
    }

    @Override
    public List<String> mineshaftSchematics()
    {
        // Distinct schematic names the miner uses; NodeType carries the canonical names (LADDER_BACK/UNDEFINED
        // are empty — skipped). Deriving from the enum means new node types are picked up automatically.
        final List<String> names = new ArrayList<>();
        for (final MineNode.NodeType type : MineNode.NodeType.values())
        {
            final String name = type.getSchematicName();
            if (name != null && !name.isEmpty() && !names.contains(name))
            {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public String assetNamespace()
    {
        return "minecolonies";
    }
}
