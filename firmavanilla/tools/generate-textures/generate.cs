#:package SixLabors.ImageSharp@3.1.10

// TFC Vanilla Building Blocks — chiseled-sandstone asset generator (.NET 10 file-based app).
//
// Run:  dotnet run generate.cs            (from this folder; needs .NET 10 SDK)
//
// What it does, per TFC sand colour: takes vanilla's chiseled relief (the "creeper" face on normal
// sandstone, the "wither" motif on red sandstone) and recolours it to TFC's palette via one of two
// techniques (see `MODE`):
//   CLUT     — repaint vanilla's emblem through a luminance->colour ramp sampled from TFC's real
//              cut_sandstone. Every output pixel's colour comes straight from TFC's palette, so the emblem
//              stays crisp and the colours are authentically TFC. (Default.)
//   MULTIPLY — out = tfc_base * (vanilla_chiseled / vanilla_flat): modulate TFC's actual grain by the
//              vanilla relief ratio. Keeps TFC's spatial grain but the emblem reads fainter and frame
//              differences between the two vanilla designs leak in as edge artifacts.
// It also writes the matching blockstate / block+item model / loot table / recipe / mineable-tag JSON.
//
// Source PNGs are read from ./input (see input/README.md for the exact files to drop in). Generated assets
// are written into ../../src/main/resources (checked in).

using System.Runtime.CompilerServices;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

// ---- config ---------------------------------------------------------------

// TFC's seven sand colours -> which vanilla chiseled motif each one wears.
//   creeper = vanilla chiseled_sandstone (normal),  wither = vanilla chiseled_red_sandstone.
var MOTIF = new Dictionary<string, string>
{
    ["yellow"] = "creeper",
    ["white"]  = "creeper",
    ["pink"]   = "creeper",
    ["green"]  = "creeper",
    ["red"]    = "wither",
    ["black"]  = "wither",
    ["brown"]  = "wither",
};

const string MODID = "firmavanilla";

// Texture technique — see the header comment. Clut (palette remap) is crisp + authentically TFC-coloured;
// Multiply (relief-transfer) keeps TFC's grain but the emblem is fainter.
const Mode MODE = Mode.Clut;

// Clamp the relief multiplier (MULTIPLY mode only) so dark vanilla pixels can't blow out / crush the TFC base.
const float RATIO_MIN = 0.45f, RATIO_MAX = 1.55f;

// TFC's 20 rock types, for the deepslate-tiles-style "tiles" blocks (CLUT-recoloured per rock's brick palette).
var ROCKS = new[] { "andesite", "basalt", "chalk", "chert", "claystone", "conglomerate", "dacite", "diorite",
    "dolomite", "gabbro", "gneiss", "granite", "limestone", "marble", "phyllite", "quartzite", "rhyolite",
    "schist", "shale", "slate" };

// Most rocks build their CLUT ramp from the flatter `rock_smooth` (cleaner colour, stronger grain, softer
// seams). These few read better with `rock_bricks`' built-in mortar contrast, so ramp them from bricks instead.
var BRICK_LUT_ROCKS = new HashSet<string> { "basalt", "claystone", "conglomerate", "granite" };

// Grain overlay on the tiles: the bright high-pass of each rock's `smooth` texture (its mineral flecks),
// amplified and added onto the tile faces. Blur radius (smaller = sharper flecks), strength (intensity), and
// the mask margin below the tile's mean luminance (grain lands on faces/highlights, not mortar/shadows).
const float GRAIN_BLUR = 0.8f;
const float GRAIN_STRENGTH = 1.5f;          // default per-rock grain amplification
const float GRAIN_STRENGTH_GRANITE = 1.2f;  // granite is already speckled -> a touch less
const int GRAIN_MARGIN = 12;
// Chalk's brick palette is so light/low-contrast that its tile seams nearly vanish after the CLUT. Deepen the
// mask's mortar (black) pixels for chalk only to restore seam contrast. <1 darkens; 1.0 = off.
const float SEAM_DARKEN_CHALK = 1.0f;

string scriptDir = ScriptDir();
string inputDir  = Path.Combine(scriptDir, "input");
string resRoot   = Path.GetFullPath(Path.Combine(scriptDir, "..", "..", "src", "main", "resources"));

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

// minecraft:mineable/pickaxe — chiseled sandstone + rock tiles (all shapes), so they mine/drop with a pickaxe.
string mineableDir = Path.Combine(resRoot, "data", "minecraft", "tags", "blocks", "mineable");
Directory.CreateDirectory(mineableDir);
File.WriteAllText(Path.Combine(mineableDir, "pickaxe.json"), MineableTag());

// Vanilla shape tags so our tile stairs/slabs/walls behave like vanilla ones (notably minecraft:walls, which
// the wall connection logic keys off). Additive (replace:false).
WriteTag("minecraft", "blocks", "stairs", ShapeTag("tile_stairs"));
WriteTag("minecraft", "blocks", "slabs", ShapeTag("tile_slab"));
WriteTag("minecraft", "blocks", "walls", ShapeTag("tile_wall"));

