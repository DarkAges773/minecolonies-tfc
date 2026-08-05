package com.structurizereplacements.substitution;

import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * Drop the memoized per-block resolutions (the rules stay). Needed on {@code TagsUpdatedEvent}:
     * {@code setRules} runs in the reload-listener phase, but registry tags rebind in a LATER async step —
     * a placement in that window memoizes a {@code from_tag} match against the OLD tag contents, and
     * without this it would persist until the next reload.
     */
    public static void clearCache()
    {
        cache.clear();
    }

    /** The active fixed rules — snapshot for the server→client rule sync. */
    public static List<SubstitutionRule> rules()
    {
        return rules;
    }

    /** The active interactive candidate rules — snapshot for the server→client rule sync. */
    public static List<CandidateRule> candidates()
    {
        return candidateRules;
    }

    /**
     * The interactive candidate rule (if any) whose source matches this block — for the GUI. When several
     * match, the highest {@code priority} wins (ties broken by load order, last wins), so an optional pack
     * can override a base pool.
     */
    public static Optional<CandidateRule> candidateFor(final Block source)
    {
        final BlockState probe = source.defaultBlockState();
        CandidateRule best = null;
        for (final CandidateRule rule : candidateRules)
        {
            if (rule.matches(probe) && (best == null || rule.priority() >= best.priority()))
            {
                best = rule;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Server-authoritative validity check for a player-submitted pick ({@code from} → chosen {@code to}): the
     * source must have a candidate pool and the target must be a legal member of it — exactly what the GUI
     * offers ({@link #candidateFor} for the pool, then the same air / Domum-Ornamentum-materialized filtering
     * the picker applies). The inbound choice packets filter through this, so a modified client can't bypass the
     * candidate pools to substitute an arbitrary block (the rules load server-side, so this check is
     * authoritative even when the client claims otherwise).
     */
    public static boolean isAllowedChoice(final Block from, final Block to)
    {
        if (from == null || to == null || to.defaultBlockState().isAir() || isMaterializedSource(to))
        {
            return false;
        }
        final Optional<CandidateRule> rule = candidateFor(from);
        return rule.isPresent() && to.defaultBlockState().is(rule.get().toTag());
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
     * Whether {@code block} is a Domum Ornamentum materialized block — not a usable plain-substitution
     * source or candidate target (its material lives in NBT; bare, it renders as a cycling preview). The GUI
     * filters these out of candidate pools/rows. Delegates to {@link DomumMaterialRewriter#isMaterializedBlock}.
     */
    public static boolean isMaterializedSource(final Block block)
    {
        return DomumMaterialRewriter.isMaterializedBlock(block);
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
     * Collect the candidate-eligible source blocks of one blueprint entry, <i>as the GUI shows them</i>,
     * recording for each the blueprint <b>host</b> block that contributed it. Each raw block (the placed
     * block plus any Domum Ornamentum materials) is first mapped through datapack fixed rules
     * ({@link #datapackTarget}) and kept only if a candidate pool matches the <b>resolved</b> block — so a
     * {@code minecraft:oak_planks -> tfc:oak_planks} conversion surfaces as a {@code tfc:oak_planks} row, and
     * the player's pick (stored against the resolved block) applies on top of the conversion.
     *
     * <p>The host is the blueprint block the source came from: a bare candidate block is its own host; a
     * Domum Ornamentum materialized block is the host of every material it carries (so a framed block whose
     * frame is oak planks registers the framed block as a host of the {@code oak_planks} row). Accumulating
     * this across a blueprint tells the GUI, per row, which distinct blueprint blocks a swap would affect —
     * the "affects N blocks" tooltip. Each source's host set preserves first-seen order.
     */
    public static void collectCandidateSourcesWithHosts(final BlockInfo info, final Map<Block, List<ItemStack>> out)
    {
        if (info == null)
        {
            return;
        }
        final BlockState state = info.getState();
        final Block hostBlock = state == null ? null : state.getBlock();
        if (hostBlock == null)
        {
            return;
        }
        // Display the host as it will actually be built: apply the implicit datapack conversions (fixed rules,
        // no player pick) first. MineColonies blueprints are vanilla, so e.g. an oak-planks host is implicitly
        // converted to its TFC equivalent and a Domum Ornamentum frame's material is converted too — the host
        // must show that, matching the (already-resolved) row, or the tooltip misleads by naming/showing the
        // un-converted blueprint block that never gets placed.
        final BlockInfo built = apply(info, null);
        final BlockState builtState = built.getState();
        final Block builtHost = builtState == null ? hostBlock : builtState.getBlock();
        // A material-aware display stack for that built host: a plain block (with the item-less fallback so e.g.
        // a potted plant shows its plant, not "Air"), or — for a Domum Ornamentum block — its (converted)
        // material map copied on, so the host reports its real name (e.g. "Oak Panel") and textured icon rather
        // than the bare block's unlocalized dynamic descriptionId. Built once per entry, shared across its sources.
        final ItemStack hostStack =
                DomumMaterialRewriter.withMaterialNbt(iconStack(builtHost), builtHost, built.getTileEntityData());
        for (final Block raw : sourceBlocksOf(info))
        {
            // A Domum Ornamentum host block (its material lives in NBT, surfaced via the contained-block rows
            // collected alongside it) is not a meaningful plain-substitution source — skip it as a row key. It
            // still appears as a host of its materials' rows below.
            if (DomumMaterialRewriter.isMaterializedBlock(raw))
            {
                continue;
            }
            final Block resolved = datapackTarget(raw);
            if (!DomumMaterialRewriter.isMaterializedBlock(resolved) && candidateFor(resolved).isPresent())
            {
                addDistinctStack(out.computeIfAbsent(resolved, k -> new ArrayList<>()), hostStack);
            }
        }
    }

    /**
     * A best-effort display {@link ItemStack} for a block: normally {@code new ItemStack(block)}, but for an
     * item-less block ({@code asItem() == AIR}) we fall back where we can — a {@link FlowerPotBlock} (e.g. TFC
     * potted plants, registered with no {@code BlockItem}) shows its contained plant's item, which exists and
     * is distinct per pot. Returns {@link ItemStack#EMPTY} when nothing can represent it. Shared by the GUI's
     * row icons and the affected-host display so both name/icon item-less blocks the same way (a bare
     * {@code new ItemStack} would otherwise read as "Air").
     */
    public static ItemStack iconStack(final Block block)
    {
        final ItemStack direct = new ItemStack(block);
        if (!direct.isEmpty())
        {
            return direct;
        }
        if (block instanceof FlowerPotBlock pot)
        {
            final ItemStack content = new ItemStack(pot.getContent());
            if (!content.isEmpty())
            {
                return content;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Append {@code stack} unless an item-and-NBT-equal one is already present (DO material combos differ by NBT). */
    private static void addDistinctStack(final List<ItemStack> hosts, final ItemStack stack)
    {
        for (final ItemStack existing : hosts)
        {
            if (ItemStack.isSameItemSameTags(existing, stack))
            {
                return;
            }
        }
        hosts.add(stack);
    }

    /**
     * The ordered {@code source -> affected-host-stacks} map for a whole blueprint (or any block-entry
     * iterable): every key is a candidate source the GUI shows as a row, mapped to the distinct blueprint
     * blocks that a swap would affect, each as a material-aware display {@link ItemStack} (see
     * {@link #collectCandidateSourcesWithHosts}). Hosts are deduped at material granularity, so the same
     * Domum Ornamentum block with two material combos counts as two affected blocks. Key order is
     * first-seen; the key set is exactly the rows the picker would show.
     */
    public static Map<Block, List<ItemStack>> candidateSourceHosts(final Iterable<BlockInfo> entries)
    {
        final Map<Block, List<ItemStack>> hosts = new LinkedHashMap<>();
        for (final BlockInfo info : entries)
        {
            collectCandidateSourcesWithHosts(info, hosts);
        }
        return hosts;
    }

    /**
     * Substitute a single block state, used by server placement and the client preview mixins.
     *
     * <p>Resolution order (see {@link #resolveBlock}): (1) datapack fixed rules convert the block (an
     * implicit, GUI-invisible swap, e.g. {@code minecraft:oak_planks -> tfc:oak_planks}); then (2) the
     * player's interactive pick applies on top, keyed by the converted block. Finally any
     * {@code apply_properties} declared by the matched rule are stamped onto the result. Returns the same
     * {@code state} reference when substitution is disabled or nothing applies.
     */
    public static BlockState applyState(final BlockState state, final Map<Block, Block> overrides)
    {
        if (!Config.enableSubstitution || state == null)
        {
            return state;
        }
        final Block source = state.getBlock();
        final Block base = datapackTarget(source);

        // Determine the final target block and which rule's properties go with it.
        Block target = base;
        Map<String, String> properties = Map.of();
        Map<String, String> copyProps = Map.of();
        if (overrides != null && !overrides.isEmpty() && overrides.get(base) != null)
        {
            // Player pick on the converted block — properties come from the candidate rule.
            target = overrides.get(base);
            final Optional<CandidateRule> rule = candidateFor(base);
            properties = rule.map(CandidateRule::properties).orElse(Map.of());
            copyProps = rule.map(CandidateRule::copyProperties).orElse(Map.of());
        }
        else
        {
            // Datapack only — properties come from the matched fixed rule (if any).
            final Optional<SubstitutionRule> rule = fixedRuleFor(source);
            properties = rule.map(SubstitutionRule::properties).orElse(Map.of());
            copyProps = rule.map(SubstitutionRule::copyProperties).orElse(Map.of());
        }

        if (target == source && properties.isEmpty() && copyProps.isEmpty())
        {
            return state;
        }
        BlockState result = (target == source) ? state : copyProperties(state, target.defaultBlockState());
        result = properties.isEmpty() ? result : applyProperties(result, properties);
        return copyProps.isEmpty() ? result : applyCopyProperties(state, result, copyProps);
    }

    /** The fixed (non-interactive) rule whose source matches this block, if any — for its properties. */
    private static Optional<SubstitutionRule> fixedRuleFor(final Block source)
    {
        return bestFixedRule(source);
    }

    /**
     * The fixed rule that wins for this source: among all rules whose source matches, the one with the
     * highest {@code priority} (ties broken by load order — last wins). This single chokepoint keeps the
     * cached target block ({@link #resolve}) and the applied rule's properties ({@link #fixedRuleFor})
     * agreeing on the same rule. An optional datapack overrides a base rule by declaring a higher priority.
     */
    private static Optional<SubstitutionRule> bestFixedRule(final Block source)
    {
        final BlockState probe = source.defaultBlockState();
        SubstitutionRule best = null;
        for (final SubstitutionRule rule : rules)
        {
            if (rule.matches(probe) && (best == null || rule.priority() >= best.priority()))
            {
                best = rule;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Stamp blockstate property assignments (name → value) onto a state; skips properties it lacks. */
    private static BlockState applyProperties(final BlockState state, final Map<String, String> properties)
    {
        BlockState result = state;
        for (final Map.Entry<String, String> entry : properties.entrySet())
        {
            final Property<?> property = result.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null)
            {
                result = setFromString(result, property, entry.getValue());
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState setFromString(final BlockState state, final Property<T> property, final String value)
    {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    /**
     * Carry a value from a source-state property into a <i>differently-named</i> property on the result
     * (source-name → target-name). Used to map vanilla {@code DoublePlantBlock}'s {@code half} onto a TFC
     * two-tall plant's {@code part} (both serialize {@code lower}/{@code upper}), so a substituted double
     * plant keeps its halves aligned. Each entry is skipped if either property is absent or the value
     * doesn't parse on the target.
     */
    private static BlockState applyCopyProperties(final BlockState source, BlockState result, final Map<String, String> copyProps)
    {
        for (final Map.Entry<String, String> entry : copyProps.entrySet())
        {
            final Property<?> srcProp = source.getBlock().getStateDefinition().getProperty(entry.getKey());
            final Property<?> dstProp = result.getBlock().getStateDefinition().getProperty(entry.getValue());
            if (srcProp != null && dstProp != null)
            {
                result = setFromString(result, dstProp, valueName(source, srcProp));
            }
        }
        return result;
    }

    /** The serialized name of {@code property}'s value in {@code state} (e.g. {@code half=LOWER} → {@code "lower"}). */
    private static <T extends Comparable<T>> String valueName(final BlockState state, final Property<T> property)
    {
        return property.getName(state.getValue(property));
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
        return bestFixedRule(source).map(SubstitutionRule::to);
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
