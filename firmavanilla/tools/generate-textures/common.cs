// Shared engine for the TFC Vanilla Building Blocks asset generator (see generate.cs for the entry point).
//
// `Gen` is the thin, cross-cutting core every feature file builds on: config (mod id, the rock/colour/stage
// lists, the tuning constants), the texture pipeline (CLUT palette-remap + grain/seam/mask passes), file/tag
// I/O (Load, WriteTag), and the JSON emitters shared by 3+ features (the stair/slab/wall shape family, the
// generic recipe shapes). Anything used by a single feature lives in that feature's file, not here.
//
// Feature files do `using static Gen;` so they reference these members unqualified.

using System.Runtime.CompilerServices;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

static class Gen
{
    // ---- config -----------------------------------------------------------

    public const string MODID = "firmavanilla";

    // Texture technique — see ClutSide/MultiplySide. Clut (palette remap) is crisp + authentically TFC-coloured;
    // Multiply (relief-transfer) keeps TFC's grain but the emblem is fainter.
    public const Mode MODE = Mode.Clut;

    // Clamp the relief multiplier (MULTIPLY mode only) so dark vanilla pixels can't blow out / crush the TFC base.
    public const float RATIO_MIN = 0.45f, RATIO_MAX = 1.55f;

    // TFC's seven sand colours -> which vanilla chiseled motif each one wears.
    //   creeper = vanilla chiseled_sandstone (normal),  wither = vanilla chiseled_red_sandstone.
    public static readonly Dictionary<string, string> MOTIF = new()
    {
        ["yellow"] = "creeper",
        ["white"]  = "creeper",
        ["pink"]   = "creeper",
        ["green"]  = "creeper",
        ["red"]    = "wither",
        ["black"]  = "wither",
        ["brown"]  = "wither",
    };

    // TFC's 20 rock types, for the deepslate-tiles-style "tiles" blocks (CLUT-recoloured per rock's brick palette).
    public static readonly string[] ROCKS = { "andesite", "basalt", "chalk", "chert", "claystone", "conglomerate", "dacite", "diorite",
        "dolomite", "gabbro", "gneiss", "granite", "limestone", "marble", "phyllite", "quartzite", "rhyolite",
        "schist", "shale", "slate" };

    // Most rocks build their CLUT ramp from the flatter `rock_smooth` (cleaner colour, stronger grain, softer
    // seams). These few read better with `rock_bricks`' built-in mortar contrast, so ramp them from bricks instead.
    public static readonly HashSet<string> BRICK_LUT_ROCKS = new() { "basalt", "claystone", "conglomerate", "granite" };

    // TFC's 16 alabaster dye colours, for the purpur-derived alabaster tile + pillar (CLUT through each colour's
    // alabaster-brick palette). Order = vanilla DyeColor (drives the creative tab).
    public static readonly string[] ALABASTER_COLORS = { "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
        "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink" };

    // Copper oxidation stages that get a patina LUT (the 3 aged stages — the bright base has no patina).
    public static readonly string[] COPPER_STAGES = { "exposed", "weathered", "oxidized" };
    // All four weather stages of the copper bars (WeatherState order), bright base first.
    public static readonly string[] BAR_STAGES = { "unaffected", "exposed", "weathered", "oxidized" };

    // Grain overlay on the tiles: the bright high-pass of each rock's `smooth` texture (its mineral flecks),
    // amplified and added onto the tile faces. Blur radius (smaller = sharper flecks), strength (intensity), and
    // the mask margin below the tile's mean luminance (grain lands on faces/highlights, not mortar/shadows).
    public const float GRAIN_BLUR = 0.8f;
    public const float GRAIN_STRENGTH = 1.5f;          // default per-rock grain amplification
    public const float GRAIN_STRENGTH_GRANITE = 1.2f;  // granite is already speckled -> a touch less
    public const int GRAIN_MARGIN = 12;
    // Alabaster detail pass: the bright high-pass of each colour's raw alabaster, stamped onto the CLUT'd tile/pillar
    // (no mask — applies everywhere). Tune this one number for more/less stone speckle.
    public const float ALABASTER_GRAIN_STRENGTH = 1.0f;
    // Chalk's brick palette is so light/low-contrast that its tile seams nearly vanish after the CLUT. Deepen the
    // mask's mortar (black) pixels for chalk only to restore seam contrast. <1 darkens; 1.0 = off.
    public const float SEAM_DARKEN_CHALK = 1.0f;

