package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reworks the shared herder loop to TFC's familiarity husbandry for TFC livestock, while leaving vanilla animals
 * (and non-TFC worlds) on the original path. MineColonies breeds via vanilla love-mode
 * ({@code isBreedAble} → {@code canMate} → {@code setInLove}); TFC animals never enter vanilla love — TFC breeds
 * familiarized adults on its own (brain {@code BreedBehavior}). So for TFC the worker's whole job is to keep the
 * herd fed/familiar; TFC mates them.
 *
 * <p>The gate for TFC is {@link TfcHerd#isTendable} (adult/child, not {@code OLD}, and still {@code isHungry} —
 * TFC's once-per-day feed window). Feeding via TFC's {@code eatFood} clears hunger for the day, so a fed animal
 * isn't picked again until tomorrow.
 *
 * <p><b>Familiarization runs through the FEED state only, one animal at a time.</b> We do <i>not</i> let the
 * vanilla BREED state run for TFC animals: {@code isBreedAble} returns {@code false} for them, so BREED is skipped
 * (it still drives vanilla animals in a mixed herd). BREED feeds <i>two</i> animals in a single tick whenever both
 * are already adjacent — and TFC animals, tempted by the grain the worker holds, swarm and cluster — which read as
 * "feeding the whole herd at once." {@code feedAnimal}, which walks up to and feeds a single animal, is the sole,
 * consistent familiarizer. Its vanilla logic needs three fixes for TFC:
 * <ul>
 *   <li><b>selection</b> — MineColonies picks by its own {@code fedRecently} ~5-minute per-worker cooldown (far
 *       shorter than a TFC day) and by iteration order; we filter {@code searchForAnimals} so a TFC animal is
 *       eligible only while {@code isTendable} (hungry today), so the worker doesn't re-walk to / waste grain on
 *       animals already fed;</li>
 *   <li><b>action</b> — the vanilla feed only force-ages babies, never familiarizing adults; we familiarize the fed
 *       TFC animal (adult or baby) on the broadcast-eat event, which raises familiarity and clears hunger;</li>
 *   <li><b>aging</b> — we suppress {@code ageUp} for TFC babies (force-aging would corrupt TFC's calendar aging).</li>
 * </ul>
 *
 * {@code @Mixin(remap = false)} — MineColonies' own class/members; the redirected vanilla calls carry
 * {@code remap = true} on their {@code @At}. See {@code docs/tfc-herder-workers.md}.
 */
@Mixin(value = AbstractEntityAIHerder.class, remap = false)
public abstract class MixinAbstractEntityAIHerder
{
    @Shadow
    protected AnimalHerdingModule current_module;

    /**
     * Reach the worker's {@code FakePlayer} via {@link AbstractEntityAIBasicInvoker} — {@code getFakePlayer} is
     * declared up in {@code AbstractEntityAIBasic}, and a plain inherited {@code @Shadow} from this subclass fails
     * to apply (Mixin doesn't resolve inherited members), so we cast {@code this} to the invoker interface.
     */
    private FakePlayer mctfc$fakePlayer()
    {
        return ((AbstractEntityAIBasicInvoker) this).mctfc$getFakePlayer();
    }

    private void mctfc$familiarize(final Animal animal)
    {
        TfcHerd.familiarize(animal, current_module.getBreedingItems().get(0).getItemStack(), mctfc$fakePlayer());
    }

    /**
     * Keep TFC animals out of the vanilla BREED state — TFC breeds them itself once familiar, and the BREED loop's
     * feed-two-at-once behaviour is the "feeds the whole herd at once" the FEED-only path avoids. Vanilla animals
     * are unaffected, so a mixed herd still breeds its vanilla members normally.
     */
    @Inject(method = "isBreedAble", at = @At("HEAD"), cancellable = true)
    private static void mctfc$tfcSkipsVanillaBreeding(final Animal entity, final CallbackInfoReturnable<Boolean> cir)
    {
        if (TfcHerd.isTfc(entity))
        {
            cir.setReturnValue(false);
        }
    }

    /**
     * FEED-state animal selection: only consider a TFC animal while it's hungry today, so the worker doesn't
     * re-walk to (and waste grain on) animals already fed for the day. Vanilla animals are unaffected.
     */
    @Redirect(method = "feedAnimal",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;searchForAnimals(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<? extends Animal> mctfc$feedHungryTfcOnly(final AbstractEntityAIHerder self, final Predicate<Animal> predicate)
    {
        return self.searchForAnimals(predicate.and(a -> !TfcHerd.isTfc(a) || TfcHerd.isTendable(a)));
    }

    /**
     * FEED-state feed action: the eat-event fires for every fed animal (baby or adult), so this is where we
     * actually raise familiarity for TFC livestock — which also clears hunger for the day.
     */
    @Redirect(method = "feedAnimal",
      at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V",
        remap = true))
    private void mctfc$familiarizeOnFeed(final Level level, final Entity entity, final byte event)
    {
        if (entity instanceof Animal animal && TfcHerd.isTfc(animal))
        {
            mctfc$familiarize(animal);
        }
        level.broadcastEntityEvent(entity, event);
    }

    @Redirect(method = "feedAnimal",
      at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/animal/Animal;ageUp(IZ)V",
        remap = true))
    private void mctfc$noForceAgeTfc(final Animal animal, final int amount, final boolean forced)
    {
        if (!TfcHerd.isTfc(animal))
        {
            animal.ageUp(amount, forced);
        }
    }

    /**
     * Raise the per-{@code DECIDE} FEED chance from MineColonies' {@code 0.1} to {@link TfcHerd#FEED_CHANCE}. Since
     * BREED is skipped for TFC, FEED is the only familiarization path, so it needs to fire more often to keep a herd
     * familiar within a TFC day. {@code ordinal = 1} targets the FEED threshold specifically — the PICKUP branch
     * uses the same {@code 0.1} literal (ordinal 0), which we leave alone.
     */
    @ModifyConstant(method = "decideWhatToDo", constant = @Constant(doubleValue = 0.1, ordinal = 1), require = 1)
    private double mctfc$feedChance(final double original)
    {
        return TfcHerd.FEED_CHANCE;
    }
}
