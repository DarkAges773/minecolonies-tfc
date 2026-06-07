package com.structurizereplacements.client.gui;

import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Strategy that decouples {@link WindowReplacements} from <i>what</i> it edits. The build wand edits the
 * global session picks ({@link BuildWandChoiceContext}); the Build Options window edits a single
 * MineColonies building (in the {@code :compat} mod). The window only renders rows and dispatches picks
 * through this interface.
 */
public interface ReplacementChoiceContext
{
    /** Candidate source blocks to show as rows (distinct schematic blocks matching a candidate rule). */
    List<Block> sources();

    /** Current source → chosen-target map to display. */
    Map<Block, Block> current();

    /** Apply a pick ({@code target == null} clears it), then persist/sync/refresh as appropriate. */
    void choose(Block source, Block target);

    /** Clear <i>all</i> picks back to the datapack defaults, then persist/sync/refresh as appropriate. */
    void reset();

    /** Called when the window closes (e.g. to refresh the parent GUI). */
    default void onClosed() {}

    /**
     * Wired by {@link WindowReplacements} so the context can ask the window to re-read {@link #sources()}
     * and redraw — used when sources load asynchronously (e.g. a building's blueprint future).
     */
    default void setReloader(final Runnable reloader) {}

    /**
     * Whether this context offers the current-vs-update palette toggle. Only the per-building Build Options
     * context does (a building has tiers); the build wand places a single blueprint, so it returns
     * {@code false} and {@link WindowReplacements} hides the toggle.
     */
    default boolean hasPaletteModeToggle()
    {
        return false;
    }

    /**
     * In <i>update</i> mode ({@code true}, the default) {@link #sources()} come from the blueprint about to
     * be built (the next tier); in <i>current</i> mode ({@code false}) they come from the building's current
     * tier. Meaningful only when {@link #hasPaletteModeToggle()} is {@code true}.
     */
    default boolean isUpdateMode()
    {
        return true;
    }

    /** Switch palette mode and reload sources (no-op unless {@link #hasPaletteModeToggle()}). */
    default void setUpdateMode(final boolean update) {}
}
