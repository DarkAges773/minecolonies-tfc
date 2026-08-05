package com.firmavanilla.block;

import com.firmavanilla.weathering.WeatheringClone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.HitResult;

/**
 * Trapdoor weathering copper (TFC's copper trapdoor). A metal trapdoor — {@link BlockSetType#IRON} (no hand-open,
 * redstone only), like TFC's. See {@link WeatheringCopperBarsBlock} for the shared pattern.
 */
public class WeatheringCopperTrapDoorBlock extends TrapDoorBlock implements WeatheringCopper
{
    private final WeatheringCopper.WeatherState age;
    private final ResourceLocation tfcItem;

    public WeatheringCopperTrapDoorBlock(final WeatheringCopper.WeatherState age, final ResourceLocation tfcItem, final BlockBehaviour.Properties properties)
    {
        super(properties, BlockSetType.IRON);
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
