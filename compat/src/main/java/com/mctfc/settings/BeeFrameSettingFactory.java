package com.mctfc.settings;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.factory.FactoryVoidInput;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.util.constant.TypeConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * (De)serialization factory for {@link BeeFrameSetting}. Every MineColonies setting must round-trip through a
 * factory registered with the {@code StandardFactoryController} (NBT for persistence, buffer for server→client
 * sync) — without it, saving/syncing a building carrying this setting throws.
 *
 * <p>Registered once in {@code MineColoniesTFC} common setup. The serialization id is in a <b>mctfc-private</b>
 * range, well clear of MineColonies' own ids (0–60), so the controller's "two factories with the same
 * serialization id" guard never trips. Registered unconditionally (the setting names no FirmaLife type), so a
 * world that once had FirmaLife can still load a saved Beekeeper after FirmaLife is removed — the setting just
 * goes inert.
 */
public class BeeFrameSettingFactory implements IFactory<FactoryVoidInput, BeeFrameSetting>
{
    /** mctfc-private serialization id (MineColonies uses 0–60). */
    private static final short SERIALIZATION_ID = (short) 9201;

    private static final String TAG_VALUE = "value";

    @NotNull
    @Override
    public TypeToken<BeeFrameSetting> getFactoryOutputType()
    {
        return TypeToken.of(BeeFrameSetting.class);
    }

    @NotNull
    @Override
    public TypeToken<FactoryVoidInput> getFactoryInputType()
    {
        return TypeConstants.FACTORYVOIDINPUT;
    }

    @Override
    public short getSerializationId()
    {
        return SERIALIZATION_ID;
    }

    @NotNull
    @Override
    public BeeFrameSetting getNewInstance(
      @NotNull final IFactoryController factoryController,
      @NotNull final FactoryVoidInput input,
      @NotNull final Object... context)
    {
        if (context.length < 1 || !(context[0] instanceof Integer))
        {
            throw new IllegalArgumentException("BeeFrameSetting requires a single Integer mask context parameter.");
        }
        return new BeeFrameSetting((Integer) context[0]);
    }

    @NotNull
    @Override
    public CompoundTag serialize(@NotNull final IFactoryController controller, @NotNull final BeeFrameSetting storage)
    {
        final CompoundTag compound = new CompoundTag();
        compound.putInt(TAG_VALUE, storage.getValue());
        return compound;
    }

    @NotNull
    @Override
    public BeeFrameSetting deserialize(@NotNull final IFactoryController controller, @NotNull final CompoundTag nbt)
    {
        return new BeeFrameSetting(nbt.getInt(TAG_VALUE));
    }

    @Override
    public void serialize(@NotNull final IFactoryController controller, @NotNull final BeeFrameSetting input, final FriendlyByteBuf buffer)
    {
        buffer.writeByte(input.getValue());
    }

    @NotNull
    @Override
    public BeeFrameSetting deserialize(@NotNull final IFactoryController controller, @NotNull final FriendlyByteBuf buffer)
    {
        return new BeeFrameSetting(buffer.readByte());
    }
}
