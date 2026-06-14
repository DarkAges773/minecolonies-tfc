package com.mctfc.mixin;

import com.mctfc.food.CraftedFoodDecay;
import com.minecolonies.api.crafting.RecipeStorage;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes colony <b>worker</b> crafting produce correct TFC food — the worker-side fix for behaviour that, for a
 * player, happens natively in {@code Recipe#assemble} at a crafting table.
 *
 * <p>{@code RecipeStorage} caches {@code primaryOutput = recipe.getPrimaryOutput()} at construction and
 * {@code fullfillRecipeAndCopy} (the single chokepoint — every {@code fullfillRecipe} overload delegates to it)
 * consumes the inputs then inserts <i>copies</i> of that cached output. It never builds a {@code CraftingContainer}
 * or calls {@code assemble}, so TFC's {@code ItemStackProvider} modifiers never run. Two consequences, fixed per
 * output:
 * <ul>
 *   <li><b>Dynamic foods</b> (TFC sandwiches, …) come out unrealized ({@code FoodData.EMPTY} → ~no nutrition).
 *       We re-run the real recipe's {@code assemble} with the consumed ingredients
 *       ({@link CraftedFoodDecay#realizeFromRecipe}) so the {@code meal} modifier computes the real nutrition —
 *       general across any crafting-table dynamic-food recipe (TFC's or an add-on's), not just sandwiches.</li>
 *   <li><b>Static foods</b> (e.g. our MineColonies dishes) come out fresh, ignoring ingredient age. We reproduce
 *       {@code copy_oldest_food} ({@link CraftedFoodDecay#carryDecay}).</li>
 * </ul>
 *
 * <p>Mechanism — injectors on {@code fullfillRecipeAndCopy}:
 * <ul>
 *   <li>HEAD — reset the per-thread capture list and record the level (server crafting is single-threaded; the
 *       thread-local is leak-safe and reset each call, covering early returns).</li>
 *   <li>{@code IItemHandler#extractItem} — record each consumed stack that's a TFC food (the actual extracted
 *       stacks carry the caps; {@code getCleanedInput}'s {@code ItemStorage}s are caps-blind).</li>
 *   <li>the {@code getPrimaryOutput()} call feeding {@code insertCraftedItems} — swap in the realized / decay-carried
 *       result (a copy; the cached template stays clean, and {@code insertCraftedItems}' own copies inherit it).</li>
 * </ul>
 *
 * <p>Scoped to TFC-food outputs (non-food crafting is untouched). {@code remap = false}: MineColonies' own class.
 */
@Mixin(value = RecipeStorage.class, remap = false)
public abstract class MixinRecipeStorage
{
    @Unique
    private static final ThreadLocal<List<ItemStack>> mctfc$consumedFood = ThreadLocal.withInitial(ArrayList::new);

    @Unique
    private static final ThreadLocal<ServerLevel> mctfc$level = new ThreadLocal<>();

    @Inject(method = "fullfillRecipeAndCopy", at = @At("HEAD"))
    private void mctfc$beginCapture(final LootParams context, final List<IItemHandler> handlers, final boolean doInsert,
        final CallbackInfoReturnable<List<ItemStack>> cir)
    {
        mctfc$consumedFood.get().clear();
        mctfc$level.set(context.getLevel());
    }

    @Redirect(
        method = "fullfillRecipeAndCopy",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mctfc$captureExtractedFood(final IItemHandler handler, final int slot, final int amount, final boolean simulate)
    {
        final ItemStack extracted = handler.extractItem(slot, amount, simulate);
        if (!simulate && FoodCapability.has(extracted))
        {
            mctfc$consumedFood.get().add(extracted.copy());
        }
        return extracted;
    }

    @Redirect(
        method = "fullfillRecipeAndCopy",
        at = @At(value = "INVOKE",
            target = "Lcom/minecolonies/api/crafting/RecipeStorage;getPrimaryOutput()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mctfc$fixCraftedFood(final RecipeStorage self)
    {
        final ItemStack output = self.getPrimaryOutput();
        if (!FoodCapability.has(output))
        {
            return output;
        }
        final List<ItemStack> consumed = mctfc$consumedFood.get();
        final FoodDefinition def = FoodCapability.getDefinition(output);
        if (def != null && def.getHandlerType() != FoodDefinition.HandlerType.STATIC)
        {
            // Dynamic food: cached output is unrealized — re-assemble to compute its nutrition via `meal`.
            final ItemStack realized = CraftedFoodDecay.realizeFromRecipe(
                mctfc$level.get(), self.getRecipeSource(), output, consumed);
            return realized != null ? realized : output;
        }
        // Static food: carry the oldest ingredient's decay onto the already-correct output.
        if (consumed.isEmpty())
        {
            return output;
        }
        final ItemStack carried = output.copy();
        CraftedFoodDecay.carryDecay(consumed, carried);
        return carried;
    }
}
