package com.mctfc.furnace;

import net.dries007.tfc.common.TFCTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Per-hut fuel restriction that reuses <b>TFC's own device fuel tags</b>, so each converted furnace hut accepts
 * exactly what the matching TFC device does — authentic, datapack-overridable, and automatically extended by addons
 * (FirmaLife, …) that tag their fuels:
 *
 * <ul>
 *   <li><b>Smeltery</b> ≈ TFC charcoal forge → {@code tfc:forge_fuel} ({@code #minecraft:coals} = coal, charcoal,
 *       and TFC's bituminous coal / lignite).</li>
 *   <li><b>Cook</b> (dining hall) ≈ TFC firepit → {@code tfc:firepit_fuel} ({@code #minecraft:logs} incl. TFC logs,
 *       plus peat, stick bundles, driftwood, …).</li>
 * </ul>
 *
 * Used both to scope each hut's fuel-list GUI ({@code MixinItemListModuleView}) and to enforce the restriction in
 * the behaviors' {@code fuelAllowed}. Other furnace huts aren't scoped ({@link #tagFor} returns {@code null}) and
 * keep the full TFC fuel list.
 */
public final class FurnaceFuelScope
{
    private FurnaceFuelScope() {}

    /** TFC charcoal-forge fuels (the coals) — the smeltery. */
    public static final TagKey<Item> SMELTER = TFCTags.Items.FORGE_FUEL;

    /** TFC firepit fuels (logs / peat / sticks / …) — the cook. */
    public static final TagKey<Item> COOK = TFCTags.Items.FIREPIT_FUEL;

    /** The fuel tag a furnace hut is restricted to, keyed by its building registry path; {@code null} if unscoped. */
    public static TagKey<Item> tagFor(final String buildingId)
    {
        return switch (buildingId)
        {
            case "smeltery" -> SMELTER;
            case "cook" -> COOK;
            default -> null;
        };
    }
}
