package com.mctfc.mixin;

import net.dries007.tfc.common.blockentities.TFCChestBlockEntity;
import net.dries007.tfc.common.container.RestrictedChestContainer;
import net.dries007.tfc.common.container.TFCContainerTypes;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the vanilla barrel behave like a {@code tfc:chest}: <b>18 slots</b> (two rows) instead of 27, and the
 * <b>same item-size restriction</b> — only items at or below {@code TFCConfig.SERVER.chestMaximumItemSize}
 * (default LARGE) fit. So the (now TFC-craftable) vanilla barrel follows TFC's storage rules instead of being
 * an oversized cheat container.
 *
 * <p>The GUI restriction can't be done with {@code Container#canPlaceItem} — vanilla {@code ChestMenu} slots use
 * the base {@code Slot#mayPlace} (always {@code true}) and never consult it. So we reuse TFC's own
 * {@link RestrictedChestContainer} (its {@code RestrictedSlot#mayPlace} calls the static
 * {@link TFCChestBlockEntity#isValid}) with TFC's {@code CHEST_9x2} menu type — the client then opens TFC's
 * 2-row chest screen for the barrel. The backing list ({@code <init>} 27→18) and {@code getContainerSize} (→18)
 * are kept in agreement (the {@code RestrictedChestContainer} ctor asserts an 18-slot container). {@code
 * canPlaceItem} reuses the same static {@code isValid} so hoppers and the Forge item-handler wrapper honour the
 * limit too. Targets a vanilla class, so this mixin is remapped (unlike the MineColonies {@code remap = false}
 * ones). No runtime config toggle: the list size is fixed at construction, so a live flip would desync.
 */
@Mixin(BarrelBlockEntity.class)
public abstract class MixinBarrelBlockEntity
{
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 27))
    private int mctfc$barrelSlots(final int original)
    {
        return 18;
    }

    @Inject(method = "getContainerSize", at = @At("HEAD"), cancellable = true)
    private void mctfc$containerSize(final CallbackInfoReturnable<Integer> cir)
    {
        cir.setReturnValue(18);
    }

    @Inject(
        method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
        at = @At("HEAD"),
        cancellable = true)
    private void mctfc$tfcChestMenu(final int id, final Inventory playerInventory, final CallbackInfoReturnable<AbstractContainerMenu> cir)
    {
        cir.setReturnValue(new RestrictedChestContainer(TFCContainerTypes.CHEST_9x2.get(), id, playerInventory, (Container) (Object) this, 2));
    }

    /** Honour the chest size limit for hoppers / the item-handler wrapper (same rule as TFC's chest slots). */
    public boolean canPlaceItem(final int slot, final ItemStack stack)
    {
        return TFCChestBlockEntity.isValid(stack);
    }
}
