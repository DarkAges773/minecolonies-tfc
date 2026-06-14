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

string scriptDir = ScriptDir();
string inputDir  = Path.Combine(scriptDir, "input");
string resRoot   = Path.GetFullPath(Path.Combine(scriptDir, "..", "..", "src", "main", "resources"));

string texDir    = Path.Combine(resRoot, "assets", MODID, "textures", "block", "chiseled_sandstone");
string bsDir     = Path.Combine(resRoot, "assets", MODID, "blockstates", "chiseled_sandstone");
string bModelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "chiseled_sandstone");
string iModelDir = Path.Combine(resRoot, "assets", MODID, "models", "item", "chiseled_sandstone");
string lootDir   = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "chiseled_sandstone");
string recipeDir = Path.Combine(resRoot, "data", MODID, "recipes", "chiseled_sandstone");

foreach (var d in new[] { texDir, bsDir, bModelDir, iModelDir, lootDir, recipeDir })
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
    File.WriteAllText(Path.Combine(recipeDir, color + ".json"), Recipe(color));
    done++;
    Console.WriteLine($"  chiseled_sandstone/{color,-6}  ({motif} motif)");
}

// minecraft:mineable/pickaxe — append all 7 blocks so they mine + drop with a pickaxe.
string mineableDir = Path.Combine(resRoot, "data", "minecraft", "tags", "blocks", "mineable");
Directory.CreateDirectory(mineableDir);
File.WriteAllText(Path.Combine(mineableDir, "pickaxe.json"), MineableTag());

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

foreach (var (_, pair) in motifSources) { pair.relief.Dispose(); pair.flat.Dispose(); }
Console.WriteLine($"Done: {done} chiseled-sandstone + {books} bookshelf variants written to {resRoot}");

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

// Two TFC cut-sandstone slabs of the matching colour, stacked, -> the chiseled block (mirrors vanilla).
string Recipe(string c) =>
    $$"""
    {
      "type": "minecraft:crafting_shaped",
      "pattern": [ "S", "S" ],
      "key": { "S": { "item": "tfc:cut_sandstone/{{c}}_slab" } },
      "result": { "item": "{{MODID}}:chiseled_sandstone/{{c}}" }
    }
    """;

string MineableTag()
{
    var ids = string.Join(",\n", MOTIF.Keys.Select(c => $"    \"{MODID}:chiseled_sandstone/{c}\""));
    return $$"""
    {
      "replace": false,
      "values": [
    {{ids}}
      ]
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

// Write a tag file under data/<ns>/tags/<kind>/<name>.json (name may contain '/', e.g. "mineable/axe").
void WriteTag(string ns, string kind, string name, string body)
{
    string p = Path.Combine(resRoot, "data", ns, "tags", kind, name + ".json");
    Directory.CreateDirectory(Path.GetDirectoryName(p)!);
    File.WriteAllText(p, body);
}

// ---- types ----------------------------------------------------------------

enum Mode { Multiply, Clut }
