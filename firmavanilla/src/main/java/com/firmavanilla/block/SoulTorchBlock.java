package com.firmavanilla.block;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.TFCTorchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Standing soul torch: TFC's {@link TFCTorchBlock} (burn-out machinery, tick-counter BE, placement reset) handed
 * vanilla's soul-fire flame particle, but burning {@link SoulTorches#BURN_MULT}× longer before turning into TFC's
 * {@code dead_torch}. Only {@link #randomTick} differs from TFC's torch — the burn-out threshold is doubled and the
 * conversion targets the dead torch resolved by {@link SoulTorches}.
 */
public class SoulTorchBlock extends TFCTorchBlock
{
    public SoulTorchBlock(ExtendedProperties properties)
    {
        super(properties, ParticleTypes.SOUL_FIRE_FLAME);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        // Replaces TFC's 1× burn-out with our 2× one (don't call super, which would burn out at the normal rate).
        final Block dead = SoulTorches.deadTorch();
        SoulTorches.tryBurnOut(level, pos, dead != null ? dead.defaultBlockState() : null);
    }
}
