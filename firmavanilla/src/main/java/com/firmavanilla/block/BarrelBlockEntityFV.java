package com.firmavanilla.block;

import net.dries007.tfc.common.blockentities.TFCChestBlockEntity;
import net.dries007.tfc.common.container.RestrictedChestContainer;
import net.dries007.tfc.common.container.TFCContainerTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block-entity behind the firmavanilla wood barrels: a vanilla {@link BarrelBlockEntity} shrunk to TFC's
 * chest rules — <b>18 slots</b> (two rows) instead of 27 and the <b>same item-size limit</b> (only items at or
 * below {@code TFCConfig.SERVER.chestMaximumItemSize}). This is the TFC-chest behaviour the (now-removed) compat
 * {@code MixinBarrelBlockEntity} used to force onto the <i>vanilla</i> barrel — moved here, applied only to our
 * own blocks, the way TFC ships small chests via its own block-entity rather than by mixing into vanilla. Vanilla
 * barrels/chests are untouched.
 *
 * <p>It reuses TFC's own {@link RestrictedChestContainer} + {@code CHEST_9x2} menu (its {@code RestrictedSlot}
 * calls {@link TFCChestBlockEntity#isValid}), so opening the barrel shows TFC's 2-row chest screen with the
 * size restriction — the same container the old mixin used. {@code canPlaceItem} reuses the same {@code isValid}
 * so hoppers / the Forge item-handler wrapper honour the limit too. (firmavanilla stays mixin-free; TFC is a
 * compile dependency.)
 *
 * <p><b>Why subclass + override {@link #getType()}.</b> {@code BarrelBlockEntity}'s only constructor hardcodes
 * {@code BlockEntityType.BARREL}, so a subclass instance reports that type — which on load would resolve back to
 * the <i>vanilla</i> factory (a plain 27-slot barrel). Overriding {@code getType()} to our own registered type
 * makes save/load round-trip through our factory instead. The shorter backing list is set in the constructor
 * (the super ctor builds a 27-slot one) and re-derived on load from {@link #getContainerSize()}.
 */
public class BarrelBlockEntityFV extends BarrelBlockEntity
{
    /** Two rows, matching TFC's small chest (and the {@code CHEST_9x2} menu). */
    public static final int ROWS = 2;
    public static final int SIZE = ROWS * 9;

    public BarrelBlockEntityFV(final BlockPos pos, final BlockState state)
    {
        super(pos, state);
        // The super ctor created a 27-slot list; replace it with an 18-slot one so it agrees with getContainerSize().
        setItems(NonNullList.withSize(SIZE, ItemStack.EMPTY));
    }

    @Override
    public BlockEntityType<?> getType()
    {
        // Our own type, not vanilla's BARREL the super ctor stamped in — so it persists/loads as a firmavanilla barrel.
        return BarrelBlocks.BARREL_BE.get();
    }

    @Override
    public int getContainerSize()
    {
        return SIZE;
    }

    @Override
    protected AbstractContainerMenu createMenu(final int id, final Inventory playerInventory)
    {
        // TFC's restricted 2-row chest container/screen, with this barrel as the backing container.
        return new RestrictedChestContainer(TFCContainerTypes.CHEST_9x2.get(), id, playerInventory, this, ROWS);
    }

    @Override
    public boolean canPlaceItem(final int slot, final ItemStack stack)
    {
        // Same size limit as TFC chest slots, so hoppers / the item-handler wrapper honour it too.
        return TFCChestBlockEntity.isValid(stack);
    }
}
