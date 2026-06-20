// Signal campfires (normal + soul) — campfire-look blocks that can't cook and burn out like a TFC torch. They reuse
// vanilla's campfire ART, so this is JSON-only (no texture generation, no recipe — added later): the LIT model
// parents vanilla's campfire/soul_campfire model with render_type=cutout (vanilla registers the campfire cutout
// layer in CODE for its own blocks only, so referencing the model directly would render the fire's transparent
// pixels as opaque black — same gotcha as the soul torches/prismarine deposits); the UNLIT state reuses vanilla's
// opaque campfire_off directly. Blockstate mirrors vanilla campfire (facing rotations × lit). See SignalCampfires.java.

using static Gen;

static class SignalCampfire
{
    public static void Generate()
    {
        // (block name, vanilla LIT model to parent, vanilla flat item sprite to reuse for the icon)
        var variants = new (string name, string litParent, string itemSprite)[]
        {
            ("signal_campfire",      "minecraft:block/campfire",      "minecraft:item/campfire"),
            ("soul_signal_campfire", "minecraft:block/soul_campfire", "minecraft:item/soul_campfire"),
        };

        string bs   = Path.Combine(resRoot, "assets", MODID, "blockstates");
        string md   = Path.Combine(resRoot, "assets", MODID, "models", "block");
        string it   = Path.Combine(resRoot, "assets", MODID, "models", "item");
        string loot = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks");
        foreach (var d in new[] { bs, md, it, loot }) Directory.CreateDirectory(d);

        foreach (var (name, litParent, itemSprite) in variants)
        {
            File.WriteAllText(Path.Combine(md, name + ".json"),
                $$"""{ "parent": "{{litParent}}", "render_type": "minecraft:cutout" }""");
            File.WriteAllText(Path.Combine(bs, name + ".json"), Blockstate(name));
            // Reuse vanilla's flat campfire item icon (vanilla's campfire item is item/generated, not the 3D block).
            File.WriteAllText(Path.Combine(it, name + ".json"), GeneratedItem(itemSprite));
            File.WriteAllText(Path.Combine(loot, name + ".json"), DropItemLoot($"{MODID}:{name}"));
        }

        // Join #minecraft:campfires so flint & steel can (re)light them (CampfireBlock.canLight gates on this tag);
        // also what bees/other campfire logic key off. replace:false append. Only this feature writes this tag path.
        WriteTag("minecraft", "blocks", "campfires", ValuesTag(new[]
        {
            $"    \"{MODID}:signal_campfire\"",
            $"    \"{MODID}:soul_signal_campfire\"",
        }));

        Console.WriteLine($"  signal campfire: {variants.Length} blocks (blockstate/models/item/loot + #minecraft:campfires tag; reuse vanilla campfire art)");
    }

    // Mirror vanilla campfire's blockstate: facing rotations (south 0 / west 90 / north 180 / east 270) × lit,
    // lit=true → our cutout model, lit=false → vanilla's opaque campfire_off.
    static string Blockstate(string name)
    {
        var rot = new (string facing, int y)[] { ("south", 0), ("west", 90), ("north", 180), ("east", 270) };
        var lines = new List<string>();
        foreach (var (facing, y) in rot)
        {
            string yStr = y == 0 ? "" : $", \"y\": {y}";
            lines.Add($"    \"facing={facing},lit=false\": {{ \"model\": \"minecraft:block/campfire_off\"{yStr} }}");
            lines.Add($"    \"facing={facing},lit=true\": {{ \"model\": \"{MODID}:block/{name}\"{yStr} }}");
        }
        return "{\n  \"variants\": {\n" + string.Join(",\n", lines) + "\n  }\n}\n";
    }
}
