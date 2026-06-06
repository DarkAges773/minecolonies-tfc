package com.mctfc.mixin;

import com.mctfc.light.ColonyLights;
import net.dries007.tfc.common.blockentities.LampBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a TFC metal lamp ({@code tfc:metal/lamp/<metal>}) from draining its fluid fuel — and so from going dark —
 * while it sits inside a MineColonies colony (see {@link ColonyLights}). The lamp meters fuel use in
 * {@code LampBlockEntity#checkHasRanOut} (called from its random tick, and from {@code use}/{@code fluidTankChanged}):
 * it computes {@code floor(elapsedTicks / burnRate)} mB to drain and sets {@code LIT=false} when the tank empties.
 * Cancelling that method at HEAD when in a colony means no drain ever happens, so a lit lamp stays lit with no
 * refuelling. (It only freezes an already-lit lamp; it won't light an empty/unlit one.)
 *
 * <p>{@code @Mixin(remap = false)} — {@code checkHasRanOut} is TFC's own method. {@code getLevel()}/{@code getBlockPos()}
 * are reached by casting {@code this} to vanilla {@code BlockEntity} (the lamp BE is one), called as plain Java.
 */
@Mixin(value = LampBlockEntity.class, remap = false)
public abstract class MixinLampBlockEntity
{
    @Inject(method = "checkHasRanOut", at = @At("HEAD"), cancellable = true)
    private void mctfc$keepColonyLampLit(final CallbackInfo ci)
    {
        final BlockEntity be = (BlockEntity) (Object) this;
        if (ColonyLights.keepLit(be.getLevel(), be.getBlockPos()))
        {
            ci.cancel();
        }
    }
}
