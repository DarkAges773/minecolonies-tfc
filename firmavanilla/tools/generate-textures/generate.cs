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

// TFC's 16 alabaster dye colours, for the purpur-derived alabaster tile + pillar (CLUT through each colour's
// alabaster-brick palette). Order = vanilla DyeColor (drives the creative tab).
var ALABASTER_COLORS = new[] { "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
    "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink" };

// Copper oxidation stages that get a patina LUT (the 3 aged stages — the bright base has no patina).
var COPPER_STAGES = new[] { "exposed", "weathered", "oxidized" };
// All four weather stages of the copper bars (WeatherState order), bright base first.
var BAR_STAGES = new[] { "unaffected", "exposed", "weathered", "oxidized" };

// Grain overlay on the tiles: the bright high-pass of each rock's `smooth` texture (its mineral flecks),
// amplified and added onto the tile faces. Blur radius (smaller = sharper flecks), strength (intensity), and
// the mask margin below the tile's mean luminance (grain lands on faces/highlights, not mortar/shadows).
const float GRAIN_BLUR = 0.8f;
const float GRAIN_STRENGTH = 1.5f;          // default per-rock grain amplification
const float GRAIN_STRENGTH_GRANITE = 1.2f;  // granite is already speckled -> a touch less
const int GRAIN_MARGIN = 12;
// Alabaster detail pass: the bright high-pass of each colour's raw alabaster, stamped onto the CLUT'd tile/pillar
// (no mask — applies everywhere). Tune this one number for more/less stone speckle.
const float ALABASTER_GRAIN_STRENGTH = 1.0f;
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
WriteTag("minecraft", "blocks", "stairs", ShapeTag("tile_stairs", "alabaster_tile_stairs"));
WriteTag("minecraft", "blocks", "slabs", ShapeTag("tile_slab", "alabaster_tile_slab"));
WriteTag("minecraft", "blocks", "walls", ShapeTag("tile_wall", "alabaster_tile_wall"));

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

// Plain CLUT (no grain): repaint `pattern` through `lutBase`'s palette ramp at the LUT's resolution. Caller
// owns/disposes the returned image. Used for the alabaster tiles/pillars (purpur recoloured per alabaster brick).
Image<Rgba32> ClutThrough(Image<Rgba32> pattern, Image<Rgba32> lutBase)
{
    using var src = pattern.Clone(c => c.Resize(lutBase.Width, lutBase.Height));
    return ClutSide(src, lutBase, lutBase.Width, lutBase.Height);
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

// ---- alabaster tile + pillar (CLUT: vanilla purpur recoloured through each TFC alabaster-brick colour) ------
// purpur_block -> alabaster_tile/<colour> (cube_all); purpur_pillar (+_top) -> alabaster_pillar/<colour>
// (cube_column). The tile is then composited with raw alabaster through the hand-authored tiles_mask.png:
// mask white = keep the generated tile (the brick seams), black = show raw alabaster (the panel faces); grey
// blends. Pillar isn't covered by the (tile-shaped) mask. Recipes deferred.
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

// --- patina palettes (extracted from vanilla copper weathering stages) ------------------------------
// A reusable luminance->colour LUT per oxidation stage, sampled straight from vanilla's exposed/weathered/
// oxidized copper (no subtraction, no copper_block needed — the stage texture's own pixels ARE the palette).
// Emitted as a 256x16 ramp strip into the tool root (tracked, like grain_mask.png); feed any of these to
// ClutThrough() as the lutBase to patina-ify an arbitrary block later. See input/README.md.
int patina = 0;
foreach (var stage in COPPER_STAGES)
{
    using var src = Load("vanilla", stage + "_copper.png");
    Rgba32[] ramp = BuildPaletteRamp(src);
    (int tMin, int tMax) = LumRange(src);
    const int rw = 256, rh = 16;
    using var strip = new Image<Rgba32>(rw, rh);
    for (int x = 0; x < rw; x++)
    {
        float p = x / (float) (rw - 1);                                  // 0..1 across the strip
        int tl = Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255);
        Rgba32 c = ramp[tl];
        for (int y = 0; y < rh; y++) strip[x, y] = new Rgba32(c.R, c.G, c.B, 255);
    }
    strip.Save(Path.Combine(scriptDir, $"patina_{stage}.png"));
    Console.WriteLine($"  patina_{stage}.png (luma {tMin}..{tMax})");
    patina++;
}

