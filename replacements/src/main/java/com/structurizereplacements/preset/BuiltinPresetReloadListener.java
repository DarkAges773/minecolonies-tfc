package com.structurizereplacements.preset;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads read-only built-in presets from {@code data/<namespace>/block_substitution_presets/*.json} across all
 * datapacks, so any mod or pack can ship ready-made pick sets (e.g. {@code :compat}'s per-TFC-rock-type stone
 * presets). Each file is one preset:
 *
 * <pre>{@code
 * {
 *   "name": "mctfc.preset.granite",                          // translation key (or literal) for the display name
 *   "icon": "tfc:rock/raw/granite",                          // optional: block for the row icon (else derived)
 *   "picks": [
 *     { "from": "minecraft:cobblestone", "to": "tfc:rock/cobble/granite" },
 *     { "from": "minecraft:stone_bricks", "to": "tfc:rock/bricks/granite" }
 *   ]
 * }
 * }</pre>
 *
 * <p>A pick whose {@code from}/{@code to} block id is absent (e.g. a TFC id in a standalone install) is skipped;
 * a preset left with no valid picks is dropped entirely, so a TFC preset never shows up — empty — without TFC.
 * {@code from}/{@code to} are <b>blocks</b> (the same keys the engine substitutes on), so a preset generalises
 * across blueprints. The file's <b>subdirectory</b> under {@code block_substitution_presets/} becomes the preset's
 * folder in the navigable picker (e.g. {@code .../rock_types/granite.json} → folder {@code "rock_types"}). Loaded
 * server-side and synced to clients (see {@code SyncBuiltinPresetsMessage}).
 */
public class BuiltinPresetReloadListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "block_substitution_presets";

    public BuiltinPresetReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(final Map<ResourceLocation, JsonElement> files, final ResourceManager manager, final ProfilerFiller profiler)
    {
        final List<Preset> presets = new ArrayList<>();
        // Sorted ids for a stable preset order regardless of the HashMap vanilla hands us.
        final List<ResourceLocation> ordered = new ArrayList<>(files.keySet());
        ordered.sort(null);
        for (final ResourceLocation file : ordered)
        {
            try
            {
                final JsonObject root = GsonHelper.convertToJsonObject(files.get(file), "top element");
                final String name = GsonHelper.getAsString(root, "name", file.getPath());
                final Block icon = root.has("icon") ? block(GsonHelper.getAsString(root, "icon")) : null;
                final Map<Block, Block> picks = new LinkedHashMap<>();
                for (final JsonElement element : GsonHelper.getAsJsonArray(root, "picks"))
                {
                    final JsonObject entry = element.getAsJsonObject();
                    final Block from = block(GsonHelper.getAsString(entry, "from"));
                    final Block to = block(GsonHelper.getAsString(entry, "to"));
                    if (from != null && to != null)
                    {
                        picks.put(from, to);
                    }
                }
                if (picks.isEmpty())
                {
                    // All referenced blocks absent (e.g. a TFC preset without TFC) — drop it rather than show
                    // an empty preset that substitutes nothing.
                    continue;
                }
                // Folder = the file's subdirectory under block_substitution_presets/ (e.g. "rock_types/granite"
                // → "rock_types"); a top-level file has no folder.
                final String path = file.getPath();
                final int lastSlash = path.lastIndexOf('/');
                final String folder = lastSlash < 0 ? "" : path.substring(0, lastSlash);
                presets.add(new Preset(file.toString(), Component.translatable(name), folder, icon, picks, false));
            }
            catch (final Exception ex)
            {
                StructurizeReplacements.LOGGER.error("Skipping malformed preset file {}: {}", file, ex.getMessage());
            }
        }

        BuiltinPresets.set(presets);
        StructurizeReplacements.LOGGER.info("Loaded {} built-in preset(s) from {} file(s).", presets.size(), files.size());
    }

    private static Block block(final String id)
    {
        final ResourceLocation key = ResourceLocation.tryParse(id);
        return (key != null && BuiltInRegistries.BLOCK.containsKey(key)) ? BuiltInRegistries.BLOCK.get(key) : null;
    }
}
