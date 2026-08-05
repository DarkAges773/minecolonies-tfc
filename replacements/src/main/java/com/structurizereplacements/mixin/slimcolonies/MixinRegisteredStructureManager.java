package com.structurizereplacements.mixin.slimcolonies;

import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.api.colony.buildings.IBuilding;
import no.monopixel.slimcolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import no.monopixel.slimcolonies.core.colony.managers.RegisteredStructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * SlimColonies twin of the MineColonies {@code MixinRegisteredStructureManager}. Adopts the placing
 * player's staged replacement choices onto a building the moment it is created ({@code addNewBuilding},
 * when the hut block registers with the colony), rather than waiting for the first build — so the Build
 * Options window reflects them right away (and they survive a restart before the first build).
 *
 * <p>{@code remap = false}: the colony mod's own method. Server-side (building creation).
 */
@Mixin(RegisteredStructureManager.class)
public class MixinRegisteredStructureManager
{
    @Inject(method = "addNewBuilding", at = @At("RETURN"), remap = false)
    private void structurizereplacements$adoptStagedChoices(final AbstractTileEntityColonyBuilding tileEntity, final Level level,
                                                            final CallbackInfoReturnable<IBuilding> cir)
    {
        final IBuilding building = cir.getReturnValue();
        if (!(building instanceof PlacementChoiceHolder holder))
        {
            return;
        }
        final Map<Block, Block> staged = StagedChoices.take(level.dimension(), building.getPosition());
        if (staged != null && !staged.isEmpty())
        {
            holder.setReplacementChoices(staged);
            building.markDirty();
        }
    }
}
