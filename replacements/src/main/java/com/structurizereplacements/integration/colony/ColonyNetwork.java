package com.structurizereplacements.integration.colony;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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

    /**
     * Registered from {@link ColonyIntegration} when a colony mod is present. NeoForge payload registration
     * is a mod-bus event, so this adds a listener rather than registering immediately; a separate registrar
     * namespace keeps this channel distinct from the engine's core one.
     */
    public static void register(final IEventBus modBus)
    {
        modBus.addListener(ColonyNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event)
    {
        final PayloadRegistrar registrar = event.registrar(StructurizeReplacements.MODID + ".colony").versioned(PROTOCOL);
        // Client → server only: the registrar rejects the payload (and never runs the handler) if it ever
        // arrives on the wrong side, matching the directional discipline of the core channel in Network.java.
        registrar.playToServer(SetBuildingChoicesMessage.TYPE, SetBuildingChoicesMessage.STREAM_CODEC,
                SetBuildingChoicesMessage::handle);
    }

    /** Client → server: set a single building's hut-palette replacement choices. */
    public static void sendBuildingChoices(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        PacketDistributor.sendToServer(new SetBuildingChoicesMessage(buildingPos, choices, false));
    }

    /** Client → server: set the miner's mineshaft-palette replacement choices. */
    public static void sendMineshaftChoices(final BlockPos buildingPos, final Map<Block, Block> choices)
    {
        PacketDistributor.sendToServer(new SetBuildingChoicesMessage(buildingPos, choices, true));
    }
}
