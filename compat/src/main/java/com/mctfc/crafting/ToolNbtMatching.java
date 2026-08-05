package com.mctfc.crafting;

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
 * Makes MineColonies treat TFC knives as durability-agnostic when matching crafting ingredients, so a colony
 * crafter can keep using the same (wearing) knife across many crafts.
 *
 * <p>MineColonies matches recipe inputs with {@code matchNBT = true}, and for any item it has no registered
 * "checked NBT keys" for it compares the <b>full</b> item tag. A fresh knife has no tag; the moment TFC's
 * {@code damage_inputs} sandwich recipe damages it, it gains a {@code Damage} tag — so the worn knife no longer
 * matches the recipe's fresh-knife input. The crafter then can't see its own (damaged) knife: it makes exactly
 * one sandwich and stalls, and doesn't even request a replacement (its only knife <i>is</i> the unmatchable
 * damaged one). See {@code ItemStackUtils#compareItemStacksIgnoreStackSize} (the {@code CHECKED_NBT_KEYS} branch).
 *
 * <p>Registering an item in {@code ItemStackUtils.CHECKED_NBT_KEYS} with an <b>empty</b> key set makes that
 * comparison ignore all NBT for it, so any-damage knives match. We populate it from the <b>{@code tfc:knives}
 * item tag</b> — covering every metal and any add-on knife — rather than a hardcoded list. MineColonies' own
 * {@code compatibility} datapack format is item-id-only (no tags), so this tag-driven population is done in code,
 * on {@link TagsUpdatedEvent}: that fires after the datapack listener which owns the map has cleared and reloaded
 * it, so our entries survive every reload. {@code putIfAbsent} so a future MineColonies-shipped entry wins.
 */
@Mod.EventBusSubscriber(modid = MineColoniesTFC.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ToolNbtMatching
{
    private static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "knives"));

    private ToolNbtMatching() {}

    @SubscribeEvent
    public static void onTagsUpdated(final TagsUpdatedEvent event)
    {
        final ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null)
        {
            return;
        }
        tags.getTag(KNIVES).forEach(knife -> ItemStackUtils.CHECKED_NBT_KEYS.putIfAbsent(knife, new HashSet<>()));
    }
}
