package com.mctfc.cook;

import com.mctfc.furnace.FurnaceProcessing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * The cook's <b>completion</b> step, run by the furnace itself (via {@code MixinAbstractFurnaceBlockEntity} →
 * {@link com.mctfc.furnace.FurnaceProcessings}) when a cook's {@code litTime} burns out — not by the worker. It
 * turns <b>one</b> raw piece from the furnace's input slot into cooked food <b>in place</b> (accumulated in the
 * result slot), preserving decay via TFC's {@code copy_food} ({@link CookRecipes#cook}), and shrinks the input by
 * one. The worker later hauls the cooked food out to the racks.
 *
 * <p><b>One piece per firing.</b> TFC cooks food one item at a time (a firepit/grill heats a single item), so a
 * completion cooks a single piece and leaves the rest of a loaded stack in place — the furnace re-ignites for the
 * next piece (see {@code MixinAbstractFurnaceBlockEntity} → {@link com.mctfc.furnace.FurnaceHeating#igniteInPlace}).
 * The dining-hall Cook always loads exactly one, so this is a no-op difference for it; the Kitchen Chef, which may
 * load a whole request's worth into a furnace, cooks it one piece per cycle for free (throughput via parallel
 * furnaces, not batch-cooking). See docs/tfc-furnace-workers.md.
 */
public final class CookProcessing implements FurnaceProcessing
{
    /** The {@link com.mctfc.furnace.FurnaceProcessings} kind the cook stamps onto a furnace it loads. */
    public static final String KIND = "cook";

    private static final int INPUT  = 0;
    private static final int RESULT = 2;

    @Override
    public void complete(final AbstractFurnaceBlockEntity furnace)
    {
        final Level level = furnace.getLevel();
        final ItemStack raw = furnace.getItem(INPUT);
        if (level == null || raw.isEmpty())
        {
            return;
        }
        final ItemStack cooked = CookRecipes.cook(raw.copyWithCount(1), level.registryAccess());
        if (cooked.isEmpty())
        {
            return; // not cookable (e.g. a hand-edited slot) — leave it for the worker to clear
        }
        cooked.setCount(1);

        final ItemStack result = furnace.getItem(RESULT);
        if (result.isEmpty())
        {
            furnace.setItem(RESULT, cooked);
        }
        else if (ItemStack.isSameItemSameTags(result, cooked) && result.getCount() < result.getMaxStackSize())
        {
            result.grow(1);
        }
        else
        {
            return; // result slot occupied by something else / full — leave the input for the worker to sort out
        }

        raw.shrink(1);
        if (raw.isEmpty())
        {
            furnace.setItem(INPUT, ItemStack.EMPTY);
        }
        furnace.setChanged();
    }
}
