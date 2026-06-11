package com.mctfc.mixin;

import com.mctfc.smithing.AnvilRecipeBridge;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the TFC anvil/welding recipes into the blacksmith's recipe list (see {@link AnvilRecipeBridge}).
 *
 * <p>{@code resolveTemplates()} is MineColonies' own post-load hook: {@code DataPackSyncEventHandler} calls it
 * once per datapack sync — after {@code CrafterRecipeListener} reset + reloaded the recipe map (an earlier
 * injection would be wiped), with the server's {@code RecipeManager} loaded and tags bound, and right before
 * the recipe map is synced to clients. The TAIL here is therefore the one deterministic seam where programmatic
 * recipes survive and reach clients, regardless of reload-listener registration order.
 *
 * <p>{@code remap = false} — MineColonies' own class and method.
 */
@Mixin(value = CustomRecipeManager.class, remap = false)
public class MixinCustomRecipeManager
{
    @Inject(method = "resolveTemplates", at = @At("TAIL"))
    private void mctfc$injectAnvilRecipes(final CallbackInfo ci)
    {
        AnvilRecipeBridge.injectAll();
    }
}
