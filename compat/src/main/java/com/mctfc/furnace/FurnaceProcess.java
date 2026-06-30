package com.mctfc.furnace;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Per-furnace process metadata for a TFC furnace worker, attached as a capability to the vanilla furnace block
 * entity (see {@link FurnaceProcessCapability}) and serialised into the furnace's NBT.
 *
 * <p>The <b>work items live in the vanilla furnace's own slots</b> (ore in input, fuel in the fuel slot, the
 * mold in the result slot — filled in place on completion) so they drop when the furnace is broken and are
 * visible in the furnace GUI; the <b>melt timer + flame is the vanilla {@code litTime}</b>. The only thing that
 * can't ride along in those is the <b>carried fuel pool</b> — how much burn is left in the fuel currently being
 * consumed and its temperature — which lets one fuel piece span several operations. That's all this holds, so it
 * too persists across reload.
 */
public class FurnaceProcess implements INBTSerializable<CompoundTag>
{
    /** Lifecycle of a worker operation in this furnace. */
    public enum Phase
    {
        /** No operation — the furnace is free to load. */
        IDLE,
        /** Loaded and cooking; the furnace finishes it autonomously when {@code litTime} hits 0. */
        MELTING,
        /** Finished — the result sits in the furnace's result slot, waiting for the worker to haul it out. */
        DONE
    }

    private Phase phase = Phase.IDLE;
    /** Remaining burn (ticks) of the furnace's carried fuel — lets one fuel piece span several operations. */
    private int fuelTicks;
    /** Temperature (°C) of the carried fuel, so we know whether it's hot enough for the next operation. */
    private float fuelTemp;
    /** Which kind of operation is loaded — the key into {@link FurnaceProcessings} that finishes it (e.g. the
     * smelter's casting vs. the cook's food heating). Empty until a worker loads the furnace; an empty/unknown
     * kind falls back to the first-registered completer (back-compat for pre-{@code kind} saves). */
    private String kind = "";

    public Phase phase()
    {
        return phase;
    }

    public void setPhase(final Phase phase)
    {
        this.phase = phase;
    }

    public String kind()
    {
        return kind;
    }

    public void setKind(final String kind)
    {
        this.kind = kind == null ? "" : kind;
    }

    public int fuelTicks()
    {
        return fuelTicks;
    }

    public float fuelTemp()
    {
        return fuelTemp;
    }

    public FurnaceFuel.Burn pool()
    {
        return new FurnaceFuel.Burn(fuelTicks, fuelTemp);
    }

    public void setPool(final FurnaceFuel.Burn pool)
    {
        this.fuelTicks = Math.max(0, pool.ticks());
        this.fuelTemp = pool.temp();
    }

    @Override
    public CompoundTag serializeNBT()
    {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("Phase", (byte) phase.ordinal());
        tag.putInt("FuelTicks", fuelTicks);
        tag.putFloat("FuelTemp", fuelTemp);
        tag.putString("Kind", kind);
        return tag;
    }

    @Override
    public void deserializeNBT(final CompoundTag tag)
    {
        final byte ordinal = tag.getByte("Phase");
        phase = ordinal >= 0 && ordinal < Phase.values().length ? Phase.values()[ordinal] : Phase.IDLE;
        fuelTicks = tag.getInt("FuelTicks");
        fuelTemp = tag.getFloat("FuelTemp");
        kind = tag.getString("Kind");
    }
}
