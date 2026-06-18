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
     * hungry (TFC's once-per-day feed gate). Drives the herder's breedable/mate decisions for TFC animals, so the
     * worker stops feeding once the herd is fed for the day instead of wasting food.
     */
    public static boolean isTendable(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties tfc
                 && tfc.getAgeType() != TFCAnimalProperties.Age.OLD
                 && tfc.isHungry();
    }

    /**
     * Feed the animal one TFC food to raise familiarity — the authentic husbandry step. Mirrors TFC's own gate
     * ({@code isFood && isHungry → eatFood}); {@code eatFood} itself raises familiarity and stamps "fed today" but
     * does <b>not</b> re-check hunger, so we must. We pass a copy because {@code eatFood} shrinks the stack itself,
     * while MineColonies' herder loop already consumes one real breeding item after this call (so the held stack
     * isn't double-spent).
     */
    public static void familiarize(final Animal animal, final ItemStack food, final FakePlayer fakePlayer)
    {
        if (fakePlayer != null && food != null && animal instanceof TFCAnimalProperties tfc
              && tfc.isFood(food) && tfc.isHungry())
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
