package com.mctfc.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.mctfc.settings.BuildingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Generic hook for the {@link BuildingSettings} registry: when a building module learns its building, if it's a
 * {@link SettingsModule} we graft on every setting registered for that building type. {@code setBuilding} is the
 * clean once-per-module seam (called from {@code BuildingEntry} at build creation, before any serialize/sync), so
 * our settings are present in time for persistence, client sync and the GUI. One mixin serves all current and
 * future custom settings — adding one is a {@code BuildingSettings.register(...)} call, never more mixin code.
 *
 * <p>{@code remap = false}: {@code setBuilding} is MineColonies' own method. Runs server-side only (client uses
 * the separate module-<i>view</i> hierarchy and receives our settings via the standard sync).
 */
@Mixin(AbstractBuildingModule.class)
public class MixinAbstractBuildingModule
{
    @Inject(method = "setBuilding", at = @At("TAIL"), remap = false)
    private void mctfc$attachCustomSettings(final IBuilding building, final CallbackInfoReturnable<IBuildingModule> cir)
    {
        if ((Object) this instanceof SettingsModule module)
        {
            BuildingSettings.attach(module, building);
        }
    }
}
