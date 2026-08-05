package com.firmavanilla.block;

import com.firmavanilla.weathering.WeatheringClone;
import net.dries007.tfc.common.blocks.TFCChainBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Chain weathering copper (TFC's copper chain). Extends {@link TFCChainBlock} (not vanilla {@code ChainBlock}):
 * TFC ships its own chain block with a fluid-logging {@code FluidProperty} and mixes into the chain expecting it,
 * so a vanilla {@code ChainBlock} crashes at {@code registerDefaultState}. See {@link WeatheringCopperBarsBlock}
 * for the shared weathering pattern.
 */
public class WeatheringCopperChainBlock extends TFCChainBlock implements WeatheringCopper
{
    private final WeatheringCopper.WeatherState age;
    private final ResourceLocation tfcItem;

    public WeatheringCopperChainBlock(final WeatheringCopper.WeatherState age, final ResourceLocation tfcItem, final BlockBehaviour.Properties properties)
    {
        super(properties);
        this.age = age;
        this.tfcItem = tfcItem;
    }

    @Override
    public void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random)
    {
        this.onRandomTick(state, level, pos, random);
    }

    @Override
    public boolean isRandomlyTicking(final BlockState state)
    {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    public WeatheringCopper.WeatherState getAge()
    {
        return this.age;
    }

    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target, final BlockGetter level, final BlockPos pos, final Player player)
    {
        return WeatheringClone.unaffectedOr(this.age, this.tfcItem, super.getCloneItemStack(state, target, level, pos, player));
    }
}
