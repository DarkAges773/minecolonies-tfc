package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;

/**
 * Mod network channel. Carries the player's per-placement replacement choices from client to server
 * so the server placement (and thus {@code handleBlockPlacement}) can apply them.
 */
public final class Network
{
    private Network() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructurizeReplacements.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncReplacementChoicesMessage.class,
                SyncReplacementChoicesMessage::encode,
                SyncReplacementChoicesMessage::new,
                SyncReplacementChoicesMessage::handle);
    }

    /** Client → server: send the current choice map (source block → chosen target). */
    public static void sendChoicesToServer(final Map<Block, Block> choices)
    {
        CHANNEL.sendToServer(new SyncReplacementChoicesMessage(choices));
    }
}
