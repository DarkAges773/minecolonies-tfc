package com.mctfc.farming;

/**
 * Duck-typing interface mixed into MineColonies' {@code FarmField} (see {@code MixinFarmField}) so the
 * per-field {@link HarvestMode} can be read/written by our code: the farmer AI (server, reads it to decide
 * harvest behaviour), the field GUI (client, shows/toggles it), and the network message (server, sets it).
 * Cast a {@code FarmField} to this to access the mode.
 */
public interface FarmFieldHarvestMode
{
    HarvestMode mctfc$getHarvestMode();

    void mctfc$setHarvestMode(HarvestMode mode);
}
