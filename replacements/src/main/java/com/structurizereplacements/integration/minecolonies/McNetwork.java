package com.structurizereplacements.integration.minecolonies;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;

/**
 * Network channel for the optional MineColonies integration. Carries per-building replacement edits from
 * the Build Options GUI to the server. Separate from the engine's core channel and registered only when
 * MineColonies is present (see {@link MineColoniesIntegration}); the build wand's global session picks use
 * the core channel.
 */
public final class McNetwork
{
    private McNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructurizeReplacements.MODID, "minecolonies"),
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
