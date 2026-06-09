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
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
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
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * TFC-flavoured replacement for the MineColonies Smelter's furnace loop (see docs/tfc-furnace-workers.md).
 * Collapses TFC metallurgy ({@link SmelterRecipes}): ~{@value SmelterRecipes#UNITS_PER_OUTPUT} mB of one ore →
 * one casting (an ingot, delivered in its mold) or, for iron, a raw bloom.
 *
 * <p><b>The work lives in the furnace itself.</b> Ore goes in the furnace's <b>input</b> slot, fuel in its
 * <b>fuel</b> slot, the mold in its <b>result</b> slot (filled in place on completion) — so the contents drop if
 * the furnace is broken and are visible in the furnace GUI — and the melt timer/flame is the vanilla
 * {@code litTime} (a depleting flame = progress). Only the carried fuel pool rides in the furnace's
 * {@link FurnaceProcess} capability. The worker keeps no per-furnace job map; a furnace is <i>idle</i> when its
 * input slot is empty, <i>melting</i> while {@code litTime > 0}, and <i>done</i> once it has ore but
 * {@code litTime == 0}. This persists exactly across reload (it's all furnace BE NBT) and lets a 5-furnace hut
 * run five melts at once.
 *
 * <p>Safe against the vanilla furnace: TFC ore has no vanilla smelting recipe, so the BE never auto-smelts our
 * ore or auto-burns our fuel — it only counts {@code litTime} down.
 *
 * <p><b>Authentic to TFC:</b> melt time from the heat model ({@code ~meltTemp × heat_capacity / 3} ticks,
 * shortened by Strength); fuel temperature-gated (must clear the metal's melt temp after the hut's level bonus)
 * and duration-pooled for longevity ({@link FurnaceFuel}); the output is the supplied mold filled and heated to
 * just below melting, or a hot iron bloom (bloomery, 2 charcoal, no mold).
 */
public class SmelterBehavior implements FurnaceBehavior
{
    private static final int INPUT  = 0;
    private static final int FUEL   = 1;
    private static final int RESULT = 2;

    private static final int    MIN_DURATION     = 40;
    private static final int    DEFAULT_DURATION = 200;
    private static final int    WORK_TICK_RATE   = 10;
    private static final double XP_PER_OUTPUT     = 5.0;
    /** How much of each material the worker stages in its own inventory per gather trip. */
    private static final int    ORE_BATCH        = 64;
    private static final int    MOLD_BATCH       = 16;
    private static final int    FUEL_BATCH       = 64;

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

    private BlockPos target;
    private boolean collect;
    private ItemStack loadOre = ItemStack.EMPTY;

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
        final List<BlockPos> furnaces = ai.furnaces();
        final Level world = ai.world();
        if (furnaces.isEmpty() || world == null)
        {
            if (!ai.gotoBuilding())
            {
                return ai.state();
            }
            ai.delay(40);
            return AIWorkerState.START_WORKING;
        }

        // 1) haul out any finished furnace — walk straight to it, no detour back to the hut.
        for (final BlockPos furnace : furnaces)
        {
            final FurnaceProcess cap = capOf(furnaceAt(furnace));
            if (cap != null && cap.phase() == FurnaceProcess.Phase.DONE)
            {
                this.target = furnace;
                this.collect = true;
                return State.WORK;
            }
        }

        // 2) load an idle furnace from the batch already in hand — again straight to the furnace.
        final ItemStack ready = findReadyJob(inventory());
        if (!ready.isEmpty())
        {
            for (final BlockPos furnace : furnaces)
            {
                final FurnaceProcess cap = capOf(furnaceAt(furnace));
                if (cap != null && cap.phase() == FurnaceProcess.Phase.IDLE)
                {
                    this.target = furnace;
                    this.collect = false;
                    this.loadOre = ready;
                    return State.WORK;
                }
            }
            // Carrying loadable materials but every furnace is busy — wait here, don't trek back to the hut.
            ai.delay(40);
            return AIWorkerState.START_WORKING;
        }

        // 3) out of materials in hand — only now return to the hut to stage a fresh batch from the racks.
        if (!ai.gotoBuilding())
        {
            return ai.state();
        }
        if (stageBatch())
        {
            ai.delay(20);
            return AIWorkerState.START_WORKING;
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

        final FurnaceBlockEntity be = furnaceAt(target);
        if (be != null)
        {
            if (collect)
            {
                retrieve(be, racks());
            }
            else if (!loadOre.isEmpty())
            {
                load(be, loadOre, inventory());
            }
        }

        this.target = null;
        this.loadOre = ItemStack.EMPTY;
        return AIWorkerState.START_WORKING;
    }

    /**
     * An ore in stock with ≥100 mB of a <i>single</i> grade and its requirement met: for a cast metal an empty
     * mold <i>and</i> fuel hot enough for its melt temp; for iron, 2 charcoal (the bloomery). Returns one
     * representative ore of that grade, or empty.
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

    private static float meltTempOf(final ItemStack ore)
    {
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        return recipe != null ? recipe.getTemperature() : 1000f;
    }

    /**
     * Load a furnace: read TFC's heat model for the melt time, then move the inputs into the furnace's own
     * slots (ore → input, mold → result, fuel into the fuel slot from the racks) and light it. Nothing is
     * consumed unless the whole operation can proceed.
     */
    private void load(final FurnaceBlockEntity be, final ItemStack oreType, final List<IItemHandler> storage)
    {
        final Output output = SmelterRecipes.outputFor(oreType);
        if (output == null)
        {
            return;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(oreType);
        final float meltTemp = recipe != null ? recipe.getTemperature() : 1000f;
        final Fluid fluid = recipe != null && !recipe.getDisplayOutputFluid().isEmpty() ? recipe.getDisplayOutputFluid().getFluid() : null;
        final int meltDuration = duration(oreType, meltTemp);
        final int level = ai.buildingLevel();
        final FurnaceProcess cap = capOf(be);
        if (cap == null)
        {
            return;
        }

        // Gate before consuming anything.
        if (output.bloom())
        {
            if (countCharcoal(storage) < SmelterRecipes.CHARCOAL_PER_BLOOM)
            {
                return;
            }
        }
        else if (fluid == null
                   || !FurnaceFuel.canBurn(cap.pool(), meltTemp, meltDuration, level, be.getItem(FUEL), storage))
        {
            return;
        }

        final ItemStack ore = consumeOre(oreType, storage);
        if (ore.isEmpty())
        {
            return;
        }
        be.setItem(INPUT, ore);

        if (output.bloom())
        {
            consumeCharcoal(storage);
        }
        else
        {
            final ItemStack mold = takeEmptyMold(storage);
            if (mold.isEmpty())
            {
                be.setItem(INPUT, ItemStack.EMPTY); // back out: hand the ore back below
                insert(storage, ore);
                return;
            }
            be.setItem(RESULT, mold);
            cap.setPool(FurnaceFuel.burn(cap.pool(), meltTemp, meltDuration, level, be, FUEL, storage));
        }
        cap.setPhase(FurnaceProcess.Phase.MELTING);
        light(be, meltDuration);
    }

    /** Haul the finished casting (filled mold or hot bloom) out of the furnace's result slot into the racks. */
    private void retrieve(final FurnaceBlockEntity be, final List<IItemHandler> storage)
    {
        final ItemStack result = be.getItem(RESULT);
        if (!result.isEmpty())
        {
            insert(storage, result.copy());
            be.setItem(RESULT, ItemStack.EMPTY);
            ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
        }
        final FurnaceProcess cap = capOf(be);
        if (cap != null)
        {
            cap.setPhase(FurnaceProcess.Phase.IDLE);
        }
        be.setChanged();
    }

    /** Consume 100 mB of one ore grade from storage, combined into a single stack to place in the furnace; empty if short. */
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

    private int countCharcoal(final List<IItemHandler> storage)
    {
        int count = 0;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                if (SmelterRecipes.isCharcoal(h.getStackInSlot(slot)))
                {
                    count += h.getStackInSlot(slot).getCount();
                }
            }
        }
        return count;
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

    /** The building's racks — the colony storage the worker stages batches out of and ships results into. */
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

    /** Inventory first, then racks — used to decide what the colony can make as a whole before staging. */
    private List<IItemHandler> combined()
    {
        final List<IItemHandler> all = new ArrayList<>(inventory());
        all.addAll(racks());
        return all;
    }

    /**
     * Pull a batch of one makeable metal's materials (ore + mold/fuel, or ore + charcoal for iron) from the
     * racks into the worker's inventory, topping up what it's already carrying. Returns whether anything moved.
     */
    private boolean stageBatch()
    {
        final ItemStack grade = findReadyJob(combined());
        final List<IItemHandler> inv = inventory();
        if (grade.isEmpty() || inv.isEmpty())
        {
            return false;
        }
        final IItemHandler to = inv.get(0);
        final List<IItemHandler> racks = racks();
        final Output output = SmelterRecipes.outputFor(grade);
        boolean moved = false;

        final int haveOre = countMatching(to, s -> ItemHandlerHelper.canItemStacksStack(s, grade));
        moved |= moveToInventory(racks, to, s -> ItemHandlerHelper.canItemStacksStack(s, grade), ORE_BATCH - haveOre);

        if (output != null && output.bloom())
        {
            moved |= moveToInventory(racks, to, SmelterRecipes::isCharcoal, FUEL_BATCH);
        }
        else
        {
            if (countMatching(to, SmelterBehavior::isEmptyMold) == 0)
            {
                moved |= moveToInventory(racks, to, SmelterBehavior::isEmptyMold, MOLD_BATCH);
            }
            final float meltTemp = meltTempOf(grade);
            if (!FurnaceFuel.hasFuelHotEnough(meltTemp, ai.buildingLevel(), inv))
            {
                moved |= moveToInventory(racks, to, s -> FurnaceFuel.isHotEnough(s, meltTemp, ai.buildingLevel()), FUEL_BATCH);
            }
        }
        return moved;
    }

    /** Move up to {@code max} matching items from the racks into the carried inventory. */
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

    private static int countMatching(final IItemHandler handler, final Predicate<ItemStack> match)
    {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && match.test(stack))
            {
                count += stack.getCount();
            }
        }
        return count;
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
        final BlockState state = world.getBlockState(target);
        if (state.getBlock() instanceof AbstractFurnaceBlock
              && state.hasProperty(AbstractFurnaceBlock.LIT)
              && !state.getValue(AbstractFurnaceBlock.LIT))
        {
            world.setBlockAndUpdate(target, state.setValue(AbstractFurnaceBlock.LIT, true));
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
