package com.structurizereplacements.mixin.minecolonies;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.modules.building.SettingsModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.structurizereplacements.integration.colony.MineshaftSettingsListProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only: adds an "Edit Mineshaft Palette" <b>row</b> to the bottom of MineColonies' generic settings
 * list ({@code SettingsModuleWindow}) — but only for the <b>miner</b> hut. The row opens the candidate-block
 * picker scoped to the miner's separate mineshaft palette, letting the player choose the materials of the
 * underground shafts/tunnels the miner builds (the {@code infrastructure/mineshafts/*} schematics)
 * independently of the hut building.
 *
 * <p>Rather than fight the data-driven settings list, this wraps the {@link ScrollingList.DataProvider} the
 * window installs in {@code updateSettingsList}: {@code @ModifyArg} swaps the provider for a
 * {@link MineshaftSettingsListProvider} that delegates the real settings rows to MineColonies and appends our
 * one extra row. The constructor inject only captures the {@code moduleView} (the {@code @ModifyArg} handler
 * needs the building to scope the picker and check it's the miner). {@code remap = false}: the wrapped members
 * ({@code updateSettingsList}, blockui's {@code setDataProvider}) are not Minecraft methods.
 *
 * <p>Both injectors use {@code require = 0}: this is a cosmetic, optional enhancement, so if a future
 * MineColonies version refactors {@code updateSettingsList} (or the constructor) out from under us, the
 * mixin simply doesn't add the row rather than failing to apply — the settings window and the rest of the
 * integration keep working. (With the handler missing, {@code structurizereplacements$moduleView} stays
 * null and the {@code @ModifyArg} returns the provider unchanged.)
 */
@Mixin(SettingsModuleWindow.class)
public abstract class MixinSettingsModuleWindow
{
    @Unique
    private SettingsModuleView structurizereplacements$moduleView;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void structurizereplacements$captureModuleView(final SettingsModuleView moduleView, final CallbackInfo ci)
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
