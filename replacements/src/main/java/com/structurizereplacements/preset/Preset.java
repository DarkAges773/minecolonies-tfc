package com.structurizereplacements.preset;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

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
 * @param id          stable identifier (a user preset's config filename stem; a built-in's source file id).
 * @param displayName the name shown in the picker.
 * @param picks       source → target map (insertion order preserved for stable editor rows; immutable).
 * @param editable    whether this preset can be edited/deleted in place (user library) vs only cloned (built-in).
 */
public record Preset(String id, Component displayName, Map<Block, Block> picks, boolean editable)
{
    public Preset
    {
        picks = Collections.unmodifiableMap(new LinkedHashMap<>(picks));
    }
}
