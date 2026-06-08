package com.structurizereplacements.integration.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ICommonBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
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
 * Client → server: set the replacement choices for a single building (the Build Options "Replace" GUI, or
 * the miner's mineshaft palette when {@code mineshaft} is true). The server resolves the colony/building at
 * {@code buildingPos} from the sender's level, applies the choices to the matching map, and
 * {@code markDirty()}s the building (persists to colony NBT + re-syncs the view). Per-building only — does
 * not touch the player's global session picks.
 */
public class SetBuildingChoicesMessage
{
    private final BlockPos buildingPos;
    private final Map<Block, Block> choices;
    /** false → the hut-building palette ({@link PlacementChoiceHolder}); true → the mineshaft palette ({@link MineshaftChoiceHolder}). */
    private final boolean mineshaft;

    public SetBuildingChoicesMessage(final BlockPos buildingPos, final Map<Block, Block> choices, final boolean mineshaft)
    {
        this.buildingPos = buildingPos;
        this.choices = choices;
        this.mineshaft = mineshaft;
    }

    public SetBuildingChoicesMessage(final FriendlyByteBuf buf)
    {
        this.buildingPos = buf.readBlockPos();
        this.mineshaft = buf.readBoolean();
        this.choices = ChoiceCodec.read(buf);
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(buildingPos);
        buf.writeBoolean(mineshaft);
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
            final Map<Block, Block> applied = choices.isEmpty() ? null : choices;
            if (mineshaft)
            {
                if (!(building instanceof MineshaftChoiceHolder holder))
                {
                    return;
                }
                holder.setMineshaftChoices(applied);
            }
            else
            {
                if (!(building instanceof PlacementChoiceHolder holder))
                {
                    return;
                }
                holder.setReplacementChoices(applied);
            }
            if (building instanceof IBuilding persistent)
            {
                persistent.markDirty();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
