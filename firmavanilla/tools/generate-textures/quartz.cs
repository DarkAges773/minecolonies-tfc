// Quartz — three related pieces:
//   1) raw_quartz_column: a vanilla-quartz-pillar-style block with TFC raw-rock drops (isolated -> self; else
//      1-4 nether quartz). Behaviour is pure data (tfc:breaks_when_isolated + an is-isolated loot table).
//   2) quartz_cluster: the self-shaping connected cave block the worldgen feature places (6 boolean sides;
//      shape = union of a half-slab per connected side, rendered as 8 disjoint octants to avoid z-fighting),
//      plus its cave-decoration feature (block-only tfc:carving_mask, gated to volcanoes + quartz-bearing rock).
//   3) the vanilla-quartz chisel/mortar chain: a firmavanilla:quartz_brick item makes the whole vanilla quartz
//      block family reachable under TFC, and the vanilla recipes for those blocks are disabled (forge:false).
// See QuartzBlocks.java / QuartzClusterBlock.java / QuartzClusterFeature.java.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using static Gen;

static class Quartz
{
    public static void Generate()
    {
        Column();
        BrickChain();
    }

    // --- raw quartz column + quartz_cluster connected cave block + cave feature ---
    static void Column()
    {
        string qTex = Path.Combine(resRoot, "assets", MODID, "textures", "block");
        Directory.CreateDirectory(qTex);
        File.Copy(Path.Combine(scriptDir, "quartz.png"), Path.Combine(qTex, "raw_quartz_column.png"), overwrite: true);
        File.Copy(Path.Combine(scriptDir, "quartz_top.png"), Path.Combine(qTex, "raw_quartz_column_top.png"), overwrite: true);
        string qBs = Path.Combine(resRoot, "assets", MODID, "blockstates");
        string qMd = Path.Combine(resRoot, "assets", MODID, "models", "block");
        string qIt = Path.Combine(resRoot, "assets", MODID, "models", "item");
        string qLoot = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks");
        foreach (var d in new[] { qBs, qMd, qIt, qLoot }) Directory.CreateDirectory(d);
        File.WriteAllText(Path.Combine(qBs, "raw_quartz_column.json"),
            """{"variants":{"axis=x":{"model":"MODID:block/raw_quartz_column_horizontal","x":90,"y":90},"axis=y":{"model":"MODID:block/raw_quartz_column"},"axis=z":{"model":"MODID:block/raw_quartz_column_horizontal","x":90}}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(qMd, "raw_quartz_column.json"),
            """{"parent":"minecraft:block/cube_column","textures":{"end":"MODID:block/raw_quartz_column_top","side":"MODID:block/raw_quartz_column"}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(qMd, "raw_quartz_column_horizontal.json"),
            """{"parent":"minecraft:block/cube_column_horizontal","textures":{"end":"MODID:block/raw_quartz_column_top","side":"MODID:block/raw_quartz_column"}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(qIt, "raw_quartz_column.json"), ParentItem("raw_quartz_column"));
        // loot: isolated → drop self (TFC raw-rock), else → 1-4 nether quartz.
        File.WriteAllText(Path.Combine(qLoot, "raw_quartz_column.json"),
            """{"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:alternatives","children":[{"type":"minecraft:item","name":"MODID:raw_quartz_column","conditions":[{"condition":"tfc:is_isolated"}]},{"type":"minecraft:item","name":"minecraft:quartz","functions":[{"function":"minecraft:set_count","count":{"min":1,"max":4,"type":"minecraft:uniform"}}]}]}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}""".Replace("MODID", MODID));
        // join TFC's isolation tag (merges; both quartz blocks are added to minecraft:mineable/pickaxe in MineableTag()).
        // The cluster shares the column's TFC raw-rock behaviour: it pops/drops itself when left unsupported.
        WriteTag("tfc", "blocks", "breaks_when_isolated", """{"replace":false,"values":["MODID:raw_quartz_column","MODID:quartz_cluster"]}""".Replace("MODID", MODID));

        // The self-shaping CAVE block the worldgen feature actually places: firmavanilla:quartz_cluster, a connected
        // block (6 boolean sides) whose shape is the UNION OF A HALF-SLAB per connected side — 1 side = slab, 2 adjacent
        // = stair, 3 adjacent = corner stair, 2 opposite = full block (and every other combo falls out the same way).
        // Reuses the raw_quartz_column side/top textures (#side/#end). Multipart blockstate (one half-slab part per
        // side); an inventory model (a representative stair) for the creative icon; drops 1-2 nether quartz.
        // See QuartzClusterBlock.java (which builds the matching collision VoxelShape).
        {
            // Build one cube element [from]..[to] with grain `axis`: the two faces perpendicular to it get the
            // quartz-top cap (#end); the rest get #side with the grain ROTATED to run along the axis. A #side face's
            // default grain runs along its `naturalGrain` axis ('y' for the upright n/s/e/w faces, 'z' for up/down);
            // when that doesn't match the grain axis we rotate the face's UV 90° so the grain follows the axis. This
            // is what makes the pillar/full-block side grain orient like the original quartz pillar on x/z.
            // perp = the axis each face is perpendicular to; naturalGrain = where that face's grain runs by default.
            string FaceJson(string name, char perp, char naturalGrain, char axis)
            {
                if (perp == axis) return $"\"{name}\":{{\"texture\":\"#end\"}}";
                string rot = naturalGrain == axis ? "" : ",\"rotation\":90";
                return $"\"{name}\":{{\"texture\":\"#side\"{rot}}}";
            }
            string Elem(int[] f, int[] t, char axis)
            {
                string faces = string.Join(",", new[] {
                    FaceJson("north", 'z', 'y', axis), FaceJson("south", 'z', 'y', axis),
                    FaceJson("east",  'x', 'y', axis), FaceJson("west",  'x', 'y', axis),
                    FaceJson("up",    'y', 'z', axis), FaceJson("down",  'y', 'z', axis) });
                return $"{{\"from\":[{f[0]},{f[1]},{f[2]}],\"to\":[{t[0]},{t[1]},{t[2]}],\"faces\":{{{faces}}}}}";
            }
            string Tex = $"\"particle\":\"{MODID}:block/raw_quartz_column\",\"side\":\"{MODID}:block/raw_quartz_column\",\"end\":\"{MODID}:block/raw_quartz_column_top\"";
            string Model(string elems) => $"{{\"textures\":{{{Tex}}},\"elements\":[{elems}]}}";

            // Render the connection union as eight DISJOINT octants (8px cubes), NOT overlapping half-slabs: two
            // half-slabs share a coplanar exterior region (e.g. the corner of a stair's side face) → coincident faces →
            // z-fighting. Octants never coincide, so no z-fighting; the volume is identical to the half-slab union (which
            // the collision VoxelShape in QuartzClusterBlock still uses). An octant fills when ANY of the three connected
            // sides adjacent to its corner is connected. The #end cap / #side grain per face still follows AXIS (Elem).
            var axes = new[] { "x", "y", "z" };
            var octs = new List<(string id, int[] f, int[] t, string[] dirs)>();
            foreach (int ox in new[]{0,1}) foreach (int oy in new[]{0,1}) foreach (int oz in new[]{0,1})
                octs.Add(($"{ox}{oy}{oz}",
                    new[]{ ox*8, oy*8, oz*8 }, new[]{ ox*8+8, oy*8+8, oz*8+8 },
                    new[]{ ox==0?"west":"east", oy==0?"down":"up", oz==0?"north":"south" }));
            foreach (var o in octs)
                foreach (var ax in axes)
                    File.WriteAllText(Path.Combine(qMd, $"quartz_cluster_oct_{o.id}_{ax}.json"), Model(Elem(o.f, o.t, ax[0])));

            // Inventory/creative icon: a representative stair as two NON-overlapping boxes (bottom slab + back-top step).
            string icon = Elem(new[]{0,0,0}, new[]{16,8,16}, 'y') + "," + Elem(new[]{0,8,0}, new[]{16,16,8}, 'y');
            File.WriteAllText(Path.Combine(qMd, "quartz_cluster_inventory.json"),
                $"{{\"parent\":\"minecraft:block/block\",\"textures\":{{{Tex}}},\"elements\":[{icon}]}}");
            File.WriteAllText(Path.Combine(qIt, "quartz_cluster.json"),
                $"{{\"parent\":\"{MODID}:block/quartz_cluster_inventory\"}}");

            // Multipart: each octant fires when any of its three adjacent sides connects (OR) AND the grain axis matches;
            // plus a full-block case when NOTHING connects (all six false), per axis, rendered as the original quartz
            // pillar (raw_quartz_column) oriented like that block's own blockstate.
            var octParts = octs.SelectMany(o => axes.Select(ax =>
                "{\"when\":{\"AND\":[{\"axis\":\"" + ax + "\"},{\"OR\":["
                + string.Join(",", o.dirs.Select(d => "{\"" + d + "\":\"true\"}"))
                + "]}]},\"apply\":{\"model\":\"" + MODID + ":block/quartz_cluster_oct_" + o.id + "_" + ax + "\"}}"));
            string allFalse = "\"north\":\"false\",\"east\":\"false\",\"south\":\"false\",\"west\":\"false\",\"up\":\"false\",\"down\":\"false\"";
            var fullParts = new[] {
                "{\"when\":{" + allFalse + ",\"axis\":\"y\"},\"apply\":{\"model\":\"" + MODID + ":block/raw_quartz_column\"}}",
                "{\"when\":{" + allFalse + ",\"axis\":\"z\"},\"apply\":{\"model\":\"" + MODID + ":block/raw_quartz_column_horizontal\",\"x\":90}}",
                "{\"when\":{" + allFalse + ",\"axis\":\"x\"},\"apply\":{\"model\":\"" + MODID + ":block/raw_quartz_column_horizontal\",\"x\":90,\"y\":90}}",
            };
            File.WriteAllText(Path.Combine(qBs, "quartz_cluster.json"),
                "{\"multipart\":[" + string.Join(",", octParts.Concat(fullParts)) + "]}");

            // Loot matches the raw quartz column's TFC raw-rock behaviour: isolated (no support) → drop a raw quartz
            // column (the full pillar an isolated cluster visually becomes); else mined with a pickaxe → 1-4 nether
            // quartz (same as the column; requiresCorrectToolForDrops gates the quartz).
            File.WriteAllText(Path.Combine(qLoot, "quartz_cluster.json"),
                """{"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:alternatives","children":[{"type":"minecraft:item","name":"MODID:raw_quartz_column","conditions":[{"condition":"tfc:is_isolated"}]},{"type":"minecraft:item","name":"minecraft:quartz","functions":[{"function":"minecraft:set_count","count":{"min":1,"max":4,"type":"minecraft:uniform"}}]}]}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}""".Replace("MODID", MODID));
            // What a cluster grows arms toward (besides any solid sturdy face): itself + the raw quartz column.
            WriteTag(MODID, "blocks", "quartz_cluster_connectable",
                """{"replace":false,"values":["MODID:quartz_cluster","MODID:raw_quartz_column"]}""".Replace("MODID", MODID));
        }

        // Worldgen: the firmavanilla:quartz_cluster cave-decoration feature (see QuartzClusterFeature.java) — grows the
        // self-shaping firmavanilla:quartz_cluster connected block (NOT the full raw_quartz_column) into veins in
        // ALREADY-carved caves (tfc:carving_mask step=air; block-only, never carves rock). GATED TO VOLCANOES via
        // tfc:volcano (x,z-only, so it works underground) — quartz caves only generate within a TFC volcano footprint
        // (the volcanic biomes), a rare/regional, lore-fitting spot whose felsic extrusive rock (rhyolite/dacite) is in
        // the host tag. Also gated to quartz-bearing rock (the firmavanilla:quartz_cluster_host block tag, checked in
        // the feature) and to deeper caves (carving_mask max_y). Injected into TFC's universal underground_decoration
        // biome tag (alongside cave_column/calcite/icicle); the volcano modifier does the concentrating.
        string qCf = Path.Combine(resRoot, "data", MODID, "worldgen", "configured_feature");
        string qPf = Path.Combine(resRoot, "data", MODID, "worldgen", "placed_feature");
        Directory.CreateDirectory(qCf); Directory.CreateDirectory(qPf);
        File.WriteAllText(Path.Combine(qCf, "quartz_cluster.json"),
            """{"type":"MODID:quartz_cluster","config":{"crystal":"MODID:quartz_cluster","host":"MODID:quartz_cluster_host","max_reach":10}}""".Replace("MODID", MODID));
        File.WriteAllText(Path.Combine(qPf, "quartz_cluster.json"),
            """{"feature":"MODID:quartz_cluster","placement":[{"type":"tfc:carving_mask","step":"air","min_y":{"above_bottom":8},"max_y":{"absolute":48}},{"type":"minecraft:rarity_filter","chance":10},{"type":"tfc:volcano"}]}""".Replace("MODID", MODID));
        // Strata gate: the quartz-bearing rocks, raw + hardened (cave walls are raw rock; hardened appears near volcanoes/deep).
        var qHostRocks = new[] { "quartzite", "rhyolite", "granite", "dacite", "gneiss", "chert" };
        var qHostVals = qHostRocks.SelectMany(r => new[] { $"\"tfc:rock/raw/{r}\"", $"\"tfc:rock/hardened/{r}\"" });
        WriteTag(MODID, "blocks", "quartz_cluster_host", "{\"replace\":false,\"values\":[" + string.Join(",", qHostVals) + "]}");
        // Inject into TFC's universal underground cave-decoration step (merges; runs in every TFC biome's caves).
        WriteTag("tfc", "worldgen/placed_feature", "in_biome/underground_decoration", """{"replace":false,"values":["MODID:quartz_cluster"]}""".Replace("MODID", MODID));

        Console.WriteLine("  raw quartz column (creative-only) + quartz_cluster connected cave block (multipart) + quartz_cluster cave feature (worldgen)");
    }

    // --- quartz brick item + the vanilla-quartz chisel/mortar chain ---
    // A new firmavanilla:quartz_brick item (the vanilla brick icon recoloured through the vanilla quartz item's palette
    // via CLUT) makes the whole vanilla quartz block family reachable under TFC, by chisel + mortar:
    //   minecraft:quartz --chisel--> firmavanilla:quartz_brick --(x4 + mortar)--> minecraft:quartz_bricks
    //   minecraft:quartz_bricks --chisel--> minecraft:chiseled_quartz_block
    //   firmavanilla:raw_quartz_column --chisel--> minecraft:smooth_quartz --chisel--> minecraft:quartz_block --chisel--> minecraft:quartz_pillar
    // Every block->block step ships BOTH a TFC in-world chisel (smooth mode) and a table craft with a chisel
    // (tfc:chisels, tool damaged not consumed), like the rest of firmavanilla. Inputs: input/vanilla/brick.png +
    // input/vanilla/quartz.png (the vanilla item icons). See QuartzBlocks.QUARTZ_BRICK.
    static void BrickChain()
    {
        string itTex = Path.Combine(resRoot, "assets", MODID, "textures", "item");
        string itMd = Path.Combine(resRoot, "assets", MODID, "models", "item");
        string recDir = Path.Combine(resRoot, "data", MODID, "recipes");
        string chiselSmooth = Path.Combine(recDir, "chisel", "smooth");
        foreach (var d in new[] { itTex, itMd, recDir, chiselSmooth }) Directory.CreateDirectory(d);

        // Texture: vanilla brick icon recoloured through the vanilla quartz item palette (CLUT). Optional-until-staged:
        // if the two vanilla item icons aren't in input/vanilla yet, skip just the texture (model + recipes still ship,
        // so the chain is testable; the item shows a missing texture until the inputs are added and the tool re-run).
        File.WriteAllText(Path.Combine(itMd, "quartz_brick.json"), GeneratedItem($"{MODID}:item/quartz_brick"));
        try
        {
            using var brick = Load("vanilla", "brick.png");
            using var quartz = Load("vanilla", "quartz.png");
            using var qb = ClutSide(brick, quartz, brick.Width, brick.Height);
            qb.Save(Path.Combine(itTex, "quartz_brick.png"));
        }
        catch (FileNotFoundException e)
        {
            Console.WriteLine("  [skip] quartz_brick texture — " + e.Message);
        }

        // chisel nether quartz -> quartz brick (table craft with a chisel).
        File.WriteAllText(Path.Combine(recDir, "quartz_brick.json"),
            ToolCraft("minecraft:quartz", "tfc:chisels", $"{MODID}:quartz_brick"));
        // quartz bricks + mortar -> vanilla quartz_bricks block, matching TFC's own brick recipe exactly: the
        // XYX/YXY/XYX checkerboard (5 bricks + 4 mortar) yielding 2 blocks (X = brick, Y = tfc:mortar tag).
        File.WriteAllText(Path.Combine(recDir, "quartz_bricks.json"),
            """{"type":"minecraft:crafting_shaped","pattern":["XYX","YXY","XYX"],"key":{"X":{"item":"MODID:quartz_brick"},"Y":{"tag":"tfc:mortar"}},"result":{"item":"minecraft:quartz_bricks","count":2}}""".Replace("MODID", MODID));

        // Block -> block chisel chain: both the in-world tfc:chisel (smooth) and the table craft with a chisel.
        var chisels = new (string from, string to, string file)[] {
            ("minecraft:quartz_bricks",    "minecraft:chiseled_quartz_block", "chiseled_quartz_block"),
            ($"{MODID}:raw_quartz_column", "minecraft:smooth_quartz",          "smooth_quartz"),
            ("minecraft:smooth_quartz",    "minecraft:quartz_block",           "quartz_block"),
            ("minecraft:quartz_block",     "minecraft:quartz_pillar",          "quartz_pillar"),
        };
        foreach (var (from, to, file) in chisels)
        {
            File.WriteAllText(Path.Combine(recDir, file + ".json"), ToolCraft(from, "tfc:chisels", to));
            File.WriteAllText(Path.Combine(chiselSmooth, file + ".json"), ChiselRecipe(from, to, "smooth"));
        }

        // Disable EVERY vanilla recipe that produces one of the quartz blocks in our chain, so the TFC chisel/mortar
        // route is the only way to get them. We override each at its data/minecraft/recipes path with a forge:false
        // copy (Forge checks conditions BEFORE parsing the recipe, so it's skipped/removed). Quartz slabs & stairs are
        // intentionally left enabled — they still derive from the now-chain-gated quartz_block, so nothing is orphaned.
        string mcRec = Path.Combine(resRoot, "data", "minecraft", "recipes");
        Directory.CreateDirectory(mcRec);
        string[] disabledVanilla = {
            "quartz_block", "smooth_quartz",
            "quartz_pillar", "quartz_pillar_from_quartz_block_stonecutting",
            "chiseled_quartz_block", "chiseled_quartz_block_from_quartz_block_stonecutting",
            "quartz_bricks", "quartz_bricks_from_quartz_block_stonecutting",
        };
        foreach (var name in disabledVanilla)
            File.WriteAllText(Path.Combine(mcRec, name + ".json"),
                """{"type":"minecraft:crafting_shapeless","conditions":[{"type":"forge:false"}],"ingredients":[{"item":"minecraft:quartz"}],"result":{"item":"minecraft:quartz"}}""");

        Console.WriteLine($"  quartz brick item (CLUT brick→quartz) + vanilla-quartz chisel/mortar chain (10 recipes) + {disabledVanilla.Length} vanilla quartz recipes disabled");
    }
}
