// Prismarine gravel deposits — a TFC-style pan/sluice source for the vanilla prismarine family (which TFC
// leaves intact but never generates the shards/crystals for). One deposit block per TFC rock, mirroring TFC's
// native_copper deposits: a tfc:soil_disc swaps deep-ocean floor gravel for the matching deposit; the player
// mines it then PANS or SLUICES it for shards (common) + crystals (rare). The crystal sheen is ONE hand-made
// animated overlay (prismarine_overlay.png in the tool root) layered over each rock's gravel via the inlined
// tfc:block/ore model. Pure datapack worldgen — no mixin. See PrismarineDeposits.java.

using static Gen;

static class PrismarineDeposits
{
    public static int Generate()
    {
        int prismarine = 0;
        string A(params string[] p) => Path.Combine(new[] { resRoot, "assets", MODID }.Concat(p).ToArray());
        string D(params string[] p) => Path.Combine(new[] { resRoot, "data", MODID }.Concat(p).ToArray());
        string texP = A("textures", "block", "deposit");
        string bsP = A("blockstates", "deposit", "prismarine"), mdP = A("models", "block", "deposit", "prismarine");
        string itP = A("models", "item", "deposit", "prismarine"), panP = A("models", "item", "pan", "prismarine");
        string lootBlk = D("loot_tables", "blocks", "deposit", "prismarine"), lootPan = D("loot_tables", "panning", "deposits");
        string panDef = D("tfc", "panning", "deposits"), sluDef = D("tfc", "sluicing", "deposits");
        string cfDir = D("worldgen", "configured_feature"), pfDir = D("worldgen", "placed_feature");
        string landslideDir = D("recipes", "landslide");
        foreach (var d in new[] { texP, bsP, mdP, itP, panP, lootBlk, lootPan, panDef, sluDef, cfDir, pfDir, landslideDir }) Directory.CreateDirectory(d);

        // Copy the hand-made animated crystal overlay (tracked input, tool root) to the single deposit texture, and
        // emit its animation .mcmeta (4-frame strip; mirrors vanilla prismarine's frame timing/sequence).
        File.Copy(Path.Combine(scriptDir, "prismarine_overlay.png"), Path.Combine(texP, "prismarine.png"), overwrite: true);
        File.WriteAllText(Path.Combine(texP, "prismarine.png.mcmeta"),
            """{"animation":{"frametime":300,"interpolate":true,"frames":[0,1,0,2,0,3,0,1,2,1,3,1,0,2,1,2,3,2,0,3,1,3]}}""");

        // The "result" pan stage is shared across rocks (it shows the prismarine you washed out, not the gravel).
        File.WriteAllText(Path.Combine(panP, "result.json"),
            """{"parent":"tfc:item/pan/result","textures":{"material":"minecraft:block/prismarine"}}""");

        var depositIds = new List<string>();
        var stateEntries = new List<string>();
        foreach (var rock in ROCKS)
        {
            depositIds.Add($"{MODID}:deposit/prismarine/{rock}");
            File.WriteAllText(Path.Combine(bsP, rock + ".json"),
                """{"variants":{"":{"model":"MODID:block/deposit/prismarine/ROCK"}}}""".Replace("MODID", MODID).Replace("ROCK", rock));
            // Inlined twin of tfc:block/ore (gravel cube + coplanar overlay cube) so we can make ONLY the overlay
            // element render fullbright — Forge per-element `forge_data` block_light/sky_light = 15 makes the crystals
            // glow in the dark with NO actual light emission and no on/off state (unlike a lightLevel). render_type
            // cutout_mipped is REQUIRED: TFC sets its deposit render layer in code (which we can't reuse), and the
            // default solid layer ignores the overlay's alpha (transparent pixels would render opaque black, hiding
            // the gravel). (The item render path respects alpha, so only the in-world block needs all this.)
            File.WriteAllText(Path.Combine(mdP, rock + ".json"), DepositModel().Replace("MODID", MODID).Replace("ROCK", rock));
            File.WriteAllText(Path.Combine(itP, rock + ".json"), ParentItem($"deposit/prismarine/{rock}"));
            // Pan animation stages (full/half show the rock's gravel being washed); reuse TFC's pan parent models.
            File.WriteAllText(Path.Combine(panP, rock + "_full.json"),
                """{"parent":"tfc:item/pan/full","textures":{"material":"tfc:block/rock/gravel/ROCK"}}""".Replace("ROCK", rock));
            File.WriteAllText(Path.Combine(panP, rock + "_half.json"),
                """{"parent":"tfc:item/pan/half","textures":{"material":"tfc:block/rock/gravel/ROCK"}}""".Replace("ROCK", rock));
            // Block-break loot: drop the deposit itself (carry it to a pan/sluice), like TFC.
            File.WriteAllText(Path.Combine(lootBlk, rock + ".json"), DropItemLoot($"{MODID}:deposit/prismarine/{rock}"));
            // Landslide recipe: the can_landslide tag only enqueues the check — without a tfc:landslide recipe TFC's
            // tryLandslide no-ops, so the deposit never collapses like the TFC gravel it mimics. Collapse into itself.
            File.WriteAllText(Path.Combine(landslideDir, "deposit_prismarine_" + rock + ".json"),
                """{"type":"tfc:landslide","ingredient":"MODID:deposit/prismarine/ROCK","result":"MODID:deposit/prismarine/ROCK"}""".Replace("MODID", MODID).Replace("ROCK", rock));
            // Panning loot (fishing type): one item per wash — crystals (rare), shard (common), loose-rock filler.
            File.WriteAllText(Path.Combine(lootPan, "prismarine_" + rock + ".json"),
                """{"type":"minecraft:fishing","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:alternatives","children":[{"type":"minecraft:item","name":"minecraft:prismarine_crystals","conditions":[{"condition":"minecraft:random_chance","chance":0.2}]},{"type":"minecraft:item","name":"minecraft:prismarine_shard","conditions":[{"condition":"minecraft:random_chance","chance":0.6}]},{"type":"minecraft:item","name":"tfc:rock/loose/ROCK","conditions":[{"condition":"minecraft:random_chance","chance":0.4}]},{"type":"minecraft:item","name":"tfc:rock/loose/ROCK","conditions":[{"condition":"minecraft:random_chance","chance":0.2}]}]}]}]}""".Replace("ROCK", rock));
            // TFC panning + sluicing definitions (both reference the panning loot table, exactly as TFC does).
            File.WriteAllText(Path.Combine(panDef, "prismarine_" + rock + ".json"),
                """{"ingredient":"MODID:deposit/prismarine/ROCK","model_stages":["MODID:item/pan/prismarine/ROCK_full","MODID:item/pan/prismarine/ROCK_half","MODID:item/pan/prismarine/result"],"loot_table":"MODID:panning/deposits/prismarine_ROCK"}""".Replace("MODID", MODID).Replace("ROCK", rock));
            File.WriteAllText(Path.Combine(sluDef, "prismarine_" + rock + ".json"),
                """{"ingredient":{"item":"MODID:deposit/prismarine/ROCK"},"loot_table":"MODID:panning/deposits/prismarine_ROCK"}""".Replace("MODID", MODID).Replace("ROCK", rock));
            stateEntries.Add("""{"replace":"tfc:rock/gravel/ROCK","with":"MODID:deposit/prismarine/ROCK"}""".Replace("MODID", MODID).Replace("ROCK", rock));
            prismarine++;
        }

        // Worldgen: a tfc:soil_disc swapping each rock's gravel for its deposit, placed on the deep-ocean floor.
        File.WriteAllText(Path.Combine(cfDir, "prismarine_deposit.json"),
            """{"type":"tfc:soil_disc","config":{"min_radius":3,"max_radius":9,"height":6,"integrity":0.9,"states":[STATES]}}""".Replace("STATES", string.Join(",", stateEntries)));
        File.WriteAllText(Path.Combine(pfDir, "prismarine_deposit.json"),
            """{"feature":"MODID:prismarine_deposit","placement":[{"type":"minecraft:rarity_filter","chance":48},{"type":"minecraft:in_square"},{"type":"minecraft:heightmap","heightmap":"OCEAN_FLOOR_WG"},{"type":"tfc:biome"}]}""".Replace("MODID", MODID));
        // Append to TFC's (empty) deep-ocean soil-disc decoration tags — its chunk generator runs whatever's in them.
        foreach (var biome in new[] { "deep_ocean", "deep_ocean_trench" })
            WriteTag("tfc", "worldgen/placed_feature", "in_biome/soil_discs/" + biome,
                """{"replace":false,"values":["MODID:prismarine_deposit"]}""".Replace("MODID", MODID));
        // Match every tag TFC's own deposits carry, so prismarine deposits behave like normal TFC gravel:
        // forge:gravel (block+item), minecraft:mineable/shovel, tfc:can_landslide (collapse/landslide like gravel),
        // and tfc:ore_deposits (block+item, TFC's deposit classification). All `replace:false` so they merge.
        string depTagBody = "{\"replace\":false,\"values\":[" + string.Join(",", depositIds.Select(id => "\"" + id + "\"")) + "]}";
        WriteTag("forge", "blocks", "gravel", depTagBody);
        WriteTag("forge", "items", "gravel", depTagBody);
        WriteTag("tfc", "blocks", "ore_deposits", depTagBody);
        WriteTag("tfc", "items", "ore_deposits", depTagBody);
        shovelMineable.AddRange(depositIds);  // minecraft:mineable/shovel + tfc:can_landslide written once at the end
        canLandslide.AddRange(depositIds);

        Console.WriteLine($"  prismarine deposits: {prismarine} blocks (per rock) + worldgen + panning/sluicing + loot");
        return prismarine;
    }