    public static readonly string scriptDir = ScriptDir();
    public static readonly string inputDir  = Path.Combine(scriptDir, "input");
    public static readonly string resRoot   = Path.GetFullPath(Path.Combine(scriptDir, "..", "..", "src", "main", "resources"));
    // CLI args (e.g. "regen-mask"). GetCommandLineArgs includes the host path + program args; Contains() still works.
    public static readonly string[] args = Environment.GetCommandLineArgs();

    // Shared merge-tags accumulated across features and written ONCE by the entry point (a mod can only ship one
    // file per tag path): minecraft:mineable/shovel + tfc:can_landslide get prismarine deposits AND coarse dirt.
    public static readonly List<string> shovelMineable = new();
    public static readonly List<string> canLandslide = new();
    public static string IdsTagBody(IEnumerable<string> ids) =>
        "{\"replace\":false,\"values\":[" + string.Join(",", ids.Select(id => "\"" + id + "\"")) + "]}";

    // ---- texture pipeline -------------------------------------------------

    // --- technique: MULTIPLY (relief-transfer) ---
    // out = tfc_base * (vanilla_chiseled / vanilla_flat), per channel. Keeps TFC's spatial grain.
    public static Image<Rgba32> MultiplySide(Image<Rgba32> relief, Image<Rgba32> flat, Image<Rgba32> tfcBase, int w, int h)
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

    static byte MulByRatio(byte baseVal, byte reliefVal, byte flatVal)
    {
        float ratio = flatVal == 0 ? 1f : (float) reliefVal / flatVal;
        ratio = Math.Clamp(ratio, RATIO_MIN, RATIO_MAX);
        return (byte) Math.Clamp((int) MathF.Round(baseVal * ratio), 0, 255);
    }

    // --- technique: CLUT (palette remap) ---
    // Repaint vanilla's chiseled art through a luminance->colour ramp sampled from TFC's real cut_sandstone, so
    // each output pixel's colour comes straight from TFC's palette and the emblem stays crisp.
    //
    // KEY STEP — normalize. We don't index the ramp by the vanilla pixel's RAW luminance: a TFC colour whose
    // palette sits in a narrow band (e.g. black is very dark, green/pink are low-contrast) doesn't overlap
    // vanilla's luminance range, so every pixel would clamp to one endpoint and the emblem flattens to a plain
    // colour. Instead we map each vanilla pixel from vanilla's luminance range [vMin,vMax] onto the TFC
    // palette's actual range [tMin,tMax], then look up — so the full emblem contrast always spans whatever
    // tonal range the colour has. (flat is unused — ramp is from tfcBase.)
    public static Image<Rgba32> ClutSide(Image<Rgba32> relief, Image<Rgba32> tfcBase, int w, int h)
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

    // Plain CLUT (no grain): repaint `pattern` through `lutBase`'s palette ramp at the LUT's resolution. Caller
    // owns/disposes the returned image. Used for the alabaster tiles/pillars (purpur recoloured per alabaster brick).
    public static Image<Rgba32> ClutThrough(Image<Rgba32> pattern, Image<Rgba32> lutBase)
    {
        using var src = pattern.Clone(c => c.Resize(lutBase.Width, lutBase.Height));
        return ClutSide(src, lutBase, lutBase.Width, lutBase.Height);
    }

    // Luminance range (min,max) over a texture's opaque pixels.
    public static (int min, int max) LumRange(Image<Rgba32> tex)
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
    public static Rgba32[] BuildPaletteRamp(Image<Rgba32> tex)
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

