package com.mctfc.smelter;

import com.mctfc.Config;
import com.mctfc.bloomery.BloomeryUserModule;
import com.mctfc.bloomery.TfcBloomery;
import com.mctfc.forge.ForgeController;
import com.mctfc.forge.ForgeTender;
import com.mctfc.forge.HeatForgeBlockEntity;
import net.dries007.tfc.common.blockentities.BloomBlockEntity;
import net.dries007.tfc.common.blockentities.BloomeryBlockEntity;
import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceFuelScope;
import com.mctfc.furnace.FurnaceWorker;
import com.mctfc.smelter.SmelterRecipes.Output;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSmeltery;
import net.dries007.tfc.common.capabilities.MoldLike;
import net.dries007.tfc.common.recipes.CastingRecipe;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;
import static com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants.REQUESTS_TYPE_BURNABLE;
import static com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants.REQUESTS_TYPE_SMELTABLE_ORE;
import static com.minecolonies.core.entity.ai.workers.crafting.EntityAIWorkSmelter.ORE_LIST;

/**
 * TFC-flavoured replacement for the MineColonies <b>Smelter</b> (see docs/tfc-forge-multiblock.md §8, §13). The hut's
 * <b>heat-forge</b> multiblocks melt ore into liquid metal and pour it into <b>molds</b> the worker seats — output to
 * 100&nbsp;mB first, then overflow; metal beyond both spills (TFC-authentic). The forge self-processes, so this AI is a
 * <b>tend</b> loop (via {@link ForgeTender}): stock fuel + ore + empty molds, seat molds, light, load matching ore
 * sized to the free mold capacity, drain full molds — then cast the cooled ones with TFC's own casting recipe.
 *
 * <p><b>Cast metals only</b> (copper/tin/bismuth/zinc/silver/gold/nickel). Iron is deferred (it produces a bloom, not a
 * pourable fluid), so {@link #accepts} excludes iron ore — it must never load one (it'd jam a position).
 *
 * <p>Three stages: {@code BATCH_STAGING} (stock fuel/ore/molds), {@code TEND_CONTROLLERS} (one visit per multiblock),
 * {@code MOLD_UNLOAD} (cast cooled full molds → ingots).
 */
public class SmelterBehavior implements FurnaceBehavior, ForgeTender.Context, ForgeTender.Policy
{
    private static final int    WORK_TICK_RATE = 10;
    private static final double XP_PER_OUTPUT  = 5.0;
    /** Ore staged per free heat slot — a few single ores (each ~10–35 mB) so a mold fills over successive melts. */
    private static final int    INPUT_PER_SLOT = 4;
    /** Empty molds staged per free heat slot (an output + an overflow per position). */
    private static final int    MOLDS_PER_SLOT = 2;

    private static final int    RESTOCK_BATCH          = 32;
    private static final int    REQUEST_CHECK_INTERVAL = 100;

    /** Per-hut low-water threshold for restocking, exposed in the Settings tab (registered in {@code MineColoniesTFC}). */
    public static final ISettingKey<IntSetting> ORE_THRESHOLD = new SettingKey<>(IntSetting.class, new ResourceLocation("mctfc", "ore_threshold"));
    /** Default for {@link #ORE_THRESHOLD}: 10 = one ingot's worth of small native ore. */
    public static final int ORE_THRESHOLD_DEFAULT = 10;

    /** Iron ore / charcoal staged into the worker's inventory per staging trip for bloomery loading (topUp caps by space). */
    private static final int BLOOMERY_ORE_STAGE      = 32;
    private static final int BLOOMERY_CHARCOAL_STAGE = 16;
    /** Restock iron ore below this many mB of cast iron in colony stock; keep this much charcoal. */
    private static final int BLOOMERY_ORE_LOW_MB     = 200;
    private static final int BLOOMERY_CHARCOAL_LOW   = 8;

    private enum State implements IAIState
    {
        BATCH_STAGING, TEND_CONTROLLERS, MOLD_UNLOAD, BLOOMERY_TEND;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    private final FurnaceWorker ai;
    private final ForgeTender   tender;
    private final Set<BlockPos> tended = new HashSet<>();
    private BlockPos target;
    private long     nextRequestCheck;
    private IToken<?> oreRequest;
    private IToken<?> fuelRequest;

