package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingShepherd;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkShepherd;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.PREPARING;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.SHEPHERD_SHEAR;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

/**
 * Makes the Shepherd shear TFC wooly animals (sheep/alpaca/musk ox) instead of only vanilla sheep. MineColonies'
 * shearing hardcodes vanilla {@code Sheep}: {@code decideWhatToDo} only enters {@code SHEPHERD_SHEAR} when its
 * {@code findShearableSheep()} ({@code instanceof Sheep && !isSheared && !isBaby}) is non-null, and {@code shearSheep}
 * emits vanilla colored wool by {@code DyeColor}. Both miss TFC wooly animals (custom {@code WoolyAnimal}s) and
 * produce the wrong item.
 *
 * <ul>
 *   <li><b>decide routing</b> — when the worker is otherwise idle, {@code SHEARING} is on, and a <i>ready</i> TFC
 *       wooly animal is present, route to {@code SHEPHERD_SHEAR} (the vanilla {@code Sheep} check would say no);</li>
 *   <li><b>shear action</b> — shear a ready {@code WoolyAnimal} via {@link TfcHerd#shear}, which drives Forge's
 *       {@code IForgeShearable.onSheared} (TFC's own path): it respects familiarity + product cooldown, fires TFC's
 *       {@code AnimalProductEvent} (so FirmaLife/add-ons can vary the wool), and returns the TFC wool — which we bank
 *       and then damage the shears. No vanilla dyeing.</li>
 * </ul>
 *
 * When no TFC wooly animal is present we fall through so vanilla sheep still shear normally. {@code @Mixin(remap =
 * false)} — MineColonies' own class. See {@code docs/tfc-herder-workers.md}.
 */
@Mixin(value = EntityAIWorkShepherd.class, remap = false)
public abstract class MixinEntityAIWorkShepherd
{
    @Inject(method = "decideWhatToDo", at = @At("RETURN"), cancellable = true)
    private void mctfc$shearDecide(final CallbackInfoReturnable<IAIState> cir)
    {
        if (cir.getReturnValue() != START_WORKING)
        {
            return; // worker has other work (or already shearing) — don't override
        }
        final IBuilding building = ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        if (!building.getSetting(BuildingShepherd.SHEARING).getValue())
        {
            return;
        }
        final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
        if (!self.searchForAnimals(TfcHerd::isReadyWooly).isEmpty())
        {
            cir.setReturnValue(SHEPHERD_SHEAR);
        }
    }

    @Inject(method = "shearSheep", at = @At("HEAD"), cancellable = true)
    private void mctfc$tfcShear(final CallbackInfoReturnable<IAIState> cir)
    {
        final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
        final Animal animal = self.searchForAnimals(TfcHerd::isReadyWooly).stream().findFirst().orElse(null);
        if (animal == null)
        {
            // TFC wooly present but none ready → our domain; no TFC wooly → let vanilla shear vanilla sheep.
            if (!self.searchForAnimals(TfcHerd::isWooly).isEmpty())
            {
                cir.setReturnValue(DECIDE);
            }
            return;
        }
        if (!self.equipTool(InteractionHand.MAIN_HAND, ModEquipmentTypes.shears.get()))
        {
            cir.setReturnValue(PREPARING); // no shears yet — the courier restocks them
            return;
        }
        if (self.walkingToAnimal(animal))
        {
            cir.setReturnValue(self.getState()); // keep walking toward the animal
            return;
        }

        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) this).mctfc$worker();
        worker.swing(InteractionHand.MAIN_HAND);
        final List<ItemStack> wool = TfcHerd.shear(animal, worker.getMainHandItem(), ((AbstractEntityAIBasicInvoker) this).mctfc$getFakePlayer());
        for (final ItemStack drop : wool)
        {
            InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(drop, worker.getInventoryCitizen());
        }
        if (!wool.isEmpty())
        {
            CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
            worker.getCitizenExperienceHandler().addExperience(0.5);
        }
        cir.setReturnValue(DECIDE);
    }
}
