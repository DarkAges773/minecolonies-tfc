package com.mctfc.network;

import com.mctfc.farming.FarmFieldHarvestMode;
import com.mctfc.farming.HarvestMode;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.permissions.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: set the {@link HarvestMode} of the {@code FarmField} at {@code fieldPos}. The server
 * resolves the colony from the {@code (id, dimension)} the GUI sent, checks the sender may manage huts, finds
 * the matching field, sets the mode (persisted + re-synced via {@code MixinFarmField}'s serialization hooks),
 * and marks the extensions dirty so the change reaches clients. Mirrors MineColonies' own
 * {@code FarmFieldUpdateSeedMessage}, but on our channel ({@link McFarmingNetwork}).
 */
public class SetHarvestModeMessage
{
    private final ResourceKey<Level> dimension;
    private final int colonyId;
    private final BlockPos fieldPos;
    private final HarvestMode mode;

    public SetHarvestModeMessage(final ResourceKey<Level> dimension, final int colonyId,
            final BlockPos fieldPos, final HarvestMode mode)
    {
        this.dimension = dimension;
        this.colonyId = colonyId;
        this.fieldPos = fieldPos;
        this.mode = mode;
    }

    public SetHarvestModeMessage(final FriendlyByteBuf buf)
    {
        this.dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        this.colonyId = buf.readVarInt();
        this.fieldPos = buf.readBlockPos();
        this.mode = buf.readEnum(HarvestMode.class);
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeResourceLocation(dimension.location());
        buf.writeVarInt(colonyId);
        buf.writeBlockPos(fieldPos);
        buf.writeEnum(mode);
    }

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer sender = ctx.get().getSender();
            if (sender == null)
            {
                return;
            }
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, dimension);
            if (colony == null || !colony.getPermissions().hasPermission(sender, Action.MANAGE_HUTS))
            {
                return;
            }
            colony.getServerBuildingManager()
                    .getMatchingBuildingExtension(f -> f.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())
                            && f.getPosition().equals(fieldPos))
                    .ifPresent(field -> {
                        if (field instanceof FarmFieldHarvestMode holder)
                        {
                            holder.mctfc$setHarvestMode(mode);
                            colony.getServerBuildingManager().markBuildingExtensionsDirty();
                        }
                    });
        });
        ctx.get().setPacketHandled(true);
    }
}
