package com.mctfc.cook;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * The two TFC dynamic dishes the Chef can compose — {@link #SALAD} and {@link #SOUP}. Each constant owns everything
 * that differs between the dishes: the valid-ingredient test, the {@link TfcDishes} compute function, and the GUI
 * title / mode-switch translation keys. Centralising them here keeps the menu, screen, and network code free of
 * {@code salad ? … : …} branching.
 *
 * <p>Serialized over the wire as a single byte via {@link #id()} (stable: {@code SALAD=0}, {@code SOUP=1}); decode
 * with {@link #byId(int)}.
 */
public enum DishType
{
    SALAD(0, TfcDishes::isSaladIngredient, TfcDishes::salad,
        "mctfc.gui.compose.salad.title", "mctfc.gui.compose.switch.salad"),
    SOUP(1, TfcDishes::isSoupIngredient, TfcDishes::soup,
        "mctfc.gui.compose.soup.title", "mctfc.gui.compose.switch.soup");

    private final int                                               id;
    private final Predicate<ItemStack>                              ingredientOk;
    private final BiFunction<List<ItemStack>, ItemStack, ItemStack> compute;
    private final String                                            titleKey;
    private final String                                            switchToKey;

    DishType(final int id, final Predicate<ItemStack> ingredientOk,
        final BiFunction<List<ItemStack>, ItemStack, ItemStack> compute,
        final String titleKey, final String switchToKey)
    {
        this.id = id;
        this.ingredientOk = ingredientOk;
        this.compute = compute;
        this.titleKey = titleKey;
        this.switchToKey = switchToKey;
    }

    /** Stable wire id (SALAD=0, SOUP=1). */
    public int id()
    {
        return id;
    }

    /** Whether {@code stack} is a valid ingredient for this dish (drives the compose-window ghost slots). */
    public boolean accepts(final ItemStack stack)
    {
        return ingredientOk.test(stack);
    }

    /** Compute this dish's output for an ingredient set + bowl (see {@link TfcDishes#salad}/{@link TfcDishes#soup}). */
    public ItemStack compute(final List<ItemStack> ingredients, final ItemStack bowl)
    {
        return compute.apply(ingredients, bowl);
    }

    /** The compose-screen window title for this dish. */
    public Component title()
    {
        return Component.translatable(titleKey);
    }

    /** The label for a button that switches <i>to</i> this dish (shown while composing the other one). */
    public Component switchToLabel()
    {
        return Component.translatable(switchToKey);
    }

    /** The other dish (there are exactly two), for the compose-screen mode-switch button. */
    public DishType other()
    {
        return this == SALAD ? SOUP : SALAD;
    }

    /** Decode a wire id back to its dish (anything other than {@code SOUP}'s id is {@code SALAD}). */
    public static DishType byId(final int id)
    {
        return id == SOUP.id ? SOUP : SALAD;
    }
}
