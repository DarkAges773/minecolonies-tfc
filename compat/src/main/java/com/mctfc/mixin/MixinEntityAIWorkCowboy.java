package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkCowboy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;

/**
 * Makes the Cowhand milk TFC dairy animals (cow/goat/yak) instead of only vanilla cows/goats. MineColonies'
 * {@code milkCows} hardcodes an {@code instanceof Cow || Goat} search and a fixed {@code getMilkInputItem →
 * getMilkOutputItem} swap (an empty vanilla bucket → a vanilla milk bucket). Both are wrong for TFC:
 *
 * <ul>
 *   <li>TFC dairy animals are custom {@code DairyAnimal}s, invisible to the vanilla predicate;</li>
 *   <li>milk is gated by TFC's {@code isReadyForAnimalProduct} (familiarity + product cooldown + adult/female);</li>
 *   <li>the milk fluid is chosen by TFC's {@code AnimalProductEvent} — which <b>FirmaLife</b> uses to give each
 *       animal its own variant (goat/yak milk) — and that event fires <b>only</b> inside TFC's {@code mobInteract}.
 *       A vanilla bucket can't even hold those variant fluids.</li>
 * </ul>
 *
 * So for TFC dairy we bypass the vanilla body: find a <i>ready</i> {@code DairyAnimal}, walk to it, and let
 * {@link TfcHerd#milk} drive TFC's own {@code mobInteract} with the worker's fake player holding a generic empty
 * fluid container (ceramic jug by default — see {@code getExtraItemsNeeded} below; any held container works). TFC
 * fires the event, fills the container, and sets the cooldown; we bank the filled container. When no TFC dairy is
 * present we fall through so vanilla cows still work. {@code @Mixin(remap = false)} — MineColonies' own class.
 * See {@code docs/tfc-herder-workers.md}.
 */
@Mixin(value = EntityAIWorkCowboy.class, remap = false)
public abstract class MixinEntityAIWorkCowboy
{
    @Inject(method = "milkCows", at = @At("HEAD"), cancellable = true)
    private void mctfc$tfcMilk(final CallbackInfoReturnable<IAIState> cir)
    {
        final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
        final Animal animal = self.searchForAnimals(TfcHerd::isReadyDairy).stream().findFirst().orElse(null);
        if (animal == null)
        {
            // TFC dairy present but none ready → our domain, skip vanilla milking. No TFC dairy → let vanilla run (vanilla cows).
            if (!self.searchForAnimals(TfcHerd::isDairy).isEmpty())
            {
                cir.setReturnValue(DECIDE);
            }
            return;
        }

        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) this).mctfc$worker();
        final ItemStack container = TfcHerd.findEmptyMilkContainer(worker.getInventoryCitizen());
        if (container.isEmpty())
        {
            cir.setReturnValue(DECIDE); // no empty container yet — the courier restocks requested jugs
            return;
        }
        if (self.walkingToAnimal(animal))
        {
            cir.setReturnValue(self.getState()); // keep walking toward the animal
            return;
        }

        final ItemStack filled = TfcHerd.milk(animal, container, ((AbstractEntityAIBasicInvoker) this).mctfc$getFakePlayer());
        if (!filled.isEmpty())
        {
            InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), container);
            InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), filled);

            final IBuilding building = ((AbstractEntityAIBasicInvoker) this).mctfc$building();
            building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).onMilked();
            worker.getCitizenExperienceHandler().addExperience(1.0);
        }
        cir.setReturnValue(DECIDE);
    }

    /**
     * Have the hut request a ceramic jug (the default milk container) when it tends TFC dairy animals, so the
     * courier keeps the Cowhand stocked — paralleling how vanilla requests buckets. Any held fluid container works
     * to milk; the jug is just the cheap default we ask for.
     */
    @Inject(method = "getExtraItemsNeeded", at = @At("RETURN"))
    private void mctfc$requestMilkContainer(final CallbackInfoReturnable<List<ItemStorage>> cir)
    {
        final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
        if (!self.searchForAnimals(TfcHerd::isDairy).isEmpty())
        {
            cir.getReturnValue().add(TfcHerd.milkContainerRequest());
        }
    }
}
