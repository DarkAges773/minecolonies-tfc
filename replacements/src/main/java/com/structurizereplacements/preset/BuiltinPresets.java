package com.structurizereplacements.preset;

import java.util.List;

/**
 * The active set of read-only built-in presets — loaded server-side from datapacks by
 * {@link BuiltinPresetReloadListener} and synced to clients (see
 * {@code SyncBuiltinPresetsMessage}). Mirrors {@link com.structurizereplacements.substitution.BlockSubstitutions}'
 * static rule store: in single-player the integrated server's load is shared (same-JVM statics); on a
 * dedicated server the client gets them via the sync on join / {@code /reload}.
 */
public final class BuiltinPresets
{
    private BuiltinPresets() {}

    private static volatile List<Preset> presets = List.of();

    public static void set(final List<Preset> newPresets)
    {
        presets = List.copyOf(newPresets);
    }

    /** The current built-in presets (read-only). Empty until a datapack load or a server sync populates them. */
    public static List<Preset> all()
    {
        return presets;
    }
}
