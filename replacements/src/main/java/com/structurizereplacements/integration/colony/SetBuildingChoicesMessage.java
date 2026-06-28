package com.structurizereplacements.integration.colony;

import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → server: set the replacement choices for a single building (the Build Options "Replace" GUI, or
 * the miner's mineshaft palette when {@code mineshaft} is true). The server resolves the building at
 * {@code buildingPos} via the {@link ColonyBridge} (permission-checked), applies the choices to the
 * matching map, and {@code markDirty()}s the building (persists to colony NBT + re-syncs the view).
 * Per-building only — does not touch the player's global session picks.
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
            final ColonyBridge bridge = ColonyIntegration.bridge();
            if (sender == null || bridge == null || !bridge.canEdit(sender, buildingPos))
            {
                return;
            }
            final ServerLevel level = sender.serverLevel();
            final Object building = bridge.buildingAt(level, buildingPos);
            // Validate against the server's candidate pools — keep only picks the GUI could legitimately offer,
            // so a modified client can't store an arbitrary substitution on the building.
            final Map<Block, Block> validated = new LinkedHashMap<>();
            choices.forEach((from, to) -> {
                if (BlockSubstitutions.isAllowedChoice(from, to))
                {
                    validated.put(from, to);
                }
            });
            final Map<Block, Block> applied = validated.isEmpty() ? null : validated;
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
            bridge.markDirty(building);
        });
        ctx.get().setPacketHandled(true);
    }
}