    public static int Lum(int r, int g, int b) => Math.Clamp((int) Math.Round(0.299 * r + 0.587 * g + 0.114 * b), 0, 255);

    // Composite `rawSrc` under `tile` via a hand-authored mask: per pixel lerp from raw (mask black) to the generated
    // tile (mask white) by the mask's luminance, so grey mask values blend the two. Result written back into `tile`.
    public static void MaskComposite(Image<Rgba32> tile, Image<Rgba32> rawSrc, Image<Rgba32> maskSrc)
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
    public static void GrainOverlay(Image<Rgba32> tile, Image<Rgba32> grainSrc, float strength, Image<Rgba32> maskSrc)
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
    public static void SeamDarken(Image<Rgba32> tile, Image<Rgba32> maskSrc, float factor)
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
    public static Image<Rgba32> BuildGrainMask(Image<Rgba32> pattern)
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

    public static Image<Rgba32> Load(string sub, string file)
    {
        string path = Path.Combine(inputDir, sub, file);
        if (!File.Exists(path))
            throw new FileNotFoundException($"Missing source texture: {path}\nSee {Path.Combine(inputDir, "README.md")}.");
        return Image.Load<Rgba32>(path);
    }

    static string ScriptDir([CallerFilePath] string path = "") => Path.GetDirectoryName(path)!;

    // ---- tag I/O ----------------------------------------------------------

    // Write a tag file under data/<ns>/tags/<kind>/<name>.json (name may contain '/', e.g. "mineable/axe").
    public static void WriteTag(string ns, string kind, string name, string body)
    {
        string p = Path.Combine(resRoot, "data", ns, "tags", kind, name + ".json");
        Directory.CreateDirectory(Path.GetDirectoryName(p)!);
        File.WriteAllText(p, body);
    }

    // {"replace":false,"values":[ ... ]} from an already-formatted (indented, quoted) id sequence.
    public static string ValuesTag(IEnumerable<string> ids) =>
        "{\n  \"replace\": false,\n  \"values\": [\n" + string.Join(",\n", ids) + "\n  ]\n}\n";

