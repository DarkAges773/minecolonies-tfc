package com.mctfc.network;

import com.mctfc.MineColoniesTFC;
import com.mctfc.cook.DishType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Network channel for the Chef dish-teaching GUI: open the {@link OpenComposeDishMessage compose screen} and
 * {@link TeachComposedDishMessage teach} the composed dish. Own channel (parallel to {@code McFarmingNetwork}),
 * registered once from the mod constructor.
 */
public final class ComposeDishNetwork
{
    private ComposeDishNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MineColoniesTFC.MODID, "compose_dish"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register()
    {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenComposeDishMessage.class,
            OpenComposeDishMessage::encode, OpenComposeDishMessage::new, OpenComposeDishMessage::handle);
        CHANNEL.registerMessage(id++, TeachComposedDishMessage.class,
            TeachComposedDishMessage::encode, TeachComposedDishMessage::new, TeachComposedDishMessage::handle);
    }

    /** Client → server: open the compose screen for a building/module in the given dish mode. */
    public static void sendOpen(final BlockPos pos, final int moduleId, final DishType dishType)
    {
        CHANNEL.sendToServer(new OpenComposeDishMessage(pos, moduleId, dishType));
    }

    /** Client → server: teach the dish composed in the open menu. */
    public static void sendTeach()
    {
        CHANNEL.sendToServer(new TeachComposedDishMessage());
    }
}
