package com.mctfc.network;

import com.mctfc.MineColoniesTFC;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;

/**
 * {@code :compat}'s network channel. Carries per-building replacement edits from the Build Options GUI to
 * the server. (The build wand's global session picks use {@code :replacements}' own channel.)
 */
public final class Network
{
    private Network() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineColoniesTFC.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, SetBuildingChoicesMessage.class,
                SetBuildingChoicesMessage::encode,
                SetBuildingChoicesMessage::new,
                SetBuildingChoicesMessage::handle);
    }

    /** Client → server: set a single building's replacement choices. */
    public static void sendBuildingChoices(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        CHANNEL.sendToServer(new SetBuildingChoicesMessage(buildingPos, choices));
    }
}
