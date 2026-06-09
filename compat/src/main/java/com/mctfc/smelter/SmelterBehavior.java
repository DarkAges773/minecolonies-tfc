package com.mctfc.smelter;

import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.mixin.FurnaceBlockEntityAccessor;
import com.mctfc.smelter.SmelterRecipes.Output;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import net.dries007.tfc.common.capabilities.MoldLike;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.CastingRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TFC-flavoured replacement for the MineColonies Smelter's furnace loop (see docs/tfc-furnace-workers.md).
 * Collapses TFC metallurgy ({@link SmelterRecipes}): ~{@value SmelterRecipes#UNITS_PER_OUTPUT} mB of one ore →
 * one casting (an ingot, delivered in its mold) or, for iron, a raw bloom.
 *
 * <p><b>Staged AI</b> (like the farmer), three stages cycling, each with a skip-guard:
 * <ol>
 *   <li><b>{@code BATCH_STAGING}</b> — at the hut, top the carried inventory up to the idle furnaces' worth of
 *       ore + molds + fuel/charcoal pulled from the racks.</li>
 *   <li><b>{@code TEND_FURNACES}</b> — one sweep of the furnaces: each finished one is unloaded (output → racks)
 *       and, if materials are in hand, immediately reloaded; each idle one is loaded. One pass, both jobs.</li>
 *   <li><b>{@code MOLD_UNLOAD}</b> — at the hut, for each fully-cooled (heat 0) filled mold in the racks, run
 *       TFC's casting extraction (ingot out, mold kept or broken per its break chance).</li>
 * </ol>
 *
 * <p>The furnace finishes a melt itself (see {@code MixinAbstractFurnaceBlockEntity}/{@link SmelterProcessing}):
 * when its {@code litTime} burns out it fills the mold in place and flips its {@link FurnaceProcess} cap to
 * {@code DONE}. Work items live in the furnace's own slots (so they drop on break / show in the GUI), the melt
 * timer is the vanilla {@code litTime}, and only the carried fuel pool rides in the cap — all furnace BE NBT, so
 * it survives reload. <b>Storage</b> is the building's racks; the worker stages batches into its own inventory
 * and loads furnaces from there.
 */
public class SmelterBehavior implements FurnaceBehavior
{
    private static final int FURNACE_INPUT  = 0;
    private static final int FURNACE_FUEL   = 1;
    private static final int FURNACE_RESULT = 2;

    private static final int    MIN_DURATION     = 40;
    private static final int    DEFAULT_DURATION = 200;
    private static final int    WORK_TICK_RATE   = 10;
    private static final double XP_PER_OUTPUT     = 5.0;

    private enum State implements IAIState
    {
        BATCH_STAGING, TEND_FURNACES, MOLD_UNLOAD;

        @Override
        public boolean isOkayToEat()
        {
            return true; // these are between-action waits; let the citizen eat if hungry
        }
    }

    private final FurnaceWorker ai;
    /** Furnaces already handled in the current TEND sweep (so each is visited once). */
    private final Set<BlockPos> tended = new HashSet<>();
    /** The furnace currently being walked to / tended. */
    private BlockPos target;

    public SmelterBehavior(final FurnaceWorker ai)
    {
        this.ai = ai;
    }

    @Override
    public Collection<AITarget<IAIState>> targets()
    {
        return List.of(
          new AITarget<IAIState>(State.BATCH_STAGING, this::stage, WORK_TICK_RATE),
          new AITarget<IAIState>(State.TEND_FURNACES, this::tend, WORK_TICK_RATE),
          new AITarget<IAIState>(State.MOLD_UNLOAD, this::moldUnload, WORK_TICK_RATE));
    }

    @Override
    public IAIState startWorking()
    {
        // Decide up front like the vanilla smelter: only enter the work cycle if there's something to do,
        // otherwise idle (the base IDLE → START_WORKING re-checks every few ticks).
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    @Override
    public boolean canGoIdle()
    {
        // Let CitizenAI run the wander/idle minimal-AI (and stop ticking us) whenever there's no work to do.
        return !hasWork();
    }

    /** Whether any stage has work: a finished furnace, an idle furnace with a makeable melt, or a cooled mold. */
    private boolean hasWork()
    {
        final List<BlockPos> furnaces = ai.furnaces();
        if (ai.world() == null || furnaces.isEmpty())
        {
            return false;
        }
        for (final BlockPos furnace : furnaces)
        {
            final FurnaceProcess cap = capOf(furnaceAt(furnace));
            if (cap != null && cap.phase() == FurnaceProcess.Phase.DONE)
            {
                return true; // a finished furnace to unload
            }
        }
        if (idleFurnaceCount() > 0 && !findReadyJob(combined()).isEmpty())
        {
            return true; // an idle furnace and a metal we can make from what's in hand or the racks
        }
        return hasCooledMold(racks()); // a fully-cooled filled mold to extract
    }

    private boolean hasCooledMold(final List<IItemHandler> storage)
    {
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final MoldLike mold = MoldLike.get(h.getStackInSlot(slot));
                if (mold != null && mold.getTemperature() == 0f && CastingRecipe.get(mold) != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    // --- Stage 1: BATCH_STAGING ---------------------------------------------------------------------------

    private IAIState stage()
    {
        if (!ai.gotoBuilding())
        {
            return ai.state();
        }
        stageBatch();
        return State.TEND_FURNACES;
    }

    /** Top the carried inventory up to the idle furnaces' worth of ore + molds + fuel/charcoal from the racks. */
    private void stageBatch()
    {
        final int idle = idleFurnaceCount();
        final List<IItemHandler> inv = inventory();
        if (idle <= 0 || inv.isEmpty())
        {
            return;
        }
        final IItemHandler to = inv.get(0);
        final List<IItemHandler> racks = racks();

        // molds (one per cast melt), fuel (covers cast melts), charcoal (the iron bloomery + doubles as fuel).
        topUp(racks, to, SmelterBehavior::isEmptyMold, idle);
        topUp(racks, to, FurnaceFuel::isFuel, idle);
        topUp(racks, to, SmelterRecipes::isCharcoal, SmelterRecipes.CHARCOAL_PER_BLOOM * idle);

        // Ore: top up to `idle` whole single-grade melts, completing the partial melts the worker already
        // carries by pulling the matching grade from the racks (so held ore isn't disregarded).
        int guard = idle + 8;
        while (inventoryMelts(to) < idle && guard-- > 0)
        {
            final ItemStack grade = gradeToComplete(to, racks);
            if (grade.isEmpty())
            {
                break; // no grade the racks can contribute a (combined) melt of
            }
            final int perMelt = (int) Math.ceil((double) SmelterRecipes.UNITS_PER_OUTPUT / SmelterRecipes.meltMb(grade));
            final int held = countMatching(List.of(to), s -> ItemHandlerHelper.canItemStacksStack(s, grade));
            final int wanted = (held / perMelt + 1) * perMelt - held; // bring held up to the next whole melt
            if (!moveToInventory(racks, to, s -> ItemHandlerHelper.canItemStacksStack(s, grade), wanted))
            {
                break; // racks ran dry for this grade — stop (avoids spinning)
            }
        }
    }

    // --- Stage 2: TEND_FURNACES (unload + load in one sweep) -----------------------------------------------

    private IAIState tend()
    {
        if (ai.furnaces().isEmpty() || ai.world() == null)
        {
            tended.clear();
            return AIWorkerState.IDLE;
        }

        if (target != null)
        {
            if (!ai.gotoWorkPos(target))
            {
                return State.TEND_FURNACES; // still walking
            }
            final FurnaceBlockEntity be = furnaceAt(target);
            final FurnaceProcess cap = capOf(be);
            if (be != null && cap != null)
            {
                if (cap.phase() == FurnaceProcess.Phase.DONE)
                {
                    retrieve(be, racks()); // output → racks, cap → IDLE
                }
                if (cap.phase() == FurnaceProcess.Phase.IDLE)
                {
                    final ItemStack ore = findReadyJob(inventory());
                    if (!ore.isEmpty())
                    {
                        load(be, ore, inventory()); // cap → MELTING
                    }
                }
            }
            tended.add(target);
            target = null;
            return State.TEND_FURNACES;
        }

        // pick the next furnace that needs unloading or can be loaded.
        for (final BlockPos furnace : ai.furnaces())
        {
            if (tended.contains(furnace))
            {
                continue;
            }
            final FurnaceProcess cap = capOf(furnaceAt(furnace));
            if (cap == null)
            {
                tended.add(furnace);
                continue;
            }
            final boolean done = cap.phase() == FurnaceProcess.Phase.DONE;
            final boolean loadable = cap.phase() == FurnaceProcess.Phase.IDLE && !findReadyJob(inventory()).isEmpty();
            if (done || loadable)
            {
                target = furnace;
                return State.TEND_FURNACES;
            }
            tended.add(furnace); // melting, or idle with nothing to load
        }

        tended.clear();
        return State.MOLD_UNLOAD;
    }

    // --- Stage 3: MOLD_UNLOAD -----------------------------------------------------------------------------

    private IAIState moldUnload()
    {
        if (!ai.gotoBuilding())
        {
            return ai.state();
        }
        if (extractCooledMold(racks()))
        {
            ai.delay(WORK_TICK_RATE);
            return State.MOLD_UNLOAD; // more may be cooled
        }
        // No more cooled molds. Loop the cycle while there's still other work (so we don't dip through the
        // AIWorkerState.IDLE "working flash"); otherwise go idle and let CitizenAI / canGoIdle take over.
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    /**
     * Extract one fully-cooled (heat 0) filled mold from storage using TFC's own casting recipe: the ingot is
     * produced and the mold is drained, then kept or broken by its {@link CastingRecipe#getBreakChance()}.
     * Skips blooms/ingots/empty molds (no casting recipe). Returns whether one was processed.
     */
    private boolean extractCooledMold(final List<IItemHandler> storage)
    {
        final Level world = ai.world();
        if (world == null)
        {
            return false;
        }
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final MoldLike mold = MoldLike.get(h.getStackInSlot(slot));
                if (mold == null || mold.getTemperature() != 0f || CastingRecipe.get(mold) == null)
                {
                    continue;
                }
                final ItemStack filled = h.extractItem(slot, 1, false);
                final MoldLike taken = MoldLike.get(filled);
                final CastingRecipe recipe = CastingRecipe.get(taken);
                if (taken == null || recipe == null)
                {
                    insert(storage, filled); // shouldn't happen; put it back
                    continue;
                }
                final ItemStack result = recipe.assemble(taken, world.registryAccess());
                taken.drainIgnoringTemperature(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                insert(storage, result);
                if (world.getRandom().nextFloat() >= recipe.getBreakChance())
                {
                    insert(storage, filled); // mold survives (now empty) — gets re-staged
                }
                return true;
            }
        }
        return false;
    }

    // --- Loading / unloading a single furnace -------------------------------------------------------------

    /**
     * Load a furnace from the carried inventory: ore → input, mold → result, fuel through the fuel slot; set the
     * cap to MELTING and light it. Nothing is consumed unless the whole operation can proceed.
     */
    private void load(final FurnaceBlockEntity be, final ItemStack oreType, final List<IItemHandler> source)
    {
        final Output output = SmelterRecipes.outputFor(oreType);
        if (output == null)
        {
            return;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(oreType);
        final float meltTemp = recipe != null ? recipe.getTemperature() : 1000f;
        final Fluid fluid = recipe != null && !recipe.getDisplayOutputFluid().isEmpty() ? recipe.getDisplayOutputFluid().getFluid() : null;
        final int meltDuration = duration(output, meltTemp);
        final int level = ai.buildingLevel();
        final FurnaceProcess cap = capOf(be);
        if (cap == null)
        {
            return;
        }

        if (output.bloom())
        {
            if (countMatching(source, SmelterRecipes::isCharcoal) < SmelterRecipes.CHARCOAL_PER_BLOOM)
            {
                return;
            }
        }
        else if (fluid == null
                   || !FurnaceFuel.canBurn(cap.pool(), meltTemp, meltDuration, level, be.getItem(FURNACE_FUEL), source))
        {
            return;
        }

        final ItemStack ore = consumeOre(oreType, source);
        if (ore.isEmpty())
        {
            return;
        }
        be.setItem(FURNACE_INPUT, ore);

        if (output.bloom())
        {
            consumeCharcoal(source);
        }
        else
        {
            final ItemStack mold = takeEmptyMold(source);
            if (mold.isEmpty())
            {
                be.setItem(FURNACE_INPUT, ItemStack.EMPTY);
                insert(source, ore); // back out — hand the ore back
                return;
            }
            be.setItem(FURNACE_RESULT, mold);
            cap.setPool(FurnaceFuel.burn(cap.pool(), meltTemp, meltDuration, level, be, FURNACE_FUEL, source));
        }
        cap.setPhase(FurnaceProcess.Phase.MELTING);
        light(be, meltDuration);
    }

    /** Haul the finished casting (filled hot mold or bloom) out of the result slot into the racks; cap → IDLE. */
    private void retrieve(final FurnaceBlockEntity be, final List<IItemHandler> storage)
    {
        final ItemStack result = be.getItem(FURNACE_RESULT);
        if (!result.isEmpty())
        {
            insert(storage, result.copy());
            be.setItem(FURNACE_RESULT, ItemStack.EMPTY);
            ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
        }
        final FurnaceProcess cap = capOf(be);
        if (cap != null)
        {
            cap.setPhase(FurnaceProcess.Phase.IDLE);
        }
        be.setChanged();
    }

    // --- Ready-job / consumption helpers ------------------------------------------------------------------

    /**
     * An ore in {@code storage} with ≥100 mB of a single grade and its requirement met: for a cast metal an
     * empty mold and fuel hot enough for its melt temp; for iron, 2 charcoal. Returns one representative ore.
     */
    private ItemStack findReadyJob(final List<IItemHandler> storage)
    {
        final Map<Item, Integer> mb = new HashMap<>();
        final Map<Item, ItemStack> sample = new HashMap<>();
        int charcoal = 0;
        boolean emptyMold = false;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (stack.isEmpty())
                {
                    continue;
                }
                if (SmelterRecipes.outputFor(stack) != null)
                {
                    mb.merge(stack.getItem(), SmelterRecipes.meltMb(stack) * stack.getCount(), Integer::sum);
                    sample.putIfAbsent(stack.getItem(), stack);
                }
                else if (SmelterRecipes.isCharcoal(stack))
                {
                    charcoal += stack.getCount();
                }
                else if (isEmptyMold(stack))
                {
                    emptyMold = true;
                }
            }
        }
        for (final Map.Entry<Item, Integer> e : mb.entrySet())
        {
            if (e.getValue() < SmelterRecipes.UNITS_PER_OUTPUT)
            {
                continue;
            }
            final ItemStack ore = sample.get(e.getKey());
            final Output out = SmelterRecipes.outputFor(ore);
            if (out == null)
            {
                continue;
            }
            if (out.bloom())
            {
                if (charcoal >= SmelterRecipes.CHARCOAL_PER_BLOOM)
                {
                    return ore;
                }
            }
            else if (emptyMold && FurnaceFuel.hasFuelHotEnough(meltTempOf(ore), ai.buildingLevel(), storage))
            {
                return ore;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * An ore grade that has rack stock to contribute <i>and</i> reaches ≥100 mB once combined with what the
     * worker already carries — so a partially-held melt gets completed instead of ignored.
     */
    private ItemStack gradeToComplete(final IItemHandler inv, final List<IItemHandler> racks)
    {
        final Map<Item, Integer> rackMb = new HashMap<>();
        final Map<Item, ItemStack> sample = new HashMap<>();
        for (final IItemHandler h : racks)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (!stack.isEmpty() && SmelterRecipes.outputFor(stack) != null)
                {
                    rackMb.merge(stack.getItem(), SmelterRecipes.meltMb(stack) * stack.getCount(), Integer::sum);
                    sample.putIfAbsent(stack.getItem(), stack);
                }
            }
        }
        for (final Map.Entry<Item, Integer> e : rackMb.entrySet())
        {
            final ItemStack grade = sample.get(e.getKey());
            final int carriedMb = countMatching(List.of(inv), s -> ItemHandlerHelper.canItemStacksStack(s, grade)) * SmelterRecipes.meltMb(grade);
            if (e.getValue() + carriedMb >= SmelterRecipes.UNITS_PER_OUTPUT)
            {
                return grade;
            }
        }
        return ItemStack.EMPTY;
    }

    private static float meltTempOf(final ItemStack ore)
    {
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        return recipe != null ? recipe.getTemperature() : 1000f;
    }

    /** Consume 100 mB of one ore grade from storage, combined into a single stack for the furnace; empty if short. */
    private ItemStack consumeOre(final ItemStack oreType, final List<IItemHandler> storage)
    {
        int need = SmelterRecipes.UNITS_PER_OUTPUT;
        ItemStack collected = ItemStack.EMPTY;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots() && need > 0; slot++)
            {
                while (need > 0 && ItemHandlerHelper.canItemStacksStack(h.getStackInSlot(slot), oreType))
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
                    need -= SmelterRecipes.meltMb(taken);
                }
            }
        }
        return need <= 0 ? collected : ItemStack.EMPTY;
    }

    private void consumeCharcoal(final List<IItemHandler> storage)
    {
        int need = SmelterRecipes.CHARCOAL_PER_BLOOM;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots() && need > 0; slot++)
            {
                while (need > 0 && SmelterRecipes.isCharcoal(h.getStackInSlot(slot)))
                {
                    if (h.extractItem(slot, 1, false).isEmpty())
                    {
                        break;
                    }
                    need--;
                }
            }
        }
    }

    /** Take one empty mold from storage (the mold the ingot is cast in). */
    private ItemStack takeEmptyMold(final List<IItemHandler> storage)
    {
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                if (isEmptyMold(h.getStackInSlot(slot)))
                {
                    return h.extractItem(slot, 1, false);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** A casting mold with no metal in it yet. */
    private static boolean isEmptyMold(final ItemStack stack)
    {
        if (!SmelterRecipes.isMold(stack))
        {
            return false;
        }
        final IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return handler == null || handler.getFluidInTank(0).isEmpty();
    }

    // --- Staging / storage plumbing -----------------------------------------------------------------------

    /** Move matching items from the racks into the carried inventory until it holds {@code target} of them. */
    private void topUp(final List<IItemHandler> racks, final IItemHandler inv, final Predicate<ItemStack> match, final int target)
    {
        final int have = countMatching(List.of(inv), match);
        if (have < target)
        {
            moveToInventory(racks, inv, match, target - have);
        }
    }

    /** Move up to {@code max} matching items from the racks into the carried inventory; returns whether any moved. */
    private static boolean moveToInventory(final List<IItemHandler> from, final IItemHandler to, final Predicate<ItemStack> match, final int max)
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
        return moved > 0;
    }

    private static int countMatching(final List<IItemHandler> handlers, final Predicate<ItemStack> match)
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

    /** How many whole 100 mB melts of ore the carried inventory holds — counted <b>per grade</b> (a melt is single-grade). */
    private int inventoryMelts(final IItemHandler inv)
    {
        final Map<Item, Integer> mb = new HashMap<>();
        for (int slot = 0; slot < inv.getSlots(); slot++)
        {
            final ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && SmelterRecipes.outputFor(stack) != null)
            {
                mb.merge(stack.getItem(), SmelterRecipes.meltMb(stack) * stack.getCount(), Integer::sum);
            }
        }
        int melts = 0;
        for (final int gradeMb : mb.values())
        {
            melts += gradeMb / SmelterRecipes.UNITS_PER_OUTPUT;
        }
        return melts;
    }

    private void insert(final List<IItemHandler> storage, ItemStack stack)
    {
        for (final IItemHandler h : storage)
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return;
            }
        }
        if (!stack.isEmpty() && ai.worker() != null)
        {
            ai.worker().spawnAtLocation(stack);
        }
    }

    /** The building's racks — the colony storage the worker stages out of and ships results into. */
    private List<IItemHandler> racks()
    {
        final IBuilding building = ai.building();
        final Level world = ai.world();
        if (building == null || world == null)
        {
            return List.of();
        }
        final List<IItemHandler> handlers = new ArrayList<>();
        for (final BlockPos pos : building.getContainers())
        {
            final var be = world.getBlockEntity(pos);
            if (be != null)
            {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    /** The batch the worker is carrying — furnaces are loaded from here, not straight from the racks. */
    private List<IItemHandler> inventory()
    {
        return ai.worker() == null ? List.of() : List.of(ai.worker().getInventoryCitizen());
    }

    /** Inventory + racks — for deciding what the colony can make as a whole (carried batch plus stock). */
    private List<IItemHandler> combined()
    {
        final List<IItemHandler> all = new ArrayList<>(inventory());
        all.addAll(racks());
        return all;
    }

    // --- Furnace helpers ----------------------------------------------------------------------------------

    private int idleFurnaceCount()
    {
        int idle = 0;
        for (final BlockPos furnace : ai.furnaces())
        {
            final FurnaceProcess cap = capOf(furnaceAt(furnace));
            if (cap != null && cap.phase() == FurnaceProcess.Phase.IDLE)
            {
                idle++;
            }
        }
        return idle;
    }

    private FurnaceBlockEntity furnaceAt(final BlockPos furnace)
    {
        final Level world = ai.world();
        return world != null && world.getBlockEntity(furnace) instanceof FurnaceBlockEntity be ? be : null;
    }

    private FurnaceProcess capOf(final FurnaceBlockEntity be)
    {
        return FurnaceProcessCapability.get(be);
    }

    /**
     * Light the furnace for {@code ticks}: set its {@code litTime}/{@code litDuration} (the vanilla BE counts it
     * down and extinguishes the flame when it expires — which is also our melt timer) and flip the LIT
     * blockstate so the flame renders immediately.
     */
    private void light(final FurnaceBlockEntity be, final int ticks)
    {
        final FurnaceBlockEntityAccessor accessor = (FurnaceBlockEntityAccessor) be;
        accessor.setLitTime(ticks);
        accessor.setLitDuration(Math.max(1, ticks));
        be.setChanged();

        final Level world = ai.world();
        if (world == null)
        {
            return;
        }
        final BlockState state = world.getBlockState(be.getBlockPos());
        if (state.getBlock() instanceof AbstractFurnaceBlock
              && state.hasProperty(AbstractFurnaceBlock.LIT)
              && !state.getValue(AbstractFurnaceBlock.LIT))
        {
            world.setBlockAndUpdate(be.getBlockPos(), state.setValue(AbstractFurnaceBlock.LIT, true));
        }
    }

    /**
     * Melt duration from TFC's heat model for the <b>fixed {@value SmelterRecipes#UNITS_PER_OUTPUT} mB</b> we
     * smelt per operation: ~{@code meltTemp × heat_capacity / 3} ticks, using the <i>output</i> ingot/bloom's
     * heat capacity (an ingot is exactly 100 mB) so the time reflects the metal amount, not the input ore grade.
     * Shortened by the smelter's Strength.
     */
    private int duration(final Output output, final float meltTemp)
    {
        final IHeat heat = HeatCapability.get(new ItemStack(SmelterRecipes.item(output.result())));
        final float heatCapacity = heat != null ? heat.getHeatCapacity() : 0f;
        final int base = heatCapacity > 0f ? Math.round(meltTemp * heatCapacity / 3.0f) : DEFAULT_DURATION;
        final int strength = ai.worker().getCitizenData().getCitizenSkillHandler().getLevel(Skill.Strength);
        return Math.max(MIN_DURATION, base - strength * 2);
    }
}
