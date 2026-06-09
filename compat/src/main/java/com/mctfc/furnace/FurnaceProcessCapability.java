package com.mctfc.furnace;

import com.mctfc.MineColoniesTFC;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers {@link FurnaceProcess} as a Forge capability and attaches one to every vanilla
 * {@link FurnaceBlockEntity} (the only furnace MineColonies' furnace workers use). The capability serializes
 * with the furnace BE's NBT, so in-progress operations persist across reload; it is attached only to plain
 * furnaces (not smokers/blast furnaces) and is never exposed on the block faces, so automation can't reach it.
 */
public final class FurnaceProcessCapability
{
    public static final Capability<FurnaceProcess> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(MineColoniesTFC.MODID, "furnace_process");

    private FurnaceProcessCapability() {}

    /** Wire the capability registration (mod bus) and the per-furnace attachment (Forge bus). */
    public static void init(final IEventBus modBus)
    {
        modBus.addListener(FurnaceProcessCapability::onRegister);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, FurnaceProcessCapability::onAttach);
    }

    /** The process store for a furnace, or {@code null} if {@code be} isn't a capable furnace. */
    @Nullable
    public static FurnaceProcess get(@Nullable final BlockEntity be)
    {
        return be == null ? null : be.getCapability(CAPABILITY).orElse(null);
    }

    private static void onRegister(final RegisterCapabilitiesEvent event)
    {
        event.register(FurnaceProcess.class);
    }

    private static void onAttach(final AttachCapabilitiesEvent<BlockEntity> event)
    {
        if (event.getObject() instanceof FurnaceBlockEntity)
        {
            event.addCapability(ID, new Provider());
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final FurnaceProcess process = new FurnaceProcess();
        private final LazyOptional<FurnaceProcess> opt = LazyOptional.of(() -> process);

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull final Capability<T> cap, @Nullable final Direction side)
        {
            return cap == CAPABILITY ? opt.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return process.serializeNBT();
        }

        @Override
        public void deserializeNBT(final CompoundTag nbt)
        {
            process.deserializeNBT(nbt);
        }
    }
}
