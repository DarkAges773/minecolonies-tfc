package com.mctfc.food;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodHandler;
import net.minecraft.world.item.ItemStack;

/**
 * Makes a TFC food stack a non-decaying <b>template</b> — for the food MineColonies shows in GUIs, requests and
 * restaurant menus, which are references to a <i>dish</i>, not real food that should rot.
 *
 * <p>It sets the creation date to TFC's persistent {@link FoodHandler#NEVER_DECAY_CREATION_DATE} sentinel ({@code -2},
 * which {@code getRottenDate} maps to "never rots"). This is deliberately <b>not</b> TFC's
 * {@code FoodCapability.setStackNonDecaying} — that only flips the transient {@code isNonDecaying} flag, which TFC's
 * {@code serializeNBT} does <b>not</b> persist; it's lost across save/network-sync (leaving {@code creationDate = -1},
 * an ancient date), so a menu/request marked that way <i>still</i> rots once stored or sent to the client. The
 * creation-date sentinel, by contrast, is serialized and synced like any normal food date, so the template stays
 * fresh everywhere.
 *
 * <p>Cosmetic only — TFC food decay lives in this capability, not the item tag, and MineColonies' {@code ItemStorage}
 * is caps-blind, so menu/request matching never depended on it. Only ever applied to template stacks, never to real
 * food in storage.
 */
public final class FoodTemplates
{
    private FoodTemplates() {}

    /** A non-decaying copy of {@code stack} if it's TFC food; otherwise {@code stack} unchanged. */
    public static ItemStack nonDecaying(final ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !FoodCapability.has(stack))
        {
            return stack;
        }
        return FoodCapability.setCreationDate(stack.copy(), FoodHandler.NEVER_DECAY_CREATION_DATE);
    }
}
