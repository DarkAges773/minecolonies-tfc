package com.mctfc.mixin;

import com.mctfc.cook.CookRecipes;
import com.mctfc.food.FoodTemplates;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.compatibility.IFurnaceRecipes;
import com.minecolonies.api.inventory.container.ContainerCraftingFurnace;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Teaches the MineColonies <b>Chef</b>'s furnace (smelting) recipe tab to use <b>TFC heating recipes</b> instead of
 * vanilla furnace recipes. The furnace teach window ({@code WindowFurnaceCrafting}) fills its output slot from
 * {@code ContainerCraftingFurnace#updateFurnaceOutput}, which looks the result up via vanilla
 * {@code FurnaceRecipes#getSmeltingResult} — <b>empty for TFC food</b> (TFC has no vanilla furnace recipes), so the
 * output slot never fills and the Done button's non-empty-output guard blocks the teach. You literally cannot teach a
 * TFC cook recipe without this.
 *
 * <p>We redirect that lookup <b>for the Kitchen only</b> (other furnace crafters keep vanilla) to TFC's item-output
 * heating recipe ({@link CookRecipes#cookedResult}, raw food → cooked food), stamped
 * {@link FoodTemplates#nonDecaying} so the recipe-list GUI never renders the taught template spoiled. The result is
 * <b>TFC-only</b>: an input with no TFC heating recipe yields an empty output slot (nothing to teach), matching the
 * design decision to drop the vanilla fallback on the Chef. The taught recipe (gridSize 1 → intermediate
 * {@code FURNACE}) is then driven with TFC heating by {@code MixinAbstractEntityAIRequestSmelter}.
 *
 * <p>{@code remap = false} — MineColonies' own class/method.
 */
@Mixin(value = ContainerCraftingFurnace.class, remap = false)
public abstract class MixinContainerCraftingFurnace
{
    @Redirect(
        method = "updateFurnaceOutput",
        at = @At(value = "INVOKE",
            target = "Lcom/minecolonies/api/compatibility/IFurnaceRecipes;getSmeltingResult(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"),
        remap = false)
    private ItemStack mctfc$tfcHeatingOutput(final IFurnaceRecipes recipes, final ItemStack input)
    {
        final ContainerCraftingFurnace self = (ContainerCraftingFurnace) (Object) this;
        final Level level = self.getWorldObj();
        final BlockPos pos = self.getPos();
        if (level == null || pos == null || !isKitchen(level, pos))
        {
            return recipes.getSmeltingResult(input); // non-Kitchen furnace crafters keep vanilla smelting
        }
        // Chef is TFC-only: the taught output is the TFC heating result (or nothing when the input isn't cookable).
        if (!CookRecipes.isCookable(input))
        {
            return ItemStack.EMPTY;
        }
        return FoodTemplates.nonDecaying(CookRecipes.cookedResult(input, level.registryAccess()));
    }

    private static boolean isKitchen(final Level level, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return false;
        }
        final IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        return building instanceof BuildingKitchen;
    }
}
