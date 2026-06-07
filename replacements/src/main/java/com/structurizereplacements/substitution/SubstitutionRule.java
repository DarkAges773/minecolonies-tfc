package com.structurizereplacements.substitution;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A single substitution rule: match a blueprint block either by exact block or by block tag,
 * and replace it with {@link #to}. Exactly one of {@link #fromBlock} / {@link #fromTag} is set.
 *
 * <p>{@link #properties} are blockstate property assignments (name → value) stamped onto the result
 * after the swap — e.g. {@code {"no_gravity":"true"}} so a substituted TFC cobble is placed non-falling.
 * A property the target block doesn't define is skipped.
 *
 * <p>{@link #copyProperties} carry a value from a <i>source</i> property into a <i>differently-named</i>
 * target property (source-name → target-name) — e.g. {@code {"half":"part"}} maps vanilla
 * {@code DoublePlantBlock}'s {@code half=lower/upper} onto a TFC two-tall plant's {@code part=lower/upper},
 * so a substituted double plant keeps its two halves aligned. Skipped if either property is absent.
 *
 * <p>Matching is per-block (both exact-block and block-tag membership are block-level), so the
 * engine can cache results keyed by {@link Block}.
 *
 * <p>{@link #priority} resolves conflicts when several rules match the same block: the highest priority
 * wins (ties broken by load order — last loaded wins). Default {@code 0}; an optional datapack that should
 * override a base rule when its mod is present declares a higher priority (e.g. {@code 1}). See
 * {@code BlockSubstitutions#bestFixedRule}.
 */
public record SubstitutionRule(@Nullable Block fromBlock, @Nullable TagKey<Block> fromTag, Block to,
                               Map<String, String> properties, Map<String, String> copyProperties, int priority)
{
    public boolean matches(final BlockState state)
    {
        if (fromBlock != null && state.is(fromBlock))
        {
            return true;
        }
        return fromTag != null && state.is(fromTag);
    }
}
