package com.structurizereplacements.integration.slimcolonies;

import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import no.monopixel.slimcolonies.api.colony.IColony;
import no.monopixel.slimcolonies.api.colony.IColonyManager;
import no.monopixel.slimcolonies.api.colony.buildings.IBuilding;
import no.monopixel.slimcolonies.api.colony.permissions.Action;

import java.util.Map;
import java.util.function.Supplier;

/**
 * SlimColonies twin of the MineColonies {@code SetBuildingChoicesMessage}. Client → server: set the
 * replacement choices for a single building (the Build Options "Replace" GUI, or the miner's mineshaft
 * palette when {@code mineshaft} is true). The server resolves the colony/building at {@code buildingPos}
 * from the sender's level, applies the choices to the matching map, and {@code markDirty()}s the building
 * (persists to colony NBT + re-syncs the view). Per-building only — does not touch the player's global
 * session picks. API delta vs MineColonies: {@code getBuildingManager().getBuilding(pos)} returns
 * {@link IBuilding} directly, so no persistent-building cast before {@code markDirty()}.
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
            final IBuilding building = colony.getBuildingManager().getBuilding(buildingPos);
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
            if (building != null)
            {
                building.markDirty();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
