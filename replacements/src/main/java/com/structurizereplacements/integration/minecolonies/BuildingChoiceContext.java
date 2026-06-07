package com.structurizereplacements.integration.minecolonies;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.structurizereplacements.client.gui.ReplacementChoiceContext;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ReplacementChoiceContext} for the Build Options window: edits a single MineColonies building's
 * replacement choices.
 *
 * <ul>
 *   <li>{@link #sources()} — distinct candidate blocks in the blueprint that <i>will be built</i>
 *       (the <b>target</b> level/style, supplied by {@code WindowBuildBuilding} — NOT the building's current
 *       level), loaded asynchronously the same way that window loads its resource list;</li>
 *   <li>{@link #current()} — the building view's synced choices ({@code MixinAbstractBuildingView});</li>
 *   <li>{@link #choose} — optimistically update the view, send {@link SetBuildingChoicesMessage} to persist
 *       on the server, re-bake the preview, and refresh the Build Options material list.</li>
 * </ul>
 *
 * <p>The choice map is per-building (block → block), not per-level, so editing it for the next tier's blocks
 * just augments the same map; only the <i>source list</i> must reflect the tier about to be built — hence the
 * caller passes the resolved target {@code structurePack}/{@code structurePath} rather than the view's current
 * ones (which would show the current tier and hide blocks new to the upgrade).
 */
public class BuildingChoiceContext implements ReplacementChoiceContext
{
    private final IBuildingView view;
    private final String structurePack;
    private final String structurePath;
    private final Runnable refreshMaterials;

    private Runnable reloader = () -> {};
    private List<Block> sources = List.of();

    public BuildingChoiceContext(final IBuildingView view,
                                 final String structurePack,
                                 final String structurePath,
                                 final Runnable refreshMaterials)
    {
        this.view = view;
        this.structurePack = structurePack;
        this.structurePath = structurePath;
        this.refreshMaterials = refreshMaterials;
        loadBlueprintSources();
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
        final Map<Block, Block> choices = (view instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
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
        // Optimistic local update so the GUI/preview reflect the pick before the server round-trip.
        if (view instanceof PlacementChoiceHolder holder)
        {
            holder.setReplacementChoices(map.isEmpty() ? null : map);
        }
        McNetwork.sendBuildingChoices(view.getPosition(), map);
        BlueprintHandler.getInstance().clearCache();
        refreshMaterials.run();
    }

    @Override
    public void onClosed()
    {
        refreshMaterials.run();
    }

    private void loadBlueprintSources()
    {
        final CompletableFuture<Blueprint> future = StructurePacks.getBlueprintFuture(structurePack, structurePath);
        ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(future, this::onBlueprintLoaded));
    }

    private void onBlueprintLoaded(final Blueprint blueprint)
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
        this.sources = new ArrayList<>(distinct);
        this.reloader.run();
    }
}
