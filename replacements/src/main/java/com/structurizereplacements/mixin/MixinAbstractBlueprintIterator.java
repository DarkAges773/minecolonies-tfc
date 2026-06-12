package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.AbstractBlueprintIterator;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.SubstitutedMatch;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the builder's "is this position already built correctly?" scan agree with substitution.
 *
 * <p>{@code AbstractBlueprintIterator#iterateWithCondition} calls the static
 * {@code IPlacementHandler.doesWorldStateMatchBlueprintState(BlockInfo, ...)} to skip positions whose
 * world block already matches the blueprint. After we place a substituted block (e.g. spruce for an
 * oak-planks blueprint), that check would see world=spruce vs blueprint=oak and treat the spot as
 * unbuilt — making the builder re-place or never finish. We redirect the call through
 * {@link SubstitutedMatch} (shared with {@code MixinStructurePlacer}'s request-scan match), which
 * substitutes the blueprint {@link BlockInfo} first so the match is consistent with what was placed.
 * (Redirecting the caller because Mixin can't target the interface's static method directly.)
 *
 * <p>{@code remap = false}: Structurize's own members.
 */
@Mixin(AbstractBlueprintIterator.class)
public class MixinAbstractBlueprintIterator
{
    @Redirect(
            method = "iterateWithCondition",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/placement/handlers/placement/IPlacementHandler;doesWorldStateMatchBlueprintState(Lcom/ldtteam/structurize/util/BlockInfo;Lnet/minecraft/core/BlockPos;Lcom/ldtteam/structurize/placement/structure/IStructureHandler;)Z"),
            remap = false)
    private boolean structurizereplacements$substituteForMatch(final BlockInfo info, final BlockPos pos, final IStructureHandler handler)
    {
        return SubstitutedMatch.matches(info, pos, handler);
    }
}
