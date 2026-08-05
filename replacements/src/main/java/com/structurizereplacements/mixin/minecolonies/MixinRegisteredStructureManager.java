package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.colony.managers.RegisteredStructureManager;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Adopts the placing player's staged replacement choices onto a building the moment it is created
 * ({@code addNewBuilding}, when the hut block registers with the colony), rather than waiting for the
 * first build. This means the building has its choices — persisted to colony NBT and synced to the client
 * view — immediately after placement, so the Build Options window reflects them right away (and they
 * survive a restart before the first build).
 *
 * <p>{@code remap = false}: MineColonies' own method. Server-side (building creation).
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
