package com.structurizereplacements.integration.colony;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;

/**
 * Network channel for the colony-mod integration (one shared channel for whichever fork is loaded —
 * the handler routes through {@link ColonyBridge}, so the packet itself is fork-agnostic). Carries
 * per-building replacement edits from the Build Options GUI to the server. Separate from the engine's
 * core channel and registered only when a colony mod is present (see {@link ColonyIntegration}); the
 * build wand's global session picks use the core channel.
 */
public final class ColonyNetwork
{
    private ColonyNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StructurizeReplacements.MODID, "colony"),
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
