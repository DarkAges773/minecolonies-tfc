package com.mctfc.placement;

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import net.dries007.tfc.common.blocks.soil.ConnectedGrassBlock;
import net.dries007.tfc.common.blocks.soil.ISoilBlock;
import net.dries007.tfc.common.blocks.soil.PathBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Lets the MineColonies builder place TFC <b>grass</b> ({@code tfc:grass/<variant>}, a {@link ConnectedGrassBlock})
 * and <b>grass paths</b> ({@code tfc:grass_path/<variant>}, a {@link PathBlock}) without being supplied the grass/
 * path block itself — mirroring how vanilla Structurize handles {@code minecraft:grass_block}/{@code dirt_path}:
 * it requests plain dirt and places the surface block. Here it requests the <b>matching TFC dirt</b>
 * ({@link ISoilBlock#getDirt()}, which is suppliable/craftable) instead of the grass/path (which a TFC player
 * can't reasonably supply), then places the grass/path.
 *
 * <p>Needed because (a) Structurize's {@code GrassPlacementHandler} only matches vanilla {@code GRASS_BLOCK}/
 * {@code DIRT}, so a substituted TFC grass would fall through to the generic handler and request the unsuppliable
 * grass; and (b) TFC's {@code PathBlock} extends vanilla {@code DirtPathBlock}, so Structurize's
 * {@code BlockGrassPathPlacementHandler} would otherwise grab a substituted TFC path and place a <i>vanilla</i>
 * dirt path while requesting {@code minecraft:dirt}. This handler is registered <b>before</b> that one (see
 * {@code MineColoniesTFC}).
 *
 * <p>Completion is matched leniently on block identity — the grass/path, or its dirt — ignoring connected-texture/
 * snowy state and any reversion to dirt, so the builder recognizes the placement as finished.
 */
public class TfcSoilPlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(final Level world, final BlockPos pos, final BlockState state)
    {
        final Block block = state.getBlock();
        return block instanceof ConnectedGrassBlock || block instanceof PathBlock;
    }

    @Override
    public ActionProcessingResult handle(final Level world, final BlockPos pos, final BlockState state,
                                         @Nullable final CompoundTag tileEntityData, final IPlacementContext context)
    {
        return world.setBlock(pos, state, 3) ? ActionProcessingResult.SUCCESS : ActionProcessingResult.DENY;
    }

    @Override
    public List<ItemStack> getRequiredItems(final Level world, final BlockPos pos, final BlockState state,
                                            @Nullable final CompoundTag tileEntityData, final IPlacementContext context)
    {
        if (!context.fancyPlacement())
        {
            return Collections.singletonList(new ItemStack(state.getBlock()));
        }
        // Builder: charge the matching TFC dirt (suppliable), not the grass/path itself.
        return Collections.singletonList(new ItemStack(((ISoilBlock) state.getBlock()).getDirt().getBlock()));
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(final BlockState worldState, final BlockState blueprintState,
                                                     @Nullable final Tuple<BlockEntity, CompoundTag> blockEntityData, final IPlacementContext context)
    {
        if (worldState.getBlock() == blueprintState.getBlock())
        {
            return true;
        }
        // Accept the corresponding dirt too: connected-grass/path can revert to dirt on bad ground, and grass
        // spreads from dirt — so don't make the builder loop waiting for an exact match.
        return worldState.getBlock() == ((ISoilBlock) blueprintState.getBlock()).getDirt().getBlock();
    }
}
