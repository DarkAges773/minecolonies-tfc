package com.mctfc.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
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

    /**
     * Client-only ambience while lit — a faithful copy of vanilla {@code BlastFurnaceBlock#animateTick}: an occasional
     * blast-furnace crackle plus a wisp of smoke drifting out the front. The forge borrows the blast furnace's look and
     * sound, so it behaves identically to a lit blast furnace visually/audibly. (Server-side burn drives {@code LIT}.)
     */
    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random)
    {
        if (!state.getValue(LIT))
        {
            return;
        }
        final double x = pos.getX() + 0.5;
        final double y = pos.getY();
        final double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1)
        {
            level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f, false);
        }
        final Direction facing = state.getValue(FACING);
        final Direction.Axis axis = facing.getAxis();
        final double jitter = random.nextDouble() * 0.6 - 0.3;
        final double dx = axis == Direction.Axis.X ? facing.getStepX() * 0.52 : jitter;
        final double dy = random.nextDouble() * 9.0 / 16.0;
        final double dz = axis == Direction.Axis.Z ? facing.getStepZ() * 0.52 : jitter;
        level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
    }

    /**
     * Right-click: with flint-and-steel, <b>light</b> the whole multiblock (like a TFC forge); otherwise open the
     * merged {@link ForgeMenu} on the <b>controller</b> (clicking any member opens the one big device). Item access is
     * player-GUI + worker only — no hoppers (§4).
     */
    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos, final Player player,
            final InteractionHand hand, final BlockHitResult hit)
    {
        final BlockPos controllerPos = ForgeMultiblock.groupOf(level, pos).controller();
        if (!(level.getBlockEntity(controllerPos) instanceof HeatForgeBlockEntity controller))
        {
            return InteractionResult.PASS;
        }

        final ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.FLINT_AND_STEEL))
        {
            if (!level.isClientSide)
            {
                controller.light();
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, level.getRandom().nextFloat() * 0.4f + 0.8f);
                held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            final MenuProvider provider = new SimpleMenuProvider(
                    (windowId, inv, p) -> new ForgeMenu(windowId, inv, controller),
                    level.getBlockState(controllerPos).getBlock().getName());
            NetworkHooks.openScreen(serverPlayer, provider, buf -> ForgeMenu.write(buf, controller));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
