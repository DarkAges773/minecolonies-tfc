package com.structurizereplacements.integration.colony;

import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Bridges the substitution engine's structure handlers to a colony building or decoration's replacement
 * choices (registered as the engine's {@link ChoiceResolver} by {@link ColonyIntegration}; all colony-mod
 * API goes through the {@link ColonyBridge}).
 *
 * <p>The engine can't look up colony objects, so its structure handler asks {@link ChoiceResolver} when it
 * has no choices of its own:
 * <ul>
 *   <li><b>building (hut)</b> — server: find the building at the position and return its persisted choices
 *       ({@code MixinAbstractBuilding}); if it has none yet, adopt any choices the placing player staged at
 *       placement ({@link StagedChoices}, captured by {@code MixinBlueprintPlacementHandling}) and persist
 *       them (markDirty, which re-syncs the view). Client: read the building <i>view</i>'s synced choices
 *       ({@code MixinAbstractBuildingView}) so Build Options reflect them. Read-only client-side.</li>
 *   <li><b>decoration / miner mineshaft node</b> (no building at the position) — resolved via the work
 *       order at the position ({@link ColonyBridge#workOrderChoicesAt}: a decoration work order carries its
 *       own adopted choices, a miner work order resolves to the claiming hut's mineshaft palette).</li>
 * </ul>
 *
 * <p>The lookup also works for the build wand's preview: there's no building or decoration work order at the
 * preview position, so the resolver returns {@code null} and the handler falls back to the global session
 * picks.
 *
 * <p>Server-side this is called once per builder structure handler (the handler caches the result), so the
 * colony / work-order lookup is not per-block.
 */
public final class ColonyChoiceResolver
{
    private ColonyChoiceResolver() {}

    static Map<Block, Block> resolve(final Level world, final BlockPos worldPos)
    {
        final ColonyBridge bridge = ColonyIntegration.bridge();

        // Client: look up the building VIEW directly (the chunk owning-colony cap that the server-side
        // colony lookup relies on is not reliably synced client-side). The view only mirrors what the
        // server synced; never adopt/persist here. Return a non-null map (empty when the building has no
        // override) so the caller knows a building exists here and must NOT fall back to the session picks.
        if (world.isClientSide)
        {
            final Object view = bridge.buildingViewAt(world, worldPos);
            if (view instanceof PlacementChoiceHolder holder)
            {
                final Map<Block, Block> choices = holder.getReplacementChoices();
                return choices == null ? Map.of() : choices;
            }
            return null;
        }

        final Object building = bridge.buildingAt(world, worldPos);
        if (building instanceof PlacementChoiceHolder holder)
        {
            Map<Block, Block> choices = holder.getReplacementChoices();
            if (choices == null || choices.isEmpty())
            {
                final Map<Block, Block> staged = StagedChoices.take(world.dimension(), worldPos);
                if (staged != null && !staged.isEmpty())
                {
                    holder.setReplacementChoices(staged);
                    bridge.markDirty(building);
                    choices = staged;
                }
            }
            return choices;
        }

        // No building here — a decoration or a miner mineshaft node. Its work order (which exists for the
        // whole build) is at worldPos (getLocation() == worldPos).
        return bridge.workOrderChoicesAt(world, worldPos);
    }
}