// --- weathering TFC copper bars (vanilla copper lifecycle) ------------------------------------------
// Eight blocks: the 4 weather stages copper_bars/<stage> + their 4 waxed twins waxed_copper_bars/<stage>,
// for unaffected/exposed/weathered/oxidized. Stage 0 (unaffected) reuses TFC's own copper-bar textures
// verbatim (identity, no LUT); the 3 aged stages recolour the grate + smooth "edge" through the patina LUTs
// (ClutSide reads the 256-wide strip as ramp, emits at native 16x16, transparent bar pixels keep alpha). The
// waxed twins reuse the same 4 textures (vanilla waxed copper looks identical). Models/blockstate mirror TFC's
// iron-bars multipart. The bright stage has no item (TFC's copper bars item stands in) and drops the TFC item.
// See CopperBarsBlocks.java + weathering/WeatheringMaps.java.
int copperBars = 0;
{
    using var barsSrc = Load("tfc", Path.Combine("copper_bars", "bars.png"));
    using var smoothSrc = Load("tfc", Path.Combine("copper_bars", "smooth.png"));
    string cbTexDir = Path.Combine(resRoot, "assets", MODID, "textures", "block", "copper_bars");
    Directory.CreateDirectory(cbTexDir);
    barsSrc.Save(Path.Combine(cbTexDir, "unaffected.png"));            // bright = TFC's copper bars verbatim
    smoothSrc.Save(Path.Combine(cbTexDir, "unaffected_edge.png"));
    foreach (var stage in COPPER_STAGES)                              // exposed/weathered/oxidized via the LUTs
    {
        using var strip = Image.Load<Rgba32>(Path.Combine(scriptDir, $"patina_{stage}.png"));
        using (var b = ClutSide(barsSrc, strip, barsSrc.Width, barsSrc.Height)) b.Save(Path.Combine(cbTexDir, $"{stage}.png"));
        using (var e = ClutSide(smoothSrc, strip, smoothSrc.Width, smoothSrc.Height)) e.Save(Path.Combine(cbTexDir, $"{stage}_edge.png"));
    }
    foreach (var kind in new[] { "copper_bars", "waxed_copper_bars" })
    {
        string cbBsDir = Path.Combine(resRoot, "assets", MODID, "blockstates", kind);
        string cbModelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", kind);
        string cbItemDir = Path.Combine(resRoot, "assets", MODID, "models", "item", kind);
        string cbLootDir = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", kind);
        string cbHeatDir = Path.Combine(resRoot, "data", MODID, "recipes", "heating", kind);
        foreach (var d in new[] { cbBsDir, cbModelDir, cbItemDir, cbLootDir, cbHeatDir }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            foreach (var part in new[] { "post", "post_ends", "cap", "cap_alt", "side", "side_alt" })
                File.WriteAllText(Path.Combine(cbModelDir, $"{stage}_{part}.json"), CopperBarsModel(stage, part));
            File.WriteAllText(Path.Combine(cbBsDir, $"{stage}.json"), CopperBarsBlockstate(kind, stage));
            bool brightWeathering = kind == "copper_bars" && stage == "unaffected";
            if (!brightWeathering) File.WriteAllText(Path.Combine(cbItemDir, $"{stage}.json"), CopperBarsItem(stage));
            // Bright stage drops TFC's copper bars (no item of its own); every other block drops itself.
            File.WriteAllText(Path.Combine(cbLootDir, $"{stage}.json"),
                CopperBarsLootDrop(brightWeathering ? "tfc:metal/bars/copper" : $"{MODID}:{kind}/{stage}"));
            // Melting: mirror TFC's copper bars (25 mB tfc:metal/copper at 1080°C). The bright stage uses TFC's
            // item, which already has TFC's heating recipe — skip it (no firmavanilla item to key on).
            if (!brightWeathering)
                File.WriteAllText(Path.Combine(cbHeatDir, $"{stage}.json"),
                    HeatingMelt($"{MODID}:{kind}/{stage}", "tfc:metal/copper", 25, 1080));
            copperBars++;
        }
    }
    Console.WriteLine($"  weathering copper bars: {copperBars} blocks (4 stages x2 kinds) + 4 grate/edge textures");
}

