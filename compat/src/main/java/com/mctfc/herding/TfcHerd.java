package com.mctfc.herding;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import net.dries007.tfc.common.entities.livestock.DairyAnimal;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.common.entities.livestock.WoolyAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
     * Per-{@code DECIDE} chance the herder enters the individual FEED (familiarization) state. MineColonies' default
     * is {@code 0.1}; we raise it so a herd familiarizes within reasonable time. Applied via {@code @ModifyConstant}.
     */
    public static final double FEED_CHANCE = 0.50;

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

    // ── Pen hygiene: one species per hut ──────────────────────────────────────────────────────────────────────

    /**
     * Interaction key for the "more than one species in this pen" worker warning. TFC breeds, familiarises and
     * reserves <b>per species</b>, so a mixed pen splits the worker's effort and over-crowds with each species'
     * breeding reserve — the player is better off one species per hut. The validator (registered in
     * {@link #onCommonSetup}) keeps the warning shown while that's the case and auto-clears it once the pen is down
     * to a single species.
     */
    public static final String MIXED_SPECIES = "com.mctfc.interaction.herder.mixedspecies";

    /** How many distinct TFC species the herding hut currently pens (animals its herding module recognises). */
    public static int penSpeciesCount(final IBuilding building)
    {
        if (building == null)
        {
            return 0;
        }
        final Level level = building.getColony().getWorld();
        if (level == null)
        {
            return 0;
        }
        final HashSet<EntityType<?>> species = new HashSet<>();
        for (final AnimalHerdingModule module : building.getModulesByType(AnimalHerdingModule.class))
        {
            for (final Animal a : WorldUtil.getEntitiesWithinBuilding(level, Animal.class, building, module::isCompatible))
            {
                if (isTfc(a))
                {
                    species.add(a.getType());
                }
            }
        }
        return species.size();
    }

    /** Register herder interaction validators on common setup (the mixed-species warning auto-clears at 1 species). */
    public static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> InteractionValidatorRegistry.registerStandardPredicate(
          Component.translatable(MIXED_SPECIES),
          citizen -> penSpeciesCount(citizen.getWorkBuilding()) > 1));
    }

    /**
     * Worth feeding individually to <b>familiarize</b> — a hungry TFC animal that can still gain familiarity (a
     * {@code CHILD}, or an adult below its {@code adultFamiliarityCap}, matching TFC's own {@code eatFood} cap) and
     * accepts the held food (TFC's {@code isFood}, which encodes the rotten rule — a picky animal refuses rotten
     * grain, a pig accepts it). Drives the individual FEED state; animals at their cap are skipped (no benefit), and
     * breeding-ready ones are pair-fed by BREED instead. This is the "default feeding until the familiarity cap."
     */
    public static boolean shouldFamiliarize(final Animal animal, final ItemStack held)
    {
        return animal instanceof TFCAnimalProperties tfc
                 && tfc.isHungry()
                 && (tfc.getAgeType() == TFCAnimalProperties.Age.CHILD || tfc.getFamiliarity() < tfc.getAdultFamiliarityCap())
                 && tfc.isFood(held);
    }

    /**
     * A breed-feed candidate: a TFC <b>adult</b> that is already familiar enough to mate
     * ({@code familiarity ≥ READY_TO_MATE_FAMILIARITY}, i.e. 0.3 — TFC won't mate below this), not pregnant, and
     * hungry today, so feeding it clears hunger into TFC's ready-to-mate state. Used as the herder's
     * {@code isBreedAble} for TFC — paired up by {@link #canPair} so only animals with a partner get fed. (Animals
     * below the mate threshold are familiarized first via {@link #shouldFamiliarize}.)
     */
    public static boolean isBreedingCandidate(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties tfc
                 && tfc.getAgeType() == TFCAnimalProperties.Age.ADULT
                 && !tfc.isFertilized()
                 && tfc.isHungry()
                 && tfc.getFamiliarity() >= TFCAnimalProperties.READY_TO_MATE_FAMILIARITY;
    }

    /**
     * Two TFC animals form a valid breeding pair — opposite genders (each is already a {@link #isBreedingCandidate}
     * from the breedables filter). TFC breeds a male with a female, so the worker feeds one of each together and
     * skips an animal with no opposite-gender partner.
     */
    public static boolean canPair(final Animal first, final Animal second)
    {
        return first instanceof TFCAnimalProperties a
                 && second instanceof TFCAnimalProperties b
                 && a.getGender() != b.getGender();
    }

    // ── Products: milk (Cowhand) ──────────────────────────────────────────────────────────────────────────────

    /**
     * TFC fluid containers offered for the Cowhand's "Milk Item" setting, in a <b>stable order</b> — the player's
     * selection persists by index ({@code StringSetting} serializes its index), so reordering would shift saved
     * picks. Only those that exist and can actually hold milk are listed; the first survivor is the default (the
     * cheap ceramic jug). Drawn from TFC's {@code fluid_item_ingredient_empty_containers} tag.
     */
    private static final ResourceLocation[] MILK_CONTAINER_IDS = {
      new ResourceLocation("tfc", "ceramic/jug"),
      new ResourceLocation("tfc", "wooden_bucket"),
      new ResourceLocation("tfc", "metal/bucket/red_steel"),
      new ResourceLocation("tfc", "metal/bucket/blue_steel"),
    };

    private static volatile List<String> milkContainerOptions;       // setting values (item description ids), in order
    private static volatile Map<String, Item> milkContainerByOption; // description id -> container item

    private static void ensureMilkContainers()
    {
        if (milkContainerOptions != null)
        {
            return;
        }
        final List<String> opts = new ArrayList<>();
        final Map<String, Item> byOpt = new HashMap<>();
        for (final ResourceLocation id : MILK_CONTAINER_IDS)
        {
            final Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || !acceptsMilk(item))
            {
                continue;
            }
            final String key = item.getDescriptionId();
            if (byOpt.putIfAbsent(key, item) == null)
            {
                opts.add(key);
            }
        }
        if (opts.isEmpty()) // never hand the GUI an empty list — fall back to the jug
        {
            final Item jug = ForgeRegistries.ITEMS.getValue(MILK_CONTAINER_IDS[0]);
            if (jug != null)
            {
                opts.add(jug.getDescriptionId());
                byOpt.put(jug.getDescriptionId(), jug);
            }
        }
        milkContainerByOption = byOpt;
        milkContainerOptions = opts;
    }

    /** Whether an empty container item can be filled with milk — TFC's milk is Forge's milk fluid (FirmaLife varies it). */
    private static boolean acceptsMilk(final Item item)
    {
        final Fluid milk = ForgeMod.MILK.get();
        if (milk == null)
        {
            return true; // can't probe (fluid not registered yet) — assume usable
        }
        return new ItemStack(item).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                 .map(h -> h.fill(new FluidStack(milk, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE) > 0)
                 .orElse(false);
    }

    /** The option list (item description ids) for the Cowhand's "Milk Item" {@code StringSetting}. */
    public static String[] milkContainerOptions()
    {
        ensureMilkContainers();
        return milkContainerOptions.toArray(new String[0]);
    }

    /** Resolve a "Milk Item" setting value to its empty-container stack (falls back to the default jug). */
    public static ItemStack milkContainerFor(final String settingValue)
    {
        ensureMilkContainers();
        Item item = settingValue == null ? null : milkContainerByOption.get(settingValue);
        if (item == null)
        {
            item = ForgeRegistries.ITEMS.getValue(MILK_CONTAINER_IDS[0]);
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** A TFC dairy animal (cow/goat/yak — base TFC and add-ons). */
    public static boolean isDairy(final Animal animal)
    {
        return animal instanceof DairyAnimal;
    }

    /** A TFC dairy animal that can be milked right now — TFC's own gate (familiarity + product cooldown + adult/female). */
    public static boolean isReadyDairy(final Animal animal)
    {
        return animal instanceof DairyAnimal dairy && dairy.isReadyForAnimalProduct();
    }

    /**
     * A single empty instance of the hut's <b>selected</b> milk container ({@code wanted}) in the inventory (a copy
     * of count 1) to milk into, or EMPTY if the worker holds none. Strictly the selected item — the caller is
     * responsible for stocking it (pulling from the hut / requesting), so the worker only ever milks into the
     * container the player picked rather than into whatever fluid item it happens to carry.
     *
     * <p>The fluid-handler capability is probed on a <b>count-1 copy</b>, not the inventory stack: TFC's
     * {@code ItemStackFluidHandler} only exposes the capability when {@code stack.getCount() == 1}, so a <i>stack</i>
     * of empty containers (e.g. 10 wooden buckets) would otherwise read as "not a fluid container" and the worker
     * would never find anything to milk into.
     */
    public static ItemStack findEmptyMilkContainer(final IItemHandler inventory, final ItemStack wanted)
    {
        if (wanted == null || wanted.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItem(stack, wanted))
            {
                continue;
            }
            final ItemStack single = stack.copyWithCount(1);
            final IFluidHandlerItem handler = single.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);
            if (handler != null && handler.getFluidInTank(0).isEmpty())
            {
                return single;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Milk a ready TFC dairy animal by driving TFC's own {@code mobInteract} with the worker's {@link FakePlayer}
     * holding the empty container. That one call fires TFC's {@code AnimalProductEvent} (so FirmaLife swaps in the
     * per-animal milk variant), fills the container with the resulting fluid, and sets the product cooldown — exactly
     * a player right-click. Returns the filled container (to bank in the worker's inventory), or EMPTY if it didn't
     * milk (wrong/incompatible container, not actually ready, …). The caller consumes one empty container on success.
     */
    public static ItemStack milk(final Animal animal, final ItemStack emptyContainer, final FakePlayer fakePlayer)
    {
        if (fakePlayer == null || emptyContainer.isEmpty() || !(animal instanceof DairyAnimal))
        {
            return ItemStack.EMPTY;
        }
        final ItemStack previousHeld = fakePlayer.getMainHandItem();
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, emptyContainer.copyWithCount(1));
        try
        {
            final InteractionResult result = animal.mobInteract(fakePlayer, InteractionHand.MAIN_HAND);
            final ItemStack filled = fakePlayer.getMainHandItem();
            if (result.consumesAction() && !filled.isEmpty() && !ItemStack.matches(filled, emptyContainer))
            {
                return filled.copy();
            }
            return ItemStack.EMPTY;
        }
        finally
        {
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, previousHeld);
        }
    }

    // ── Products: wool (Shepherd) ─────────────────────────────────────────────────────────────────────────────

    /** A TFC wooly animal (sheep/alpaca/musk ox — base TFC and add-ons). */
    public static boolean isWooly(final Animal animal)
    {
        return animal instanceof WoolyAnimal;
    }

    /** A TFC wooly animal that can be sheared right now — TFC's own gate (familiarity + product cooldown + adult). */
    public static boolean isReadyWooly(final Animal animal)
    {
        return animal instanceof WoolyAnimal wooly && wooly.isReadyForAnimalProduct();
    }

    /**
     * Shear a ready TFC wooly animal via Forge's {@code IForgeShearable.onSheared} — TFC's own shear path. That call
     * fires TFC's {@code AnimalProductEvent} (so FirmaLife/add-ons can vary the wool per species), sets the product
     * cooldown, and <b>returns</b> the wool drops directly (unlike milk, no container is involved). Returns an empty
     * list if the animal isn't a ready TFC wooly. The caller banks the drops and damages the shears.
     */
    public static List<ItemStack> shear(final Animal animal, final ItemStack shears, final FakePlayer fakePlayer)
    {
        if (fakePlayer == null || !(animal instanceof WoolyAnimal wooly))
        {
            return List.of();
        }
        final Level level = animal.level();
        final BlockPos pos = animal.blockPosition();
        if (!wooly.isShearable(shears, level, pos))
        {
            return List.of();
        }
        return wooly.onSheared(fakePlayer, shears, level, pos, shears.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE));
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
     * produce, so they're culled even below the gate. Otherwise the worker culls only while some <b>species</b>
     * exceeds its {@link #maleReserve}/{@link #femaleReserve}. The reserve is counted <b>per species</b> (cow, goat,
     * yak — all {@code DairyAnimal} — are separate), so a multi-species hut keeps a breeding pair of <i>each</i>
     * species rather than culling a minority species to extinction. {@code CHILD}/{@code OLD} don't count.
     */
    public static Double butcherChance(final List<? extends Animal> animals, final int level)
    {
        final Map<EntityType<?>, int[]> adultsBySpecies = new HashMap<>();
        boolean anyTfc = false;
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
                return 1.0; // always cull old
            }
            if (age == TFCAnimalProperties.Age.ADULT)
            {
                final int[] count = adultsBySpecies.computeIfAbsent(a.getType(), k -> new int[2]);
                count[tfc.getGender() == TFCAnimalProperties.Gender.MALE ? 0 : 1]++;
            }
        }
        if (!anyTfc)
        {
            return null;
        }
        for (final int[] count : adultsBySpecies.values())
        {
            if (count[0] > maleReserve(level) || count[1] > femaleReserve(level))
            {
                return 1.0;
            }
        }
        return 0.0;
    }

    /**
     * Choose which TFC animal the worker should cull, given the whole herd:
     * <ol>
     *   <li><b>Resume an interrupted kill</b> — the most-hurt <i>cull-eligible</i> animal (below full health), if any.
     *       Butchering lands one hit per cycle, so if the loop is interrupted and re-picks a fresh animal each time it
     *       just spreads damage that then regenerates and nothing ever dies; finishing the already-hurt animal first
     *       avoids that. Gated to cull-eligible animals (OLD, or a surplus-gender adult) so a reserved breeder hurt by
     *       something else is never finished off.</li>
     *   <li><b>OLD</b> animals next (they no longer breed or produce) — least familiar among them;</li>
     *   <li>then surplus adults from the <b>species + gender</b> with the largest overshoot of its own reserve
     *       ({@link #maleReserve}/{@link #femaleReserve}) — least familiar among that group.</li>
     * </ol>
     * Reserves are <b>per species</b>, so culling a multi-species hut trims each species toward its own breeding
     * reserve instead of wiping out a minority species (e.g. it won't butcher the last female goat just because
     * there are also cows). Babies ({@code CHILD}) are never targeted. Returns {@code null} when there's nothing to
     * cull.
     */
    public static Animal pickButcherTarget(final List<? extends Animal> animals, final int level)
    {
        final Map<EntityType<?>, int[]> adultsBySpecies = new HashMap<>();
        for (final Animal a : animals)
        {
            if (ageOf(a) == TFCAnimalProperties.Age.ADULT)
            {
                final int[] count = adultsBySpecies.computeIfAbsent(a.getType(), k -> new int[2]);
                count[((TFCAnimalProperties) a).getGender() == TFCAnimalProperties.Gender.MALE ? 0 : 1]++;
            }
        }

        final Animal hurt = mostHurtCullable(animals, level, adultsBySpecies);
        if (hurt != null)
        {
            return hurt;
        }

        final Animal old = leastFamiliar(animals, a -> ageOf(a) == TFCAnimalProperties.Age.OLD);
        if (old != null)
        {
            return old;
        }

        EntityType<?> cullType = null;
        TFCAnimalProperties.Gender cullGender = null;
        int bestSurplus = 0;
        for (final Map.Entry<EntityType<?>, int[]> entry : adultsBySpecies.entrySet())
        {
            final int maleSurplus = entry.getValue()[0] - maleReserve(level);
            final int femaleSurplus = entry.getValue()[1] - femaleReserve(level);
            if (maleSurplus > bestSurplus)
            {
                bestSurplus = maleSurplus;
                cullType = entry.getKey();
                cullGender = TFCAnimalProperties.Gender.MALE;
            }
            if (femaleSurplus > bestSurplus)
            {
                bestSurplus = femaleSurplus;
                cullType = entry.getKey();
                cullGender = TFCAnimalProperties.Gender.FEMALE;
            }
        }
        if (cullType == null)
        {
            return null; // no species over its reserve
        }
        final EntityType<?> type = cullType;
        final TFCAnimalProperties.Gender gender = cullGender;
        return leastFamiliar(animals, a -> ageOf(a) == TFCAnimalProperties.Age.ADULT
                                             && a.getType() == type
                                             && ((TFCAnimalProperties) a).getGender() == gender);
    }

    /** Age of a TFC animal, or {@code null} if it isn't TFC livestock. */
    private static TFCAnimalProperties.Age ageOf(final Animal animal)
    {
        return animal instanceof TFCAnimalProperties tfc ? tfc.getAgeType() : null;
    }

    /**
     * The most-hurt <b>cull-eligible</b> animal that is below full health (lowest current health wins), or {@code null}
     * if none are hurt. Lets the butcher resume an interrupted kill instead of starting over on a fresh animal.
     */
    private static Animal mostHurtCullable(final List<? extends Animal> animals, final int level, final Map<EntityType<?>, int[]> adultsBySpecies)
    {
        Animal best = null;
        float bestHealth = Float.MAX_VALUE;
        for (final Animal a : animals)
        {
            if (!(a instanceof TFCAnimalProperties) || a.getHealth() >= a.getMaxHealth() || !isCullable(a, level, adultsBySpecies))
            {
                continue;
            }
            if (best == null || a.getHealth() < bestHealth)
            {
                best = a;
                bestHealth = a.getHealth();
            }
        }
        return best;
    }

    /** Whether this animal is a legitimate cull target right now: an OLD animal, or an adult of a species+gender over its reserve. */
    private static boolean isCullable(final Animal animal, final int level, final Map<EntityType<?>, int[]> adultsBySpecies)
    {
        final TFCAnimalProperties.Age age = ageOf(animal);
        if (age == TFCAnimalProperties.Age.OLD)
        {
            return true;
        }
        if (age != TFCAnimalProperties.Age.ADULT)
        {
            return false;
        }
        final int[] count = adultsBySpecies.get(animal.getType());
        if (count == null)
        {
            return false;
        }
        return ((TFCAnimalProperties) animal).getGender() == TFCAnimalProperties.Gender.MALE
                 ? count[0] > maleReserve(level)
                 : count[1] > femaleReserve(level);
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

    /**
     * The breeding item TFC herding huts request/equip/feed: a TFC grain, NBT-agnostic so food decay doesn't break
     * matching. Amount is <b>2</b> to match vanilla's {@code new ItemStorage(Items.WHEAT, 2)}: the herder's
     * {@code prepareForHerding} requests with {@code minCount = amount}, and the FEED/BREED gate needs {@code > 1} in
     * inventory — so amount 1 would leave a worker stuck at a single grain (can't feed, yet "has enough" to skip
     * restocking). Amount 2 makes it restock whenever it drops below 2.
     */
    public static List<ItemStorage> breedingFood()
    {
        List<ItemStorage> list = feedList;
        if (list == null)
        {
            final Item grain = ForgeRegistries.ITEMS.getValue(FEED_ID);
            // amount = 2, ignoreDamage = false, ignoreNBT = true
            list = List.of(new ItemStorage(new ItemStack(grain), 2, false, true));
            feedList = list;
        }
        return list;
    }
}
