package com.mctfc.cook;

import com.mctfc.forge.ForgeController;
import com.mctfc.forge.ForgeTender;
import com.mctfc.forge.HeatForgeBlockEntity;
import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceFuelScope;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.food.FoodTemplates;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;
import static com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants.REQUESTS_TYPE_FOOD;

/**
 * TFC-flavoured replacement for the MineColonies <b>Cook</b>'s vanilla-furnace cooking (see
 * docs/tfc-furnace-workers.md §6, docs/tfc-forge-multiblock.md §13). The vanilla cook turns a vanilla-smeltable raw food
 * into its furnace result; TFC food has no vanilla furnace recipe. Instead the hut's <b>heat-forge</b> multiblocks cook
 * raw TFC food into cooked food via TFC's {@code heating} recipes ({@link CookRecipes}, decay preserved through
 * {@code copy_food}), gated to the dishes on the restaurant menu.
 *
 * <p>The forge <b>self-processes</b>, so this AI is a pure <b>tend</b> loop (via {@link ForgeTender}): it stocks fuel +
 * menu-cookable raw food, lights each controller, loads its free heat slots, and drains cooked food — no ignition
 * timing or completion bookkeeping (the block does that). It also preserves the dining hall's <b>serving</b> duties
 * ({@code EntityAIWorkCook#checkForImportantJobs}, run first via {@link FurnaceWorker#checkImportantJobs()}) and the
 * demand-scaled raw-ingredient auto-request.
 */
public class CookBehavior implements FurnaceBehavior, ForgeTender.Context, ForgeTender.Policy
{
    private static final int    WORK_TICK_RATE = 10;
    private static final double XP_PER_OUTPUT  = 2.0;
    /** Raw food staged per free heat slot — a small buffer so the worker reloads from hand several times before
     * re-staging (each forge cooks one item per position at a time, throughput from parallel positions). */
    private static final int    INPUT_PER_SLOT = 8;
    private static final int    REQUEST_CHECK_INTERVAL = 100;
    private static final int    MAX_REQUEST    = 16;

    private enum State implements IAIState
    {
        BATCH_STAGING, TEND_CONTROLLERS;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    private final FurnaceWorker ai;
    private final ForgeTender   tender;
    /** Controllers already visited in the current TEND sweep. */
    private final Set<BlockPos> tended = new HashSet<>();
    private BlockPos target;
    private long     nextRequestCheck;
    private IToken<?> foodRequest;

    public CookBehavior(final FurnaceWorker ai)
    {
        this.ai = ai;
        this.tender = new ForgeTender(this, this);
    }

    @Override
    public Collection<AITarget<IAIState>> targets()
    {
        return List.of(
          new AITarget<IAIState>(State.BATCH_STAGING, this::stage, WORK_TICK_RATE),
          new AITarget<IAIState>(State.TEND_CONTROLLERS, this::tend, WORK_TICK_RATE));
    }

