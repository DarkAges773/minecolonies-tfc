package com.mctfc.furnace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry of {@link FurnaceBehavior}s, keyed by the MineColonies furnace-worker AI class they replace
 * (e.g. {@code EntityAIWorkSmelter}). {@code MixinAbstractEntityAIUsesFurnace} consults this once per AI it
 * constructs: if a factory is registered for that exact AI class, it builds a behavior (handed the
 * {@link FurnaceWorker} bridge) and installs it; otherwise the worker keeps its vanilla furnace behaviour.
 *
 * <p>This is the single extension point — adding a TFC-customised furnace worker is "implement a behavior +
 * register it here", with no further mixin work. Register from the mod's setup
 * ({@code MineColoniesTFC} ctor), before any AI is created.
 */
public final class FurnaceBehaviors
{
    private FurnaceBehaviors() {}

    /** AI class → factory producing a fresh behavior for one worker, given its bridge. */
    private static final Map<Class<?>, Function<FurnaceWorker, FurnaceBehavior>> FACTORIES = new ConcurrentHashMap<>();

    /** Register a behavior factory for an AI class (e.g. {@code EntityAIWorkSmelter.class}). */
    public static void register(final Class<?> aiClass, final Function<FurnaceWorker, FurnaceBehavior> factory)
    {
        FACTORIES.put(aiClass, factory);
    }

    /**
     * Create the behavior for a freshly-constructed AI, or {@code null} if that AI class has none registered
     * (keep vanilla). Keyed by exact class so a subclass never inherits an unintended behavior.
     */
    public static FurnaceBehavior create(final Object ai, final FurnaceWorker bridge)
    {
        final Function<FurnaceWorker, FurnaceBehavior> factory = FACTORIES.get(ai.getClass());
        return factory == null ? null : factory.apply(bridge);
    }
}
