package com.mctfc.mixin;

import com.mctfc.food.FoodPreservation;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colony-storage food preservation, applied at the rack inventory. While food sits in a rack that belongs to a
 * colony building (warehouse rack, or a hut rack with a {@code buildingPos}), TFC food carries our
 * {@code mctfc:colony_storage} trait so it decays at the configured (reduced) rate; on withdrawal the trait is
 * stripped so the food decays normally again. Player-placed free-standing racks ({@code !inWarehouse} and no
 * {@code buildingPos}) are left untouched.
 *
 * <p>{@code @Mixin(remap = false)} — {@link AbstractTileEntityRack.RackInventory} is MineColonies' own inner class;
 * the {@code insertItem}/{@code onContentsChanged} method names are Forge's (also non-SRG). We extend
 * {@link ItemStackHandler} (the real superclass of {@code RackInventory}) so the {@code extractItem} soft-override
 * can call {@code super.extractItem}, and shadow the synthetic outer reference {@code this$0} to reach the rack.
 */
@Mixin(value = AbstractTileEntityRack.RackInventory.class, remap = false)
public abstract class MixinRackInventory extends ItemStackHandler
{
    @Shadow(aliases = "this$0")
    @Final
    AbstractTileEntityRack mctfc$outer;

    /** True only for a rack that is part of a colony building (server side). */
    private boolean mctfc$isColonyOwned()
    {
        final AbstractTileEntityRack rack = this.mctfc$outer;
        if (rack == null)
        {
            return false;
        }
        final Level level = rack.getLevel();
        if (level == null || level.isClientSide)
        {
            return false;
        }
        final AbstractTileEntityRackAccessor acc = (AbstractTileEntityRackAccessor) (Object) rack;
        return acc.mctfc$inWarehouse() || !acc.mctfc$buildingPos().equals(BlockPos.ZERO);
    }

    /** Tag the incoming stack before it is merged, so same-age food entering colony storage still stacks together. */
    @Inject(method = "insertItem", at = @At("HEAD"))
    private void mctfc$preserveOnInsert(final int slot, final ItemStack stack, final boolean simulate, final CallbackInfoReturnable<ItemStack> cir)
    {
        if (!simulate && mctfc$isColonyOwned())
        {
            FoodPreservation.markStored(stack);
        }
    }

    /** Catch every other change path (manual set, the post-merge slot) and ensure stored food carries the trait. */
    @Inject(method = "onContentsChanged", at = @At("TAIL"))
    private void mctfc$preserveOnChange(final int slot, final CallbackInfo ci)
    {
        if (mctfc$isColonyOwned())
        {
            FoodPreservation.markStored(getStackInSlot(slot));
        }
    }

    /** Strip the preservation trait from withdrawn food so it decays normally outside colony storage. */
    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        final ItemStack result = super.extractItem(slot, amount, simulate);
        if (!simulate && !result.isEmpty() && mctfc$isColonyOwned())
        {
            return FoodPreservation.clearStored(result);
        }
        return result;
    }
}