    @Override
    public IAIState startWorking()
    {
        requestMissing();
        final IAIState serving = ai.checkImportantJobs();
        if (serving != AIWorkerState.START_WORKING)
        {
            return serving;
        }
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    /**
     * Whether any controller has cooked food to drain, a cold forge with cookable raw stranded in it that we can
     * relight, or a free heat slot with a menu-cookable dish makeable now.
     */
    private boolean hasWork()
    {
        final List<ForgeController> controllers = tender.resolve(ai.controllers());
        if (controllers.isEmpty())
        {
            return false;
        }
        for (final ForgeController c : controllers)
        {
            if (c.hasFinished() || needsRelight(c))
            {
                return true;
            }
            if (canLoadNow(c, combined()))
            {
                return true;
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
        tender.stage(tender.resolve(ai.controllers()));
        return State.TEND_CONTROLLERS;
    }

    // --- Stage 2: TEND_CONTROLLERS (one visit per controller) ---------------------------------------------

    private IAIState tend()
    {
        final List<BlockPos> controllers = ai.controllers();
        if (controllers.isEmpty() || ai.world() == null)
        {
            tended.clear();
            return AIWorkerState.IDLE;
        }

        if (target != null)
        {
            if (!ai.gotoWorkPos(target))
            {
                return State.TEND_CONTROLLERS; // still walking
            }
            final ForgeController controller = controllerAt(target);
            if (controller != null)
            {
                for (final ItemStack cooked : tender.tend(controller))
                {
                    if (deliver(cooked))
                    {
                        ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
                        ai.countAction();
                    }
                }
            }
            tended.add(target);
            target = null;
            return State.TEND_CONTROLLERS;
        }

        for (final BlockPos pos : controllers)
        {
            if (tended.contains(pos))
            {
                continue;
            }
            final ForgeController c = controllerAt(pos);
            if (c == null)
            {
                tended.add(pos);
                continue;
            }
            if (c.hasFinished() || canLoadNow(c, inventory()) || needsRelight(c) || c.isLit())
            {
                target = pos; // visit lit forges too, so keep-warm / extinguish + refuel run
                return State.TEND_CONTROLLERS;
            }
            tended.add(pos);
        }

        tended.clear();
        // Back to START_WORKING after each sweep so the Cook re-runs its serving check between sweeps.
        return AIWorkerState.START_WORKING;
    }

    private ForgeController controllerAt(final BlockPos pos)
    {
        final Level world = ai.world();
        return world != null && world.getBlockEntity(pos) instanceof HeatForgeBlockEntity be ? be : null;
    }

    /**
     * Whether {@code c} has a free heat slot and a menu-cookable raw in {@code storage} it can cook <b>now</b> — fuel
     * already in its column <b>or</b> obtainable from {@code storage}. Consulting the column (not just storage) stops the
     * Cook idling next to a forge whose 5-slot column is stocked while the racks are momentarily empty (review COOK-2).
     */
    private boolean canLoadNow(final ForgeController c, final List<IItemHandler> storage)
    {
        if (ai.world() == null || c.freeHeatSlots() <= 0)
        {
            return false;
        }
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (accepts(stack) && fuelReady(c, CookRecipes.cookTemp(stack), storage))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code c} can reach {@code temp} on fuel already in its column, or on hot-enough fuel in {@code storage}. */
    private boolean fuelReady(final ForgeController c, final float temp, final List<IItemHandler> storage)
    {
        return c.canReach(temp) || FurnaceFuel.hasFuelHotEnough(temp, ai.buildingLevel(), this::fuelAllowed, storage);
    }

    /**
     * Whether {@code c} is a forge that went cold with a still-cookable raw stranded in its heat slots (its fuel ran out
     * mid-cook) and we have fuel to relight it. Such a forge won't restart itself — the worker must revisit to relight,
     * so it counts as work; without this the Cook goes IDLE and the raw is stranded forever, no matter how much
     * fuel/food later reaches the racks (review COOK-1).
     */
    private boolean needsRelight(final ForgeController c)
    {
        if (c.isLit())
        {
            return false;
        }
        final List<IItemHandler> stock = combined();
        for (final ItemStack heat : c.heatItems())
        {
            if (accepts(heat) && fuelReady(c, CookRecipes.cookTemp(heat), stock))
            {
                return true;
            }
        }
        return false;
    }

    // --- ForgeTender.Policy -------------------------------------------------------------------------------

    /** A raw food the cook should cook: a TFC heating recipe to a cooked food whose result is on the restaurant menu. */
    @Override
    public boolean accepts(final ItemStack stack)
    {
        final Level world = ai.world();
        if (world == null || !CookRecipes.isCookable(stack))
        {
            return false;
        }
        return onMenu(CookRecipes.cookedResult(stack, world.registryAccess()));
    }

    @Override
    public float requiredTemp(final ItemStack stack)
    {
        return CookRecipes.cookTemp(stack);
    }

    /**
     * A TFC fuel the player permits via the hut's <b>fuel</b> list. The cook only burns TFC <b>firepit</b> fuels
     * ({@link FurnaceFuelScope#COOK} — woods/peat/sticks), matching a TFC firepit, so even the empty-list fallback never
     * lets it burn coals. Within that, the list only constrains once it names a firepit fuel.
     */
    @Override
    public boolean fuelAllowed(final ItemStack stack)
    {
        if (!FurnaceFuel.isFuel(stack) || !stack.is(FurnaceFuelScope.COOK))
        {
            return false;
        }
        final ItemListModule allowed = listModule(FUEL_LIST);
        if (allowed == null)
        {
            return true;
        }
        final List<ItemStorage> list = allowed.getList();
        // Only an IN-SCOPE fuel entry constrains us. The native list is seeded with out-of-scope [coal, charcoal],
        // which must NOT flip this on — otherwise it would reject the firepit fuels the Cook actually burns (which
        // aren't in that seed). Once the player lists a real firepit fuel, restrict to the listed ones.
        boolean constrains = false;
        for (final ItemStorage entry : list)
        {
            if (FurnaceFuel.isFuel(entry.getItemStack()) && entry.getItemStack().is(FurnaceFuelScope.COOK))
            {
                constrains = true;
                break;
            }
        }
        return !constrains || list.contains(new ItemStorage(stack));
    }

    @Override
    public int inputPerSlot()
    {
        return INPUT_PER_SLOT;
    }

    // --- ForgeTender.Context ------------------------------------------------------------------------------

    @Override
    public Level world()
    {
        return ai.world();
    }

    @Override
    public int buildingLevel()
    {
        return ai.buildingLevel();
    }

    @Override
    public List<IItemHandler> inventory()
    {
        return ai.worker() == null ? List.of() : List.of(ai.worker().getInventoryCitizen());
    }

    @Override
    public List<IItemHandler> racks()
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

    private List<IItemHandler> combined()
    {
        final List<IItemHandler> all = new ArrayList<>(inventory());
        all.addAll(racks());
        return all;
    }

    // --- Output delivery ----------------------------------------------------------------------------------

    /** Carry cooked food in the worker's inventory (the dump ships it to the racks); returns whether it was placed. */
    private boolean deliver(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }
        for (final IItemHandler h : inventory())
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return true;
            }
        }
        for (final IItemHandler h : racks())
        {
            stack = ItemHandlerHelper.insertItem(h, stack, false);
            if (stack.isEmpty())
            {
                return true;
            }
        }
        if (!stack.isEmpty() && ai.worker() != null)
        {
            ai.worker().spawnAtLocation(stack);
        }
        return true;
    }

