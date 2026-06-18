package com.mctfc.herding;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.ItemStorage;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Bridge between MineColonies' herding workers and TFC livestock. The whole herder family is hardcoded to vanilla
 * animals (recognition by {@code instanceof Cow/Sheep/Pig/Chicken}, breeding by vanilla love-mode), but every TFC
 * livestock — both {@code TFCAnimal} (cow/sheep/pig/chicken/duck/quail/…) and {@code TFCRabbit} (via
 * {@code MammalProperties}) — implements the single interface {@link TFCAnimalProperties}. That is the key this
 * bridge dispatches on.
 *
 * <p>Phase 1 (this class): per-hut <b>recognition</b> via {@code #mctfc:herding/<job>} entity-type tags, and
 * <b>breeding reworked to TFC familiarity</b> — instead of vanilla {@code setInLove}, the worker feeds the animal
 * TFC food (TFC's {@code eatFood}, the same call a player's right-click makes), raising familiarity; TFC's own brain
 * then mates familiarized adults. Products (milk/wool/eggs) are later phases; meat already works once recognized
 * (the butcher path is unchanged). See {@code docs/tfc-herder-workers.md}.
 */
public final class TfcHerd
{
    private TfcHerd() {}

    /** Job paths (under {@code minecolonies:}) of the five herding huts this bridge governs. */
    private static final Set<String> HERDER_JOBS =
      Set.of("cowboy", "shepherd", "swineherder", "chickenherder", "rabbitherder");

    /**
     * Per-{@code DECIDE} chance the herder enters the FEED state. MineColonies' default is {@code 0.1}; we raise it
     * because for TFC animals FEED is the <i>sole</i> familiarization path (BREED is skipped — see
     * {@code MixinAbstractEntityAIHerder}), so the worker needs to feed more often to keep a herd familiar within a
     * TFC day. Applied via {@code @ModifyConstant} on {@code decideWhatToDo}.
     */
    public static final double FEED_CHANCE = 0.33;


    /** Universal TFC feed: a grain, which sits in {@code #tfc:foods/grains} — accepted by every herded TFC species. */
    private static final ResourceLocation FEED_ID = new ResourceLocation("tfc", "food/wheat_grain");

    /** Per-job {@code #mctfc:herding/<path>} entity-type tags, resolved lazily. */
    private static final Map<String, TagKey<EntityType<?>>> TAGS = new ConcurrentHashMap<>();

    private static volatile List<ItemStorage> feedList;

    /** True for any TFC livestock (the common {@link TFCAnimalProperties} interface). */
    public static boolean isTfc(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties;
    }

    /** True if this herding module's job is one we bridge to TFC. */
    public static boolean isHerderJob(final JobEntry jobEntry)
    {
        return jobEntry != null && HERDER_JOBS.contains(jobEntry.getKey().getPath());
    }

    /**
     * Does the hut for {@code jobEntry} tend this animal? True when the animal's type is in
     * {@code #mctfc:herding/<jobpath>}. (Vanilla animals are still matched by MineColonies' own predicate.)
     */
    public static boolean recognizes(final JobEntry jobEntry, final Animal animal)
    {
        if (!isHerderJob(jobEntry))
        {
            return false;
        }
        final TagKey<EntityType<?>> tag = TAGS.computeIfAbsent(jobEntry.getKey().getPath(),
          path -> TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("mctfc", "herding/" + path)));
        return ForgeRegistries.ENTITY_TYPES.tags().getTag(tag).contains(animal.getType());
    }

    /**
     * Worth tending today: a TFC animal that isn't {@code OLD} (old animals don't breed/produce) and is still
     * hungry (TFC's once-per-day feed gate).
     */
    public static boolean isTendable(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties tfc
                 && tfc.getAgeType() != TFCAnimalProperties.Age.OLD
                 && tfc.isHungry();
    }

    /**
     * Whether the worker should feed this TFC animal the food it's currently holding — tendable today <i>and</i> the
     * held item is valid food for it. The food test is TFC's own {@code isFood}, which is where the <b>rotten</b>
     * rule lives: a rotten item is "not food" for an animal that doesn't {@code eatsRottenFood}, but rotten food is
     * still food for one that does (e.g. pigs). So the worker won't waste-feed rotten grain to a picky animal, but a
     * pig still accepts it. Used to filter the FEED-state selection.
     */
    public static boolean willAcceptFeed(final Animal animal, final ItemStack held)
    {
        return isTendable(animal) && ((TFCAnimalProperties) animal).isFood(held);
    }

    /**
     * Per-gender adult breeding reserve, scaled with hut level and <b>female-weighted</b>: females grow one per
     * level, males {@code ceil(level / 2)} (one per two levels, rounded up), each floored at 1. So a herd skews
     * increasingly female as the hut upgrades (L1 1♂/1♀, L2 1♂/2♀, L3 2♂/3♀, L4 2♂/4♀, L5 3♂/5♀).
     */
    public static int maleReserve(final int level)
    {
        return Math.max(1, (level + 1) / 2); // ceil(level / 2)
    }

    public static int femaleReserve(final int level)
    {
        return Math.max(1, level);
    }

    /**
     * TFC butcher gate, replacing MineColonies' "more than 3 adults over a {@code level × 2} cap" rule. Returns the
     * chance the worker should cull from this herd, or {@code null} when the herd has no TFC animals (MineColonies'
     * own logic then applies). We <b>always</b> cull when an {@code OLD} animal is present — they no longer breed or
     * produce, so they're culled even below the gate. Otherwise the worker culls only while a gender exceeds its own
     * {@link #maleReserve}/{@link #femaleReserve} (which scale with hut level). {@code CHILD}/{@code OLD} don't count.
     */
    public static Double butcherChance(final List<? extends Animal> animals, final int level)
    {
        boolean anyTfc = false;
        boolean hasOld = false;
        int adultMales = 0;
        int adultFemales = 0;
        for (final Animal a : animals)
        {
            if (!(a instanceof TFCAnimalProperties tfc))
            {
                continue;
            }
            anyTfc = true;
            final TFCAnimalProperties.Age age = tfc.getAgeType();
            if (age == TFCAnimalProperties.Age.OLD)
            {
                hasOld = true;
            }
            else if (age == TFCAnimalProperties.Age.ADULT)
            {
                if (tfc.getGender() == TFCAnimalProperties.Gender.MALE)
                {
                    adultMales++;
                }
                else
                {
                    adultFemales++;
                }
            }
        }
        if (!anyTfc)
        {
            return null;
        }
        if (hasOld)
        {
            return 1.0;
        }
        return (adultMales > maleReserve(level) || adultFemales > femaleReserve(level)) ? 1.0 : 0.0;
    }

    /**
     * Choose which TFC animal the worker should cull, given the whole herd:
     * <ol>
     *   <li><b>OLD</b> animals first (they no longer breed or produce) — least familiar among them;</li>
     *   <li>then surplus adults from the gender with the <b>largest overshoot of its own reserve</b>
     *       ({@link #maleReserve}/{@link #femaleReserve}) — least familiar among that gender.</li>
     * </ol>
     * So the colony keeps its productive, familiarized breeding stock and spends the worn-out / surplus / least-
     * invested animals first, converging the herd to the female-weighted reserve. Babies ({@code CHILD}) are never
     * targeted. Returns {@code null} when there's nothing to cull (MineColonies' own distance/shelter selection
     * then applies — e.g. for a vanilla animal in a mixed herd).
     */
    public static Animal pickButcherTarget(final List<? extends Animal> animals, final int level)
    {
        final Animal old = leastFamiliar(animals, a -> ageOf(a) == TFCAnimalProperties.Age.OLD);
        if (old != null)
        {
            return old;
        }

        int adultMales = 0;
        int adultFemales = 0;
        for (final Animal a : animals)
        {
            if (ageOf(a) == TFCAnimalProperties.Age.ADULT)
            {
                if (((TFCAnimalProperties) a).getGender() == TFCAnimalProperties.Gender.MALE)
                {
                    adultMales++;
                }
                else
                {
                    adultFemales++;
                }
            }
        }

        final int maleSurplus = adultMales - maleReserve(level);
        final int femaleSurplus = adultFemales - femaleReserve(level);
        if (maleSurplus <= 0 && femaleSurplus <= 0)
        {
            return null;
        }
        // Cull from the gender that overshoots its reserve the most; ties favour culling males (keeps it female-heavy).
        final TFCAnimalProperties.Gender cull =
          maleSurplus >= femaleSurplus ? TFCAnimalProperties.Gender.MALE : TFCAnimalProperties.Gender.FEMALE;
        return leastFamiliar(animals, a -> ageOf(a) == TFCAnimalProperties.Age.ADULT
                                             && ((TFCAnimalProperties) a).getGender() == cull);
    }

    /** Age of a TFC animal, or {@code null} if it isn't TFC livestock. */
    private static TFCAnimalProperties.Age ageOf(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties tfc ? tfc.getAgeType() : null;
    }

    /** The least-familiar animal matching the filter (used to pick the least-invested cull target), or null. */
    private static Animal leastFamiliar(final List<? extends Animal> animals, final Predicate<Animal> filter)
    {
        Animal best = null;
        float bestFamiliarity = Float.MAX_VALUE;
        for (final Animal a : animals)
        {
            if (!filter.test(a))
            {
                continue;
            }
            final float familiarity = ((TFCAnimalProperties) a).getFamiliarity();
            if (best == null || familiarity < bestFamiliarity)
            {
                best = a;
                bestFamiliarity = familiarity;
            }
        }
        return best;
    }

    /**
     * Feed the animal the worker's actually-held TFC food to raise familiarity — the authentic husbandry step.
     * Mirrors TFC's own gate ({@code isFood && isHungry → eatFood}); {@code eatFood} raises familiarity and stamps
     * "fed today" but doesn't re-check hunger, so we do. Using the real held stack (not a synthetic one) is what
     * makes the rotten rule authentic: a rotten grain familiarizes a pig but not a cow. We pass a copy because
     * {@code eatFood} shrinks the stack itself, while MineColonies' herder loop already consumes one real breeding
     * item after this call.
     */
    public static void familiarize(final Animal animal, final ItemStack food, final FakePlayer fakePlayer)
    {
        if (fakePlayer == null || food == null || food.isEmpty() || !(animal instanceof TFCAnimalProperties tfc))
        {
            return;
        }
        if (tfc.isFood(food) && tfc.isHungry())
        {
            tfc.eatFood(food.copy(), InteractionHand.MAIN_HAND, fakePlayer);
        }
    }

    /** The breeding item TFC herding huts request/equip/feed: a TFC grain, NBT-agnostic so food decay doesn't break matching. */
    public static List<ItemStorage> breedingFood()
    {
        List<ItemStorage> list = feedList;
        if (list == null)
        {
            final Item grain = ForgeRegistries.ITEMS.getValue(FEED_ID);
            // ignoreDamage = false, ignoreNBT = true
            list = List.of(new ItemStorage(new ItemStack(grain), false, true));
            feedList = list;
        }
        return list;
    }
}
