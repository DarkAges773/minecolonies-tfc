// Moss block — a TFC loom recipe (no new block/texture) that makes vanilla's minecraft:moss_block reachable under
// TFC. Vanilla never ships a moss_block recipe (it's lush-caves/trader loot only), so a TFC world has no way to
// obtain it; TFC does grow its own moss plant (tfc:plant/moss). This fills that gap, in keeping with the mod's
// mission (vanilla building forms TFC lacks): woven on the TFC loom, 4 tfc:plant/moss -> 2 minecraft:moss_block.
//
// Uses TFC's `tfc:loom` recipe type (same shape as tfc's own wool_block loom recipe): an ItemStackIngredient with a
// count, a result, steps_required (TFC's own recipes set this equal to the input count), and the in-progress block
// texture shown on the loom while weaving. Pure datapack (JSON only): firmavanilla compiles against TFC so both the
// recipe type and tfc:plant/moss are always present, and the result is a vanilla block — no mod_loaded condition.
// Recipe id firmavanilla:moss_block (the result is the vanilla block; only the recipe lives in our namespace).

using static Gen;

static class Moss
{
    public static void Generate()
    {
        string rec = Path.Combine(resRoot, "data", MODID, "recipes", "loom");
        Directory.CreateDirectory(rec);

        File.WriteAllText(Path.Combine(rec, "moss_block.json"),
            """{"type":"tfc:loom","ingredient":{"ingredient":{"item":"tfc:plant/moss"},"count":4},"result":{"item":"minecraft:moss_block","count":2},"steps_required":4,"in_progress_texture":"minecraft:block/moss_block"}""");

        Console.WriteLine("  moss: 1 tfc:loom recipe (4 tfc:plant/moss -> 2 minecraft:moss_block)");
    }
}
