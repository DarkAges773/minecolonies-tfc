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

    /** Called when the window closes (e.g. to refresh the parent GUI). */
    default void onClosed() {}

    /**
     * Wired by {@link WindowReplacements} so the context can ask the window to re-read {@link #sources()}
     * and redraw — used when sources load asynchronously (e.g. a building's blueprint future).
     */
    default void setReloader(final Runnable reloader) {}
}
