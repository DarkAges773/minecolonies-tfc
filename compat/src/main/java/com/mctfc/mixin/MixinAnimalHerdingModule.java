package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Makes MineColonies' herding huts (Cowhand / Shepherd / Swineherd / Chicken Herder / Rabbit Hutch) tend TFC
 * livestock, which the vanilla module never recognizes or feeds correctly:
 *
 * <ul>
 *   <li><b>{@code isCompatible}</b> — the module's animal predicate is hardcoded to {@code instanceof
 *       Cow/Sheep/Pig/Chicken}; TFC's cow/goat/yak/… are custom {@code TFCAnimal} subclasses, so they're invisible.
 *       We OR-in a per-job {@code #mctfc:herding/<job>} entity-type tag. (TFC rabbits already match the vanilla
 *       {@code Rabbit} predicate.) Counting, feed-target and butcher-target search all funnel through this method.</li>
 *   <li><b>{@code getBreedingItems}</b> — TFC animals don't eat vanilla wheat/carrot/seeds (each has its own
 *       {@code #tfc:*_food} tag). We return a TFC grain (in {@code #tfc:foods/grains}, accepted by every herded TFC
 *       species), so the hut requests/equips/feeds something TFC animals will actually eat. NBT-agnostic so TFC food
 *       decay doesn't break request matching.</li>
 * </ul>
 *
 * {@code @Mixin(remap = false)} — MineColonies' own class/members. See {@code docs/tfc-herder-workers.md}.
 */
@Mixin(value = AnimalHerdingModule.class, remap = false)
public abstract class MixinAnimalHerdingModule
{
    @Shadow
    @Final
    private JobEntry jobEntry;

    @Inject(method = "isCompatible", at = @At("HEAD"), cancellable = true)
    private void mctfc$recognizeTfcAnimals(final Animal animal, final CallbackInfoReturnable<Boolean> cir)
    {
        if (TfcHerd.recognizes(this.jobEntry, animal))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBreedingItems", at = @At("HEAD"), cancellable = true)
    private void mctfc$tfcBreedingFood(final CallbackInfoReturnable<List<ItemStorage>> cir)
    {
        if (TfcHerd.isHerderJob(this.jobEntry))
        {
            cir.setReturnValue(TfcHerd.breedingFood());
        }
    }
}
