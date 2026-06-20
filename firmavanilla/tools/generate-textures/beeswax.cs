// Block of beeswax — vanilla honeycomb_block's comb motif recoloured to FirmaLife beeswax's warm-tan palette via
// the standard luminance-normalized CLUT (vanilla art's exact pixels → beeswax's luminance→colour ramp). A plain
// decorative cube (see WaxBlocks.java), the beeswax analogue of vanilla's 4-honeycomb → honeycomb-block.
//
// firmavanilla keeps NO FirmaLife dependency: the texture ships as a committed derivative, and the only FirmaLife
// touch — the 4×firmalife:beeswax recipe — is gated by a forge:mod_loaded condition, so it just doesn't load when
// FirmaLife is absent. Inputs: input/vanilla/honeycomb_block.png + input/firmalife/beeswax.png (both git-ignored).

using SixLabors.ImageSharp;
using static Gen;

static class Beeswax
{
    public static void Generate()
    {
        using var relief  = Load("vanilla", "honeycomb_block.png");
        using var palette = Load("firmalife", "beeswax.png");

        string tex = Path.Combine(resRoot, "assets", MODID, "textures", "block");
        Directory.CreateDirectory(tex);
        using (var img = ClutSide(relief, palette, relief.Width, relief.Height))
            img.Save(Path.Combine(tex, "beeswax_block.png"));

        string bs   = Path.Combine(resRoot, "assets", MODID, "blockstates");
        string md   = Path.Combine(resRoot, "assets", MODID, "models", "block");
        string it   = Path.Combine(resRoot, "assets", MODID, "models", "item");
        string loot = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks");
        string rec  = Path.Combine(resRoot, "data", MODID, "recipes");
        foreach (var d in new[] { bs, md, it, loot, rec }) Directory.CreateDirectory(d);

        File.WriteAllText(Path.Combine(bs, "beeswax_block.json"),
            """{"variants":{"":{"model":"MODID:block/beeswax_block"}}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(md, "beeswax_block.json"),
            """{"parent":"minecraft:block/cube_all","textures":{"all":"MODID:block/beeswax_block"}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(it, "beeswax_block.json"), ParentItem("beeswax_block"));
        File.WriteAllText(Path.Combine(loot, "beeswax_block.json"),
            """{"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:item","name":"MODID:beeswax_block"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}""".Replace("MODID", MODID));
        // 4 firmalife:beeswax (2×2) → 1 block, mirroring vanilla 4-honeycomb → honeycomb-block. The forge:mod_loaded
        // condition keeps firmavanilla free of any hard FirmaLife dependency (the recipe is skipped when absent).
        File.WriteAllText(Path.Combine(rec, "beeswax_block.json"),
            """{"type":"minecraft:crafting_shaped","conditions":[{"type":"forge:mod_loaded","modid":"firmalife"}],"pattern":["BB","BB"],"key":{"B":{"item":"firmalife:beeswax"}},"result":{"item":"MODID:beeswax_block","count":1}}""".Replace("MODID", MODID));

        Console.WriteLine("  beeswax: 1 block (texture + blockstate/model/item/loot + mod_loaded recipe)");
    }
}
