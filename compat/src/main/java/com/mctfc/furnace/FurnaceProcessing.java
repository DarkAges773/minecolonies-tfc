package com.mctfc.furnace;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * The <b>completion</b> step for one kind of TFC furnace operation — what turns the loaded inputs in a furnace's
 * slots into the finished result when its {@code litTime} burns out. Run by the furnace itself (via
 * {@code MixinAbstractFurnaceBlockEntity}), not the worker, so a melt/cook finishes the moment the flame dies and
 * resumes correctly after a reload, wherever the worker happens to be.
 *
 * <p>One implementation per converted hut (the smelter casts; the cook heats food). They are registered against a
 * {@code kind} string in {@link FurnaceProcessings}; the kind is stamped into the furnace's {@link FurnaceProcess}
 * cap when the worker loads it, so the right completer is selected even across a reload. Adding a converted hut is
 * "a behavior + a completer + two registration lines" — no new mixin.
 */
public interface FurnaceProcessing
{
    /**
     * Finish the operation loaded in this furnace, placing the result in its result slot (and consuming the input).
     * Called once, when the furnace's flame has just died on an operation this completer's kind owns.
     */
    void complete(AbstractFurnaceBlockEntity furnace);
}
