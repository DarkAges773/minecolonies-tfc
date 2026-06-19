package com.mctfc.mixin;

import com.mctfc.herding.TfcHerd;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
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
 * Reworks the shared herder loop to TFC's husbandry for TFC livestock, leaving vanilla animals (and non-TFC worlds)
 * on the original path. TFC animals never enter vanilla love-mode; instead TFC's {@code isReadyToMate} requires an
 * adult that is familiar enough (≥ 0.3), not already pregnant, and — crucially — <b>fed that day</b>
 * ({@code !isHungry}), with the mate cooldown elapsed. Its brain {@code BreedBehavior} then pairs a male with a
 * valid opposite-gender partner. So breeding is genuinely <b>pair-based</b>: feeding <i>both</i> of a fitting pair
 * today is what makes them mate.
 *
 * <p>This needs <b>two</b> phases — familiarize first, then breed in pairs:
 * <ul>
 *   <li><b>FEED</b> (individual familiarization) — TFC's {@code eatFood} raises familiarity only while an animal can
 *       still gain it (a child, or an adult below its {@code adultFamiliarityCap}). So the individual FEED state
 *       feeds any such hungry animal ({@link TfcHerd#shouldFamiliarize}) to build it toward the mate threshold —
 *       the "default feeding until the familiarity cap." At-cap animals are skipped (no benefit).</li>
 *   <li><b>BREED</b> (pair mating) — once an adult is familiar enough (≥ 0.3), MineColonies' {@code breedAnimals}
 *       finds it a {@code canMate} partner and feeds both. We point its pieces at TFC: {@code isBreedAble} →
 *       {@link TfcHerd#isBreedingCandidate} (fertile, non-pregnant, hungry, <b>mate-ready</b> adult); {@code canMate}
 *       → {@link TfcHerd#canPair} (opposite genders — so the worker feeds a fitting <b>pair at once</b> and skips an
 *       animal with no partner, no wasted food); and the {@code setInLove} action → feed the animal (raising
 *       familiarity + clearing hunger). Both being fed today + ≥ 0.3 satisfies TFC's {@code isReadyToMate} and its
 *       brain {@code BreedBehavior} mates them.</li>
 * </ul>
 * BREED is checked before FEED, so a mate-ready pair is bred while everyone else is familiarized.
 *
 * <p>Butchering is also TFC-aware: {@code chanceToButcher} and {@code butcherAnimals} use the per-species,
 * female-weighted reserve (see {@link TfcHerd#butcherChance}/{@link TfcHerd#pickButcherTarget}).
 *
 * {@code @Mixin(remap = false)} — MineColonies' own class/members; redirected vanilla calls carry {@code remap = true}
 * on their {@code @At}. See {@code docs/tfc-herder-workers.md}.
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

    /** The food the worker is currently holding (the breeding item it equips before feeding a pair). */
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

    /** BREED candidates for TFC: fertile adults that need feeding today (so the worker familiarizes them toward mating). */
    @Inject(method = "isBreedAble", at = @At("HEAD"), cancellable = true)
    private static void mctfc$tfcBreedAble(final Animal entity, final CallbackInfoReturnable<Boolean> cir)
    {
        if (TfcHerd.isTfc(entity))
        {
            cir.setReturnValue(TfcHerd.isBreedingCandidate(entity));
        }
    }

    /** TFC pairs by gender: a male + female form the pair the worker feeds together; same-gender never pairs. */
    @Inject(method = "canMate", at = @At("HEAD"), cancellable = true)
    private static void mctfc$tfcCanMate(final Animal first, final Animal second, final CallbackInfoReturnable<Boolean> cir)
    {
        if (TfcHerd.isTfc(first) || TfcHerd.isTfc(second))
        {
            cir.setReturnValue(TfcHerd.canPair(first, second));
        }
    }

    /** Breed action for TFC: feed the animal TFC food (raise familiarity + clear hunger) instead of vanilla love-mode. */
    @Redirect(method = "breedTwoAnimals",
      at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/animal/Animal;setInLove(Lnet/minecraft/world/entity/player/Player;)V",
        remap = true))
    private void mctfc$tendInsteadOfLove(final Animal animal, final Player player)
    {
        if (TfcHerd.isTfc(animal))
        {
            mctfc$familiarize(animal);
        }
        else
        {
            animal.setInLove(player);
        }
    }

    /**
     * Individual FEED state = <b>familiarization</b>: select a TFC animal that's hungry today and can still gain
     * familiarity ({@link TfcHerd#shouldFamiliarize} — a child or an adult below its cap, that accepts the held
     * grain). This is the "default feeding until the familiarity cap"; mate-ready animals are pair-fed by BREED
     * instead, and at-cap animals are skipped (no benefit). Vanilla animals are unaffected.
     */
    @Redirect(method = "feedAnimal",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;searchForAnimals(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<? extends Animal> mctfc$familiarizeHungryTfc(final AbstractEntityAIHerder self, final Predicate<Animal> predicate)
    {
        final ItemStack held = mctfc$heldFood();
        return self.searchForAnimals(predicate.and(a -> !TfcHerd.isTfc(a) || TfcHerd.shouldFamiliarize(a, held)));
    }

    /** FEED action for TFC: raise familiarity (TFC {@code eatFood}) on the broadcast-eat event, which also clears hunger. */
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

    /** Don't force-age a TFC baby in the FEED state (that would corrupt TFC's calendar aging); vanilla unaffected. */
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
     * Replace MineColonies' butcher gate for TFC herds: always cull when an OLD animal is present, otherwise cull
     * while some species exceeds its per-gender reserve. {@link TfcHerd#butcherChance} returns {@code null} for a
     * non-TFC herd, leaving MineColonies' logic in place.
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
     * BUTCHER-state target selection: pick the TFC animal to cull by husbandry priority (OLD first, then the
     * species+gender most over its reserve — see {@link TfcHerd#pickButcherTarget}) instead of MineColonies'
     * furthest-and-sheltered choice. We hand the butcher loop a one-element list of that animal. When there's no
     * valid TFC cull target we must not fall back to vanilla selection for a TFC herd (it would kill a reserved
     * breeder), so we return an empty list; only a purely vanilla herd uses MineColonies' own selection.
     */
    @Redirect(method = "butcherAnimals",
      at = @At(value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/herders/AbstractEntityAIHerder;searchForAnimals(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<? extends Animal> mctfc$butcherByPriority(final AbstractEntityAIHerder self, final Predicate<Animal> predicate)
    {
        final List<? extends Animal> all = self.searchForAnimals(predicate);
        final Animal target = TfcHerd.pickButcherTarget(all, mctfc$buildingLevel());
        if (target != null)
        {
            final List<Animal> chosen = new ArrayList<>(1);
            chosen.add(target);
            return chosen;
        }
        for (final Animal a : all)
        {
            if (TfcHerd.isTfc(a))
            {
                return new ArrayList<>();
            }
        }
        return all;
    }

    /**
     * Raise the per-{@code DECIDE} FEED (familiarization) chance from MineColonies' {@code 0.1} to
     * {@link TfcHerd#FEED_CHANCE}, so a herd familiarizes in reasonable time. {@code ordinal = 1} targets the FEED
     * threshold — the PICKUP branch uses the same {@code 0.1} literal (ordinal 0), which we leave alone.
     */
    @ModifyConstant(method = "decideWhatToDo", constant = @Constant(doubleValue = 0.1, ordinal = 1), require = 1)
    private double mctfc$feedChance(final double original)
    {
        return TfcHerd.FEED_CHANCE;
    }
}