    // minecraft:mineable/pickaxe — every stone/metal block firmavanilla ships (chiseled sandstone, rock tiles +
    // shapes, alabaster tiles/pillars + shapes, the copper forms, soul lamps, the quartz column + cluster), so they
    // mine/drop with a pickaxe. A pure function of the config lists — written once by the entry point.
    public static string MineableTag()
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
                .SelectMany(kind => BAR_STAGES.Select(s => $"    \"{MODID}:{kind}/{s}\"")))
            .Concat(new[] { "copper", "bronze", "bismuth_bronze", "black_bronze", "wrought_iron", "steel", "black_steel", "blue_steel", "red_steel" }
                .Select(m => $"    \"{MODID}:soul_lamp/{m}\""))
            .Concat(new[] { $"    \"{MODID}:raw_quartz_column\"", $"    \"{MODID}:quartz_cluster\"" });
        return ValuesTag(ids);
    }

    // A vanilla shape tag (minecraft:blocks/{stairs|slabs|walls}) listing both the rock-tile shape (all rocks) and the
    // alabaster-tile shape (all colours), so our variants behave like vanilla ones (wall connection keys off
    // minecraft:walls, etc.).
    public static string ShapeTag(string rockKind, string alabKind) => ValuesTag(
        ROCKS.Select(r => $"    \"{MODID}:{rockKind}/{r}\"")
            .Concat(ALABASTER_COLORS.Select(c => $"    \"{MODID}:{alabKind}/{c}\"")));

    // ---- directory helpers (shared by rock tiles + alabaster) -------------

    // tex/bs/model/item/loot(/rec) dirs for a "tiles"-style block kind, created on demand.
    public static (string tex, string bs, string model, string item, string loot, string rec) TileDirs(string kind, bool withRecipe)
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

    // Derived-shape dirs (stairs/slab/wall) off a tile — no texture dir (they reuse the base tile texture).
    public static (string bs, string model, string item, string loot, string rec) DerivedDirs(string kind)
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

    // ---- shared model / item / loot emitters ------------------------------

    public static string CubeAllModel(string tex) => $$"""{ "parent": "minecraft:block/cube_all", "textures": { "all": "{{tex}}" } }""";
    public static string CubeVariant(string kind, string stage) => $$"""{ "variants": { "": { "model": "{{MODID}}:block/{{kind}}/{{stage}}" } } }""";
    public static string ParentItem(string modelPath) => $$"""{ "parent": "{{MODID}}:block/{{modelPath}}" }""";
    public static string GeneratedItem(string tex) => $$"""{ "parent": "item/generated", "textures": { "layer0": "{{tex}}" } }""";

    // Loot that drops a single given item id (self for most blocks; e.g. a weathering bright stage drops its TFC item).
    public static string DropItemLoot(string itemId) =>
        $$$"""
        {"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:item","name":"{{{itemId}}}"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}
        """;

    public static string TilesBlockstate(string kind, string rock) =>
        $$"""
        {
          "variants": {
            "": { "model": "{{MODID}}:block/{{kind}}/{{rock}}" }
          }
        }
        """;

    // cube_all: the generated tiles texture on every face (vanilla deepslate_tiles is the same).
    public static string TilesModel(string kind, string rock) =>
        $$"""
        {
          "parent": "minecraft:block/cube_all",
          "textures": { "all": "{{MODID}}:block/{{kind}}/{{rock}}" }
        }
        """;

    public static string TilesItemModel(string kind, string rock) =>
        $$"""
        { "parent": "{{MODID}}:block/{{kind}}/{{rock}}" }
        """;

    public static string TilesLoot(string kind, string rock) =>
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

    public static string ItemParent(string kind, string rock, string suffix) =>
        $$"""{ "parent": "{{MODID}}:block/{{kind}}/{{rock}}{{suffix}}" }""";

    // Plain self-drop loot (used by stairs & wall; slabs need the double-count variant below).
    public static string SelfLoot(string kind, string rock) => TilesLoot(kind, rock);

    // ---- shared recipe emitters -------------------------------------------

    // TFC table-craft with a damaged tool (shapeless): `ingredient` + a tool from `toolTag` (damaged, not consumed)
    // -> `result`. Matches TFC's rock chiseled (bricks + tfc:chisels) and cracked (bricks + tfc:hammers) crafts.
    public static string ToolCraft(string ingredient, string toolTag, string result) =>
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
    public static string ChiselRecipe(string ingredient, string result, string mode, string extraDrop = null) =>
        extraDrop == null
            ? $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}" }"""
            : $$"""{ "type": "tfc:chisel", "ingredient": "{{ingredient}}", "result": "{{result}}", "mode": "{{mode}}", "extra_drop": { "item": "{{extraDrop}}" } }""";

    // Shape crafting off a base block `baseKind/<rock>` — `patternJson` is the recipe grid; counts match TFC's
    // brick shapes (stairs 8, slab 6, wall 6). Key 'X' is the base block.
    public static string ShapeCraft(string baseKind, string kind, string rock, string patternJson, int count) =>
        $$"""
        {
          "type": "minecraft:crafting_shaped",
          "pattern": {{patternJson}},
          "key": { "X": { "item": "{{MODID}}:{{baseKind}}/{{rock}}" } },
          "result": { "item": "{{MODID}}:{{kind}}/{{rock}}", "count": {{count}} }
        }
        """;

    // Shape stonecutting off a base block `baseKind/<rock>` — counts match TFC (stairs 1, slab 2, wall 1).
    public static string ShapeStonecut(string baseKind, string kind, string rock, int count) =>
        $$"""
        {
          "type": "minecraft:stonecutting",
          "ingredient": { "item": "{{MODID}}:{{baseKind}}/{{rock}}" },
          "result": "{{MODID}}:{{kind}}/{{rock}}",
          "count": {{count}}
        }
        """;

    // TFC heating recipe: melt an item into a fluid at a temperature (mirrors TFC's metal-bar melts). NOTE the
    // `temperature` is a TOP-LEVEL field (sibling of result_fluid), not inside it — matching TFC's own recipes.
    public static string HeatingMelt(string itemId, string fluid, int amount, int temperature) =>
        $$$"""
        {"type":"tfc:heating","ingredient":{"item":"{{{itemId}}}"},"result_fluid":{"fluid":"{{{fluid}}}","amount":{{{amount}}}},"temperature":{{{temperature}}}}
        """;

    // ---- shared stair/slab/wall family (off a base block, matching vanilla's deepslate-tile family) -------------
    // All faces use the base texture block/<baseKind>/<rock>; only JSON is generated.

    public static (string suffix, string body)[] StairsModels(string baseKind, string rock)
    {
        string tex = $$"""{ "bottom": "{{MODID}}:block/{{baseKind}}/{{rock}}", "top": "{{MODID}}:block/{{baseKind}}/{{rock}}", "side": "{{MODID}}:block/{{baseKind}}/{{rock}}" }""";
        return new[]
        {
            ("",       $$"""{ "parent": "minecraft:block/stairs",       "textures": {{tex}} }"""),
            ("_inner", $$"""{ "parent": "minecraft:block/inner_stairs", "textures": {{tex}} }"""),
            ("_outer", $$"""{ "parent": "minecraft:block/outer_stairs", "textures": {{tex}} }"""),
        };
    }

    public static string StairsBlockstate(string kind, string rock) =>
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

    public static (string suffix, string body)[] SlabModels(string baseKind, string rock)
    {
        string tex = $$"""{ "bottom": "{{MODID}}:block/{{baseKind}}/{{rock}}", "top": "{{MODID}}:block/{{baseKind}}/{{rock}}", "side": "{{MODID}}:block/{{baseKind}}/{{rock}}" }""";
        return new[]
        {
            ("",     $$"""{ "parent": "minecraft:block/slab",     "textures": {{tex}} }"""),
            ("_top", $$"""{ "parent": "minecraft:block/slab_top", "textures": {{tex}} }"""),
        };
    }

    public static string SlabBlockstate(string kind, string baseKind, string rock) =>
        $$"""
        {
          "variants": {
            "type=bottom": { "model": "{{MODID}}:block/{{kind}}/{{rock}}" },
            "type=top":    { "model": "{{MODID}}:block/{{kind}}/{{rock}}_top" },
            "type=double": { "model": "{{MODID}}:block/{{baseKind}}/{{rock}}" }
          }
        }
        """;

    public static string SlabLoot(string kind, string rock) =>
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

    public static (string suffix, string body)[] WallModels(string baseKind, string rock)
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

    public static string WallBlockstate(string kind, string rock) =>
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

    // Stair/slab models textured by an explicit texture id (used by the copper forms, which point every face at a
    // vanilla copper-block/cut-copper texture rather than a block/<kind>/<rock> path).
    public static (string suffix, string body)[] StairModelsTex(string tex)
    {
        string t = $$"""{ "bottom": "{{tex}}", "top": "{{tex}}", "side": "{{tex}}" }""";
        return new[]
        {
            ("",       $$"""{ "parent": "minecraft:block/stairs",       "textures": {{t}} }"""),
            ("_inner", $$"""{ "parent": "minecraft:block/inner_stairs", "textures": {{t}} }"""),
            ("_outer", $$"""{ "parent": "minecraft:block/outer_stairs", "textures": {{t}} }"""),
        };
    }

    public static (string suffix, string body)[] SlabModelsTex(string tex)
    {
        string t = $$"""{ "bottom": "{{tex}}", "top": "{{tex}}", "side": "{{tex}}" }""";
        return new[]
        {
            ("",     $$"""{ "parent": "minecraft:block/slab",     "textures": {{t}} }"""),
            ("_top", $$"""{ "parent": "minecraft:block/slab_top", "textures": {{t}} }"""),
        };
    }
}

// ---- types ----------------------------------------------------------------

enum Mode { Multiply, Clut }
