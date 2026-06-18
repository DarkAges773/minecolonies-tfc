package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
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
 *       eligible only while {@code willAcceptFeed} — hungry today <i>and</i> the held grain is valid food for it
 *       (TFC's {@code isFood}, which encodes the rotten rule: a picky animal refuses rotten grain, a pig accepts
 *       it) — so the worker neither re-feeds the already-fed nor wastes rotten grain on animals that won't eat it;</li>
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
    /**
     * Reach the worker's {@code FakePlayer} via {@link AbstractEntityAIBasicInvoker} — {@code getFakePlayer} is
     * declared up in {@code AbstractEntityAIBasic}, and a plain inherited {@code @Shadow} from this subclass fails
     * to apply (Mixin doesn't resolve inherited members), so we cast {@code this} to the invoker interface.
     */
    private FakePlayer mctfc$fakePlayer()
    {
        return ((AbstractEntityAIBasicInvoker) this).mctfc$getFakePlayer();
    }

    /** The food the worker is currently holding (equipped from the breeding-item list before feeding). */
    private ItemStack mctfc$heldFood()
    {
        return ((AbstractAISkeletonAccessor) this).mctfc$worker().getMainHandItem();
    }

    /** The hut's level — drives the (female-weighted) per-gender breeding reserve used by the butcher logic. */
    private int mctfc$buildingLevel()
    {
        return ((AbstractEntityAIBasicInvoker) this).mctfc$building().getBuildingLevel();
    }

    private void mctfc$familiarize(final Animal animal)
    {
        TfcHerd.familiarize(animal, mctfc$heldFood(), mctfc$fakePlayer());
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
     * Replace MineColonies' butcher gate ({@code >3 adults} over a {@code level × 2} cap) for TFC herds: always cull
     * when an OLD animal is present (even below any gate), otherwise cull only while more than
     * {@link TfcHerd#MIN_BREEDING_PAIRS} breedable pairs remain. {@link TfcHerd#butcherChance} returns {@code null}
     * for a non-TFC herd, leaving MineColonies' logic in place.
     */
    @Inject(method = "chanceToButcher", at = @At("HEAD"), cancellable = true)
    private void mctfc$tfcButcherGate(final List<? extends Animal> allAnimals, final CallbackInfoReturnable<Double> cir)
    {
        final Double chance = TfcHerd.butcherChance(allAnimals, mctfc$buildingLevel());
        if (chance != null)
        {
            cir.setReturnValue(chance);
        }
    }

    /**
     * FEED-state animal selection: only consider a TFC animal that is hungry today <i>and</i> will accept the food
     * the worker is currently holding ({@code willAcceptFeed} → TFC's {@code isFood}, which encodes the rotten rule).
     * So the worker won't walk to / waste grain on an animal that's already fed, nor on a picky animal when the held
     * grain is rotten — but a pig (which eats rotten food) is still fed. Vanilla animals are unaffected.
     */
    @Redirect(method = "feedAnimal",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;searchForAnimals(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<? extends Animal> mctfc$feedAcceptingTfcOnly(final AbstractEntityAIHerder self, final Predicate<Animal> predicate)
    {
        final ItemStack held = mctfc$heldFood();
        return self.searchForAnimals(predicate.and(a -> !TfcHerd.isTfc(a) || TfcHerd.willAcceptFeed(a, held)));
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
     * BUTCHER-state target selection: pick the TFC animal to cull by husbandry priority (OLD first, then least
     * familiar — see {@link TfcHerd#pickButcherTarget}) instead of MineColonies' furthest-and-sheltered choice.
     * We hand the butcher loop a one-element list of that animal so it kills exactly it. Returns the original list
     * (mutable — the method then {@code sort}s it) when there's no TFC adult/old to cull, so vanilla animals keep
     * their normal selection. The decision of <i>whether</i> to butcher ({@code chanceToButcher}) is untouched.
     */
    @Redirect(method = "butcherAnimals",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;searchForAnimals(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<? extends Animal> mctfc$butcherByPriority(final AbstractEntityAIHerder self, final Predicate<Animal> predicate)
    {
        final List<? extends Animal> all = self.searchForAnimals(predicate);
        final Animal target = TfcHerd.pickButcherTarget(all, mctfc$buildingLevel());
        if (target == null)
        {
            return all;
        }
        final List<Animal> chosen = new ArrayList<>(1);
        chosen.add(target);
        return chosen;
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
