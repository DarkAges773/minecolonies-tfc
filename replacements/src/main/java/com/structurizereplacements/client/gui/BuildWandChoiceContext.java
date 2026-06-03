package com.structurizereplacements.client.gui;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ReplacementChoiceContext} for the build wand: edits the global session picks
 * ({@link ClientPlacementChoices}, which sync to the server) and reads sources from the currently
 * previewed blueprint. This is the original {@link WindowReplacements} behaviour, factored out so the
 * window can also serve per-building editing.
 */
public class BuildWandChoiceContext implements ReplacementChoiceContext
{
    @Override
    public List<Block> sources()
    {
        final Blueprint blueprint = currentBlueprint();
        if (blueprint == null)
        {
            return List.of();
        }
        final Set<Block> distinct = new LinkedHashSet<>();
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            final BlockState state = info.getState();
            if (state != null && BlockSubstitutions.candidateFor(state.getBlock()).isPresent())
            {
                distinct.add(state.getBlock());
            }
        }
        return new ArrayList<>(distinct);
    }

    @Override
    public Map<Block, Block> current()
    {
        return ClientPlacementChoices.current();
    }

    @Override
    public void choose(final Block source, final Block target)
    {
        final Map<Block, Block> map = new HashMap<>(ClientPlacementChoices.current());
        if (target == null || target == Blocks.AIR)
        {
            map.remove(source);
        }
        else
        {
            map.put(source, target);
        }
        ClientPlacementChoices.set(map);
        // Force the blueprint preview to re-bake so the hologram reflects the new choice immediately.
        BlueprintHandler.getInstance().clearCache();
    }

    private static Blueprint currentBlueprint()
    {
        for (final BlueprintPreviewData data : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = data.getBlueprint();
            if (blueprint != null)
            {
                return blueprint;
            }
        }
        return null;
    }
}