    // Bloomery tending (iron path — docs/tfc-bloomery-smelter.md).
    private final Set<BlockPos> bloomeriesTended = new HashSet<>();
    private BlockPos bloomeryTarget;
    private long     nextBloomeryCheck;
    private boolean  bloomeryWorkPending;
    private IToken<?> ironRequest;
    private IToken<?> charcoalRequest;

    public SmelterBehavior(final FurnaceWorker ai)
    {
        this.ai = ai;
        this.tender = new ForgeTender(this, this);
    }

    @Override
    public Collection<AITarget<IAIState>> targets()
    {
        return List.of(
          new AITarget<IAIState>(State.BATCH_STAGING, this::stage, WORK_TICK_RATE),
          new AITarget<IAIState>(State.TEND_CONTROLLERS, this::tend, WORK_TICK_RATE),
          new AITarget<IAIState>(State.MOLD_UNLOAD, this::moldUnload, WORK_TICK_RATE),
          new AITarget<IAIState>(State.BLOOMERY_TEND, this::bloomeryTend, WORK_TICK_RATE));
    }

    @Override
    public IAIState startWorking()
    {
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    @Override
    public boolean canGoIdle()
    {
        requestMissing();
        return !hasWork();
    }

    /** Whether any controller has a full mold to drain, an idle slot with a makeable melt, or a cooled mold to cast. */
    private boolean hasWork()
    {
        final List<ForgeController> controllers = tender.resolve(ai.controllers());
        if (!controllers.isEmpty())
        {
            for (final ForgeController c : controllers)
            {
                if (c.hasFinished() || needsRelight(c))
                {
                    return true;
                }
            }
            if (tender.totalFreeHeatSlots(controllers) > 0 && canMakeMelt(combined()))
            {
                return true;
            }
        }
        return hasCooledMold(racks()) || bloomeryWork();
    }

    // --- Stage 1: BATCH_STAGING ---------------------------------------------------------------------------

    private IAIState stage()
    {
        if (!ai.gotoBuilding())
        {
            return ai.state();
        }
        final List<ForgeController> controllers = tender.resolve(ai.controllers());
        tender.stage(controllers); // fuel + ore
        stageMolds(controllers);   // empty molds to seat
        stageBloomeryMaterials();  // iron ore + charcoal for marked bloomeries
        return State.TEND_CONTROLLERS;
    }

    /** Stage iron ore + charcoal from the racks into the worker's inventory for the marked bloomeries (if any). */
    private void stageBloomeryMaterials()
    {
        final BloomeryUserModule module = bloomeryModule();
        if (module == null || module.getBloomeries().isEmpty())
        {
            return;
        }
        final List<IItemHandler> inv = inventory();
        if (inv.isEmpty())
        {
            return;
        }
        ForgeTender.topUp(racks(), inv.get(0), this::usableIronOre, BLOOMERY_ORE_STAGE);
        ForgeTender.topUp(racks(), inv.get(0), TfcBloomery::isCatalyst, BLOOMERY_CHARCOAL_STAGE);
    }

    private void stageMolds(final List<ForgeController> controllers)
    {
        final int free = tender.totalFreeHeatSlots(controllers);
        final List<IItemHandler> inv = inventory();
        if (free <= 0 || inv.isEmpty())
        {
            return;
        }
        ForgeTender.topUp(racks(), inv.get(0), this::isEmptyMold, free * MOLDS_PER_SLOT);
    }

    // --- Stage 2: TEND_CONTROLLERS (one visit per controller) ---------------------------------------------

    private IAIState tend()
    {
        final List<BlockPos> controllers = ai.controllers();
        if (controllers.isEmpty() || ai.world() == null)
        {
            tended.clear();
            return State.MOLD_UNLOAD;
        }

        if (target != null)
        {
            if (!ai.gotoWorkPos(target))
            {
                return State.TEND_CONTROLLERS;
            }
            final ForgeController controller = controllerAt(target);
            if (controller != null)
            {
                tendController(controller);
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
            final boolean loadable = c.freeHeatSlots() > 0 && carryingCastOre();
            if (c.hasFinished() || loadable || needsRelight(c) || c.isLit())
            {
                target = pos;
                return State.TEND_CONTROLLERS;
            }
            tended.add(pos);
        }

        tended.clear();
        return State.MOLD_UNLOAD;
    }

    /** Tend one controller: drain full molds to the racks, seat molds, fuel + light, load matching ore, keep-warm. */
    private void tendController(final ForgeController c)
    {
        c.setLevelBonus(Config.furnaceFuelTempBonus(ai.buildingLevel()));

        for (final ItemStack full : c.takeFinished())
        {
            insertRacks(full); // hot full molds cool in the racks; MOLD_UNLOAD casts them
        }
        for (final ItemStack bad : c.takeUnprocessable())
        {
            insertRacks(bad);
        }

        seatMolds(c);
        if (c.needsFuel())
        {
            tender.refuel(c);
        }

        final boolean willLoad = c.freeHeatSlots() > 0 && carryingCastOre();
        // Light only for work the fuel can finish — an advanceable occupant or an imminent load — not for a stalled ore
        // the fuel can't reach. Refuel above runs first, so a just-refuelled column makes a stranded ore advanceable
        // again; keying on that (not raw occupancy) stops the relight↔extinguish churn on an unfinishable item (SHARED-2).
        if ((c.hasAdvanceableOccupant() || willLoad) && !c.isLit())
        {
            c.light();
        }

        loadOre(c);
        tender.keepWarm(c);
    }

    /** Seat empty molds into the empty output/overflow slots of positions that will melt (the §8 seating rule). */
    private void seatMolds(final ForgeController c)
    {
        for (final BlockPos pos : c.members())
        {
            if (!willMelt(c, pos))
            {
                continue;
            }
            if (!c.outputHasMold(pos))
            {
                final ItemStack mold = extractEmptyMold();
                if (mold.isEmpty())
                {
                    break; // out of molds
                }
                if (!c.seatContainers(pos, mold, ItemStack.EMPTY))
                {
                    insert(inventory(), mold); // couldn't seat (race) — hand it back
                }
            }
            if (!c.overflowHasMold(pos))
            {
                final ItemStack mold = extractEmptyMold();
                if (mold.isEmpty())
                {
                    break;
                }
                if (!c.seatContainers(pos, ItemStack.EMPTY, mold))
                {
                    insert(inventory(), mold);
                }
            }
        }
    }

    /** A position that has (or is about to get) an ore to melt — worth seating molds for. */
    private boolean willMelt(final ForgeController c, final BlockPos pos)
    {
        return !c.heatFree(pos) || carryingCastOre();
    }

    /** Load a matching ore into each free, mold-seated, non-full position — matching the seated metal, cast-only. */
    private void loadOre(final ForgeController c)
    {
        for (final BlockPos pos : c.members())
        {
            if (!c.heatFree(pos) || !c.hasContainer(pos) || c.containerFreeCapacity(pos) <= 0)
            {
                continue; // occupied / no mold / molds full (would spill)
            }
            final Fluid seated = c.seatedMetal(pos);
            final ItemStack ore = pickOre(inventory(), seated, c);
            if (ore.isEmpty())
            {
                continue;
            }
            final ItemStack one = ForgeTender.extractOne(inventory(), s -> ItemHandlerHelper.canItemStacksStack(s, ore));
            if (!one.isEmpty())
            {
                c.loadInputAt(pos, one);
            }
        }
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
            return State.MOLD_UNLOAD;
        }
        return State.BLOOMERY_TEND;
    }

    /**
     * Extract one fully-cooled (heat 0) filled mold from storage with TFC's casting recipe: the ingot is produced and
     * the mold drained, then kept or broken by its {@link CastingRecipe#getBreakChance()}. Returns whether one was cast.
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
                    continue;
                }
                final ItemStack filled = h.extractItem(slot, 1, false);
                final MoldLike taken = MoldLike.get(filled);
                if (taken != null)
                {
                    taken.drainIgnoringTemperature(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                }
                deliver(result);
                ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
                ai.countAction();
                if (world.getRandom().nextFloat() >= recipe.getBreakChance())
                {
                    insertRacks(filled); // mold survives (now empty) — back to the racks to be re-staged
                }
                return true;
            }
        }
        return false;
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
                    return true;
                }
            }
        }
        return false;
    }

    // --- Bloomery tending (iron path) ---------------------------------------------------------------------

    /** The grafted {@link BloomeryUserModule} on this Smeltery (holds the wand-marked bloomery positions), or null. */
    private BloomeryUserModule bloomeryModule()
    {
        final IBuilding building = ai.building();
        return building == null ? null : building.getFirstModuleOccurance(BloomeryUserModule.class);
    }

    /**
     * Whether any marked bloomery needs the worker (docs/tfc-bloomery-smelter.md §3c). Ordered cheapest-first and
     * zero-cost when unused: bail immediately if no bloomeries are marked; a finished bloom to extract is checked live
     * (cheap — {@code LIT}/bloom reads); the expensive {@code isFormed} loadability scan is throttled to
     * {@code REQUEST_CHECK_INTERVAL} and gated behind having iron ore + charcoal on hand.
     */
    private boolean bloomeryWork()
    {
        final BloomeryUserModule module = bloomeryModule();
        if (module == null)
        {
            return false;
        }
        final Set<BlockPos> marks = module.getBloomeries();
        if (marks.isEmpty())
        {
            return false; // feature unused → no world access at all
        }
        final Level world = ai.world();
        if (world == null)
        {
            return false;
        }
        // Live (cheap): a finished bloom anywhere is always work to extract.
        for (final BlockPos pos : marks)
        {
            if (world.hasChunkAt(pos))
            {
                final BloomeryBlockEntity be = TfcBloomery.bloomeryAt(world, pos);
                if (be != null && TfcBloomery.bloomAt(world, be) != null)
                {
                    return true;
                }
            }
        }
        // Throttled: loading is only worth a trip if we have materials and an unlit, formed, empty bloomery.
        if (world.getGameTime() >= nextBloomeryCheck)
        {
            nextBloomeryCheck = world.getGameTime() + REQUEST_CHECK_INTERVAL;
            bloomeryWorkPending = anyBloomeryLoadable(world, marks);
        }
        return bloomeryWorkPending;
    }

    /** True if the colony has enough iron ore + charcoal for ≥1 bloom and a marked bloomery is unlit, empty and formed. */
    private boolean anyBloomeryLoadable(final Level world, final Set<BlockPos> marks)
    {
        final List<IItemHandler> stock = combined();
        if (ironMb(stock) < SmelterRecipes.UNITS_PER_OUTPUT || ForgeTender.countMatching(stock, TfcBloomery::isCatalyst) < 2)
        {
            return false;
        }
        for (final BlockPos pos : marks)
        {
            if (!world.hasChunkAt(pos))
            {
                continue;
            }
            final BloomeryBlockEntity be = TfcBloomery.bloomeryAt(world, pos);
            if (be == null || TfcBloomery.isLit(be) || TfcBloomery.bloomAt(world, be) != null)
            {
                continue;
            }
            if (TfcBloomery.isFormed(world, pos))
            {
                return true;
            }
        }
        return false;
    }

    /** One visit per actionable marked bloomery (walk → service), mirroring {@link #tend}; then back to the work loop. */
    private IAIState bloomeryTend()
    {
        final BloomeryUserModule module = bloomeryModule();
        final Level world = ai.world();
        if (module == null || world == null)
        {
            bloomeriesTended.clear();
            return afterBloomery();
        }
        module.pruneStale(world);
        final Set<BlockPos> marks = module.getBloomeries();

        if (bloomeryTarget != null)
        {
            if (!ai.gotoWorkPos(bloomeryTarget))
            {
                return State.BLOOMERY_TEND;
            }
            serviceBloomery(world, bloomeryTarget);
            bloomeriesTended.add(bloomeryTarget);
            bloomeryTarget = null;
            return State.BLOOMERY_TEND;
        }

        for (final BlockPos pos : marks)
        {
            if (bloomeriesTended.contains(pos))
            {
                continue;
            }
            if (!world.hasChunkAt(pos))
            {
                bloomeriesTended.add(pos);
                continue;
            }
            final BloomeryBlockEntity be = TfcBloomery.bloomeryAt(world, pos);
            if (be != null && bloomeryActionable(world, be, pos))
            {
                bloomeryTarget = pos;
                return State.BLOOMERY_TEND;
            }
            bloomeriesTended.add(pos);
        }
        bloomeriesTended.clear();
        return afterBloomery();
    }

    private IAIState afterBloomery()
    {
        return hasWork() ? State.BATCH_STAGING : AIWorkerState.IDLE;
    }

    /** A finished bloom to extract, or an unlit + formed + loadable bloomery. A burning bloomery is skipped (self-runs). */
    private boolean bloomeryActionable(final Level world, final BloomeryBlockEntity be, final BlockPos pos)
    {
        if (TfcBloomery.bloomAt(world, be) != null)
        {
            return true;
        }
        return !TfcBloomery.isLit(be) && TfcBloomery.isFormed(world, pos) && carryingBloomeryMaterials();
    }

    /** Do one action at the walked-to bloomery: extract a finished bloom, else (if unlit + formed) load and light it. */
    private void serviceBloomery(final Level world, final BlockPos pos)
    {
        final BloomeryBlockEntity be = TfcBloomery.bloomeryAt(world, pos);
        if (be == null)
        {
            return;
        }
        final BloomBlockEntity bloom = TfcBloomery.bloomAt(world, be);
        if (bloom != null)
        {
            for (final ItemStack raw : TfcBloomery.extractBlooms(world, bloom))
            {
                insertRacks(raw); // hot raw_iron_bloom cools in the racks; the player hammers it on a TFC anvil
                ai.worker().getCitizenExperienceHandler().addExperience(XP_PER_OUTPUT);
            }
            ai.countAction();
            return;
        }
        if (!TfcBloomery.isLit(be) && TfcBloomery.isFormed(world, pos))
        {
            loadAndLight(world, be);
        }
    }

    /**
     * Load whole-bloom batches of iron ore + charcoal into the bloomery, then light it (docs/tfc-bloomery-smelter.md §2).
     * All iron ore pools into one cast-iron total, so we align to 100&nbsp;mB: plan carried ore (rich-first, reserving
     * charcoal room), take {@code k = ⌊totalMb/100⌋} blooms (also capped by carried charcoal), then <b>trim</b> the
     * smallest ores while the total stays ≥&nbsp;100k to minimise the sub-100 remainder (bounded-waste flush — the worker
     * never idles on an un-alignable pile; a clean batch falls out when the colony has enough of an aligning grade). If
     * &lt;1 whole bloom is makeable, load nothing (the ore keeps accumulating in storage for a later, bigger batch).
     */
    private void loadAndLight(final Level world, final BloomeryBlockEntity be)
    {
        final List<IItemHandler> inv = inventory();
        final int free = TfcBloomery.freeCapacity(world, be);
        if (free < 3)
        {
            return; // no room for even a minimal bloom (1 ore + 2 charcoal)
        }

        // Every carried iron ore item as a size-1 unit (so mixed grades combine toward a 100 mB multiple).
        final List<ItemStack> ores = new ArrayList<>();
        for (final IItemHandler h : inv)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack st = h.getStackInSlot(slot);
                if (usableIronOre(st))
                {
                    for (int n = 0; n < st.getCount(); n++)
                    {
                        ores.add(oreUnit(st));
                    }
                }
            }
        }
        final int charcoal = ForgeTender.countMatching(inv, TfcBloomery::isCatalyst);
        if (ores.isEmpty() || charcoal < 2)
        {
            return;
        }

