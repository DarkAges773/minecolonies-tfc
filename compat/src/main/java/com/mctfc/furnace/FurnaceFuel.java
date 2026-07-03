package com.mctfc.furnace;

import com.mctfc.Config;
import net.dries007.tfc.util.Fuel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable, <b>stateless</b> TFC-fuel predicate for the heat-forge workers (Smelter, Cook, Chef). "Which hut can burn
 * what / make what" is driven by <b>required temperature per operation</b>, not hard-coded per-hut fuel lists:
 *
 * <ul>
 *   <li><b>Fuel</b> is any TFC fuel ({@code Fuel.get != null}); each carries a TFC {@code duration} (ticks) and
 *       {@code temperature} (°C).</li>
 *   <li><b>Effective heat</b> = the burning fuel's temperature + a per-building-level bonus
 *       ({@link Config#furnaceFuelTempBonus}, configurable per level), so higher-level huts reach hotter work
 *       (e.g. nickel) without ever reaching the cast-iron melt.</li>
 * </ul>
 *
 * <p>The workers gate a load on whether hot-enough fuel is <b>available</b> ({@link #hasFuelHotEnough}); the forge block
 * itself burns the fuel and tracks the partial-burn pool in its own BE ({@code HeatForgeBlockEntity}), so this class no
 * longer models the burn — it's purely the "is this fuel / is it hot enough" predicate the tend-AI consults.
 */
public final class FurnaceFuel
{
    private FurnaceFuel() {}

    /** Whether this stack is a usable (TFC) fuel. */
    public static boolean isFuel(final ItemStack stack)
    {
        return !stack.isEmpty() && Fuel.get(stack) != null;
    }

    private static float fuelTemp(final ItemStack stack)
    {
        final Fuel fuel = Fuel.get(stack);
        return fuel == null ? 0f : fuel.getTemperature();
    }

    /**
     * Cheap pre-check (for picking a job): is any <b>allowed</b> fuel in storage hot enough for {@code required}?
     * Ignores duration. {@code allowed} is the hut's fuel allow-list filter (see the Smelter's {@code fuelAllowed}).
     */
    public static boolean hasFuelHotEnough(final float required, final int hutLevel, final Predicate<ItemStack> allowed,
            final List<IItemHandler> storage)
    {
        final float bonus = Config.furnaceFuelTempBonus(hutLevel);
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (allowed.test(stack) && fuelTemp(stack) + bonus >= required)
                {
                    return true;
                }
            }
        }
        return false;
    }
}
