package com.mctfc.mixin;

import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the two protected fields on {@link AbstractTileEntityRack} that mark a rack as colony-owned, so the
 * inner-class {@code MixinRackInventory} can decide whether to preserve food. {@code remap = false} — these are
 * MineColonies' own fields, not Minecraft SRG names.
 */
@Mixin(AbstractTileEntityRack.class)
public interface AbstractTileEntityRackAccessor
{
    @Accessor(value = "inWarehouse", remap = false)
    boolean mctfc$inWarehouse();

    @Accessor(value = "buildingPos", remap = false)
    BlockPos mctfc$buildingPos();
}