// --- weathering copper: plated block + stairs/slab (vanilla cut-copper textures, no generation) and chains +
//     trapdoors (TFC textures recoloured through the patina LUTs). Same 8-block-per-form layout as the bars. ---
int copperForms = 0;
{
    string A(params string[] p) => Path.Combine(new[] { resRoot, "assets", MODID }.Concat(p).ToArray());
    string D(params string[] p) => Path.Combine(new[] { resRoot, "data", MODID }.Concat(p).ToArray());
    // Shared loot (bright stage drops its TFC item; others self) + melt recipe (skip the itemless bright stage).
    void Common(string kind, string stage, bool bright, string tfcItem, int melt)
    {
        Directory.CreateDirectory(D("loot_tables", "blocks", kind));
        File.WriteAllText(Path.Combine(D("loot_tables", "blocks", kind), stage + ".json"),
            CopperBarsLootDrop(bright ? tfcItem : $"{MODID}:{kind}/{stage}"));
        if (!bright)
        {
            Directory.CreateDirectory(D("recipes", "heating", kind));
            File.WriteAllText(Path.Combine(D("recipes", "heating", kind), stage + ".json"),
                HeatingMelt($"{MODID}:{kind}/{stage}", "tfc:metal/copper", melt, 1080));
        }
    }

    // 1) plated block (cube) — vanilla cut-copper textures.
    foreach (var kind in new[] { "copper_block", "waxed_copper_block" })
    {
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            bool bright = kind == "copper_block" && stage == "unaffected";
            File.WriteAllText(Path.Combine(md, stage + ".json"), CubeAllModel(CopperBlockTex(stage)));
            File.WriteAllText(Path.Combine(bs, stage + ".json"), CubeVariant(kind, stage));
            if (!bright) File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}"));
            Common(kind, stage, bright, "tfc:metal/block/copper", 100);
            copperForms++;
        }
    }

    // 2) plated stairs — vanilla copper-block textures; reuse StairsBlockstate (item parents the straight model).
    //    Crafted from the matching plated block of the same stage (mirrors TFC's copper_block→stairs, per stage).
    foreach (var kind in new[] { "copper_block_stairs", "waxed_copper_block_stairs" })
    {
        string baseKind = kind == "copper_block_stairs" ? "copper_block" : "waxed_copper_block";
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            bool bright = kind == "copper_block_stairs" && stage == "unaffected";
            foreach (var (suffix, body) in StairModelsTex(CopperBlockTex(stage)))
                File.WriteAllText(Path.Combine(md, stage + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(bs, stage + ".json"), StairsBlockstate(kind, stage));
            if (!bright)
            {
                File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}"));
                Directory.CreateDirectory(D("recipes", "crafting", kind));
                File.WriteAllText(Path.Combine(D("recipes", "crafting", kind), stage + ".json"),
                    ShapedFromBlock($"{MODID}:{baseKind}/{stage}", $"{MODID}:{kind}/{stage}", "[ \"X  \", \"XX \", \"XXX\" ]", 8));
            }
            Common(kind, stage, bright, "tfc:metal/block/copper_stairs", 75);
            copperForms++;
        }
    }

    // 3) plated slab — vanilla copper-block textures; reuse SlabBlockstate (double = the cube of the same kind family).
    //    Crafted from the matching plated block of the same stage (mirrors TFC's copper_block→slab, per stage).
    foreach (var kind in new[] { "copper_block_slab", "waxed_copper_block_slab" })
    {
        string baseKind = kind == "copper_block_slab" ? "copper_block" : "waxed_copper_block";
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            bool bright = kind == "copper_block_slab" && stage == "unaffected";
            foreach (var (suffix, body) in SlabModelsTex(CopperBlockTex(stage)))
                File.WriteAllText(Path.Combine(md, stage + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(bs, stage + ".json"), SlabBlockstate(kind, baseKind, stage));
            if (!bright)
            {
                File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}"));
                Directory.CreateDirectory(D("recipes", "crafting", kind));
                File.WriteAllText(Path.Combine(D("recipes", "crafting", kind), stage + ".json"),
                    ShapedFromBlock($"{MODID}:{baseKind}/{stage}", $"{MODID}:{kind}/{stage}", "[ \"XXX\" ]", 6));
            }
            Common(kind, stage, bright, "tfc:metal/block/copper_slab", 50);
            copperForms++;
        }
    }

    // 3b) CUT copper block (cube) — vanilla cut-copper textures; firmavanilla-only (no TFC bridge), so every stage
    //     is a normal item. The unwaxed cut block is cut from the plated block of the same stage via saw + stonecutter.
    foreach (var kind in new[] { "copper_cut", "waxed_copper_cut" })
    {
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            File.WriteAllText(Path.Combine(md, stage + ".json"), CubeAllModel(CutCopperTex(stage)));
            File.WriteAllText(Path.Combine(bs, stage + ".json"), CubeVariant(kind, stage));
            File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}"));
            Common(kind, stage, false, "", 100); // self loot + heating for every stage (no TFC bridge)
            if (kind == "copper_cut")
            {
                string plated = stage == "unaffected" ? "tfc:metal/block/copper" : $"{MODID}:copper_block/{stage}";
                Directory.CreateDirectory(D("recipes", "saw", kind));
                File.WriteAllText(Path.Combine(D("recipes", "saw", kind), stage + ".json"),
                    ToolCraft(plated, "tfc:saws", $"{MODID}:{kind}/{stage}"));
                Directory.CreateDirectory(D("recipes", "stonecutting", kind));
                File.WriteAllText(Path.Combine(D("recipes", "stonecutting", kind), stage + ".json"),
                    Stonecutting(plated, $"{MODID}:{kind}/{stage}", 1));
            }
            copperForms++;
        }
    }

    // 3c) CUT copper stairs + slab — vanilla cut-copper; crafted from the cut block of the same stage (like plated).
    foreach (var (kind, baseKind, models, pattern, count, melt) in new[]
    {
        ("copper_cut_stairs", "copper_cut", "stairs", "[ \"X  \", \"XX \", \"XXX\" ]", 8, 75),
        ("waxed_copper_cut_stairs", "waxed_copper_cut", "stairs", "[ \"X  \", \"XX \", \"XXX\" ]", 8, 75),
        ("copper_cut_slab", "copper_cut", "slab", "[ \"XXX\" ]", 6, 50),
        ("waxed_copper_cut_slab", "waxed_copper_cut", "slab", "[ \"XXX\" ]", 6, 50),
    })
    {
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            if (models == "stairs")
            {
                foreach (var (suffix, body) in StairModelsTex(CutCopperTex(stage)))
                    File.WriteAllText(Path.Combine(md, stage + suffix + ".json"), body);
                File.WriteAllText(Path.Combine(bs, stage + ".json"), StairsBlockstate(kind, stage));
            }
            else
            {
                foreach (var (suffix, body) in SlabModelsTex(CutCopperTex(stage)))
                    File.WriteAllText(Path.Combine(md, stage + suffix + ".json"), body);
                File.WriteAllText(Path.Combine(bs, stage + ".json"), SlabBlockstate(kind, baseKind, stage));
            }
            File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}"));
            Directory.CreateDirectory(D("recipes", "crafting", kind));
            File.WriteAllText(Path.Combine(D("recipes", "crafting", kind), stage + ".json"),
                ShapedFromBlock($"{MODID}:{baseKind}/{stage}", $"{MODID}:{kind}/{stage}", pattern, count));
            Common(kind, stage, false, "", melt);
            copperForms++;
        }
    }

    // 4) chain — TFC chain textures recoloured through the patina LUTs (block + item; unaffected = verbatim).
    {
        string blkTex = A("textures", "block", "copper_chain"), itmTex = A("textures", "item", "copper_chain");
        Directory.CreateDirectory(blkTex); Directory.CreateDirectory(itmTex);
        using var chBlk = Load("tfc", Path.Combine("copper_chain", "block.png"));
        using var chItm = Load("tfc", Path.Combine("copper_chain", "item.png"));
        chBlk.Save(Path.Combine(blkTex, "unaffected.png"));
        chItm.Save(Path.Combine(itmTex, "unaffected.png"));
        foreach (var stage in COPPER_STAGES)
        {
            using var strip = Image.Load<Rgba32>(Path.Combine(scriptDir, $"patina_{stage}.png"));
            using (var b = ClutSide(chBlk, strip, chBlk.Width, chBlk.Height)) b.Save(Path.Combine(blkTex, $"{stage}.png"));
            using (var i = ClutSide(chItm, strip, chItm.Width, chItm.Height)) i.Save(Path.Combine(itmTex, $"{stage}.png"));
        }
    }
    foreach (var kind in new[] { "copper_chain", "waxed_copper_chain" })
    {
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            bool bright = kind == "copper_chain" && stage == "unaffected";
            File.WriteAllText(Path.Combine(md, stage + ".json"), ChainModelTex($"{MODID}:block/copper_chain/{stage}"));
            File.WriteAllText(Path.Combine(bs, stage + ".json"), ChainBlockstate(kind, stage));
            if (!bright) File.WriteAllText(Path.Combine(it, stage + ".json"), GeneratedItem($"{MODID}:item/copper_chain/{stage}"));
            Common(kind, stage, bright, "tfc:metal/chain/copper", 6);
            copperForms++;
        }
    }

    // 5) trapdoor — TFC trapdoor texture recoloured through the patina LUTs (unaffected = verbatim).
    {
        string tdTex = A("textures", "block", "copper_trapdoor");
        Directory.CreateDirectory(tdTex);
        using var td = Load("tfc", Path.Combine("copper_trapdoor", "door.png"));
        td.Save(Path.Combine(tdTex, "unaffected.png"));
        foreach (var stage in COPPER_STAGES)
        {
            using var strip = Image.Load<Rgba32>(Path.Combine(scriptDir, $"patina_{stage}.png"));
            using (var d = ClutSide(td, strip, td.Width, td.Height)) d.Save(Path.Combine(tdTex, $"{stage}.png"));
        }
    }
    foreach (var kind in new[] { "copper_trapdoor", "waxed_copper_trapdoor" })
    {
        string bs = A("blockstates", kind), md = A("models", "block", kind), it = A("models", "item", kind);
        foreach (var d in new[] { bs, md, it }) Directory.CreateDirectory(d);
        foreach (var stage in BAR_STAGES)
        {
            bool bright = kind == "copper_trapdoor" && stage == "unaffected";
            foreach (var (suffix, body) in TrapdoorModelsTex($"{MODID}:block/copper_trapdoor/{stage}"))
                File.WriteAllText(Path.Combine(md, stage + suffix + ".json"), body);
            File.WriteAllText(Path.Combine(bs, stage + ".json"), TrapdoorBlockstate(kind, stage));
            if (!bright) File.WriteAllText(Path.Combine(it, stage + ".json"), ParentItem($"{kind}/{stage}_bottom"));
            Common(kind, stage, bright, "tfc:metal/trapdoor/copper", 200);
            copperForms++;
        }
    }
    Console.WriteLine($"  weathering copper forms (block/stairs/slab/chain/trapdoor): {copperForms} blocks");
}

