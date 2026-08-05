package com.mctfc.mixin;

import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives herder workers a small random chance to do a <b>full unload</b> at the hut, on top of the standard dump
 * triggers (inventory physically full, or {@code actionsDone >= ACTIONS_FOR_DUMP}). Without it, a herd that trickles
 * out the odd product — a single milking, a shear, one cull — can sit on those items for a long time when it never
 * fills its inventory or reaches the action threshold. The chance is rolled through MineColonies' own
 * {@code wantInventoryDumped()} hook, which {@code inventoryNeedsDump()} polls every 100 ticks (~5s) as a
 * {@code STATE_BLOCKING} event guarded by {@code canBeInterrupted()} — so the unload only fires at a safe point.
 *
 * <p>Scoped to herders: {@code wantInventoryDumped} lives on the shared {@code AbstractEntityAIBasic}, so we gate on
 * {@code this instanceof AbstractEntityAIHerder} to leave every other worker untouched. {@code remap = false} —
 * MineColonies' own method.
 */
@Mixin(value = AbstractEntityAIBasic.class, remap = false)
public abstract class MixinAbstractEntityAIBasic
{
    /**
     * Per-poll (~5s) chance a herder triggers a full unload. Small on purpose — at 0.05 a herd averages a delivery
     * roughly every minute or two of otherwise-idle time, without constant hut trips.
     */
    private static final double HERDER_RANDOM_UNLOAD_CHANCE = 0.05;

    @Inject(method = "wantInventoryDumped", at = @At("HEAD"), cancellable = true)
    private void mctfc$herderRandomUnload(final CallbackInfoReturnable<Boolean> cir)
    {
        if ((Object) this instanceof AbstractEntityAIHerder && ColonyConstants.rand.nextDouble() < HERDER_RANDOM_UNLOAD_CHANCE)
        {
            cir.setReturnValue(true);
        }
    }
}
