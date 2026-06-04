package com.mctfc.food;

import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Decay-aware stack-merging for MineColonies inventories.
 *
 * <p>TFC stores a food's {@code creationDate} (and decay traits) in a Forge <b>capability</b> — serialized to
 * the stack's {@code ForgeCaps} and synced separately, <i>not</i> in the vanilla item {@code tag}. MineColonies'
 * {@code ItemStackUtils.compareItemStacksIgnoreStackSize} only inspects {@code ItemStack#getTag()}, so it is
 * blind to that capability: two TFC crops of different freshness read as identical. The custom
 * {@code InventoryCitizen#insertItem} uses that comparison as its merge gate and then grows the <i>existing</i>
 * slot, so a fresh harvest poured onto an older/rotten stack adopts the older stack's caps and ages instantly.
 *
 * <p>{@link #canMerge} is the extra gate: for TFC food it defers to the vanilla, caps-aware
 * {@link ItemHandlerHelper#canItemStacksStack} (which compares the food caps — same rounded creation date and
 * traits), exactly the rule TFC itself uses for slot stacking. Foods made in the same decay window still stack;
 * differently-aged ones don't. Non-food items are unaffected (returns {@code true}), so request/storage matching
 * — which deliberately ignores freshness so any wheat fulfils a wheat request — keeps working as before.
 */
public final class FoodStackingHelper
{
    private FoodStackingHelper() {}

    /**
     * Whether {@code incoming} may be merged into the {@code existing} slot stack, on top of MineColonies' own
     * equality test. {@code true} for everything that isn't TFC food; for TFC food, only when the two stacks are
     * genuinely stackable including their food capability (so fresh produce never merges into a rotten stack).
     */
    public static boolean canMerge(final ItemStack existing, final ItemStack incoming)
    {
        if (!FoodCapability.has(existing) && !FoodCapability.has(incoming))
        {
            return true;
        }
        return ItemHandlerHelper.canItemStacksStack(existing, incoming);
    }
}