// ---- decorative bookshelves (no images; models reference each source mod's textures directly) ----------
// TFC woods are always present; AFC/Beneath woods only exist when those mods are loaded (the block code
// gates registration on ModList). Their client assets ship unconditionally (unused when unregistered); the
// recipes carry a forge:mod_loaded condition and the axe-tag entries are required:false, so nothing errors.
var BOOKSHELF = new (string ns, string[] woods)[]
{
    ("tfc", new[] { "acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
                    "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow" }),
    ("afc", new[] { "baobab", "cypress", "eucalyptus", "fig", "hevea", "ipe", "ironwood", "mahogany", "teak", "tualang" }),
    ("beneath", new[] { "crimson", "warped" }),
};
string bkTexDir   = Path.Combine(resRoot, "assets", MODID, "textures", "block", "bookshelf");
string bkBsDir    = Path.Combine(resRoot, "assets", MODID, "blockstates", "bookshelf");
string bkModelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "bookshelf");
string bkItemDir  = Path.Combine(resRoot, "assets", MODID, "models", "item", "bookshelf");
string bkRecDir   = Path.Combine(resRoot, "data", MODID, "recipes", "bookshelf");
foreach (var d in new[] { bkTexDir, bkBsDir, bkModelDir, bkItemDir, bkRecDir }) Directory.CreateDirectory(d);

// Vanilla-style book spines (authored, with transparency where there's no book) — overlaid on each wood's
// empty bookshelf frame so the books read like a vanilla bookshelf rather than the chiseled 6-slot look.
using var bookOverlay = Load("vanilla", "bookshelf_overlay.png");

var tagEntries = new List<string>();
int books = 0;
foreach (var (ns, woods) in BOOKSHELF)
foreach (var wood in woods)
{
    // Side texture = the books overlay composited (source-over) onto this wood's empty frame.
    using (var empty = Load(ns, Path.Combine("bookshelf_empty", wood + ".png")))
    using (var ov = bookOverlay.Clone(c => c.Resize(empty.Width, empty.Height)))
    {
        empty.Mutate(c => c.DrawImage(ov, PixelColorBlendingMode.Normal, 1f));
        empty.Save(Path.Combine(bkTexDir, wood + ".png"));
    }

    File.WriteAllText(Path.Combine(bkBsDir, wood + ".json"), BookshelfBlockstate(wood));
    File.WriteAllText(Path.Combine(bkModelDir, wood + ".json"), BookshelfModel(ns, wood));
    File.WriteAllText(Path.Combine(bkItemDir, wood + ".json"), BookshelfItemModel(wood));
    File.WriteAllText(Path.Combine(bkRecDir, wood + ".json"), BookshelfRecipe(ns, wood));
    tagEntries.Add(ns == "tfc"
        ? $"    \"{MODID}:bookshelf/{wood}\""
        : $"    {{ \"id\": \"{MODID}:bookshelf/{wood}\", \"required\": false }}");
    books++;
}
// Tag membership matching vanilla minecraft:bookshelf, so the decorative blocks behave the same: mineable/axe,
// enchantment_power_provider (enchanting-table power) and forge:bookshelves (block + item). replace:false
// appends to the existing tags; AFC/Beneath entries are required:false so they're skipped when the mod is absent.
string tagBody = "{\n  \"replace\": false,\n  \"values\": [\n" + string.Join(",\n", tagEntries) + "\n  ]\n}\n";
WriteTag("minecraft", "blocks", "mineable/axe", tagBody);
WriteTag("minecraft", "blocks", "enchantment_power_provider", tagBody);
WriteTag("forge", "blocks", "bookshelves", tagBody);
WriteTag("forge", "items", "bookshelves", tagBody);

// ---- rock tiles (CLUT: the vanilla deepslate-tiles pattern recoloured through each rock's brick palette) ----
// Two variants per rock, each from a different vanilla pattern: "tiles" (deepslate_tiles) and "cracked_tiles"
// (cracked_deepslate_tiles). Both share the same per-rock CLUT ramp + grain + chalk seam-darken pipeline.
(string tex, string bs, string model, string item, string loot, string rec) TileDirs(string kind, bool withRecipe)
{
    var d = (
        tex:   Path.Combine(resRoot, "assets", MODID, "textures", "block", kind),
        bs:    Path.Combine(resRoot, "assets", MODID, "blockstates", kind),
        model: Path.Combine(resRoot, "assets", MODID, "models", "block", kind),
        item:  Path.Combine(resRoot, "assets", MODID, "models", "item", kind),
        loot:  Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", kind),
        rec:   Path.Combine(resRoot, "data", MODID, "recipes", kind));
    var made = withRecipe ? new[] { d.tex, d.bs, d.model, d.item, d.loot, d.rec }
                          : new[] { d.tex, d.bs, d.model, d.item, d.loot };
    foreach (var p in made) Directory.CreateDirectory(p);
    return d;
}
var tlDirs = TileDirs("tiles", withRecipe: true);           // plain tiles: chiseled + chisel (table), or in-world chisel
var ckDirs = TileDirs("cracked_tiles", withRecipe: true);   // cracked tiles: tile + hammer (table), like TFC cracked bricks

