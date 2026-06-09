package com.mctfc.furnace;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Bridge handed to a {@link FurnaceBehavior} so it can drive a MineColonies furnace-using worker without
 * touching the AI's internals directly. Implemented by {@code MixinAbstractEntityAIUsesFurnace} on the live
 * AI, which exposes exactly the pieces a behavior needs — the worker/colony context, the building's furnace
 * positions, and the AI's own navigation/pacing primitives — so behaviors stay decoupled from MineColonies'
 * (deeply inherited, partly private) AI class hierarchy.
 *
 * <p>This is the seam that keeps the base mixin a thin dispatcher: new customised furnace workers are added as
 * {@link FurnaceBehavior} implementations registered in {@link FurnaceBehaviors}, never as more mixin code.
 */
public interface FurnaceWorker
{
    /** The citizen doing the work (also the route to world, colony, inventory, skills). */
    AbstractEntityCitizen worker();

    /** The worker's building (the Smeltery, Restaurant, …). */
    IBuilding building();

    /** The building's level (1–5) — drives the fuel heat bonus, so higher huts melt higher-tier metals. */
    int buildingLevel();

    /** The level the worker is in. */
    Level world();

    /** Positions of the (vanilla) furnaces registered to the building — the heat stations. */
    List<BlockPos> furnaces();

    /** Path toward the building's centre; {@code true} once arrived (mirrors the AI's own helper). */
    boolean gotoBuilding();

    /** Path toward {@code pos}; {@code true} once in working range. */
    boolean gotoWorkPos(BlockPos pos);

    /** Set ticks until this AI state ticks again (pacing for "cooking"/"melting" timers). */
    void delay(int ticks);

    /** The AI's current state (for handlers that need to stay put: {@code return ai.state()}). */
    IAIState state();
}
