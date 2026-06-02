package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.substitution.BlockSubstitutions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies datapack-driven block substitution at the single point where Structurize turns a
 * blueprint's stored {@link BlockInfo} into a placed block.
 *
 * <p>{@code handleBlockPlacement(Level, BlockPos, ChangeStorage, BlockInfo)} reads the state via
 * {@code blockInfo.getState()} immediately, so rewriting the {@link BlockInfo} argument at HEAD
 * feeds the rest of the method (handler selection + placement) our substituted state, fully
 * non-destructively. {@link BlockSubstitutions#apply} returns the argument unchanged when
 * substitution is disabled or no rule matches.
 */
@Mixin(StructurePlacer.class)
public class MixinStructurePlacer
{
    // remap = false: handleBlockPlacement is Structurize's own (non-Minecraft) method, so its name
    // is stable across dev/production and must NOT be remapped to an SRG name.
    @ModifyVariable(method = "handleBlockPlacement", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockInfo structurizereplacements$substituteBlueprintBlock(final BlockInfo blockInfo)
    {
        return BlockSubstitutions.apply(blockInfo);
    }
}
