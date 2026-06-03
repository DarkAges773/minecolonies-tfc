package com.structurizereplacements.mixin;

import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.PlacementChoices;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Attaches the placing player's replacement choices to the {@link StructurePlacer} when a build-tool
 * placement operation is created. This is the link that lets {@code StructurePlacer#handleBlockPlacement}
 * (which has no player of its own) reach the per-placement choices — the operation holds both.
 *
 * <p>{@code remap = false}: {@code PlaceStructureOperation} and its constructor are Structurize's own.
 */
@Mixin(PlaceStructureOperation.class)
public class MixinPlaceStructureOperation
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void structurizereplacements$attachChoices(final StructurePlacer placer, final Player player, final CallbackInfo ci)
    {
        // Choices live on the structure handler (shared by placer + iterator). The mixin adds
        // PlacementChoiceHolder to AbstractStructureHandler at runtime; cast via Object.
        final IStructureHandler handler = placer.getHandler();
        if (handler instanceof PlacementChoiceHolder holder)
        {
            holder.setReplacementChoices(PlacementChoices.forPlayer(player));
        }
    }
}
