// Alabaster tile + pillar — vanilla purpur recoloured through each TFC alabaster-brick colour (CLUT), with a
// raw-alabaster detail pass and (for the tile) a hand-authored mask that shows raw alabaster on the panel faces
// and the generated seams on the brick lines. An uncoloured dyeable base (firmavanilla:alabaster_tile /
// alabaster_pillar) plus the 16 dye colours. Recipes match TFC's alabaster coloring strictly: chisel the
// coloured bricks, OR dye the uncoloured form in a sealed barrel; shapes (stairs/slab/wall) off the tile.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class Alabaster
{
    public static int Generate()
    {
        string chiselRoot = Path.Combine(resRoot, "data", MODID, "recipes", "chisel");
        foreach (var m in new[] { "smooth", "stair", "slab" }) Directory.CreateDirectory(Path.Combine(chiselRoot, m));

        var alTileDirs = TileDirs("alabaster_tile", withRecipe: true);
        var alPillarDirs = TileDirs("alabaster_pillar", withRecipe: true);
        // Shapes off the alabaster tile (stairs/slab/wall) — reuse the tile texture, no recipes for now.
        var alStDirs = DerivedDirs("alabaster_tile_stairs");
        var alSlDirs = DerivedDirs("alabaster_tile_slab");
        var alWlDirs = DerivedDirs("alabaster_tile_wall");
        using var purpurBlock = Load("vanilla", "purpur_block.png");
        using var purpurPillar = Load("vanilla", "purpur_pillar.png");
        using var purpurPillarTop = Load("vanilla", "purpur_pillar_top.png");
        using var tilesMask = Image.Load<Rgba32>(Path.Combine(scriptDir, "tiles_mask.png"));   // hand-authored, tracked

        // Uncoloured base tile (firmavanilla:alabaster_tile, no colour) — TFC's alabaster/bricks tier, the dyeable base.
        // Same pipeline as the coloured tiles, from the uncoloured alabaster bricks + raw. Chiselled from uncoloured
        // bricks; dyed (in a barrel) into the 16 colours below.
        string alAsset = Path.Combine(resRoot, "assets", MODID);
        string alData = Path.Combine(resRoot, "data", MODID);
        using (var ubLut = Load("tfc", Path.Combine("alabaster_bricks", "uncolored.png")))
        using (var ubRaw = Load("tfc", Path.Combine("alabaster_raw", "uncolored.png")))
        using (var ut = ClutThrough(purpurBlock, ubLut))
        {
            GrainOverlay(ut, ubRaw, ALABASTER_GRAIN_STRENGTH, null);
            MaskComposite(ut, ubRaw, tilesMask);
            ut.Save(Path.Combine(alAsset, "textures", "block", "alabaster_tile.png"));
        }
        File.WriteAllText(Path.Combine(alAsset, "blockstates", "alabaster_tile.json"),
            $$"""{ "variants": { "": { "model": "{{MODID}}:block/alabaster_tile" } } }""");
        File.WriteAllText(Path.Combine(alAsset, "models", "block", "alabaster_tile.json"),
            $$"""{ "parent": "minecraft:block/cube_all", "textures": { "all": "{{MODID}}:block/alabaster_tile" } }""");
        File.WriteAllText(Path.Combine(alAsset, "models", "item", "alabaster_tile.json"),
            $$"""{ "parent": "{{MODID}}:block/alabaster_tile" }""");
        File.WriteAllText(Path.Combine(alData, "loot_tables", "blocks", "alabaster_tile.json"),
            $$"""{ "type": "minecraft:block", "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "{{MODID}}:alabaster_tile" } ], "conditions": [ { "condition": "minecraft:survives_explosion" } ] } ] }""");
        // Uncoloured tile from uncoloured bricks: in-world smooth chisel + table chisel-craft (no dye — it IS the base).
        File.WriteAllText(Path.Combine(chiselRoot, "smooth", "alabaster_uncolored_tile.json"),
            ChiselRecipe("tfc:alabaster/bricks", $"{MODID}:alabaster_tile", "smooth"));
        File.WriteAllText(Path.Combine(alTileDirs.rec, "uncolored.json"),
            ToolCraft("tfc:alabaster/bricks", "tfc:chisels", $"{MODID}:alabaster_tile"));

        // Uncoloured base pillar (firmavanilla:alabaster_pillar, no colour) — also a dyeable base, like the tile.
        // Chiselled from the uncoloured tile; dyed (barrel) into the 16 colours below.
        using (var ubLut = Load("tfc", Path.Combine("alabaster_bricks", "uncolored.png")))
        using (var ubRaw = Load("tfc", Path.Combine("alabaster_raw", "uncolored.png")))
        {
            using (var s = ClutThrough(purpurPillar, ubLut)) { GrainOverlay(s, ubRaw, ALABASTER_GRAIN_STRENGTH, null); s.Save(Path.Combine(alAsset, "textures", "block", "alabaster_pillar.png")); }
            using (var top = ClutThrough(purpurPillarTop, ubLut)) { GrainOverlay(top, ubRaw, ALABASTER_GRAIN_STRENGTH, null); top.Save(Path.Combine(alAsset, "textures", "block", "alabaster_pillar_top.png")); }
        }
        File.WriteAllText(Path.Combine(alAsset, "blockstates", "alabaster_pillar.json"),
            $$"""{ "variants": { "axis=x": { "model": "{{MODID}}:block/alabaster_pillar", "x": 90, "y": 90 }, "axis=y": { "model": "{{MODID}}:block/alabaster_pillar" }, "axis=z": { "model": "{{MODID}}:block/alabaster_pillar", "x": 90 } } }""");
        File.WriteAllText(Path.Combine(alAsset, "models", "block", "alabaster_pillar.json"),
            $$"""{ "parent": "minecraft:block/cube_column", "textures": { "end": "{{MODID}}:block/alabaster_pillar_top", "side": "{{MODID}}:block/alabaster_pillar" } }""");
        File.WriteAllText(Path.Combine(alAsset, "models", "item", "alabaster_pillar.json"),
            $$"""{ "parent": "{{MODID}}:block/alabaster_pillar" }""");
        File.WriteAllText(Path.Combine(alData, "loot_tables", "blocks", "alabaster_pillar.json"),
            $$"""{ "type": "minecraft:block", "pools": [ { "rolls": 1, "entries": [ { "type": "minecraft:item", "name": "{{MODID}}:alabaster_pillar" } ], "conditions": [ { "condition": "minecraft:survives_explosion" } ] } ] }""");
        // Uncoloured pillar from the uncoloured tile: in-world smooth chisel + table chisel-craft.
        File.WriteAllText(Path.Combine(chiselRoot, "smooth", "alabaster_uncolored_pillar.json"),
            ChiselRecipe($"{MODID}:alabaster_tile", $"{MODID}:alabaster_pillar", "smooth"));
        File.WriteAllText(Path.Combine(alPillarDirs.rec, "uncolored.json"),
            ToolCraft($"{MODID}:alabaster_tile", "tfc:chisels", $"{MODID}:alabaster_pillar"));

        int alab = 0;
        foreach (var color in ALABASTER_COLORS)
        {
            using var lut = Load("tfc", Path.Combine("alabaster_bricks", color + ".png"));
            using var raw = Load("tfc", Path.Combine("alabaster_raw", color + ".png"));
            using (var t = ClutThrough(purpurBlock, lut))
            {
                GrainOverlay(t, raw, ALABASTER_GRAIN_STRENGTH, null);
                MaskComposite(t, raw, tilesMask);   // raw alabaster on the panel faces (mask black), generated seams (white)
                t.Save(Path.Combine(alTileDirs.tex, color + ".png"));
            }
            using (var s = ClutThrough(purpurPillar, lut))
            {
                GrainOverlay(s, raw, ALABASTER_GRAIN_STRENGTH, null);
                s.Save(Path.Combine(alPillarDirs.tex, color + ".png"));
            }
            using (var top = ClutThrough(purpurPillarTop, lut))
            {
                GrainOverlay(top, raw, ALABASTER_GRAIN_STRENGTH, null);
                top.Save(Path.Combine(alPillarDirs.tex, color + "_top.png"));
            }

            File.WriteAllText(Path.Combine(alTileDirs.bs, color + ".json"), TilesBlockstate("alabaster_tile", color));
            File.WriteAllText(Path.Combine(alTileDirs.model, color + ".json"), TilesModel("alabaster_tile", color));
            File.WriteAllText(Path.Combine(alTileDirs.item, color + ".json"), TilesItemModel("alabaster_tile", color));
            File.WriteAllText(Path.Combine(alTileDirs.loot, color + ".json"), TilesLoot("alabaster_tile", color));

            File.WriteAllText(Path.Combine(alPillarDirs.bs, color + ".json"), PillarBlockstate(color));
            File.WriteAllText(Path.Combine(alPillarDirs.model, color + ".json"), PillarModel(color));
            File.WriteAllText(Path.Combine(alPillarDirs.item, color + ".json"), TilesItemModel("alabaster_pillar", color));
            File.WriteAllText(Path.Combine(alPillarDirs.loot, color + ".json"), TilesLoot("alabaster_pillar", color));

            // Stairs / slab / wall off the alabaster tile — same emitters as the rock tiles, base texture alabaster_tile/<colour>.
            File.WriteAllText(Path.Combine(alStDirs.bs, color + ".json"), StairsBlockstate("alabaster_tile_stairs", color));
            foreach (var (suffix, body) in StairsModels("alabaster_tile", color)) File.WriteAllText(Path.Combine(alStDirs.model, color + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(alStDirs.item, color + ".json"), ItemParent("alabaster_tile_stairs", color, ""));
            File.WriteAllText(Path.Combine(alStDirs.loot, color + ".json"), SelfLoot("alabaster_tile_stairs", color));

            File.WriteAllText(Path.Combine(alSlDirs.bs, color + ".json"), SlabBlockstate("alabaster_tile_slab", "alabaster_tile", color));
            foreach (var (suffix, body) in SlabModels("alabaster_tile", color)) File.WriteAllText(Path.Combine(alSlDirs.model, color + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(alSlDirs.item, color + ".json"), ItemParent("alabaster_tile_slab", color, ""));
            File.WriteAllText(Path.Combine(alSlDirs.loot, color + ".json"), SlabLoot("alabaster_tile_slab", color));

            File.WriteAllText(Path.Combine(alWlDirs.bs, color + ".json"), WallBlockstate("alabaster_tile_wall", color));
            foreach (var (suffix, body) in WallModels("alabaster_tile", color)) File.WriteAllText(Path.Combine(alWlDirs.model, color + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(alWlDirs.item, color + ".json"), ItemParent("alabaster_tile_wall", color, "_inventory"));
            File.WriteAllText(Path.Combine(alWlDirs.loot, color + ".json"), SelfLoot("alabaster_tile_wall", color));

            // --- recipes (match TFC alabaster coloring strictly) ---
            // Coloured tile: chisel the coloured bricks (in-world smooth + table chisel-craft), OR dye the uncoloured tile.
            File.WriteAllText(Path.Combine(chiselRoot, "smooth", "alabaster_" + color + "_tile.json"),
                ChiselRecipe($"tfc:alabaster/bricks/{color}", $"{MODID}:alabaster_tile/{color}", "smooth"));
            File.WriteAllText(Path.Combine(alTileDirs.rec, color + ".json"),
                ToolCraft($"tfc:alabaster/bricks/{color}", "tfc:chisels", $"{MODID}:alabaster_tile/{color}"));
            File.WriteAllText(Path.Combine(alTileDirs.rec, color + "_dye.json"),
                BarrelDye($"{MODID}:alabaster_tile", color, $"{MODID}:alabaster_tile/{color}"));
            // Pillar from the tile: in-world smooth chisel + table chisel-craft; OR dye the uncoloured pillar (like the tile).
            File.WriteAllText(Path.Combine(chiselRoot, "smooth", "alabaster_" + color + "_pillar.json"),
                ChiselRecipe($"{MODID}:alabaster_tile/{color}", $"{MODID}:alabaster_pillar/{color}", "smooth"));
            File.WriteAllText(Path.Combine(alPillarDirs.rec, color + ".json"),
                ToolCraft($"{MODID}:alabaster_tile/{color}", "tfc:chisels", $"{MODID}:alabaster_pillar/{color}"));
            File.WriteAllText(Path.Combine(alPillarDirs.rec, color + "_dye.json"),
                BarrelDye($"{MODID}:alabaster_pillar", color, $"{MODID}:alabaster_pillar/{color}"));
            // Shapes off the tile: chisel (stair/slab; no wall), crafting + stonecutting (all three).
            File.WriteAllText(Path.Combine(chiselRoot, "stair", "alabaster_" + color + "_stairs.json"),
                ChiselRecipe($"{MODID}:alabaster_tile/{color}", $"{MODID}:alabaster_tile_stairs/{color}", "stair"));
            File.WriteAllText(Path.Combine(chiselRoot, "slab", "alabaster_" + color + "_slab.json"),
                ChiselRecipe($"{MODID}:alabaster_tile/{color}", $"{MODID}:alabaster_tile_slab/{color}", "slab", $"{MODID}:alabaster_tile_slab/{color}"));
            File.WriteAllText(Path.Combine(alStDirs.rec, color + ".json"), ShapeCraft("alabaster_tile", "alabaster_tile_stairs", color, "[ \"X  \", \"XX \", \"XXX\" ]", 8));
            File.WriteAllText(Path.Combine(alStDirs.rec, color + "_stonecutting.json"), ShapeStonecut("alabaster_tile", "alabaster_tile_stairs", color, 1));
            File.WriteAllText(Path.Combine(alSlDirs.rec, color + ".json"), ShapeCraft("alabaster_tile", "alabaster_tile_slab", color, "[ \"XXX\" ]", 6));
            File.WriteAllText(Path.Combine(alSlDirs.rec, color + "_stonecutting.json"), ShapeStonecut("alabaster_tile", "alabaster_tile_slab", color, 2));
            File.WriteAllText(Path.Combine(alWlDirs.rec, color + ".json"), ShapeCraft("alabaster_tile", "alabaster_tile_wall", color, "[ \"XXX\", \"XXX\" ]", 6));
            File.WriteAllText(Path.Combine(alWlDirs.rec, color + "_stonecutting.json"), ShapeStonecut("alabaster_tile", "alabaster_tile_wall", color, 1));
            alab++;
            Console.WriteLine($"  alabaster_tile/{color} (+pillar, +stairs/slab/wall, +recipes)");
        }
        return alab;
    }

    // Pillar (cube_column, RotatedPillarBlock) — axis variants like vanilla purpur_pillar; `_top` texture on the ends.
    static string PillarBlockstate(string color) =>
        $$"""
        {
          "variants": {
            "axis=x": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}", "x": 90, "y": 90 },
            "axis=y": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}" },
            "axis=z": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}", "x": 90 }
          }
        }
        """;

    static string PillarModel(string color) =>
        $$"""
        {
          "parent": "minecraft:block/cube_column",
          "textures": {
            "end": "{{MODID}}:block/alabaster_pillar/{{color}}_top",
            "side": "{{MODID}}:block/alabaster_pillar/{{color}}"
          }
        }
        """;

    // TFC sealed-barrel dye: the (uncoloured) base item + 25 mB of a colour's dye fluid -> the coloured item.
    // Matches TFC's data/tfc/recipes/barrel/dye/* for alabaster exactly (duration 1000).
    static string BarrelDye(string inputItem, string color, string outputItem) =>
        $$"""{ "type": "tfc:barrel_sealed", "input_item": { "ingredient": { "item": "{{inputItem}}" } }, "input_fluid": { "ingredient": "tfc:{{color}}_dye", "amount": 25 }, "output_item": { "item": "{{outputItem}}" }, "duration": 1000 }""";
}
