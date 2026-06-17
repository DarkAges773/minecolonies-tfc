// Coarse dirt — vanilla's coarse-dirt "pebbles" detail carried onto each concrete TFC soil. First DERIVE an
// overlay isolating where coarse_dirt differs from plain dirt (rgb = coarse colour, alpha = the difference,
// amplified), saved to the tool root (tracked/tweakable); CLUT that overlay through a gravel palette so the
// pebbles take a stone colour; then source-over it onto each TFC dirt. The blocks are plain (NOT TFC
// DirtBlock / IDirtBlock), so they carry TFC dirt's tags yet never transform (no grass spread / shovel→path /
// hoe→farmland). Crafted from the matching tfc:dirt/<soil> + #forge:gravel; landslide via a tfc:landslide
// recipe. See CoarseDirtBlocks.java.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using static Gen;

static class CoarseDirt
{
    static readonly string[] SOILS = { "loam", "sandy_loam", "silt", "silty_loam" };

    public static void Generate()
    {
        const int COARSE_OVERLAY_GAIN = 5; // amplify the subtle coarse↔dirt difference into a visible pebble alpha
        string cdTex = Path.Combine(resRoot, "assets", MODID, "textures", "block", "coarse_dirt");
        Directory.CreateDirectory(cdTex);
        using var coarse = Load("vanilla", "coarse_dirt.png");
        using var dirt = Load("vanilla", "dirt.png");
        using var gravel = Load("tfc/gravel", "gabbro.png");
        int w = coarse.Width, h = coarse.Height;

        // Derive the overlay (build a NEW image — ImageSharp here doesn't persist set-on-loaded through Save).
        var overlay = new Image<Rgba32>(w, h);
        for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++)
        {
            Rgba32 c = coarse[x, y], d = dirt[x, y];
            int diff = Math.Max(Math.Abs(c.R - d.R), Math.Max(Math.Abs(c.G - d.G), Math.Abs(c.B - d.B)));
            byte a = (byte) Math.Clamp(diff * COARSE_OVERLAY_GAIN, 0, 255);
            overlay[x, y] = new Rgba32(c.R, c.G, c.B, a);
        }
        overlay.Save(Path.Combine(scriptDir, "coarse_dirt_overlay.png"));

        // Per soil: CLUT the overlay through that soil's dirt palette FIRST (so the pebbles take the soil's own colour
        // instead of vanilla's brown — ClutSide keeps the overlay's alpha), then source-over the recoloured overlay.
        using var clutOverlay = ClutSide(overlay, gravel, w, h);
        foreach (var soil in SOILS)
        {
            using var tfcDirt = Load("tfc", Path.Combine("dirt", soil + ".png"));
            var outImg = new Image<Rgba32>(w, h);
            for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
            {
                Rgba32 o = clutOverlay[x, y], b = tfcDirt[x, y];
                float oa = o.A / 255f;
                outImg[x, y] = new Rgba32(
                    (byte) Math.Round(o.R * oa + b.R * (1 - oa)),
                    (byte) Math.Round(o.G * oa + b.G * (1 - oa)),
                    (byte) Math.Round(o.B * oa + b.B * (1 - oa)),
                    b.A);
            }
            outImg.Save(Path.Combine(cdTex, soil + ".png"));
            outImg.Dispose();
        }
        overlay.Dispose();

        // Blocks: per soil, a blockstate (cube_all) + block/item models + drop-self loot + crafting recipe (vanilla
        // coarse_dirt's 2x2 checkerboard, but with the matching CONCRETE tfc:dirt/<soil> and the #forge:gravel tag) +
        // classification tags. See CoarseDirtBlocks.java — these are plain blocks (NOT TFC DirtBlock / IDirtBlock), so
        // they carry TFC dirt's tags yet never transform (no grass spread / shovel→path / hoe→farmland).
        string cdBs = Path.Combine(resRoot, "assets", MODID, "blockstates", "coarse_dirt");
        string cdMd = Path.Combine(resRoot, "assets", MODID, "models", "block", "coarse_dirt");
        string cdIt = Path.Combine(resRoot, "assets", MODID, "models", "item", "coarse_dirt");
        string cdLoot = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "coarse_dirt");
        string cdRec = Path.Combine(resRoot, "data", MODID, "recipes", "coarse_dirt");
        string cdLandslide = Path.Combine(resRoot, "data", MODID, "recipes", "landslide");
        foreach (var d in new[] { cdBs, cdMd, cdIt, cdLoot, cdRec, cdLandslide }) Directory.CreateDirectory(d);
        var cdIds = new List<string>();
        foreach (var soil in SOILS)
        {
            cdIds.Add($"{MODID}:coarse_dirt/{soil}");
            File.WriteAllText(Path.Combine(cdBs, soil + ".json"),
                """{"variants":{"":{"model":"MODID:block/coarse_dirt/SOIL"}}}""".Replace("MODID", MODID).Replace("SOIL", soil));
            File.WriteAllText(Path.Combine(cdMd, soil + ".json"),
                """{"parent":"minecraft:block/cube_all","textures":{"all":"MODID:block/coarse_dirt/SOIL"}}""".Replace("MODID", MODID).Replace("SOIL", soil));
            File.WriteAllText(Path.Combine(cdIt, soil + ".json"), ParentItem($"coarse_dirt/{soil}"));
            File.WriteAllText(Path.Combine(cdLoot, soil + ".json"),
                """{"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:item","name":"MODID:coarse_dirt/SOIL"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}""".Replace("MODID", MODID).Replace("SOIL", soil));
            File.WriteAllText(Path.Combine(cdRec, soil + ".json"),
                """{"type":"minecraft:crafting_shaped","pattern":["DG","GD"],"key":{"D":{"item":"tfc:dirt/SOIL"},"G":{"tag":"forge:gravel"}},"result":{"item":"MODID:coarse_dirt/SOIL","count":4}}""".Replace("MODID", MODID).Replace("SOIL", soil));
            // The can_landslide tag only ENQUEUES a check; TFC's tryLandslide also needs a matching tfc:landslide recipe
            // or it does nothing. Collapse into itself (coarse dirt stays coarse dirt), like TFC plain dirt landslides.
            File.WriteAllText(Path.Combine(cdLandslide, "coarse_dirt_" + soil + ".json"),
                """{"type":"tfc:landslide","ingredient":"MODID:coarse_dirt/SOIL","result":"MODID:coarse_dirt/SOIL"}""".Replace("MODID", MODID).Replace("SOIL", soil));
        }
        // Match TFC dirt's classification tag (transforms are IDirtBlock/DirtBlock-gated, never tag-gated, so this is
        // safe). tfc:dirt cascades into minecraft:dirt / sniffer / can_carve (which reference #tfc:dirt). The shovel +
        // landslide tags are written once at the end (shared with the prismarine deposits).
        string cdTagBody = IdsTagBody(cdIds);
        WriteTag("tfc", "blocks", "dirt", cdTagBody);
        WriteTag("tfc", "items", "dirt", cdTagBody);
        shovelMineable.AddRange(cdIds);
        canLandslide.AddRange(cdIds);

        Console.WriteLine($"  coarse dirt: overlay → tool root + {SOILS.Length} blocks (textures + blockstate/models/loot/recipe + tags)");
    }
}
