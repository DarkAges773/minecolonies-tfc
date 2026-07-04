package com.mctfc.bloomery;

import com.mctfc.smelter.SmelterRecipes;
import net.dries007.tfc.common.blockentities.BloomBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import net.dries007.tfc.common.blocks.devices.BloomeryBlock;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

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

    // --- Tending (slice 2) ---------------------------------------------------------------------------------

    /** Whether the bloomery is burning (the {@code LIT} blockstate — TFC's single source of truth). */
    public static boolean isLit(final BloomeryBlockEntity be)
    {
        return be.getBlockState().getValue(BloomeryBlock.LIT);
    }

    /** Item capacity = {@code chimneyLevels × bloomeryCapacity} (TFC server config); each loaded item is one entry. */
    public static int capacity(final Level level, final BloomeryBlockEntity be)
    {
        return BloomeryBlock.getChimneyLevels(level, be.getInternalBlockPos()) * TFCConfig.SERVER.bloomeryCapacity.get();
    }

    /** Free item slots left in the bloomery ({@link #capacity} − currently loaded). */
    public static int freeCapacity(final Level level, final BloomeryBlockEntity be)
    {
        return capacity(level, be) - be.getInputCount();
    }

    /**
     * Load one item into the bloomery — added straight to its <b>live</b> {@code inputStacks} list ({@code
     * getInputStacks()} returns the field, verified) as a size-1 stack, then {@code setChanged}. This is TFC's own
     * internal load path (add size-1 stacks, then {@link #light} runs {@code updateCachedRecipe} over the list): no
     * item-entities on the floor, no reflection. Caller stays within {@link #capacity}.
     */
    public static void loadInput(final BloomeryBlockEntity be, final ItemStack oneItem)
    {
        if (oneItem.isEmpty())
        {
            return;
        }
        oneItem.setCount(1);
        be.getInputStacks().add(oneItem);
        be.setChanged();
    }

    /** Ignite the bloomery (public, server-safe); caches the recipe from the loaded ore + sets {@code LIT}. */
    public static boolean light(final BloomeryBlockEntity be)
    {
        return be.light(be.getBlockState());
    }

    /** The finished-bloom BE inside the bloomery (at the internal pos), or {@code null} if none has formed. */
    public static BloomBlockEntity bloomAt(final Level level, final BloomeryBlockEntity be)
    {
        return level.getBlockEntity(be.getInternalBlockPos()) instanceof BloomBlockEntity bloom ? bloom : null;
    }

    /**
     * Take every finished bloom out of a {@link BloomBlockEntity} <b>into a returned list</b> (for the worker to bank),
     * then clear the bloom block. Reads {@code getCount()}/{@code getItem()} directly and {@code removeBlock}s — the
     * bloom BE isn't a {@code Container} and {@code BloomBlock} has no drop-on-remove, so this doesn't spill items on the
     * ground (unlike TFC's own {@code dropBloom}, which spawns item-entities). Blooms drop hot; they cool in the racks.
     */
    public static List<ItemStack> extractBlooms(final Level level, final BloomBlockEntity bloom)
    {
        final int count = bloom.getCount();
        final ItemStack template = bloom.getItem();
        if (count <= 0 || template.isEmpty())
        {
            return List.of();
        }
        final List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            final ItemStack one = template.copy();
            one.setCount(1);
            out.add(one);
        }
        level.removeBlock(bloom.getBlockPos(), false);
        return out;
    }

    /** Whether this stack is an iron ore the bloomery smelts (hematite/magnetite/limonite — melts to cast iron). */
    public static boolean isIronOre(final ItemStack stack)
    {
        final SmelterRecipes.Output out = SmelterRecipes.outputFor(stack);
        return out != null && out.bloom();
    }

    /** The cast-iron mB one of this ore yields (grade-based: small 10 / poor 15 / normal 25 / rich 35); 0 if not ore. */
    public static int oreMb(final ItemStack stack)
    {
        return SmelterRecipes.meltMb(stack);
    }

    /** Whether this stack is the bloomery catalyst (charcoal). */
    public static boolean isCatalyst(final ItemStack stack)
    {
        return SmelterRecipes.isCharcoal(stack);
    }
}
