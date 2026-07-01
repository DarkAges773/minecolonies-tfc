package com.mctfc.cook;

import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.recipes.PotRecipe;
import net.dries007.tfc.common.recipes.SimplePotRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.common.recipes.outputs.ItemStackProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feeds every static TFC <b>pot</b> ({@code tfc:pot}) recipe that produces a <b>food item</b> to the MineColonies
 * <b>Chef</b> (Kitchen) as a colony crafter recipe — the pot equivalent of {@link com.mctfc.smithing.AnvilRecipeBridge}.
 * These are the fixed pot foods (base TFC: boiled egg, cooked rice — add-ons like FirmaLife add more) as opposed to the
 * <i>dynamic</i> {@code tfc:pot_soup} dishes, which are player-composed instead (see {@link TfcDishes}).
 *
 * <p>Two filters. <b>Food only:</b> skip recipes with a <b>fluid</b> output ({@code getDisplayFluid} non-empty — the
 * dyes, tallow, …) and keep only those whose first item output carries a TFC food capability. <b>Water input only:</b>
 * we abstract the pot's fluid away (colony crafters consume items, not fluids — like the Blacksmith abstracts the
 * anvil's heat), and abstracting a free/renewable <i>water</i> is fair, but abstracting a scarce fluid (milk, brine, an
 * add-on's exotic liquid) would hand out the food for nothing — so a recipe whose fluid ingredient doesn't accept water
 * is dropped rather than made free. Base TFC's food pot recipes are all water, so this only bites add-ons.
 *
 * <p>Inputs are the recipe's item ingredients verbatim (first stack of each, TFC's own item for tag ingredients),
 * duplicates merged (e.g. {@code cooked_rice_3} = 3&times; rice grain). Decay carries onto the output via the existing
 * {@code MixinRecipeStorage}.
 *
 * <p>{@link #injectAll()} is invoked from {@code MixinCustomRecipeManager} at the TAIL of
 * {@code CustomRecipeManager#resolveTemplates()} — the same seam {@link com.mctfc.smithing.AnvilRecipeBridge} uses
 * (after the recipe map is reset+reloaded, tags bound, right before it syncs to clients). Re-runs are harmless:
 * {@code addRecipe} overwrites by recipe id.
 */
public final class PotRecipeBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PotRecipeBridge.class);

    /** The Chef's crafting module id, as {@code CustomRecipe} matches it ({@code jobPath + "_" + moduleId}). */
    private static final String CHEF_CRAFTER = "chef_crafting";

    private PotRecipeBridge() {}

    /** Regenerate and register all Chef pot-food recipes from the current TFC recipe set. Server-side no-op-safe. */
    public static void injectAll()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
        {
            return;
        }
        final RecipeManager recipes = server.getRecipeManager();

        int count = 0;
        for (final PotRecipe recipe : recipes.getAllRecipesFor(TFCRecipeTypes.POT.get()))
        {
            if (!(recipe instanceof SimplePotRecipe pot) || !pot.getDisplayFluid().isEmpty())
            {
                continue; // dynamic soups aren't SimplePotRecipe; a fluid output means a dye/tallow, not a dish
            }
            if (!pot.getFluidIngredient().ingredient().fluids().contains(Fluids.WATER))
            {
                continue; // water-input recipes only — we abstract the pot's fluid away, and abstracting a
                          // free/renewable water is fair, but doing so for a scarce fluid (milk, brine, an add-on's
                          // exotic liquid) would hand out the food for nothing, so those are dropped instead.
            }
            final List<ItemStackProvider> outputs = pot.getOutputProviders();
            if (outputs.isEmpty())
            {
                continue;
            }
            final ItemStack result = outputs.get(0).getEmptyStack();
            if (result.isEmpty() || !FoodCapability.has(result))
            {
                continue; // food items only
            }

            final List<ItemStorage> inputs = new ArrayList<>();
            boolean ok = true;
            for (final Ingredient ingredient : pot.getItemIngredients())
            {
                final ItemStack[] items = ingredient.getItems();
                if (items.length == 0)
                {
                    ok = false;
                    break;
                }
                inputs.add(new ItemStorage(items[0].copyWithCount(1)));
            }
            if (!ok || inputs.isEmpty())
            {
                continue;
            }

            addRecipe(recipe.getId(), mergeDuplicates(inputs), result);
            count++;
        }
        LOGGER.info("Registered {} TFC pot food recipes for the colony chef", count);
    }

    private static void addRecipe(final ResourceLocation sourceId, final List<ItemStorage> inputs, final ItemStack result)
    {
        final ResourceLocation recipeId =
            new ResourceLocation("mctfc", "pot/" + sourceId.getNamespace() + "/" + sourceId.getPath());
        CustomRecipeManager.getInstance().addRecipe(new CustomRecipe(
            CHEF_CRAFTER,
            0,
            5,
            false,
            true,
            recipeId,
            Set.of(),
            Set.of(),
            null,
            ModEquipmentTypes.none.get(),
            inputs,
            result,
            List.of(),
            List.of(),
            Blocks.AIR));
    }

    /** Fold identical inputs (e.g. {@code cooked_rice_3} = rice + rice + rice → 3&times; rice) into one entry. */
    private static List<ItemStorage> mergeDuplicates(final List<ItemStorage> inputs)
    {
        final Map<Item, ItemStorage> merged = new LinkedHashMap<>();
        for (final ItemStorage input : inputs)
        {
            final ItemStorage existing = merged.get(input.getItem());
            if (existing != null && ItemStack.isSameItemSameTags(existing.getItemStack(), input.getItemStack()))
            {
                existing.setAmount(existing.getAmount() + input.getAmount());
            }
            else
            {
                merged.put(input.getItem(), input);
            }
        }
        return new ArrayList<>(merged.values());
    }
}
