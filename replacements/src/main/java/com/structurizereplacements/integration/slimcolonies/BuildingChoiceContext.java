package com.structurizereplacements.integration.slimcolonies;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.client.gui.ReplacementChoiceContext;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.api.colony.buildings.views.IBuildingView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * SlimColonies twin of the MineColonies {@code BuildingChoiceContext} — see that class for the full
 * design rationale. {@link ReplacementChoiceContext} for the Build Options window: edits a single
 * SlimColonies building's replacement choices.
 *
 * <ul>
 *   <li>{@link #sources()} — distinct candidate blocks in the blueprint that <i>will be built</i>
 *       (the <b>target</b> level/style, supplied by {@code WindowBuildBuilding} — NOT the building's current
 *       level), loaded asynchronously the same way that window loads its resource list;</li>
 *   <li>{@link #current()} — the building view's synced choices ({@code MixinAbstractBuildingView});</li>
 *   <li>{@link #choose} — optimistically update the view, send {@link SetBuildingChoicesMessage} to persist
 *       on the server, re-bake the preview, and refresh the Build Options material list.</li>
 * </ul>
 */
public class BuildingChoiceContext implements ReplacementChoiceContext
{
    private final IBuildingView view;
    private final String targetPack;
    private final String targetPath;
    private final Runnable refreshMaterials;

    /** true = source from the to-be-built (next tier) palette (default); false = the current tier's palette. */
    private boolean updateMode = true;

    private Runnable reloader = () -> {};
    private List<Block> sources = List.of();

    public BuildingChoiceContext(final IBuildingView view,
                                 final String targetPack,
                                 final String targetPath,
                                 final Runnable refreshMaterials)
    {
        this.view = view;
        this.targetPack = targetPack;
        this.targetPath = targetPath;
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
        ScNetwork.sendBuildingChoices(view.getPosition(), map);
        BlueprintHandler.getInstance().clearCache();
        refreshMaterials.run();
    }

    @Override
    public void reset()
    {
        // Optimistic local clear, then tell the server to drop all picks for this building.
        if (view instanceof PlacementChoiceHolder holder)
        {
            holder.setReplacementChoices(null);
        }
        ScNetwork.sendBuildingChoices(view.getPosition(), Map.of());
        BlueprintHandler.getInstance().clearCache();
        refreshMaterials.run();
    }

    @Override
    public void onClosed()
    {
        refreshMaterials.run();
    }

    @Override
    public boolean hasPaletteModeToggle()
    {
        return true;
    }

    @Override
    public boolean isUpdateMode()
    {
        return updateMode;
    }

    @Override
    public void setUpdateMode(final boolean update)
    {
        if (update == updateMode)
        {
            return;
        }
        updateMode = update;
        loadBlueprintSources(); // async; the reloader redraws the rows when the blueprint resolves
    }

    private void loadBlueprintSources()
    {
        // Update mode → the next-tier blueprint passed in by the window (what will be built). Current mode →
        // the building's CURRENT tier: the view's structure path carries the original build-level digit (not
        // the upgraded level), so re-stamp it with the building's actual current level — same trailing-digit
        // scheme the colony mod uses to derive a level's blueprint.
        final String pack = updateMode ? targetPack : view.getStructurePack();
        final String path = updateMode ? targetPath : pathForLevel(view.getStructurePath(), view.getBuildingLevel());
        final CompletableFuture<Blueprint> future = StructurePacks.getBlueprintFuture(pack, path);
        ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(future, this::onBlueprintLoaded));
    }

    /** The blueprint path for a given level: the base path with its trailing level digit replaced by {@code level}. */
    private static String pathForLevel(final String basePath, final int level)
    {
        if (basePath == null || basePath.isEmpty())
        {
            return basePath;
        }
        final String base = basePath.replace(".blueprint", "");
        return base.substring(0, base.length() - 1) + level + ".blueprint";
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
