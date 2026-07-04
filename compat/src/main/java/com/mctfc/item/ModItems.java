package com.mctfc.item;

import com.mctfc.MineColoniesTFC;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration for {@code mctfc}'s plain (non-block) items — currently just the {@link ItemBloomeryScepter}, the wand a
 * player takes from the Smeltery GUI to mark TFC bloomeries for the Smelter to tend (see
 * {@code docs/tfc-bloomery-smelter.md}). Wired from the mod constructor via {@link #init(IEventBus)}.
 */
public final class ModItems
{
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineColoniesTFC.MODID);

    /** The bloomery-marking wand (max stack 1, like MineColonies' scepters). */
    public static final RegistryObject<Item> BLOOMERY_SCEPTER =
            ITEMS.register("bloomery_scepter", () -> new ItemBloomeryScepter(new Item.Properties()));

    public static void init(final IEventBus modBus)
    {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(final BuildCreativeModeTabContentsEvent event)
    {
        // Tools & Utilities so it's grabbable and — like the heat-forge items — discoverable by MineColonies' pickers.
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
        {
            event.accept(BLOOMERY_SCEPTER);
        }
    }
}
