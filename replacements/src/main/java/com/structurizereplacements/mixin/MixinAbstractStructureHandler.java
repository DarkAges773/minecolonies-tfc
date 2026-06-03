package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.structure.AbstractStructureHandler;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.ServerChoiceResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

/**
 * Stores the per-placement replacement choices on the <b>structure handler</b> — the object shared by
 * both {@code StructurePlacer} (place/request) and {@code AbstractBlueprintIterator} (build-progress
 * match), so all three phases read the same choices. Both the build-tool handler
 * ({@code CreativeStructureHandler}) and the builder handler ({@code BuildingStructureHandler}) extend
 * {@code AbstractStructureHandler}, so this one mixin covers both.
 */
@Mixin(AbstractStructureHandler.class)
public class MixinAbstractStructureHandler implements PlacementChoiceHolder
{
    @Shadow(remap = false)
    private BlockPos worldPos;

    @Unique
    private Map<Block, Block> structurizereplacements$choices;

    @Unique
    private boolean structurizereplacements$serverResolved;

    @Override
    public void setReplacementChoices(final Map<Block, Block> choices)
    {
        this.structurizereplacements$choices = choices;
    }

    /**
     * Resolution order:
     * <ol>
     *   <li>explicit choices set on this handler (build-tool placement attaches the player's GUI picks;
     *       the builder hooks attach the building's persisted choices) — win;</li>
     *   <li>otherwise, on the <b>client</b>, default to the player's current GUI picks
     *       ({@code ClientPlacementChoices}). The "Build Options" window builds a client-side
     *       {@code LoadOnlyStructureHandler} and runs {@code GET_RES_REQUIREMENTS} to populate its
     *       required-materials list — defaulting here makes that list match the preview hologram (which
     *       reads the same source) without relying on constructor injection (which doesn't fire for these
     *       Structurize handlers);</li>
     *   <li>otherwise {@code null} → datapack rules only (server build with no per-building choice).</li>
     * </ol>
     */
    @Override
    public Map<Block, Block> getReplacementChoices()
    {
        if (this.structurizereplacements$choices != null)
        {
            return this.structurizereplacements$choices;
        }
        final AbstractStructureHandler self = (AbstractStructureHandler) (Object) this;
        final Level world = self.getWorld();
        if (world == null)
        {
            return null;
        }
        if (world.isClientSide)
        {
            // Not cached: the player can re-pick between previews.
            final Map<Block, Block> client = ClientPlacementChoices.current();
            return (client != null && !client.isEmpty()) ? client : null;
        }
        // Server: resolve once (e.g. from the MineColonies building at this position) and cache on the
        // handler, so a full build doesn't re-query per block.
        if (!this.structurizereplacements$serverResolved)
        {
            this.structurizereplacements$serverResolved = true;
            final Map<Block, Block> server = ServerChoiceResolver.resolve(world, this.worldPos);
            if (server != null && !server.isEmpty())
            {
                this.structurizereplacements$choices = server;
            }
        }
        return this.structurizereplacements$choices;
    }
}
