package com.structurizereplacements.substitution;

import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active block-substitution ruleset and applies it during blueprint placement.
 *
 * <p>Two kinds of rules, loaded server-side from datapacks by {@link BlockSubstitutionReloadListener}:
 * <ul>
 *   <li>{@link SubstitutionRule} — exact block or block-tag → replacement block (checked first).</li>
 *   <li>{@link FamilyRule} — a material-token cascade derived from a {@code from → to} rule (e.g.
 *       {@code oak → spruce}), applied to sibling forms (stairs/slabs/fences/…) when the target
 *       block exists.</li>
 * </ul>
 * Per-source-block resolution is memoized in {@link #cache} and cleared on every reload.
 */
public final class BlockSubstitutions
{
    private BlockSubstitutions() {}

    private static volatile List<SubstitutionRule> rules = List.of();
    private static volatile List<FamilyRule> families = List.of();

    /** Memoized source-block -> replacement-block (empty Optional = no substitution). */
    private static final Map<Block, Optional<Block>> cache = new ConcurrentHashMap<>();

    public static void setRules(final List<SubstitutionRule> newRules, final List<FamilyRule> newFamilies)
    {
        rules = List.copyOf(newRules);
        families = List.copyOf(newFamilies);
        cache.clear();
    }

    /**
     * Apply the active ruleset to a blueprint position. Returns the original {@code info} unchanged
     * when substitution is disabled, no rule matches, or the rule is a no-op; otherwise returns a new
     * {@link BlockInfo} carrying the replacement state with compatible properties copied over.
     */
    public static BlockInfo apply(final BlockInfo info)
    {
        if (!Config.enableSubstitution || info == null || (rules.isEmpty() && families.isEmpty()))
        {
            return info;
        }

        final BlockState src = info.getState();
        if (src == null)
        {
            return info;
        }

        final Optional<Block> target = targetFor(src.getBlock());
        if (target.isEmpty() || target.get() == src.getBlock())
        {
            return info;
        }

        final BlockState newState = copyProperties(src, target.get().defaultBlockState());
        return new BlockInfo(info.getPos(), newState, info.getTileEntityData());
    }

    private static Optional<Block> targetFor(final Block source)
    {
        return cache.computeIfAbsent(source, BlockSubstitutions::resolve);
    }

    private static Optional<Block> resolve(final Block source)
    {
        // Exact-block and tag rules take priority over derived family cascades.
        final BlockState probe = source.defaultBlockState();
        for (final SubstitutionRule rule : rules)
        {
            if (rule.matches(probe))
            {
                return Optional.of(rule.to());
            }
        }

        final ResourceLocation sourceId = ForgeRegistries.BLOCKS.getKey(source);
        if (sourceId != null)
        {
            for (final FamilyRule family : families)
            {
                final Optional<ResourceLocation> targetId = family.apply(sourceId);
                if (targetId.isPresent() && ForgeRegistries.BLOCKS.containsKey(targetId.get()))
                {
                    final Block target = ForgeRegistries.BLOCKS.getValue(targetId.get());
                    // Only cascade between matching building shapes (stairs->stairs, slab->slab, ...).
                    // This excludes logs/wood/leaves/saplings even though their names share the token.
                    if (target != null && target != source && CascadeShapes.shareShape(source, target))
                    {
                        return Optional.of(target);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Carry over every property the source and target blocks share, so orientation etc. survive. */
    private static BlockState copyProperties(final BlockState from, BlockState to)
    {
        for (final Property<?> property : from.getProperties())
        {
            if (to.hasProperty(property))
            {
                to = copyProperty(from, to, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(final BlockState from, final BlockState to, final Property<T> property)
    {
        return to.setValue(property, from.getValue(property));
    }
}
