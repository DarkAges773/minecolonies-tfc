// Decorative per-wood bookshelves (no generated palettes; models reference each source mod's plank textures
// directly). The side face is a vanilla-style books overlay composited onto each wood's empty bookshelf frame.
// TFC woods are always present; AFC/Beneath woods exist only when those mods are loaded — their client assets
// ship unconditionally (unused when unregistered), recipes carry a forge:mod_loaded condition, and the
// axe/bookshelf tag entries are required:false, so nothing errors when a mod is absent.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class Bookshelves
{
    static readonly (string ns, string[] woods)[] BOOKSHELF = new (string, string[])[]
    {
        ("tfc", new[] { "acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
                        "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow" }),
        ("afc", new[] { "baobab", "cypress", "eucalyptus", "fig", "hevea", "ipe", "ironwood", "mahogany", "teak", "tualang" }),
        ("beneath", new[] { "crimson", "warped" }),
    };

    public static int Generate()
    {
        string bkTexDir   = Path.Combine(resRoot, "assets", MODID, "textures", "block", "bookshelf");
        string bkBsDir    = Path.Combine(resRoot, "assets", MODID, "blockstates", "bookshelf");
        string bkModelDir = Path.Combine(resRoot, "assets", MODID, "models", "block", "bookshelf");
        string bkItemDir  = Path.Combine(resRoot, "assets", MODID, "models", "item", "bookshelf");
        string bkRecDir   = Path.Combine(resRoot, "data", MODID, "recipes", "bookshelf");
        foreach (var d in new[] { bkTexDir, bkBsDir, bkModelDir, bkItemDir, bkRecDir }) Directory.CreateDirectory(d);

        // Vanilla-style book spines (authored, with transparency where there's no book) — overlaid on each wood's
        // empty bookshelf frame so the books read like a vanilla bookshelf rather than the chiseled 6-slot look.
        using var bookOverlay = Load("vanilla", "bookshelf_overlay.png");

        var tagEntries = new List<string>();
        int books = 0;
        foreach (var (ns, woods) in BOOKSHELF)
        foreach (var wood in woods)
        {
            // Side texture = the books overlay composited (source-over) onto this wood's empty frame.
            using (var empty = Load(ns, Path.Combine("bookshelf_empty", wood + ".png")))
            using (var ov = bookOverlay.Clone(c => c.Resize(empty.Width, empty.Height)))
            {
                empty.Mutate(c => c.DrawImage(ov, PixelColorBlendingMode.Normal, 1f));
                empty.Save(Path.Combine(bkTexDir, wood + ".png"));
            }

            File.WriteAllText(Path.Combine(bkBsDir, wood + ".json"), BookshelfBlockstate(wood));
            File.WriteAllText(Path.Combine(bkModelDir, wood + ".json"), BookshelfModel(ns, wood));
            File.WriteAllText(Path.Combine(bkItemDir, wood + ".json"), BookshelfItemModel(wood));
            File.WriteAllText(Path.Combine(bkRecDir, wood + ".json"), BookshelfRecipe(ns, wood));
            tagEntries.Add(ns == "tfc"
                ? $"    \"{MODID}:bookshelf/{wood}\""
                : $"    {{ \"id\": \"{MODID}:bookshelf/{wood}\", \"required\": false }}");
            books++;
        }
        // Tag membership matching vanilla minecraft:bookshelf, so the decorative blocks behave the same: mineable/axe,
        // enchantment_power_provider (enchanting-table power) and forge:bookshelves (block + item). replace:false
        // appends to the existing tags; AFC/Beneath entries are required:false so they're skipped when the mod is absent.
        string tagBody = "{\n  \"replace\": false,\n  \"values\": [\n" + string.Join(",\n", tagEntries) + "\n  ]\n}\n";
        // mineable/axe is SHARED with the wood barrels — accumulate and let the entry point write it once (a mod can
        // ship only one file per tag path). The other two are bookshelf-only.
        axeMineable.AddRange(tagEntries);
        WriteTag("minecraft", "blocks", "enchantment_power_provider", tagBody);
        WriteTag("forge", "blocks", "bookshelves", tagBody);
        WriteTag("forge", "items", "bookshelves", tagBody);
        return books;
    }

    static string BookshelfBlockstate(string wood) =>
        $$"""
        {
          "variants": {
            "": { "model": "{{MODID}}:block/bookshelf/{{wood}}" }
          }
        }
        """;

    // cube_column: our generated books-on-frame face on the 4 sides, the source mod's wood planks (referenced
    // directly) on top/bottom — mirroring vanilla bookshelf (books on the sides, planks on the caps).
    static string BookshelfModel(string ns, string wood) =>
        $$"""
        {
          "parent": "minecraft:block/cube_column",
          "textures": {
            "end": "{{ns}}:block/wood/planks/{{wood}}",
            "side": "{{MODID}}:block/bookshelf/{{wood}}"
          }
        }
        """;

    static string BookshelfItemModel(string wood) =>
        $$"""
        { "parent": "{{MODID}}:block/bookshelf/{{wood}}" }
        """;

    // 6 lumber + 3 books (vanilla bookshelf shape, but lumber instead of plank blocks — lumber exists for every
    // wood with no exceptions). AFC/Beneath recipes carry a forge:mod_loaded condition so they're silently
    // skipped (no error) when the mod — hence the lumber item and result block — is absent.
    static string BookshelfRecipe(string ns, string wood)
    {
        string cond = ns == "tfc"
            ? ""
            : $"\n      \"conditions\": [ {{ \"type\": \"forge:mod_loaded\", \"modid\": \"{ns}\" }} ],";
        return $$"""
        {
          "type": "minecraft:crafting_shaped",{{cond}}
          "pattern": [ "LLL", "BBB", "LLL" ],
          "key": {
            "L": { "item": "{{ns}}:wood/lumber/{{wood}}" },
            "B": { "item": "minecraft:book" }
          },
          "result": { "item": "{{MODID}}:bookshelf/{{wood}}" }
        }
        """;
    }
}
