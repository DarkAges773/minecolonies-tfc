package com.mctfc.furnace;

import net.dries007.tfc.util.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable TFC-fuel model for a furnace-using worker (Smelter, Cook, …). Shared by every
 * {@link FurnaceBehavior} so the "what can this hut burn / make" split is driven by <b>required temperature</b>,
 * not by hard-coded per-hut fuel lists:
 *
 * <ul>
 *   <li><b>Fuel</b> is any TFC fuel ({@code Fuel.get != null}); each carries a TFC {@code duration} (ticks) and
 *       {@code temperature} (°C).</li>
 *   <li><b>Effective heat</b> at a furnace = the burning fuel's temperature + {@code hutLevel ×}
 *       {@value #TEMP_BONUS_PER_LEVEL}°C, so higher-level huts reach hotter work (e.g. nickel) without ever
 *       reaching the cast-iron melt.</li>
 *   <li>An operation needing {@code requiredTemp} for {@code durationTicks} succeeds only if a fuel hot enough
 *       is (or can be) burning. Fuel is consumed by <b>duration with carry-over</b> — one long fuel covers
 *       several operations.</li>
 * </ul>
 *
 * <p>Each behavior keeps its own instance (per-furnace burn state is held here). Callers supply the
 * {@code requiredTemp} for their operation: the Smelter passes the metal's melt temp, the Cook would pass the
 * food's cooking temp — so the same component naturally lets a Cook burn cheap logs while the Smelter needs
 * charcoal for hot metals.
 */
public final class FurnaceFuel
{
    /** Heat added to a fuel's temperature per building level (1–5). */
    public static final int TEMP_BONUS_PER_LEVEL = 10;

    /** Remaining burn at a furnace: ticks left and the temperature of the fuel producing them. */
    private record Burn(int ticks, float temp) {}

    private final Map<BlockPos, Burn> burns = new HashMap<>();

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

    private static int fuelDuration(final ItemStack stack)
    {
        final Fuel fuel = Fuel.get(stack);
        return fuel == null ? 0 : fuel.getDuration();
    }

    /** Cheap pre-check: is any fuel hot enough for {@code required} (carried, or in storage)? Ignores duration. */
    public boolean hasFuelHotEnough(final float required, final int hutLevel, final List<IItemHandler> storage)
    {
        final float bonus = hutLevel * (float) TEMP_BONUS_PER_LEVEL;
        for (final Burn burn : burns.values())
        {
            if (burn.ticks() > 0 && burn.temp() + bonus >= required)
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
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code required}°C for {@code duration} ticks is satisfiable — the carried burn plus hot-enough fuel in storage. */
    public boolean canBurn(final BlockPos furnace, final float required, final int duration, final int hutLevel, final List<IItemHandler> storage)
    {
        final float bonus = hutLevel * (float) TEMP_BONUS_PER_LEVEL;
        final Burn cur = burns.get(furnace);
        int have = (cur != null && cur.temp() + bonus >= required) ? cur.ticks() : 0;
        if (have >= duration)
        {
            return true;
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
     * Reserve {@code duration} ticks at {@code required}°C for this furnace, consuming hot-enough fuel from
     * storage as needed (carrying over any surplus). Call only after {@link #canBurn} returned true.
     */
    public void burn(final BlockPos furnace, final float required, final int duration, final int hutLevel, final List<IItemHandler> storage)
    {
        final float bonus = hutLevel * (float) TEMP_BONUS_PER_LEVEL;
        final Burn cur = burns.get(furnace);
        final boolean carry = cur != null && cur.temp() + bonus >= required;
        int pool = carry ? cur.ticks() : 0;
        float temp = carry ? cur.temp() : 0f;

        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots() && pool < duration; slot++)
            {
                while (pool < duration)
                {
                    final ItemStack in = h.getStackInSlot(slot);
                    if (!isFuel(in) || fuelTemp(in) + bonus < required)
                    {
                        break;
                    }
                    final ItemStack taken = h.extractItem(slot, 1, false);
                    if (taken.isEmpty())
                    {
                        break;
                    }
                    pool += fuelDuration(taken);
                    temp = fuelTemp(taken);
                }
            }
        }
        burns.put(furnace, new Burn(Math.max(0, pool - duration), temp));
    }
}