foreach (var (_, pair) in motifSources) { pair.relief.Dispose(); pair.flat.Dispose(); }
Console.WriteLine($"Done: {done} chiseled-sandstone + {books} bookshelf + {tiles} rock-tiles + {alab} alabaster variants + {patina} patina palettes + {copperBars} copper-bar stages + {copperForms} copper-form blocks written to {resRoot}");

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

// Composite `rawSrc` under `tile` via a hand-authored mask: per pixel lerp from raw (mask black) to the generated
// tile (mask white) by the mask's luminance, so grey mask values blend the two. Result written back into `tile`.
void MaskComposite(Image<Rgba32> tile, Image<Rgba32> rawSrc, Image<Rgba32> maskSrc)
{
    int w = tile.Width, h = tile.Height;
    using var raw = (rawSrc.Width == w && rawSrc.Height == h) ? rawSrc.Clone() : rawSrc.Clone(c => c.Resize(w, h));
    using var mk = (maskSrc.Width == w && maskSrc.Height == h) ? maskSrc.Clone() : maskSrc.Clone(c => c.Resize(w, h));
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
    {
        float m = Lum(mk[x, y].R, mk[x, y].G, mk[x, y].B) / 255f;   // 1 = white = generated tile, 0 = black = raw
        Rgba32 t = tile[x, y], r = raw[x, y];
        tile[x, y] = new Rgba32(
            (byte) Math.Clamp((int) MathF.Round(r.R + (t.R - r.R) * m), 0, 255),
            (byte) Math.Clamp((int) MathF.Round(r.G + (t.G - r.G) * m), 0, 255),
            (byte) Math.Clamp((int) MathF.Round(r.B + (t.B - r.B) * m), 0, 255),
            t.A);
    }
}

