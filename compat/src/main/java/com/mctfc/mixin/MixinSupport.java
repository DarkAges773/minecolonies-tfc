package com.mctfc.mixin;

import com.mctfc.collapse.BuildAreaSupport;
import net.dries007.tfc.util.Support;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Makes an active MineColonies build area read as TFC-supported, at the exact two points TFC consults supports — so a
 * worker's schematic can't cave in while it's being built, matching real support behaviour for both collapse paths with
 * no extra polling.
 *
 * <ul>
 *   <li><b>{@code isSupported}</b> (raw-rock random-tick collapse path, {@code RawRockBlock}) — return {@code true} for a
 *       position inside a build area.</li>
 *   <li><b>{@code findUnsupportedPositions}</b> (block-break / chisel path, {@code CollapseRecipe.tryTriggerCollapse}) —
 *       drop any returned position inside a build area, so no collapse starts there.</li>
 * </ul>
 *
 * Both TFC methods only run after TFC's own cheap probability gates, and the added cost is a point-in-AABB test over the
 * few active boxes (short-circuited entirely when nothing is building). {@code @Mixin(remap = false)} — TFC's own class.
 */
@Mixin(value = Support.class, remap = false)
public class MixinSupport
{
    @Inject(method = "isSupported", at = @At("HEAD"), cancellable = true)
    private static void mctfc$virtualSupport(final BlockGetter world, final BlockPos pos, final CallbackInfoReturnable<Boolean> cir)
    {
        if (BuildAreaSupport.isProtected(world, pos))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "findUnsupportedPositions", at = @At("RETURN"))
    private static void mctfc$filterUnsupported(final BlockGetter world, final BlockPos from, final BlockPos to, final CallbackInfoReturnable<Set<BlockPos>> cir)
    {
        if (BuildAreaSupport.isEmpty())
        {
            return;
        }
        final Set<BlockPos> result = cir.getReturnValue();
        if (result == null || result.isEmpty())
        {
            return;
        }
        result.removeIf(p -> BuildAreaSupport.isProtected(world, p));
    }
}
