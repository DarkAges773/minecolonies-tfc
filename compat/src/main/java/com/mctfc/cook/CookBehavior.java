package com.mctfc.cook;

import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.mixin.FurnaceBlockEntityAccessor;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;

/**
 * TFC-flavoured replacement for the MineColonies <b>Cook</b>'s vanilla-furnace cooking (see
 * docs/tfc-furnace-workers.md §6). The vanilla cook turns a vanilla-smeltable raw food into its furnace result;
 * TFC food has no vanilla furnace recipe, so the vanilla cook never cooks it. Instead this cooks raw TFC food into
 * cooked food via TFC's {@code heating} recipes ({@link CookRecipes}, preserving decay through {@code copy_food}),
 * gated to the dishes on the restaurant menu — exactly as the vanilla cook only cooks raw foods whose result is on
 * the menu.
 *
 * <p>The Cook also <b>serves food</b> to citizens/players ({@code EntityAIWorkCook#checkForImportantJobs}); that is
 * untouched. Because the dispatcher replaces the whole {@code startWorking} loop, {@link #startWorking()} runs the
 * Cook's own serving check first (via {@link FurnaceWorker#checkImportantJobs()}) and only cooks when there's
 * nothing to serve.
 *
 * <p>Simpler than the smelter (no molds/casting): a two-stage cycle —
 * <ol>
 *   <li><b>{@code BATCH_STAGING}</b> — at the hut, top the carried inventory up to the idle furnaces' worth of
 *       menu-cookable raw food + fuel pulled from the racks.</li>
 *   <li><b>{@code TEND_FURNACES}</b> — one sweep: each finished furnace's cooked food is hauled into the worker's
 *       inventory (the deliverable the dump ships to the racks) and, if raw food is in hand, the furnace is
 *       reloaded; each idle furnace is loaded (raw food → input, fuel through the fuel slot; {@code litTime} lit,
 *       cap → {@code MELTING}, kind {@code cook}).</li>
 * </ol>
 * The furnace finishes a cook itself when its {@code litTime} burns out ({@code MixinAbstractFurnaceBlockEntity} →
 * {@link CookProcessing}). Fuel is the shared {@link FurnaceFuel} at the food's cook temperature (~200&nbsp;°C), so
 * cheap fuels (logs/peat) qualify but never reach metal-melting temperatures.
 */
public class CookBehavior implements FurnaceBehavior
{
    private static final int FURNACE_INPUT  = 0;
    private static final int FURNACE_FUEL   = 1;
    private static final int FURNACE_RESULT = 2;

    private static final int    MIN_DURATION     = 60;
    private static final int    DEFAULT_DURATION = 200;
    private static final int    WORK_TICK_RATE   = 10;
    private static final double XP_PER_OUTPUT    = 2.0;
    /** Items cooked per furnace load. TFC cooks food <b>one piece at a time</b> (a furnace/firepit heats a single
     * item), so each furnace cooks one item per {@code litTime} cycle; throughput comes from running the hut's
     * furnaces in parallel (like a TFC grill's slots), not from cooking a whole stack at once. */
    private static final int    COOK_BATCH       = 1;
    /** Raw food carried per idle furnace — a small buffer so the worker reloads each furnace several times from hand
     * before trekking back to re-stage (it still cooks one item per furnace cycle). */
    private static final int    STAGE_BUFFER     = 8;

    private enum State implements IAIState
    {
        BATCH_STAGING, TEND_FURNACES;

        @Override
        public boolean isOkayToEat()
        {
            return true; // these are between-action waits; let the cook eat if hungry
        }
    }

    private final FurnaceWorker ai;
    /** Furnaces already handled in the current TEND sweep (so each is visited once). */
    private final Set<BlockPos> tended = new HashSet<>();
    /** The furnace currently being walked to / tended. */
    private BlockPos target;

    public CookBehavior(final FurnaceWorker ai)
    {
        this.ai = ai;
    }

    @Override
    public Collection<AITarget<IAIState>> targets()
    {
        return List.of(
          new AITarget<IAIState>(State.BATCH_STAGING, this::stage, WORK_TICK_RATE),
          new AITarget<IAIState>(State.TEND_FURNACES, this::tend, WORK_TICK_RATE));
    }

