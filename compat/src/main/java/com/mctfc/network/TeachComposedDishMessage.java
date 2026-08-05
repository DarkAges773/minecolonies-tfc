package com.mctfc.network;

import com.mctfc.inventory.ComposeDishMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: teach the dish currently composed in the open {@link ComposeDishMenu}. Payload-free — the
 * composition lives in the player's open menu, which the server reads authoritatively (its slots carry the TFC food
 * capabilities the client's synced view lacks). Mirrors MineColonies' {@code SwitchRecipeCraftingTeachingMessage},
 * which likewise acts on {@code player.containerMenu}.
 */
public class TeachComposedDishMessage
{
    public TeachComposedDishMessage() {}

    public TeachComposedDishMessage(final FriendlyByteBuf buf) {}

    public void encode(final FriendlyByteBuf buf) {}

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof ComposeDishMenu menu)
            {
                menu.teach(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
