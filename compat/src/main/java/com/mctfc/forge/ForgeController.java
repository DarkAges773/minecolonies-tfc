package com.mctfc.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Worker-facing façade of a heat-forge multiblock (see {@code docs/tfc-forge-multiblock.md} §11). The tend-AI (Smelter /
 * Cook) and the Chef driver program against <b>this and nothing else</b>; the {@link HeatForgeBlockEntity} controller
 * implements it and self-ticks the actual processing (burn shared fuel → climb {@code deviceTemp} → advance every
 * runnable position → surface holds), so the worker only lights, feeds, seats molds, and drains.
 *
 * <p>All per-position methods take a member {@link BlockPos} (one of {@link #members()}); the controller-level methods
 * (lit / fuel / temperature) act on the shared device.
 */
public interface ForgeController
{
    // --- shared device: lit state + keep-warm + fuel + temperature ------------------------------------------

    /** Whether the device is currently burning. */
    boolean isLit();

    /** Light the device (the worker lights it <b>before</b> loading; it then warms up while it runs). No-op if lit. */
    void light();

    /** Explicitly stop the burn, retaining the unburned fuel (the worker's deliberate stop after keep-warm). */
    void extinguish();

    /** Game-time of the last completed operation (a stored timestamp, persisted) — the keep-warm reference. */
    long lastActiveTick();

    /** Whether the shared 5-slot fuel column has room for more fuel at its top. */
    boolean needsFuel();

    /** Add fuel to the <b>top</b> of the fixed 5-slot column (only the bottom slot burns); returns whether all fit. */
    boolean addFuel(ItemStack stack);

    /** The <b>live</b> device temperature (°C) — for rendering/timing only; climbs gradually toward the fuel ceiling. */
    float deviceTemp();

    /**
     * Feasibility: whether the fuel <b>ceiling</b> (hottest available fuel + level bonus) is ≥ {@code requiredTemp}. The
     * tend-AI gates loads on this, <b>not</b> the live {@link #deviceTemp()} — so it loads freely into a cold, just-lit
     * forge and the warm-up is absorbed as longer craft time.
     */
    boolean canReach(float requiredTemp);

    /** The per-building heat bonus (°C) the worker's hut level grants; applied to the burning fuel's temperature. */
    void setLevelBonus(int bonusCelsius);

    // --- per-position input / state / output ---------------------------------------------------------------

    /** How many member heat slots are currently empty (accept a load). */
    int freeHeatSlots();

    /** Load one heatable item into the first free heat slot (1 per slot); returns whether it was placed. */
    boolean loadInput(ItemStack stack);

    /** The lifecycle state of the position at {@code pos} (a member block). */
    ForgeState state(BlockPos pos);

    /** Drain <b>all</b> finished output (cooked food / cast-ready molds) from every position; returns what was removed. */
    List<ItemStack> takeFinished();

    /** Remove and return every {@link ForgeState#INVALID} heat-slot item (the worker stows them). */
    List<ItemStack> takeUnprocessable();

    // --- membership ----------------------------------------------------------------------------------------

    /** The member positions of this controller (includes the controller's own position), lowest-{@code BlockPos}-first. */
    List<BlockPos> members();

    /** The controller's own position (the group's lowest {@link BlockPos}). */
    BlockPos controllerPos();
}
