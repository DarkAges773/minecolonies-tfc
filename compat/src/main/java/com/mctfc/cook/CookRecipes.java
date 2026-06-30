package com.mctfc.cook;

import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Data model for the TFC-flavoured MineColonies <b>Cook</b> (dining hall). MineColonies' vanilla cook turns a
 * vanilla-furnace-smeltable raw food into its cooked result in a furnace; TFC food isn't vanilla-smeltable, so the
 * vanilla cook never cooks it. TFC instead defines {@code tfc:heating} recipes that turn raw food into cooked food
 * (e.g. {@code minecraft:egg → tfc:food/cooked_egg}, raw → cooked meat), at ~200&nbsp;°C, carrying the food's decay
 * across with the {@code tfc:copy_food} output modifier.
 *
 * <p>This class is <b>pure query</b> — no world or AI state. Unlike the smelter (which hard-codes TFC's ore table),
 * the cook can read TFC's recipes directly: a "cook" recipe is simply a heating recipe whose output is an
 * <b>item</b> (cooked food) rather than a fluid (the smelter's ore melts output a fluid). The behavior reads this
 * to decide what counts as cookable, what it cooks into (for menu-matching), and to produce the cooked stack with
 * decay preserved.
 */
public final class CookRecipes
{
    private CookRecipes() {}

    /** Default cook temperature (°C) if a recipe doesn't specify one — every TFC food heating recipe is 200. */
    public static final float DEFAULT_COOK_TEMP = 200f;

    /**
     * The TFC heating recipe that cooks this stack into a food <b>item</b>, or {@code null} if there is none. We
     * accept only item-output heating recipes (raw → cooked food); fluid-output heating recipes (ore → molten
     * metal) belong to the smelter, so a stack that melts to a fluid is not "cookable".
     */
    public static HeatingRecipe cookRecipe(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return null;
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(stack);
        if (recipe == null || !recipe.getDisplayOutputFluid().isEmpty())
        {
            return null;
        }
        return recipe;
    }

    /** Whether this stack is a raw food the cook can cook (has an item-output heating recipe). */
    public static boolean isCookable(final ItemStack stack)
    {
        return cookRecipe(stack) != null;
    }

    /**
     * The cooked result <b>template</b> (the recipe's output, with no input-specific food data applied) — for
     * matching against the restaurant menu. Empty if {@code raw} isn't cookable.
     */
    public static ItemStack cookedResult(final ItemStack raw, final RegistryAccess access)
    {
        final HeatingRecipe recipe = cookRecipe(raw);
        return recipe == null ? ItemStack.EMPTY : recipe.getResultItem(access);
    }

    /**
     * Cook the given raw stack into its cooked food, applying {@code tfc:copy_food} so the cooked food inherits the
     * raw food's decay/creation date. TFC food heating recipes are 1:1, so we scale the (count-1) recipe output up
     * to the input count, capped at the cooked item's max stack size. Empty if {@code raw} isn't cookable.
     */
    public static ItemStack cook(final ItemStack raw, final RegistryAccess access)
    {
        final HeatingRecipe recipe = cookRecipe(raw);
        if (recipe == null)
        {
            return ItemStack.EMPTY;
        }
        final ItemStack cooked = recipe.assemble(new ItemStackInventory(raw), access);
        if (cooked.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        cooked.setCount(Math.min(Math.max(1, raw.getCount()), cooked.getMaxStackSize()));
        return cooked;
    }

    /** The temperature (°C) this raw food must reach to cook; {@link #DEFAULT_COOK_TEMP} if it isn't cookable. */
    public static float cookTemp(final ItemStack raw)
    {
        final HeatingRecipe recipe = cookRecipe(raw);
        return recipe == null ? DEFAULT_COOK_TEMP : recipe.getTemperature();
    }
}
