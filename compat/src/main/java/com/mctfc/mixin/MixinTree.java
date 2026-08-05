package com.mctfc.mixin;

import com.minecolonies.core.entity.ai.workers.util.Tree;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedList;

/**
 * Make the Lumberjack chop a tree from the <b>bottom</b> log up, not the top down. {@code Tree} sorts its
 * {@code woodBlocks} ascending by distance from the base, then hands them out from the <i>end</i>
 * ({@code peekNextLog}/{@code pollNextLog} = {@code peekLast}/{@code pollLast}) — i.e. the topmost log first. That's
 * vanilla's "work down so nothing floats" order, but it reads as nonsense once TFC whole-tree felling is in play
 * (see {@link MixinEntityAIWorkLumberjack}): the worker reaches for the <i>top</i> of the trunk to drop the whole
 * tree. We flip both ends to {@code peekFirst}/{@code pollFirst} so the worker cuts the base block — which is where
 * TFC's {@code shouldLog} fells from, and what a person would actually do.
 *
 * <p>peek and poll must flip <b>together</b>: peeking the base but polling the top would never remove the base and
 * loop forever. After felling, the remaining tracked logs are air and drain a tick each, base-closest first.
 * {@code remap = false}: MineColonies' own field and methods. {@code woodBlocks} is declared in {@code Tree} itself,
 * so shadowing it is safe (no inherited-field lookup).
 */
@Mixin(Tree.class)
public abstract class MixinTree
{
    @Shadow(remap = false)
    private LinkedList<BlockPos> woodBlocks;

    @Inject(method = "peekNextLog", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$peekBaseFirst(final CallbackInfoReturnable<BlockPos> cir)
    {
        if (!woodBlocks.isEmpty())
        {
            cir.setReturnValue(woodBlocks.peekFirst());
        }
    }

    @Inject(method = "pollNextLog", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$pollBaseFirst(final CallbackInfoReturnable<BlockPos> cir)
    {
        if (!woodBlocks.isEmpty())
        {
            cir.setReturnValue(woodBlocks.pollFirst());
        }
    }
}
