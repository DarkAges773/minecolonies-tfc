package com.firmavanilla.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * A decorative, full-cube bookshelf (vanilla {@code minecraft:bookshelf} style) that provides enchanting
 * power — the per-wood variant TFC/AFC/Beneath don't ship (they only add the chiseled, 6-slot bookshelf).
 * Its 4 sides are a vanilla-style books face on the wood's frame, top/bottom its planks (generated models).
 *
 * <p>Drops are handled in code (rather than a loot table) because the AFC/Beneath variants are registered
 * conditionally, so a shipped loot table referencing them would fail to validate when those mods are absent.
 */
public class DecorativeBookshelfBlock extends Block
{
    public DecorativeBookshelfBlock(final Properties props)
    {
        super(props);
    }

    /** Same enchanting-power bonus as a vanilla bookshelf, so it boosts an enchanting table. */
    @Override
    public float getEnchantPowerBonus(final BlockState state, final LevelReader level, final BlockPos pos)
    {
        return 1.0F;
    }

    /** Vanilla bookshelf drops: 3 books normally, or the block itself when broken with Silk Touch. */
    @Override
    public @NotNull List<ItemStack> getDrops(final @NotNull BlockState state, final LootParams.@NotNull Builder params)
    {
        final ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0)
        {
            return Collections.singletonList(new ItemStack(this));
        }
        return Collections.singletonList(new ItemStack(Items.BOOK, 3));
    }
}
