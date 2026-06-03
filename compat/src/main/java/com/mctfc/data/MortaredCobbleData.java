package com.mctfc.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mctfc.MineColoniesTFC;
import com.mctfc.block.MortaredCobbleBlock;
import com.mctfc.block.MortaredCobbleRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Builds and registers the runtime {@link GeneratedDataPack} that backs the cobble twins: the
 * {@code mctfc:mortared_cobblestone} block tag (every twin) and a {@code <source cobble> + tfc:mortar}
 * shapeless recipe per twin. Built at {@code AddPackFindersEvent} time, when the twin set is already
 * registered. The substitution rule itself ({@code minecraft:cobblestone -> #mctfc:mortared_cobblestone})
 * ships as a normal static datapack file.
 */
public final class MortaredCobbleData
{
    private MortaredCobbleData() {}

    private static final String TAG_PATH = "tags/blocks/mortared_cobblestone.json";
    private static final String MORTAR_TAG = "tfc:mortar";

    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA)
        {
            return;
        }
        final GeneratedDataPack pack = build();
        event.addRepositorySource(consumer ->
        {
            final Pack created = Pack.readMetaAndCreate(
                    "mctfc_generated",
                    Component.literal("MineColonies x TFC generated data"),
                    true, // forced on — it carries the cobble-twin tag + recipes
                    id -> pack,
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (created != null)
            {
                consumer.accept(created);
            }
        });
    }

    private static GeneratedDataPack build()
    {
        final GeneratedDataPack pack = new GeneratedDataPack("mctfc_generated");
        final JsonArray tagValues = new JsonArray();

        for (final Map.Entry<Block, MortaredCobbleBlock> entry : MortaredCobbleRegistry.twins().entrySet())
        {
            final ResourceLocation sourceId = ForgeRegistries.BLOCKS.getKey(entry.getKey());
            final ResourceLocation twinId = ForgeRegistries.BLOCKS.getKey(entry.getValue());
            if (sourceId == null || twinId == null)
            {
                continue;
            }
            tagValues.add(twinId.toString());
            pack.putJson(twinId.getNamespace(), "recipes/" + twinId.getPath() + ".json",
                    mortarRecipe(sourceId, twinId));
        }

        final JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", tagValues);
        pack.putJson(MineColoniesTFC.MODID, TAG_PATH, tag.toString());

        // Make the twins behave/identify like normal cobble (minus gravity): add #mctfc:mortared_cobblestone
        // to the same block tags real cobble is in. NOT tfc:can_landslide — that's the falling we're avoiding.
        final String mortaredRef = "#" + MineColoniesTFC.MODID + ":mortared_cobblestone";
        joinTag(pack, "minecraft", "mineable/pickaxe", mortaredRef); // proper pickaxe mining
        joinTag(pack, "forge", "cobblestone/normal", mortaredRef);   // counts as cobblestone for recipes/mods
        joinTag(pack, "tfc", "can_carve", mortaredRef);              // TFC chisel/carving
        joinTag(pack, "tfc", "toughness_2", mortaredRef);            // TFC explosion-resistance tier

        MineColoniesTFC.LOGGER.info("Generated mortared-cobble data: {} twin(s) tagged + recipes.", tagValues.size());
        return pack;
    }

    /** Append {@code value} to an existing block tag ({@code replace:false}) via the runtime pack. */
    private static void joinTag(final GeneratedDataPack pack, final String namespace, final String tagPath, final String value)
    {
        final JsonArray values = new JsonArray();
        values.add(value);
        final JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", values);
        pack.putJson(namespace, "tags/blocks/" + tagPath + ".json", tag.toString());
    }

    /**
     * Shaped recipe: the cobble surrounded by 4 mortar (cross pattern) -> the twin.
     * <pre>
     *   . M .
     *   M C M
     *   . M .
     * </pre>
     */
    private static String mortarRecipe(final ResourceLocation sourceId, final ResourceLocation twinId)
    {
        final JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");

        final JsonArray pattern = new JsonArray();
        pattern.add(" M ");
        pattern.add("MCM");
        pattern.add(" M ");
        recipe.add("pattern", pattern);

        final JsonObject mortar = new JsonObject();
        mortar.addProperty("tag", MORTAR_TAG);
        final JsonObject cobble = new JsonObject();
        cobble.addProperty("item", sourceId.toString());
        final JsonObject key = new JsonObject();
        key.add("M", mortar);
        key.add("C", cobble);
        recipe.add("key", key);

        final JsonObject result = new JsonObject();
        result.addProperty("item", twinId.toString());
        recipe.add("result", result);

        return recipe.toString();
    }
}