        // Plan ore rich-first, reserving 2 charcoal per (bloom-so-far + 1) so a charcoal pair always fits.
        ores.sort((a, b) -> Integer.compare(TfcBloomery.oreMb(b), TfcBloomery.oreMb(a)));
        final List<ItemStack> plan = new ArrayList<>();
        int mb = 0;
        for (final ItemStack ore : ores)
        {
            final int reserve = 2 * ((mb + TfcBloomery.oreMb(ore)) / SmelterRecipes.UNITS_PER_OUTPUT + 1);
            if (plan.size() + 1 + reserve > free)
            {
                break;
            }
            plan.add(ore);
            mb += TfcBloomery.oreMb(ore);
        }

        final int k = Math.min(mb / SmelterRecipes.UNITS_PER_OUTPUT, charcoal / 2);
        if (k < 1)
        {
            return; // can't make a whole bloom from what's carried — leave it to accumulate in storage
        }

        // Trim the smallest ores while the total still yields k blooms, minimising the wasted remainder.
        plan.sort((a, b) -> Integer.compare(TfcBloomery.oreMb(a), TfcBloomery.oreMb(b)));
        while (!plan.isEmpty() && mb - TfcBloomery.oreMb(plan.get(0)) >= SmelterRecipes.UNITS_PER_OUTPUT * k)
        {
            mb -= TfcBloomery.oreMb(plan.remove(0));
        }

