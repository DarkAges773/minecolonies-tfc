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
 * <p>Matching is per-block (both exact-block and block-tag membership are block-level), so the
 * engine can cache results keyed by {@link Block}.
 */
public record SubstitutionRule(@Nullable Block fromBlock, @Nullable TagKey<Block> fromTag, Block to,
                               Map<String, String> properties)
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
