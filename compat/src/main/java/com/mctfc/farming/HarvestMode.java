package com.mctfc.farming;

import java.util.Locale;

/**
 * Per-field harvest policy for the MineColonies farmer working TFC crops. Chosen in the field
 * (scarecrow) GUI and stored on the {@code FarmField} (see {@code MixinFarmField}).
 *
 * <ul>
 *   <li>{@link #FRUITING} (default) — harvest crops at full growth (produce + 1 seed), and also collect
 *       any crop that has gone to seed (a mature {@code DeadCropBlock}). Produce-focused and seed-stable.</li>
 *   <li>{@link #SEEDING} — never harvest a live crop; let it ripen and die into its seeding stage, then
 *       harvest the dead/mature crop for the larger seed yield (no produce). Seed-focused.</li>
 * </ul>
 */
public enum HarvestMode
{
    FRUITING,
    SEEDING;

    /** Toggle order for the GUI button. */
    public HarvestMode next()
    {
        return this == FRUITING ? SEEDING : FRUITING;
    }

    /** Translation key for the toggle button label. */
    public String labelKey()
    {
        return "gui.mctfc.field.harvestmode." + name().toLowerCase(Locale.ROOT);
    }

    /** Translation key for the toggle button tooltip. */
    public String tooltipKey()
    {
        return labelKey() + ".tooltip";
    }
}