        // Load the planned ore + exactly 2k charcoal from the worker's inventory, then ignite.
        for (final ItemStack ore : plan)
        {
            final ItemStack one = ForgeTender.extractOne(inv, s -> ItemHandlerHelper.canItemStacksStack(s, ore));
            if (!one.isEmpty())
            {
                TfcBloomery.loadInput(be, one);
            }
        }
        for (int i = 0; i < 2 * k; i++)
        {
            final ItemStack c = ForgeTender.extractOne(inv, TfcBloomery::isCatalyst);
            if (!c.isEmpty())
            {
                TfcBloomery.loadInput(be, c);
            }
        }
        if (TfcBloomery.light(be))
        {
            ai.countAction();
        }
    }

    /** A size-1 copy of an ore stack (bloomery inputs are stored one item per entry). */
    private static ItemStack oreUnit(final ItemStack ore)
    {
        final ItemStack one = ore.copy();
        one.setCount(1);
        return one;
    }

    /** Total cast-iron mB the iron ore in {@code storage} is worth (sum of per-item grade mB). */
    private int ironMb(final List<IItemHandler> storage)
    {
        int mb = 0;
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack st = h.getStackInSlot(slot);
                if (usableIronOre(st))
                {
                    mb += TfcBloomery.oreMb(st) * st.getCount();
                }
            }
        }
        return mb;
    }

    /** Whether the worker carries enough iron ore + charcoal for at least one bloom. */
    private boolean carryingBloomeryMaterials()
    {
        final List<IItemHandler> inv = inventory();
        return ironMb(inv) >= SmelterRecipes.UNITS_PER_OUTPUT && ForgeTender.countMatching(inv, TfcBloomery::isCatalyst) >= 2;
    }

    // --- ForgeTender.Policy -------------------------------------------------------------------------------

    /** A smelter ore the player hasn't excluded — and <b>not iron</b> (a bloom, not a pourable fluid; deferred). */
    @Override
    public boolean accepts(final ItemStack stack)
    {
        final Output out = SmelterRecipes.outputFor(stack);
        if (out == null || out.bloom())
        {
            return false;
        }
        return oreEnabled(stack);
    }

    /**
     * Whether the hut's ore list permits {@code stack}. The Smeltery's "smeltable ores" GUI is a <b>deny</b>-list
     * (matching native {@code EntityAIWorkSmelter#isSmeltable}: an ore is processed unless it's in the list); we fill it
     * with the TFC ores via {@code MixinCompatibilityManager}, so toggling an ore off adds it here. Applied to BOTH the
     * forge cast-metal path ({@link #accepts}) and the bloomery iron path ({@link #usableIronOre}) so the one toggle is
     * an honest control over everything the Smeltery melts.
     */
    private boolean oreEnabled(final ItemStack stack)
    {
        final ItemListModule blocked = listModule(ORE_LIST);
        return blocked == null || !blocked.isItemInList(new ItemStorage(stack));
    }

    /** An iron-bearing ore the hut may feed its bloomeries: an iron ore that isn't toggled off in the ore list. */
    private boolean usableIronOre(final ItemStack stack)
    {
        return TfcBloomery.isIronOre(stack) && oreEnabled(stack);
    }

    @Override
    public float requiredTemp(final ItemStack stack)
    {
        return meltTempOf(stack);
    }

    /**
     * A TFC fuel the player permits via the hut's <b>fuel</b> list. The smelter only burns TFC <b>forge</b> fuels
     * ({@link FurnaceFuelScope#SMELTER} — the coals), matching a TFC charcoal forge; the list only constrains once it
     * names a forge fuel, so the vanilla default can't starve it.
     */
    @Override
    public boolean fuelAllowed(final ItemStack stack)
    {
        if (!FurnaceFuel.isFuel(stack) || !stack.is(FurnaceFuelScope.SMELTER))
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

    // --- Ore / mold helpers -------------------------------------------------------------------------------

    /** An ore in {@code storage} the fuel can reach and (if the position has a seated metal) matching it. */
    private ItemStack pickOre(final List<IItemHandler> storage, final Fluid seated, final ForgeController c)
    {
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (!accepts(stack) || !c.canReach(meltTempOf(stack)))
                {
                    continue;
                }
                if (seated != null && oreFluid(stack) != seated)
                {
                    continue; // a seated partial mold dictates the metal
                }
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether the colony (storage) can make a melt: an accepted ore with hot-enough fuel + an empty mold available. */
    private boolean canMakeMelt(final List<IItemHandler> storage)
    {
        if (ForgeTender.countMatching(storage, this::isEmptyMold) <= 0)
        {
            return false;
        }
        for (final IItemHandler h : storage)
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                final ItemStack stack = h.getStackInSlot(slot);
                if (accepts(stack) && FurnaceFuel.hasFuelHotEnough(meltTempOf(stack), ai.buildingLevel(), this::fuelAllowed, storage))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean carryingCastOre()
    {
        return ForgeTender.countMatching(inventory(), this::accepts) > 0;
    }

    /**
     * Whether {@code c} is a forge that went cold with a still-meltable ore stranded in its heat slots (its fuel ran out
     * mid-melt) and we have fuel to relight it — from its own column or the racks. Such a forge won't restart itself, so
     * the worker must revisit to relight it; without this the Smelter goes IDLE and the ore is stranded (review COOK-1's
     * Smelter analog).
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
            if (accepts(heat)
                  && (c.canReach(meltTempOf(heat))
                        || FurnaceFuel.hasFuelHotEnough(meltTempOf(heat), ai.buildingLevel(), this::fuelAllowed, stock)))
            {
                return true;
            }
        }
        return false;
    }

    private static float meltTempOf(final ItemStack ore)
    {
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        return recipe != null ? recipe.getTemperature() : 1000f;
    }

    private static Fluid oreFluid(final ItemStack ore)
    {
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        return recipe != null && !recipe.getDisplayOutputFluid().isEmpty() ? recipe.getDisplayOutputFluid().getFluid() : null;
    }

    /** A casting mold with no metal in it yet. */
    private boolean isEmptyMold(final ItemStack stack)
    {
        if (!SmelterRecipes.isMold(stack))
        {
            return false;
        }
        final IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return handler == null || handler.getFluidInTank(0).isEmpty();
    }

    private ItemStack extractEmptyMold()
    {
        return ForgeTender.extractOne(inventory(), this::isEmptyMold);
    }

    private ForgeController controllerAt(final BlockPos pos)
    {
        final Level world = ai.world();
        return world != null && world.getBlockEntity(pos) instanceof HeatForgeBlockEntity be ? be : null;
    }

    // --- Delivery / storage -------------------------------------------------------------------------------

    /** Carry a finished ingot in the worker's inventory (the dump ships it); falls back to racks/ground on a race. */
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
        for (final IItemHandler h : racks())
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

    /** Put an intermediate (a hot full mold cooling toward casting, or a recycled empty mold) into the racks. */
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
        insert(inventory(), stack); // fall back so nothing is lost
    }

    private void insert(final List<IItemHandler> storage, ItemStack stack)
    {
        ForgeTender.insert(storage, stack);
    }

    private boolean canCarry(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return true;
        }
        for (final IItemHandler h : inventory())
        {
            for (int slot = 0; slot < h.getSlots(); slot++)
            {
                if (h.getStackInSlot(slot).isEmpty())
                {
                    return true;
                }
            }
            if (ItemHandlerHelper.insertItem(h, stack.copy(), true).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private ItemListModule listModule(final String id)
    {
        final IBuilding building = ai.building();
        return building == null ? null : building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(id));
    }

    // --- Auto-requesting (low-water restock) --------------------------------------------------------------

    private void requestMissing()
    {
        final Level world = ai.world();
        if (world == null || !(ai.building() instanceof BuildingSmeltery) || ai.worker() == null || ai.worker().getCitizenData() == null)
        {
            return;
        }
        if (world.getGameTime() < nextRequestCheck)
        {
            return;
        }
        nextRequestCheck = world.getGameTime() + REQUEST_CHECK_INTERVAL;

        final int threshold = settingValue();
        final int reserve = reserveValue();
        final List<IItemHandler> stock = combined();

        if (ForgeTender.countMatching(stock, this::accepts) <= threshold && !isOpen(oreRequest))
        {
            final List<ItemStack> ores = enabledOreStacks();
            if (!ores.isEmpty())
            {
                oreRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(ores, REQUESTS_TYPE_SMELTABLE_ORE, RESTOCK_BATCH, 1, reserve));
            }
        }
        if (ForgeTender.countMatching(stock, this::fuelAllowed) <= threshold && !isOpen(fuelRequest))
        {
            final List<ItemStack> fuels = allowedFuelStacks();
            if (!fuels.isEmpty())
            {
                fuelRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(fuels, REQUESTS_TYPE_BURNABLE, RESTOCK_BATCH, 1, reserve));
            }
        }

        // Bloomery iron path: keep iron ore + charcoal stocked whenever the hut has marked bloomeries.
        final BloomeryUserModule bloomeries = bloomeryModule();
        if (bloomeries != null && !bloomeries.getBloomeries().isEmpty())
        {
            if (ironMb(stock) <= BLOOMERY_ORE_LOW_MB && !isOpen(ironRequest))
            {
                final List<ItemStack> ironOres = ironOreStacks();
                if (!ironOres.isEmpty())
                {
                    ironRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(ironOres, REQUESTS_TYPE_SMELTABLE_ORE, RESTOCK_BATCH, 1, reserve));
                }
            }
            if (ForgeTender.countMatching(stock, TfcBloomery::isCatalyst) <= BLOOMERY_CHARCOAL_LOW && !isOpen(charcoalRequest))
            {
                final ItemStack charcoal = new ItemStack(SmelterRecipes.item(SmelterRecipes.CHARCOAL));
                if (!charcoal.isEmpty())
                {
                    charcoalRequest = ai.worker().getCitizenData().createRequestAsync(new StackList(List.of(charcoal), REQUESTS_TYPE_BURNABLE, RESTOCK_BATCH, 1, reserve));
                }
            }
        }
    }

    private List<ItemStack> ironOreStacks()
    {
        final List<ItemStack> ores = new ArrayList<>();
        for (final ItemStack ore : SmelterRecipes.oreStacks())
        {
            if (usableIronOre(ore))
            {
                ores.add(ore);
            }
        }
        return ores;
    }

    private int settingValue()
    {
        if (!(ai.building() instanceof BuildingSmeltery building))
        {
            return ORE_THRESHOLD_DEFAULT;
        }
        final IntSetting setting = building.getSetting(ORE_THRESHOLD);
        return setting == null ? ORE_THRESHOLD_DEFAULT : Math.max(0, setting.getValue());
    }

    private int reserveValue()
    {
        if (!(ai.building() instanceof BuildingSmeltery building))
        {
            return 0;
        }
        final IntSetting setting = building.getSetting(BuildingSmeltery.MIN);
        return setting == null ? 0 : Math.max(0, setting.getValue());
    }

    private List<ItemStack> enabledOreStacks()
    {
        final List<ItemStack> ores = new ArrayList<>();
        for (final ItemStack ore : SmelterRecipes.oreStacks())
        {
            if (accepts(ore))
            {
                ores.add(ore);
            }
        }
        return ores;
    }

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
