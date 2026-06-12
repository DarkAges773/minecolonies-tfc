package com.structurizereplacements.integration.colony;

import com.structurizereplacements.placement.ChoiceResolver;

/**
 * Entry point and bridge holder for the colony-mod integration. The mod ctor (guarded by
 * {@code ModList.isLoaded}) calls {@link #init} with the matching fork's {@link ColonyBridge}; everything
 * in this package is fork-agnostic and routes its colony-API calls through {@link #bridge()}.
 *
 * <p>Single-slot by design (mirrors {@link ChoiceResolver}): the two forks can't coexist at runtime, and
 * the ctor initializes at most one.
 */
public final class ColonyIntegration
{
    private ColonyIntegration() {}

    private static volatile ColonyBridge bridge;

    public static void init(final ColonyBridge forkBridge)
    {
        bridge = forkBridge;
        ChoiceResolver.set(ColonyChoiceResolver::resolve);
        ColonyNetwork.register();
    }

    /** The installed fork bridge — null only when no colony mod is loaded (then nothing here is called). */
    public static ColonyBridge bridge()
    {
        return bridge;
    }
}
