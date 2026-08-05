package com.structurizereplacements.integration.colony;

import com.structurizereplacements.placement.ChoiceResolver;
import net.neoforged.bus.api.IEventBus;

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

    /**
     * @param modBus the mod event bus — NeoForge registers network payloads through a mod-bus event, so the
     *               bus has to be threaded down here from the mod ctor (on Forge this was a direct call).
     */
    public static void init(final ColonyBridge forkBridge, final IEventBus modBus)
    {
        bridge = forkBridge;
        ChoiceResolver.set(ColonyChoiceResolver::resolve);
        ColonyNetwork.register(modBus);
    }

    /** The installed fork bridge — null only when no colony mod is loaded (then nothing here is called). */
    public static ColonyBridge bridge()
    {
        return bridge;
    }

    /**
     * Whether the loaded colony mod needs the engine's DO-material compensation (see
     * {@link ColonyBridge#placementIgnoresDoMaterials}). False when no colony mod is loaded — Structurize's
     * own DO handler compares materials natively.
     */
    public static boolean placementIgnoresDoMaterials()
    {
        final ColonyBridge b = bridge;
        return b != null && b.placementIgnoresDoMaterials();
    }
}
