package com.mctfc.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the vanilla furnace's private burn fields, so a TFC furnace worker can drive the <b>flame</b>
 * directly: setting {@code litTime} (and {@code litDuration} for the flame-progress overlay) lights the furnace
 * while an operation cooks. The flame is cosmetic — the authoritative timer is the worker's
 * {@link com.mctfc.furnace.FurnaceProcess#doneTick()} — and the vanilla BE leaves our work alone (TFC ore has
 * no vanilla smelting recipe, so it only counts {@code litTime} down).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceBlockEntityAccessor
{
    @Accessor("litTime")
    int getLitTime();

    @Accessor("litTime")
    void setLitTime(int litTime);

    @Accessor("litDuration")
    void setLitDuration(int litDuration);
}
