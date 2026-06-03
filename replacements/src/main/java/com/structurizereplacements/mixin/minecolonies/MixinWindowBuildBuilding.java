package com.structurizereplacements.mixin.minecolonies;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.View;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.structurizereplacements.client.gui.WindowReplacements;
import com.structurizereplacements.integration.minecolonies.BuildingChoiceContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Client-only: adds a "Replace" button to MineColonies' Build Options window ({@code WindowBuildBuilding})
 * that opens the candidate-block picker scoped to <i>this building</i> (a {@link BuildingChoiceContext}).
 * Picks persist on the building (via the edit packet) and the material list/preview refresh.
 *
 * <p>Injected at the constructor TAIL (the window has a single ctor and builds its panes from XML in
 * {@code super(...)}, so children exist by TAIL). {@code remap = false}: {@code building}/
 * {@code updateResources} are MineColonies' own members.
 */
@Mixin(WindowBuildBuilding.class)
public abstract class MixinWindowBuildBuilding
{
    @Shadow(remap = false)
    private void updateResources() {}

    @Shadow(remap = false)
    private IBuildingView building;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void structurizereplacements$addReplaceButton(final CallbackInfo ci)
    {
        final View window = (View) (Object) this;
        final int width = 60;
        final int height = 17;
        final ButtonImage button = new ButtonImage();
        button.setID("structurizereplacements:replace");
        button.setSize(width, height);
        // Bottom-left corner, relative to the window height (robust across layouts).
        button.setPosition(5, window.getHeight() - height - 5);
        // Match the surrounding Build Options buttons: same MineColonies button texture, and the same
        // toned-down text styling (their XML uses color="black", which BlockUI applies to normal AND hover
        // alike — the texture brightens on hover, the text color stays put).
        button.setImage(new ResourceLocation("minecolonies", "textures/gui/builderhut/builder_button_medium_large.png"), false);
        button.setText(List.of(Component.translatable("com.ldtteam.structurize.gui.scantool.replace")));
        button.setTextColor(0x000000);
        button.setTextHoverColor(0x000000);
        button.setTextRenderBox(width, 17);
        button.setTextAlignment(Alignment.MIDDLE);
        button.setHandler(b -> new WindowReplacements(
                new BuildingChoiceContext(this.building, this::updateResources), (BOWindow) (Object) this).open());
        window.addChild(button);
    }
}
