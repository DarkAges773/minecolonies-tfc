package com.structurizereplacements.integration.slimcolonies;

/**
 * Entry point for the optional SlimColonies integration — the SlimColonies (no.monopixel.slimcolonies.*)
 * twin of {@code MineColoniesIntegration}. {@code StructurizeReplacements} calls {@link #init()} only when
 * {@code slimcolonies} is loaded — this class is the single place that touches SlimColonies-referencing
 * types, so none of them are classloaded when SlimColonies is absent.
 *
 * <p>What the integration adds on top of the base substitution engine: the builder/Build-Options choice
 * resolver ({@link BuildingChoiceResolver}, registered as the engine's {@code ChoiceResolver}) and the
 * per-building edit network channel ({@link ScNetwork}). The SlimColonies-targeting mixins live in the
 * separate {@code structurizereplacements.slimcolonies.mixins.json} config, which Mixin skips when the
 * target classes are absent.
 *
 * <p>SlimColonies is a MineColonies fork (forked before the {@code ICommonBuilding} API split): packages
 * moved to {@code no.monopixel.slimcolonies.*}, and {@code IColony#getBuildingManager()} (returning the
 * structure manager whose {@code getBuilding} yields {@code IBuilding} directly) replaces the newer
 * {@code getCommonBuildingManager()}/{@code ICommonBuilding} pair. Those are the only API deltas; the
 * shared engine pieces (ChoiceCodec, StagedChoices, the holder interfaces, the picker GUI) are fork-agnostic.
 */
public final class SlimColoniesIntegration
{
    private SlimColoniesIntegration() {}

    public static void init()
    {
        BuildingChoiceResolver.register();
        ScNetwork.register();
    }
}
