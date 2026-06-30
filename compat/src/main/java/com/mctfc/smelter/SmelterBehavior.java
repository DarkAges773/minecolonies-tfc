package com.mctfc.smelter;

import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.mixin.FurnaceBlockEntityAccessor;
import com.mctfc.smelter.SmelterRecipes.Output;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSmeltery;
import net.minecraft.resources.ResourceLocation;
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

import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;
import static com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE;
import static com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants.REQUESTS_TYPE_SMELTABLE_ORE;
import static com.minecolonies.core.entity.ai.workers.crafting.EntityAIWorkSmelter.ORE_LIST;

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

    /** Per-hut low-water threshold for restocking, exposed in the Settings tab (registered via
     * {@code BuildingSettings} in {@code MineColoniesTFC}); when colony stock of an enabled ore / allowed fuel /
     * charcoal falls to this, the worker requests more. */
    public static final ISettingKey<IntSetting> ORE_THRESHOLD = new SettingKey<>(IntSetting.class, new ResourceLocation("mctfc", "ore_threshold"));
    /** Default for {@link #ORE_THRESHOLD}: 10 = one ingot's worth of small native ore. */
    public static final int ORE_THRESHOLD_DEFAULT = 10;

    /** Per-restock order size (items) for ore/fuel — modest and flat (not per-furnace), so a restock tops up the
     * smeltery without draining the warehouse; consumed stock is simply re-requested. */
    private static final int RESTOCK_BATCH          = 32;
    /** Per-restock order size for charcoal (the iron bloomery catalyst, 2 per bloom — a smaller batch suffices). */
    private static final int CHARCOAL_RESTOCK_BATCH = 16;
    private static final int REQUEST_CHECK_INTERVAL = 100; // ticks between restock checks (throttle)

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
    /** Game-time gate so the restock check (polled every tick via {@link #canGoIdle()}) only runs occasionally. */
    private long nextRequestCheck;
    /** Tokens of our outstanding restock requests, so we don't fire a duplicate while one is still in flight. */
    private IToken<?> oreRequest;
    private IToken<?> fuelRequest;
    private IToken<?> charcoalRequest;

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
        // canGoIdle is polled continuously by CitizenAI in *both* states, so it's our one reliable hook that
        // also runs while the worker is idle (work AI paused) — exactly when we may be out of ore and need to
        // restock. Fire the (throttled, debounced) request check here so the worker still orders materials while
        // waiting/wandering. Then: let CitizenAI run the wander/idle minimal-AI whenever there's no work to do.
        requestMissing();
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
            final FurnaceBlockEntity be = furnaceAt(furnace);
            final FurnaceProcess cap = capOf(be);
            if (cap != null && cap.phase() == FurnaceProcess.Phase.DONE && canDeliver(be.getItem(FURNACE_RESULT)))
            {
                return true; // a finished furnace we have room to unload
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
                if (mold == null || mold.getTemperature() != 0f)
                {
                    continue;
                }
                final CastingRecipe recipe = CastingRecipe.get(mold);
                if (recipe != null && canCarry(recipe.assemble(mold, world.registryAccess())))
                {
                    return true; // a cooled mold whose ingot we have room to carry
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

    /** Top the carried inventory up to the idle furnaces' worth of <b>consumable</b> ore + fuel/charcoal from the
     * racks. Molds are deliberately <i>not</i> staged: a mold is a reusable tool, so it's loaded into a furnace
     * straight from the racks and returned there after casting (cycling racks → furnace → racks), rather than
     * lingering as a stray in the worker's inventory. */
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

        // fuel (allowed by the hut's fuel list), charcoal (the iron bloomery catalyst) — both consumed by melts.
        topUp(racks, to, this::fuelAllowed, idle);
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
                    retrieve(be); // bloom → inventory (dump ships it), filled mold → racks; cap → IDLE
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
            final FurnaceBlockEntity be = furnaceAt(furnace);
            final FurnaceProcess cap = capOf(be);
            if (be == null || cap == null)
            {
                tended.add(furnace);
                continue;
            }
            final boolean done = cap.phase() == FurnaceProcess.Phase.DONE && canDeliver(be.getItem(FURNACE_RESULT));
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
                if (mold == null || mold.getTemperature() != 0f)
                {
                    continue;
                }
                final CastingRecipe recipe = CastingRecipe.get(mold);
                if (recipe == null)
                {
                    continue;
                }
                final ItemStack result = recipe.assemble(mold, world.registryAccess());
                if (!canCarry(result))
                {
                    continue; // inventory full — leave the cooled mold here, cast it later
                }
                final ItemStack filled = h.extractItem(slot, 1, false);
                final MoldLike taken = MoldLike.get(filled);
                if (taken != null)
                {
                    taken.drainIgnoringTemperature(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                }
                deliver(result); // ingot is the deliverable — carry it; the dump ships it to storage
                ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
                ai.countAction();
                if (world.getRandom().nextFloat() >= recipe.getBreakChance())
                {
                    insert(storage, filled); // mold survives (now empty) — back to the racks to be re-staged
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
                   || !FurnaceFuel.canBurn(cap.pool(), meltTemp, meltDuration, level, this::fuelAllowed, be.getItem(FURNACE_FUEL), source))
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
            final ItemStack mold = takeEmptyMold(racks()); // molds live in the racks, not the carried batch
            if (mold.isEmpty())
            {
                be.setItem(FURNACE_INPUT, ItemStack.EMPTY);
                insert(source, ore); // back out — hand the ore back
                return;
            }
            be.setItem(FURNACE_RESULT, mold);
            cap.setPool(FurnaceFuel.burn(cap.pool(), meltTemp, meltDuration, level, this::fuelAllowed, be, FURNACE_FUEL, source));
        }
        cap.setKind(SmelterProcessing.KIND); // so the furnace finishes it with the casting completer, not the cook's
        cap.setPhase(FurnaceProcess.Phase.MELTING);
        light(be, meltDuration);
    }

    /**
     * Empty the furnace's result slot and flip the cap to IDLE. A <b>bloom</b> is a finished deliverable, so it's
     * carried out into the worker's inventory (vanilla-style — MineColonies' dump cycle then ships it to the
     * building/warehouse, see §8). A <b>filled hot mold</b> is intermediate: it goes to the racks to cool, where
     * {@code MOLD_UNLOAD} later casts the ingot. Leaves the result in place (stays DONE) when there's no room.
     */
    private void retrieve(final FurnaceBlockEntity be)
    {
        final ItemStack result = be.getItem(FURNACE_RESULT);
        if (!result.isEmpty())
        {
            if (SmelterRecipes.isMold(result))
            {
                if (!canStowRacks(result))
                {
                    return; // racks full — leave the filled mold in the furnace and retry when space frees
                }
                insertRacks(result.copy());
            }
            else
            {
                if (!canCarry(result))
                {
                    return; // inventory full — leave the bloom; the dump will free space, then retry
                }
                deliver(result.copy());
                ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
                ai.countAction();
            }
            be.setItem(FURNACE_RESULT, ItemStack.EMPTY);
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
     * An ore in {@code storage} with ≥100 mB of a single grade and its requirement met: for a cast metal fuel hot
     * enough for its melt temp <i>and</i> an empty mold available in the racks (molds aren't carried — see
     * {@link #stageBatch()}); for iron, 2 charcoal. Returns one representative ore.
     */
    private ItemStack findReadyJob(final List<IItemHandler> storage)
    {
        final Map<Item, Integer> mb = new HashMap<>();
        final Map<Item, ItemStack> sample = new HashMap<>();
        int charcoal = 0;
        final boolean emptyMold = countMatching(racks(), SmelterBehavior::isEmptyMold) > 0;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (stack.isEmpty())
                {
                    continue;
                }
                if (oreEnabled(stack))
                {
                    mb.merge(stack.getItem(), SmelterRecipes.meltMb(stack) * stack.getCount(), Integer::sum);
                    sample.putIfAbsent(stack.getItem(), stack);
                }
                else if (SmelterRecipes.isCharcoal(stack))
                {
                    charcoal += stack.getCount();
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
            else if (emptyMold && FurnaceFuel.hasFuelHotEnough(meltTempOf(ore), ai.buildingLevel(), this::fuelAllowed, storage))
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
                if (oreEnabled(stack))
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
            if (oreEnabled(stack))
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

    /**
     * General placement for non-deliverables (an empty mold being recycled, ore handed back): the given storage
     * first, then the worker's inventory, then the ground as an absolute last resort.
     */
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
        for (final IItemHandler h : inventory())
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return;
            }
        }
        if (!stack.isEmpty() && ai.worker() != null)
        {
            ai.worker().spawnAtLocation(stack); // absolute last resort (both racks and inventory full)
        }
    }

    /** Carry a finished deliverable (ingot/bloom) in the worker's inventory; the dump cycle ships it onward. */
    private void deliver(ItemStack stack)
    {
        for (final IItemHandler h : inventory())
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return;
            }
        }
        insert(racks(), stack); // gated by canCarry; on a race, fall back so nothing is lost
    }

    /** Put an intermediate (a filled hot mold cooling toward casting) into the racks. */
    private void insertRacks(ItemStack stack)
    {
        for (final IItemHandler h : racks())
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return;
            }
        }
        insert(inventory(), stack); // gated by canStowRacks; on a race, fall back so nothing is lost
    }

    /** Whether the finished result can be placed where it belongs: a bloom carried, a filled mold to the racks. */
    private boolean canDeliver(final ItemStack result)
    {
        return !result.isEmpty() && (SmelterRecipes.isMold(result) ? canStowRacks(result) : canCarry(result));
    }

    /** Whether {@code stack} fully fits into the worker's inventory (where deliverables are carried). */
    private boolean canCarry(final ItemStack stack)
    {
        return fits(inventory(), stack);
    }

    /** Whether {@code stack} fully fits into the building's racks (where intermediates/empty molds live). */
    private boolean canStowRacks(final ItemStack stack)
    {
        return fits(racks(), stack);
    }

    private static boolean fits(final List<IItemHandler> handlers, final ItemStack stack)
    {
        if (hasEmptySlot(handlers))
        {
            return true; // an empty slot takes any single output — fast path
        }
        ItemStack remaining = stack.copy();
        for (final IItemHandler h : handlers)
        {
            remaining = ItemHandlerHelper.insertItem(h, remaining, true);
            if (remaining.isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEmptySlot(final List<IItemHandler> handlers)
    {
        for (final IItemHandler h : handlers)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                if (h.getStackInSlot(slot).isEmpty())
                {
                    return true;
                }
            }
        }
        return false;
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

    // --- Hut list filters (the "ores" block-list and "fuel" allow-list the player configures) ---------------

    /**
     * A smelter ore the player hasn't excluded via the hut's <b>ores</b> list (a block-list — empty by default,
     * so everything smelts until the player unchecks specific TFC ores; see docs §8).
     */
    private boolean oreEnabled(final ItemStack stack)
    {
        if (SmelterRecipes.outputFor(stack) == null)
        {
            return false;
        }
        final ItemListModule blocked = listModule(ORE_LIST);
        return blocked == null || !blocked.isItemInList(new ItemStorage(stack));
    }

    /**
     * A TFC fuel the player permits via the hut's <b>fuel</b> list (an allow-list). The list only constrains once
     * it actually names a TFC fuel; the vanilla default (coal/charcoal) that maps to no TFC fuel must not starve
     * the smelter, so we fall back to "any TFC fuel" until the player curates the list with real TFC fuels.
     */
    private boolean fuelAllowed(final ItemStack stack)
    {
        if (!FurnaceFuel.isFuel(stack))
        {
            return false;
        }
        final ItemListModule allowed = listModule(FUEL_LIST);
        if (allowed == null)
        {
            return true;
        }
        final List<ItemStorage> list = allowed.getList();
        boolean constrains = false;
        for (final ItemStorage entry : list)
        {
            if (FurnaceFuel.isFuel(entry.getItemStack()))
            {
                constrains = true;
                break;
            }
        }
        return !constrains || list.contains(new ItemStorage(stack));
    }

    private ItemListModule listModule(final String id)
    {
        final IBuilding building = ai.building();
        return building == null ? null : building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(id));
    }

    // --- Auto-requesting (low-water restock; docs §8 stage 4) ---------------------------------------------

    /**
     * Restock check: when the colony's stock of an enabled ore / allowed fuel / charcoal has fallen to the hut's
     * {@link #ORE_THRESHOLD} setting, fire one <b>debounced</b> colony request to top it up. Called from
     * {@link #canGoIdle()} (so it runs even while the worker idles waiting on a delivery) and throttled to keep
     * that continuous polling cheap. The ore request carries the smeltery's {@code MIN} ("warehousemin") setting
     * as its left-over, so couriers keep that many in the warehouse reserve — exactly as the vanilla smelter does.
     */
    private void requestMissing()
    {
        final Level world = ai.world();
        if (world == null || !(ai.building() instanceof BuildingSmeltery) || ai.worker() == null
              || ai.worker().getCitizenData() == null)
        {
            return;
        }
        if (world.getGameTime() < nextRequestCheck)
        {
            return;
        }
        nextRequestCheck = world.getGameTime() + REQUEST_CHECK_INTERVAL;

        final int threshold = settingValue(ORE_THRESHOLD, ORE_THRESHOLD_DEFAULT);
        final int reserve = settingValue(BuildingSmeltery.MIN, 0);
        // A modest, fixed per-restock batch — NOT scaled by furnace count. We top up to here, consume it, then
        // re-request, so the smeltery never hoovers the whole warehouse into its racks in one order. The MIN
        // ("warehousemin") setting rides along as the request's left-over, so the warehouse keeps that reserve.
        final List<IItemHandler> stock = combined();

        if (countMatching(stock, this::oreEnabled) <= threshold && !isOpen(oreRequest))
        {
            final List<ItemStack> ores = enabledOreStacks();
            if (!ores.isEmpty())
            {
                oreRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(ores, REQUESTS_TYPE_SMELTABLE_ORE, RESTOCK_BATCH, 1, reserve));
            }
        }
        if (countMatching(stock, this::fuelAllowed) <= threshold && !isOpen(fuelRequest))
        {
            final List<ItemStack> fuels = allowedFuelStacks();
            if (!fuels.isEmpty())
            {
                fuelRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(fuels, REQUESTS_TYPE_BURNABLE, RESTOCK_BATCH, 1, reserve));
            }
        }
        if (countMatching(stock, SmelterRecipes::isCharcoal) <= threshold && !isOpen(charcoalRequest))
        {
            final Item charcoal = SmelterRecipes.item(SmelterRecipes.CHARCOAL);
            if (charcoal != null)
            {
                charcoalRequest = ai.worker().getCitizenData().createRequestAsync(new Stack(new ItemStack(charcoal), CHARCOAL_RESTOCK_BATCH, 1));
            }
        }
    }

    /** A smeltery IntSetting's value, or {@code fallback} if the building isn't a smeltery / the setting is absent. */
    private int settingValue(final ISettingKey<IntSetting> key, final int fallback)
    {
        if (!(ai.building() instanceof BuildingSmeltery building))
        {
            return fallback;
        }
        final IntSetting setting = building.getSetting(key);
        return setting == null ? fallback : Math.max(0, setting.getValue());
    }

    /** One stack of each enabled TFC ore (not blocked via the hut's ores list) — the ore request's candidate set. */
    private List<ItemStack> enabledOreStacks()
    {
        final List<ItemStack> ores = new ArrayList<>();
        for (final ItemStack ore : SmelterRecipes.oreStacks())
        {
            if (oreEnabled(ore))
            {
                ores.add(ore);
            }
        }
        return ores;
    }

    /** The fuels the player allows via the hut's fuel list — the fuel request's candidate set. */
    private List<ItemStack> allowedFuelStacks()
    {
        final ItemListModule fuelList = listModule(FUEL_LIST);
        final List<ItemStack> fuels = new ArrayList<>();
        if (fuelList != null)
        {
            for (final ItemStorage entry : fuelList.getList())
            {
                fuels.add(entry.getItemStack());
            }
        }
        return fuels;
    }

    /**
     * Whether {@code token} is one of the worker's still-outstanding requests — the debounce. Matches by token id
     * against {@link IBuilding#getOpenRequests} (which keeps a request listed through its in-flight/being-delivered
     * states, only dropping it once resolved), so we never queue a duplicate while one is on its way. (This is the
     * fix for the earlier spam: a display-string filter missed requests once they were assigned to a resolver.)
     */
    private boolean isOpen(final IToken<?> token)
    {
        if (token == null || ai.worker() == null || ai.worker().getCitizenData() == null)
        {
            return false;
        }
        for (final IRequest<?> req : ai.building().getOpenRequests(ai.worker().getCitizenData().getId()))
        {
            if (token.equals(req.getId()))
            {
                return true;
            }
        }
        return false;
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