// Derived shapes off the plain tiles (stairs / slab / wall), matching vanilla's deepslate-tile family. They
// reuse the plain tile texture on every face, so only JSON is emitted — no texture dir.
(string bs, string model, string item, string loot, string rec) DerivedDirs(string kind)
{
    var d = (
        bs:    Path.Combine(resRoot, "assets", MODID, "blockstates", kind),
        model: Path.Combine(resRoot, "assets", MODID, "models", "block", kind),
        item:  Path.Combine(resRoot, "assets", MODID, "models", "item", kind),
        loot:  Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", kind),
        rec:   Path.Combine(resRoot, "data", MODID, "recipes", kind));
    foreach (var p in new[] { d.bs, d.model, d.item, d.loot, d.rec }) Directory.CreateDirectory(p);
    return d;
}
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
Image<Rgba32> LoadOrBuildMask(string fileName, Image<Rgba32> pattern)
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
var grainMask = LoadOrBuildMask("grain_mask.png", deepslateTiles);
var crackedMask = LoadOrBuildMask("grain_mask_cracked.png", crackedDeepslateTiles);

// Render one rock's tile face: vanilla `pattern` CLUT-recoloured through the rock's ramp, then grain + (chalk)
// seam-darken, both gated by `mask` (its matching face/mortar map). Caller owns/disposes the returned image.
Image<Rgba32> RenderTile(string rock, Image<Rgba32> pattern, Image<Rgba32> mask)
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
    File.WriteAllText(Path.Combine(stDirs.bs, rock + ".json"), StairsBlockstate(rock));
    foreach (var (suffix, body) in StairsModels(rock)) File.WriteAllText(Path.Combine(stDirs.model, rock + suffix + ".json"), body);
    File.WriteAllText(Path.Combine(stDirs.item, rock + ".json"), ItemParent("tile_stairs", rock, ""));
    File.WriteAllText(Path.Combine(stDirs.loot, rock + ".json"), SelfLoot("tile_stairs", rock));

    File.WriteAllText(Path.Combine(slDirs.bs, rock + ".json"), SlabBlockstate(rock));
    foreach (var (suffix, body) in SlabModels(rock)) File.WriteAllText(Path.Combine(slDirs.model, rock + suffix + ".json"), body);
    File.WriteAllText(Path.Combine(slDirs.item, rock + ".json"), ItemParent("tile_slab", rock, ""));
    File.WriteAllText(Path.Combine(slDirs.loot, rock + ".json"), SlabLoot(rock));

    File.WriteAllText(Path.Combine(wlDirs.bs, rock + ".json"), WallBlockstate(rock));
    foreach (var (suffix, body) in WallModels(rock)) File.WriteAllText(Path.Combine(wlDirs.model, rock + suffix + ".json"), body);
    File.WriteAllText(Path.Combine(wlDirs.item, rock + ".json"), ItemParent("tile_wall", rock, "_inventory"));
    File.WriteAllText(Path.Combine(wlDirs.loot, rock + ".json"), SelfLoot("tile_wall", rock));

    // Shape recipes — crafting + stonecutting off the plain tile, matching TFC's brick-shape counts.
    File.WriteAllText(Path.Combine(stDirs.rec, rock + ".json"), ShapeCraft("tile_stairs", rock, "[ \"X  \", \"XX \", \"XXX\" ]", 8));
    File.WriteAllText(Path.Combine(stDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tile_stairs", rock, 1));
    File.WriteAllText(Path.Combine(slDirs.rec, rock + ".json"), ShapeCraft("tile_slab", rock, "[ \"XXX\" ]", 6));
    File.WriteAllText(Path.Combine(slDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tile_slab", rock, 2));
    File.WriteAllText(Path.Combine(wlDirs.rec, rock + ".json"), ShapeCraft("tile_wall", rock, "[ \"XXX\", \"XXX\" ]", 6));
    File.WriteAllText(Path.Combine(wlDirs.rec, rock + "_stonecutting.json"), ShapeStonecut("tile_wall", rock, 1));

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
foreach (var (_, pair) in motifSources) { pair.relief.Dispose(); pair.flat.Dispose(); }
Console.WriteLine($"Done: {done} chiseled-sandstone + {books} bookshelf + {tiles} rock-tiles variants written to {resRoot}");

// ---- helpers --------------------------------------------------------------

// --- technique: MULTIPLY (relief-transfer) ----------------------------------
// out = tfc_base * (vanilla_chiseled / vanilla_flat), per channel. Keeps TFC's spatial grain.
Image<Rgba32> MultiplySide(Image<Rgba32> relief, Image<Rgba32> flat, Image<Rgba32> tfcBase, int w, int h)
{
    var img = new Image<Rgba32>(w, h);
    for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++)
    {
        Rgba32 r = relief[x, y], f = flat[x, y], b = tfcBase[x, y];
        img[x, y] = new Rgba32(MulByRatio(b.R, r.R, f.R), MulByRatio(b.G, r.G, f.G), MulByRatio(b.B, r.B, f.B), r.A);
    }
    return img;
}

byte MulByRatio(byte baseVal, byte reliefVal, byte flatVal)
{
    float ratio = flatVal == 0 ? 1f : (float) reliefVal / flatVal;
    ratio = Math.Clamp(ratio, RATIO_MIN, RATIO_MAX);
    return (byte) Math.Clamp((int) MathF.Round(baseVal * ratio), 0, 255);
}

// --- technique: CLUT (palette remap) ----------------------------------------
// Repaint vanilla's chiseled art through a luminance->colour ramp sampled from TFC's real cut_sandstone, so
// each output pixel's colour comes straight from TFC's palette and the emblem stays crisp.
//
// KEY STEP — normalize. We don't index the ramp by the vanilla pixel's RAW luminance: a TFC colour whose
// palette sits in a narrow band (e.g. black is very dark, green/pink are low-contrast) doesn't overlap
// vanilla's luminance range, so every pixel would clamp to one endpoint and the emblem flattens to a plain
// colour. Instead we map each vanilla pixel from vanilla's luminance range [vMin,vMax] onto the TFC
// palette's actual range [tMin,tMax], then look up — so the full emblem contrast always spans whatever
// tonal range the colour has. (flat is unused — ramp is from tfcBase.)
Image<Rgba32> ClutSide(Image<Rgba32> relief, Image<Rgba32> tfcBase, int w, int h)
{
    Rgba32[] ramp = BuildPaletteRamp(tfcBase);
    (int tMin, int tMax) = LumRange(tfcBase);
    (int vMin, int vMax) = LumRange(relief);
    int vSpan = Math.Max(1, vMax - vMin);

    var img = new Image<Rgba32>(w, h);
    for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++)
    {
        Rgba32 r = relief[x, y];
        float p = (float) (Lum(r.R, r.G, r.B) - vMin) / vSpan;          // 0..1 within vanilla's range
        int tl = Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255);
        Rgba32 c = ramp[tl];
        img[x, y] = new Rgba32(c.R, c.G, c.B, r.A);
    }
    return img;
}

// Luminance range (min,max) over a texture's opaque pixels.
(int min, int max) LumRange(Image<Rgba32> tex)
{
    int min = 255, max = 0; bool any = false;
    for (int y = 0; y < tex.Height; y++)
    for (int x = 0; x < tex.Width; x++)
    {
        Rgba32 p = tex[x, y];
        if (p.A < 8) continue;
        int L = Lum(p.R, p.G, p.B);
        if (L < min) min = L;
        if (L > max) max = L;
        any = true;
    }
    return any ? (min, max) : (0, 255);
}

// 256-entry luminance -> colour table sampled from a texture: average the pixels at each luminance level,
// then linearly interpolate across luminances that have no sample (clamping at the ends).
Rgba32[] BuildPaletteRamp(Image<Rgba32> tex)
{
    var sumR = new long[256]; var sumG = new long[256]; var sumB = new long[256]; var cnt = new long[256];
    for (int y = 0; y < tex.Height; y++)
    for (int x = 0; x < tex.Width; x++)
    {
        Rgba32 p = tex[x, y];
        if (p.A < 8) continue;
        int L = Lum(p.R, p.G, p.B);
        sumR[L] += p.R; sumG[L] += p.G; sumB[L] += p.B; cnt[L]++;
    }
    var has = new bool[256]; var rr = new int[256]; var gg = new int[256]; var bb = new int[256];
    for (int L = 0; L < 256; L++)
        if (cnt[L] > 0) { has[L] = true; rr[L] = (int) (sumR[L] / cnt[L]); gg[L] = (int) (sumG[L] / cnt[L]); bb[L] = (int) (sumB[L] / cnt[L]); }

    var ramp = new Rgba32[256];
    for (int L = 0; L < 256; L++)
    {
        if (has[L]) { ramp[L] = new Rgba32((byte) rr[L], (byte) gg[L], (byte) bb[L], 255); continue; }
        int lo = L; while (lo >= 0 && !has[lo]) lo--;
        int hi = L; while (hi < 256 && !has[hi]) hi++;
        if (lo < 0 && hi >= 256)  ramp[L] = new Rgba32(0, 0, 0, 255);           // no data at all
        else if (lo < 0)          ramp[L] = new Rgba32((byte) rr[hi], (byte) gg[hi], (byte) bb[hi], 255);
        else if (hi >= 256)       ramp[L] = new Rgba32((byte) rr[lo], (byte) gg[lo], (byte) bb[lo], 255);
        else
        {
            float t = (float) (L - lo) / (hi - lo);
            ramp[L] = new Rgba32(
                (byte) Math.Round(rr[lo] + (rr[hi] - rr[lo]) * t),
                (byte) Math.Round(gg[lo] + (gg[hi] - gg[lo]) * t),
                (byte) Math.Round(bb[lo] + (bb[hi] - bb[lo]) * t),
                255);
        }
    }
    return ramp;
}

int Lum(int r, int g, int b) => Math.Clamp((int) Math.Round(0.299 * r + 0.587 * g + 0.114 * b), 0, 255);

// Overlay a rock's bright mineral grain onto the tile faces: the positive high-pass of its smooth texture
// (smooth - blur, bright parts only), amplified by GRAIN_STRENGTH and added — masked to pixels at/above
// (tile mean luminance - GRAIN_MARGIN) so it lands on faces/highlights and stays out of the mortar/shadows.
void GrainOverlay(Image<Rgba32> tile, Image<Rgba32> smoothSrc, float strength, Image<Rgba32> maskSrc)
{
    int w = tile.Width, h = tile.Height;
    using var sm = (smoothSrc.Width == w && smoothSrc.Height == h) ? smoothSrc.Clone() : smoothSrc.Clone(c => c.Resize(w, h));
    using var blur = sm.Clone(c => c.GaussianBlur(GRAIN_BLUR));
    using var mk = (maskSrc.Width == w && maskSrc.Height == h) ? maskSrc.Clone() : maskSrc.Clone(c => c.Resize(w, h));
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
    {
        Rgba32 m = mk[x, y];
        if (m.A < 128 || Lum(m.R, m.G, m.B) < 128) continue;   // mask black/transparent = skip (mortar/shadows)
        Rgba32 t = tile[x, y], s = sm[x, y], b = blur[x, y];
        tile[x, y] = new Rgba32(
            (byte) Math.Clamp(t.R + (int) MathF.Round(Math.Max(0, s.R - b.R) * strength), 0, 255),
            (byte) Math.Clamp(t.G + (int) MathF.Round(Math.Max(0, s.G - b.G) * strength), 0, 255),
            (byte) Math.Clamp(t.B + (int) MathF.Round(Math.Max(0, s.B - b.B) * strength), 0, 255),
            t.A);
    }
}

// Darken a tile's mortar pixels (mask black) by `factor` to deepen the seams — uses the same grain mask, the
// inverse region from GrainOverlay. Used to restore seam contrast on very low-contrast rocks (chalk).
void SeamDarken(Image<Rgba32> tile, Image<Rgba32> maskSrc, float factor)
{
    int w = tile.Width, h = tile.Height;
    using var mk = (maskSrc.Width == w && maskSrc.Height == h) ? maskSrc.Clone() : maskSrc.Clone(c => c.Resize(w, h));
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
    {
        Rgba32 m = mk[x, y];
        if (m.A >= 128 && Lum(m.R, m.G, m.B) >= 128) continue;   // white = face -> leave; darken only mortar (black)
        Rgba32 t = tile[x, y];
        tile[x, y] = new Rgba32(
            (byte) Math.Clamp((int) MathF.Round(t.R * factor), 0, 255),
            (byte) Math.Clamp((int) MathF.Round(t.G * factor), 0, 255),
            (byte) Math.Clamp((int) MathF.Round(t.B * factor), 0, 255),
            t.A);
    }
}

// Default grain mask for a given vanilla `pattern`: white where grain lands (tile faces), black on mortar/shadows
// (and, on the cracked pattern, the crack lines). Derived from granite's tile (whose luminance structure every
// rock shares), thresholded at tile-mean - GRAIN_MARGIN. Saved to disk once, then hand-editable; only
// `-- regen-mask` (or a missing file) rebuilds it.
Image<Rgba32> BuildGrainMask(Image<Rgba32> pattern)
{
    using var bricks = Load("tfc", Path.Combine("rock_bricks", "granite.png"));
    int w = bricks.Width, h = bricks.Height;
    using var src = pattern.Clone(c => c.Resize(w, h));
    using var tile = ClutSide(src, bricks, w, h);
    long sum = 0;
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { Rgba32 t = tile[x, y]; sum += Lum(t.R, t.G, t.B); }
    int faceMin = (int) (sum / (w * h)) - GRAIN_MARGIN;
    var mask = new Image<Rgba32>(w, h);
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
    {
        bool face = Lum(tile[x, y].R, tile[x, y].G, tile[x, y].B) >= faceMin;
        mask[x, y] = face ? new Rgba32((byte) 255, (byte) 255, (byte) 255, (byte) 255) : new Rgba32((byte) 0, (byte) 0, (byte) 0, (byte) 255);
    }
    return mask;
}

Image<Rgba32> Load(string sub, string file)
{
    string path = Path.Combine(inputDir, sub, file);
    if (!File.Exists(path))
        throw new FileNotFoundException($"Missing source texture: {path}\nSee {Path.Combine(inputDir, "README.md")}.");
    return Image.Load<Rgba32>(path);
}

static string ScriptDir([CallerFilePath] string path = "") => Path.GetDirectoryName(path)!;

string Blockstate(string c) =>
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
string BlockModel(string c) =>
    $$"""
    {
      "parent": "minecraft:block/cube_column",
      "textures": {
        "end": "tfc:block/sandstone/top/{{c}}",
        "side": "{{MODID}}:block/chiseled_sandstone/{{c}}"
      }
    }
    """;

string ItemModel(string c) =>
    $$"""
    { "parent": "{{MODID}}:block/chiseled_sandstone/{{c}}" }
    """;

string LootTable(string c) =>
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

string MineableTag()
{
    var ids = MOTIF.Keys.Select(c => $"    \"{MODID}:chiseled_sandstone/{c}\"")
        .Concat(ROCKS.Select(r => $"    \"{MODID}:tiles/{r}\""))
        .Concat(ROCKS.Select(r => $"    \"{MODID}:cracked_tiles/{r}\""))
        .Concat(ROCKS.Select(r => $"    \"{MODID}:tile_stairs/{r}\""))
        .Concat(ROCKS.Select(r => $"    \"{MODID}:tile_slab/{r}\""))
        .Concat(ROCKS.Select(r => $"    \"{MODID}:tile_wall/{r}\""));
    return ValuesTag(ids);
}

// {"replace":false,"values":[ ... ]} from an already-formatted (indented, quoted) id sequence.
string ValuesTag(IEnumerable<string> ids) =>
    "{\n  \"replace\": false,\n  \"values\": [\n" + string.Join(",\n", ids) + "\n  ]\n}\n";

// A vanilla shape tag (minecraft:blocks/{stairs|slabs|walls}) listing this rock-tile shape for all rocks, so
// our variants behave like vanilla ones (wall connection logic keys off minecraft:walls, etc.).
string ShapeTag(string kind) => ValuesTag(ROCKS.Select(r => $"    \"{MODID}:{kind}/{r}\""));

// ---- bookshelf JSON emitters ----------------------------------------------

string BookshelfBlockstate(string wood) =>
    $$"""
    {
      "variants": {
        "": { "model": "{{MODID}}:block/bookshelf/{{wood}}" }
      }
    }
    """;

// cube_column: our generated books-on-frame face on the 4 sides, the source mod's wood planks (referenced
// directly) on top/bottom — mirroring vanilla bookshelf (books on the sides, planks on the caps).
string BookshelfModel(string ns, string wood) =>
    $$"""
    {
      "parent": "minecraft:block/cube_column",
      "textures": {
        "end": "{{ns}}:block/wood/planks/{{wood}}",
        "side": "{{MODID}}:block/bookshelf/{{wood}}"
      }
    }
    """;

string BookshelfItemModel(string wood) =>
    $$"""
    { "parent": "{{MODID}}:block/bookshelf/{{wood}}" }
    """;

// 6 lumber + 3 books (vanilla bookshelf shape, but lumber instead of plank blocks — lumber exists for every
// wood with no exceptions). AFC/Beneath recipes carry a forge:mod_loaded condition so they're silently
// skipped (no error) when the mod — hence the lumber item and result block — is absent.
string BookshelfRecipe(string ns, string wood)
{
    string cond = ns == "tfc"
        ? ""
        : $"\n      \"conditions\": [ {{ \"type\": \"forge:mod_loaded\", \"modid\": \"{ns}\" }} ],";
    return $$"""
    {
      "type": "minecraft:crafting_shaped",{{cond}}
      "pattern": [ "LLL", "BBB", "LLL" ],
      "key": {
        "L": { "item": "{{ns}}:wood/lumber/{{wood}}" },
        "B": { "item": "minecraft:book" }
      },
      "result": { "item": "{{MODID}}:bookshelf/{{wood}}" }
    }
    """;
}

// ---- rock-tiles JSON emitters ----------------------------------------------

string TilesBlockstate(string kind, string rock) =>
    $$"""
    {
      "variants": {
        "": { "model": "{{MODID}}:block/{{kind}}/{{rock}}" }
      }
    }
    """;

// cube_all: the generated tiles texture on every face (vanilla deepslate_tiles is the same).
string TilesModel(string kind, string rock) =>
    $$"""
    {
      "parent": "minecraft:block/cube_all",
      "textures": { "all": "{{MODID}}:block/{{kind}}/{{rock}}" }
    }
    """;

string TilesItemModel(string kind, string rock) =>
    $$"""
    { "parent": "{{MODID}}:block/{{kind}}/{{rock}}" }
    """;

string TilesLoot(string kind, string rock) =>
    $$"""
    {
      "type": "minecraft:block",
      "pools": [
        {
          "rolls": 1,
          "entries": [ { "type": "minecraft:item", "name": "{{MODID}}:{{kind}}/{{rock}}" } ],
          "conditions": [ { "condition": "minecraft:survives_explosion" } ]
        }
      ]
    }
    """;

// TFC table-craft with a damaged tool (shapeless): `ingredient` + a tool from `toolTag` (damaged, not consumed)
// -> `result`. Matches TFC's rock chiseled (bricks + tfc:chisels) and cracked (bricks + tfc:hammers) crafts.
string ToolCraft(string ingredient, string toolTag, string result) =>
    $$"""
    {
      "type": "tfc:damage_inputs_shapeless_crafting",
      "recipe": {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [
          { "item": "{{ingredient}}" },
          { "tag": "{{toolTag}}" }
        ],
        "result": { "item": "{{result}}" }
      }
    }
    """;

// TFC chisel recipe (block-in-world conversion). `mode` is smooth/stair/slab; `extraDrop` (slab mode) drops a
// spare slab so a full block yields two. Matches TFC's own data/tfc/recipes/chisel/* exactly.
string ChiselRecipe(string ingredient, string result, string mode, string extraDrop = null) =>
    extraDrop == null
        ? $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}" }"""
        : $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}", "extra_drop": { "item": "{{extraDrop}}" } }""";

