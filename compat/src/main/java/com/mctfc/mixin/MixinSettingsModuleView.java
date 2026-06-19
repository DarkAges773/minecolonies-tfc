package com.mctfc.mixin;

import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hides herder-hut settings that are inert in a TFC world from the building GUI:
 * <ul>
 *   <li><b>{@code stewing_amount}</b> (Cowhand) — mooshroom stew needs a {@code MushroomCow}, which TFC never spawns;
 *       the stew state is also suppressed in {@link MixinEntityAIWorkCowboy}.</li>
 *   <li><b>{@code dyeing}</b> (Shepherd) — auto-dyeing only runs on vanilla {@code Sheep} (which TFC replaces), and our
 *       TFC shear path ({@link MixinEntityAIWorkShepherd}) bypasses the vanilla shear+dye body entirely.</li>
 * </ul>
 * Both settings stay registered (and serialize) so save-compat is untouched; we only drop their rows from the GUI's
 * {@code getSettingsToShow()} list (which drives both the row count and the rows in {@code SettingsModuleWindow}). The
 * keys are unique to those two buildings, so matching by path id needs no per-building check.
 *
 * <p>{@code remap = false} — MineColonies' own (client) class.
 */
@Mixin(value = SettingsModuleView.class, remap = false)
public class MixinSettingsModuleView
{
    @Inject(method = "getSettingsToShow", at = @At("RETURN"))
    private void mctfc$hideInertTfcSettings(final CallbackInfoReturnable<List<ISettingKey<? extends ISetting<?>>>> cir)
    {
        cir.getReturnValue().removeIf(key -> {
            final String path = key.getUniqueId().getPath();
            return path.equals("stewing_amount") || path.equals("dyeing");
        });
    }
}
