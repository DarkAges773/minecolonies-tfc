package com.mctfc.mixin;

import com.mctfc.cook.CookProcessing;
import com.mctfc.furnace.FurnaceHeating;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.mctfc.furnace.FurnaceProcessings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a furnace <b>finish a TFC worker operation on its own</b>: when a furnace carrying an active
 * {@link FurnaceProcess} (phase {@code MELTING}) burns out its fuel ({@code litTime == 0}), it turns the inputs in
 * its slots into the finished result in place — via the {@link FurnaceProcessings completer} for the operation's
 * {@code kind} (the smelter's casting, the cook's food heating, …) — and flips to {@code DONE}, so the worker only
 * has to haul the result out later. The completion is furnace-driven, so it happens the moment the flame dies (and
 * resumes correctly after a reload) regardless of where the worker is.
 *
 * <p>Gated cheaply — it only touches a furnace that is unlit <i>and</i> has something in its input slot — so
 * idle furnaces pay almost nothing per tick.
 *
 * <p>A <b>cook</b> loaded with more than one raw piece (the Kitchen Chef fulfilling a request) keeps the flame
 * going one piece at a time: after a completion, if raw food remains it re-ignites in place from the furnace's own
 * fuel slot ({@link FurnaceHeating#igniteInPlace}) rather than going {@code DONE}, so throughput comes from
 * parallel furnaces (like a TFC grill), not from cooking a whole stack in one item's time. The same path recovers
 * a batch that stalled {@code DONE} when its fuel ran out, once fuel returns. The dining-hall Cook always loads
 * exactly one, so its furnaces cook empty and this never fires for them.
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
        if (process == null)
        {
            return;
        }
        if (process.phase() == FurnaceProcess.Phase.MELTING)
        {
            FurnaceProcessings.complete(process.kind(), furnace);
            process.setPhase(FurnaceProcess.Phase.DONE);
        }
        // (Re-)ignite a cook that still has raw food loaded: the next piece of a multi-item batch, or a batch that
        // stalled DONE when its fuel ran out and now has fuel again. Only a cook with leftover input re-lights — a
        // finished single-item cook (input empty) is left DONE for the worker to unload.
        if (process.phase() == FurnaceProcess.Phase.DONE
              && CookProcessing.KIND.equals(process.kind())
              && !furnace.getItem(0).isEmpty())
        {
            FurnaceHeating.igniteInPlace(furnace, process, 0, FurnaceHeating.COOK_FUEL);
        }
    }
}
