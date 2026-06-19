package com.mctfc.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.mctfc.herding.TfcHerd;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.mctfc.settings.BuildingStockSeeds;
import net.minecraft.network.chat.Component;
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

    /**
     * Keep the "more than one species in this pen" worker warning ({@link TfcHerd#MIXED_SPECIES}) alive from the
     * building's slow colony tick rather than the worker's AI loop — a herd with nothing to do parks the worker in
     * an idle state that stops ticking the job, so an AI-driven trigger would never re-surface after the player
     * dismisses it. Re-triggering here (de-duped by key, auto-cleared by the validator once the pen is one species)
     * makes the warning persistent. {@link TfcHerd#penSpeciesCount} is a no-op for non-herding buildings (no
     * {@code AnimalHerdingModule}), so this is cheap everywhere else. Server-side; {@code remap = false}.
     */
    @Inject(method = "onColonyTick", at = @At("TAIL"), remap = false)
    private void mctfc$herderPenWarning(final IColony colony, final CallbackInfo ci)
    {
        final IBuilding self = (IBuilding) this;
        if (TfcHerd.penSpeciesCount(self) > 1)
        {
            for (final ICitizenData citizen : self.getAllAssignedCitizen())
            {
                citizen.triggerInteraction(new StandardInteraction(Component.translatable(TfcHerd.MIXED_SPECIES), ChatPriority.BLOCKING));
            }
        }
    }
}
