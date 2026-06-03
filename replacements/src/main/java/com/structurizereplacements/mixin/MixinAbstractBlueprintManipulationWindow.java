package com.structurizereplacements.mixin;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.View;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.structurizereplacements.client.gui.WindowReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-only: adds a "Replace" button to Structurize's blueprint placement window. Clicking it opens
 * the candidate-block picker ({@link ReplacementPicker}).
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
        final int width = 60;
        final ButtonImage button = new ButtonImage();
        button.setID("structurizereplacements:open");
        button.setSize(width, 17);
        // Top-right corner, relative to the window width (robust across the different windows).
        button.setPosition(window.getWidth() - width - 5, 5);
        button.setImage(new ResourceLocation("structurize", "textures/gui/buildtool/button_medium.png"), false);
        // Reuse Structurize's own translation for "Replace" so the button is localized everywhere.
        button.setText(List.of(Component.translatable("com.ldtteam.structurize.gui.scantool.replace")));
        // Match Structurize's buttons: black text, gray on hover (default hover is white).
        button.setTextColor(0x000000);
        button.setTextHoverColor(0x808080);
        // Programmatic buttons have no text render box (XML buttons derive it from their size), so the
        // label wouldn't draw without this.
        button.setTextRenderBox(width, 17);
        button.setTextAlignment(Alignment.MIDDLE);
        button.setHandler(b -> new WindowReplacements((BOWindow) (Object) this).open());
        window.addChild(button);
    }
}
