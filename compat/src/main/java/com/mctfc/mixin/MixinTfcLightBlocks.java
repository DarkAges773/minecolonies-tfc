package com.mctfc.mixin;

import com.mctfc.light.ColonyLights;
import net.dries007.tfc.common.blocks.TFCCandleBlock;
import net.dries007.tfc.common.blocks.TFCCandleCakeBlock;
import net.dries007.tfc.common.blocks.TFCTorchBlock;
import net.dries007.tfc.common.blocks.TFCWallTorchBlock;
import net.dries007.tfc.common.blocks.devices.JackOLanternBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops TFC's timer-based light sources from burning out while they sit inside a MineColonies colony, so colonies
 * stay lit (see {@link ColonyLights} for the in-colony gate + config). Each of these blocks expires on a server
 * {@code randomTick} backed by a {@code TickCounterBlockEntity}: torch / wall torch turn into a dead torch,
 * candle / candle cake go out, jack-o'-lantern reverts to a carved pumpkin. They all override the same
 * {@code randomTick(BlockState, ServerLevel, BlockPos, RandomSource)}, so one multi-target {@code @Inject} at HEAD
 * cancels the whole tick (burn-out and, for candles, the rain-extinguish too) when the position is in a colony.
 *
 * <p>{@code randomTick} is a vanilla {@code Block} method overridden by TFC, so it IS remapped (the default) — unlike
 * the metal-lamp hook ({@code MixinLampBlockEntity}), which targets TFC's own {@code checkHasRanOut} with
 * {@code remap = false}. Random ticks are infrequent, so the per-tick colony lookup is negligible.
 */
@Mixin({TFCTorchBlock.class, TFCWallTorchBlock.class, TFCCandleBlock.class, TFCCandleCakeBlock.class, JackOLanternBlock.class})
public abstract class MixinTfcLightBlocks
{
    @Inject(
            method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mctfc$keepColonyLightLit(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random, final CallbackInfo ci)
    {
        if (ColonyLights.keepLit(level, pos))
        {
            ci.cancel();
        }
    }
}
