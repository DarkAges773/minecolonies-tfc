package com.firmavanilla.block;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.LampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * The "soul" twin of a TFC metal lamp. Reuses TFC's {@link LampBlock} wholesale — same fuel tank, lit toggle,
 * flint&steel lighting, fill/empty — by reusing {@code TFCBlockEntities.LAMP} (see {@link SoulLamps}); the only
 * differences are a teal "soul" glass texture, a dimmer light (10, matching vanilla soul lantern, set on the
 * properties at registration) and the burn-out behaviour below.
 *
 * <p><b>Burns back to normal.</b> When the lamp's fuel runs out, TFC's {@code LampBlockEntity#checkHasRanOut}
 * flips {@code LIT} false — and that runs from <em>both</em> {@link #randomTick} <em>and</em> {@link #use} (a
 * right-click also re-checks the fuel). So we wrap both and revert to the matching normal TFC lamp on the
 * lit→unlit transition. The guard that makes this correct: only revert when the tank is actually <b>empty</b>, so
 * a manual shift-toggle-<i>off</i> (which leaves fuel in the lamp) stays a soul lamp, while a genuine burn-out
 * reverts. A never-lit lamp, or a lava lamp that never empties, is untouched. No mixin needed.
 */
public class SoulLampBlock extends LampBlock
{
    /** Soul lamps glow at vanilla soul-lantern brightness (10), vs a normal lamp's 15. */
    public static final int SOUL_LIGHT = 10;

    public SoulLampBlock(ExtendedProperties properties)
    {
        // Take the TFC lamp's properties as-is and change the one soul detail here, in the block itself: dim the
        // lit light to soul-lantern level (registration just hands us the inherited lamp properties).
        super(properties.lightLevel(s -> s.getValue(LIT) ? SOUL_LIGHT : 0));
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        final boolean wasLit = state.getValue(LIT);
        super.randomTick(state, level, pos, random); // TFC: may drain fuel and flip LIT=false on running out
        revertIfBurnedOut(wasLit, level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        final boolean wasLit = state.getValue(LIT);
        final InteractionResult result = super.use(state, level, pos, player, hand, hit); // re-checks fuel (checkHasRanOut)
        if (!level.isClientSide) revertIfBurnedOut(wasLit, level, pos);
        return result;
    }

    /**
     * If a lit soul lamp just went unlit <em>and</em> its tank is now empty (a real burn-out, not a manual
     * toggle-off), swap it to the matching normal TFC lamp, keeping orientation. Catches every path that flips
     * {@code LIT} via TFC's {@code checkHasRanOut} (random tick, right-click, fluid-tank change during either).
     */
    private void revertIfBurnedOut(boolean wasLit, Level level, BlockPos pos)
    {
        if (!wasLit) return;
        final BlockState now = level.getBlockState(pos);
        if (!now.is(this) || now.getValue(LIT)) return; // still our lamp and now unlit?
        if (!isTankEmpty(level, pos)) return;           // empty = burn-out; has fuel = manual toggle-off (keep soul)

        final Block normal = SoulLamps.normalFor(this);
        if (normal == null) return;
        level.setBlockAndUpdate(pos, normal.defaultBlockState()
                .setValue(LIT, false)
                .setValue(HANGING, now.getValue(HANGING)));
    }

    private static boolean isTankEmpty(Level level, BlockPos pos)
    {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return true;
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER)
                .map(h -> h.getFluidInTank(0).isEmpty())
                .orElse(true);
    }
}
