package com.mctfc.network;

import com.mctfc.MineColoniesTFC;
import com.mctfc.farming.HarvestMode;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel for the MineColonies&times;TFC farming bridge. Carries the per-field harvest-mode toggle
 * from the field (scarecrow) GUI to the server. Own channel (not MineColonies') — registered once from the
 * mod constructor (MineColonies is a mandatory dependency of {@code :compat}).
 */
public final class McFarmingNetwork
{
    private McFarmingNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineColoniesTFC.MODID, "farming"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, SetHarvestModeMessage.class,
                SetHarvestModeMessage::encode,
                SetHarvestModeMessage::new,
                SetHarvestModeMessage::handle);
    }

    /** Client → server: set one field's harvest mode. */
    public static void sendHarvestMode(final ResourceKey<Level> dimension, final int colonyId,
            final BlockPos fieldPos, final HarvestMode mode)
    {
        CHANNEL.sendToServer(new SetHarvestModeMessage(dimension, colonyId, fieldPos, mode));
    }
}
