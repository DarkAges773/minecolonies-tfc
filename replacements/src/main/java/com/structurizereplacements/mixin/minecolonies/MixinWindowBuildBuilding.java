package com.structurizereplacements.mixin.minecolonies;

import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.View;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.structurizereplacements.client.gui.ButtonImageWithIcon;
import com.structurizereplacements.client.gui.WindowReplacements;
import com.structurizereplacements.integration.colony.BuildingChoiceContext;
import com.structurizereplacements.integration.colony.LevelPaths;
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
 * <p>The window (420×240) is full — top row (style nav / builder dropdown / cancel), the resource list,
 * and the bottom action row (Repair/Build/Deconstruct) leave no room for a labeled button. So this is a
 * small 16×16 icon button in the empty top-left corner (left of the style {@code <} arrow at x=40): a
 * {@link ButtonImageWithIcon} that draws MineColonies' own {@code builder_button_mini} frame and overlays
 * our dark {@code replace_button.png} icon (at full 32×32 source res, so it reads on the light GUI and
 * stays crisp at high GUI scale) with a "Replace" tooltip; it brightens on hover like MC's mini buttons.
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

    @Shadow(remap = false)
    private DropDownList stylesDropDownList;

    @Shadow(remap = false)
    private List<String> styles;

    @Shadow(remap = false)
    public abstract boolean canBeUpgraded();

    // require = 0: this is a cosmetic, optional add-on button (parity with MixinSettingsModuleWindow). If a
    // MineColonies update changes the ctor so the injector no longer applies, degrade to a missing button —
    // never fail the whole required:false config and take the other MineColonies-integration mixins down.
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void structurizereplacements$addReplaceButton(final CallbackInfo ci)
    {
        final View window = (View) (Object) this;
        final int size = 16;
        // 2px frame visible on each side; the 32×32 icon fills the inner 12×12, overlaid at draw time.
        final ButtonImageWithIcon button = new ButtonImageWithIcon(
                ResourceLocation.fromNamespaceAndPath("structurizereplacements", "textures/gui/replace_button.png"), 2);
        button.setID("structurizereplacements:replace");
        button.setSize(size, size);
        // Empty top-left corner, left of the style "<" arrow (which begins at x=40).
        button.setPosition(4, 1);
        // MineColonies' own mini-button frame as the (light) background; the icon is overlaid over it.
        button.setImage(ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/builderhut/builder_button_mini.png"));
        button.setHandler(b -> new WindowReplacements(
                new BuildingChoiceContext(this.building, targetStructurePack(), targetStructurePath(), this::updateResources),
                (BOWindow) (Object) this).open());
        window.addChild(button);
        // Tooltip must be built AFTER the button is attached — it resolves the hover pane's parent window.
        PaneBuilders.singleLineTooltip(
                Component.translatable("com.ldtteam.structurize.gui.scantool.replace"), button);
    }

    /**
     * The structure pack of the blueprint that will actually be built — the style currently selected in the
     * window's dropdown (mirrors {@code updateResources}), falling back to the building's own pack if the
     * dropdown isn't populated yet.
     */
    private String targetStructurePack()
    {
        final int idx = stylesDropDownList == null ? -1 : stylesDropDownList.getSelectedIndex();
        if (styles != null && idx >= 0 && idx < styles.size())
        {
            return styles.get(idx);
        }
        return this.building.getStructurePack();
    }

    /**
     * The blueprint path of the <i>target</i> level (current + 1 when upgradable, else current) — the same
     * next-level path {@code WindowBuildBuilding#updateResources} loads for its material list, so the picker
     * lists the blocks about to be built rather than the current tier's. The defensive path surgery is
     * shared ({@link LevelPaths#targetLevelPath}).
     */
    private String targetStructurePath()
    {
        return LevelPaths.targetLevelPath(
                this.building.getStructurePath(), this.building.getBuildingLevel(), canBeUpgraded());
    }
}
