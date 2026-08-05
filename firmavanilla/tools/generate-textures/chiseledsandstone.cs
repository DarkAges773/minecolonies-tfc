// Chiseled sandstone — vanilla's chiseled relief (creeper face / wither motif) recoloured to TFC's 7 sand
// colours via the CLUT palette remap (or MULTIPLY relief-transfer; see Gen.MODE). Caps reuse TFC's own smooth
// sandstone top texture; only the chiseled side face is generated. Ships blockstate/model/loot + the TFC
// chisel recipes (table chisel-craft + in-world smooth-mode chisel off TFC's cut_sandstone).

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class ChiseledSandstone
{
    public static int Generate()
    {
        string texDir    = Path.Combine(resRoot, "assets", MODID, "textures", "block", "chiseled_sandstone");
        string bsDir     = Path.Combine(resRoot, "assets", MODID, "blockstates", "chiseled_sandstone");
        string bModelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "chiseled_sandstone");
        string iModelDir = Path.Combine(resRoot, "assets", MODID, "models", "item", "chiseled_sandstone");
        string lootDir   = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "chiseled_sandstone");
        string recipeDir = Path.Combine(resRoot, "data", MODID, "recipes", "chiseled_sandstone");
        // In-world chisel recipes live alongside the rock-tile ones under recipes/chisel/smooth/.
        string chiselSmoothDir = Path.Combine(resRoot, "data", MODID, "recipes", "chisel", "smooth");

        foreach (var d in new[] { texDir, bsDir, bModelDir, iModelDir, lootDir, recipeDir, chiselSmoothDir })
            Directory.CreateDirectory(d);

        // Load the two vanilla relief/flat pairs once.
        var motifSources = new Dictionary<string, (Image<Rgba32> relief, Image<Rgba32> flat)>
        {
            ["creeper"] = (Load("vanilla", "chiseled_sandstone.png"),     Load("vanilla", "cut_sandstone.png")),
            ["wither"]  = (Load("vanilla", "chiseled_red_sandstone.png"), Load("vanilla", "cut_red_sandstone.png")),
        };

        int done = 0;
        foreach (var (color, motif) in MOTIF)
        {
            using var tfcBase = Load("tfc", Path.Combine("cut_sandstone", color + ".png"));
            int w = tfcBase.Width, h = tfcBase.Height;

            // Resize the vanilla relief + flat to the TFC base resolution so the maths is pixel-aligned.
            using var relief = motifSources[motif].relief.Clone(c => c.Resize(w, h));
            using var flat   = motifSources[motif].flat.Clone(c => c.Resize(w, h));

            // Build the chiseled side face via the selected technique.
            using var side = MODE == Mode.Clut
                ? ClutSide(relief, tfcBase, w, h)             // repaint vanilla's emblem through TFC's palette ramp
                : MultiplySide(relief, flat, tfcBase, w, h);  // modulate TFC's grain by the vanilla relief ratio

            // Only the chiseled side is generated. The top/bottom reuse TFC's own smooth sandstone texture directly
            // (referenced from the block model) — no point shipping a verbatim copy of TFC's asset.
            side.Save(Path.Combine(texDir, color + ".png"));

            File.WriteAllText(Path.Combine(bsDir, color + ".json"), Blockstate(color));
            File.WriteAllText(Path.Combine(bModelDir, color + ".json"), BlockModel(color));
            File.WriteAllText(Path.Combine(iModelDir, color + ".json"), ItemModel(color));
            File.WriteAllText(Path.Combine(lootDir, color + ".json"), LootTable(color));
            // Same TFC pattern as the rock tiles: cut sandstone + a chisel -> chiseled sandstone, both at a table
            // (damaged-tool shapeless craft) and in-world (tfc:chisel smooth mode). TFC ships no sandstone chisel, so
            // the smooth-mode recipe is free to claim cut_sandstone.
            File.WriteAllText(Path.Combine(recipeDir, color + ".json"),
                ToolCraft($"tfc:cut_sandstone/{color}", "tfc:chisels", $"{MODID}:chiseled_sandstone/{color}"));
            File.WriteAllText(Path.Combine(chiselSmoothDir, "sandstone_" + color + ".json"),
                ChiselRecipe($"tfc:cut_sandstone/{color}", $"{MODID}:chiseled_sandstone/{color}", "smooth"));
            done++;
            Console.WriteLine($"  chiseled_sandstone/{color,-6}  ({motif} motif)");
        }

        foreach (var (_, pair) in motifSources) { pair.relief.Dispose(); pair.flat.Dispose(); }
        return done;
    }

    static string Blockstate(string c) =>
        $$"""
        {
          "variants": {
            "": { "model": "{{MODID}}:block/chiseled_sandstone/{{c}}" }
          }
        }
        """;

    // cube_column: the chiseled emblem on the 4 sides, TFC's SMOOTH sandstone on top/bottom — matching vanilla,
    // whose chiseled sandstone caps with the smooth sandstone_top, not the cut face. TFC's smooth_sandstone is
    // `tfc:block/sandstone/top/<colour>` (reused directly, not copied — firmavanilla hard-depends on TFC).
    static string BlockModel(string c) =>
        $$"""
        {
          "parent": "minecraft:block/cube_column",
          "textures": {
            "end": "tfc:block/sandstone/top/{{c}}",
            "side": "{{MODID}}:block/chiseled_sandstone/{{c}}"
          }
        }
        """;

    static string ItemModel(string c) =>
        $$"""
        { "parent": "{{MODID}}:block/chiseled_sandstone/{{c}}" }
        """;

    static string LootTable(string c) =>
        $$"""
        {
          "type": "minecraft:block",
          "pools": [
            {
              "rolls": 1,
              "entries": [ { "type": "minecraft:item", "name": "{{MODID}}:chiseled_sandstone/{{c}}" } ],
              "conditions": [ { "condition": "minecraft:survives_explosion" } ]
            }
          ]
        }
        """;
}
