package com.mctfc.block;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * A non-falling twin of a "cobble" block (vanilla, TFC, or any mod's). It copies the source block's
 * properties (material/strength/sound/…) but extends plain {@link Block} rather than a falling block, and
 * is deliberately <b>not</b> a member of {@code tfc:can_landslide} — so TFC's gravity never applies to it.
 *
 * <p>Intended as a substitution target so MineColonies builds keep cobble constructions that would be
 * infeasible under TFC's collapsing cobble. Drops itself; named "Mortared &lt;source&gt;".
 */
public class MortaredCobbleBlock extends Block
{
    private final Block source;

    public MortaredCobbleBlock(final Block source)
    {
        super(BlockBehaviour.Properties.copy(source));
        this.source = source;
    }

    /** The cobble block this is a non-falling twin of (also its visual model + recipe input). */
    public Block source()
    {
        return source;
    }

    @Override
    public @NotNull MutableComponent getName()
    {
        return Component.translatable("block.mctfc.mortared_cobblestone", source.getName());
    }

    @Override
    public @NotNull List<ItemStack> getDrops(final @NotNull BlockState state, final LootParams.@NotNull Builder params)
    {
        // Dynamically-registered blocks have no loot table; just drop ourselves.
        return Collections.singletonList(new ItemStack(this));
    }
}
