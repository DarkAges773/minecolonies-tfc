package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.structure.AbstractStructureHandler;
import com.structurizereplacements.placement.ChoiceResolver;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.placement.PlacementChoiceHolder;
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
     * Resolution order (no constructor injection — {@code @Inject(method="<init>")} silently never fires
     * for these Structurize handlers, so resolution is lazy here):
     * <ol>
     *   <li>explicit choices set on this handler (build-tool placement attaches the player's GUI picks) — win;</li>
     *   <li>{@link ChoiceResolver} — a building's choices at this position. On the <b>client</b> the
     *       building <i>view</i>'s synced choices (so the "Build Options" list/preview reflect that
     *       building); on the <b>server</b> the building's persisted choices (so the builder uses them),
     *       cached on the handler since a full build queries per block;</li>
     *   <li>otherwise, on the <b>client</b>, the build-wand session picks ({@code ClientPlacementChoices})
     *       — the preview path, where no building exists at the position;</li>
     *   <li>otherwise {@code null} → datapack rules only.</li>
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
            // A building at this position is authoritative: use ITS choices and never bleed in the global
            // session picks. The resolver returns non-null when a building exists here (an empty map means
            // "building, but no per-building override" → datapack rules), and null when there is no
            // building — only then (the build-wand preview) do we fall back to the session picks. Not
            // cached: the building view / session picks can change between previews.
            final Map<Block, Block> resolved = ChoiceResolver.resolve(world, this.worldPos);
            if (resolved != null)
            {
                return resolved.isEmpty() ? null : resolved;
            }
            final Map<Block, Block> client = ClientPlacementChoices.current();
            return (client != null && !client.isEmpty()) ? client : null;
        }
        // Server: resolve once (e.g. from the MineColonies building at this position) and cache on the
        // handler, so a full build doesn't re-query per block.
        if (!this.structurizereplacements$serverResolved)
        {
            this.structurizereplacements$serverResolved = true;
            final Map<Block, Block> server = ChoiceResolver.resolve(world, this.worldPos);
            if (server != null && !server.isEmpty())
            {
                this.structurizereplacements$choices = server;
            }
        }
        return this.structurizereplacements$choices;
    }
}
