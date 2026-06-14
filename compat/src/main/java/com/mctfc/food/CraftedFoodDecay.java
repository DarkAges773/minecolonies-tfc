package com.mctfc.food;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Fixes up TFC food produced by colony <b>worker</b> crafting, which copies a cached
 * {@code RecipeStorage#getPrimaryOutput()} and never calls {@code Recipe#assemble} — so TFC's
 * {@code ItemStackProvider} modifiers (which only run in {@code assemble}) never apply. Used by
 * {@code MixinRecipeStorage}; the player path is native (a real crafting table runs {@code assemble}).
 *
 * <p>Two fixes, picked per output by {@code MixinRecipeStorage}:
 * <ul>
 *   <li>{@link #carryDecay} — for <b>static</b> foods (e.g. our MineColonies dishes): reproduce
 *       {@code copy_oldest_food}, ageing the output from its oldest ingredient (TFC's
 *       {@code FoodCapability#updateFoodFromAllPrevious}).</li>
 *   <li>{@link #realizeFromRecipe} — for <b>dynamic</b> foods (TFC sandwiches, etc.): the cached output is
 *       unrealized ({@code FoodData.EMPTY}). Re-run the real recipe's {@code assemble} with the consumed
 *       ingredients so the {@code meal} modifier computes the real nutrition (it also fixes decay, freshly, as
 *       TFC does for a player-crafted meal).</li>
 * </ul>
 */
public final class CraftedFoodDecay
{
    private CraftedFoodDecay() {}

    /**
     * If {@code output} is a TFC food, age it from its (food) {@code inputs} — the {@code copy_oldest_food} rule.
     * Mutates {@code output}'s food capability in place. {@code updateFoodFromAllPrevious} skips non-food entries
     * and no-ops when none are food, so callers may pass a mixed ingredient list.
     */
    public static void carryDecay(final Collection<ItemStack> inputs, final ItemStack output)
    {
        if (output.isEmpty() || inputs.isEmpty() || !FoodCapability.has(output))
        {
            return;
        }
        FoodCapability.updateFoodFromAllPrevious(inputs, output);
    }

    /**
     * Re-run the source recipe's assembly with the consumed ingredients, so input-dependent
     * {@code ItemStackProvider} modifiers (notably {@code tfc:meal}) run and a dynamic food comes out realized.
     * Returns {@code null} (caller falls back to the cached output) if the recipe can't be resolved, isn't a
     * crafting recipe, throws on the synthetic container, or yields a different item than expected.
     *
     * @param level         the server level (for the recipe manager + registry access)
     * @param recipeSource  {@code RecipeStorage#getRecipeSource()} — the recipe id to re-assemble
     * @param cachedOutput  the cached primary output (its item is the expected result)
     * @param consumedFood  the TFC-food ingredients consumed this craft (what {@code meal} sums)
     */
    @Nullable
    public static ItemStack realizeFromRecipe(@Nullable final ServerLevel level, @Nullable final ResourceLocation recipeSource,
        final ItemStack cachedOutput, final List<ItemStack> consumedFood)
    {
        if (level == null || recipeSource == null)
        {
            return null;
        }
        final Recipe<?> recipe = level.getRecipeManager().byKey(recipeSource).orElse(null);
        if (!(recipe instanceof CraftingRecipe crafting))
        {
            return null;
        }
        final ItemStack result;
        try
        {
            result = crafting.assemble(buildCraftingInput(consumedFood), level.registryAccess());
        }
        catch (final Exception e)
        {
            // Any recipe that dislikes a synthetic flat container: fall back to the cached output.
            return null;
        }
        if (result.isEmpty() || !result.is(cachedOutput.getItem()))
        {
            return null;
        }
        return result;
    }

    /**
     * A throwaway 3×3 crafting grid holding the consumed ingredients. The {@code meal} modifier reads the grid
     * flat (positions don't matter), and {@code assemble} doesn't re-validate the recipe shape, so a flat fill is
     * enough to realize the nutrition. The dummy menu's callbacks are never exercised (no slots are attached).
     */
    private static CraftingContainer buildCraftingInput(final List<ItemStack> items)
    {
        final AbstractContainerMenu dummyMenu = new AbstractContainerMenu(null, -1)
        {
            @Override
            public ItemStack quickMoveStack(final Player player, final int index) { return ItemStack.EMPTY; }

            @Override
            public boolean stillValid(final Player player) { return true; }
        };
        final TransientCraftingContainer container = new TransientCraftingContainer(dummyMenu, 3, 3);
        for (int i = 0; i < items.size() && i < 9; i++)
        {
            container.setItem(i, items.get(i).copy());
        }
        return container;
    }
}
