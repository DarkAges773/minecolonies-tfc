package com.structurizereplacements.client.gui;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ReplacementChoiceContext} for the build wand: edits the global session picks
 * ({@link ClientPlacementChoices}, which sync to the server) and reads sources from the currently
 * previewed blueprint. This is the original {@link WindowReplacements} behaviour, factored out so the
 * window can also serve per-building editing.
 */
public class BuildWandChoiceContext implements ReplacementChoiceContext
{
    /** Built alongside {@link #sources()} from the same blueprint scan; surfaced via {@link #affectedBlocks()}. */
    private Map<Block, List<Block>> affected = Map.of();

    @Override
    public List<Block> sources()
    {
        final Blueprint blueprint = currentBlueprint();
        if (blueprint == null)
        {
            this.affected = Map.of();
            return List.of();
        }
        this.affected = BlockSubstitutions.candidateSourceHosts(blueprint.getBlockInfoAsList());
        return new ArrayList<>(this.affected.keySet());
    }

    @Override
    public Map<Block, List<Block>> affectedBlocks()
    {
        return affected;
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

    @Override
    public void reset()
    {
        ClientPlacementChoices.set(new HashMap<>());
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