// Overlay a source texture's bright grain onto a tile: the positive high-pass of `grainSrc` (grain - blur, bright
// parts only), amplified by `strength` and added. `maskSrc` gates where it lands (white = apply, black = skip);
// pass null to stamp everywhere (no masking — used by the alabaster detail pass off raw alabaster).
void GrainOverlay(Image<Rgba32> tile, Image<Rgba32> grainSrc, float strength, Image<Rgba32> maskSrc)
{
    int w = tile.Width, h = tile.Height;
    using var sm = (grainSrc.Width == w && grainSrc.Height == h) ? grainSrc.Clone() : grainSrc.Clone(c => c.Resize(w, h));
    using var blur = sm.Clone(c => c.GaussianBlur(GRAIN_BLUR));
    Image<Rgba32> mk = maskSrc == null ? null
        : ((maskSrc.Width == w && maskSrc.Height == h) ? maskSrc.Clone() : maskSrc.Clone(c => c.Resize(w, h)));
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
    {
        if (mk != null)
        {
            Rgba32 m = mk[x, y];
            if (m.A < 128 || Lum(m.R, m.G, m.B) < 128) continue;   // mask black/transparent = skip (mortar/shadows)
        }
        Rgba32 t = tile[x, y], s = sm[x, y], b = blur[x, y];
        tile[x, y] = new Rgba32(
            (byte) Math.Clamp(t.R + (int) MathF.Round(Math.Max(0, s.R - b.R) * strength), 0, 255),
            (byte) Math.Clamp(t.G + (int) MathF.Round(Math.Max(0, s.G - b.G) * strength), 0, 255),
            (byte) Math.Clamp(t.B + (int) MathF.Round(Math.Max(0, s.B - b.B) * strength), 0, 255),
            t.A);
    }
    mk?.Dispose();
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
        .Concat(ROCKS.Select(r => $"    \"{MODID}:tile_wall/{r}\""))
        .Concat(new[] { $"    \"{MODID}:alabaster_tile\"", $"    \"{MODID}:alabaster_pillar\"" })
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:alabaster_tile/{c}\""))
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:alabaster_pillar/{c}\""))
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:alabaster_tile_stairs/{c}\""))
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:alabaster_tile_slab/{c}\""))
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:alabaster_tile_wall/{c}\""))
        .Concat(new[] { "copper_bars", "waxed_copper_bars", "copper_block", "waxed_copper_block",
                        "copper_block_stairs", "waxed_copper_block_stairs", "copper_block_slab", "waxed_copper_block_slab",
                        "copper_chain", "waxed_copper_chain", "copper_trapdoor", "waxed_copper_trapdoor",
                        "copper_cut", "waxed_copper_cut", "copper_cut_stairs", "waxed_copper_cut_stairs",
                        "copper_cut_slab", "waxed_copper_cut_slab" }
            .SelectMany(kind => BAR_STAGES.Select(s => $"    \"{MODID}:{kind}/{s}\"")));
    return ValuesTag(ids);
}

// {"replace":false,"values":[ ... ]} from an already-formatted (indented, quoted) id sequence.
string ValuesTag(IEnumerable<string> ids) =>
    "{\n  \"replace\": false,\n  \"values\": [\n" + string.Join(",\n", ids) + "\n  ]\n}\n";

// A vanilla shape tag (minecraft:blocks/{stairs|slabs|walls}) listing both the rock-tile shape (all rocks) and the
// alabaster-tile shape (all colours), so our variants behave like vanilla ones (wall connection keys off
// minecraft:walls, etc.).
string ShapeTag(string rockKind, string alabKind) => ValuesTag(
    ROCKS.Select(r => $"    \"{MODID}:{rockKind}/{r}\"")
        .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:{alabKind}/{c}\"")));

// ---- copper-bars JSON emitters (one patina'd retexture of TFC's iron-bars multipart per stage) ----
// One part model: parent the vanilla iron_bars_<part>, point bars/particle at our recoloured grate and edge
// at our recoloured smooth copper.
// render_type cutout_mipped (Forge) — vanilla registers iron bars on that layer in code; our blocks don't get
// that registration, so without it the grate's transparent pixels render opaque. Data-driven via the model.
string CopperBarsModel(string stage, string part) =>
    $$$"""
    {"parent":"minecraft:block/iron_bars_{{{part}}}","render_type":"minecraft:cutout_mipped","textures":{"particle":"{{{MODID}}}:block/copper_bars/{{{stage}}}","bars":"{{{MODID}}}:block/copper_bars/{{{stage}}}","edge":"{{{MODID}}}:block/copper_bars/{{{stage}}}_edge"}}
    """;

// Multipart blockstate — identical topology to TFC's metal/bars/copper, retargeted at our per-kind/stage models.
string CopperBarsBlockstate(string kind, string stage)
{
    string m(string part) => $"{MODID}:block/{kind}/{stage}_{part}";
    return $$$"""
    {"multipart":[
    {"apply":{"model":"{{{m("post_ends")}}}"}},
    {"when":{"north":false,"south":false,"east":false,"west":false},"apply":{"model":"{{{m("post")}}}"}},
    {"when":{"north":true,"south":false,"east":false,"west":false},"apply":{"model":"{{{m("cap")}}}"}},
    {"when":{"north":false,"south":false,"east":true,"west":false},"apply":{"model":"{{{m("cap")}}}","y":90}},
    {"when":{"north":false,"south":true,"east":false,"west":false},"apply":{"model":"{{{m("cap_alt")}}}"}},
    {"when":{"north":false,"south":false,"east":false,"west":true},"apply":{"model":"{{{m("cap_alt")}}}","y":90}},
    {"when":{"north":true},"apply":{"model":"{{{m("side")}}}"}},
    {"when":{"east":true},"apply":{"model":"{{{m("side")}}}","y":90}},
    {"when":{"south":true},"apply":{"model":"{{{m("side_alt")}}}"}},
    {"when":{"west":true},"apply":{"model":"{{{m("side_alt")}}}","y":90}}
    ]}
    """;
}

