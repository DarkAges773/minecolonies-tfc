package com.mctfc.food;

import com.mctfc.MineColoniesTFC;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.HashSet;

/**
 * Makes MineColonies treat TFC <b>salads and pot soups</b> as NBT-agnostic when matching item stacks, so a colony
 * crafter (the Chef) can actually fulfil requests for them.
 *
 * <p><b>The bug.</b> TFC's salad/soup items use a {@code DynamicBowlHandler}, whose {@code save()} writes the bowl (and
 * with it the dish's per-instance data) into the item's <b>vanilla {@code getTag()}</b> — unlike a plain sandwich
 * ({@code FoodHandler.Dynamic}, whose data lives only in the food capability, invisible to {@code getTag}). So every
 * freshly-made salad carries a non-empty, per-instance tag. MineColonies matches with {@code matchNBT = true}
 * (e.g. the dining-hall request predicate {@code RestaurantMenuModule#onColonyTick} →
 * {@code ItemStackUtils.compareItemStacksIgnoreStackSize(stack, menuItem, false, true)}), and with no registered
 * checked-NBT keys that compares the <b>full</b> tag — so the Chef's freshly-crafted salad never equals the menu's
 * salad, and the request is never fulfilled.
 *
 * <p><b>The fix</b> (same mechanism as {@link com.mctfc.crafting.ToolNbtMatching} for worn knives): register each
 * salad/soup item in {@code ItemStackUtils.CHECKED_NBT_KEYS} with an <b>empty</b> key set, which makes that comparison
 * short-circuit to a match on item id alone — so any {@code fruit_salad} satisfies a {@code fruit_salad} request,
 * regardless of bowl/ingredients/freshness (all of which are colony-irrelevant; a citizen eats whichever it gets).
 * Populated from the {@code #tfc:dynamic_bowl_items} tag (= {@code #tfc:salads} + {@code #tfc:soups}) on
 * {@link TagsUpdatedEvent} (after the datapack listener that owns the map has reloaded it, so our entries survive
 * {@code /reload}); {@code putIfAbsent} so a future MineColonies-shipped entry wins.
 */
@Mod.EventBusSubscriber(modid = MineColoniesTFC.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DynamicBowlFoodMatching
{
    private static final TagKey<Item> DYNAMIC_BOWL_ITEMS =
        TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "dynamic_bowl_items"));

    private DynamicBowlFoodMatching() {}

    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event)
    {
        final ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null)
        {
            return;
        }
        tags.getTag(DYNAMIC_BOWL_ITEMS).forEach(dish -> ItemStackUtils.CHECKED_NBT_KEYS.putIfAbsent(dish, new HashSet<>()));
    }
}
