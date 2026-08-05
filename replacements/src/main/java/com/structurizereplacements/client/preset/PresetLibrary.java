package com.structurizereplacements.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.preset.Preset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The player's personal preset library: editable {@link Preset}s persisted as one JSON file per preset under
 * {@code config/structurizereplacements/presets/}. Subdirectories are <b>folders</b> in the navigable picker, so a
 * preset's {@code id} is its relative path under the library (folder segments joined with {@code /}, no extension).
 * Client-only — it never touches the server; a preset only reaches the world when the player <i>applies</i> it
 * through the normal (permission-checked) choice channels. Because the library lives in the client config it travels
 * with the player across worlds and servers: build a "Granite" preset once, reuse it in every colony.
 *
 * <p>File shape (matches the built-in preset datapack format, minus the read-only flag):
 * <pre>{@code { "name": "My Granite", "icon": "minecraft:stone", "picks": [ { "from": "...", "to": "..." } ] } }</pre>
 * {@code icon} is optional (the GUI derives one from the picks when absent).
 */
public final class PresetLibrary
{
    private PresetLibrary() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path root()
    {
        return FMLPaths.CONFIGDIR.get().resolve(StructurizeReplacements.MODID).resolve("presets");
    }

    /** All user presets (across folders), sorted by display name. Returns an empty list (never throws) on IO error. */
    public static List<Preset> all()
    {
        final List<Preset> out = new ArrayList<>();
        final Path root = root();
        if (!Files.isDirectory(root))
        {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root))
        {
            walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).forEach(file -> {
                final Preset preset = read(root, file);
                if (preset != null)
                {
                    out.add(preset);
                }
            });
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not list preset library: {}", ex.getMessage());
        }
        out.sort((a, b) -> a.displayName().getString().compareToIgnoreCase(b.displayName().getString()));
        return out;
    }

    /**
     * Create a new user preset named {@code name} inside {@code folder} (empty for the root). {@code name} may
     * itself contain {@code /} to nest further. Returns the created preset (or {@code null} on failure).
     */
    public static Preset create(final String folder, final String name, final Map<Block, Block> picks)
    {
        return create(folder, name, picks, null);
    }

    /** As {@link #create(String, String, Map)} but with an explicit row icon (e.g. carried over when cloning). */
    public static Preset create(final String folder, final String name, final Map<Block, Block> picks, final Block icon)
    {
        final String raw = folder.isBlank() ? name : folder + "/" + name;
        final String id = uniqueId(slugPath(raw));
        final String leaf = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        final String display = leaf.isBlank() ? lastSegment(id) : leaf.trim();
        final Preset preset = new Preset(id, Component.literal(display), parentOf(id), icon, picks, true);
        return write(preset) ? preset : null;
    }

    /** Overwrite the file backing {@code preset.id()} with its current name + picks (used by the editor). */
    public static void update(final Preset preset)
    {
        write(preset);
    }

    public static void delete(final String id)
    {
        try
        {
            final Path file = root().resolve(id + ".json");
            Files.deleteIfExists(file);
            pruneEmptyParents(file.getParent());
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not delete preset {}: {}", id, ex.getMessage());
        }
    }

    // --- IO -------------------------------------------------------------------------------------------------

    private static Preset read(final Path root, final Path file)
    {
        try (Reader reader = Files.newBufferedReader(file))
        {
            final String id = relativeId(root, file);
            final JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            final String name = obj.has("name") ? obj.get("name").getAsString() : lastSegment(id);
            final Block icon = obj.has("icon") ? block(obj.get("icon").getAsString()) : null;
            final Map<Block, Block> picks = new LinkedHashMap<>();
            final List<Preset.UnresolvedPick> unresolved = new ArrayList<>();
            if (obj.has("picks"))
            {
                for (final var element : obj.getAsJsonArray("picks"))
                {
                    final JsonObject entry = element.getAsJsonObject();
                    final String fromId = entry.get("from").getAsString();
                    final String toId = entry.get("to").getAsString();
                    final Block from = block(fromId);
                    final Block to = block(toId);
                    if (from != null && to != null)
                    {
                        picks.put(from, to);
                    }
                    else
                    {
                        // A block id not registered in this world (e.g. a TFC pick opened without TFC). Keep the raw
                        // ids so a later save re-emits them unchanged instead of silently dropping the pick.
                        unresolved.add(new Preset.UnresolvedPick(fromId, toId));
                    }
                }
            }
            return new Preset(id, Component.literal(name), parentOf(id), icon, picks, true, unresolved);
        }
        catch (final Exception ex)
        {
            StructurizeReplacements.LOGGER.error("Skipping unreadable preset {}: {}", file.getFileName(), ex.getMessage());
            return null;
        }
    }

    private static boolean write(final Preset preset)
    {
        try
        {
            final Path file = root().resolve(preset.id() + ".json");
            Files.createDirectories(file.getParent());
            final JsonObject obj = new JsonObject();
            obj.addProperty("name", preset.displayName().getString());
            if (preset.icon() != null)
            {
                final ResourceLocation iconId = BuiltInRegistries.BLOCK.getKey(preset.icon());
                if (iconId != null)
                {
                    obj.addProperty("icon", iconId.toString());
                }
            }
            final JsonArray picks = new JsonArray();
            preset.picks().forEach((from, to) -> {
                final ResourceLocation f = BuiltInRegistries.BLOCK.getKey(from);
                final ResourceLocation t = BuiltInRegistries.BLOCK.getKey(to);
                if (f != null && t != null)
                {
                    final JsonObject entry = new JsonObject();
                    entry.addProperty("from", f.toString());
                    entry.addProperty("to", t.toString());
                    picks.add(entry);
                }
            });
            // Re-emit picks for blocks absent in this world verbatim, so editing a cross-mod preset never deletes them.
            for (final Preset.UnresolvedPick u : preset.unresolved())
            {
                final JsonObject entry = new JsonObject();
                entry.addProperty("from", u.from());
                entry.addProperty("to", u.to());
                picks.add(entry);
            }
            obj.add("picks", picks);
            try (Writer writer = Files.newBufferedWriter(file))
            {
                GSON.toJson(obj, writer);
            }
            return true;
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not save preset {}: {}", preset.id(), ex.getMessage());
            return false;
        }
    }

    // --- path helpers ---------------------------------------------------------------------------------------

    /** Relative library id ({@code folder/name}, {@code /}-separated, no extension) for a preset file. */
    private static String relativeId(final Path root, final Path file)
    {
        final String rel = root.relativize(file).toString().replace('\\', '/');
        return rel.substring(0, rel.length() - ".json".length());
    }

    /** Slug each path segment (filesystem-safe) while keeping the folder separators. */
    private static String slugPath(final String raw)
    {
        final List<String> segments = new ArrayList<>();
        for (final String seg : raw.split("/"))
        {
            String slug = seg.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (slug.isEmpty())
            {
                // An all-non-ASCII segment (Cyrillic/CJK/…) strips to nothing; fall back to a stable short hash of
                // the original so distinct names map to distinct files instead of all colliding on "preset". The
                // human-readable name lives in the JSON "name" field, so the opaque filename is invisible in-game.
                final String trimmed = seg.trim();
                if (!trimmed.isEmpty())
                {
                    slug = "preset_" + Integer.toHexString(trimmed.hashCode() & 0x7fffffff);
                }
            }
            if (!slug.isEmpty())
            {
                segments.add(slug);
            }
        }
        return segments.isEmpty() ? "preset" : String.join("/", segments);
    }

    /** Make {@code id} unique against existing files by bumping the leaf ({@code name}, {@code name-2}, …). */
    private static String uniqueId(final String id)
    {
        String candidate = id;
        int n = 2;
        while (Files.exists(root().resolve(candidate + ".json")))
        {
            candidate = id + "-" + n++;
        }
        return candidate;
    }

    private static String parentOf(final String id)
    {
        final int slash = id.lastIndexOf('/');
        return slash < 0 ? "" : id.substring(0, slash);
    }

    private static String lastSegment(final String id)
    {
        final int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(slash + 1);
    }

    /** Remove now-empty folders up the tree (but never the library root) after a delete. */
    private static void pruneEmptyParents(final Path dir) throws IOException
    {
        final Path root = root();
        Path current = dir;
        while (current != null && !current.equals(root) && current.startsWith(root) && Files.isDirectory(current))
        {
            try (Stream<Path> entries = Files.list(current))
            {
                if (entries.findAny().isPresent())
                {
                    return;
                }
            }
            final Path parent = current.getParent();
            Files.deleteIfExists(current);
            current = parent;
        }
    }

    private static Block block(final String id)
    {
        final ResourceLocation key = ResourceLocation.tryParse(id);
        return (key != null && BuiltInRegistries.BLOCK.containsKey(key)) ? BuiltInRegistries.BLOCK.get(key) : null;
    }
}