// Shape crafting (off the plain tile) — `patternJson` is the recipe grid; counts match TFC's brick shapes
// (stairs 8, slab 6, wall 6). Key 'X' is the plain tile.
string ShapeCraft(string kind, string rock, string patternJson, int count) =>
    $$"""
    {
      "type": "minecraft:crafting_shaped",
      "pattern": {{patternJson}},
      "key": { "X": { "item": "{{MODID}}:tiles/{{rock}}" } },
      "result": { "item": "{{MODID}}:{{kind}}/{{rock}}", "count": {{count}} }
    }
    """;

// Shape stonecutting (off the plain tile) — counts match TFC (stairs 1, slab 2, wall 1).
string ShapeStonecut(string kind, string rock, int count) =>
    $$"""
    {
      "type": "minecraft:stonecutting",
      "ingredient": { "item": "{{MODID}}:tiles/{{rock}}" },
      "result": "{{MODID}}:{{kind}}/{{rock}}",
      "count": {{count}}
    }
    """;

// ---- stairs / slab / wall (off the plain tiles, matching vanilla's deepslate-tile family) -------------------
// All faces use the plain tile texture block/tiles/<rock>; only JSON is generated.

string ItemParent(string kind, string rock, string suffix) =>
    $$"""{ "parent": "{{MODID}}:block/{{kind}}/{{rock}}{{suffix}}" }""";

