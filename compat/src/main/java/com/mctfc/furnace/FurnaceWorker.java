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

    /**
     * Distinct heat-forge <b>controller</b> positions registered to the building (one per multiblock), from the grafted
     * {@code ForgeUserModule}. The tend-AI drives these instead of vanilla furnaces once a worker is retargeted to the
     * forge (see {@code docs/tfc-forge-multiblock.md} §13). Empty if the hut has no forge module / no forges.
     */
    List<BlockPos> controllers();

    /** Path toward the building's centre; {@code true} once arrived (mirrors the AI's own helper). */
    boolean gotoBuilding();

    /** Path toward {@code pos}; {@code true} once in working range. */
    boolean gotoWorkPos(BlockPos pos);

    /** Set ticks until this AI state ticks again (pacing for "cooking"/"melting" timers). */
    void delay(int ticks);

    /**
     * Count one completed work action: bumps the citizen's actions-done (which, past
     * {@link FurnaceBehavior#actionsUntilDump()}, makes MineColonies' standard dump cycle ship the carried
     * outputs to the building/warehouse) and decrements saturation (the worker gets hungry from working).
     * Call once per delivered output.
     */
    void countAction();

    /** The AI's current state (for handlers that need to stay put: {@code return ai.state()}). */
    IAIState state();

    /**
     * Run the worker's own {@code checkForImportantJobs} pre-check and return where it wants to go — or
     * {@code START_WORKING} if it has nothing more pressing than its furnace work. Furnace workers with extra
     * duties override this in MineColonies (the Cook serves food / fetches it to serve); a behavior calls this
     * first so those duties still run even though it has replaced the vanilla furnace loop. For workers with no
     * override (the Smelter) it simply returns {@code START_WORKING}.
     */
    IAIState checkImportantJobs();
}
