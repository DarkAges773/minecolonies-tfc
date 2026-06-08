package com.mctfc.furnace;

import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;

import java.util.Collection;

/**
 * A pluggable, TFC-flavoured replacement for one MineColonies furnace-using worker's behaviour (Smelter,
 * Cook, …). The vanilla furnace loop (put smeltable + fuel in a furnace block entity, let it smelt, extract)
 * doesn't fit TFC, so a behavior replaces it wholesale: it supplies its own AI states and its own
 * {@link #startWorking()} entry, driving the worker through the {@link FurnaceWorker} bridge.
 *
 * <p>One behavior instance is created per AI (so it may hold per-worker progress, e.g. accumulated melt mB).
 * It is registered against its AI class in {@link FurnaceBehaviors}; {@code MixinAbstractEntityAIUsesFurnace}
 * installs it: it registers {@link #targets()} on the state machine and routes the vanilla
 * {@code startWorking} to {@link #startWorking()}.
 */
public interface FurnaceBehavior
{
    /**
     * The custom AI states this behavior runs (e.g. a MELTING/CASTING loop). Registered onto the worker's
     * state machine when the behavior is installed; the vanilla furnace states are simply never routed to.
     */
    Collection<AITarget<IAIState>> targets();

    /**
     * Replacement for the worker's {@code START_WORKING} handler — the behavior's loop entry. Returns the
     * next {@link IAIState} (one of its own {@link #targets()} states, a shared MineColonies state such as
     * gathering, or {@code ai.state()} to stay put).
     */
    IAIState startWorking();
}
