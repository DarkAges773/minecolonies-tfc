package com.firmavanilla.block;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.TFCWallTorchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wall-mounted soul torch — the wall twin of {@link SoulTorchBlock}, extending TFC's {@link TFCWallTorchBlock}.
 * Same 2× burn-out, but converts to TFC's {@code dead_wall_torch} carrying the wall facing over
 * ({@code withPropertiesOf}).
 */
public class SoulWallTorchBlock extends TFCWallTorchBlock
{
    public SoulWallTorchBlock(ExtendedProperties properties)
    {
        super(properties, ParticleTypes.SOUL_FIRE_FLAME);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        final Block dead = SoulTorches.deadWallTorch();
        SoulTorches.tryBurnOut(level, pos, dead != null ? dead.withPropertiesOf(state) : null);
    }
}
