package com.structurizereplacements.substitution;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A single substitution rule: match a blueprint block either by exact block or by block tag,
 * and replace it with {@link #to}. Exactly one of {@link #fromBlock} / {@link #fromTag} is set.
 *
 * <p>Matching is per-block (both exact-block and block-tag membership are block-level), so the
 * engine can cache results keyed by {@link Block}.
 */
public record SubstitutionRule(@Nullable Block fromBlock, @Nullable TagKey<Block> fromTag, Block to)
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
