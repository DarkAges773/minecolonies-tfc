package com.mctfc.forge;

/**
 * Lifecycle of one heat-forge <b>position</b> (one member block's {@code heat / output / overflow} slot triple). The
 * controller BE self-tick advances the "running" states; the tend-AI reacts to the holds. See
 * {@code docs/tfc-forge-multiblock.md} §6.
 *
 * <p>{@code deviceTemp} is shared across the whole controller, so "is this position running right now?" is
 * {@code deviceTemp ≥ input.requiredTemp}. {@link #COLD} splits by cause only for the AI's benefit — a position warming
 * up under adequate fuel is transiently {@code COLD} (self-clears as the device climbs), whereas fuel whose ceiling
 * can't reach the required temperature is genuinely {@code COLD} and never clears; the tend-AI prevents the latter by
 * gating loads on the ceiling.
 */
public enum ForgeState
{
    /** No input loaded. */
    EMPTY,
    /** Cooking 1 raw → 1 cooked (Cook / Chef). */
    HEATING,
    /** Melting ore → pouring metal into the output mold (Smelter). */
    ACCUMULATING,
    /** Output mold full → spilling into the overflow mold (Smelter). */
    CASTING,
    /** Loaded, but {@code deviceTemp < requiredTemp} — no progress, no consumption (warm-up or genuinely cold). */
    COLD,
    /** Metal ready to pour but no mold seated (Smelter) — waiting on the worker. */
    READY_NO_MOLD,
    /** Finished, but output + overflow are both full — waiting on a drain. */
    BLOCKED,
    /** Item has no heating recipe for this device — inert, the worker ejects it. */
    INVALID
}
