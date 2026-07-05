package com.mctfc.forge;

import com.mctfc.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shared <b>tend</b> logic for a heat-forge worker (Smelter / Cook / Chef) — see
 * {@code docs/tfc-forge-multiblock.md} §13. Because the forge self-processes, the worker loop keeps no
 * ignition/timing/completion; it only stocks fuel + inputs, lights, loads, and drains. This factors that common
 * load/fuel/drain/keep-warm out of the behaviors; each worker differs only in its {@link Policy} (what it feeds, at what
 * temperature, from what fuel) and in where it puts the drained output (the caller places {@link #tend}'s return).
 *
 * <p>Stateless per call — driven by a {@link Context} (the worker's world/level/inventory/racks) and a {@link Policy}.
 */
public class ForgeTender
{
    /** The worker's environment the tender reads/writes (inventory is the carried batch; racks are colony storage). */
    public interface Context
    {
        Level world();

        /** Building level (1–5) — drives the fuel heat bonus applied to the forge's burning fuel. */
        int buildingLevel();

        /** The worker's carried inventory (forges are loaded from here). */
        List<IItemHandler> inventory();

        /** The building's racks (staged out of, drained finished goods into). */
        List<IItemHandler> racks();
    }

    /** What this worker feeds a forge, and from what fuel — the only thing that differs between workers. */
    public interface Policy
    {
        /** Whether {@code stack} is a valid input for this worker to load (e.g. menu-cookable food / smeltable ore). */
        boolean accepts(ItemStack stack);

        /** The temperature (°C) an accepted input needs — the tend-AI gates loads on the fuel <b>ceiling</b> reaching it. */
        float requiredTemp(ItemStack stack);

        /** Whether {@code stack} is a fuel this hut may burn (its scope + the player's fuel allow-list). */
        boolean fuelAllowed(ItemStack stack);

        /** Input items to stage per free heat slot (a small carry buffer so the worker reloads several times). */
        int inputPerSlot();
    }

    private final Context ctx;
    private final Policy policy;

    public ForgeTender(final Context ctx, final Policy policy)
    {
        this.ctx = ctx;
        this.policy = policy;
    }

    /** Resolve controller positions to their live controller BEs (skipping any unloaded / non-forge). */
    public List<ForgeController> resolve(final List<BlockPos> controllerPositions)
    {
        final List<ForgeController> out = new ArrayList<>();
        final Level level = ctx.world();
        if (level == null)
        {
            return out;
        }
        for (final BlockPos pos : controllerPositions)
        {
            if (level.getBlockEntity(pos) instanceof HeatForgeBlockEntity be)
            {
                out.add(be);
            }
        }
        return out;
    }

    /** Total free heat slots across every controller — the amount of input/fuel to stage. */
    public int totalFreeHeatSlots(final List<ForgeController> controllers)
    {
        int free = 0;
        for (final ForgeController c : controllers)
        {
            free += c.freeHeatSlots();
        }
        return free;
    }

    /** Top the carried inventory up with fuel (sized to the empty fuel column) + inputs (sized to free heat slots). */
    public void stage(final List<ForgeController> controllers)
    {
        final List<IItemHandler> inv = ctx.inventory();
        if (inv.isEmpty())
        {
            return;
        }
        final IItemHandler to = inv.get(0);
        // Fuel demand tracks the shared column's emptiness, NOT free heat slots: a lit forge with every heat slot busy
        // still burns its column down and must be refuelled, else it self-extinguishes mid-op and strands its load
        // (review SHARED-1 / CHEF-2b / COOK-1's fuel-death precondition). Size the fuel pull to the total empty column
        // slots across the hut's controllers, independent of input staging.
        int emptyFuelSlots = 0;
        for (final ForgeController c : controllers)
        {
            emptyFuelSlots += c.freeFuelSlots();
        }
        if (emptyFuelSlots > 0)
        {
            topUp(ctx.racks(), to, policy::fuelAllowed, emptyFuelSlots);
        }
        // Inputs: nothing to load into a forge with no free heat slot, so this half stays free-slot-sized.
        final int free = totalFreeHeatSlots(controllers);
        if (free > 0)
        {
            topUp(ctx.racks(), to, policy::accepts, free * policy.inputPerSlot());
        }
    }

    /**
     * Tend one controller in a single visit (proactive workers — the Cook): {@link #drain} it, then
     * {@link #stockAndLoad} every free heat slot. Returns the finished stacks removed (the caller delivers them + counts
     * the action). Request-bounded workers (the Chef) must instead call {@link #drain} and {@link #stockAndLoad} directly
     * so they can count deliveries and cap the load to the outstanding request <b>between</b> the two — otherwise they'd
     * reload freed slots on the very cycle the request completes and abandon that batch (see docs/tfc-forge-multiblock.md §14).
     */
    public List<ItemStack> tend(final ForgeController c)
    {
        final List<ItemStack> finished = drain(c);
        stockAndLoad(c, Integer.MAX_VALUE);
        return finished;
    }

    /**
     * Drain finished output (returned for the caller to place) + eject unprocessable items to the racks. Does <b>not</b>
     * load or light — call {@link #stockAndLoad} after the caller has processed the drained output.
     */
    public List<ItemStack> drain(final ForgeController c)
    {
        c.setLevelBonus(Config.furnaceFuelTempBonus(ctx.buildingLevel()));

        final List<ItemStack> finished = c.takeFinished();
        for (final ItemStack bad : c.takeUnprocessable())
        {
            insert(ctx.racks(), bad);
        }
        return finished;
    }

    /**
     * Top the fuel column, light before loading, load up to {@code budget} accepted inputs from the carried inventory
     * (gated on the fuel <b>ceiling</b>, not the live temp), and keep-warm / extinguish when idle. Returns how many
     * inputs were loaded (so a request-bounded caller can decrement its remaining budget across controllers). Pass
     * {@link Integer#MAX_VALUE} to fill every free slot.
     */
    public int stockAndLoad(final ForgeController c, final int budget)
    {
        if (c.needsFuel())
        {
            refuel(c);
        }

        final boolean willLoad = budget > 0 && c.freeHeatSlots() > 0 && hasLoadable(c);
        // Light only for work the fuel can actually finish: an advanceable occupant (an item whose recipe temp the fuel
        // ceiling reaches) or a load we're about to make. A stalled occupant the fuel can't reach (e.g. a player-dropped
        // high-melt ore) is NOT a reason to (re)light — else the forge burns fuel forever for zero output (review
        // SHARED-2). refuel above runs first, so a just-refuelled column makes its stranded occupant advanceable again.
        if ((c.hasAdvanceableOccupant() || willLoad) && !c.isLit())
        {
            c.light(); // the forge must be lit before loading; it warms up while it runs
        }

        final int loaded = loadInputs(c, budget);
        manageFlame(c);
        return loaded;
    }

    /** Extinguish an idle forge once its keep-warm window has elapsed — exposed for behaviors that compose their own tend. */
    public void keepWarm(final ForgeController c)
    {
        manageFlame(c);
    }

    /** Fill the empty fuel slots from the carried inventory (only the bottom slot burns; the column just holds reserve). */
    public void refuel(final ForgeController c)
    {
        int guard = HeatForgeBlockEntity.FUEL_SLOTS + 1;
        while (c.needsFuel() && guard-- > 0)
        {
            final ItemStack fuel = extractOne(ctx.inventory(), policy::fuelAllowed);
            if (fuel.isEmpty())
            {
                break;
            }
            if (!c.addFuel(fuel))
            {
                insert(ctx.inventory(), fuel); // 1-item fuel slots: no room after all — hand it back, don't lose it
                break;
            }
        }
    }

    /**
     * Load up to {@code budget} free heat slots with an accepted input the fuel can reach; stop when out of slots,
     * inputs, or budget. Returns how many were loaded.
     */
    private int loadInputs(final ForgeController c, final int budget)
    {
        int loaded = 0;
        int guard = c.members().size() + 1;
        while (loaded < budget && c.freeHeatSlots() > 0 && guard-- > 0)
        {
            final ItemStack type = findLoadable(c);
            if (type.isEmpty())
            {
                break;
            }
            final ItemStack one = extractOne(ctx.inventory(), s -> ItemHandlerHelper.canItemStacksStack(s, type));
            if (one.isEmpty())
            {
                break;
            }
            if (!c.loadInput(one))
            {
                insert(ctx.inventory(), one); // no free slot after all — hand it back
                break;
            }
            loaded++;
        }
        return loaded;
    }

    /**
     * Extinguish an idle forge once its keep-warm window has elapsed (the fuel-vs-latency knob). Keys on "nothing the
     * forge can still advance" rather than "all heat slots empty", so a stalled occupant the fuel can never finish (a
     * high-melt ore a player dropped in) no longer pins the flame on forever (review SHARED-2). A warming-up item stays
     * advanceable ({@code canReach} tests the fuel ceiling, not the live temp), so this never extinguishes mid-op.
     */
    private void manageFlame(final ForgeController c)
    {
        if (c.isLit() && !c.hasAdvanceableOccupant())
        {
            final long now = ctx.world().getGameTime();
            if (now - c.lastActiveTick() > Config.forgeKeepWarmTicks)
            {
                c.extinguish();
            }
        }
    }

    private boolean hasLoadable(final ForgeController c)
    {
        return !findLoadable(c).isEmpty();
    }

    /** A representative accepted input in the carried inventory the fuel ceiling can reach (not extracted). */
    private ItemStack findLoadable(final ForgeController c)
    {
        for (final IItemHandler h : ctx.inventory())
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (!stack.isEmpty() && policy.accepts(stack) && c.canReach(policy.requiredTemp(stack)))
                {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // --- storage plumbing (shared by the forge workers) ---------------------------------------------------

    /** Move matching items from {@code from} into {@code to} until it holds {@code target} of them. */
    public static void topUp(final List<IItemHandler> from, final IItemHandler to, final Predicate<ItemStack> match, final int target)
    {
        final int have = countMatching(List.of(to), match);
        if (have < target)
        {
            moveToInventory(from, to, match, target - have);
        }
    }

    /** Move up to {@code max} matching items from {@code from} into {@code to}. */
    public static void moveToInventory(final List<IItemHandler> from, final IItemHandler to, final Predicate<ItemStack> match, final int max)
    {
        int moved = 0;
        for (final IItemHandler h : from)
        {
            for (int slot = 0; slot < h.getSlots() && moved < max; slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (stack.isEmpty() || !match.test(stack))
                {
                    continue;
                }
                final ItemStack pulled = h.extractItem(slot, Math.min(max - moved, stack.getCount()), true);
                if (pulled.isEmpty())
                {
                    continue;
                }
                final int accepted = pulled.getCount() - ItemHandlerHelper.insertItem(to, pulled.copy(), false).getCount();
                if (accepted > 0)
                {
                    h.extractItem(slot, accepted, false);
                    moved += accepted;
                }
            }
        }
    }

    public static int countMatching(final List<IItemHandler> handlers, final Predicate<ItemStack> match)
    {
        int count = 0;
        for (final IItemHandler h : handlers)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (!stack.isEmpty() && match.test(stack))
                {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** Extract up to {@code max} matching items, combined into one stack (for a whole-slot fuel top-up). */
    public static ItemStack extractMatching(final List<IItemHandler> from, final Predicate<ItemStack> match, final int max)
    {
        ItemStack collected = ItemStack.EMPTY;
        for (final IItemHandler h : from)
        {
            for (int slot = 0; slot < h.getSlots() && collected.getCount() < max; slot++)
            {
                while (collected.getCount() < max && match.test(h.getStackInSlot(slot)))
                {
                    final ItemStack taken = h.extractItem(slot, 1, false);
                    if (taken.isEmpty())
                    {
                        break;
                    }
                    if (collected.isEmpty())
                    {
                        collected = taken;
                    }
                    else
                    {
                        collected.grow(1);
                    }
                }
            }
        }
        return collected;
    }

    /** Extract a single matching item. */
    public static ItemStack extractOne(final List<IItemHandler> from, final Predicate<ItemStack> match)
    {
        for (final IItemHandler h : from)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                if (match.test(h.getStackInSlot(slot)))
                {
                    final ItemStack taken = h.extractItem(slot, 1, false);
                    if (!taken.isEmpty())
                    {
                        return taken;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Insert into the given storage, spilling to nowhere (caller guarantees room, or it's a best-effort return). */
    public static void insert(final List<IItemHandler> storage, ItemStack stack)
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
