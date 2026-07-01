package com.mctfc.mixin;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.View;
import com.mctfc.cook.DishType;
import com.mctfc.network.ComposeDishNetwork;
import com.minecolonies.core.client.gui.modules.building.WindowListRecipes;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only: adds a <b>"Compose TFC Dish"</b> button to the Kitchen Chef's crafting recipe-list tab
 * ({@code WindowListRecipes}, opened by {@code CraftingModuleView#getWindow}). The button fires an
 * {@code OpenComposeDishMessage} which opens our native {@code ComposeDishMenu}/{@code ComposeDishScreen} (a container
 * screen, like MineColonies' own teach GUIs), where the player composes a TFC salad/soup and teaches it to the Chef —
 * surfacing the dynamic-dish feature without grafting a new building module (see
 * <a href="../../../../../../../docs/tfc-chef-dishes.md">tfc-chef-dishes.md</a> §5).
 *
 * <p>{@code WindowListRecipes} is shared by every crafting building, so we gate on the module producer's key
 * ({@code "chef_craft"}) — unique to the Kitchen's crafting module — to scope the button to that one tab (not the
 * Chef's smelting tab, not any other worker). The constructor's {@code CraftingModuleView} argument is read straight
 * off the injected handler (no {@code @Shadow} of inherited window fields). {@code remap = false}: MineColonies' own
 * class.
 */
@Mixin(value = WindowListRecipes.class, remap = false)
public abstract class MixinWindowListRecipes
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mctfc$addComposeButton(final CraftingModuleView module, final CallbackInfo ci)
    {
        if (module.getProducer() == null || !"chef_craft".equals(module.getProducer().key))
        {
            return;
        }

        final ButtonImage button = new ButtonImage();
        button.setID("mctfc:compose-dish");
        button.setImage(new ResourceLocation("minecolonies", "textures/gui/builderhut/builder_button_medium_large.png"), false);
        button.setSize(129, 17);
        button.setPosition(30, 220);
        // ButtonImage from the no-arg ctor leaves the text-render box at 0 (label never draws) and text white
        // (invisible on the light button) — match the window's black XML buttons. See MixinWindowField.
        button.setTextRenderBox(129, 17);
        button.setColors(0x000000);
        button.setTextAlignment(Alignment.MIDDLE);
        button.setText(Component.translatable("mctfc.gui.compose.open"));
        // Open our native container screen (server launches it) in salad mode; the screen can switch to soup.
        button.setHandler(b -> ComposeDishNetwork.sendOpen(
            module.getBuildingView().getID(), module.getProducer().getRuntimeID(), DishType.SALAD));

        ((View) (Object) this).addChild(button);
        PaneBuilders.singleLineTooltip(Component.translatable("mctfc.gui.compose.open.tooltip"), button);
    }
}
