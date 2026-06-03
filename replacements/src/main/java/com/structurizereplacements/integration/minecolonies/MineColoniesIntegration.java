package com.structurizereplacements.integration.minecolonies;

/**
 * Entry point for the optional MineColonies integration. {@code StructurizeReplacements} calls
 * {@link #init()} only when {@code minecolonies} is loaded — this class is the single place that touches
 * MineColonies-referencing types, so none of them are classloaded when MineColonies is absent (the
 * standalone case).
 *
 * <p>What the integration adds on top of the base substitution engine: the builder/Build-Options choice
 * resolver ({@link BuildingChoiceResolver}, registered as the engine's {@code ChoiceResolver}) and the
 * per-building edit network channel ({@link McNetwork}). The MineColonies-targeting mixins live in the
 * separate {@code structurizereplacements.minecolonies.mixins.json} config, which Mixin skips when the
 * target classes are absent.
 */
public final class MineColoniesIntegration
{
    private MineColoniesIntegration() {}

    public static void init()
    {
        BuildingChoiceResolver.register();
        McNetwork.register();
    }
}