// Plain self-drop loot (used by stairs & wall; slabs need the double-count variant below).
string SelfLoot(string kind, string rock) => TilesLoot(kind, rock);

// --- stairs ---
(string suffix, string body)[] StairsModels(string rock)
{
    string tex = $$"""{ "bottom": "{{MODID}}:block/tiles/{{rock}}", "top": "{{MODID}}:block/tiles/{{rock}}", "side": "{{MODID}}:block/tiles/{{rock}}" }""";
    return new[]
    {
        ("",       $$"""{ "parent": "minecraft:block/stairs",       "textures": {{tex}} }"""),
        ("_inner", $$"""{ "parent": "minecraft:block/inner_stairs", "textures": {{tex}} }"""),
        ("_outer", $$"""{ "parent": "minecraft:block/outer_stairs", "textures": {{tex}} }"""),
    };
}

string StairsBlockstate(string rock) =>
    $$"""
    {
      "variants": {
        "facing=east,half=bottom,shape=straight":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}" },
        "facing=west,half=bottom,shape=straight":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=straight":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=straight":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=outer_right":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer" },
        "facing=west,half=bottom,shape=outer_right":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=outer_right": { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=outer_right": { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=outer_left":   { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 270, "uvlock": true },
        "facing=west,half=bottom,shape=outer_left":   { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 90, "uvlock": true },
        "facing=south,half=bottom,shape=outer_left":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer" },
        "facing=north,half=bottom,shape=outer_left":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "y": 180, "uvlock": true },
        "facing=east,half=bottom,shape=inner_right":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner" },
        "facing=west,half=bottom,shape=inner_right":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=inner_right": { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=inner_right": { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=inner_left":   { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 270, "uvlock": true },
        "facing=west,half=bottom,shape=inner_left":   { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 90, "uvlock": true },
        "facing=south,half=bottom,shape=inner_left":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner" },
        "facing=north,half=bottom,shape=inner_left":  { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "y": 180, "uvlock": true },
        "facing=east,half=top,shape=straight":        { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=straight":        { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=straight":       { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=straight":       { "model": "{{MODID}}:block/tile_stairs/{{rock}}", "x": 180, "y": 270, "uvlock": true },
        "facing=east,half=top,shape=outer_right":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 90, "uvlock": true },
        "facing=west,half=top,shape=outer_right":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 270, "uvlock": true },
        "facing=south,half=top,shape=outer_right":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "uvlock": true },
        "facing=north,half=top,shape=outer_right":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 180, "uvlock": true },
        "facing=east,half=top,shape=outer_left":      { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=outer_left":      { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=outer_left":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=outer_left":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_outer", "x": 180, "y": 270, "uvlock": true },
        "facing=east,half=top,shape=inner_right":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 90, "uvlock": true },
        "facing=west,half=top,shape=inner_right":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 270, "uvlock": true },
        "facing=south,half=top,shape=inner_right":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "uvlock": true },
        "facing=north,half=top,shape=inner_right":    { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 180, "uvlock": true },
        "facing=east,half=top,shape=inner_left":      { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=inner_left":      { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=inner_left":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=inner_left":     { "model": "{{MODID}}:block/tile_stairs/{{rock}}_inner", "x": 180, "y": 270, "uvlock": true }
      }
    }
    """;

