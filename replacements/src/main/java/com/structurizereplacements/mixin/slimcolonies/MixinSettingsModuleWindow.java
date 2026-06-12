package com.structurizereplacements.mixin.slimcolonies;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.structurizereplacements.integration.colony.MineshaftSettingsListProvider;
import no.monopixel.slimcolonies.api.colony.buildings.ModBuildings;
import no.monopixel.slimcolonies.api.colony.buildings.views.IBuildingView;
import no.monopixel.slimcolonies.core.client.gui.modules.SettingsModuleWindow;
import no.monopixel.slimcolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SlimColonies twin of the MineColonies {@code MixinSettingsModuleWindow} — see that class for the full
 * design rationale (provider wrapping, row recycling, {@code require = 0} degradation). Client-only: adds
 * an "Edit Mineshaft Palette" row to the bottom of the settings list — but only for the <b>miner</b> hut.
 *
 * <p>Fork deltas: {@code SettingsModuleWindow} lives in {@code core.client.gui.modules} (no
 * {@code .building} subpackage), and its constructor takes {@code (String, IBuildingView,
 * SettingsModuleView)} — the capture handler mirrors that full argument list (a ctor inject must capture
 * either all target args or none).
 */
@Mixin(SettingsModuleWindow.class)
public abstract class MixinSettingsModuleWindow
{
    @Unique
    private SettingsModuleView structurizereplacements$moduleView;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void structurizereplacements$captureModuleView(final String res, final IBuildingView buildingView,
                                                           final SettingsModuleView moduleView, final CallbackInfo ci)
    {
        this.structurizereplacements$moduleView = moduleView;
    }

    @ModifyArg(
            method = "updateSettingsList",
            at = @At(value = "INVOKE",
                    target = "Lcom/ldtteam/blockui/views/ScrollingList;setDataProvider(Lcom/ldtteam/blockui/views/ScrollingList$DataProvider;)V"),
            remap = false,
            require = 0)
    private ScrollingList.DataProvider structurizereplacements$appendMineshaftRow(final ScrollingList.DataProvider provider)
    {
        final SettingsModuleView moduleView = this.structurizereplacements$moduleView;
        if (moduleView == null)
        {
            return provider;
        }
        final IBuildingView buildingView = moduleView.getBuildingView();
        if (buildingView == null || buildingView.getBuildingType() != ModBuildings.miner.get())
        {
            return provider;
        }
        return new MineshaftSettingsListProvider(provider, buildingView, (BOWindow) (Object) this);
    }
}