    // --- Menu gating --------------------------------------------------------------------------------------

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

    private ItemListModule listModule(final String id)
    {
        final IBuilding building = ai.building();
        return building == null ? null : building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(id));
    }

    // --- Auto-requesting raw ingredients (the cook keeps itself fed) ---------------------------------------

    /**
     * Order the <b>raw</b> ingredients the menu needs. The vanilla restaurant menu's raw lookup is a vanilla-furnace
     * recipe — absent for TFC food — so nothing requests the raw the cook actually cooks. We fill that gap: for each
     * menu dish, reverse-look-up its raw food ({@link CookRecipes#rawForDishes}) and, if colony stock of (cooked + raw)
     * is below the demand-scaled target, request the raw — one debounced {@link StackList}, batch-capped so a perishable
     * pile is never ordered. Called from {@link #startWorking()} every work cycle and throttled here.
     */
    private void requestMissing()
    {
        final Level world = ai.world();
        if (world == null || ai.worker() == null || ai.worker().getCitizenData() == null)
        {
            return;
        }
        if (world.getGameTime() < nextRequestCheck)
        {
            return;
        }
        nextRequestCheck = world.getGameTime() + REQUEST_CHECK_INTERVAL;
        if (isOpen(foodRequest))
        {
            return;
        }

        final RestaurantMenuModule menu = menuModule();
        if (menu == null)
        {
            return;
        }
        final Set<Item> dishes = new HashSet<>();
        for (final ItemStorage entry : menu.getMenu())
        {
            dishes.add(entry.getItem());
        }
        final Map<Item, List<ItemStack>> rawByDish = CookRecipes.rawForDishes(world, dishes);
        if (rawByDish.isEmpty())
        {
            return;
        }

        final int target = Math.max(1, menu.getExpectedStock());
        final List<IItemHandler> stock = combined();
        final List<ItemStack> request = new ArrayList<>();
        int shortfall = 0;
        for (final Map.Entry<Item, List<ItemStack>> entry : rawByDish.entrySet())
        {
            final Item dish = entry.getKey();
            final List<ItemStack> raws = entry.getValue();
            final Set<Item> rawItems = new HashSet<>();
            for (final ItemStack raw : raws)
            {
                rawItems.add(raw.getItem());
            }
            final int have = ForgeTender.countMatching(stock, s -> s.getItem() == dish || rawItems.contains(s.getItem()));
            if (have < target)
            {
                shortfall += target - have;
                for (final ItemStack raw : raws)
                {
                    request.add(FoodTemplates.nonDecaying(raw));
                }
            }
        }
        if (request.isEmpty())
        {
            return;
        }
        final int batch = Math.min(shortfall, MAX_REQUEST);
        foodRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(request, REQUESTS_TYPE_FOOD, batch, 1));
    }

    /** Whether {@code token} is still one of the worker's outstanding requests — the debounce. */
    private boolean isOpen(final IToken<?> token)
    {
        if (token == null || ai.building() == null || ai.worker() == null || ai.worker().getCitizenData() == null)
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
}
