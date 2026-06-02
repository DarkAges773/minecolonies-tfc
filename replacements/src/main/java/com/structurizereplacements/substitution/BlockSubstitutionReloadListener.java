package com.structurizereplacements.substitution;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads block-substitution rules from {@code data/<namespace>/block_substitutions/*.json} across all
 * datapacks and namespaces, so any mod (or pack) can contribute rules.
 *
 * <p>Each file is a JSON object with a {@code "replacements"} array. Every entry has a {@code "to"}
 * block id and exactly one of {@code "from"} (block id) or {@code "from_tag"} (block tag id):
 * <pre>{@code
 * {
 *   "replacements": [
 *     { "from_tag": "minecraft:planks", "to": "tfc:wood/planks/oak" },
 *     { "from": "minecraft:cobblestone", "to": "tfc:rock/cobble/granite" }
 *   ]
 * }
 * }</pre>
 */
public class BlockSubstitutionReloadListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "block_substitutions";

    public BlockSubstitutionReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(final Map<ResourceLocation, JsonElement> files, final ResourceManager manager, final ProfilerFiller profiler)
    {
        final List<SubstitutionRule> parsed = new ArrayList<>();

        for (final Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet())
        {
            try
            {
                final JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "top element");
                for (final JsonElement element : GsonHelper.getAsJsonArray(root, "replacements"))
                {
                    final SubstitutionRule rule = parseRule(element.getAsJsonObject(), entry.getKey());
                    if (rule != null)
                    {
                        parsed.add(rule);
                    }
                }
            }
            catch (final Exception ex)
            {
                StructurizeReplacements.LOGGER.error("Skipping malformed substitution file {}: {}", entry.getKey(), ex.getMessage());
            }
        }

        BlockSubstitutions.setRules(parsed);
        StructurizeReplacements.LOGGER.info("Loaded {} block substitution rule(s) from {} file(s).", parsed.size(), files.size());
    }

    @Nullable
    private static SubstitutionRule parseRule(final JsonObject obj, final ResourceLocation file)
    {
        final Block to = block(GsonHelper.getAsString(obj, "to"));
        if (to == null)
        {
            StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'to' block; skipping.", file);
            return null;
        }

        if (obj.has("from"))
        {
            final Block from = block(GsonHelper.getAsString(obj, "from"));
            if (from == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'from' block; skipping.", file);
                return null;
            }
            return new SubstitutionRule(from, null, to);
        }

        if (obj.has("from_tag"))
        {
            final ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "from_tag"));
            if (tagId == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has a malformed 'from_tag'; skipping.", file);
                return null;
            }
            return new SubstitutionRule(null, TagKey.create(Registries.BLOCK, tagId), to);
        }

        StructurizeReplacements.LOGGER.warn("Substitution in {} has neither 'from' nor 'from_tag'; skipping.", file);
        return null;
    }

    @Nullable
    private static Block block(final String id)
    {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl))
        {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(rl);
    }
}
