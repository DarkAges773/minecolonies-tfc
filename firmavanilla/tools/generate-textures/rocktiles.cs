// Rock tiles — the vanilla deepslate-tiles pattern recoloured through each TFC rock's brick palette (CLUT),
// plus grain + (chalk) seam-darken. Two variants per rock from two vanilla patterns: "tiles" (deepslate_tiles)
// and "cracked_tiles" (cracked_deepslate_tiles). Off the plain tile: stairs / slab / wall, matching vanilla's
// deepslate-tile family. Recipes mirror TFC's brick-shape family — crafting + stonecutting + TFC chisel
// (chiseled rock -> tile, tile -> stairs/slab; no chisel for walls).

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class RockTiles
{
    public static int Generate()
    {
        var tlDirs = TileDirs("tiles", withRecipe: true);           // plain tiles: chiseled + chisel (table), or in-world chisel
        var ckDirs = TileDirs("cracked_tiles", withRecipe: true);   // cracked tiles: tile + hammer (table), like TFC cracked bricks

        // Derived shapes off the plain tiles (stairs / slab / wall), matching vanilla's deepslate-tile family. They
        // reuse the plain tile texture on every face, so only JSON is emitted — no texture dir.
        var stDirs = DerivedDirs("tile_stairs");
        var slDirs = DerivedDirs("tile_slab");
        var wlDirs = DerivedDirs("tile_wall");

        // Recipes mirror what TFC ships for its brick shape family — all three paths: vanilla crafting + stonecutting
        // (the shapes below), and TFC chisel (tfc:chisel, here). The plain tile is obtained ONLY by chiselling TFC's
        // `chiseled` rock (extending TFC's own bricks->chiseled smooth-chisel chain — TFC's chiseled likewise has no
        // craft/stonecut). Off the tile: stairs/slab via chisel stair/slab mode too; walls have no TFC chisel mode
        // (craft+stonecut only, as in TFC). firmavanilla hard-depends on TFC, so tfc:chisel recipes always load.
        string chiselRoot = Path.Combine(resRoot, "data", MODID, "recipes", "chisel");
        foreach (var m in new[] { "smooth", "stair", "slab" }) Directory.CreateDirectory(Path.Combine(chiselRoot, m));

        using var deepslateTiles = Load("vanilla", "deepslate_tiles.png");
        using var crackedDeepslateTiles = Load("vanilla", "cracked_deepslate_tiles.png");

        // Grain masks (face vs mortar/shadow) — file-based and hand-editable: white = grain applied (tile faces), black =
        // skipped (mortar grooves, and on the cracked mask the crack lines too). One mask per vanilla pattern, since the
        // plain and cracked layouts differ: `grain_mask.png` (from deepslate_tiles) and `grain_mask_cracked.png` (from
        // cracked_deepslate_tiles, so the grain/seam-darken follow the actual cracks). Auto-built from granite's tile
        // structure (shared by every rock — the CLUT preserves the pattern's luminance ordering) when missing, or on
        // demand with `dotnet run generate.cs -- regen-mask`; otherwise the on-disk (possibly hand-edited) mask is used.
        var grainMask = LoadOrBuildMask("grain_mask.png", deepslateTiles);
        var crackedMask = LoadOrBuildMask("grain_mask_cracked.png", crackedDeepslateTiles);

        int tiles = 0;
        foreach (var rock in ROCKS)
        {
            using (var side = RenderTile(rock, deepslateTiles, grainMask)) side.Save(Path.Combine(tlDirs.tex, rock + ".png"));
            File.WriteAllText(Path.Combine(tlDirs.bs, rock + ".json"), TilesBlockstate("tiles", rock));
            File.WriteAllText(Path.Combine(tlDirs.model, rock + ".json"), TilesModel("tiles", rock));
            File.WriteAllText(Path.Combine(tlDirs.item, rock + ".json"), TilesItemModel("tiles", rock));
            File.WriteAllText(Path.Combine(tlDirs.loot, rock + ".json"), TilesLoot("tiles", rock));
            // Table twin of the smooth chisel: TFC chiseled + a chisel (damaged, not consumed) -> tile.
            File.WriteAllText(Path.Combine(tlDirs.rec, rock + ".json"),
                ToolCraft($"tfc:rock/chiseled/{rock}", "tfc:chisels", $"{MODID}:tiles/{rock}"));

            using (var side = RenderTile(rock, crackedDeepslateTiles, crackedMask)) side.Save(Path.Combine(ckDirs.tex, rock + ".png"));
            File.WriteAllText(Path.Combine(ckDirs.bs, rock + ".json"), TilesBlockstate("cracked_tiles", rock));
            File.WriteAllText(Path.Combine(ckDirs.model, rock + ".json"), TilesModel("cracked_tiles", rock));
            File.WriteAllText(Path.Combine(ckDirs.item, rock + ".json"), TilesItemModel("cracked_tiles", rock));
            File.WriteAllText(Path.Combine(ckDirs.loot, rock + ".json"), TilesLoot("cracked_tiles", rock));
            // Like TFC cracked bricks: tile + a hammer (damaged, not consumed) -> cracked tile.
            File.WriteAllText(Path.Combine(ckDirs.rec, rock + ".json"),
                ToolCraft($"{MODID}:tiles/{rock}", "tfc:hammers", $"{MODID}:cracked_tiles/{rock}"));

            // Stairs / slab / wall off the plain tiles (no new textures — all reference block/tiles/<rock>).
            File.WriteAllText(Path.Combine(stDirs.bs, rock + ".json"), StairsBlockstate("tile_stairs", rock));
            foreach (var (suffix, body) in StairsModels("tiles", rock)) File.WriteAllText(Path.Combine(stDirs.model, rock + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(stDirs.item, rock + ".json"), ItemParent("tile_stairs", rock, ""));
            File.WriteAllText(Path.Combine(stDirs.loot, rock + ".json"), SelfLoot("tile_stairs", rock));

            File.WriteAllText(Path.Combine(slDirs.bs, rock + ".json"), SlabBlockstate("tile_slab", "tiles", rock));
            foreach (var (suffix, body) in SlabModels("tiles", rock)) File.WriteAllText(Path.Combine(slDirs.model, rock + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(slDirs.item, rock + ".json"), ItemParent("tile_slab", rock, ""));
            File.WriteAllText(Path.Combine(slDirs.loot, rock + ".json"), SlabLoot("tile_slab", rock));

            File.WriteAllText(Path.Combine(wlDirs.bs, rock + ".json"), WallBlockstate("tile_wall", rock));
            foreach (var (suffix, body) in WallModels("tiles", rock)) File.WriteAllText(Path.Combine(wlDirs.model, rock + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(wlDirs.item, rock + ".json"), ItemParent("tile_wall", rock, "_inventory"));
            File.WriteAllText(Path.Combine(wlDirs.loot, rock + ".json"), SelfLoot("tile_wall", rock));

            // Shape recipes — crafting + stonecutting off the plain tile, matching TFC's brick-shape counts.
            File.WriteAllText(Path.Combine(stDirs.rec, rock + ".json"), ShapeCraft("tiles", "tile_stairs", rock, "[ \"X  \", \"XX \", \"XXX\" ]", 8));
            File.WriteAllText(Path.Combine(stDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tiles", "tile_stairs", rock, 1));
            File.WriteAllText(Path.Combine(slDirs.rec, rock + ".json"), ShapeCraft("tiles", "tile_slab", rock, "[ \"XXX\" ]", 6));
            File.WriteAllText(Path.Combine(slDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tiles", "tile_slab", rock, 2));
            File.WriteAllText(Path.Combine(wlDirs.rec, rock + ".json"), ShapeCraft("tiles", "tile_wall", rock, "[ \"XXX\", \"XXX\" ]", 6));
            File.WriteAllText(Path.Combine(wlDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tiles", "tile_wall", rock, 1));

            // TFC chisel paths: chiseled rock -> tile (smooth), tile -> stairs (stair) / slab (slab). No chisel for walls.
            File.WriteAllText(Path.Combine(chiselRoot, "smooth", rock + "_tiles.json"),
                ChiselRecipe($"tfc:rock/chiseled/{rock}", $"{MODID}:tiles/{rock}", "smooth"));
            File.WriteAllText(Path.Combine(chiselRoot, "stair", rock + "_tile_stairs.json"),
                ChiselRecipe($"{MODID}:tiles/{rock}", $"{MODID}:tile_stairs/{rock}", "stair"));
            File.WriteAllText(Path.Combine(chiselRoot, "slab", rock + "_tile_slab.json"),
                ChiselRecipe($"{MODID}:tiles/{rock}", $"{MODID}:tile_slab/{rock}", "slab", $"{MODID}:tile_slab/{rock}"));

            tiles++;
            Console.WriteLine($"  tiles/{rock} (+cracked, +stairs/slab/wall, +chisel/craft/stonecut)");
        }

        grainMask.Dispose();
        crackedMask.Dispose();
        return tiles;
    }

    // Auto-build the grain mask from granite's tile structure (shared by every rock) when missing, or on demand with
    // `-- regen-mask`; otherwise use the on-disk (possibly hand-edited) mask.
    static Image<Rgba32> LoadOrBuildMask(string fileName, Image<Rgba32> pattern)
    {
        string path = Path.Combine(scriptDir, fileName);
        if (args.Contains("regen-mask") || !File.Exists(path))
        {
            var built = BuildGrainMask(pattern);
            built.SaveAsPng(path);
            Console.WriteLine($"  grain mask (re)generated -> {path}");
            return built;
        }
        Console.WriteLine($"  grain mask loaded -> {path}");
        return Image.Load<Rgba32>(path);
    }

    // Render one rock's tile face: vanilla `pattern` CLUT-recoloured through the rock's ramp, then grain + (chalk)
    // seam-darken, both gated by `mask` (its matching face/mortar map). Caller owns/disposes the returned image.
    static Image<Rgba32> RenderTile(string rock, Image<Rgba32> pattern, Image<Rgba32> mask)
    {
        string rampVariant = BRICK_LUT_ROCKS.Contains(rock) ? "rock_bricks" : "rock_smooth";
        using var ramp = Load("tfc", Path.Combine(rampVariant, rock + ".png"));
        using var smooth = Load("tfc", Path.Combine("rock_smooth", rock + ".png"));
        using var src = pattern.Clone(c => c.Resize(ramp.Width, ramp.Height));
        var side = ClutSide(src, ramp, ramp.Width, ramp.Height);
        // Grain overlay: add each rock's own bright mineral grain (high-pass of its smooth texture) onto the tile
        // faces. Self-scaling — uniform rocks (marble) get little, speckled rocks (granite) get more.
        GrainOverlay(side, smooth, rock == "granite" ? GRAIN_STRENGTH_GRANITE : GRAIN_STRENGTH, mask);
        if (rock == "chalk") SeamDarken(side, mask, SEAM_DARKEN_CHALK);   // chalk's seams are too faint; deepen them
        return side;
    }
}
