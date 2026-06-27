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
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's personal preset library: editable {@link Preset}s persisted as one JSON file per preset under
 * {@code config/structurizereplacements/presets/}. Client-only — it never touches the server; a preset only
 * reaches the world when the player <i>applies</i> it through the normal (permission-checked) choice channels.
 * Because the library lives in the client config it travels with the player across worlds and servers, which
 * is the point: build a "Granite" preset once, reuse it in every colony.
 *
 * <p>File shape (matches the built-in preset datapack format, minus the read-only flag):
 * <pre>{@code { "name": "My Granite", "picks": [ { "from": "...", "to": "..." }, ... ] } }</pre>
 */
public final class PresetLibrary
{
    private PresetLibrary() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dir()
    {
        return FMLPaths.CONFIGDIR.get().resolve(StructurizeReplacements.MODID).resolve("presets");
    }

    /** All user presets, sorted by display name. Returns an empty list (never throws) on any IO problem. */
    public static List<Preset> all()
    {
        final List<Preset> out = new ArrayList<>();
        final Path dir = dir();
        if (!Files.isDirectory(dir))
        {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json"))
        {
            for (final Path file : stream)
            {
                final Preset preset = read(file);
                if (preset != null)
                {
                    out.add(preset);
                }
            }
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not list preset library: {}", ex.getMessage());
        }
        out.sort((a, b) -> a.displayName().getString().compareToIgnoreCase(b.displayName().getString()));
        return out;
    }

    /** Create a new user preset with a fresh id derived from {@code name}; returns it (or {@code null} on failure). */
    public static Preset create(final String name, final Map<Block, Block> picks)
    {
        final String id = freshId(name);
        final Preset preset = new Preset(id, Component.literal(name.isBlank() ? id : name), picks, true);
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
            Files.deleteIfExists(dir().resolve(id + ".json"));
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not delete preset {}: {}", id, ex.getMessage());
        }
    }

    // --- IO -------------------------------------------------------------------------------------------------

    private static Preset read(final Path file)
    {
        try (Reader reader = Files.newBufferedReader(file))
        {
            final JsonObject root = GSON.fromJson(reader, JsonObject.class);
            final String name = root.has("name") ? root.get("name").getAsString() : stem(file);
            final Map<Block, Block> picks = new LinkedHashMap<>();
            if (root.has("picks"))
            {
                for (final var element : root.getAsJsonArray("picks"))
                {
                    final JsonObject entry = element.getAsJsonObject();
                    final Block from = block(entry.get("from").getAsString());
                    final Block to = block(entry.get("to").getAsString());
                    if (from != null && to != null)
                    {
                        picks.put(from, to);
                    }
                }
            }
            return new Preset(stem(file), Component.literal(name), picks, true);
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
            Files.createDirectories(dir());
            final JsonObject root = new JsonObject();
            root.addProperty("name", preset.displayName().getString());
            final JsonArray picks = new JsonArray();
            preset.picks().forEach((from, to) -> {
                final ResourceLocation f = ForgeRegistries.BLOCKS.getKey(from);
                final ResourceLocation t = ForgeRegistries.BLOCKS.getKey(to);
                if (f != null && t != null)
                {
                    final JsonObject entry = new JsonObject();
                    entry.addProperty("from", f.toString());
                    entry.addProperty("to", t.toString());
                    picks.add(entry);
                }
            });
            root.add("picks", picks);
            try (Writer writer = Files.newBufferedWriter(dir().resolve(preset.id() + ".json")))
            {
                GSON.toJson(root, writer);
            }
            return true;
        }
        catch (final IOException ex)
        {
            StructurizeReplacements.LOGGER.error("Could not save preset {}: {}", preset.id(), ex.getMessage());
            return false;
        }
    }

    /** A filesystem-safe id from a display name, made unique against existing files (slug, slug-2, slug-3, …). */
    private static String freshId(final String name)
    {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty())
        {
            base = "preset";
        }
        String id = base;
        int n = 2;
        while (Files.exists(dir().resolve(id + ".json")))
        {
            id = base + "-" + n++;
        }
        return id;
    }

    private static String stem(final Path file)
    {
        final String fileName = file.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static Block block(final String id)
    {
        final ResourceLocation key = ResourceLocation.tryParse(id);
        return (key != null && ForgeRegistries.BLOCKS.containsKey(key)) ? ForgeRegistries.BLOCKS.getValue(key) : null;
    }
}
