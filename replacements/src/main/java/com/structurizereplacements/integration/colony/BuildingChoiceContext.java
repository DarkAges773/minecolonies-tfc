package com.structurizereplacements.integration.colony;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.structurizereplacements.client.gui.ReplacementChoiceContext;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ReplacementChoiceContext} for the Build Options window: edits a single colony building's
 * replacement choices ({@code view} is the fork's building view, supplied by the fork's
 * {@code MixinWindowBuildBuilding}; colony API goes through the {@link ColonyBridge}).
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
    private final Object view;
    private final String targetPack;
    private final String targetPath;
    private final Runnable refreshMaterials;

    /** true = source from the to-be-built (next tier) palette (default); false = the current tier's palette. */
    private boolean updateMode = true;

    private Runnable reloader = () -> {};
    private List<Block> sources = List.of();
    private Map<Block, List<ItemStack>> affected = Map.of();

    public BuildingChoiceContext(final Object view,
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
    public Map<Block, List<ItemStack>> affectedBlocks()
    {
        return affected;
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
        ColonyNetwork.sendBuildingChoices(ColonyIntegration.bridge().viewPosition(view), map);
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
        ColonyNetwork.sendBuildingChoices(ColonyIntegration.bridge().viewPosition(view), Map.of());
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
        final ColonyBridge bridge = ColonyIntegration.bridge();
        final String pack = updateMode ? targetPack : bridge.viewStructurePack(view);
        final String path = updateMode ? targetPath
                : LevelPaths.pathForLevel(bridge.viewStructurePath(view), bridge.viewBuildingLevel(view));
        // 1.21 threads a HolderLookup.Provider through blueprint loading (block states/entities now resolve
        // through registries rather than raw ids). This runs client-side from the GUI, so the connected level's
        // registry access is the right one; without a level there is nothing to load against.
        final Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            return;
        }
        final CompletableFuture<Blueprint> future = StructurePacks.getBlueprintFuture(pack, path, level.registryAccess());
        ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(future, this::onBlueprintLoaded));
    }

    private void onBlueprintLoaded(final Blueprint blueprint)
    {
        if (blueprint == null)
        {
            return;
        }
        this.affected = BlockSubstitutions.candidateSourceHosts(blueprint.getBlockInfoAsList());
        this.sources = new ArrayList<>(this.affected.keySet());
        this.reloader.run();
    }
}
