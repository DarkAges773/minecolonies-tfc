package com.firmavanilla.block;

import com.firmavanilla.weathering.WeatheringClone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Copper bars that oxidise like vanilla copper — an {@link IronBarsBlock} that {@code implements WeatheringCopper}
 * (random-tick wiring copied from {@code WeatheringCopperFullBlock}). One of the {@code WeatheringCopper*Block}
 * family; see {@link com.firmavanilla.weathering.WeatheringMaps} for how the lifecycle is wired with no mixin and
 * {@link com.firmavanilla.block.CopperWeathering} for registration.
 */
public class WeatheringCopperBarsBlock extends IronBarsBlock implements WeatheringCopper
{
    private final WeatheringCopper.WeatherState age;
    /** The TFC item the bright (UNAFFECTED) stage stands in for (this stage has no item of its own). */
    private final ResourceLocation tfcItem;

    public WeatheringCopperBarsBlock(final WeatheringCopper.WeatherState age, final ResourceLocation tfcItem, final BlockBehaviour.Properties properties)
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
