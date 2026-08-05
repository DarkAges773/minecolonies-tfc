// Coarse dirt — vanilla's coarse-dirt "pebbles" detail carried onto each concrete TFC soil. The textures are now
// HAND-PAINTED (checked in under textures/block/coarse_dirt/), so this feature no longer generates them — the old
// derive-overlay/CLUT/source-over texture pipeline was removed to avoid clobbering the hand art on a re-run. Only the
// block JSON + tags are emitted here. The blocks are plain (NOT TFC DirtBlock / IDirtBlock), so they carry TFC dirt's
// tags yet never transform (no grass spread / shovel→path / hoe→farmland). Crafted from the matching tfc:dirt/<soil> +
// #forge:gravel; landslide via a tfc:landslide recipe. See CoarseDirtBlocks.java.

using static Gen;

static class CoarseDirt
{
    static readonly string[] SOILS = { "loam", "sandy_loam", "silt", "silty_loam" };

    public static void Generate()
    {
        // Textures are hand-painted and checked in — no texture generation here (see the header comment).

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

        Console.WriteLine($"  coarse dirt: {SOILS.Length} blocks (blockstate/models/loot/recipe + tags; textures hand-painted, not generated)");
    }
}