    @Override
    public IAIState startWorking()
    {
        // Preserve the dining hall's serving: run the Cook's own important-jobs check (serve citizens/players,
        // fetch food to serve) first; only run the TFC cooking loop when it has nothing more pressing to do.
        final IAIState serving = ai.checkImportantJobs();
        if (serving != AIWorkerState.START_WORKING)
        {
            return serving;
        }
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    // Note: canGoIdle is deliberately left at its default (false) — the cook keeps its work AI running so it can
    // serve continuously, exactly like the vanilla cook (which never goes idle). The dispatcher then keeps the
    // working render texture on.

    /** Whether the cook has furnace work: a finished furnace to unload, or an idle furnace with a cookable dish. */
    private boolean hasWork()
    {
        final List<BlockPos> furnaces = ai.furnaces();
        if (ai.world() == null || furnaces.isEmpty())
        {
            return false;
        }
        for (final BlockPos furnace : furnaces)
        {
            if (isUnloadable(furnaceAt(furnace)))
            {
                return true; // a finished furnace we have room to unload (or a degenerate empty one to reset)
            }
        }
        return idleFurnaceCount() > 0 && !findReadyJob(combined()).isEmpty();
    }

    /** A DONE furnace whose cooked result we can carry now (or whose result is empty — a degenerate state to reset). */
    private boolean isUnloadable(final FurnaceBlockEntity be)
    {
        final FurnaceProcess cap = capOf(be);
        if (cap == null || cap.phase() != FurnaceProcess.Phase.DONE)
        {
            return false;
        }
        final ItemStack result = be.getItem(FURNACE_RESULT);
        return result.isEmpty() || canCarry(result);
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

    /** Top the carried inventory up with fuel + a small buffer of menu-cookable raw food from the racks. The raw
     * carry is capped to {@code idle × STAGE_BUFFER} so the worker reloads furnaces several times from hand before
     * re-staging, without hoarding perishable food. */
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
        topUp(racks, to, this::fuelAllowed, idle);
        topUp(racks, to, this::cookable, idle * STAGE_BUFFER);
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
                    retrieve(be); // cooked food → inventory (dump ships it); cap → IDLE
                }
                if (cap.phase() == FurnaceProcess.Phase.IDLE)
                {
                    final ItemStack raw = findReadyJob(inventory());
                    if (!raw.isEmpty())
                    {
                        load(be, raw, inventory()); // cap → MELTING
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
            final boolean loadable = cap.phase() == FurnaceProcess.Phase.IDLE && !findReadyJob(inventory()).isEmpty();
            if (isUnloadable(be) || loadable)
            {
                target = furnace;
                return State.TEND_FURNACES;
            }
            tended.add(furnace); // cooking, or idle with nothing to load
        }

        tended.clear();
        // Back to START_WORKING after each sweep (not looping our own stages like the smelter) so the Cook re-runs
        // its serving check between sweeps — food-serving stays as responsive as the vanilla cook. startWorking then
        // decides the next stage (serve / stage / idle). START_WORKING isn't IDLE, so the render texture won't flicker.
        return AIWorkerState.START_WORKING;
    }

    // --- Loading / unloading a single furnace -------------------------------------------------------------

    /**
     * Load a furnace from the carried inventory: {@value #COOK_BATCH} raw food → input, fuel through the fuel slot;
     * stamp the cook kind, light it, and flip the cap to MELTING. The cook time is one item's worth (so cooking is
     * one piece at a time, not a whole stack in a single item's time). Nothing is consumed unless fuel hot enough is
     * available.
     */
    private void load(final FurnaceBlockEntity be, final ItemStack rawType, final List<IItemHandler> source)
    {
        final Level world = ai.world();
        final FurnaceProcess cap = capOf(be);
        if (world == null || cap == null)
        {
            return;
        }
        final float cookTemp = CookRecipes.cookTemp(rawType);
        final int duration = cookDuration(rawType);
        final int level = ai.buildingLevel();
        if (!FurnaceFuel.canBurn(cap.pool(), cookTemp, duration, level, this::fuelAllowed, be.getItem(FURNACE_FUEL), source))
        {
            return; // no fuel hot enough for ~200 °C — can't cook yet
        }
        final ItemStack raw = consume(rawType, source, COOK_BATCH);
        if (raw.isEmpty())
        {
            return;
        }
        be.setItem(FURNACE_INPUT, raw);
        cap.setPool(FurnaceFuel.burn(cap.pool(), cookTemp, duration, level, this::fuelAllowed, be, FURNACE_FUEL, source));
        cap.setKind(CookProcessing.KIND);
        cap.setPhase(FurnaceProcess.Phase.MELTING);
        light(be, duration);
    }

    /**
     * Empty a finished furnace's result slot and flip the cap to IDLE. The cooked food is a deliverable, so it's
     * carried into the worker's inventory (MineColonies' dump cycle ships it to the building/warehouse, where the
     * colony food-preservation trait protects it). Leaves the food in place (stays DONE) when the inventory is full.
     */
    private void retrieve(final FurnaceBlockEntity be)
    {
        final ItemStack result = be.getItem(FURNACE_RESULT);
        if (!result.isEmpty())
        {
            if (!canCarry(result))
            {
                return; // inventory full — leave the cooked food; the dump frees space, then retry
            }
            deliver(result.copy());
            be.setItem(FURNACE_RESULT, ItemStack.EMPTY);
            ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
            ai.countAction();
        }
        final FurnaceProcess cap = capOf(be);
        if (cap != null)
        {
            cap.setPhase(FurnaceProcess.Phase.IDLE);
        }
        be.setChanged();
    }

    // --- Ready-job / consumption helpers ------------------------------------------------------------------

    /** A menu-cookable raw food in {@code storage} with fuel hot enough for its cook temp available; empty if none. */
    private ItemStack findReadyJob(final List<IItemHandler> storage)
    {
        if (ai.world() == null)
        {
            return ItemStack.EMPTY;
        }
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (cookable(stack)
                      && FurnaceFuel.hasFuelHotEnough(CookRecipes.cookTemp(stack), ai.buildingLevel(), this::fuelAllowed, storage))
                {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Pull up to {@code max} items matching {@code type} from storage, combined into a single stack. */
    private ItemStack consume(final ItemStack type, final List<IItemHandler> storage, final int max)
    {
        ItemStack collected = ItemStack.EMPTY;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots() && collected.getCount() < max; slot++)
            {
                while (collected.getCount() < max && ItemHandlerHelper.canItemStacksStack(h.getStackInSlot(slot), type))
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

    /** A raw food the cook should cook: it has a TFC heating recipe to a cooked food whose result is on the menu. */
    private boolean cookable(final ItemStack stack)
    {
        final Level world = ai.world();
        if (world == null || !CookRecipes.isCookable(stack))
        {
            return false;
        }
        return onMenu(CookRecipes.cookedResult(stack, world.registryAccess()));
    }

    /** Whether the restaurant menu lists this cooked food (matched by item, ignoring TFC food-data NBT). */
    private boolean onMenu(final ItemStack cooked)
    {
        if (cooked.isEmpty())
        {
            return false;
        }
        final RestaurantMenuModule menu = menuModule();
        if (menu == null)
        {
            return false;
        }
        for (final ItemStorage entry : menu.getMenu())
        {
            if (entry.getItem() == cooked.getItem())
            {
                return true;
            }
        }
        return false;
    }

    private RestaurantMenuModule menuModule()
    {
        final IBuilding building = ai.building();
        return building == null ? null : building.getFirstModuleOccurance(RestaurantMenuModule.class);
    }

    // --- Hut fuel allow-list (the player-configured "fuel" list) -------------------------------------------

    /**
     * A TFC fuel the player permits via the hut's <b>fuel</b> list (an allow-list). The list only constrains once it
     * actually names a TFC fuel, so the vanilla default that maps to no TFC fuel can't starve the cook (mirrors the
     * smelter's {@code fuelAllowed}).
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

    /** Move up to {@code max} matching items from the racks into the carried inventory. */
    private static void moveToInventory(final List<IItemHandler> from, final IItemHandler to, final Predicate<ItemStack> match, final int max)
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

    /** Carry a finished deliverable (cooked food) in the worker's inventory; the dump cycle ships it onward. */
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
        for (final IItemHandler h : racks()) // gated by canCarry; on a race, fall back so nothing is lost
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

    /** Whether {@code stack} fully fits into the worker's inventory (where deliverables are carried). */
    private boolean canCarry(final ItemStack stack)
    {
        return fits(inventory(), stack);
    }

    private static boolean fits(final List<IItemHandler> handlers, final ItemStack stack)
    {
        if (stack.isEmpty() || hasEmptySlot(handlers))
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

    /** The building's racks — the colony storage the worker stages out of and ships cooked food into. */
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

    /** Inventory + racks — for deciding what the colony can cook as a whole (carried batch plus stock). */
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
     * Light the furnace for {@code ticks}: set its {@code litTime}/{@code litDuration} (the vanilla BE counts it down
     * and extinguishes when it expires — which is also our cook timer) and flip the LIT blockstate so the flame
     * renders immediately.
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
     * Cook duration from TFC's heat model — ~{@code cookTemp × heat_capacity / 3} ticks (TFC heats an item by
     * ~{@code 3/heat_capacity} °C/tick) for the raw food being heated — floored at {@value #MIN_DURATION} and
     * shortened by the cook's Adaptability. Falls back to {@value #DEFAULT_DURATION} when the food has no heat data.
     */
    private int cookDuration(final ItemStack raw)
    {
        final float cookTemp = CookRecipes.cookTemp(raw);
        final IHeat heat = HeatCapability.get(raw);
        final float heatCapacity = heat != null ? heat.getHeatCapacity() : 0f;
        final int base = heatCapacity > 0f ? Math.round(cookTemp * heatCapacity / 3.0f) : DEFAULT_DURATION;
        final int adaptability = ai.worker().getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability);
        return Math.max(MIN_DURATION, base - adaptability * 2);
    }
}