    // Prismarine deposit block model — inlined twin of tfc:block/ore (a gravel cube + a coplanar overlay cube), so the
    // overlay element alone can carry Forge `forge_data` block_light/sky_light = 15 (the crystals render fullbright /
    // "glow", with no real light emission and no on/off state). Plain raw string; caller replaces ROCK + MODID.
    static string DepositModel() =>
        """
        {"parent":"block/block","render_type":"minecraft:cutout_mipped","textures":{"all":"tfc:block/rock/gravel/ROCK","overlay":"MODID:block/deposit/prismarine","particle":"#all"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"north":{"uv":[0,0,16,16],"texture":"#all","cullface":"north"},"east":{"uv":[0,0,16,16],"texture":"#all","cullface":"east"},"south":{"uv":[0,0,16,16],"texture":"#all","cullface":"south"},"west":{"uv":[0,0,16,16],"texture":"#all","cullface":"west"},"up":{"uv":[0,0,16,16],"texture":"#all","cullface":"up"},"down":{"uv":[0,0,16,16],"texture":"#all","cullface":"down"}}},{"from":[0,0,0],"to":[16,16,16],"forge_data":{"block_light":15,"sky_light":15},"faces":{"north":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"north"},"east":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"east"},"south":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"south"},"west":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"west"},"up":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"up"},"down":{"uv":[0,0,16,16],"texture":"#overlay","cullface":"down"}}}]}
        """;
}
