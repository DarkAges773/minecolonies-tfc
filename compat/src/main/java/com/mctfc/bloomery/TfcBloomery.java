package com.mctfc.bloomery;

import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blocks.devices.BloomeryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The <b>only</b> class in {@code :compat} that names TFC bloomery types — the bridge the Smelter's bloomery feature
 * reaches the device through (see {@code docs/tfc-bloomery-smelter.md}). TFC is a mandatory dependency, so this loads
 * eagerly (no {@code ModList} guard, unlike the FirmaLife beekeeper bridge).
 *
 * <p>Slice 1 (marking) needs only recognition + structure validation; the load / light / extract helpers are added with
 * the tend-AI in slice 2.
 */
public final class TfcBloomery
{
    private TfcBloomery() {}

    /** Whether this block is a TFC bloomery (the gate block). */
    public static boolean isBloomeryBlock(final Block block)
    {
        return block instanceof BloomeryBlock;
    }

    /** Whether this state is a TFC bloomery. */
    public static boolean isBloomery(final BlockState state)
    {
        return state.getBlock() instanceof BloomeryBlock;
    }

    /** The bloomery block entity at {@code pos}, or {@code null} if it isn't a loaded bloomery. */
    public static BloomeryBlockEntity bloomeryAt(final Level level, final BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof BloomeryBlockEntity be ? be : null;
    }

    /**
     * Whether the bloomery at {@code pos} is a <b>valid, formed multiblock</b> (insulation shell present for its facing) —
     * TFC's {@link BloomeryBlock#isFormed}. Capacity is {@code isFormed ? chimneyLevels * bloomeryCapacity : 0}, so a
     * formed bloomery always has ≥1 chimney level (≥16 capacity) and is actually loadable. The clicked block must be a
     * bloomery (checked here so callers can pass any clicked position). The internal block (behind the gate) is
     * {@code pos.relative(facing.getOpposite())}, matching {@code BloomeryBlockEntity#getInternalBlockPos}.
     */
    public static boolean isFormed(final Level level, final BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BloomeryBlock))
        {
            return false;
        }
        final Direction facing = state.getValue(BloomeryBlock.FACING);
        final BlockPos internal = pos.relative(facing.getOpposite());
        return BloomeryBlock.isFormed(level, internal, facing);
    }
}
