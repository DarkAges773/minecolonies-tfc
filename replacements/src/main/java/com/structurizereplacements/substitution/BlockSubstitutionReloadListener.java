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
 * block id and exactly one of {@code "from"} (block id) or {@code "from_tag"} (block tag id). A
 * {@code "from" → "to"} entry also yields a family cascade by default (see {@link FamilyRule}); set
 * {@code "family": false} to make it an exact-only swap.
 * <pre>{@code
 * {
 *   "replacements": [
 *     { "from": "minecraft:oak_planks", "to": "minecraft:spruce_planks" },     // + cascade oak->spruce
 *     { "from": "minecraft:cobblestone", "to": "minecraft:mossy_cobblestone" },// exact (no clean material token)
 *     { "from_tag": "minecraft:planks", "to": "tfc:wood/planks/oak" }          // tag rule (no cascade)
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
        final List<FamilyRule> families = new ArrayList<>();

        for (final Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet())
        {
            try
            {
                final JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "top element");
                for (final JsonElement element : GsonHelper.getAsJsonArray(root, "replacements"))
                {
                    parseInto(element.getAsJsonObject(), entry.getKey(), rules, families);
                }
            }
            catch (final Exception ex)
            {
                StructurizeReplacements.LOGGER.error("Skipping malformed substitution file {}: {}", entry.getKey(), ex.getMessage());
            }
        }

        BlockSubstitutions.setRules(rules, families);
        StructurizeReplacements.LOGGER.info("Loaded {} substitution rule(s) and {} family cascade(s) from {} file(s).",
                rules.size(), families.size(), files.size());
    }

    private static void parseInto(final JsonObject obj, final ResourceLocation file,
                                  final List<SubstitutionRule> rules, final List<FamilyRule> families)
    {
        final ResourceLocation toId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "to"));
        final Block to = block(toId);
        if (to == null)
        {
            StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'to' block; skipping.", file);
            return;
        }

        if (obj.has("from"))
        {
            final ResourceLocation fromId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "from"));
            final Block from = block(fromId);
            if (from == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has an unknown 'from' block; skipping.", file);
                return;
            }
            rules.add(new SubstitutionRule(from, null, to));

            // Family cascade is on by default for from->to rules; "family": false forces exact-only.
            if (GsonHelper.getAsBoolean(obj, "family", true))
            {
                FamilyRule.derive(fromId, toId).ifPresentOrElse(
                        families::add,
                        () -> StructurizeReplacements.LOGGER.debug(
                                "No family cascade for {} -> {} (needs exactly one differing path token).", fromId, toId));
            }
            return;
        }

        if (obj.has("from_tag"))
        {
            final ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "from_tag"));
            if (tagId == null)
            {
                StructurizeReplacements.LOGGER.warn("Substitution in {} has a malformed 'from_tag'; skipping.", file);
                return;
            }
            rules.add(new SubstitutionRule(null, TagKey.create(Registries.BLOCK, tagId), to));
            return;
        }

        StructurizeReplacements.LOGGER.warn("Substitution in {} has neither 'from' nor 'from_tag'; skipping.", file);
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
