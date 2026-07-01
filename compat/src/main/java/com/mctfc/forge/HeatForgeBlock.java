package com.mctfc.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The heat-forge block (see {@code docs/tfc-forge-multiblock.md}). A furnace-shaped, front-facing device that replaces
 * the vanilla furnaces MineColonies' huts use: face-adjacent forge blocks merge into one multiblock (capped at 5) whose
 * lowest-{@code BlockPos} member is the controller — the {@link HeatForgeBlockEntity} that self-ticks the shared
 * processing. This block is deliberately <b>fully custom</b> (not a {@code FurnaceBlock}) so it's free of every
 * vanilla-furnace quirk; discovery + tending is done by our own {@code ForgeUserModule} + tend-AI.
 *
 * <p>{@code LIT} is driven by the controller's burn (all members light together); it exposes <b>no</b> item-handler
 * capability to the world, so hoppers/pipes can't touch it — item access is player-GUI + worker only (a deliberate
 * handicap, §4).
 */
public class HeatForgeBlock extends BaseEntityBlock
{
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public HeatForgeBlock(final Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(final BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state)
    {
        return new HeatForgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type)
    {
        if (level.isClientSide)
        {
            return null; // the device processes server-side only
        }
        return createTickerHelper(type, HeatForgeBlocks.HEAT_FORGE_BE.get(), HeatForgeBlockEntity::serverTick);
    }

    /** Drop this block's own position slots (+ the fuel column if it was the controller) when it's broken. */
    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState, final boolean moved)
    {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HeatForgeBlockEntity be)
        {
            for (final var drop : be.dropContents())
            {
                Block.popResource(level, pos, drop);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
