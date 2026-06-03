package com.structurizereplacements.mixin;

import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.View;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.structurizereplacements.client.gui.ButtonImageWithIcon;
import com.structurizereplacements.client.gui.WindowReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only: adds a "Replace" button to Structurize's blueprint placement window. Clicking it opens
 * the candidate-block picker ({@link WindowReplacements}).
 *
 * <p>Same 16×16 icon button as the MineColonies Build Options one: a {@link ButtonImageWithIcon} that
 * draws a light mini-button frame and overlays our {@code replace_button.png} icon at full source res. The
 * frame here is Structurize's own {@code builder_button_mini} (NOT MineColonies' — this is the core,
 * MineColonies-optional mixin, so it must not reference MineColonies textures). The label is dropped in
 * favour of a "Replace" tooltip; hover keeps the default brighten (its existing hover behaviour).
 *
 * <p>Targets the abstract base (which defines {@code onOpened}); the button thus also appears on the
 * shape tool etc. — acceptable for now. {@code remap = false}: {@code onOpened} is a BlockUI/Structurize
 * method, not a Minecraft override.
 */
@Mixin(AbstractBlueprintManipulationWindow.class)
public class MixinAbstractBlueprintManipulationWindow
{
    @Inject(method = "onOpened", at = @At("TAIL"), remap = false)
    private void structurizereplacements$addReplaceButton(final CallbackInfo ci)
    {
        final View window = (View) (Object) this;
        final int size = 16;
        // 2px frame visible on each side; the 32×32 icon fills the inner 12×12, overlaid at draw time.
        final ButtonImageWithIcon button = new ButtonImageWithIcon(
                new ResourceLocation("structurizereplacements", "textures/gui/replace_button.png"), 2);
        button.setID("structurizereplacements:open");
        button.setSize(size, size);
        // Top-right corner, relative to the window width (robust across the different windows).
        button.setPosition(window.getWidth() - size - 5, 5);
        // Structurize's own mini-button frame (MC-independent) as the light background; icon overlaid on it.
        button.setImage(new ResourceLocation("structurize", "textures/gui/builderhut/builder_button_mini.png"), false);
        button.setHandler(b -> new WindowReplacements((BOWindow) (Object) this).open());
        window.addChild(button);
        // No label now, so a tooltip names it; reuse Structurize's own "Replace" translation. Built AFTER
        // the button is attached — it resolves the hover pane's parent window.
        PaneBuilders.singleLineTooltip(
                Component.translatable("com.ldtteam.structurize.gui.scantool.replace"), button);
    }
}
