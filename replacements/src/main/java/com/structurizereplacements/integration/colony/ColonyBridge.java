package com.structurizereplacements.integration.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * The fork-specific API surface of a colony mod (MineColonies or its SlimColonies fork), abstracted so all
 * integration <i>logic</i> lives once in this package and only this thin adapter (plus the unavoidable
 * per-fork mixins) is duplicated per fork. Implemented by {@code MineColoniesBridge} /
 * {@code SlimColoniesBridge}; installed via {@link ColonyIntegration#init} from the guarded mod ctor, so no
 * fork classes are classloaded when no colony mod is present.
 *
 * <p>Buildings and building views are passed as {@link Object}: the shared code never needs their colony-mod
 * type — it casts them to the engine's mixed-in holder interfaces ({@code PlacementChoiceHolder} /
 * {@code MineshaftChoiceHolder}) and hands them back to the bridge for fork-API calls (the loss of static
 * typing is the price of writing the logic once; each bridge method documents what it accepts).
 */
public interface ColonyBridge
{
    // --- server side ---

    /** The colony building at {@code pos}, or null if no colony/building there. */
    Object buildingAt(Level world, BlockPos pos);

    /** Persist a building (from {@link #buildingAt}) to colony NBT and re-sync its client view. */
    void markDirty(Object building);

    /**
     * The replacement choices carried by the work order located at {@code pos}, or null when there is no
     * matching work order (or it carries none). Fork policy lives here because it navigates fork classes:
     * a <b>miner mineshaft</b> work order resolves to the claiming hut's mineshaft palette; a plain
     * (decoration) work order resolves to its own adopted choices.
     */
    Map<Block, Block> workOrderChoicesAt(Level world, BlockPos pos);

    /** May this player edit the choices of the building at {@code buildingPos}? (colony permission check) */
    boolean canEdit(ServerPlayer player, BlockPos buildingPos);

    // --- client side ---

    /** The client building <b>view</b> at {@code pos}, or null. */
    Object buildingViewAt(Level world, BlockPos pos);

    /** The world position of a building view (from {@link #buildingViewAt} or a mixin-supplied view). */
    BlockPos viewPosition(Object view);

    /** The structure pack of a building view. */
    String viewStructurePack(Object view);

    /** The blueprint path of a building view (carries the original build-level digit). */
    String viewStructurePath(Object view);

    /** The current building level of a building view. */
    int viewBuildingLevel(Object view);

    /** Schematic names (no {@code .blueprint} suffix) of every mineshaft node type the miner places. */
    List<String> mineshaftSchematics();

    /** The fork's asset namespace ({@code minecolonies} / {@code slimcolonies}) for borrowed GUI textures. */
    String assetNamespace();
}
