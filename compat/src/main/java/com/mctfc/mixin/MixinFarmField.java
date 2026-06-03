package com.mctfc.mixin;

import com.mctfc.farming.FarmFieldHarvestMode;
import com.mctfc.farming.HarvestMode;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stores the per-field {@link HarvestMode} on MineColonies' {@code FarmField} and carries it through both
 * serialization paths the class already has — NBT ({@code serializeNBT}/{@code deserializeNBT}, colony save)
 * and the {@code FriendlyByteBuf} ({@code serialize}/{@code deserialize}, client sync). The same class is used
 * server- and client-side, so the buffer hooks make the mode available to the field GUI; the NBT hooks persist
 * it. The buffer hooks append/consume the mode last (both at {@code TAIL}), keeping the stream aligned with the
 * base seed/radii/stage payload. {@code remap = false}: these are MineColonies' own methods.
 */
@Mixin(value = FarmField.class, remap = false)
public abstract class MixinFarmField implements FarmFieldHarvestMode
{
    @Unique
    private static final String MCTFC_MODE_KEY = "mctfc_harvest_mode";

    @Unique
    private HarvestMode mctfc$harvestMode = HarvestMode.FRUITING;

    @Override
    public HarvestMode mctfc$getHarvestMode()
    {
        return mctfc$harvestMode;
    }

    @Override
    public void mctfc$setHarvestMode(final HarvestMode mode)
    {
        mctfc$harvestMode = mode == null ? HarvestMode.FRUITING : mode;
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void mctfc$writeModeNbt(final CallbackInfoReturnable<CompoundTag> cir)
    {
        cir.getReturnValue().putString(MCTFC_MODE_KEY, mctfc$harvestMode.name());
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void mctfc$readModeNbt(final CompoundTag compound, final CallbackInfo ci)
    {
        mctfc$harvestMode = mctfc$parse(compound.contains(MCTFC_MODE_KEY) ? compound.getString(MCTFC_MODE_KEY) : null);
    }

    @Inject(method = "serialize(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void mctfc$writeModeBuf(final FriendlyByteBuf buf, final CallbackInfo ci)
    {
        buf.writeEnum(mctfc$harvestMode);
    }

    @Inject(method = "deserialize(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"))
    private void mctfc$readModeBuf(final FriendlyByteBuf buf, final CallbackInfo ci)
    {
        mctfc$harvestMode = buf.readEnum(HarvestMode.class);
    }

    @Unique
    private static HarvestMode mctfc$parse(final String name)
    {
        if (name == null)
        {
            return HarvestMode.FRUITING;
        }
        try
        {
            return HarvestMode.valueOf(name);
        }
        catch (final IllegalArgumentException e)
        {
            return HarvestMode.FRUITING;
        }
    }
}
