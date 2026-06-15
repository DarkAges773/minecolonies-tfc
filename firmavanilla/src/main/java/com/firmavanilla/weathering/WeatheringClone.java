package com.firmavanilla.weathering;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Shared helper for the weathering copper blocks whose bright (UNAFFECTED) stage has no item of its own — it
 * stands in for a TFC block's item. Used by every {@code WeatheringCopper*Block} so the pick-block result of a
 * bright block is the TFC item it represents, not air.
 */
public final class WeatheringClone
{
    private WeatheringClone() {}

    /** For the UNAFFECTED stage return the TFC item (if present); otherwise the block's normal clone stack. */
    public static ItemStack unaffectedOr(final WeatherState age, final ResourceLocation tfcItem, final ItemStack fallback)
    {
        if (age == WeatherState.UNAFFECTED)
        {
            final Item tfc = ForgeRegistries.ITEMS.getValue(tfcItem);
            if (tfc != null) return new ItemStack(tfc);
        }
        return fallback;
    }
}
