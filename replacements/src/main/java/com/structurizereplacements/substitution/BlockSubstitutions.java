package com.structurizereplacements.substitution;

import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.Config;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active block-substitution ruleset and applies it during blueprint placement.
 *
 * <p>Rules are loaded server-side from datapacks by {@link BlockSubstitutionReloadListener} and
 * swapped in via {@link #setRules(List)}. The per-source-block resolution is memoized in
 * {@link #cache} and cleared on every reload.
 */
public final class BlockSubstitutions
{
    private BlockSubstitutions() {}

    /** Active rules; replaced wholesale on datapack reload. */
    private static volatile List<SubstitutionRule> rules = List.of();

    /** Memoized source-block -> replacement-block (empty Optional = no substitution). */
    private static final Map<Block, Optional<Block>> cache = new ConcurrentHashMap<>();

    public static void setRules(final List<SubstitutionRule> newRules)
    {
        rules = List.copyOf(newRules);
        cache.clear();
    }

    /**
     * Apply the active ruleset to a blueprint position. Returns the original {@code info} unchanged
     * when substitution is disabled, no rule matches, or the rule is a no-op; otherwise returns a new
     * {@link BlockInfo} carrying the replacement state with compatible properties copied over.
     */
    public static BlockInfo apply(final BlockInfo info)
    {
        if (!Config.enableSubstitution || rules.isEmpty() || info == null)
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
        final BlockState probe = source.defaultBlockState();
        for (final SubstitutionRule rule : rules)
        {
            if (rule.matches(probe))
            {
                return Optional.of(rule.to());
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
