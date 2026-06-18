package com.firmavanilla.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A vanilla {@link BarrelBlock} that backs itself with {@link BarrelBlockEntityFV} (TFC's small-chest rules)
 * instead of the vanilla 27-slot one. Everything else — the {@code facing}/{@code open} states, the open/close
 * animation + sounds (driven by the inherited opener counter), the use/menu path — is plain vanilla barrel
 * behaviour. {@code newBlockEntity} is the only override.
 */
public class BarrelBlockFV extends BarrelBlock
{
    public BarrelBlockFV(final Properties properties)
    {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state)
    {
        return new BarrelBlockEntityFV(pos, state);
    }
}
