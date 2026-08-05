package com.mctfc.forge;

import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.inventory.ModMenus;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.items.MoldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The "one big furnace" container for a heat-forge multiblock (see {@code docs/tfc-forge-multiblock.md} §4). Presents
 * the controller's aggregate: the shared 5-slot fuel column + a live temperature gauge on the left, and up to five
 * vertically-stacked processing rows (one per member position: {@code heat → output → overflow}) in the centre. It's a
 * <b>native</b> {@link AbstractContainerMenu} (not BlockUI — this is a world block), shared by player and worker.
 *
 * <p>Slots reference the real BE handlers server-side and dummy handlers client-side (the container syncs stacks by
 * index either way); {@code deviceTemp} + lit ride a {@link ContainerData}. If the multiblock changes shape while open,
 * {@link #stillValid} closes it (the layout is fixed at open time).
 */
public class ForgeMenu extends AbstractContainerMenu
{
    // Slot layout (pixels).
    private static final int FUEL_X = 8;
    private static final int FUEL_BOTTOM_Y = 90; // slot 0 (the burning slot) sits at the bottom of the column
    // TFC forge/firepit gauge dimensions (15 wide × 50 tall — matching TFC's gradient, which is 1px shorter than the
    // scale's 0..51 range), bottom-anchored so the cold end is fixed and the marker's travel lines up with the painted
    // gradient (bottom = cold). Bottom kept at y≈88 (as generated); GAUGE_Y = 88 − 50. Horizontal position unchanged.
    static final int GAUGE_X = 28;
    static final int GAUGE_Y = 38;
    static final int GAUGE_W = 15;
    static final int GAUGE_H = 50;
    static final int HEAT_X = 48;
    static final int OUTPUT_X = 76;   // output + overflow shifted 6px right of the heat slot (separates input from output)
    static final int OVERFLOW_X = 98;
    static final int ROW_Y = 18;
    private static final int INV_X = 8;
    private static final int INV_Y = 124; // player inventory dropped 6px (see ForgeScreen imageHeight) for label room
    private static final int HOTBAR_Y = 182;

    private final BlockPos controllerPos;
    private final int memberCount;
    private final ContainerData data;
    private final int machineSlotCount;

    /** Server factory — slots reference the live controller + member BE handlers. */
    public ForgeMenu(final int windowId, final Inventory inv, final HeatForgeBlockEntity controller)
    {
        super(ModMenus.HEAT_FORGE.get(), windowId);
        this.controllerPos = controller.getBlockPos();
        final List<IItemHandler> positionHandlers = new ArrayList<>();
        for (final BlockPos m : controller.members())
        {
            final HeatForgeBlockEntity mbe = controller.memberEntity(m);
            positionHandlers.add(mbe != null ? mbe.positions() : new ItemStackHandler(3));
        }
        this.memberCount = positionHandlers.size();
        this.data = new ContainerData()
        {
            @Override public int get(final int i) { return i == 0 ? Math.round(controller.deviceTemp()) : (controller.isLit() ? 1 : 0); }
            @Override public void set(final int i, final int v) { }
            @Override public int getCount() { return 2; }
        };
        this.machineSlotCount = HeatForgeBlockEntity.FUEL_SLOTS + memberCount * 3;
        layout(inv, controller.fuelHandler(), positionHandlers);
    }

    /** Client factory — dummy handlers of matching size; the container sync fills them by index. */
    public ForgeMenu(final int windowId, final Inventory inv, final BlockPos controllerPos, final int memberCount)
    {
        super(ModMenus.HEAT_FORGE.get(), windowId);
        this.controllerPos = controllerPos;
        this.memberCount = memberCount;
        this.data = new SimpleContainerData(2);
        this.machineSlotCount = HeatForgeBlockEntity.FUEL_SLOTS + memberCount * 3;
        final List<IItemHandler> positionHandlers = new ArrayList<>();
        for (int i = 0; i < memberCount; i++)
        {
            positionHandlers.add(new ItemStackHandler(3));
        }
        layout(inv, new ItemStackHandler(HeatForgeBlockEntity.FUEL_SLOTS), positionHandlers);
    }

    public static ForgeMenu fromBuffer(final int windowId, final Inventory inv, final FriendlyByteBuf buf)
    {
        final BlockPos pos = buf.readBlockPos();
        final int members = buf.readVarInt();
        return new ForgeMenu(windowId, inv, pos, members);
    }

    /** Write the controller position + member count so the client factory reproduces the same slot layout. */
    public static void write(final FriendlyByteBuf buf, final HeatForgeBlockEntity controller)
    {
        buf.writeBlockPos(controller.getBlockPos());
        buf.writeVarInt(controller.members().size());
    }

    private void layout(final Inventory inv, final IItemHandler fuel, final List<IItemHandler> positions)
    {
        // Fuel column (index 0 = bottom = the burning slot), any TFC fuel.
        for (int i = 0; i < HeatForgeBlockEntity.FUEL_SLOTS; i++)
        {
            addSlot(new FilterSlot(fuel, i, FUEL_X, FUEL_BOTTOM_Y - i * 18, FurnaceFuel::isFuel));
        }
        // Processing rows: heat (any heatable item) → output/overflow (any fluid container, e.g. a mold).
        for (int r = 0; r < positions.size(); r++)
        {
            final IItemHandler h = positions.get(r);
            final int y = ROW_Y + r * 18;
            addSlot(new FilterSlot(h, HeatForgeBlockEntity.HEAT, HEAT_X, y, HeatCapability::has));
            addSlot(new FilterSlot(h, HeatForgeBlockEntity.OUTPUT, OUTPUT_X, y, ForgeMenu::isMoldOrVessel));
            addSlot(new FilterSlot(h, HeatForgeBlockEntity.OVERFLOW, OVERFLOW_X, y, ForgeMenu::isMoldOrVessel));
        }
        // Player inventory + hotbar.
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++)
        {
            addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    /** Output/overflow accept a mold (or any fluid vessel). Detected by item class first — TFC empty molds don't
     * reliably expose {@code FLUID_HANDLER_ITEM} client-side, where {@code mayPlace} is first checked. */
    private static boolean isMoldOrVessel(final ItemStack stack)
    {
        return !stack.isEmpty()
                && (stack.getItem() instanceof MoldItem
                        || stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent());
    }

    /** The controller BE this menu is bound to, or {@code null} if it's gone (server-side check). */
    private HeatForgeBlockEntity controller(final Player player)
    {
        return player.level().getBlockEntity(controllerPos) instanceof HeatForgeBlockEntity be ? be : null;
    }

    public int displayTemp()
    {
        return data.get(0);
    }

    public boolean displayLit()
    {
        return data.get(1) != 0;
    }

    public int memberRows()
    {
        return memberCount;
    }

    @Override
    public boolean stillValid(final Player player)
    {
        if (player.level().isClientSide)
        {
            return true;
        }
        final HeatForgeBlockEntity be = controller(player);
        // Close if the block is gone, is no longer the controller, or the multiblock changed size (layout is fixed).
        return be != null && be.getBlockPos().equals(be.controllerPos()) && be.members().size() == memberCount
                && player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index)
    {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack copy = stack.copy();
        final int invEnd = slots.size();
        if (index < machineSlotCount)
        {
            // machine → player inventory
            if (!moveItemStackTo(stack, machineSlotCount, invEnd, true))
            {
                return ItemStack.EMPTY;
            }
        }
        else if (!moveItemStackTo(stack, 0, machineSlotCount, false))
        {
            // player → machine (moveItemStackTo respects each slot's mayPlace filter)
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty())
        {
            slot.set(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return copy;
    }

    /** A {@link SlotItemHandler} with an insert filter (and the handler's own 1-item limit for position slots). */
    private static class FilterSlot extends SlotItemHandler
    {
        private final Predicate<ItemStack> valid;

        FilterSlot(final IItemHandler handler, final int index, final int x, final int y, final Predicate<ItemStack> valid)
        {
            super(handler, index, x, y);
            this.valid = valid;
        }

        @Override
        public boolean mayPlace(final ItemStack stack)
        {
            return valid.test(stack) && super.mayPlace(stack);
        }
    }
}
