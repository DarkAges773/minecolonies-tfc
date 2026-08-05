package com.mctfc.inventory;

import com.mctfc.MineColoniesTFC;
import com.mctfc.forge.ForgeMenu;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.inventory.MenuType;

/**
 * Our menu (container) types. Currently just the {@link ComposeDishMenu} used by the Chef dish-teaching screen.
 * Registered from the mod constructor via {@link #register(IEventBus)}.
 */
public final class ModMenus
{
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineColoniesTFC.MODID);

    public static final RegistryObject<MenuType<ComposeDishMenu>> COMPOSE_DISH =
        MENUS.register("compose_dish", () -> IForgeMenuType.create(ComposeDishMenu::fromBuffer));

    public static final RegistryObject<MenuType<ForgeMenu>> HEAT_FORGE =
        MENUS.register("heat_forge", () -> IForgeMenuType.create(ForgeMenu::fromBuffer));

    public static void register(final IEventBus modBus)
    {
        MENUS.register(modBus);
    }
}
