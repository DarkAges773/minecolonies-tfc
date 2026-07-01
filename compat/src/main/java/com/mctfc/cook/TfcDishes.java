package com.mctfc.cook;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.capabilities.food.DynamicBowlHandler;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodData;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.dries007.tfc.common.capabilities.food.Nutrient;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless replica of TFC's two <b>dynamic device dishes</b> — the <b>salad</b> and the <b>pot soup</b> — so the
 * MineColonies <b>Chef</b> can make them as ordinary taught colony recipes (see
 * <a href="../../../../../../../docs/tfc-chef-dishes.md">tfc-chef-dishes.md</a>).
 *
 * <p>Neither dish is a callable recipe headlessly: the salad math is private inside
 * {@code SaladContainer#setAndUpdateSlots}, and {@code SoupPotRecipe#getOutput} needs a live {@code PotBlockEntity}.
 * Both algorithms are short and use only public, common (non-client) food primitives, so we transcribe them
 * <b>byte-for-byte</b> from the TFC bytecode here — the produced stack (its {@code DynamicBowlHandler} cap: food data,
 * ingredient list, bowl, creation date) is identical to what TFC's GUI/pot would emit. Pure: no world/AI state, so the
 * compose window can preview it client-side and the Chef can realise it server-side.
 *
 * <p>The player composing an ingredient set in the compose window <i>fixes</i> a discrete recipe out of this dynamic
 * space: we compute the output once, teach it, and the Chef repeats it. Food <i>values</i> are deterministic for a
 * fixed ingredient set; only freshness varies per craft, which is re-stamped at craft time (see
 * {@code MixinRecipeStorage}).
 */
public final class TfcDishes
{
    private TfcDishes() {}

    /**
     * Both dishes take 1–5 ingredients <b>plus a bowl</b>. A salad assembles in TFC's bowl GUI; a soup is boiled in a
     * pot and then <b>extracted with a bowl</b> (the soup item is a {@link DynamicBowlHandler} that returns the bowl
     * when eaten) — so for the colony both consume a bowl, and we abstract the soup's water away. That makes the two
     * UIs identical, which is why the compose screen reuses TFC's salad assets for both.
     */
    public static final int MAX_INGREDIENTS = 5;

    // --- TFC salad constants (SaladContainer#setAndUpdateSlots) ---
    private static final float SALAD_BLEND  = 0.75f; // blending penalty on water/saturation/every nutrient
    private static final int   SALAD_HUNGER = 4;
    private static final float SALAD_DECAY  = 4.0f;

    // --- TFC soup constants (SoupPotRecipe#getOutput / SOUP_HUNGER_VALUE / SOUP_DECAY_MODIFIER) ---
    private static final float SOUP_SEED_WATER      = 20f; // soup starts wetter than a salad
    private static final float SOUP_SEED_SATURATION = 2f;
    private static final int   SOUP_HUNGER          = 4;
    private static final float SOUP_DECAY           = 3.5f;

    /**
     * {@code #tfc:foods/usable_in_soup} — fruits + vegetables + raw/cooked meats + cooked rice. TFC exposes
     * {@code USABLE_IN_SALAD}/{@code SALAD_BOWLS}/{@code SOUP_BOWLS} as constants but <b>not</b> this one, so we build
     * the {@link TagKey} by hand (item tags are baked into the item — resolvable headlessly via {@link ItemStack#is}).
     */
    public static final TagKey<Item> USABLE_IN_SOUP =
        TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "foods/usable_in_soup"));

    /** A valid, non-rotten TFC food usable as a salad ingredient (filters the compose-window picker). */
    public static boolean isSaladIngredient(final ItemStack stack)
    {
        return FoodCapability.has(stack) && stack.is(TFCTags.Items.USABLE_IN_SALAD) && !FoodCapability.isRotten(stack);
    }

    /** A valid, non-rotten TFC food usable as a soup ingredient. */
    public static boolean isSoupIngredient(final ItemStack stack)
    {
        return FoodCapability.has(stack) && stack.is(USABLE_IN_SOUP) && !FoodCapability.isRotten(stack);
    }

    /** A bowl accepted by either dish ({@code tfc:ceramic/bowl} or {@code minecraft:bowl}; {@code SALAD_BOWLS} and
     * {@code SOUP_BOWLS} are both {@code #tfc:bowls}). */
    public static boolean isDishBowl(final ItemStack stack)
    {
        return stack.is(TFCTags.Items.SALAD_BOWLS);
    }

    /**
     * Compute the salad {@link ItemStack} for an ingredient set + bowl, replicating
     * {@code SaladContainer#setAndUpdateSlots}. Empty if invalid: no/too-many ingredients, no bowl, any ingredient
     * rotten, or no positive dominant nutrient (an all-zero-nutrient salad makes nothing in TFC either).
     *
     * <p>Accumulate water/saturation/nutrients over the (food-cap) ingredients, apply the {@code ×0.75} blending
     * penalty, pick the argmax nutrient as the {@code *_salad} category, and stamp the dynamic-bowl output with
     * {@code FoodData.create(4, water, saturation, nutrients, 4.0f)}, the ingredient list (1 each), the consumed bowl,
     * and a fresh creation date. Output count mirrors TFC: {@code min(min ingredient count, bowl count)}.
     */
    public static ItemStack salad(final List<ItemStack> ingredients, final ItemStack bowl)
    {
        if (bowl == null || bowl.isEmpty() || ingredients == null
            || ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS)
        {
            return ItemStack.EMPTY;
        }

        float water = 0f;
        float saturation = 0f;
        final float[] nutrients = new float[Nutrient.TOTAL];
        int count = 0;
        int outputCount = 64;
        final List<ItemStack> used = new ArrayList<>();

        for (final ItemStack stack : ingredients)
        {
            final IFood food = FoodCapability.get(stack);
            if (food == null)
            {
                continue;
            }
            used.add(stack.copyWithCount(1));
            if (food.isRotten())
            {
                return ItemStack.EMPTY; // TFC aborts (count := 0) on any rotten ingredient
            }
            final FoodData data = food.getData();
            water += data.water();
            saturation += data.saturation();
            for (final Nutrient n : Nutrient.VALUES)
            {
                nutrients[n.ordinal()] += data.nutrient(n);
            }
            count++;
            outputCount = Math.min(outputCount, stack.getCount());
        }
        outputCount = Math.min(outputCount, bowl.getCount());
        if (count == 0)
        {
            return ItemStack.EMPTY;
        }

        water *= SALAD_BLEND;
        saturation *= SALAD_BLEND;
        Nutrient dominant = null;
        float max = 0f;
        for (final Nutrient n : Nutrient.VALUES)
        {
            nutrients[n.ordinal()] *= SALAD_BLEND;
            if (nutrients[n.ordinal()] > max)
            {
                max = nutrients[n.ordinal()];
                dominant = n;
            }
        }
        if (dominant == null)
        {
            return ItemStack.EMPTY; // no positive nutrient → no salad (TFC leaves the output slot empty)
        }

        final ItemStack output = new ItemStack(TFCItems.SALADS.get(dominant).get(), outputCount);
        final IFood cap = FoodCapability.get(output);
        if (cap instanceof DynamicBowlHandler dyn)
        {
            dyn.setCreationDate(FoodCapability.getRoundedCreationDate());
            dyn.setIngredients(used);
            dyn.setBowl(bowl.copy().split(1));
            dyn.setFood(FoodData.create(SALAD_HUNGER, water, saturation, nutrients, SALAD_DECAY));
        }
        return output;
    }

    /**
     * Compute the pot-soup {@link ItemStack} for an ingredient set + bowl, replicating {@code SoupPotRecipe#getOutput}
     * plus the bowl-extraction step ({@code DynamicBowlHandler}). Empty if invalid: no/too-many ingredients, no bowl,
     * or any rotten.
     *
     * <p>Parallel to {@link #salad}: the colony consumes a bowl and abstracts the soup's water (TFC boils 100&nbsp;mB
     * in the pot, then a bowl extracts the soup — for us that's one craft). Seed {@code water=20, saturation=2},
     * accumulate, apply the {@code ×(1 − 0.05·count)} dilution, pick the argmax nutrient (seeded to {@code GRAIN}, so a
     * soup always has a category), and stamp the dynamic-bowl output with
     * {@code FoodData.create(4, water, saturation, nutrients, 3.5f)}, the ingredient list, the consumed bowl (so eating
     * it returns the bowl, like a real TFC soup), and a fresh creation date. Output count {@code min(min ingredient
     * count, bowl count)} — one bowl in, one soup out.
     */
    public static ItemStack soup(final List<ItemStack> ingredients, final ItemStack bowl)
    {
        if (bowl == null || bowl.isEmpty() || ingredients == null
            || ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS)
        {
            return ItemStack.EMPTY;
        }

        float water = SOUP_SEED_WATER;
        float saturation = SOUP_SEED_SATURATION;
        final float[] nutrients = new float[Nutrient.TOTAL];
        int count = 0;
        int outputCount = 64;
        final List<ItemStack> used = new ArrayList<>();

        for (final ItemStack stack : ingredients)
        {
            final IFood food = FoodCapability.get(stack);
            if (food == null)
            {
                continue;
            }
            used.add(stack.copyWithCount(1));
            if (food.isRotten())
            {
                return ItemStack.EMPTY;
            }
            final FoodData data = food.getData();
            water += data.water();
            saturation += data.saturation();
            for (final Nutrient n : Nutrient.VALUES)
            {
                nutrients[n.ordinal()] += data.nutrient(n);
            }
            count++;
            outputCount = Math.min(outputCount, stack.getCount());
        }
        outputCount = Math.min(outputCount, bowl.getCount());
        if (count == 0)
        {
            return ItemStack.EMPTY;
        }

        final float m = 1f - 0.05f * count;
        water *= m;
        saturation *= m;
        Nutrient dominant = Nutrient.GRAIN;
        float max = 0f;
        for (final Nutrient n : Nutrient.VALUES)
        {
            nutrients[n.ordinal()] *= m;
            if (nutrients[n.ordinal()] > max)
            {
                max = nutrients[n.ordinal()];
                dominant = n;
            }
        }

        final ItemStack output = new ItemStack(TFCItems.SOUPS.get(dominant).get(), outputCount);
        final IFood cap = FoodCapability.get(output);
        if (cap instanceof DynamicBowlHandler dyn)
        {
            dyn.setCreationDate(FoodCapability.getRoundedCreationDate());
            dyn.setIngredients(used);
            dyn.setBowl(bowl.copy().split(1));
            dyn.setFood(FoodData.create(SOUP_HUNGER, water, saturation, nutrients, SOUP_DECAY));
        }
        return output;
    }
}
