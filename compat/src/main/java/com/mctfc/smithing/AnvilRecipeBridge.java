package com.mctfc.smithing;

import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feeds every TFC anvil-working ({@code tfc:anvil}) and anvil-welding ({@code tfc:welding}) recipe to the
 * MineColonies <b>Blacksmith</b> as a colony crafter recipe, gated by the {@code mctfc:smithing/*} research chain
 * (in the stock Technology branch, under Hitting Iron): each recipe requires the unlock effect matching its
 * minimum anvil tier (≤1 → none, stone/copper-tier work is craftable out of the box; 2 → bronze; 3 → wrought
 * iron; 4 → steel; 5 → black steel; 6 → red/blue steel). Tools never appear here by construction — TFC forges
 * tool <i>heads</i> on the anvil and crafts the tool from head + stick in the grid, so "all anvil recipes" is
 * exactly heads, sheets, double ingots/sheets, rods, shears, tuyeres, plated blocks, …
 *
 * <p>Inputs are taken verbatim from the TFC recipe (first stack of each ingredient — canonically TFC's own item
 * for tag ingredients), so the colony pays the same metal a player would at the anvil; welding additionally
 * consumes one flux (resolved from {@code #tfc:flux}), mirroring the anvil's flux slot. The colony abstracts away
 * heating the work piece and the forging minigame, like it abstracts every other crafter's process.
 *
 * <p>{@link #injectAll()} is invoked from {@code MixinCustomRecipeManager} at the TAIL of
 * {@code CustomRecipeManager#resolveTemplates()} — MineColonies calls that once per datapack sync (from
 * {@code DataPackSyncEventHandler}), which is exactly the right moment: after {@code CrafterRecipeListener}
 * has reset + reloaded the datapack recipes (so ours survive the reset), with the server's {@code RecipeManager}
 * fully loaded and tags bound (so TFC recipes resolve), and right before {@code sendCustomRecipeManagerPackets}
 * (so clients receive ours in the same sync). Re-runs are harmless: {@code addRecipe} overwrites by recipe id.
 */
public final class AnvilRecipeBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilRecipeBridge.class);

    /** The blacksmith's crafting module id, as used by stock {@code crafterrecipes} JSON. */
    private static final String BLACKSMITH_CRAFTER = "blacksmith_crafting";

    private static final TagKey<Item> FLUX = TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "flux"));

    private static final ResourceLocation EFFECT_BRONZE = new ResourceLocation("mctfc", "effects/smithing_bronze");
    private static final ResourceLocation EFFECT_WROUGHT_IRON = new ResourceLocation("mctfc", "effects/smithing_wrought_iron");
    private static final ResourceLocation EFFECT_STEEL = new ResourceLocation("mctfc", "effects/smithing_steel");
    private static final ResourceLocation EFFECT_BLACK_STEEL = new ResourceLocation("mctfc", "effects/smithing_black_steel");
    private static final ResourceLocation EFFECT_RED_BLUE_STEEL = new ResourceLocation("mctfc", "effects/smithing_red_blue_steel");

    private AnvilRecipeBridge() {}

    /** Regenerate and register all blacksmith recipes from the current TFC recipe set. Server-side no-op-safe. */
    public static void injectAll()
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
        {
            return;
        }
        final RecipeManager recipes = server.getRecipeManager();
        final RegistryAccess registryAccess = server.registryAccess();
        final ItemStorage flux = fluxStorage();

        int anvil = 0;
        int welding = 0;
        for (final AnvilRecipe recipe : recipes.getAllRecipesFor(TFCRecipeTypes.ANVIL.get()))
        {
            final ItemStack result = recipe.getResultItem(registryAccess);
            final ItemStack[] input = recipe.getInput().getItems();
            if (result.isEmpty() || input.length == 0)
            {
                continue;
            }
            addRecipe("anvil", recipe.getId(), List.of(new ItemStorage(input[0].copyWithCount(1))), result, recipe.getMinTier());
            anvil++;
        }
        for (final WeldingRecipe recipe : recipes.getAllRecipesFor(TFCRecipeTypes.WELDING.get()))
        {
            final ItemStack result = recipe.getResultItem(registryAccess);
            final ItemStack[] first = recipe.getFirstInput().getItems();
            final ItemStack[] second = recipe.getSecondInput().getItems();
            if (result.isEmpty() || first.length == 0 || second.length == 0)
            {
                continue;
            }
            final List<ItemStorage> inputs = new ArrayList<>();
            inputs.add(new ItemStorage(first[0].copyWithCount(1)));
            inputs.add(new ItemStorage(second[0].copyWithCount(1)));
            if (flux != null)
            {
                inputs.add(flux.copy());
            }
            addRecipe("welding", recipe.getId(), mergeDuplicates(inputs), result, recipe.getTier());
            welding++;
        }
        LOGGER.info("Registered {} TFC anvil + {} welding recipes for the colony blacksmith", anvil, welding);
    }

    private static void addRecipe(final String kind, final ResourceLocation sourceId, final List<ItemStorage> inputs,
            final ItemStack result, final int minAnvilTier)
    {
        final ResourceLocation recipeId =
                new ResourceLocation("mctfc", kind + "/" + sourceId.getNamespace() + "/" + sourceId.getPath());
        final ResourceLocation effect = effectForTier(minAnvilTier);
        CustomRecipeManager.getInstance().addRecipe(new CustomRecipe(
                BLACKSMITH_CRAFTER,
                0,
                5,
                false,
                true,
                recipeId,
                effect == null ? Set.of() : Set.of(effect),
                Set.of(),
                null,
                ModEquipmentTypes.none.get(),
                inputs,
                result,
                List.of(),
                List.of(),
                Blocks.AIR));
    }

    /**
     * TFC anvil tiers: 0 stone / 1 copper / 2 bronzes / 3 wrought iron / 4 steel / 5 black steel / 6 red+blue steel.
     * Stone- and copper-tier work needs no research ({@code null}) — the blacksmith does it out of the box.
     */
    private static ResourceLocation effectForTier(final int tier)
    {
        return switch (tier)
        {
            case 2 -> EFFECT_BRONZE;
            case 3 -> EFFECT_WROUGHT_IRON;
            case 4 -> EFFECT_STEEL;
            case 5 -> EFFECT_BLACK_STEEL;
            default -> tier <= 1 ? null : EFFECT_RED_BLUE_STEEL;
        };
    }

    /** Welding two identical inputs (e.g. ingot + ingot → double ingot) folds into one entry with count 2. */
    private static List<ItemStorage> mergeDuplicates(final List<ItemStorage> inputs)
    {
        final Map<Item, ItemStorage> merged = new LinkedHashMap<>();
        for (final ItemStorage input : inputs)
        {
            final ItemStorage existing = merged.get(input.getItem());
            if (existing != null && ItemStack.isSameItemSameTags(existing.getItemStack(), input.getItemStack()))
            {
                existing.setAmount(existing.getAmount() + input.getAmount());
            }
            else
            {
                merged.put(input.getItem(), input);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static ItemStorage fluxStorage()
    {
        final ITag<Item> tag = ForgeRegistries.ITEMS.tags().getTag(FLUX);
        if (tag.isEmpty())
        {
            LOGGER.warn("#tfc:flux is empty - colony welding recipes will not consume flux");
            return null;
        }
        return new ItemStorage(new ItemStack(tag.iterator().next()));
    }
}
