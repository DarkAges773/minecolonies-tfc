package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Applies block substitution where Structurize turns a blueprint into placed blocks and requested
 * materials. The per-placement choice map lives on the structure handler (see
 * {@link MixinAbstractStructureHandler}); here we read it off {@code this.getHandler()} so the same
 * choices drive placement and material requests (and {@link MixinAbstractBlueprintIterator} drives the
 * match). For the build tool the handler carries the player's GUI picks; for the builder it carries the
 * building's choices (or null → datapack rules only).
 *
 * <p>{@code remap = false}: these are Structurize's own methods.
 */
@Mixin(StructurePlacer.class)
public class MixinStructurePlacer
{
    @ModifyVariable(method = "handleBlockPlacement", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockInfo structurizereplacements$substituteBlueprintBlock(final BlockInfo blockInfo)
    {
        return BlockSubstitutions.apply(blockInfo, structurizereplacements$choices());
    }

    /**
     * The builder/quarrier compute what materials to request through {@code getResourceRequirements};
     * substitute the blueprint state here too so they request the block that will actually be placed.
     */
    @ModifyVariable(method = "getResourceRequirements", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockState structurizereplacements$substituteRequirement(final BlockState state)
    {
        return BlockSubstitutions.applyState(state, structurizereplacements$choices());
    }

    @Unique
    private Map<Block, Block> structurizereplacements$choices()
    {
        final IStructureHandler handler = ((StructurePlacer) (Object) this).getHandler();
        return (handler instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
    }
}
