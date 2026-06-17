// Weathering copper — TFC's copper given the vanilla copper lifecycle (unaffected -> exposed -> weathered ->
// oxidized, plus waxed twins). Three pieces, in order (bars/forms read the patina strips the first pass writes):
//   1) patina palettes: a reusable luminance->colour LUT per aged stage, sampled straight from vanilla's
//      exposed/weathered/oxidized copper, emitted as a 256x16 strip to the tool root (tracked).
//   2) copper bars + waxed twins: TFC's iron-bars multipart retextured; aged stages recolour the grate/edge
//      through the patina LUTs, bright stage reuses TFC's copper-bar textures verbatim.
//   3) the other forms (plated block/stairs/slab, cut block/stairs/slab, chain, trapdoor) + waxed twins:
//      block/stairs/slab reuse vanilla cut-copper textures; chain/trapdoor recolour TFC textures via the LUTs.
// The bright weathering stage drops its TFC item (no item of its own) and is melt-recipe-less; others self/melt.
// See CopperBarsBlocks.java + weathering/WeatheringMaps.java.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class Copper
{
    public static (int patina, int copperBars, int copperForms) Generate()
    {
        int patina = Patina();
        int copperBars = Bars();
        int copperForms = Forms();
        return (patina, copperBars, copperForms);
    }

    // --- patina palettes (extracted from vanilla copper weathering stages) ---
    // A reusable luminance->colour LUT per oxidation stage, sampled straight from vanilla's exposed/weathered/
    // oxidized copper (no subtraction, no copper_block needed — the stage texture's own pixels ARE the palette).
    // Emitted as a 256x16 ramp strip into the tool root (tracked, like grain_mask.png); feed any of these to
    // ClutThrough() as the lutBase to patina-ify an arbitrary block later. See input/README.md.
    static int Patina()
    {
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
        return patina;
    }

    // --- weathering TFC copper bars (vanilla copper lifecycle) ---
    // Eight blocks: the 4 weather stages copper_bars/<stage> + their 4 waxed twins waxed_copper_bars/<stage>,
    // for unaffected/exposed/weathered/oxidized. Stage 0 (unaffected) reuses TFC's own copper-bar textures
    // verbatim (identity, no LUT); the 3 aged stages recolour the grate + smooth "edge" through the patina LUTs
    // (ClutSide reads the 256-wide strip as ramp, emits at native 16x16, transparent bar pixels keep alpha). The
    // waxed twins reuse the same 4 textures (vanilla waxed copper looks identical). Models/blockstate mirror TFC's
    // iron-bars multipart. The bright stage has no item (TFC's copper bars item stands in) and drops the TFC item.
    static int Bars()
    {
        int copperBars = 0;
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
                    DropItemLoot(brightWeathering ? "tfc:metal/bars/copper" : $"{MODID}:{kind}/{stage}"));
                // Melting: mirror TFC's copper bars (25 mB tfc:metal/copper at 1080°C). The bright stage uses TFC's
                // item, which already has TFC's heating recipe — skip it (no firmavanilla item to key on).
                if (!brightWeathering)
                    File.WriteAllText(Path.Combine(cbHeatDir, $"{stage}.json"),
                        HeatingMelt($"{MODID}:{kind}/{stage}", "tfc:metal/copper", 25, 1080));
                copperBars++;
            }
        }
        Console.WriteLine($"  weathering copper bars: {copperBars} blocks (4 stages x2 kinds) + 4 grate/edge textures");
        return copperBars;
    }

    // --- weathering copper: plated block + stairs/slab (vanilla cut-copper textures, no generation) and chains +
    //     trapdoors (TFC textures recoloured through the patina LUTs). Same 8-block-per-form layout as the bars. ---
    static int Forms()
    {
        int copperForms = 0;
        string A(params string[] p) => Path.Combine(new[] { resRoot, "assets", MODID }.Concat(p).ToArray());
        string D(params string[] p) => Path.Combine(new[] { resRoot, "data", MODID }.Concat(p).ToArray());
        // Shared loot (bright stage drops its TFC item; others self) + melt recipe (skip the itemless bright stage).
        void Common(string kind, string stage, bool bright, string tfcItem, int melt)
        {
            Directory.CreateDirectory(D("loot_tables", "blocks", kind));
            File.WriteAllText(Path.Combine(D("loot_tables", "blocks", kind), stage + ".json"),
                DropItemLoot(bright ? tfcItem : $"{MODID}:{kind}/{stage}"));
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
        return copperForms;
    }

    // ---- copper JSON emitters ---------------------------------------------

    // One part model: parent the vanilla iron_bars_<part>, point bars/particle at our recoloured grate and edge
    // at our recoloured smooth copper. render_type cutout_mipped (Forge) — vanilla registers iron bars on that layer
    // in code; our blocks don't get that registration, so without it the grate's transparent pixels render opaque.
    static string CopperBarsModel(string stage, string part) =>
        $$$"""
        {"parent":"minecraft:block/iron_bars_{{{part}}}","render_type":"minecraft:cutout_mipped","textures":{"particle":"{{{MODID}}}:block/copper_bars/{{{stage}}}","bars":"{{{MODID}}}:block/copper_bars/{{{stage}}}","edge":"{{{MODID}}}:block/copper_bars/{{{stage}}}_edge"}}
        """;

    // Multipart blockstate — identical topology to TFC's metal/bars/copper, retargeted at our per-kind/stage models.
    static string CopperBarsBlockstate(string kind, string stage)
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

    static string CopperBarsItem(string stage) =>
        $$$"""
        {"parent":"item/generated","textures":{"layer0":"{{{MODID}}}:block/copper_bars/{{{stage}}}"}}
        """;

    // Plain shaped crafting — mirrors TFC's metal block→stairs/slab recipes: `pattern` (X = `fromItem`) → `result`×count.
    static string ShapedFromBlock(string fromItem, string result, string pattern, int count) =>
        $$$"""{"type":"minecraft:crafting_shaped","pattern":{{{pattern}}},"key":{"X":{"item":"{{{fromItem}}}"}},"result":{"item":"{{{result}}}","count":{{{count}}}}}""";

    // Vanilla plain copper-block texture id for a weather stage (TFC's plated block is the smooth full block, so it
    // maps to copper_block/exposed_copper/… — NOT the lined cut_copper). Stairs/slabs reuse this on every face.
    static string CopperBlockTex(string stage) => "minecraft:block/" + (stage == "unaffected" ? "copper_block" : $"{stage}_copper");
    // Vanilla cut-copper texture id for a weather stage (the firmavanilla "cut" forms).
    static string CutCopperTex(string stage) => "minecraft:block/" + (stage == "unaffected" ? "cut_copper" : $"{stage}_cut_copper");

    // Vanilla stonecutting recipe (full ids).
    static string Stonecutting(string ingredient, string result, int count) =>
        $$$"""{"type":"minecraft:stonecutting","ingredient":{"item":"{{{ingredient}}}"},"result":"{{{result}}}","count":{{{count}}}}""";

    static string ChainModelTex(string tex) => $$"""{ "parent": "minecraft:block/chain", "render_type": "minecraft:cutout_mipped", "textures": { "all": "{{tex}}", "particle": "{{tex}}" } }""";
    static string ChainBlockstate(string kind, string stage)
    {
        string m = $"{MODID}:block/{kind}/{stage}";
        return $$"""{ "variants": { "axis=x": { "model": "{{m}}", "x": 90, "y": 90 }, "axis=y": { "model": "{{m}}" }, "axis=z": { "model": "{{m}}", "x": 90 } } }""";
    }

    static (string suffix, string body)[] TrapdoorModelsTex(string tex) => new[]
    {
        ("_bottom", $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_bottom", "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
        ("_top",    $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_top",    "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
        ("_open",   $$"""{ "parent": "minecraft:block/template_orientable_trapdoor_open",   "render_type": "minecraft:cutout", "textures": { "texture": "{{tex}}" } }"""),
    };

    // Orientable trapdoor blockstate — mirrors TFC's metal/trapdoor/copper, retargeted at our per-kind/stage models.
    static string TrapdoorBlockstate(string kind, string stage)
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
}
