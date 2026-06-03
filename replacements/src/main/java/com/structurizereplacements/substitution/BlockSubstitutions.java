package com.structurizereplacements.substitution;

import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the active block-substitution ruleset and applies it during blueprint placement.
 *
 * <p>Rules are loaded server-side from datapacks by {@link BlockSubstitutionReloadListener}:
 * <ul>
 *   <li>{@link SubstitutionRule} — exact block or block-tag → replacement block. Matches are
 *       <b>explicit</b>: a rule applies only to the exact block(s) it names; there is no implicit
 *       cascade to sibling forms (use one rule per form, or the interactive GUI candidate rules).</li>
 *   <li>{@link CandidateRule} — an interactive {@code to_tag} pool the player picks from in the GUI;
 *       substitutes nothing on its own.</li>
 * </ul>
 * Per-source-block resolution is memoized in {@link #cache} and cleared on every reload.
 */
public final class BlockSubstitutions
{
    private BlockSubstitutions() {}

    private static volatile List<SubstitutionRule> rules = List.of();
    private static volatile List<CandidateRule> candidateRules = List.of();

    /** Memoized source-block -> replacement-block (empty Optional = no substitution). */
    private static final Map<Block, Optional<Block>> cache = new ConcurrentHashMap<>();

    public static void setRules(final List<SubstitutionRule> newRules,
                                final List<CandidateRule> newCandidates)
    {
        rules = List.copyOf(newRules);
        candidateRules = List.copyOf(newCandidates);
        cache.clear();
    }

    /** The interactive candidate rule (if any) whose source matches this block — for the GUI. */
    public static Optional<CandidateRule> candidateFor(final Block source)
    {
        final BlockState probe = source.defaultBlockState();
        for (final CandidateRule rule : candidateRules)
        {
            if (rule.matches(probe))
            {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * Apply the active ruleset to a blueprint position. Returns the original {@code info} unchanged
     * when substitution is disabled, no rule matches, or the rule is a no-op; otherwise returns a new
     * {@link BlockInfo} carrying the replacement state with compatible properties copied over.
     */
    public static BlockInfo apply(final BlockInfo info)
    {
        return apply(info, null);
    }

    public static BlockState applyState(final BlockState state)
    {
        return applyState(state, null);
    }

    /**
     * As {@link #apply(BlockInfo)} but with a per-placement override map (player choices). See
     * {@link #resolveBlock} for the resolution order (datapack conversion first, then player pick on top);
     * {@code null}/empty {@code overrides} means "datapack rules only".
     */
    public static BlockInfo apply(final BlockInfo info, final Map<Block, Block> overrides)
    {
        if (info == null)
        {
            return info;
        }
        final BlockState src = info.getState();
        final BlockState newState = applyState(src, overrides);

        // Domum Ornamentum "materialized" blocks keep their material(s) in tile-entity NBT, not the state;
        // rewrite those contained blocks through the same per-block resolution.
        final CompoundTag tileData = info.getTileEntityData();
        final CompoundTag newTileData = (src == null)
                ? tileData
                : DomumMaterialRewriter.rewrite(src.getBlock(), tileData, block -> resolveBlock(block, overrides));

        if (newState == src && newTileData == tileData)
        {
            return info; // nothing changed (applyState / rewrite return the same reference when no-op)
        }
        return new BlockInfo(info.getPos(), newState, newTileData);
    }

    /**
     * All blocks in a single blueprint entry that could be a substitution source: the placed block plus
     * any material(s) a Domum Ornamentum block carries in its {@code textureData} NBT. Used by the GUI to
     * enumerate pickable rows (so a DO panel's contained oak surfaces alongside bare oak).
     */
    public static List<Block> sourceBlocksOf(final BlockInfo info)
    {
        final List<Block> out = new ArrayList<>();
        if (info == null)
        {
            return out;
        }
        final BlockState state = info.getState();
        final Block host = state == null ? null : state.getBlock();
        if (host != null)
        {
            out.add(host);
        }
        DomumMaterialRewriter.collectContainedBlocks(host, info.getTileEntityData(), out);
        return out;
    }

    /**
     * Collect the candidate-eligible source blocks of one blueprint entry, <i>as the GUI shows them</i>:
     * each raw block (the placed block plus any Domum Ornamentum materials) is first mapped through
     * datapack fixed rules ({@link #datapackTarget}), and kept only if a candidate pool matches the
     * <b>resolved</b> block. So a {@code minecraft:oak_planks -> tfc:oak_planks} conversion surfaces as a
     * {@code tfc:oak_planks} row, and the player's pick (stored against the resolved block) applies on top
     * of the conversion.
     */
    public static void collectCandidateSources(final BlockInfo info, final Collection<Block> out)
    {
        for (final Block raw : sourceBlocksOf(info))
        {
            final Block resolved = datapackTarget(raw);
            if (candidateFor(resolved).isPresent())
            {
                out.add(resolved);
            }
        }
    }

    /**
     * Substitute a single block state, used by server placement and the client preview mixins.
     *
     * <p>Resolution order (see {@link #resolveBlock}): (1) datapack fixed rules convert the block (an
     * implicit, GUI-invisible swap, e.g. {@code minecraft:oak_planks -> tfc:oak_planks}); then (2) the
     * player's interactive pick applies on top, keyed by the converted block. Returns the same
     * {@code state} reference when substitution is disabled or nothing applies.
     */
    public static BlockState applyState(final BlockState state, final Map<Block, Block> overrides)
    {
        if (!Config.enableSubstitution || state == null)
        {
            return state;
        }
        final Block target = resolveBlock(state.getBlock(), overrides);
        if (target == state.getBlock())
        {
            return state;
        }
        return copyProperties(state, target.defaultBlockState());
    }

    /**
     * Resolve a single block to its replacement. Resolution order:
     * <ol>
     *   <li><b>Datapack fixed rules</b> ({@link #datapackTarget}) convert the block first — an implicit
     *       conversion (e.g. {@code minecraft:oak_planks -> tfc:oak_planks}) that is never shown in the GUI.</li>
     *   <li>The <b>player's interactive pick</b> applies on top, keyed by the <i>converted</i> block, so
     *       the picker operates on what is actually placed (e.g. a {@code tfc:oak_planks} row).</li>
     * </ol>
     * Returns the same {@code source} when substitution is disabled or nothing applies. Shared by state
     * substitution and the Domum Ornamentum NBT-material rewrite.
     */
    public static Block resolveBlock(final Block source, final Map<Block, Block> overrides)
    {
        if (!Config.enableSubstitution || source == null)
        {
            return source;
        }
        // (1) Datapack fixed rules first (implicit conversion).
        final Block base = datapackTarget(source);
        // (2) Player pick on top, keyed by the converted block.
        if (overrides != null && !overrides.isEmpty())
        {
            final Block chosen = overrides.get(base);
            if (chosen != null)
            {
                return chosen;
            }
        }
        return base;
    }

    /**
     * The block a source resolves to under datapack fixed rules alone (no player overrides) — the
     * implicit conversion target. Returns the {@code source} itself when no rule matches or substitution
     * is disabled.
     */
    public static Block datapackTarget(final Block source)
    {
        if (!Config.enableSubstitution || source == null || rules.isEmpty())
        {
            return source;
        }
        return targetFor(source).orElse(source);
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
