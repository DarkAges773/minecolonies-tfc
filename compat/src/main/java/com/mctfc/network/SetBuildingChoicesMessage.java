package com.mctfc.network;

import com.mctfc.builder.ChoiceCodec;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → server: set the replacement choices for a single building (the Build Options "Replace" GUI).
 * The server resolves the colony/building at {@code buildingPos} from the sender's level, applies the
 * choices, and {@code markDirty()}s the building (persists to colony NBT + re-syncs the view). Per-building
 * only — does not touch the player's global session picks.
 */
public class SetBuildingChoicesMessage
{
    private final BlockPos buildingPos;
    private final Map<Block, Block> choices;

    public SetBuildingChoicesMessage(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        this.buildingPos = buildingPos;
        this.choices = choices;
    }

    public SetBuildingChoicesMessage(final FriendlyByteBuf buf)
    {
        this.buildingPos = buf.readBlockPos();
        this.choices = ChoiceCodec.read(buf);
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(buildingPos);
        ChoiceCodec.write(buf, choices);
    }

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer sender = ctx.get().getSender();
            if (sender == null)
            {
                return;
            }
            final ServerLevel level = sender.serverLevel();
            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, buildingPos);
            if (colony == null || !colony.getPermissions().hasPermission(sender, Action.MANAGE_HUTS))
            {
                return;
            }
            final ICommonBuilding building = colony.getCommonBuildingManager().getBuilding(buildingPos);
            if (!(building instanceof PlacementChoiceHolder holder))
            {
                return;
            }
            holder.setReplacementChoices(choices.isEmpty() ? null : choices);
            if (building instanceof IBuilding persistent)
            {
                persistent.markDirty();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
