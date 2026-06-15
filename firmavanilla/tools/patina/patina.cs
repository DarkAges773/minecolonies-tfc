#:package SixLabors.ImageSharp@3.1.10
using System.Runtime.CompilerServices;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

// ==========================================================================================================
//  patina — a self-contained CLI that recolours a texture through copper's oxidation palette (CLUT).
//
//  USAGE
//    dotnet run patina.cs -- <input> [outDir] [--unaffected] [--stages a,b,c]
//        Apply the patina LUTs to a texture (or every *.png in a directory) and write one variant per stage:
//        <stem>_exposed.png / <stem>_weathered.png / <stem>_oxidized.png. outDir defaults to the input's folder.
//        --unaffected also copies the input verbatim as <stem>_unaffected.png (stage 0).
//
//    dotnet run patina.cs -- extract <exposed_copper.png> <weathered_copper.png> <oxidized_copper.png>
//        Rebuild the bundled LUT strips in ./lut/ from vanilla copper's weathering-stage textures. The palette
//        is sampled straight from each stage (no subtraction) — this is how lut/*.png were produced.
//
//  The LUT strips in ./lut/ are 256x16 luminance->colour ramps; the CLUT maps each input pixel's luminance
//  (normalised to its own range) onto the strip's range, preserving relief and alpha. Self-contained: no
//  dependency on the firmavanilla asset generator.
// ==========================================================================================================

string scriptDir = ScriptDir();
string lutDir = Path.Combine(scriptDir, "lut");
string[] ALL_STAGES = { "exposed", "weathered", "oxidized" };

if (args.Length == 0) { Usage(); return; }

if (args[0] == "extract")
{
    if (args.Length < 4) { Console.Error.WriteLine("extract needs 3 args: <exposed_copper> <weathered_copper> <oxidized_copper>"); Environment.Exit(2); return; }
    Directory.CreateDirectory(lutDir);
    for (int i = 0; i < 3; i++)
    {
        using var src = Image.Load<Rgba32>(args[i + 1]);
        using var strip = BuildLutStrip(src);
        string outPath = Path.Combine(lutDir, ALL_STAGES[i] + ".png");
        strip.Save(outPath);
        Console.WriteLine($"extracted lut/{ALL_STAGES[i]}.png  ({args[i + 1]})");
    }
    return;
}

// ---- apply mode ----
bool includeUnaffected = args.Contains("--unaffected");
string[] stages = ALL_STAGES;
int stagesIdx = Array.IndexOf(args, "--stages");
if (stagesIdx >= 0 && stagesIdx + 1 < args.Length)
    stages = args[stagesIdx + 1].Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

var positional = new List<string>();
for (int i = 0; i < args.Length; i++)
{
    if (args[i] == "--stages") { i++; continue; }
    if (args[i].StartsWith("--")) continue;
    positional.Add(args[i]);
}
if (positional.Count == 0) { Usage(); return; }

string input = positional[0];
string? outDirArg = positional.Count > 1 ? positional[1] : null;

string[] inputs = Directory.Exists(input)
    ? Directory.GetFiles(input, "*.png")
    : new[] { input };
if (inputs.Length == 0) { Console.Error.WriteLine($"no input PNGs at {input}"); Environment.Exit(2); return; }

foreach (var stage in stages)
{
    string lut = Path.Combine(lutDir, stage + ".png");
    if (!File.Exists(lut)) { Console.Error.WriteLine($"missing LUT: {lut} (run 'extract' first, or check --stages)"); Environment.Exit(2); return; }
}

var luts = stages.ToDictionary(s => s, s => Image.Load<Rgba32>(Path.Combine(lutDir, s + ".png")));
int done = 0;
foreach (var file in inputs)
{
    using var src = Image.Load<Rgba32>(file);
    string dir = outDirArg ?? Path.GetDirectoryName(Path.GetFullPath(file))!;
    Directory.CreateDirectory(dir);
    string stem = Path.GetFileNameWithoutExtension(file);
    if (includeUnaffected) src.Save(Path.Combine(dir, $"{stem}_unaffected.png"));
    foreach (var stage in stages)
    {
        using var outImg = ClutSide(src, luts[stage], src.Width, src.Height);
        outImg.Save(Path.Combine(dir, $"{stem}_{stage}.png"));
    }
    Console.WriteLine($"patina: {stem} -> {string.Join(", ", stages.Select(s => $"{stem}_{s}.png"))}");
    done++;
}
foreach (var l in luts.Values) l.Dispose();
Console.WriteLine($"Done: {done} texture(s) x {stages.Length} stage(s).");

// ===== helpers ============================================================================================

void Usage()
{
    Console.WriteLine("patina — recolour a texture through copper's oxidation palette.");
    Console.WriteLine("  dotnet run patina.cs -- <input.png|dir> [outDir] [--unaffected] [--stages exposed,weathered,oxidized]");
    Console.WriteLine("  dotnet run patina.cs -- extract <exposed_copper> <weathered_copper> <oxidized_copper>");
}

// Build a 256x16 luminance->colour LUT strip from a stage texture (samples the stage's own pixels; column x is
// the patina colour for source luminance x, the stage's luma range stretched across the strip).
Image<Rgba32> BuildLutStrip(Image<Rgba32> src)
{
    Rgba32[] ramp = BuildPaletteRamp(src);
    (int tMin, int tMax) = LumRange(src);
    const int rw = 256, rh = 16;
    var strip = new Image<Rgba32>(rw, rh);
    for (int x = 0; x < rw; x++)
    {
        float p = x / (float) (rw - 1);
        int tl = Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255);
        Rgba32 c = ramp[tl];
        for (int y = 0; y < rh; y++) strip[x, y] = new Rgba32(c.R, c.G, c.B, 255);
    }
    return strip;
}

// CLUT — repaint `relief` through a luminance->colour ramp sampled from `palette`, normalising the relief's
// luminance range onto the palette's so the full relief contrast always spans the palette's tonal range. Alpha
// is preserved (transparent pixels stay transparent). Caller owns the returned image.
Image<Rgba32> ClutSide(Image<Rgba32> relief, Image<Rgba32> palette, int w, int h)
{
    Rgba32[] ramp = BuildPaletteRamp(palette);
    (int tMin, int tMax) = LumRange(palette);
    (int vMin, int vMax) = LumRange(relief);
    int vSpan = Math.Max(1, vMax - vMin);

    var img = new Image<Rgba32>(w, h);
    for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++)
    {
        Rgba32 r = relief[x % relief.Width, y % relief.Height];
        float p = (float) (Lum(r.R, r.G, r.B) - vMin) / vSpan;
        int tl = Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255);
        Rgba32 c = ramp[tl];
        img[x, y] = new Rgba32(c.R, c.G, c.B, r.A);
    }
    return img;
}

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
        if (lo < 0 && hi >= 256)  ramp[L] = new Rgba32(0, 0, 0, 255);
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

static string ScriptDir([CallerFilePath] string path = "") => Path.GetDirectoryName(path)!;
