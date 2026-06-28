package com.structurizereplacements.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

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

    /**
     * For each source row, the distinct blueprint blocks a swap of that source would affect, as material-aware
     * display {@link ItemStack}s — a bare candidate block is its own host; a Domum Ornamentum block is the host
     * of every material it carries (so swapping oak planks reports both the bare oak-planks block and the
     * framed block that contains it, the latter named for its actual materials). Deduped at material
     * granularity. Drives {@link WindowReplacements}'s per-row "affects N blocks" tooltip and count badge. A
     * missing key or empty list means there's no detail to show; defaults to empty so contexts can opt in.
     */
    default Map<Block, List<ItemStack>> affectedBlocks()
    {
        return Map.of();
    }

    /** Current source → chosen-target map to display. */
    Map<Block, Block> current();

    /** Apply a pick ({@code target == null} clears it), then persist/sync/refresh as appropriate. */
    void choose(Block source, Block target);

    /**
     * Remove the pick for {@code source} entirely. For blueprint-backed contexts (build wand / building) the row
     * comes from the schematic and stays — so this just clears the pick (defaults to {@link #choose} with a null
     * target). The preset editor overrides it to drop the row, since there its rows <i>are</i> the preset's picks.
     */
    default void removePick(final Block source)
    {
        choose(source, null);
    }

    /** Whether each row shows a delete button — only the preset editor (whose rows are the preset's own picks). */
    default boolean allowRowDelete()
    {
        return false;
    }

    /**
     * If this context's name can be edited inline (the preset editor), the current name to show in an editable
     * field; {@code null} for contexts with a fixed title (build wand / building). {@link #setName} commits a change.
     */
    @Nullable
    default String editableName()
    {
        return null;
    }

    /** Rename this context (only meaningful when {@link #editableName()} is non-null), persisting as appropriate. */
    default void setName(final String name) {}

    /**
     * Whether this context offers an "update from current" action — only the preset editor, which can fold the
     * originating build/building's current picks into the preset being edited.
     */
    default boolean canUpdateFromCurrent()
    {
        return false;
    }

    /**
     * Merge the originating context's current picks into this one: override on collision, add new, leave picks
     * for sources not mentioned untouched (the preset editor only). No-op elsewhere.
     */
    default void updateFromCurrent() {}

    /**
     * Whether an empty row list should prompt the player to select a schematic — true for the build-wand context
     * (no blueprint previewed ⇒ nothing to edit). A building/preset editor leaves it false (empty there means
     * something else, not "pick a schematic").
     */
    default boolean wantsSchematicHint()
    {
        return false;
    }

    /**
     * Whether the window offers the "Presets" menu (save current picks / load a preset). True for the editable
     * build-wand and building contexts; false inside the preset editor itself (you're already editing one).
     */
    default boolean offersPresetMenu()
    {
        return true;
    }

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

    /**
     * Translation key for the window title. Defaults to the generic "Edit Block Palette"; contexts editing a
     * specific palette (e.g. the miner's mineshaft palette) override this to label the window accordingly.
     */
    default String titleKey()
    {
        return "structurizereplacements.gui.replace.title";
    }

    /**
     * The full window title. Defaults to {@link #titleKey()} as a translatable; contexts that need a dynamic
     * title (e.g. the preset editor showing the preset's name) override this directly.
     */
    default Component titleComponent()
    {
        return Component.translatable(titleKey());
    }
}
