package com.mctfc.smelter;

import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.smelter.SmelterRecipes.Output;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TFC-flavoured replacement for the MineColonies Smelter's furnace loop (see docs/compat-features.md).
 * Collapses TFC metallurgy ({@link SmelterRecipes}): ~{@value SmelterRecipes#UNITS_PER_OUTPUT} mB of one
 * metal's ore → one casting (an ingot, delivered in its mold) or, for iron, a raw bloom. No fuel — the
 * furnace is lit for show only.
 *
 * <p><b>Authentic to TFC:</b>
 * <ul>
 *   <li><b>Melt time</b> comes from TFC's own heat model — an item heats by {@code ~3/heat_capacity} °C/tick,
 *       so melting takes about {@code meltTemp × heat_capacity / 3} ticks (read live from
 *       {@link HeatCapability} + the ore's {@link HeatingRecipe}); shortened by the smelter's Strength.</li>
 *   <li><b>The output is the supplied mold, filled</b> with the cast ingot, heated to just below its melting
 *       point (so it has just solidified — extractable but glowing). Iron instead yields a hot raw bloom (no
 *       mold). The player extracts/finishes it exactly as in TFC.</li>
 * </ul>
 *
 * <p><b>Storage</b> = the building's racks ({@code getContainers()}). <b>Furnaces</b> run in parallel: each is
 * loaded with one melt (inputs consumed up front) and cooks on its own timer; the worker collects each as it
 * finishes, so a 5-furnace hut runs five melts at once.
 */
public class SmelterBehavior implements FurnaceBehavior
{
    private static final int    MIN_DURATION   = 40;
    private static final int    DEFAULT_DURATION = 200;
    private static final int    WORK_TICK_RATE = 10;
    private static final double  XP_PER_OUTPUT = 5.0;
    /** Heat the just-cast ingot/bloom to this many °C below its melting point — hot, but solidified. */
    private static final float   BELOW_MELT     = 1.0f;

    /** A melt in progress at one furnace. {@code mold} is the empty mold reserved for a cast ingot (null for iron). */
    private record FurnaceJob(Output output, long doneAt, ItemStack mold, Fluid fluid, float meltTemp) {}

    private enum State implements IAIState
    {
        WORK;

        @Override
        public boolean isOkayToEat()
        {
            return false;
        }
    }

    private final FurnaceWorker ai;
    private final FurnaceFuel fuel = new FurnaceFuel();
    private final Map<BlockPos, FurnaceJob> furnaceJobs = new HashMap<>();

    private BlockPos target;
    private boolean collect;
    private Output loadJob;

    public SmelterBehavior(final FurnaceWorker ai)
    {
        this.ai = ai;
    }

    @Override
    public Collection<AITarget<IAIState>> targets()
    {
        return List.of(new AITarget<IAIState>(State.WORK, this::work, WORK_TICK_RATE));
    }

    @Override
    public IAIState startWorking()
    {
        if (!ai.gotoBuilding())
        {
            return ai.state();
        }

        final List<BlockPos> furnaces = ai.furnaces();
        final Level world = ai.world();
        if (furnaces.isEmpty() || world == null)
        {
            ai.delay(40);
            return AIWorkerState.START_WORKING;
        }
        final long now = world.getGameTime();

        for (final BlockPos furnace : furnaces)
        {
            final FurnaceJob job = furnaceJobs.get(furnace);
            if (job != null && now >= job.doneAt())
            {
                this.target = furnace;
                this.collect = true;
                return State.WORK;
            }
        }

        final List<IItemHandler> storage = storage();
        final Output ready = findReadyJob(storage);
        if (ready != null)
        {
            for (final BlockPos furnace : furnaces)
            {
                if (!furnaceJobs.containsKey(furnace))
                {
                    this.target = furnace;
                    this.collect = false;
                    this.loadJob = ready;
                    return State.WORK;
                }
            }
        }

        ai.delay(60);
        return AIWorkerState.START_WORKING;
    }

    private IAIState work()
    {
        if (target == null)
        {
            return AIWorkerState.START_WORKING;
        }
        if (!ai.gotoWorkPos(target))
        {
            return State.WORK;
        }

        final List<IItemHandler> storage = storage();
        if (collect)
        {
            final FurnaceJob job = furnaceJobs.remove(target);
            if (job != null)
            {
                produce(job, storage);
                ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
            }
            setLit(target, false);
        }
        else if (loadJob != null)
        {
            final FurnaceJob job = load(loadJob, storage);
            if (job != null)
            {
                furnaceJobs.put(target, job);
                setLit(target, true);
            }
        }

        this.target = null;
        this.loadJob = null;
        return AIWorkerState.START_WORKING;
    }

    /**
     * A metal in stock with ≥100 mB of ore and its requirement: for a cast metal an empty mold <i>and</i> fuel
     * hot enough for its melt temp; for iron, 2 charcoal (the bloomery).
     */
    private Output findReadyJob(final List<IItemHandler> storage)
    {
        final Map<Output, Integer> mb = new HashMap<>();
        final Map<Output, ItemStack> sample = new HashMap<>();
        int charcoal = 0;
        boolean emptyMold = false;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                final Output out = SmelterRecipes.outputFor(stack);
                if (out != null)
                {
                    mb.merge(out, SmelterRecipes.meltMb(stack) * stack.getCount(), Integer::sum);
                    sample.putIfAbsent(out, stack);
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
        for (final Map.Entry<Output, Integer> e : mb.entrySet())
        {
            final Output out = e.getKey();
            if (e.getValue() < SmelterRecipes.UNITS_PER_OUTPUT)
            {
                continue;
            }
            if (out.bloom())
            {
                if (charcoal >= SmelterRecipes.CHARCOAL_PER_BLOOM)
                {
                    return out;
                }
            }
            else if (emptyMold
                       && fuel.hasFuelHotEnough(meltTempOf(sample.get(out)), ai.buildingLevel(), storage))
            {
                return out;
            }
        }
        return null;
    }

    private static float meltTempOf(final ItemStack ore)
    {
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        return recipe != null ? recipe.getTemperature() : 1000f;
    }

    /** Reserve a furnace's melt: read TFC's heat model for the duration + fluid, consume the inputs up front. */
    private FurnaceJob load(final Output job, final List<IItemHandler> storage)
    {
        final ItemStack sample = firstOre(job, storage);
        if (sample.isEmpty())
        {
            return null;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(sample);
        final float meltTemp = recipe != null ? recipe.getTemperature() : 1000f;
        final Fluid fluid = recipe != null && !recipe.getDisplayOutputFluid().isEmpty() ? recipe.getDisplayOutputFluid().getFluid() : null;
        final int meltDuration = duration(sample, meltTemp);
        final int level = ai.buildingLevel();

        // Cast metals burn fuel hot enough for the melt (checked before consuming anything); iron's heat comes
        // from its bloomery charcoal instead.
        if (!job.bloom() && !fuel.canBurn(target, meltTemp, meltDuration, level, storage))
        {
            return null;
        }

        if (!consumeOre(job, storage))
        {
            return null;
        }

        ItemStack mold = ItemStack.EMPTY;
        if (job.bloom())
        {
            consumeCharcoal(storage);
        }
        else
        {
            mold = takeEmptyMold(storage);
            if (mold.isEmpty() || fluid == null)
            {
                return null; // lost the mold / fluid between picking and loading; ore already spent (rare)
            }
            fuel.burn(target, meltTemp, meltDuration, level, storage);
        }
        return new FurnaceJob(job, ai.world().getGameTime() + meltDuration, mold, fluid, meltTemp);
    }

    /** Produce the finished casting: the supplied mold filled with the hot ingot, or a hot iron bloom. */
    private void produce(final FurnaceJob job, final List<IItemHandler> storage)
    {
        final float temp = Math.max(1f, job.meltTemp() - BELOW_MELT);
        if (job.output().bloom())
        {
            final ItemStack bloom = new ItemStack(SmelterRecipes.item(job.output().result()));
            HeatCapability.setTemperature(bloom, temp);
            insert(storage, bloom);
            return;
        }

        // Cast metal: fill the reserved mold and heat it to just below melting.
        final ItemStack mold = job.mold().copy();
        final IFluidHandlerItem handler = mold.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler != null && job.fluid() != null)
        {
            handler.fill(new FluidStack(job.fluid(), SmelterRecipes.UNITS_PER_OUTPUT), IFluidHandler.FluidAction.EXECUTE);
            final ItemStack filled = handler.getContainer();
            HeatCapability.setTemperature(filled, temp);
            insert(storage, filled);
        }
        else
        {
            insert(storage, mold); // fallback: return the mold unfilled rather than void it
        }
    }

    private ItemStack firstOre(final Output job, final List<IItemHandler> storage)
    {
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (job.equals(SmelterRecipes.outputFor(stack)))
                {
                    return stack.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean consumeOre(final Output job, final List<IItemHandler> storage)
    {
        int need = SmelterRecipes.UNITS_PER_OUTPUT;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots() && need > 0; slot++)
            {
                while (need > 0 && job.equals(SmelterRecipes.outputFor(h.getStackInSlot(slot))))
                {
                    final ItemStack taken = h.extractItem(slot, 1, false);
                    if (taken.isEmpty())
                    {
                        break;
                    }
                    need -= SmelterRecipes.meltMb(taken);
                }
            }
        }
        return need <= 0;
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

    /** Take one empty mold from storage (the supplied mold the ingot is cast in). */
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

    private List<IItemHandler> storage()
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
            final BlockEntity be = world.getBlockEntity(pos);
            if (be != null)
            {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    private void setLit(final BlockPos furnace, final boolean lit)
    {
        final Level world = ai.world();
        if (world == null)
        {
            return;
        }
        final BlockState state = world.getBlockState(furnace);
        if (state.getBlock() instanceof AbstractFurnaceBlock
              && state.hasProperty(AbstractFurnaceBlock.LIT)
              && state.getValue(AbstractFurnaceBlock.LIT) != lit)
        {
            world.setBlockAndUpdate(furnace, state.setValue(AbstractFurnaceBlock.LIT, lit));
        }
    }

    /**
     * Melt duration from TFC's heat model: ~{@code meltTemp × heat_capacity / 3} ticks (the item heats by
     * {@code ~3/heat_capacity} °C per tick), shortened by the smelter's Strength.
     */
    private int duration(final ItemStack ore, final float meltTemp)
    {
        final IHeat heat = HeatCapability.get(ore);
        final float heatCapacity = heat != null ? heat.getHeatCapacity() : 0f;
        final int base = heatCapacity > 0f ? Math.round(meltTemp * heatCapacity / 3.0f) : DEFAULT_DURATION;
        final int strength = ai.worker().getCitizenData().getCitizenSkillHandler().getLevel(Skill.Strength);
        return Math.max(MIN_DURATION, base - strength * 2);
    }
}
