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

        /** Fuel items to stage per free heat slot. */
        int fuelPerSlot();

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

    /** Top the carried inventory up with fuel + inputs from the racks, sized to the total free heat slots. */
    public void stage(final List<ForgeController> controllers)
    {
        final int free = totalFreeHeatSlots(controllers);
        final List<IItemHandler> inv = ctx.inventory();
        if (free <= 0 || inv.isEmpty())
        {
            return;
        }
        final IItemHandler to = inv.get(0);
        topUp(ctx.racks(), to, policy::fuelAllowed, free * policy.fuelPerSlot());
        topUp(ctx.racks(), to, policy::accepts, free * policy.inputPerSlot());
    }

    /**
     * Tend one controller in a single visit: drain finished output (returned for the caller to place), eject
     * unprocessable items to the racks, top the shared fuel column, light before loading, load every free heat slot
     * from the carried inventory (gated on the fuel <b>ceiling</b>, not the live temp), and keep-warm / extinguish when
     * idle. Returns the finished stacks removed (the caller delivers them + counts the action).
     */
    public List<ItemStack> tend(final ForgeController c)
    {
        c.setLevelBonus(Config.furnaceFuelTempBonus(ctx.buildingLevel()));

        final List<ItemStack> finished = c.takeFinished();
        for (final ItemStack bad : c.takeUnprocessable())
        {
            insert(ctx.racks(), bad);
        }

        if (c.needsFuel())
        {
            refuel(c);
        }

        final int members = c.members().size();
        final boolean occupied = c.freeHeatSlots() < members;
        final boolean willLoad = c.freeHeatSlots() > 0 && hasLoadable(c);
        if ((occupied || willLoad) && !c.isLit())
        {
            c.light(); // the forge must be lit before loading; it warms up while it runs
        }

        loadInputs(c);
        manageFlame(c, members);
        return finished;
    }

    /** Extinguish an idle forge once its keep-warm window has elapsed — exposed for behaviors that compose their own tend. */
    public void keepWarm(final ForgeController c)
    {
        manageFlame(c, c.members().size());
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

    /** Load each free heat slot with an accepted input the fuel can reach; stop when out of slots or inputs. */
    private void loadInputs(final ForgeController c)
    {
        int guard = c.members().size() + 1;
        while (c.freeHeatSlots() > 0 && guard-- > 0)
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
        }
    }

    /** Extinguish an idle forge once its keep-warm window has elapsed (the fuel-vs-latency knob). */
    private void manageFlame(final ForgeController c, final int members)
    {
        if (c.isLit() && c.freeHeatSlots() == members)
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
