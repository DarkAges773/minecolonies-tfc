package com.mctfc.furnace;

import com.mctfc.cook.CookProcessing;
import com.mctfc.cook.CookRecipes;
import com.mctfc.mixin.FurnaceBlockEntityAccessor;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * The shared "<b>ignite a furnace to TFC-heat the item in its input slot</b>" step — the reusable core of the
 * TFC cooking both the dining-hall {@link com.mctfc.cook.CookBehavior Cook} and the Kitchen
 * {@code Chef} ({@code MixinAbstractEntityAIRequestSmelter}) drive. TFC food has no vanilla furnace recipe, so the
 * furnace never lights or produces on its own; this lights it deliberately: it consumes fuel via the shared
 * temperature-gated {@link FurnaceFuel} pool, stamps the {@link CookProcessing} kind onto the furnace's
 * {@link FurnaceProcess} cap, and drives the vanilla {@code litTime} as the cook timer + flame. When the timer
 * expires {@code MixinAbstractFurnaceBlockEntity} runs {@link CookProcessing} to turn one raw piece into cooked
 * food in the result slot.
 *
 * <p>Stateless. One item is heated per ignition ({@link CookProcessing} cooks a single piece), so a worker that
 * loads several pieces re-ignites for each — throughput comes from parallel furnaces, exactly like a TFC grill's
 * slots. See docs/tfc-furnace-workers.md.
 */
public final class FurnaceHeating
{
    private FurnaceHeating() {}

    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT  = 1;

    private static final int MIN_DURATION     = 60;
    private static final int DEFAULT_DURATION = 200;

    /** Fuel a firepit-style TFC cook may burn — any TFC fuel in TFC's own firepit-fuel tag (woods / peat / sticks). */
    public static final Predicate<ItemStack> COOK_FUEL =
        stack -> FurnaceFuel.isFuel(stack) && stack.is(FurnaceFuelScope.COOK);

    /**
     * Cook duration for one item of {@code raw} from TFC's heat model — {@code cookTemp × heat_capacity / 3} ticks
     * (TFC heats an item by ~{@code 3/heat_capacity} °C/tick), floored at {@value #MIN_DURATION}. Falls back to
     * {@value #DEFAULT_DURATION} when the food carries no heat data. This is the un-skilled base; a worker may
     * shorten it by its relevant skill before calling {@link #ignite}.
     */
    public static int baseDuration(final ItemStack raw)
    {
        final float cookTemp = CookRecipes.cookTemp(raw);
        final IHeat heat = HeatCapability.get(raw);
        final float heatCapacity = heat != null ? heat.getHeatCapacity() : 0f;
        final int base = heatCapacity > 0f ? Math.round(cookTemp * heatCapacity / 3.0f) : DEFAULT_DURATION;
        return Math.max(MIN_DURATION, base);
    }

    /**
     * Ignite {@code be} to cook the raw food already in its input slot for {@code duration} ticks, drawing fuel from
     * the furnace's fuel slot (and optionally {@code source}) via the {@link FurnaceFuel} pool carried on {@code cap}.
     * Stamps the cook {@code kind} + {@code MELTING} phase and lights the {@code litTime}. Returns {@code false}
     * (leaving the furnace cold) when no allowed fuel hot enough for the cook temp is available — call has no effect
     * then. The input stack must already be in the slot; only fuel is consumed here.
     */
    public static boolean ignite(final AbstractFurnaceBlockEntity be, final FurnaceProcess cap, final float cookTemp,
            final int duration, final int level, final Predicate<ItemStack> fuelAllowed, final List<IItemHandler> source)
    {
        if (be.getItem(INPUT_SLOT).isEmpty())
        {
            return false;
        }
        if (!FurnaceFuel.canBurn(cap.pool(), cookTemp, duration, level, fuelAllowed, be.getItem(FUEL_SLOT), source))
        {
            return false;
        }
        cap.setPool(FurnaceFuel.burn(cap.pool(), cookTemp, duration, level, fuelAllowed, be, FUEL_SLOT, source));
        cap.setKind(CookProcessing.KIND);
        cap.setPhase(FurnaceProcess.Phase.MELTING);
        light(be, duration);
        return true;
    }

    /**
     * Ignite a furnace for the raw food in its slot at the un-skilled {@link #baseDuration}, drawing only on the
     * furnace's own fuel slot (no external storage). Used to (re-)light a furnace already loaded by the worker —
     * e.g. the next piece of a multi-item batch, or recovering a batch that stalled when its fuel ran out.
     */
    public static boolean igniteInPlace(final AbstractFurnaceBlockEntity be, final FurnaceProcess cap, final int level,
            final Predicate<ItemStack> fuelAllowed)
    {
        final ItemStack raw = be.getItem(INPUT_SLOT);
        if (raw.isEmpty())
        {
            return false;
        }
        return ignite(be, cap, CookRecipes.cookTemp(raw), baseDuration(raw), level, fuelAllowed, List.of());
    }

    /**
     * Light the furnace for {@code ticks}: set its {@code litTime}/{@code litDuration} (the vanilla BE counts it down
     * unconditionally and extinguishes when it expires — which is our cook timer) and flip the {@code LIT} blockstate
     * so the flame renders immediately.
     */
    public static void light(final AbstractFurnaceBlockEntity be, final int ticks)
    {
        final FurnaceBlockEntityAccessor accessor = (FurnaceBlockEntityAccessor) be;
        accessor.setLitTime(ticks);
        accessor.setLitDuration(Math.max(1, ticks));
        be.setChanged();

        final Level world = be.getLevel();
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
}