// --- slab ---
(string suffix, string body)[] SlabModels(string rock)
{
    string tex = $$"""{ "bottom": "{{MODID}}:block/tiles/{{rock}}", "top": "{{MODID}}:block/tiles/{{rock}}", "side": "{{MODID}}:block/tiles/{{rock}}" }""";
    return new[]
    {
        ("",     $$"""{ "parent": "minecraft:block/slab",     "textures": {{tex}} }"""),
        ("_top", $$"""{ "parent": "minecraft:block/slab_top", "textures": {{tex}} }"""),
    };
}

string SlabBlockstate(string rock) =>
    $$"""
    {
      "variants": {
        "type=bottom": { "model": "{{MODID}}:block/tile_slab/{{rock}}" },
        "type=top":    { "model": "{{MODID}}:block/tile_slab/{{rock}}_top" },
        "type=double": { "model": "{{MODID}}:block/tiles/{{rock}}" }
      }
    }
    """;

string SlabLoot(string rock) =>
    $$"""
    {
      "type": "minecraft:block",
      "pools": [
        {
          "rolls": 1,
          "entries": [
            {
              "type": "minecraft:item",
              "name": "{{MODID}}:tile_slab/{{rock}}",
              "functions": [
                {
                  "function": "minecraft:set_count",
                  "conditions": [
                    { "condition": "minecraft:block_state_property", "block": "{{MODID}}:tile_slab/{{rock}}", "properties": { "type": "double" } }
                  ],
                  "count": 2,
                  "add": false
                }
              ]
            }
          ],
          "conditions": [ { "condition": "minecraft:survives_explosion" } ]
        }
      ]
    }
    """;

