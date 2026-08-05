package com.mctfc.mixin;

import com.mctfc.cook.PotRecipeBridge;
import com.mctfc.crafting.LumberjackRecipes;
import com.mctfc.smithing.AnvilRecipeBridge;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Post-load tweaks to the worker recipe map: inject the TFC anvil/welding recipes for the blacksmith (see
 * {@link AnvilRecipeBridge}), the static TFC pot food recipes for the chef (see {@link PotRecipeBridge}), and strip
 * MineColonies' vanilla-log lumberjack recipes (see {@link LumberjackRecipes}).
 *
 * <p>{@code resolveTemplates()} is MineColonies' own post-load hook: {@code DataPackSyncEventHandler} calls it
 * once per datapack sync — after {@code CrafterRecipeListener} reset + reloaded the recipe map (an earlier
 * injection would be wiped), with the server's {@code RecipeManager} loaded and tags bound, and right before
 * the recipe map is synced to clients. The TAIL here is therefore the one deterministic seam where programmatic
 * recipe edits survive and reach clients, regardless of reload-listener registration order — and it runs after
 * both the vanilla and our TFC {@code recipe-template}s have expanded, so the removal sees the resolved children.
 *
 * <p>{@code remap = false} — MineColonies' own class and method.
 */
@Mixin(value = CustomRecipeManager.class, remap = false)
public class MixinCustomRecipeManager
{
    @Inject(method = "resolveTemplates", at = @At("TAIL"))
    private void mctfc$postProcessRecipes(final CallbackInfo ci)
    {
        AnvilRecipeBridge.injectAll();
        PotRecipeBridge.injectAll();
        LumberjackRecipes.removeVanillaDefaults();
    }
}
