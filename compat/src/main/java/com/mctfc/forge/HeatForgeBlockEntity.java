package com.mctfc.forge;

import com.mctfc.Config;
import com.mctfc.cook.CookRecipes;
import com.mctfc.forge.ForgeMultiblock.Group;
import com.mctfc.smelter.SmelterRecipes;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The heat-forge's block entity — and, when it's the lowest-{@code BlockPos} member of its ≤5 group, the
 * <b>controller</b> that owns the shared device state and <b>self-ticks</b> the processing. See
 * {@code docs/tfc-forge-multiblock.md}.
 *
 * <p><b>Per-position state</b> ({@link #positions}) — every forge block, controller or not, holds its own single
 * {@code heat / output / overflow} slot triple (each 1 item, TFC-forge style); these <b>drop on break</b> at the block's
 * own location. <b>Shared state</b> (the 5-slot {@link #fuel} column, {@link #deviceTemp}, {@link #lit}, the burn
 * bookkeeping, {@link #levelBonus}, {@link #lastActiveTick}) is used only while this BE is acting as the controller —
 * dormant on a follower, and re-initialised fresh if a follower is later elected controller.
 *
 * <p>The controller {@link #serverTick}: if lit, burn the bottom fuel → climb {@code deviceTemp} toward the ceiling →
 * advance every runnable position (slice 1: the cook path — heat 1 raw toward the device temp; when the item reaches
 * its recipe temperature, produce cooked food into the position's output/overflow). The worker never touches ignition
 * timing — it only lights, feeds, and drains (via the {@link ForgeController} façade this implements).
 */
public class HeatForgeBlockEntity extends BlockEntity implements ForgeController
{
    /** Per-position slot indices (each block's own {@link #positions} handler; every slot holds a single item). */
    public static final int HEAT     = 0;
    public static final int OUTPUT   = 1;
    public static final int OVERFLOW = 2;
    private static final int POSITION_SLOTS = 3;

    /** The shared fuel column — a 1-to-1 copy of TFC's charcoal forge (5 slots; only slot 0, the bottom, burns). */
    public static final int FUEL_SLOTS = 5;

    /** This block's own position: heat + output + overflow, each capped to one item. */
    private final ItemStackHandler positions = new ItemStackHandler(POSITION_SLOTS)
    {
        @Override public int getSlotLimit(final int slot) { return 1; }
    };

    /** Controller-only: the shared 5-slot fuel column (dormant on a follower). One fuel item per slot, TFC-forge style. */
    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS)
    {
        @Override
        public int getSlotLimit(final int slot)
        {
            return 1;
        }
    };

    // --- shared device state (controller-only) ---
    private float deviceTemp;
    private boolean lit;
    private int burnTicks;
    private float burnTemperature;
    private int levelBonus;
    private long lastActiveTick;

    public HeatForgeBlockEntity(final BlockPos pos, final BlockState state)
    {
        super(HeatForgeBlocks.HEAT_FORGE_BE.get(), pos, state);
    }

    /** This block's own heat/output/overflow handler (used by the GUI and the controller when driving positions). */
    public ItemStackHandler positions()
    {
        return positions;
    }

    /** The controller's shared 5-slot fuel column (used by the GUI; dormant on a follower). */
    public ItemStackHandler fuelHandler()
    {
        return fuel;
    }

    /** The forge BE at {@code pos} (a member of this group), or {@code null} if it isn't a loaded forge. */
    public HeatForgeBlockEntity memberEntity(final BlockPos pos)
    {
        return level == null ? null : memberAt(level, pos);
    }

    // === Self-tick (controller only) =======================================================================

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final HeatForgeBlockEntity be)
    {
        final Group group = ForgeMultiblock.groupOf(level, pos);
        if (!pos.equals(group.controller()))
        {
            return; // followers don't drive; the controller reaches into their positions
        }

        // Freeze while any member is unloaded (§4): timers are game-time based, so keep-warm still resumes exactly.
        final List<HeatForgeBlockEntity> members = new ArrayList<>();
        for (final BlockPos m : group.members())
        {
            final HeatForgeBlockEntity mbe = be.memberAt(level, m);
            if (mbe == null)
            {
                return;
            }
            members.add(mbe);
        }

        be.cascadeFuel(); // keep fuel packed at the bottom every tick, so player-/worker-placed fuel travels down (TFC forge)
        be.burnStep();
        be.climbTemp();
        for (final HeatForgeBlockEntity member : members)
        {
            be.processPosition(level, member);
        }
        be.driveLitState(level, group.members());
        be.setChanged();
    }

    /**
     * Burn the bottom fuel — TFC forge: when the timer expires, the bottom item is <b>consumed the moment it starts
     * burning</b> (removed from slot 0; its duration/temperature drive the burn, tracked by {@link #burnTicks}), then
     * the column cascades down. So the slot never holds the item currently on fire.
     */
    private void burnStep()
    {
        if (!lit)
        {
            return;
        }
        if (burnTicks <= 0)
        {
            cascadeFuel();
            final ItemStack bottom = fuel.getStackInSlot(0);
            final Fuel f = bottom.isEmpty() ? null : Fuel.get(bottom);
            if (f == null)
            {
                lit = false;
                burnTemperature = 0f;
                return;
            }
            burnTicks = f.getDuration();
            burnTemperature = f.getTemperature() + levelBonus;
            fuel.extractItem(0, 1, false); // consumed at ignition (TFC), then the column cascades down
            cascadeFuel();
        }
        burnTicks--;
    }

    /** Shift the column down so the bottom slot is filled if any fuel is present (TFC forge cascade). */
    private void cascadeFuel()
    {
        for (int target = 0; target < FUEL_SLOTS; target++)
        {
            if (!fuel.getStackInSlot(target).isEmpty())
            {
                continue;
            }
            for (int src = target + 1; src < FUEL_SLOTS; src++)
            {
                if (!fuel.getStackInSlot(src).isEmpty())
                {
                    fuel.setStackInSlot(target, fuel.getStackInSlot(src));
                    fuel.setStackInSlot(src, ItemStack.EMPTY);
                    break;
                }
            }
        }
    }

    /** Move the shared temperature toward the ceiling ({@link #burnTemperature} while lit, else 0). */
    private void climbTemp()
    {
        final float target = lit ? burnTemperature : 0f;
        if (deviceTemp < target)
        {
            deviceTemp = Math.min(target, deviceTemp + Config.forgeTempRisePerTick);
        }
        else if (deviceTemp > target)
        {
            deviceTemp = Math.max(target, deviceTemp - Config.forgeTempFallPerTick);
        }
    }

    /**
     * Advance one position: warm the heat-slot item toward the device temperature and, once it reaches its recipe
     * temperature, transform it — an <b>item</b>-output heating recipe cooks (Cook/Chef), a <b>fluid</b>-output one
     * melts into the seated molds (Smelter). No recipe → {@link ForgeState#INVALID} (the worker ejects it).
     */
    private void processPosition(final Level level, final HeatForgeBlockEntity member)
    {
        final ItemStack heat = member.positions.getStackInSlot(HEAT);
        if (heat.isEmpty())
        {
            return;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(heat);
        if (recipe == null)
        {
            return; // INVALID — nothing to advance; the worker ejects it
        }
        final float required = recipe.getTemperature();
        // The item heats toward the device temperature whenever the device is warmer — it glows during warm-up, even
        // below its own transform temperature (TFC). Its rising heat IS the progress; it transforms only once it
        // reaches the recipe temperature, so a device that can't get hot enough just leaves it COLD-stalled.
        float temp = HeatCapability.getTemperature(heat);
        if (deviceTemp > temp)
        {
            temp = Math.min(deviceTemp, temp + Config.forgeItemHeatPerTick);
            HeatCapability.setTemperature(heat, temp);
            member.setChanged();
        }
        if (temp < required)
        {
            return; // still heating (or COLD-stalled below its transform temperature)
        }
        if (recipe.getDisplayOutputFluid().isEmpty())
        {
            cook(level, member, heat);
        }
        else
        {
            melt(level, member, heat, recipe);
        }
    }

    /** Cook one raw piece into cooked food (decay carried by {@code copy_food}) into output → overflow. */
    private void cook(final Level level, final HeatForgeBlockEntity member, final ItemStack heat)
    {
        final ItemStack cooked = CookRecipes.cook(heat.copyWithCount(1), level.registryAccess());
        if (cooked.isEmpty())
        {
            return;
        }
        cooked.setCount(1);
        if (!member.depositFinished(cooked))
        {
            return; // BLOCKED — both output and overflow full; hold (item keeps its heat)
        }
        consumeHeatPiece(level, member, heat);
    }

    /**
     * Melt one ore piece into liquid metal and pour it into the position's molds — output to capacity first, then
     * overflow; metal beyond both molds spills and is lost (TFC-authentic, §8/§10). The melt never stops for lack of
     * room, so the tend-AI keeps molds seated/drained and sizes ore loads to the free capacity.
     */
    private void melt(final Level level, final HeatForgeBlockEntity member, final ItemStack heat, final HeatingRecipe recipe)
    {
        final Fluid fluid = recipe.getDisplayOutputFluid().getFluid();
        int amount = SmelterRecipes.meltMb(heat);
        if (amount <= 0)
        {
            amount = recipe.getDisplayOutputFluid().getAmount();
        }
        if (fluid == null || amount <= 0)
        {
            return;
        }
        final float castTemp = Math.max(1f, recipe.getTemperature() - 1f);
        final FluidStack metal = new FluidStack(fluid, amount);
        final int poured = member.pourInto(OUTPUT, metal, castTemp);
        if (poured < metal.getAmount())
        {
            metal.setAmount(metal.getAmount() - poured);
            member.pourInto(OVERFLOW, metal, castTemp); // remainder spills (lost) if the overflow can't take it either
        }
        consumeHeatPiece(level, member, heat);
    }

    /** Consume one item from the heat slot after a transform; a remaining piece resets cool for the next cycle. */
    private void consumeHeatPiece(final Level level, final HeatForgeBlockEntity member, final ItemStack heat)
    {
        heat.shrink(1);
        if (heat.isEmpty())
        {
            member.positions.setStackInSlot(HEAT, ItemStack.EMPTY);
        }
        else
        {
            HeatCapability.setTemperature(heat, 0f);
        }
        lastActiveTick = level.getGameTime();
        member.setChanged();
    }

    /** Pour metal into the mold seated in {@code slot}, heating the filled mold to {@code castTemp}; returns mB accepted. */
    private int pourInto(final int slot, final FluidStack metal, final float castTemp)
    {
        final ItemStack mold = positions.getStackInSlot(slot);
        final IFluidHandlerItem handler = mold.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler == null)
        {
            return 0;
        }
        final int filled = handler.fill(metal, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0)
        {
            final ItemStack container = handler.getContainer();
            HeatCapability.setTemperature(container, castTemp);
            positions.setStackInSlot(slot, container);
        }
        return filled;
    }

    /** Place a finished item into this position's output slot, else overflow; false if both are occupied (BLOCKED). */
    private boolean depositFinished(final ItemStack finished)
    {
        if (positions.getStackInSlot(OUTPUT).isEmpty())
        {
            positions.setStackInSlot(OUTPUT, finished);
            return true;
        }
        if (positions.getStackInSlot(OVERFLOW).isEmpty())
        {
            positions.setStackInSlot(OVERFLOW, finished);
            return true;
        }
        return false;
    }

    /** Keep every member block's {@code LIT} blockstate in step with the shared burn (drives the lit furnace look). */
    private void driveLitState(final Level level, final List<BlockPos> memberPositions)
    {
        for (final BlockPos m : memberPositions)
        {
            final BlockState st = level.getBlockState(m);
            if (st.getBlock() instanceof HeatForgeBlock && st.hasProperty(HeatForgeBlock.LIT)
                  && st.getValue(HeatForgeBlock.LIT) != lit)
            {
                level.setBlock(m, st.setValue(HeatForgeBlock.LIT, lit), 3);
            }
        }
    }

    private HeatForgeBlockEntity memberAt(final Level level, final BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof HeatForgeBlockEntity mbe ? mbe : null;
    }

    // === ForgeController façade (called on the controller BE) ===============================================

    private Group group()
    {
        return level == null ? new Group(worldPosition, List.of(worldPosition)) : ForgeMultiblock.groupOf(level, worldPosition);
    }

    @Override
    public boolean isLit()
    {
        return lit;
    }

    @Override
    public void light()
    {
        if (!lit)
        {
            lit = true;
            burnTicks = 0; // ignite the bottom fuel on the next tick
            setChanged();
        }
    }

    @Override
    public void extinguish()
    {
        if (lit)
        {
            lit = false;
            burnTicks = 0;
            burnTemperature = 0f;
            setChanged();
        }
    }

    @Override
    public long lastActiveTick()
    {
        return lastActiveTick;
    }

    @Override
    public boolean needsFuel()
    {
        for (int i = 0; i < FUEL_SLOTS; i++)
        {
            if (fuel.getStackInSlot(i).isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addFuel(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return true;
        }
        final ItemStack leftover = ItemHandlerHelper.insertItem(fuel, stack, false);
        setChanged();
        return leftover.isEmpty();
    }

    @Override
    public float deviceTemp()
    {
        return deviceTemp;
    }

    @Override
    public boolean canReach(final float requiredTemp)
    {
        if (lit && burnTemperature >= requiredTemp)
        {
            return true;
        }
        for (int i = 0; i < FUEL_SLOTS; i++)
        {
            final Fuel f = Fuel.get(fuel.getStackInSlot(i));
            if (f != null && f.getTemperature() + levelBonus >= requiredTemp)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setLevelBonus(final int bonusCelsius)
    {
        if (levelBonus != bonusCelsius)
        {
            levelBonus = bonusCelsius;
            setChanged();
        }
    }

    @Override
    public int freeHeatSlots()
    {
        if (level == null)
        {
            return 0;
        }
        int free = 0;
        for (final BlockPos m : group().members())
        {
            final HeatForgeBlockEntity mbe = memberAt(level, m);
            if (mbe != null && mbe.positions.getStackInSlot(HEAT).isEmpty())
            {
                free++;
            }
        }
        return free;
    }

    @Override
    public boolean loadInput(final ItemStack stack)
    {
        if (level == null || stack.isEmpty())
        {
            return false;
        }
        for (final BlockPos m : group().members())
        {
            final HeatForgeBlockEntity mbe = memberAt(level, m);
            if (mbe != null && mbe.positions.getStackInSlot(HEAT).isEmpty())
            {
                mbe.positions.setStackInSlot(HEAT, stack.copyWithCount(1));
                mbe.setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean heatFree(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        return mbe != null && mbe.positions.getStackInSlot(HEAT).isEmpty();
    }

    @Override
    public boolean loadInputAt(final BlockPos pos, final ItemStack stack)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        if (mbe == null || stack.isEmpty() || !mbe.positions.getStackInSlot(HEAT).isEmpty())
        {
            return false;
        }
        mbe.positions.setStackInSlot(HEAT, stack.copyWithCount(1));
        mbe.setChanged();
        return true;
    }

    @Override
    public boolean hasContainer(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        return mbe != null && (fluidHandler(mbe, OUTPUT) != null || fluidHandler(mbe, OVERFLOW) != null);
    }

    @Override
    public boolean outputHasMold(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        return mbe != null && fluidHandler(mbe, OUTPUT) != null;
    }

    @Override
    public boolean overflowHasMold(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        return mbe != null && fluidHandler(mbe, OVERFLOW) != null;
    }

    @Override
    public int containerFreeCapacity(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        return mbe == null ? 0 : freeCap(mbe, OUTPUT) + freeCap(mbe, OVERFLOW);
    }

    @Override
    public Fluid seatedMetal(final BlockPos pos)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        if (mbe == null)
        {
            return null;
        }
        for (final int slot : new int[] {OUTPUT, OVERFLOW})
        {
            final IFluidHandlerItem h = fluidHandler(mbe, slot);
            if (h != null && !h.getFluidInTank(0).isEmpty())
            {
                return h.getFluidInTank(0).getFluid();
            }
        }
        return null;
    }

    @Override
    public boolean seatContainers(final BlockPos pos, final ItemStack outputMold, final ItemStack overflowMold)
    {
        final HeatForgeBlockEntity mbe = level == null ? null : memberAt(level, pos);
        if (mbe == null)
        {
            return false;
        }
        boolean seated = false;
        if (!outputMold.isEmpty() && mbe.positions.getStackInSlot(OUTPUT).isEmpty())
        {
            mbe.positions.setStackInSlot(OUTPUT, outputMold.copyWithCount(1));
            seated = true;
        }
        if (!overflowMold.isEmpty() && mbe.positions.getStackInSlot(OVERFLOW).isEmpty())
        {
            mbe.positions.setStackInSlot(OVERFLOW, overflowMold.copyWithCount(1));
            seated = true;
        }
        if (seated)
        {
            mbe.setChanged();
        }
        return seated;
    }

    private static IFluidHandlerItem fluidHandler(final HeatForgeBlockEntity mbe, final int slot)
    {
        return mbe.positions.getStackInSlot(slot).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
    }

    private static int freeCap(final HeatForgeBlockEntity mbe, final int slot)
    {
        final IFluidHandlerItem h = fluidHandler(mbe, slot);
        return h == null ? 0 : Math.max(0, h.getTankCapacity(0) - h.getFluidInTank(0).getAmount());
    }

    private static boolean moldFull(final HeatForgeBlockEntity mbe, final int slot)
    {
        final IFluidHandlerItem h = fluidHandler(mbe, slot);
        return h != null && !h.getFluidInTank(0).isEmpty() && h.getFluidInTank(0).getAmount() >= h.getTankCapacity(0);
    }

    @Override
    public ForgeState state(final BlockPos pos)
    {
        if (level == null)
        {
            return ForgeState.EMPTY;
        }
        final HeatForgeBlockEntity mbe = memberAt(level, pos);
        if (mbe == null)
        {
            return ForgeState.EMPTY;
        }
        final ItemStack heat = mbe.positions.getStackInSlot(HEAT);
        if (heat.isEmpty())
        {
            return ForgeState.EMPTY;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(heat);
        if (recipe == null)
        {
            return ForgeState.INVALID;
        }
        if (!recipe.getDisplayOutputFluid().isEmpty())
        {
            // Melt (Smelter): needs a mold; then accumulate → cast; both molds full is a drain hold.
            if (fluidHandler(mbe, OUTPUT) == null && fluidHandler(mbe, OVERFLOW) == null)
            {
                return ForgeState.READY_NO_MOLD;
            }
            if (deviceTemp < recipe.getTemperature())
            {
                return ForgeState.COLD;
            }
            if (containerFreeCapacity(pos) <= 0)
            {
                return ForgeState.BLOCKED;
            }
            return moldFull(mbe, OUTPUT) ? ForgeState.CASTING : ForgeState.ACCUMULATING;
        }
        // Cook: both output + overflow occupied is a drain hold.
        if (!mbe.positions.getStackInSlot(OUTPUT).isEmpty() && !mbe.positions.getStackInSlot(OVERFLOW).isEmpty())
        {
            return ForgeState.BLOCKED;
        }
        return deviceTemp < recipe.getTemperature() ? ForgeState.COLD : ForgeState.HEATING;
    }

    @Override
    public boolean hasFinished()
    {
        if (level == null)
        {
            return false;
        }
        for (final BlockPos m : group().members())
        {
            final HeatForgeBlockEntity mbe = memberAt(level, m);
            if (mbe != null && (isDrainable(mbe.positions.getStackInSlot(OUTPUT)) || isDrainable(mbe.positions.getStackInSlot(OVERFLOW))))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ItemStack> takeFinished()
    {
        final List<ItemStack> out = new ArrayList<>();
        if (level == null)
        {
            return out;
        }
        for (final BlockPos m : group().members())
        {
            final HeatForgeBlockEntity mbe = memberAt(level, m);
            if (mbe == null)
            {
                continue;
            }
            for (final int slot : new int[] {OUTPUT, OVERFLOW})
            {
                final ItemStack s = mbe.positions.getStackInSlot(slot);
                if (isDrainable(s))
                {
                    out.add(s);
                    mbe.positions.setStackInSlot(slot, ItemStack.EMPTY);
                    mbe.setChanged();
                }
            }
            mbe.normalizeMolds();
        }
        return out;
    }

    /**
     * Whether an output/overflow item is finished and should be hauled out: a <b>full</b> mold (metal at capacity,
     * ready to cool + cast) or any <b>non-fluid</b> output (cooked food). An empty or partial mold stays seated.
     */
    private static boolean isDrainable(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }
        final IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler == null)
        {
            return true; // a cooked-food (non-fluid) output — always drainable
        }
        final FluidStack fluid = handler.getFluidInTank(0);
        return !fluid.isEmpty() && fluid.getAmount() >= handler.getTankCapacity(0);
    }

    /** Keep the ≤1-partial invariant: if the output slot holds no metal but the overflow does, swap them so the
     * partial lives in the output (§8). No-op for cook (no molds). */
    private void normalizeMolds()
    {
        final ItemStack out = positions.getStackInSlot(OUTPUT);
        final ItemStack over = positions.getStackInSlot(OVERFLOW);
        if (moldNoFluid(out) && moldHasFluid(over))
        {
            positions.setStackInSlot(OUTPUT, over);
            positions.setStackInSlot(OVERFLOW, out);
            setChanged();
        }
    }

    /** A slot with no molten metal: empty, or a fluid container holding nothing. */
    private static boolean moldNoFluid(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return true;
        }
        final IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return handler != null && handler.getFluidInTank(0).isEmpty();
    }

    private static boolean moldHasFluid(final ItemStack stack)
    {
        final IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return handler != null && !handler.getFluidInTank(0).isEmpty();
    }

    @Override
    public List<ItemStack> takeUnprocessable()
    {
        final List<ItemStack> out = new ArrayList<>();
        if (level == null)
        {
            return out;
        }
        for (final BlockPos m : group().members())
        {
            final HeatForgeBlockEntity mbe = memberAt(level, m);
            if (mbe == null)
            {
                continue;
            }
            final ItemStack heat = mbe.positions.getStackInSlot(HEAT);
            if (!heat.isEmpty() && HeatingRecipe.getRecipe(heat) == null)
            {
                out.add(heat);
                mbe.positions.setStackInSlot(HEAT, ItemStack.EMPTY);
                mbe.setChanged();
            }
        }
        return out;
    }

    @Override
    public List<BlockPos> members()
    {
        return group().members();
    }

    @Override
    public BlockPos controllerPos()
    {
        return group().controller();
    }

    // === Drops & persistence ================================================================================

    /** Items that drop when <b>this</b> block breaks: its own position slots, plus the fuel column if it's holding one. */
    public List<ItemStack> dropContents()
    {
        final List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < POSITION_SLOTS; i++)
        {
            final ItemStack s = positions.getStackInSlot(i);
            if (!s.isEmpty())
            {
                drops.add(s);
            }
        }
        for (int i = 0; i < FUEL_SLOTS; i++)
        {
            final ItemStack s = fuel.getStackInSlot(i);
            if (!s.isEmpty())
            {
                drops.add(s);
            }
        }
        return drops;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.put("Positions", positions.serializeNBT());
        tag.put("Fuel", fuel.serializeNBT());
        tag.putFloat("DeviceTemp", deviceTemp);
        tag.putBoolean("Lit", lit);
        tag.putInt("BurnTicks", burnTicks);
        tag.putFloat("BurnTemp", burnTemperature);
        tag.putInt("LevelBonus", levelBonus);
        tag.putLong("LastActive", lastActiveTick);
    }

    @Override
    public void load(final CompoundTag tag)
    {
        super.load(tag);
        if (tag.contains("Positions"))
        {
            positions.deserializeNBT(tag.getCompound("Positions"));
        }
        if (tag.contains("Fuel"))
        {
            fuel.deserializeNBT(tag.getCompound("Fuel"));
        }
        deviceTemp = tag.getFloat("DeviceTemp");
        lit = tag.getBoolean("Lit");
        burnTicks = tag.getInt("BurnTicks");
        burnTemperature = tag.getFloat("BurnTemp");
        levelBonus = tag.getInt("LevelBonus");
        lastActiveTick = tag.getLong("LastActive");
    }
}
