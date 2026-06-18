// Per-wood barrel textures: vanilla's four barrel faces (side / top / bottom / top_open) recoloured through each
// wood's PLANKS palette via CLUT (the same luminance-normalized palette remap the rest of the mod uses). So every
// wood's barrel keeps vanilla's exact stave relief but is repainted into that wood's tones.
//
// MASKED CLUT (the metal hoops / open hole). A hand-painted mask per face — white = wood (CLUT through planks),
// black = metal/hole (keep vanilla's original pixel) — does double duty:
//   1. the hoops stay vanilla's dark band (a consistent "iron" look across every wood, not recoloured wood), and
//   2. the wood region's normalization range [vMin,vMax] is computed over the WOOD pixels only — so the near-black
//      hoops no longer drag vMin down and squeeze the stave contrast into the top of the palette. (That squeeze is
//      why the unmasked side/top read flat while the hoop-free bottom looked right.)
// Masks are HAND-PAINTED, tracked files in the tool root (alongside the rock-tile grain masks):
// barrel_<face>_mask.png (barrel_side_mask.png / barrel_top_mask.png / barrel_top_open_mask.png). They're load-only
// — never auto-generated or overwritten (unlike the luminance-derived grain masks), so a hand edit is safe. The
// bottom is all wood and has no mask. A masked face whose file is missing falls back to a plain unmasked CLUT (with
// a warning) rather than inventing a wrong mask.
//
// TEXTURES ONLY (no blockstate/model/item/registration yet) — a first exploratory pass to review the look. The
// wood lists mirror the bookshelves (TFC always; AFC/Beneath generated unconditionally — assets are harmless when
// the mod is absent, like the bookshelf assets). Source planks come from input/<ns>/planks/<wood>.png.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class Barrels
{
    // (ns, woods) — same sets as the decorative bookshelves (BookshelfBlocks.java / bookshelves.cs); keep in sync.
    static readonly (string ns, string[] woods)[] BARREL = new (string, string[])[]
    {
        ("tfc", new[] { "acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
                        "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow" }),
        ("afc", new[] { "baobab", "cypress", "eucalyptus", "fig", "hevea", "ipe", "ironwood", "mahogany", "teak", "tualang" }),
        ("beneath", new[] { "crimson", "warped" }),
    };

    // Vanilla barrel face -> generated suffix (output is block/barrel/<wood>_<face>.png). The bottom is pure wood,
    // so it has no mask; side/top/top_open get a hand-painted metal/hole mask.
    static readonly string[] FACES = { "side", "top", "bottom", "top_open" };
    static readonly HashSet<string> MASKED = new() { "side", "top", "top_open" };

    // Masked region treatment. Most faces keep vanilla's pixel where the mask is black (the metal hoops / bung — a
    // consistent dark band on every wood). For the open top, the black region is the barrel's INTERIOR, so instead
    // of leaving it vanilla-spruce it's painted as that wood's DARKEST tone (ramp[tMin]) with only its BRIGHTNESS
    // modulated from the hole's edge inward. Anchoring to the darkest wood colour and keeping even the rim below
    // full brightness guarantees the hole is darker than the surrounding staves on EVERY wood (a fixed multiply of
    // the wood's own mid-tones blended into dark/normal woods, shrinking the apparent hole). RIM = brightness at the
    // hole edge, CORE = at its centre — both fractions of the darkest wood colour; the gap between them is the
    // interior gradient's contrast, and RIM < 1 is what keeps the rim from blending into the lid.
    static readonly HashSet<string> HOLE = new() { "top_open" };
    const float HOLE_RIM = 0.8f;
    const float HOLE_CORE = 0.5f;

    public static int Generate()
    {
        string texDir   = Path.Combine(resRoot, "assets", MODID, "textures", "block", "barrel");
        string bsDir    = Path.Combine(resRoot, "assets", MODID, "blockstates", "barrel");
        string modelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "barrel");
        string itemDir  = Path.Combine(resRoot, "assets", MODID, "models", "item", "barrel");
        string lootDir  = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "barrel");
        foreach (var d in new[] { texDir, bsDir, modelDir, itemDir, lootDir }) Directory.CreateDirectory(d);

        // Load the four vanilla faces + each masked face's hand-painted mask (null = file missing -> plain CLUT).
        var faces = FACES.ToDictionary(f => f, f => Load("vanilla", "barrel_" + f + ".png"));
        var masks = new Dictionary<string, Image<Rgba32>>();
        foreach (var f in MASKED) masks[f] = LoadMask(f);

        int count = 0;
        foreach (var (ns, woods) in BARREL)
        foreach (var wood in woods)
        {
            using (var planks = Load(ns, Path.Combine("planks", wood + ".png")))
            foreach (var face in FACES)
            {
                Image<Rgba32> src = faces[face];
                Image<Rgba32> mask = masks.GetValueOrDefault(face);
                using var outImg = mask != null
                    ? ClutMasked(src, planks, mask, HOLE.Contains(face))
                    : ClutSide(src, planks, src.Width, src.Height);
                outImg.Save(Path.Combine(texDir, $"{wood}_{face}.png"));
            }

            // Functional barrel: vanilla's facing/open variants + closed/open cube_bottom_top models, item, loot.
            File.WriteAllText(Path.Combine(bsDir, wood + ".json"), Blockstate(wood));
            File.WriteAllText(Path.Combine(modelDir, wood + ".json"), Model(wood, "top"));
            File.WriteAllText(Path.Combine(modelDir, wood + "_open.json"), Model(wood, "top_open"));
            File.WriteAllText(Path.Combine(itemDir, wood + ".json"), ParentItem($"barrel/{wood}"));
            File.WriteAllText(Path.Combine(lootDir, wood + ".json"), DropItemLoot($"{MODID}:barrel/{wood}"));
            // mineable/axe membership (shared with the bookshelves; AFC/Beneath required:false), written once by the
            // entry point.
            axeMineable.Add(ns == "tfc"
                ? $"    \"{MODID}:barrel/{wood}\""
                : $"    {{ \"id\": \"{MODID}:barrel/{wood}\", \"required\": false }}");
            count++;
        }

        foreach (var img in faces.Values) img.Dispose();
        foreach (var img in masks.Values) if (img != null) img.Dispose();
        return count;
    }

    // Vanilla barrel blockstate: 6 facings x open/closed, with the same x/y rotations vanilla uses.
    static string Blockstate(string wood) =>
        $$"""
        {
          "variants": {
            "facing=down,open=false":  { "model": "{{MODID}}:block/barrel/{{wood}}", "x": 180 },
            "facing=down,open=true":   { "model": "{{MODID}}:block/barrel/{{wood}}_open", "x": 180 },
            "facing=east,open=false":  { "model": "{{MODID}}:block/barrel/{{wood}}", "x": 90, "y": 90 },
            "facing=east,open=true":   { "model": "{{MODID}}:block/barrel/{{wood}}_open", "x": 90, "y": 90 },
            "facing=north,open=false": { "model": "{{MODID}}:block/barrel/{{wood}}", "x": 90 },
            "facing=north,open=true":  { "model": "{{MODID}}:block/barrel/{{wood}}_open", "x": 90 },
            "facing=south,open=false": { "model": "{{MODID}}:block/barrel/{{wood}}", "x": 90, "y": 180 },
            "facing=south,open=true":  { "model": "{{MODID}}:block/barrel/{{wood}}_open", "x": 90, "y": 180 },
            "facing=up,open=false":    { "model": "{{MODID}}:block/barrel/{{wood}}" },
            "facing=up,open=true":     { "model": "{{MODID}}:block/barrel/{{wood}}_open" },
            "facing=west,open=false":  { "model": "{{MODID}}:block/barrel/{{wood}}", "x": 90, "y": 270 },
            "facing=west,open=true":   { "model": "{{MODID}}:block/barrel/{{wood}}_open", "x": 90, "y": 270 }
          }
        }
        """;

    // cube_bottom_top: our generated side on the 4 sides, generated bottom, and `top` ("top" closed / "top_open" open).
    static string Model(string wood, string topFace) =>
        $$"""
        {
          "parent": "minecraft:block/cube_bottom_top",
          "textures": {
            "bottom": "{{MODID}}:block/barrel/{{wood}}_bottom",
            "side": "{{MODID}}:block/barrel/{{wood}}_side",
            "top": "{{MODID}}:block/barrel/{{wood}}_{{topFace}}"
          }
        }
        """;

    // CLUT the WOOD region (mask white) through the planks; the masked (black) region is either kept vanilla
    // (holeMode == false — the metal hoops / bung) or, for the open top (holeMode == true), painted as the darkest
    // wood tone with its brightness modulated edge->centre (the shadowed interior). The WOOD region's normalization
    // range is measured over wood pixels only, so the hoops/hole don't compress the stave contrast (the whole point
    // of the mask).
    static Image<Rgba32> ClutMasked(Image<Rgba32> relief, Image<Rgba32> palette, Image<Rgba32> maskSrc, bool holeMode)
    {
        int w = relief.Width, h = relief.Height;
        using var mk = (maskSrc.Width == w && maskSrc.Height == h) ? maskSrc.Clone() : maskSrc.Clone(c => c.Resize(w, h));

        Rgba32[] ramp = BuildPaletteRamp(palette);
        (int tMin, int tMax) = LumRange(palette);

        int vMin = 255, vMax = 0; bool any = false;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
        {
            Rgba32 r = relief[x, y];
            if (r.A < 8 || !IsWood(mk[x, y])) continue;
            int L = Lum(r.R, r.G, r.B);
            if (L < vMin) vMin = L; if (L > vMax) vMax = L; any = true;
        }
        if (!any) { vMin = 0; vMax = 255; }
        int vSpan = Math.Max(1, vMax - vMin);

        // The hole's OWN luminance range (over the masked pixels), so its handful of interior shades stretch across
        // the full 0..1 brightness ramp — that's what gives the edge->centre gradient its separation.
        int hMin = 255, hMax = 0; bool anyHole = false;
        if (holeMode)
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            {
                Rgba32 r = relief[x, y];
                if (r.A < 8 || IsWood(mk[x, y])) continue;
                int L = Lum(r.R, r.G, r.B);
                if (L < hMin) hMin = L; if (L > hMax) hMax = L; anyHole = true;
            }
        if (!anyHole) { hMin = 0; hMax = 255; }
        int hSpan = Math.Max(1, hMax - hMin);
        Rgba32 darkWood = ramp[Math.Clamp(tMin, 0, 255)];          // the wood's darkest tone — the hole's base colour

        var img = new Image<Rgba32>(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
        {
            Rgba32 r = relief[x, y];
            if (IsWood(mk[x, y]))                                 // wood staves: wood-only range across full palette
            {
                float p = (float) (Lum(r.R, r.G, r.B) - vMin) / vSpan;
                Rgba32 c = ramp[Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255)];
                img[x, y] = new Rgba32(c.R, c.G, c.B, r.A);
            }
            else if (holeMode)                                    // open-top interior: darkest wood, brightness edge->centre
            {
                float p = (float) (Lum(r.R, r.G, r.B) - hMin) / hSpan;   // 0 = centre (darkest), 1 = edge
                float b = HOLE_CORE + p * (HOLE_RIM - HOLE_CORE);
                img[x, y] = new Rgba32((byte) (darkWood.R * b), (byte) (darkWood.G * b), (byte) (darkWood.B * b), r.A);
            }
            else img[x, y] = r;                                   // metal hoops / bung: keep vanilla
        }
        return img;
    }

    static bool IsWood(Rgba32 m) => m.A >= 128 && Lum(m.R, m.G, m.B) >= 128;   // white = wood (CLUT), black = keep

    // Hand-painted, tracked mask in the tool root (alongside grain_mask.png / patina_*.png): barrel_<face>_mask.png.
    // Load-only — returns null (so the caller falls back to a plain CLUT) and warns if the file is missing; never
    // generates or overwrites, since these are authored by hand.
    static Image<Rgba32> LoadMask(string face)
    {
        string p = Path.Combine(scriptDir, $"barrel_{face}_mask.png");
        if (File.Exists(p)) return Image.Load<Rgba32>(p);
        Console.WriteLine($"  WARNING: missing barrel mask {Path.GetFileName(p)} — generating {face} with a plain (unmasked) CLUT.");
        return null;
    }
}
