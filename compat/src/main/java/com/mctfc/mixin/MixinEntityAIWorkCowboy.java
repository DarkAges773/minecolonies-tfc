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
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.COWBOY_STEW;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.INVENTORY_FULL;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

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
        final IBuilding building = ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        final ItemStack selected = TfcHerd.milkContainerFor(building.getSetting(BuildingCowboy.MILK_ITEM).getValue());

        // The worker milks into the SELECTED container, which it must hold in its own inventory. If it doesn't, pull
        // one from the hut's racks — mirroring vanilla `milkCows` (we bypass that whole body, so we must do the
        // transfer ourselves); if the hut hasn't got one either, wait for the courier instead of milking for nothing.
        ItemStack container = TfcHerd.findEmptyMilkContainer(worker.getInventoryCitizen(), selected);
        if (container.isEmpty())
        {
            if (!selected.isEmpty()
                  && InventoryUtils.hasBuildingEnoughElseCount(building, new ItemStorage(selected), 1) > 0
                  && ((AbstractEntityAIBasicInvoker) this).mctfc$walkToBuilding())
            {
                self.checkAndTransferFromHut(selected);
                container = TfcHerd.findEmptyMilkContainer(worker.getInventoryCitizen(), selected);
            }
            if (container.isEmpty())
            {
                cir.setReturnValue(DECIDE); // no empty selected container in worker or hut — wait for delivery
                return;
            }
        }
        // Visually carry the empty container while approaching/milking, mirroring vanilla milkCows' hand handling
        // (it equips the input bucket, then swaps the hand to the milk output). The fill itself goes through the
        // fake player; this just keeps the worker's shown item consistent instead of leaving a stale tool in hand.
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, self.getItemSlot(container.getItem()));
        if (self.walkingToAnimal(animal))
        {
            cir.setReturnValue(self.getState()); // keep walking toward the animal
            return;
        }

        worker.swing(InteractionHand.MAIN_HAND);
        final ItemStack filled = TfcHerd.milk(animal, container, ((AbstractEntityAIBasicInvoker) this).mctfc$getFakePlayer());
        if (!filled.isEmpty())
        {
            InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), container);
            InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), filled);
            CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, self.getItemSlot(filled.getItem())); // swap to the filled container

            building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).onMilked();
            worker.getCitizenExperienceHandler().addExperience(1.0);
            self.incrementActionsDoneAndDecSaturation();
            cir.setReturnValue(INVENTORY_FULL); // bank the milk to the hut after milking, like vanilla milkCows
            return;
        }
        cir.setReturnValue(DECIDE);
    }

    /**
     * Drop the mooshroom-stew attempt in a TFC world. The Cowhand's {@code decideWhatToDo} routes to
     * {@code COWBOY_STEW} purely on {@code canTryToStew()} — it never checks that a {@code MushroomCow} exists — so
     * without mooshrooms (TFC spawns none) the worker keeps entering a stew state that can never produce anything. We
     * cancel that route when no mooshroom is around, leaving the vanilla stew path intact if one ever is.
     */
    @Inject(method = "decideWhatToDo", at = @At("RETURN"), cancellable = true)
    private void mctfc$noStewWithoutMooshroom(final CallbackInfoReturnable<IAIState> cir)
    {
        if (cir.getReturnValue() == COWBOY_STEW)
        {
            final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
            if (self.searchForAnimals(a -> a instanceof MushroomCow).isEmpty())
            {
                cir.setReturnValue(START_WORKING);
            }
        }
    }

    /**
     * Have the hut request the <b>selected</b> milk container (the hut's Milk Item setting — a TFC fluid container,
     * ceramic jug by default) when it tends TFC dairy animals, so the courier keeps the Cowhand stocked. Each TFC
     * milking consumes an empty container (it becomes a <i>filled</i> one), so we stock the hut's
     * {@code MILKING_AMOUNT} (a full milking cycle's worth), gated on {@code canTryToMilk()} exactly like vanilla's
     * input-container request.
     */
    @Inject(method = "getExtraItemsNeeded", at = @At("RETURN"))
    private void mctfc$requestMilkContainer(final CallbackInfoReturnable<List<ItemStorage>> cir)
    {
        final AbstractEntityAIHerder<?, ?> self = (AbstractEntityAIHerder<?, ?>) (Object) this;
        final IBuilding building = ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        if (building.getFirstModuleOccurance(BuildingCowboy.HerdingModule.class).canTryToMilk()
              && !self.searchForAnimals(TfcHerd::isDairy).isEmpty())
        {
            final ItemStack container = TfcHerd.milkContainerFor(building.getSetting(BuildingCowboy.MILK_ITEM).getValue());
            if (!container.isEmpty())
            {
                final int amount = Math.max(1, building.getSetting(BuildingCowboy.MILKING_AMOUNT).getValue());
                cir.getReturnValue().add(new ItemStorage(container, amount));
            }
        }
    }
}
