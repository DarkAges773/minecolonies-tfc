package com.mctfc.mixin;

import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.mctfc.smelter.SmelterProcessing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a furnace <b>finish a TFC worker melt on its own</b>: when a furnace carrying an active
 * {@link FurnaceProcess} (phase {@code MELTING}) burns out its fuel ({@code litTime == 0}), it turns the ore +
 * mold in its slots into the finished casting in place (via {@link SmelterProcessing}) and flips to
 * {@code DONE}, so the worker only has to haul the result out later. The completion is furnace-driven, so it
 * happens the moment the flame dies (and resumes correctly after a reload) regardless of where the worker is.
 *
 * <p>Gated cheaply — it only touches a furnace that is unlit <i>and</i> has something in its input slot — so
 * idle furnaces pay almost nothing per tick.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinAbstractFurnaceBlockEntity
{
    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void mctfc$finishMelt(final Level level, final BlockPos pos, final BlockState state,
            final AbstractFurnaceBlockEntity furnace, final CallbackInfo ci)
    {
        if (((FurnaceBlockEntityAccessor) furnace).getLitTime() > 0 || furnace.getItem(0).isEmpty())
        {
            return;
        }
        final FurnaceProcess process = FurnaceProcessCapability.get(furnace);
        if (process == null || process.phase() != FurnaceProcess.Phase.MELTING)
        {
            return;
        }
        SmelterProcessing.complete(furnace);
        process.setPhase(FurnaceProcess.Phase.DONE);
    }
}
