package com.mctfc.crafting;

import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Strips MineColonies' built-in <b>vanilla-log</b> lumberjack recipes so only the TFC ones remain. MineColonies
 * ships two {@code recipe-template}s — {@code strip_logs} and {@code strip_stems} — that expand (over
 * {@code #minecraft:logs}, with vanilla {@code _log}/{@code _stem} naming) into one recipe per vanilla log/stem,
 * e.g. {@code minecolonies:lumberjack/strip_logs/minecraft/oak_log}. In a TFC world those vanilla items don't
 * exist, so the recipes are dead clutter; our {@code data/mctfc/crafterrecipes/lumberjack/tfc_strip_logs.json}
 * template adds the TFC equivalents instead.
 *
 * <p>The {@code remove} crafter-recipe type only deletes by exact id, which is impractical against a template that
 * fans out to a child per log. So we prune by id prefix once the templates have resolved — called from
 * {@code MixinCustomRecipeManager} at the {@code resolveTemplates()} TAIL (the deterministic post-load seam, after
 * both the vanilla and our TFC templates have been expanded, and before the recipe map syncs to clients).
 *
 * <p>Only {@code minecolonies:}-namespaced {@code strip_logs/}/{@code strip_stems/} children are removed — the
 * bamboo recipe ({@code lumberjack/strip_bamboo_block}) and our TFC recipes ({@code mctfc:} namespace) are kept.
 */
public final class LumberjackRecipes
{
    private LumberjackRecipes() {}

    /** The lumberjack's custom-crafter key (matches the {@code "crafter"} field in the recipe JSON). */
    private static final String CRAFTER = "lumberjack_custom";

    public static void removeVanillaDefaults()
    {
        final Map<ResourceLocation, CustomRecipe> recipes =
          CustomRecipeManager.getInstance().getAllRecipes().get(CRAFTER);
        if (recipes == null)
        {
            return;
        }
        recipes.keySet().removeIf(id -> id.getNamespace().equals("minecolonies")
                                          && (id.getPath().startsWith("lumberjack/strip_logs/")
                                                || id.getPath().startsWith("lumberjack/strip_stems/")));
    }
}
