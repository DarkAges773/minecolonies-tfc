package com.structurizereplacements.substitution;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * An <b>interactive</b> rule: a source matcher (exact block or block tag) whose target is a
 * <i>tag</i> ({@link #toTag}) — the pool of candidates the player chooses from in the GUI. Unlike
 * {@link SubstitutionRule}, a candidate rule substitutes nothing on its own; it only declares "blocks
 * matching this source may be replaced by a member of {@code toTag}, player's pick."
 *
 * <p>{@link #properties} are blockstate property assignments (name → value) stamped onto whichever
 * candidate the player picks — e.g. {@code {"no_gravity":"true"}} so any picked TFC cobble is non-falling.
 *
 * <p>Example: {@code #minecraft:planks → #tfc:planks} lets any vanilla plank be replaced by a chosen
 * TFC plank.
 */
public record CandidateRule(@Nullable Block fromBlock, @Nullable TagKey<Block> fromTag, TagKey<Block> toTag,
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
