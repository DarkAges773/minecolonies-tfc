package com.mctfc.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.mctfc.settings.BuildingStockSeeds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Seeds default minimum-stock entries (see {@link BuildingStockSeeds}) onto a building the first time it's built.
 * We hook {@code onUpgradeComplete} at <b>level 1</b> rather than {@code onPlacement}: at placement the building
 * is still level 0, so {@code MinimumStockModule#addMinimumStock} rejects entries (its size cap is
 * {@code level × STOCK_PER_LEVEL == 0}). Gating on {@code newLevel == 1} also makes it a true one-time default —
 * not re-applied on later upgrades, and a player's removal sticks.
 *
 * <p>{@code remap = false}: {@code onUpgradeComplete} is MineColonies' own method. Runs server-side.
 */
@Mixin(AbstractBuilding.class)
public class MixinAbstractBuilding
{
    @Inject(method = "onUpgradeComplete", at = @At("TAIL"), remap = false)
    private void mctfc$seedDefaultStock(@Nullable final Blueprint blueprint, final int newLevel, final CallbackInfo ci)
    {
        if (newLevel == 1)
        {
            BuildingStockSeeds.seed((IBuilding) this);
        }
    }
}
