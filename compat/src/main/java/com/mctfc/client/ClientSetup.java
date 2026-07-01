package com.mctfc.client;

import com.mctfc.MineColoniesTFC;
import com.mctfc.client.gui.ComposeDishScreen;
import com.mctfc.inventory.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only mod-bus setup. Binds our {@link ModMenus#COMPOSE_DISH} menu type to its {@link ComposeDishScreen}.
 * {@code value = Dist.CLIENT} so it is registered only on the client.
 */
@Mod.EventBusSubscriber(modid = MineColoniesTFC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup
{
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.COMPOSE_DISH.get(), ComposeDishScreen::new));
    }
}
