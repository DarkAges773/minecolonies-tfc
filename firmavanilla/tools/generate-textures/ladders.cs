// Per-wood ladders: a hand-painted jungle-ladder relief recoloured through each wood's PLANKS palette via CLUT (the
// same luminance-normalized palette remap the rest of the mod uses), so every wood's ladder keeps the authored
// rail/rung relief but is repainted into that wood's tones. Registered as the plain vanilla LadderBlock — see
// LadderBlocks.java. Block id firmavanilla:ladder/<wood>.
//
// MASKED CLUT. A hand-painted mask (input/custom/jungle_ladder_mask.png) does double duty, exactly like the barrel
// masks: white = wood (CLUT through planks), black = keep the vanilla pixel (the small dark nail/bolt dots where the
// rungs meet the rails — a consistent "iron" look on every wood, not recoloured wood). It also confines the
// normalization range [vMin,vMax] to the WOOD (white) pixels, so the near-black nails don't drag vMin down and
// squash the wood contrast. Transparency is carried straight from the relief's own alpha (the mask is fully opaque).
//
// Emits textures + blockstate/model/item/loot/recipe + tags. The blockstate/model/item ship unconditionally (harmless
// when an AFC/Beneath wood's block isn't registered); recipes carry a forge:mod_loaded condition and the tag entries
// are required:false for AFC/Beneath. CLIMBING IS TAG-DRIVEN — an entity climbs a block iff it's in
// #minecraft:climbable — so writing that tag (replace:false) is what actually makes these climbable. The wood lists
// mirror the bookshelves/barrels (TFC always; AFC/Beneath generated unconditionally). Source relief + mask come from
// input/custom/; planks from input/<ns>/planks/<wood>.png.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class Ladders
{
    // (ns, woods) — same sets as the decorative bookshelves/barrels (bookshelves.cs / barrels.cs); keep in sync.
    static readonly (string ns, string[] woods)[] LADDER = new (string, string[])[]
    {
        ("tfc", new[] { "acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
                        "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow" }),
        ("afc", new[] { "baobab", "cypress", "eucalyptus", "fig", "hevea", "ipe", "ironwood", "mahogany", "teak", "tualang" }),
        ("beneath", new[] { "crimson", "warped" }),
    };

    public static int Generate()
    {
        string texDir   = Path.Combine(resRoot, "assets", MODID, "textures", "block", "ladder");
        string bsDir    = Path.Combine(resRoot, "assets", MODID, "blockstates", "ladder");
        string modelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "ladder");
        string itemDir  = Path.Combine(resRoot, "assets", MODID, "models", "item", "ladder");
        string lootDir  = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "ladder");
        string recDir   = Path.Combine(resRoot, "data", MODID, "recipes", "ladder");
        foreach (var d in new[] { texDir, bsDir, modelDir, itemDir, lootDir, recDir }) Directory.CreateDirectory(d);

        using var relief = Load("custom", "jungle_ladder.png");
        using var mask   = Load("custom", "jungle_ladder_mask.png");

        int count = 0;
        var climbEntries = new List<string>();   // formatted ladder ids for #minecraft:climbable (AFC/Beneath required:false)
        foreach (var (ns, woods) in LADDER)
        foreach (var wood in woods)
        {
            using (var planks = Load(ns, Path.Combine("planks", wood + ".png")))
            using (var outImg = ClutMasked(relief, planks, mask))
                outImg.Save(Path.Combine(texDir, wood + ".png"));

            File.WriteAllText(Path.Combine(bsDir, wood + ".json"), Blockstate(wood));
            File.WriteAllText(Path.Combine(modelDir, wood + ".json"), Model(wood));
            File.WriteAllText(Path.Combine(itemDir, wood + ".json"), GeneratedItem($"{MODID}:block/ladder/{wood}"));
            File.WriteAllText(Path.Combine(lootDir, wood + ".json"), DropItemLoot($"{MODID}:ladder/{wood}"));
            File.WriteAllText(Path.Combine(recDir, wood + ".json"), Recipe(ns, wood));

            // Tag membership matching vanilla minecraft:ladder: #minecraft:climbable (below) + #minecraft:mineable/axe
            // (shared accumulator, written once by the entry point). AFC/Beneath ids are required:false.
            string entry = ns == "tfc"
                ? $"    \"{MODID}:ladder/{wood}\""
                : $"    {{ \"id\": \"{MODID}:ladder/{wood}\", \"required\": false }}";
            axeMineable.Add(entry);
            climbEntries.Add(entry);
            count++;
        }

        // What actually makes them climbable — vanilla checks #minecraft:climbable per entity tick. replace:false so we
        // merge into the vanilla ladder/vine set rather than clobber it.
        WriteTag("minecraft", "blocks", "climbable", ValuesTag(climbEntries));

        Console.WriteLine($"  ladders: {count} wood-variant blocks (masked CLUT textures + blockstate/model/loot/recipe + climbable/axe tags)");
        return count;
    }

    // Vanilla ladder blockstate: the ladder model rotated per horizontal facing.
    static string Blockstate(string wood) =>
        $$"""
        {
          "variants": {
            "facing=east":  { "model": "{{MODID}}:block/ladder/{{wood}}", "y": 90 },
            "facing=north": { "model": "{{MODID}}:block/ladder/{{wood}}" },
            "facing=south": { "model": "{{MODID}}:block/ladder/{{wood}}", "y": 180 },
            "facing=west":  { "model": "{{MODID}}:block/ladder/{{wood}}", "y": 270 }
          }
        }
        """;

    // Self-contained copy of vanilla's block/ladder geometry (a single unshaded plane at z=15.2), textured with our
    // per-wood face. Not parented to minecraft:block/ladder so it never depends on that model's texture-variable name.
    // render_type cutout (Forge model field) so the gaps between rungs show through — matches vanilla ladder's layer.
    static string Model(string wood) =>
        $$"""
        {
          "ambientocclusion": false,
          "render_type": "minecraft:cutout",
          "textures": {
            "particle": "{{MODID}}:block/ladder/{{wood}}",
            "texture": "{{MODID}}:block/ladder/{{wood}}"
          },
          "elements": [
            {
              "from": [ 0, 0, 15.2 ],
              "to": [ 16, 16, 15.2 ],
              "shade": false,
              "faces": {
                "north": { "uv": [ 0, 0, 16, 16 ], "texture": "#texture" },
                "south": { "uv": [ 16, 0, 0, 16 ], "texture": "#texture" }
              }
            }
          ]
        }
        """;

    // Vanilla's ladder shape (X X / XXX / X X -> 3) but keyed to that wood's lumber, so each ladder is wood-specific.
    // AFC/Beneath recipes carry a forge:mod_loaded condition so they're silently skipped when the mod — hence its
    // lumber — is absent.
    static string Recipe(string ns, string wood)
    {
        string cond = ns == "tfc"
            ? ""
            : $"\n  \"conditions\": [ {{ \"type\": \"forge:mod_loaded\", \"modid\": \"{ns}\" }} ],";
        return $$"""
        {
          "type": "minecraft:crafting_shaped",{{cond}}
          "pattern": [ "X X", "XXX", "X X" ],
          "key": { "X": { "item": "{{ns}}:wood/lumber/{{wood}}" } },
          "result": { "item": "{{MODID}}:ladder/{{wood}}", "count": 3 }
        }
        """;
    }

    // CLUT the WOOD region (mask white) through the planks; the masked (black) region keeps the vanilla pixel (the
    // nail/bolt dots). The wood region's normalization range is measured over wood pixels only, so the near-black
    // nails don't compress the wood contrast (the whole point of the mask). Alpha is carried from the relief.
    static Image<Rgba32> ClutMasked(Image<Rgba32> relief, Image<Rgba32> palette, Image<Rgba32> maskSrc)
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

        var img = new Image<Rgba32>(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
        {
            Rgba32 r = relief[x, y];
            if (IsWood(mk[x, y]))                                 // wood rails/rungs: wood-only range across full palette
            {
                float p = (float) (Lum(r.R, r.G, r.B) - vMin) / vSpan;
                Rgba32 c = ramp[Math.Clamp((int) MathF.Round(tMin + p * (tMax - tMin)), 0, 255)];
                img[x, y] = new Rgba32(c.R, c.G, c.B, r.A);
            }
            else img[x, y] = r;                                  // nail/bolt dots: keep vanilla
        }
        return img;
    }

    static bool IsWood(Rgba32 m) => m.A >= 128 && Lum(m.R, m.G, m.B) >= 128;   // white = wood (CLUT), black = keep
}
