package com.mctfc.cook;

import com.mctfc.furnace.FurnaceProcessing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * The cook's <b>completion</b> step, run by the furnace itself (via {@code MixinAbstractFurnaceBlockEntity} →
 * {@link com.mctfc.furnace.FurnaceProcessings}) when a cook's {@code litTime} burns out — not by the worker. It
 * turns the raw food sitting in the furnace's input slot into cooked food <b>in place</b> (in the result slot),
 * preserving decay via TFC's {@code copy_food} ({@link CookRecipes#cook}), and clears the input. The worker later
 * hauls the cooked food out to the racks.
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
        final ItemStack cooked = CookRecipes.cook(raw, level.registryAccess());
        if (cooked.isEmpty())
        {
            return; // not cookable (e.g. a hand-edited slot) — leave it for the worker to clear
        }
        furnace.setItem(RESULT, cooked);
        furnace.setItem(INPUT, ItemStack.EMPTY);
        furnace.setChanged();
    }
}
