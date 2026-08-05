package com.mctfc.network;

import com.mctfc.cook.DishType;
import com.mctfc.inventory.ComposeDishMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Client → server: open the {@link ComposeDishMenu} (the Chef dish-teaching screen) for the given building/module and
 * dish type. The server launches the container via {@code NetworkHooks.openScreen} (the same way MineColonies opens
 * its crafting/furnace teaching GUIs in {@code OpenCraftingGUIMessage}). Also used to switch salad ⇄ soup (reopen with
 * the other type).
 */
public class OpenComposeDishMessage
{
    private final BlockPos pos;
    private final int      moduleId;
    private final DishType dishType;

    public OpenComposeDishMessage(final BlockPos pos, final int moduleId, final DishType dishType)
    {
        this.pos = pos;
        this.moduleId = moduleId;
        this.dishType = dishType;
    }

    public OpenComposeDishMessage(final FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.moduleId = buf.readInt();
        this.dishType = DishType.byId(buf.readByte());
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeInt(moduleId);
        buf.writeByte(dishType.id());
    }

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer player = ctx.get().getSender();
            if (player == null)
            {
                return;
            }
            NetworkHooks.openScreen(player, new MenuProvider()
            {
                @NotNull
                @Override
                public Component getDisplayName()
                {
                    return dishType.title();
                }

                @NotNull
                @Override
                public AbstractContainerMenu createMenu(final int id, @NotNull final Inventory inv, @NotNull final Player p)
                {
                    return new ComposeDishMenu(id, inv, dishType, pos, moduleId);
                }
            }, buf -> {
                // Read back by ComposeDishMenu.fromBuffer in this exact order.
                buf.writeByte(dishType.id());
                buf.writeBlockPos(pos);
                buf.writeInt(moduleId);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
