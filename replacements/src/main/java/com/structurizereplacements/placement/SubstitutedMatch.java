package com.structurizereplacements.placement;

import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.integration.colony.ColonyIntegration;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.DomumMaterialRewriter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * The substitution-aware version of Structurize's "is this position already built correctly?" check,
 * shared by every call site that decides whether a blueprint position needs work
 * ({@code AbstractBlueprintIterator#iterateWithCondition} via {@code MixinAbstractBlueprintIterator},
 * and {@code StructurePlacer#getResourceRequirements}'s own internal match via
 * {@code MixinStructurePlacer} — the builder's material list comes from the latter, so missing it there
 * leaves substituted blocks out of the request list and the colony AI recalc-loops on them).
 *
 * <p>Substitutes the blueprint {@link BlockInfo} with the handler's choices first, then defers to the
 * placement handler's match. When the loaded colony mod's DO matching ignores tile materials
 * ({@code ColonyBridge#placementIgnoresDoMaterials} — SlimColonies; MineColonies and Structurize compare
 * them natively) and the substitution changed the <b>tile data</b> (a Domum Ornamentum material swap —
 * the host state is unchanged) yet the handler still reports a match, the world block's DO materials are
 * verified directly.
 */
public final class SubstitutedMatch
{
    private SubstitutedMatch() {}

    public static boolean matches(final BlockInfo info, final BlockPos worldPos, final IStructureHandler handler)
    {
        // The handler (shared by placer and iterator) carries this placement's choices; null -> datapack rules.
        final Map<Block, Block> choices = (handler instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
        final BlockInfo substituted = BlockSubstitutions.apply(info, choices);
        final boolean matches = IPlacementHandler.doesWorldStateMatchBlueprintState(substituted, worldPos, handler);
        if (matches
                && substituted.getTileEntityData() != info.getTileEntityData()
                && ColonyIntegration.placementIgnoresDoMaterials())
        {
            return DomumMaterialRewriter.materialsMatchWorld(
                    handler.getWorld().getBlockEntity(worldPos), substituted.getTileEntityData());
        }
        return matches;
    }
}