string CopperBarsItem(string stage) =>
    $$$"""
    {"parent":"item/generated","textures":{"layer0":"{{{MODID}}}:block/copper_bars/{{{stage}}}"}}
    """;

// Loot that drops a single given item id (self for most stages; the bright weathering stage drops TFC's bars).
string CopperBarsLootDrop(string itemId) =>
    $$$"""
    {"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:item","name":"{{{itemId}}}"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}
    """;

// TFC heating recipe: melt an item into a fluid at a temperature (mirrors TFC's metal-bar melts). NOTE the
// `temperature` is a TOP-LEVEL field (sibling of result_fluid), not inside it — matching TFC's own recipes.
string HeatingMelt(string itemId, string fluid, int amount, int temperature) =>
    $$$"""
    {"type":"tfc:heating","ingredient":{"item":"{{{itemId}}}"},"result_fluid":{"fluid":"{{{fluid}}}","amount":{{{amount}}}},"temperature":{{{temperature}}}}
    """;

// Plain shaped crafting — mirrors TFC's metal block→stairs/slab recipes: `pattern` (X = `fromItem`) → `result`×count.
string ShapedFromBlock(string fromItem, string result, string pattern, int count) =>
    $$$"""{"type":"minecraft:crafting_shaped","pattern":{{{pattern}}},"key":{"X":{"item":"{{{fromItem}}}"}},"result":{"item":"{{{result}}}","count":{{{count}}}}}""";

// ---- weathering-copper form emitters (block/stairs/slab reuse vanilla cut-copper textures; chain/trapdoor use a
//      generated patina texture). The big StairsBlockstate/SlabBlockstate emitters above are reused (they only
//      reference model paths), so these supply only the texture-bearing models. ----

// Vanilla plain copper-block texture id for a weather stage (TFC's plated block is the smooth full block, so it
// maps to copper_block/exposed_copper/… — NOT the lined cut_copper). Stairs/slabs reuse this on every face.
string CopperBlockTex(string stage) => "minecraft:block/" + (stage == "unaffected" ? "copper_block" : $"{stage}_copper");
// Vanilla cut-copper texture id for a weather stage (the firmavanilla "cut" forms).
string CutCopperTex(string stage) => "minecraft:block/" + (stage == "unaffected" ? "cut_copper" : $"{stage}_cut_copper");

// Vanilla stonecutting recipe (full ids).
string Stonecutting(string ingredient, string result, int count) =>
    $$$"""{"type":"minecraft:stonecutting","ingredient":{"item":"{{{ingredient}}}"},"result":"{{{result}}}","count":{{{count}}}}""";

string CubeAllModel(string tex) => $$"""{ "parent": "minecraft:block/cube_all", "textures": { "all": "{{tex}}" } }""";
string CubeVariant(string kind, string stage) => $$"""{ "variants": { "": { "model": "{{MODID}}:block/{{kind}}/{{stage}}" } } }""";
string ParentItem(string modelPath) => $$"""{ "parent": "{{MODID}}:block/{{modelPath}}" }""";
string GeneratedItem(string tex) => $$"""{ "parent": "item/generated", "textures": { "layer0": "{{tex}}" } }""";

(string suffix, string body)[] StairModelsTex(string tex)
{
    string t = $$"""{ "bottom": "{{tex}}", "top": "{{tex}}", "side": "{{tex}}" }""";
    return new[]
    {
        ("",       $$"""{ "parent": "minecraft:block/stairs",       "textures": {{t}} }"""),
        ("_inner", $$"""{ "parent": "minecraft:block/inner_stairs", "textures": {{t}} }"""),
        ("_outer", $$"""{ "parent": "minecraft:block/outer_stairs", "textures": {{t}} }"""),
    };
}

(string suffix, string body)[] SlabModelsTex(string tex)
{
    string t = $$"""{ "bottom": "{{tex}}", "top": "{{tex}}", "side": "{{tex}}" }""";
    return new[]
    {
        ("",     $$"""{ "parent": "minecraft:block/slab",     "textures": {{t}} }"""),
        ("_top", $$"""{ "parent": "minecraft:block/slab_top", "textures": {{t}} }"""),
    };
}

string ChainModelTex(string tex) => $$"""{ "parent": "minecraft:block/chain", "render_type": "minecraft:cutout_mipped", "textures": { "all": "{{tex}}", "particle": "{{tex}}" } }""";
string ChainBlockstate(string kind, string stage)
{
    string m = $"{MODID}:block/{kind}/{stage}";
    return $$"""{ "variants": { "axis=x": { "model": "{{m}}", "x": 90, "y": 90 }, "axis=y": { "model": "{{m}}" }, "axis=z": { "model": "{{m}}", "x": 90 } } }""";
}

