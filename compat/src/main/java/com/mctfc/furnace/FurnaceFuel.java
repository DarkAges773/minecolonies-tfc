package com.mctfc.furnace;

import com.mctfc.Config;
import net.dries007.tfc.util.Fuel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Reusable, <b>stateless</b> TFC-fuel model for furnace workers (Smelter, Cook, …). The "which hut can burn
 * what / make what" split is driven by <b>required temperature per operation</b>, not by hard-coded per-hut fuel
 * lists:
 *
 * <ul>
 *   <li><b>Fuel</b> is any TFC fuel ({@code Fuel.get != null}); each carries a TFC {@code duration} (ticks) and
 *       {@code temperature} (°C).</li>
 *   <li><b>Effective heat</b> = the burning fuel's temperature + a per-building-level bonus
 *       ({@link Config#furnaceFuelTempBonus}, configurable per level), so higher-level huts reach hotter work
 *       (e.g. nickel) without ever reaching the cast-iron melt.</li>
 *   <li>An operation needing {@code requiredTemp} for {@code durationTicks} runs only if a fuel hot enough is (or
 *       can be) burning. TFC-authentic: the heat-up <i>rate</i> is fixed by {@code heat_capacity}; the device
 *       temperature is only the <i>ceiling</i> — hotter fuel doesn't melt faster, it just decides whether the
 *       metal melts at all.</li>
 *   <li><b>Longevity</b> — fuel is consumed by duration with carry-over: one charcoal (1800 ticks) covers ~4
 *       copper melts. Fuel physically sits in the furnace's <b>fuel slot</b> (so it drops on break and shows in
 *       the GUI), restocked from the racks; the partial-burn pool ({@link Burn}) lives in the furnace's
 *       {@link FurnaceProcess} cap, so it persists across reload.</li>
 * </ul>
 */
public final class FurnaceFuel
{
    private FurnaceFuel() {}

    /** A furnace's carried fuel: remaining burn ticks and the temperature of the fuel producing them. */
    public record Burn(int ticks, float temp) {}

    /** Whether this stack is a usable (TFC) fuel. */
    public static boolean isFuel(final ItemStack stack)
    {
        return !stack.isEmpty() && Fuel.get(stack) != null;
    }

    /** Whether this fuel is hot enough (after the hut's level bonus) to run an operation needing {@code required}°C. */
    public static boolean isHotEnough(final ItemStack stack, final float required, final int hutLevel)
    {
        return isFuel(stack) && fuelTemp(stack) + Config.furnaceFuelTempBonus(hutLevel) >= required;
    }

    private static float fuelTemp(final ItemStack stack)
    {
        final Fuel fuel = Fuel.get(stack);
        return fuel == null ? 0f : fuel.getTemperature();
    }

    private static int fuelDuration(final ItemStack stack)
    {
        final Fuel fuel = Fuel.get(stack);
        return fuel == null ? 0 : fuel.getDuration();
    }

    /** Cheap pre-check (for picking a job): is any fuel in storage hot enough for {@code required}? Ignores duration. */
    public static boolean hasFuelHotEnough(final float required, final int hutLevel, final List<IItemHandler> storage)
    {
        final float bonus = Config.furnaceFuelTempBonus(hutLevel);
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (isFuel(stack) && fuelTemp(stack) + bonus >= required)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether {@code required}°C for {@code duration} ticks is satisfiable from the carried pool, the fuel
     * already in the furnace's fuel slot, and hot-enough fuel in storage.
     */
    public static boolean canBurn(final Burn pool, final float required, final int duration, final int hutLevel,
            final ItemStack fuelInSlot, final List<IItemHandler> storage)
    {
        final float bonus = Config.furnaceFuelTempBonus(hutLevel);
        int have = (pool.temp() + bonus >= required) ? pool.ticks() : 0;
        if (have >= duration)
        {
            return true;
        }
        if (isFuel(fuelInSlot) && fuelTemp(fuelInSlot) + bonus >= required)
        {
            have += fuelDuration(fuelInSlot) * fuelInSlot.getCount();
            if (have >= duration)
            {
                return true;
            }
        }
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (isFuel(stack) && fuelTemp(stack) + bonus >= required)
                {
                    have += fuelDuration(stack) * stack.getCount();
                    if (have >= duration)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Reserve {@code duration} ticks at {@code required}°C. The furnace's {@code fuelSlot} holds at most the
     * <b>single</b> fuel item currently being burned (so the colony's fuel isn't hoarded into furnaces); the
     * carried {@link Burn} pool is that item's remaining ticks. We draw down the pool, and only when it's spent
     * do we discard the slot item and ignite the next one (a fresh item pulled from storage). A too-cool fuel
     * sitting in the slot is returned to storage first. Returns the new pool; call only after {@link #canBurn}.
     */
    public static Burn burn(final Burn pool, final float required, final int duration, final int hutLevel,
            final Container furnace, final int fuelSlot, final List<IItemHandler> storage)
    {
        final float bonus = Config.furnaceFuelTempBonus(hutLevel);

        // A fuel already in the slot but too cool for this metal: hand it back so we can stock a hot one.
        final ItemStack inSlot = furnace.getItem(fuelSlot);
        if (!inSlot.isEmpty() && (!isFuel(inSlot) || fuelTemp(inSlot) + bonus < required))
        {
            insert(storage, inSlot.copy());
            furnace.setItem(fuelSlot, ItemStack.EMPTY);
        }

        // The carried pool is the remaining burn of the item currently in the slot (valid only if hot enough).
        int have = (pool.temp() + bonus >= required) ? pool.ticks() : 0;
        float temp = pool.temp();

        while (have < duration)
        {
            // The current item is spent (its remaining `have` is already counted) — discard it and ignite the next.
            consumeOne(furnace, fuelSlot);
            final ItemStack next = takeOneHotFuel(storage, required, bonus);
            if (next.isEmpty())
            {
                break; // out of fuel (canBurn should have prevented this); melt proceeds on what we have
            }
            furnace.setItem(fuelSlot, next);
            have += fuelDuration(next);
            temp = fuelTemp(next);
        }

        have -= duration;
        if (have <= 0)
        {
            consumeOne(furnace, fuelSlot); // the burning item is used up
            have = 0;
            temp = 0f;
        }
        furnace.setChanged();
        return new Burn(have, temp);
    }

    private static void consumeOne(final Container furnace, final int fuelSlot)
    {
        final ItemStack slot = furnace.getItem(fuelSlot);
        if (!slot.isEmpty())
        {
            slot.shrink(1);
            if (slot.isEmpty())
            {
                furnace.setItem(fuelSlot, ItemStack.EMPTY);
            }
        }
    }

    /** Pull a single hot-enough fuel item out of storage (the worker igniting one piece at a time). */
    private static ItemStack takeOneHotFuel(final List<IItemHandler> storage, final float required, final float bonus)
    {
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (isFuel(stack) && fuelTemp(stack) + bonus >= required)
                {
                    return h.extractItem(slot, 1, false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void insert(final List<IItemHandler> storage, ItemStack stack)
    {
        for (final IItemHandler h : storage)
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return;
            }
        }
    }
}
