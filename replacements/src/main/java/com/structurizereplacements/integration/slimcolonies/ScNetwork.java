package com.structurizereplacements.integration.slimcolonies;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;

/**
 * Network channel for the optional SlimColonies integration — the SlimColonies twin of {@code McNetwork}.
 * Carries per-building replacement edits from the Build Options GUI to the server. Separate from the
 * engine's core channel and registered only when SlimColonies is present (see
 * {@link SlimColoniesIntegration}); the build wand's global session picks use the core channel.
 */
public final class ScNetwork
{
    private ScNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructurizeReplacements.MODID, "slimcolonies"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, SetBuildingChoicesMessage.class,
                SetBuildingChoicesMessage::encode,
                SetBuildingChoicesMessage::new,
                SetBuildingChoicesMessage::handle);
    }

    /** Client → server: set a single building's hut-palette replacement choices. */
    public static void sendBuildingChoices(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        CHANNEL.sendToServer(new SetBuildingChoicesMessage(buildingPos, choices, false));
    }

    /** Client → server: set the miner's mineshaft-palette replacement choices. */
    public static void sendMineshaftChoices(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        CHANNEL.sendToServer(new SetBuildingChoicesMessage(buildingPos, choices, true));
    }
}
