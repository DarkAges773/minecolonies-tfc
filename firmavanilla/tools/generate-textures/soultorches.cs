// Soul torches — vanilla soul-torch look + TFC's burn-out (code-side: SoulTorches.java / SoulTorchBlock /
// SoulWallTorchBlock / SoulTorchInteraction). No textures are generated — we reuse the vanilla soul-torch SPRITE —
// but we DO emit our own block models (vanilla torch geometry + that sprite) carrying Forge's `render_type: cutout`,
// because vanilla registers the torch cutout layer in code for its own blocks only; reusing vanilla's model would
// render our torch's transparent pixels as opaque black. The flame is the SOUL_FIRE_FLAME particle the block hands
// to TFC's torch block. This file emits: block models, blockstates, the item model, self-drop loot for each block,
// and the catalyst craft. Lang is in en_us.json.

using static Gen;

static class SoulTorches
{
    public static void Generate()
    {
        string bsDir    = Path.Combine(resRoot, "assets", MODID, "blockstates");
        string modelDir = Path.Combine(resRoot, "assets", MODID, "models", "block");
        string itemDir  = Path.Combine(resRoot, "assets", MODID, "models", "item");
        string lootDir  = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks");
        string recDir   = Path.Combine(resRoot, "data", MODID, "recipes");
        foreach (var d in new[] { bsDir, modelDir, itemDir, lootDir, recDir }) Directory.CreateDirectory(d);

        // Our own torch models (vanilla geometry + the vanilla soul-torch sprite) that add Forge's
        // `render_type: cutout`. We can't reuse vanilla's `block/soul_torch` model directly: vanilla registers the
        // torch's cutout render layer in CODE for its OWN blocks, so our blocks would fall back to the SOLID layer
        // and draw the torch sprite's transparent pixels as opaque black. The render_type tag fixes it data-side.
        File.WriteAllText(Path.Combine(modelDir, "soul_torch.json"),
            """{"parent":"minecraft:block/template_torch","render_type":"minecraft:cutout","textures":{"torch":"minecraft:block/soul_torch"}}""");
        File.WriteAllText(Path.Combine(modelDir, "soul_wall_torch.json"),
            """{"parent":"minecraft:block/template_torch_wall","render_type":"minecraft:cutout","textures":{"torch":"minecraft:block/soul_torch"}}""");

        // Standing: single variant. Wall: 4 facings (the same y-rotations vanilla uses) -> our cutout models.
        File.WriteAllText(Path.Combine(bsDir, "soul_torch.json"),
            """{"variants":{"":{"model":"MODID:block/soul_torch"}}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(bsDir, "soul_wall_torch.json"),
            """
            {
              "variants": {
                "facing=east":  { "model": "MODID:block/soul_wall_torch" },
                "facing=north": { "model": "MODID:block/soul_wall_torch", "y": 270 },
                "facing=south": { "model": "MODID:block/soul_wall_torch", "y": 90 },
                "facing=west":  { "model": "MODID:block/soul_wall_torch", "y": 180 }
              }
            }
            """.Replace("MODID", MODID));
        // Item icon = vanilla soul-torch flat sprite.
        File.WriteAllText(Path.Combine(itemDir, "soul_torch.json"),
            """{"parent":"item/generated","textures":{"layer0":"minecraft:block/soul_torch"}}""");
        // Both blocks drop the (standing) soul torch item.
        File.WriteAllText(Path.Combine(lootDir, "soul_torch.json"), DropItemLoot($"{MODID}:soul_torch"));
        File.WriteAllText(Path.Combine(lootDir, "soul_wall_torch.json"), DropItemLoot($"{MODID}:soul_torch"));
        // Craft: a lit TFC torch + any soul catalyst (the same tag the soul lamps use) -> soul torch.
        File.WriteAllText(Path.Combine(recDir, "soul_torch.json"),
            """{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"tfc:torch"},{"tag":"MODID:soul_lamp_catalyst"}],"result":{"item":"MODID:soul_torch"}}""".Replace("MODID", MODID));

        Console.WriteLine("  soul torches: standing + wall (cutout models on the vanilla soul-torch sprite + SOUL_FIRE_FLAME) + item/loot/catalyst recipe");
    }
}
