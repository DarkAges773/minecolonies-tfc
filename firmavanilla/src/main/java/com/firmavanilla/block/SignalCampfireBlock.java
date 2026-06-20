package com.firmavanilla.block;

import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A campfire that <b>can't cook</b> and <b>burns out like a TFC torch</b> — a "signal" campfire. Extends vanilla
 * {@link CampfireBlock} so it keeps the look, light, fire damage, facing/lit/signal-fire/waterlogged states,
 * douse-with-water and flint-and-steel relight, but with three changes:
 *
 * <ul>
 *   <li><b>No cooking.</b> Vanilla {@code use()} only places food onto the campfire to cook, so it's overridden to
 *       {@link InteractionResult#PASS}; and the block-entity is swapped from the cooking {@code CampfireBlockEntity}
 *       to our {@link SignalCampfireBlockEntity} (a calendar burn timer + smoke, no cook logic).</li>
 *   <li><b>Burns out + smokes via the BE.</b> {@link #getTicker} wires the BE's {@code serverTick} (extinguish once
 *       past {@link SignalCampfires#BURN_MULT}× TFC's {@code torchTicks}) and {@code clientTick} (the rising smoke
 *       column — a <i>client BE tick</i> so it shows at full render distance, like vanilla, not just the ~16-block
 *       {@code animateTick} range). The block's inherited {@code animateTick} still adds the crackle + lava sparks.</li>
 *   <li><b>Relightable.</b> The timer is reset on placement ({@link #setPlacedBy}) and whenever the campfire is
 *       (re)lit ({@link #onPlace}, {@code LIT} false→true via flint &amp; steel), so a relight gives a fresh life.</li>
 * </ul>
 *
 * <p>One class serves both the normal and soul variants (constructor params mirror vanilla
 * {@code CampfireBlock(spawnParticles, fireDamage, …)}). firmavanilla is TFC-only and mixin-free. See
 * {@code docs/firmavanilla.md}.
 */
public class SignalCampfireBlock extends CampfireBlock
{
    /** Stays lit this many times TFC's {@code torchTicks} (normal 4×; soul 8× — twice as long). */
    private final int burnMult;

    public SignalCampfireBlock(final boolean spawnParticles, final int fireDamage, final int burnMult, final Properties properties)
    {
        super(spawnParticles, fireDamage, properties);
        this.burnMult = burnMult;
    }

    /** Burn-out duration multiplier (× TFC's {@code torchTicks}) — soul campfires burn longer than normal. */
    public int burnMult()
    {
        return burnMult;
    }

    /** No cooking — vanilla {@code use} exists only to place food on the campfire. */
    @Override
    public InteractionResult use(final BlockState state, final Level level, final BlockPos pos,
            final Player player, final InteractionHand hand, final BlockHitResult hit)
    {
        return InteractionResult.PASS;
    }

    /** Our burn-timer-+-smoke BE instead of the cooking {@code CampfireBlockEntity} (so it never cooks). */
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state)
    {
        return new SignalCampfireBlockEntity(pos, state);
    }

    /** Server tick = burn-out; client tick = smoke column (full render distance, unlike {@code animateTick}). */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state,
            final BlockEntityType<T> type)
    {
        return createTickerHelper(type, SignalCampfires.SIGNAL_CAMPFIRE_BE.get(),
                level.isClientSide ? SignalCampfireBlockEntity::clientTick : SignalCampfireBlockEntity::serverTick);
    }

    /** Reset the burn timer when first placed (lit). */
    @Override
    public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state,
            @Nullable final LivingEntity placer, final ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(LIT))
        {
            resetTimer(level, pos);
        }
    }

    /** Reset the timer on a (re)light — {@code LIT} flips false→true on the same block (flint &amp; steel). */
    @Override
    public void onPlace(final BlockState state, final Level level, final BlockPos pos,
            final BlockState oldState, final boolean isMoving)
    {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(LIT) && !(oldState.is(state.getBlock()) && oldState.getValue(LIT)))
        {
            resetTimer(level, pos); // no-op if the BE isn't created yet (initial place → setPlacedBy resets)
        }
    }

    /**
     * Start the burn timer from "now". We must look up our <b>own</b> BE type and call {@code resetCounter()} — TFC's
     * static {@code TickCounterBlockEntity.reset(level,pos)} only matches a BE of type {@code TICK_COUNTER}, so it
     * silently misses our {@code signal_campfire} type and leaves {@code lastUpdateTick} at its sentinel default,
     * which makes the campfire read as "ancient" and burn out instantly.
     */
    private static void resetTimer(final Level level, final BlockPos pos)
    {
        level.getBlockEntity(pos, SignalCampfires.SIGNAL_CAMPFIRE_BE.get()).ifPresent(TickCounterBlockEntity::resetCounter);
    }
}
