package com.mctfc.mixin;

import com.mctfc.Config;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.FoodUtils;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * TFC freshness awareness for MineColonies food selection. Both citizen eating and the cook funnel through
 * {@link FoodUtils#canEat} and {@link FoodUtils#getBestFoodForCitizen}, so two hooks cover the whole flow.
 *
 * <ul>
 *   <li><b>Skip rotten</b> — {@code canEat} returns {@code false} for a rotten TFC stack, so citizens, the cook and
 *       the building food scan never select rotten food.</li>
 *   <li><b>FIFO tiebreaker</b> — after MineColonies picks a slot, swap to another slot holding the <i>same item</i>
 *       (hence the same desirability score — a true tiebreaker that preserves MC's diet-variety logic) but with a
 *       sooner rot date, so the colony eats the food closest to spoiling first.</li>
 *   <li><b>TFC nutrition bridge</b> — TFC food carries only a flat vanilla {@code FoodProperties} (nutrition 4,
 *       saturation 0.3); its real nutrition lives in the TFC {@code FoodData} capability. MineColonies' value calc
 *       reads the flat vanilla nutrition and applies a 0.25&times; non-MC-food nerf, so every TFC food fed a citizen
 *       barely any saturation (~0.83). We recompute the value from TFC's own {@code FoodData}.</li>
 * </ul>
 *
 * {@code @Mixin(remap = false)} — {@code FoodUtils} is MineColonies' own class.
 */
@Mixin(value = FoodUtils.class, remap = false)
public class MixinFoodUtils
{
    /**
     * Value TFC food from its real {@code FoodData} instead of the flat, nerfed vanilla nutrition. Uses TFC's
     * {@code hunger} (fullness restored) scaled by {@code saturation} (TFC's quality signal), dropping MineColonies'
     * 0.25&times; non-MC-food penalty but keeping the {@code /1.2} and research-bonus scaling so it stays comparable
     * to MineColonies' own food. Non-TFC food falls through to the vanilla MineColonies calc unchanged.
     */
    @Inject(method = "getFoodValue(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;D)D", at = @At("HEAD"), cancellable = true)
    private static void mctfc$tfcFoodValue(final ItemStack foodStack, final FoodProperties itemFood, final double researchBonus, final CallbackInfoReturnable<Double> cir)
    {
        if (itemFood == null || !FoodCapability.has(foodStack))
        {
            return;
        }
        final IFood food = FoodCapability.get(foodStack);
        if (food == null)
        {
            return;
        }
        final FoodData data = food.getData();
        final double base = Math.max(data.hunger(), 1);
        final double quality = 1.0 + Math.max(0.0, data.saturation());
        cir.setReturnValue(base * quality / 1.2 * (1.0 + researchBonus) * Config.tfcFoodSaturationModifier);
    }

    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private static void mctfc$skipRotten(final ItemStack stack, final IBuilding homeBuilding, final IBuilding workBuilding, final CallbackInfoReturnable<Boolean> cir)
    {
        if (FoodCapability.isRotten(stack))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getBestFoodForCitizen", at = @At("RETURN"), cancellable = true)
    private static void mctfc$fifoTiebreak(final InventoryCitizen inventoryCitizen, final ICitizenData citizenData, final Set<ItemStorage> menu, final CallbackInfoReturnable<Integer> cir)
    {
        final int chosen = cir.getReturnValue();
        if (chosen < 0)
        {
            return;
        }
        final ItemStack chosenStack = inventoryCitizen.getStackInSlot(chosen);
        final IFood chosenFood = FoodCapability.get(chosenStack);
        if (chosenFood == null)
        {
            return;
        }

        long soonestRot = chosenFood.getRottenDate();
        int bestSlot = chosen;
        for (int i = 0; i < inventoryCitizen.getSlots(); i++)
        {
            if (i == chosen)
            {
                continue;
            }
            final ItemStack candidate = inventoryCitizen.getStackInSlot(i);
            // Same item == same eating-desirability score, so this only ever breaks ties (never overrides MC's pick).
            if (candidate.getItem() != chosenStack.getItem() || FoodCapability.isRotten(candidate))
            {
                continue;
            }
            final IFood food = FoodCapability.get(candidate);
            if (food == null)
            {
                continue;
            }
            if (food.getRottenDate() < soonestRot)
            {
                soonestRot = food.getRottenDate();
                bestSlot = i;
            }
        }

        if (bestSlot != chosen)
        {
            cir.setReturnValue(bestSlot);
        }
    }
}
