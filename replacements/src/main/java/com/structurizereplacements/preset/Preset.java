package com.structurizereplacements.preset;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named, reusable set of replacement picks (source block → chosen target). Because picks are keyed by
 * source block — the same key the engine looks up at placement — a single preset generalizes across <i>any</i>
 * blueprint or building: it substitutes whatever sources it recognises and ignores the rest. So one "Granite"
 * preset can re-palette a whole colony.
 *
 * <p>Two flavours feed the {@link com.structurizereplacements.client.gui.WindowPresetList} picker:
 * <ul>
 *   <li><b>User presets</b> ({@code editable == true}) — the player's personal library, stored as JSON in the
 *       mod config folder ({@link com.structurizereplacements.client.preset.PresetLibrary}); their
 *       {@code displayName} is a literal the player typed.</li>
 *   <li><b>Built-in presets</b> ({@code editable == false}) — read-only, shipped by a mod/pack as datapack data
 *       ({@link BuiltinPresetReloadListener}) and synced to clients; their {@code displayName} is a translatable
 *       component. Cloning one copies it into the editable user library.</li>
 * </ul>
 *
 * @param id          stable identifier — a user preset's relative path under the library (folder segments joined
 *                    with {@code /}, no extension); a built-in's source file id ({@code namespace:path}).
 * @param displayName the name shown in the picker.
 * @param folder      the folder this preset lives in for the navigable picker ({@code ""} = library root), e.g.
 *                    {@code "rock_types"}. Nested folders join segments with {@code /}.
 * @param icon        an explicit representative block for the row icon, or {@code null} to derive one from the
 *                    picks (see {@link #iconBlock()}).
 * @param picks       source → target map (insertion order preserved for stable editor rows; immutable).
 * @param editable    whether this preset can be edited/deleted in place (user library) vs only cloned (built-in).
 */
public record Preset(String id, Component displayName, String folder, @Nullable Block icon,
                     Map<Block, Block> picks, boolean editable)
{
    public Preset
    {
        folder = folder == null ? "" : folder;
        picks = Collections.unmodifiableMap(new LinkedHashMap<>(picks));
    }

    /** The block to show as this preset's icon: the explicit {@link #icon} if set, else the first pick's target. */
    @Nullable
    public Block iconBlock()
    {
        if (icon != null)
        {
            return icon;
        }
        final var it = picks.values().iterator();
        return it.hasNext() ? it.next() : null;
    }
}
