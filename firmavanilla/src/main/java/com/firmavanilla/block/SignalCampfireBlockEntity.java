package com.firmavanilla.block;

import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block-entity for the {@link SignalCampfireBlock}s. Extends TFC's {@link TickCounterBlockEntity} so it keeps the
 * <b>calendar-based</b> burn timer (ages even while the chunk is unloaded, exactly like a TFC torch), but is
 * registered as our <b>own</b> BE type (with the campfires as valid blocks) — unlike reusing TFC's {@code TICK_COUNTER}
 * directly, that guarantees the <b>client</b> creates and ticks it, which is what spawns the smoke at full render
 * distance (vanilla's campfire smoke is a client BE tick; the block's {@code animateTick} only reaches ~16 blocks).
 *
 * <p>Two tickers (wired in {@link SignalCampfireBlock#getTicker}): {@link #serverTick} extinguishes the campfire once
 * the counter passes {@link SignalCampfires#BURN_MULT}× TFC's {@code torchTicks}; {@link #clientTick} re-emits the
 * rising smoke column that vanilla's (removed-for-no-cooking) {@code CampfireBlockEntity.particleTick} would.
 */
public class SignalCampfireBlockEntity extends TickCounterBlockEntity
{
    public SignalCampfireBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(SignalCampfires.SIGNAL_CAMPFIRE_BE.get(), pos, state);
    }

    /** TFC's {@code lastUpdateTick} sentinel (set in the ctor) — means the burn timer was never started. */
    private static final long UNINITIALIZED = -2147483648L;

    /** Burn out like a TFC torch: once lit long enough, extinguish to the unlit state. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final SignalCampfireBlockEntity be)
    {
        if (!state.getValue(CampfireBlock.LIT))
        {
            return;
        }
        // Start the timer if it was never reset — placement paths that don't call setPlacedBy/onPlace-with-BE
        // (the MineColonies builder, /setblock, worldgen) leave it at the sentinel, which would otherwise read as
        // "ancient" and burn out on the first tick. Begin the burn from now instead.
        if (be.getLastUpdateTick() == UNINITIALIZED)
        {
            be.resetCounter();
            return;
        }
        final int t = TFCConfig.SERVER.torchTicks.get();
        final int mult = state.getBlock() instanceof SignalCampfireBlock c ? c.burnMult() : SignalCampfires.BURN_MULT;
        if (t > 0 && be.getTicksSinceUpdate() > (long) mult * t)
        {
            // Burn out into the NORMAL (unlit) signal campfire — a soul campfire degrades to a plain one (like a
            // torch → dead torch), carrying over facing/waterlogged/signal-fire. Relighting then gives the normal flame.
            final BlockState burnt = SignalCampfires.SIGNAL_CAMPFIRE.get().withPropertiesOf(state).setValue(CampfireBlock.LIT, false);
            level.setBlockAndUpdate(pos, burnt);
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.5F, 2.6F);
        }
    }

    /** Rising smoke column (cozy or tall "signal"), mirroring vanilla {@code CampfireBlockEntity.particleTick}. */
    public static void clientTick(final Level level, final BlockPos pos, final BlockState state, final SignalCampfireBlockEntity be)
    {
        if (!state.getValue(CampfireBlock.LIT))
        {
            return;
        }
        final RandomSource random = level.getRandom();
        if (random.nextFloat() < 0.11F)
        {
            for (int i = 0; i < random.nextInt(2) + 2; i++)
            {
                CampfireBlock.makeParticles(level, pos, state.getValue(CampfireBlock.SIGNAL_FIRE), false);
            }
        }
    }
}
