package com.structurizereplacements.integration.slimcolonies;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.client.gui.ReplacementChoiceContext;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.api.colony.buildings.views.IBuildingView;
import no.monopixel.slimcolonies.core.entity.ai.workers.util.MineNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * SlimColonies twin of the MineColonies {@code MineshaftChoiceContext} — see that class for the full design
 * rationale. {@link ReplacementChoiceContext} for the miner hut's mineshaft palette (opened from the miner's
 * settings window): edits the miner's <b>separate</b> mineshaft palette ({@link MineshaftChoiceHolder}) and
 * aggregates the candidate blocks of <i>all</i> the mineshaft schematics the miner ever places (every
 * non-empty {@link MineNode.NodeType}), loaded from the hut's own structure pack.
 */
public class MineshaftChoiceContext implements ReplacementChoiceContext
{
    private final IBuildingView view;

    private Runnable reloader = () -> {};
    /** Accumulated across the per-blueprint futures (all resolve on the client thread, so no syncing needed). */
    private final Set<Block> accumulated = new LinkedHashSet<>();
    private List<Block> sources = List.of();

    public MineshaftChoiceContext(final IBuildingView view)
    {
        this.view = view;
        loadMineshaftSources();
    }

    @Override
    public void setReloader(final Runnable reloader)
    {
        this.reloader = reloader;
    }

    @Override
    public List<Block> sources()
    {
        return sources;
    }

    @Override
    public Map<Block, Block> current()
    {
        final Map<Block, Block> choices = (view instanceof MineshaftChoiceHolder holder) ? holder.getMineshaftChoices() : null;
        return choices == null ? Map.of() : choices;
    }

    @Override
    public void choose(final Block source, final Block target)
    {
        final Map<Block, Block> map = new HashMap<>(current());
        if (target == null)
        {
            map.remove(source);
        }
        else
        {
            map.put(source, target);
        }
        if (view instanceof MineshaftChoiceHolder holder)
        {
            holder.setMineshaftChoices(map.isEmpty() ? null : map);
        }
        ScNetwork.sendMineshaftChoices(view.getPosition(), map);
        BlueprintHandler.getInstance().clearCache();
    }

    @Override
    public void reset()
    {
        if (view instanceof MineshaftChoiceHolder holder)
        {
            holder.setMineshaftChoices(null);
        }
        ScNetwork.sendMineshaftChoices(view.getPosition(), Map.of());
        BlueprintHandler.getInstance().clearCache();
    }

    @Override
    public String titleKey()
    {
        return "structurizereplacements.gui.replace.mineshaft.title";
    }

    private void loadMineshaftSources()
    {
        final String pack = view.getStructurePack();
        // Distinct schematic paths the miner uses; NodeType carries the canonical names (LADDER_BACK/UNDEFINED
        // are empty — skipped). Deriving from the enum means new node types are picked up automatically.
        final Set<String> paths = new LinkedHashSet<>();
        for (final MineNode.NodeType type : MineNode.NodeType.values())
        {
            final String name = type.getSchematicName();
            if (name != null && !name.isEmpty())
            {
                paths.add(name + ".blueprint");
            }
        }
        for (final String path : paths)
        {
            final CompletableFuture<Blueprint> future = StructurePacks.getBlueprintFuture(pack, path);
            ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(future, this::foldBlueprint));
        }
    }

    private void foldBlueprint(final Blueprint blueprint)
    {
        if (blueprint == null)
        {
            return;
        }
        final Set<Block> distinct = new LinkedHashSet<>();
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            BlockSubstitutions.collectCandidateSources(info, distinct);
        }
        if (accumulated.addAll(distinct))
        {
            this.sources = new ArrayList<>(accumulated);
            this.reloader.run();
        }
    }
}
