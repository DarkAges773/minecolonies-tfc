package com.mctfc.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.FoodUtils;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
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
 * </ul>
 *
 * {@code @Mixin(remap = false)} — {@code FoodUtils} is MineColonies' own class.
 */
@Mixin(value = FoodUtils.class, remap = false)
public class MixinFoodUtils
{
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
