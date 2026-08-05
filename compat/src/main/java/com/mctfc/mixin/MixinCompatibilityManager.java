package com.mctfc.mixin;

import com.minecolonies.api.compatibility.CompatibilityManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.mctfc.food.FoodTemplates;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.smelter.SmelterRecipes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes the MineColonies Smeltery's two configurable item lists offer <b>TFC</b> candidates (see
 * docs/tfc-furnace-workers.md §8). The list GUIs pull their toggleable items from
 * {@link CompatibilityManager#getSmeltableOres()} (the "ores" block-list) and {@link CompatibilityManager#getFuel()}
 * (the "fuel" allow-list); vanilla populates those from items with a <i>vanilla furnace recipe</i> / vanilla
 * burn time, which excludes every TFC ore (TFC ore is <i>melted</i>, not smelted) and ignores TFC's fuel model.
 *
 * <p>We replace both sets outright so the player picks from <b>TFC ores / TFC fuels only</b> — matching how our
 * {@code SmelterBehavior} actually decides what to melt and burn. These getters feed only smeltery-aligned
 * consumers (the two list GUIs, the "needs smeltable ore" interaction validator, and the base ore request), so
 * the swap keeps all three consistent with the TFC pipeline.
 */
@Mixin(CompatibilityManager.class)
public class MixinCompatibilityManager
{
    /**
     * Mark the food MineColonies discovers as <b>non-decaying</b>. {@code discoverFood} stores each food as a template
     * {@link ItemStorage} in the {@code food}/{@code edibles} lists (which feed the restaurant-menu picker, the menu,
     * and food requests). TFC stamps a creation date on those template stacks, so over time they render as
     * decaying/rotten — see {@link FoodTemplates} for why we use the persistent never-decay sentinel here.
     */
    @ModifyVariable(method = "discoverFood", at = @At("HEAD"), argsOnly = true, remap = false)
    private ItemStack mctfc$nonDecayingFood(final ItemStack stack)
    {
        return FoodTemplates.nonDecaying(stack);
    }

    /**
     * Restrict the discovered <b>edible</b> food to <b>TFC-tracked</b> food (those with the TFC food capability). This
     * is the candidate source for the restaurant-menu dish picker, so non-TFC vanilla/modded foods — which don't
     * participate in TFC's nutrition/decay and so don't belong on a TFC colony's menu — are dropped from it. (Also the
     * netherminer's edible-food list, which in a TFC world should likewise be TFC food.)
     */
    @Inject(method = "getEdibles", at = @At("RETURN"), remap = false)
    private void mctfc$tfcEdiblesOnly(final int minNutrition, final CallbackInfoReturnable<Set<ItemStorage>> cir)
    {
        cir.getReturnValue().removeIf(storage -> !FoodTemplates.isTfcFood(storage.getItemStack()));
    }

    @Inject(method = "getSmeltableOres", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$tfcSmeltableOres(final CallbackInfoReturnable<Set<ItemStorage>> cir)
    {
        final Set<ItemStorage> ores = new LinkedHashSet<>();
        for (final ItemStack ore : SmelterRecipes.oreStacks())
        {
            ores.add(new ItemStorage(ore));
        }
        cir.setReturnValue(ores);
    }

    @Inject(method = "getFuel", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$tfcFuel(final CallbackInfoReturnable<Set<ItemStorage>> cir)
    {
        final Set<ItemStorage> fuels = new LinkedHashSet<>();
        for (final Item item : ForgeRegistries.ITEMS)
        {
            final ItemStack stack = new ItemStack(item);
            if (FurnaceFuel.isFuel(stack))
            {
                fuels.add(new ItemStorage(stack));
            }
        }
        cir.setReturnValue(fuels);
    }
}
