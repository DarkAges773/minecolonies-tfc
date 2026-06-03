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
 * <p>Each file is a JSON object with a {@code "replacements"} array. Every entry has exactly one source
 * — {@code "from"} (block id) or {@code "from_tag"} (block tag id) — and one target: {@code "to"} (a
 * fixed replacement block → {@link SubstitutionRule}) or {@code "to_tag"} (an interactive GUI candidate
 * pool → {@link CandidateRule}). Substitution is <b>explicit</b>: a rule applies only to the block(s) it
 * names — there is no implicit cascade to sibling forms, so list each form (planks/stairs/slabs/…) you
 * want swapped, or offer a {@code to_tag} pool and let the player pick.
 * <pre>{@code
 * {
 *   "replacements": [
 *     { "from": "minecraft:oak_planks", "to": "minecraft:spruce_planks" },     // exact block -> block
 *     { "from_tag": "minecraft:planks", "to": "tfc:wood/planks/oak" },         // tag -> block
 *     { "from_tag": "minecraft:wooden_stairs", "to_tag": "minecraft:wooden_stairs" } // GUI candidate pool
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
        final List<SubstitutionRule> rules = new ArrayList<>();
        final List<CandidateRule> candidates = new ArrayList<>();

        for (final Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet())
        {
            try
            {
                final JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "top element");
                for (final JsonElement element : GsonHelper.getAsJsonArray(root, "replacements"))
                {
                    parseInto(element.getAsJsonObject(), entry.getKey(), rules, candidates);
                }
            }
            catch (final Exception ex)
            {
                StructurizeReplacements.LOGGER.error("Skipping malformed substitution file {}: {}", entry.getKey(), ex.getMessage());
            }
        }

        BlockSubstitutions.setRules(rules, candidates);
        StructurizeReplacements.LOGGER.info("Loaded {} fixed rule(s), {} candidate rule(s) from {} file(s).",
                rules.size(), candidates.size(), files.size());
    }

    /**
     * Parse one entry. Source is {@code "from"} (block) or {@code "from_tag"} (block tag). Target is
     * either {@code "to_tag"} (interactive candidate pool → {@link CandidateRule}) or {@code "to"}
     * (fixed block → {@link SubstitutionRule}).
     */
    private static void parseInto(final JsonObject obj, final ResourceLocation file,
                                  final List<SubstitutionRule> rules,
                                  final List<CandidateRule> candidates)
    {
        // --- source matcher ---
        Block fromBlock = null;
        TagKey<Block> fromTag = null;
        if (obj.has("from"))
        {
            final ResourceLocation fromId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "from"));
            fromBlock = block(fromId);
            if (fromBlock == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'from' block; skipping.", file);
                return;
            }
        }
        else if (obj.has("from_tag"))
        {
            final ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "from_tag"));
            if (tagId == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has a malformed 'from_tag'; skipping.", file);
                return;
            }
            fromTag = TagKey.create(Registries.BLOCK, tagId);
        }
        else
        {
            StructurizeReplacements.LOGGER.warn("Substitution in {} has neither 'from' nor 'from_tag'; skipping.", file);
            return;
        }

        // --- target: candidate pool (interactive) ---
        if (obj.has("to_tag"))
        {
            final ResourceLocation toTagId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "to_tag"));
            if (toTagId == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has a malformed 'to_tag'; skipping.", file);
                return;
            }
            candidates.add(new CandidateRule(fromBlock, fromTag, TagKey.create(Registries.BLOCK, toTagId)));
            return;
        }

        // --- target: fixed block ---
        if (obj.has("to"))
        {
            final ResourceLocation toId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "to"));
            final Block to = block(toId);
            if (to == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'to' block; skipping.", file);
                return;
            }
            rules.add(new SubstitutionRule(fromBlock, fromTag, to));
            return;
        }

        StructurizeReplacements.LOGGER.warn("Substitution in {} has neither 'to' nor 'to_tag'; skipping.", file);
    }

    @Nullable
    private static Block block(@Nullable final ResourceLocation id)
    {
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id))
        {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(id);
    }
}
