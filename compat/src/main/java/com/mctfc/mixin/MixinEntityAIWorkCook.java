package com.mctfc.mixin;

import com.mctfc.Config;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkCook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Stops the dining hall's Waiter from hoarding a full stack of perishable food in its own inventory.
 *
 * <p>{@code EntityAIWorkCook#checkForImportantJobs} pulls food from the building into the worker's inventory in
 * {@code STACKSIZE} (64) batches ({@code needsCurrently = new Tuple<>(predicate, STACKSIZE)}, twice — for citizens
 * and for players). The worker's inventory is <b>not</b> a colony rack, so the colony food-preservation trait
 * doesn't protect it — anything the Waiter over-carries decays in hand. We cap that gather at
 * {@code Config.diningHallWorkerCarry} (default 16).
 *
 * <p>{@code STACKSIZE} is a compile-time constant, so it's inlined as the literal {@code 64} — the only {@code 64}
 * in {@code checkForImportantJobs}, so this {@code @ModifyConstant} hits exactly the two gather sites.
 * {@code remap = false}: MineColonies' own class.
 */
@Mixin(value = EntityAIWorkCook.class, remap = false)
public abstract class MixinEntityAIWorkCook
{
    @ModifyConstant(method = "checkForImportantJobs", constant = @Constant(intValue = 64))
    private int mctfc$capWaiterCarry(final int original)
    {
        return Config.diningHallWorkerCarry;
    }
}