(string suffix, string body)[] TrapdoorModelsTex(string tex) => new[]
{
    ("_bottom", $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_bottom", "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
    ("_top",    $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_top",    "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
    ("_open",   $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_open",   "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
};

// Orientable trapdoor blockstate — mirrors TFC's metal/trapdoor/copper, retargeted at our per-kind/stage models.
string TrapdoorBlockstate(string kind, string stage)
{
    string b = $"{MODID}:block/{kind}/{stage}_bottom", t = $"{MODID}:block/{kind}/{stage}_top", o = $"{MODID}:block/{kind}/{stage}_open";
    return $$"""
    {
      "variants": {
        "facing=north,half=bottom,open=false": { "model": "{{b}}" },
        "facing=south,half=bottom,open=false": { "model": "{{b}}", "y": 180 },
        "facing=east,half=bottom,open=false":  { "model": "{{b}}", "y": 90 },
        "facing=west,half=bottom,open=false":  { "model": "{{b}}", "y": 270 },
        "facing=north,half=top,open=false":    { "model": "{{t}}" },
        "facing=south,half=top,open=false":    { "model": "{{t}}", "y": 180 },
        "facing=east,half=top,open=false":     { "model": "{{t}}", "y": 90 },
        "facing=west,half=top,open=false":     { "model": "{{t}}", "y": 270 },
        "facing=north,half=bottom,open=true":  { "model": "{{o}}" },
        "facing=south,half=bottom,open=true":  { "model": "{{o}}", "y": 180 },
        "facing=east,half=bottom,open=true":   { "model": "{{o}}", "y": 90 },
        "facing=west,half=bottom,open=true":   { "model": "{{o}}", "y": 270 },
        "facing=north,half=top,open=true":     { "model": "{{o}}", "x": 180, "y": 180 },
        "facing=south,half=top,open=true":     { "model": "{{o}}", "x": 180, "y": 0 },
        "facing=east,half=top,open=true":      { "model": "{{o}}", "x": 180, "y": 270 },
        "facing=west,half=top,open=true":      { "model": "{{o}}", "x": 180, "y": 90 }
      }
    }
    """;
}

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

// Pillar (cube_column, RotatedPillarBlock) — axis variants like vanilla purpur_pillar; `_top` texture on the ends.
string PillarBlockstate(string color) =>
    $$"""
    {
      "variants": {
        "axis=x": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}", "x": 90, "y": 90 },
        "axis=y": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}" },
        "axis=z": { "model": "{{MODID}}:block/alabaster_pillar/{{color}}", "x": 90 }
      }
    }
    """;

string PillarModel(string color) =>
    $$"""
    {
      "parent": "minecraft:block/cube_column",
      "textures": {
        "end": "{{MODID}}:block/alabaster_pillar/{{color}}_top",
        "side": "{{MODID}}:block/alabaster_pillar/{{color}}"
      }
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

// TFC sealed-barrel dye: the (uncoloured) base item + 25 mB of a colour's dye fluid -> the coloured item.
// Matches TFC's data/tfc/recipes/barrel/dye/* for alabaster exactly (duration 1000).
string BarrelDye(string inputItem, string color, string outputItem) =>
    $$"""{ "type": "tfc:barrel_sealed", "input_item": { "ingredient": { "item": "{{inputItem}}" } }, "input_fluid": { "ingredient": "tfc:{{color}}_dye", "amount": 25 }, "output_item": { "item": "{{outputItem}}" }, "duration": 1000 }""";

// TFC chisel recipe (block-in-world conversion). `mode` is smooth/stair/slab; `extraDrop` (slab mode) drops a
// spare slab so a full block yields two. Matches TFC's own data/tfc/recipes/chisel/* exactly.
string ChiselRecipe(string ingredient, string result, string mode, string extraDrop = null) =>
    extraDrop == null
        ? $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}" }"""
        : $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}", "extra_drop": { "item": "{{extraDrop}}" } }""";

// Shape crafting off the base block `baseKind/<rock>` — `patternJson` is the recipe grid; counts match TFC's
// brick shapes (stairs 8, slab 6, wall 6). Key 'X' is the base block.
string ShapeCraft(string baseKind, string kind, string rock, string patternJson, int count) =>
    $$"""
    {
      "type": "minecraft:crafting_shaped",
      "pattern": {{patternJson}},
      "key": { "X": { "item": "{{MODID}}:{{baseKind}}/{{rock}}" } },
      "result": { "item": "{{MODID}}:{{kind}}/{{rock}}", "count": {{count}} }
    }
    """;

// Shape stonecutting off the base block `baseKind/<rock>` — counts match TFC (stairs 1, slab 2, wall 1).
string ShapeStonecut(string baseKind, string kind, string rock, int count) =>
    $$"""
    {
      "type": "minecraft:stonecutting",
      "ingredient": { "item": "{{MODID}}:{{baseKind}}/{{rock}}" },
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
(string suffix, string body)[] StairsModels(string baseKind, string rock)
{
    string tex = $$"""{ "bottom": "{{MODID}}:block/{{baseKind}}/{{rock}}", "top": "{{MODID}}:block/{{baseKind}}/{{rock}}", "side": "{{MODID}}:block/{{baseKind}}/{{rock}}" }""";
    return new[]
    {
        ("",       $$"""{ "parent": "minecraft:block/stairs",       "textures": {{tex}} }"""),
        ("_inner", $$"""{ "parent": "minecraft:block/inner_stairs", "textures": {{tex}} }"""),
        ("_outer", $$"""{ "parent": "minecraft:block/outer_stairs", "textures": {{tex}} }"""),
    };
}

string StairsBlockstate(string kind, string rock) =>
    $$"""
    {
      "variants": {
        "facing=east,half=bottom,shape=straight":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}" },
        "facing=west,half=bottom,shape=straight":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=straight":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=straight":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=outer_right":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer" },
        "facing=west,half=bottom,shape=outer_right":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=outer_right": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=outer_right": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=outer_left":   { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 270, "uvlock": true },
        "facing=west,half=bottom,shape=outer_left":   { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 90, "uvlock": true },
        "facing=south,half=bottom,shape=outer_left":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer" },
        "facing=north,half=bottom,shape=outer_left":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "y": 180, "uvlock": true },
        "facing=east,half=bottom,shape=inner_right":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner" },
        "facing=west,half=bottom,shape=inner_right":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 180, "uvlock": true },
        "facing=south,half=bottom,shape=inner_right": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 90, "uvlock": true },
        "facing=north,half=bottom,shape=inner_right": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 270, "uvlock": true },
        "facing=east,half=bottom,shape=inner_left":   { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 270, "uvlock": true },
        "facing=west,half=bottom,shape=inner_left":   { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 90, "uvlock": true },
        "facing=south,half=bottom,shape=inner_left":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner" },
        "facing=north,half=bottom,shape=inner_left":  { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "y": 180, "uvlock": true },
        "facing=east,half=top,shape=straight":        { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=straight":        { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=straight":       { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=straight":       { "model": "{{MODID}}:block/{{kind}}/{{rock}}", "x": 180, "y": 270, "uvlock": true },
        "facing=east,half=top,shape=outer_right":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 90, "uvlock": true },
        "facing=west,half=top,shape=outer_right":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 270, "uvlock": true },
        "facing=south,half=top,shape=outer_right":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "uvlock": true },
        "facing=north,half=top,shape=outer_right":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 180, "uvlock": true },
        "facing=east,half=top,shape=outer_left":      { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=outer_left":      { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=outer_left":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=outer_left":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_outer", "x": 180, "y": 270, "uvlock": true },
        "facing=east,half=top,shape=inner_right":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 90, "uvlock": true },
        "facing=west,half=top,shape=inner_right":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 270, "uvlock": true },
        "facing=south,half=top,shape=inner_right":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "uvlock": true },
        "facing=north,half=top,shape=inner_right":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 180, "uvlock": true },
        "facing=east,half=top,shape=inner_left":      { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "uvlock": true },
        "facing=west,half=top,shape=inner_left":      { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 180, "uvlock": true },
        "facing=south,half=top,shape=inner_left":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 90, "uvlock": true },
        "facing=north,half=top,shape=inner_left":     { "model": "{{MODID}}:block/{{kind}}/{{rock}}_inner", "x": 180, "y": 270, "uvlock": true }
      }
    }
    """;

// --- slab ---
(string suffix, string body)[] SlabModels(string baseKind, string rock)
{
    string tex = $$"""{ "bottom": "{{MODID}}:block/{{baseKind}}/{{rock}}", "top": "{{MODID}}:block/{{baseKind}}/{{rock}}", "side": "{{MODID}}:block/{{baseKind}}/{{rock}}" }""";
    return new[]
    {
        ("",     $$"""{ "parent": "minecraft:block/slab",     "textures": {{tex}} }"""),
        ("_top", $$"""{ "parent": "minecraft:block/slab_top", "textures": {{tex}} }"""),
    };
}

string SlabBlockstate(string kind, string baseKind, string rock) =>
    $$"""
    {
      "variants": {
        "type=bottom": { "model": "{{MODID}}:block/{{kind}}/{{rock}}" },
        "type=top":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_top" },
        "type=double": { "model": "{{MODID}}:block/{{baseKind}}/{{rock}}" }
      }
    }
    """;

string SlabLoot(string kind, string rock) =>
    $$"""
    {
      "type": "minecraft:block",
      "pools": [
        {
          "rolls": 1,
          "entries": [
            {
              "type": "minecraft:item",
              "name": "{{MODID}}:{{kind}}/{{rock}}",
              "functions": [
                {
                  "function": "minecraft:set_count",
                  "conditions": [
                    { "condition": "minecraft:block_state_property", "block": "{{MODID}}:{{kind}}/{{rock}}", "properties": { "type": "double" } }
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
(string suffix, string body)[] WallModels(string baseKind, string rock)
{
    string tex = $$"""{ "wall": "{{MODID}}:block/{{baseKind}}/{{rock}}" }""";
    return new[]
    {
        ("_post",      $$"""{ "parent": "minecraft:block/template_wall_post",      "textures": {{tex}} }"""),
        ("_side",      $$"""{ "parent": "minecraft:block/template_wall_side",      "textures": {{tex}} }"""),
        ("_side_tall", $$"""{ "parent": "minecraft:block/template_wall_side_tall", "textures": {{tex}} }"""),
        ("_inventory", $$"""{ "parent": "minecraft:block/wall_inventory",          "textures": {{tex}} }"""),
    };
}

string WallBlockstate(string kind, string rock) =>
    $$"""
    {
      "multipart": [
        { "when": { "up": "true" }, "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_post" } },
        { "when": { "north": "low" }, "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side", "uvlock": true } },
        { "when": { "east": "low" },  "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side", "y": 90, "uvlock": true } },
        { "when": { "south": "low" }, "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side", "y": 180, "uvlock": true } },
        { "when": { "west": "low" },  "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side", "y": 270, "uvlock": true } },
        { "when": { "north": "tall" }, "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side_tall", "uvlock": true } },
        { "when": { "east": "tall" },  "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side_tall", "y": 90, "uvlock": true } },
        { "when": { "south": "tall" }, "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side_tall", "y": 180, "uvlock": true } },
        { "when": { "west": "tall" },  "apply": { "model": "{{MODID}}:block/{{kind}}/{{rock}}_side_tall", "y": 270, "uvlock": true } }
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
