package com.structurizereplacements.mixin.slimcolonies;

import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.View;
import com.structurizereplacements.client.gui.ButtonImageWithIcon;
import com.structurizereplacements.client.gui.WindowReplacements;
import com.structurizereplacements.integration.slimcolonies.BuildingChoiceContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import no.monopixel.slimcolonies.api.colony.buildings.views.IBuildingView;
import no.monopixel.slimcolonies.core.client.gui.WindowBuildBuilding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * SlimColonies twin of the MineColonies {@code MixinWindowBuildBuilding} — see that class for the full
 * design rationale (button placement, ctor-TAIL injection, defensive level-path surgery). Client-only:
 * adds a "Replace" button to the Build Options window ({@code WindowBuildBuilding}) that opens the
 * candidate-block picker scoped to <i>this building</i> (a {@link BuildingChoiceContext}). The mini-button
 * frame texture comes from the {@code slimcolonies} asset namespace.
 *
 * <p>{@code remap = false}: {@code building}/{@code updateResources} are the colony mod's own members.
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
    // SlimColonies update changes the ctor so the injector no longer applies, degrade to a missing button —
    // never fail the whole required:false config and take the other SlimColonies-integration mixins down.
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void structurizereplacements$addReplaceButton(final CallbackInfo ci)
    {
        final View window = (View) (Object) this;
        final int size = 16;
        // 2px frame visible on each side; the 32×32 icon fills the inner 12×12, overlaid at draw time.
        final ButtonImageWithIcon button = new ButtonImageWithIcon(
                new ResourceLocation("structurizereplacements", "textures/gui/replace_button.png"), 2);
        button.setID("structurizereplacements:replace");
        button.setSize(size, size);
        // Empty top-left corner, left of the style "<" arrow (which begins at x=40).
        button.setPosition(4, 1);
        // The fork's own mini-button frame as the (light) background; the icon is overlaid over it.
        button.setImage(new ResourceLocation("slimcolonies", "textures/gui/builderhut/builder_button_mini.png"), false);
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
     * next-level path {@code WindowBuildBuilding#updateResources} loads for its material list. Defensive:
     * only rewrites the level when the path actually ends with the current level number; otherwise falls
     * back to the current path (an honest current-tier degrade beats a corrupted path that loads nothing).
     */
    private String targetStructurePath()
    {
        final String current = this.building.getStructurePath();
        if (current == null || current.isEmpty() || !canBeUpgraded())
        {
            return current;
        }
        final int currentLevel = this.building.getBuildingLevel();
        final String currentSuffix = Integer.toString(currentLevel);
        final String base = current.replace(".blueprint", "");
        if (!base.endsWith(currentSuffix))
        {
            // Naming convention not as expected — don't risk constructing a path that loads nothing.
            return current;
        }
        return base.substring(0, base.length() - currentSuffix.length()) + (currentLevel + 1) + ".blueprint";
    }
}
