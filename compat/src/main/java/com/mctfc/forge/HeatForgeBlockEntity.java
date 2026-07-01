package com.mctfc.forge;

import com.mctfc.Config;
import com.mctfc.cook.CookRecipes;
import com.mctfc.forge.ForgeMultiblock.Group;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.Fuel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    /** Controller-only: the shared 5-slot fuel column (dormant on a follower). */
    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS);

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

        be.burnStep();
        be.climbTemp();
        for (final HeatForgeBlockEntity member : members)
        {
            be.processCookPosition(level, member);
        }
        be.driveLitState(level, group.members());
        be.setChanged();
    }

    /** Burn the bottom fuel: count down the current item, igniting the next from the bottom slot when it's spent. */
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
            fuel.extractItem(0, 1, false);
            if (fuel.getStackInSlot(0).isEmpty())
            {
                cascadeFuel();
            }
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
     * Advance one position along the cook path: warm the heat-slot item toward the device temperature and, once it
     * reaches its recipe temperature, produce one cooked food into the output (then overflow) slot. Only item-output
     * heating recipes cook here; fluid-output (ore) belongs to the Smelter melt path (a later slice).
     */
    private void processCookPosition(final Level level, final HeatForgeBlockEntity member)
    {
        final ItemStack heat = member.positions.getStackInSlot(HEAT);
        if (heat.isEmpty())
        {
            return;
        }
        final HeatingRecipe recipe = CookRecipes.cookRecipe(heat);
        if (recipe == null)
        {
            return; // EMPTY handled above; non-cookable is INVALID / (future) a melt job — nothing to advance
        }
        final float required = recipe.getTemperature();
        if (deviceTemp < required)
        {
            return; // COLD — warming up, or genuinely too cool; no progress, no consumption
        }
        final float current = HeatCapability.getTemperature(heat);
        final float next = Math.min(deviceTemp, current + Config.forgeItemHeatPerTick);
        HeatCapability.setTemperature(heat, next);
        if (next < required)
        {
            member.setChanged();
            return;
        }
        // Reached temperature — cook one piece (decay carried by CookRecipes.cook) into output/overflow.
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
        heat.shrink(1);
        if (heat.isEmpty())
        {
            member.positions.setStackInSlot(HEAT, ItemStack.EMPTY);
        }
        else
        {
            HeatCapability.setTemperature(heat, 0f); // the next raw piece starts cool
        }
        lastActiveTick = level.getGameTime();
        member.setChanged();
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
        final HeatingRecipe recipe = CookRecipes.cookRecipe(heat);
        if (recipe == null)
        {
            return ForgeState.INVALID; // slice 1: only item-output (cook) recipes are handled
        }
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
            if (mbe != null && (!mbe.positions.getStackInSlot(OUTPUT).isEmpty() || !mbe.positions.getStackInSlot(OVERFLOW).isEmpty()))
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
                if (!s.isEmpty())
                {
                    out.add(s);
                    mbe.positions.setStackInSlot(slot, ItemStack.EMPTY);
                    mbe.setChanged();
                }
            }
        }
        return out;
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