// --- wall ---
(string suffix, string body)[] WallModels(string rock)
{
    string tex = $$"""{ "wall": "{{MODID}}:block/tiles/{{rock}}" }""";
    return new[]
    {
        ("_post",      $$"""{ "parent": "minecraft:block/template_wall_post",      "textures": {{tex}} }"""),
        ("_side",      $$"""{ "parent": "minecraft:block/template_wall_side",      "textures": {{tex}} }"""),
        ("_side_tall", $$"""{ "parent": "minecraft:block/template_wall_side_tall", "textures": {{tex}} }"""),
        ("_inventory", $$"""{ "parent": "minecraft:block/wall_inventory",          "textures": {{tex}} }"""),
    };
}

string WallBlockstate(string rock) =>
    $$"""
    {
      "multipart": [
        { "when": { "up": "true" }, "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_post" } },
        { "when": { "north": "low" }, "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side", "uvlock": true } },
        { "when": { "east": "low" },  "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side", "y": 90, "uvlock": true } },
        { "when": { "south": "low" }, "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side", "y": 180, "uvlock": true } },
        { "when": { "west": "low" },  "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side", "y": 270, "uvlock": true } },
        { "when": { "north": "tall" }, "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side_tall", "uvlock": true } },
        { "when": { "east": "tall" },  "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side_tall", "y": 90, "uvlock": true } },
        { "when": { "south": "tall" }, "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side_tall", "y": 180, "uvlock": true } },
        { "when": { "west": "tall" },  "apply": { "model": "{{MODID}}:block/tile_wall/{{rock}}_side_tall", "y": 270, "uvlock": true } }
      ]
    }
    """;


// Write a tag file under data/<ns>/tags/<kind>/<name>.json (name may contain '/', e.g. "mineable/axe").
void WriteTag(string ns, string kind, string name, string body)
{
    string p = Path.Combine(resRoot, "data", ns, "tags", kind, name + ".json");
    Directory.CreateDirectory(Path.GetDirectoryName(p)!);
    File.WriteAllText(p, body);
}

// ---- types ----------------------------------------------------------------

enum Mode { Multiply, Clut }
