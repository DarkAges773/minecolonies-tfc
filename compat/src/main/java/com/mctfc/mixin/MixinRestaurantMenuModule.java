package com.mctfc.mixin;

import com.mctfc.Config;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the dining hall's food stock target to actual colony demand instead of stack size, so the Chef doesn't
 * bulk-cook perishable TFC food that rots in storage.
 *
 * <p>Vanilla {@code RestaurantMenuModule#onColonyTick} requests each menu item up to
 * {@code target = itemStack.getMaxStackSize() * getExpectedStock()} (the dining hall registers
 * {@code getExpectedStock = buildingLevel}), and keeps the same amount ({@code alterItemsToBeKept}). For
 * 32-stacking TFC food at a level-5 hut that's 160 of <i>every</i> dish — fine for inert vanilla food, a rotting
 * pile in TFC.
 *
 * <p>We replace it with a demand-scaled per-item target = {@code clamp(round(citizens * perCitizen), min, max)}
 * (config), by:
 * <ul>
 *   <li>{@code getExpectedStock} HEAD-cancellable → return that demand target, and</li>
 *   <li>redirecting the {@code getMaxStackSize()} factor to 1 at the two target/keep sites — leaving the
 *       <i>per-request</i> {@code getMaxStackSize()} (the batch cap, ordinal 1 in {@code onColonyTick}) intact, so
 *       {@code target = 1 * demand = demand}.</li>
 * </ul>
 *
 * <p>All gated on {@code canCook} so only the <b>dining hall</b> is affected — the netherminer menu (the other
 * {@code RestaurantMenuModule}, {@code canCook=false}, {@code expectedStock=1}) keeps vanilla behaviour.
 * {@code remap = false}: MineColonies' own class.
 */
@Mixin(value = RestaurantMenuModule.class, remap = false)
public abstract class MixinRestaurantMenuModule
{
    @Shadow
    @Final
    private boolean canCook;

    @Inject(method = "getExpectedStock", at = @At("HEAD"), cancellable = true)
    private void mctfc$demandStock(final CallbackInfoReturnable<Integer> cir)
    {
        if (canCook)
        {
            cir.setReturnValue(mctfc$demandTarget());
        }
    }

    @Redirect(
        method = "onColonyTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I", ordinal = 0))
    private int mctfc$dropStackMultiplierForTarget(final ItemStack stack)
    {
        // ordinal 0 = the `target = maxStackSize * expectedStock` factor; the later (ordinal 1) getMaxStackSize
        // is the per-request batch cap and stays real.
        return canCook ? 1 : stack.getMaxStackSize();
    }

    @Redirect(
        method = "alterItemsToBeKept",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int mctfc$dropStackMultiplierForKeep(final ItemStack stack)
    {
        return canCook ? 1 : stack.getMaxStackSize();
    }

    @Unique
    private int mctfc$demandTarget()
    {
        // getBuilding() is inherited from AbstractBuildingModule — cast `this` rather than @Shadow it (the
        // Mixin AP can't resolve inherited members; see the @Shadow notes in CLAUDE.md).
        final IBuilding building = ((AbstractBuildingModule) (Object) this).getBuilding();
        if (building == null || building.getColony() == null)
        {
            return Config.diningHallStockMin;
        }
        final IColony colony = building.getColony();
        final int eaters = colony.getCitizenManager().getCurrentCitizenCount();
        final int scaled = (int) Math.ceil(eaters * Config.diningHallStockPerCitizen);
        return Mth.clamp(scaled, Config.diningHallStockMin, Config.diningHallStockMax);
    }
}
