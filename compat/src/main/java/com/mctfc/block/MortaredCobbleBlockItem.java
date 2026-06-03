package com.mctfc.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * BlockItem for a {@link MortaredCobbleBlock}. The twins are registered dynamically, so they have no lang
 * keys; a plain {@code BlockItem} would show its raw {@code block.mctfc.mortared.<…>} description id in the
 * inventory/tooltip. Delegating the item name to {@link MortaredCobbleBlock#getName()} ("Mortared &lt;source&gt;")
 * gives a sensible display name everywhere with no per-twin lang entry.
 */
public class MortaredCobbleBlockItem extends BlockItem
{
    public MortaredCobbleBlockItem(final MortaredCobbleBlock block)
    {
        super(block, new Item.Properties());
    }

    @Override
    public @NotNull Component getName(final @NotNull ItemStack stack)
    {
        return getBlock().getName();
    }

    @Override
    public @NotNull Component getDescription()
    {
        return getBlock().getName();
    }
}
