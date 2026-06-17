// Soul lamps — a vanilla-soul-lantern flavour for TFC's metal lamps. Visuals only here: TFC's lit/unlit lamp
// GLASS recoloured toward soul-fire teal via CLUT (the #lamp texture only — the metal frame stays each metal's
// own), reusing TFC's lamp geometry; one shared glass pair serves all 9 metals. Recipes/heating mirror TFC's
// lamps exactly. The behaviour (reuse TFC's lamp block-entity, light 10, convert/burn-out) is code-side in
// SoulLamps.java.

using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;
using static Gen;

static class SoulLamps
{
    public static void Generate()
    {
        int soulLamps = 0;
        string slTexDir = Path.Combine(resRoot, "assets", MODID, "textures", "block");
        Directory.CreateDirectory(slTexDir);
        // Soul glass = TFC's lamp glass recoloured through vanilla SOUL FIRE's palette via CLUT (luminance→colour),
        // so the teal carries soul-fire's real tones rather than a flat hue shift. The lit glass keeps its 3-frame
        // animation. The off glass is the same recolour, then darkened — CLUT normalises each input's own luma range
        // onto the palette, which would otherwise make the unlit glass as bright as the lit one.
        const float SOUL_OFF_DIM = 0.45f;
        using (var soulFire = Load("vanilla", "soul_fire_0.png"))
        {
            using (var lit = Load("tfc", "lamp.png"))
            using (var soulLit = ClutSide(lit, soulFire, lit.Width, lit.Height))
                soulLit.Save(Path.Combine(slTexDir, "soul_lamp.png"));
            using (var off = Load("tfc", "lamp_off.png"))
            using (var soulOff = ClutSide(off, soulFire, off.Width, off.Height))
            {
                soulOff.Mutate(c => c.Brightness(SOUL_OFF_DIM)); // unlit stays clearly dimmer than lit
                soulOff.Save(Path.Combine(slTexDir, "soul_lamp_off.png"));
            }
        }
        File.Copy(Path.Combine(scriptDir, "input", "tfc", "lamp.png.mcmeta"), Path.Combine(slTexDir, "soul_lamp.png.mcmeta"), overwrite: true);

        string[] metals = { "copper", "bronze", "bismuth_bronze", "black_bronze", "wrought_iron", "steel", "black_steel", "blue_steel", "red_steel" };
        // Asset paths follow the BLOCK ID (firmavanilla:soul_lamp/<metal>) — no "metal/" prefix (unlike TFC, whose
        // lamp id is metal/lamp/<metal>). Blockstate/item/loot resolve from the registry path, so they must match.
        string slBs = Path.Combine(resRoot, "assets", MODID, "blockstates", "soul_lamp");
        string slMd = Path.Combine(resRoot, "assets", MODID, "models", "block", "soul_lamp");
        string slIt = Path.Combine(resRoot, "assets", MODID, "models", "item", "soul_lamp");
        string slLoot = Path.Combine(resRoot, "data", MODID, "loot_tables", "blocks", "soul_lamp");
        string slRec = Path.Combine(resRoot, "data", MODID, "recipes", "soul_lamp");
        string slHeat = Path.Combine(resRoot, "data", MODID, "recipes", "heating", "soul_lamp");
        string slItemHeat = Path.Combine(resRoot, "data", MODID, "tfc", "item_heats", "soul_lamp");
        string slItemTex = Path.Combine(resRoot, "assets", MODID, "textures", "item", "soul_lamp");
        foreach (var d in new[] { slBs, slMd, slIt, slLoot, slRec, slHeat, slItemHeat, slItemTex }) Directory.CreateDirectory(d);
        // Item icon = TFC's lamp item texture with the hand-extracted soul-glass overlay composited over it (one
        // 16x16 overlay, tracked in the tool root, serves every metal; the metal body keeps each metal's colour).
        using var soulItemOverlay = Image.Load<Rgba32>(Path.Combine(scriptDir, "soul_lantern_item_overlay.png"));
        // Melting mirrors TFC's lamp heating recipes exactly (result fluid + temperature per metal; amount 100).
        // Note wrought_iron melts to cast_iron, like TFC.
        var lampMelt = new Dictionary<string, (string fluid, int temp)>
        {
            ["copper"] = ("tfc:metal/copper", 1080), ["bronze"] = ("tfc:metal/bronze", 950),
            ["bismuth_bronze"] = ("tfc:metal/bismuth_bronze", 985), ["black_bronze"] = ("tfc:metal/black_bronze", 1070),
            ["wrought_iron"] = ("tfc:metal/cast_iron", 1535), ["steel"] = ("tfc:metal/steel", 1540),
            ["black_steel"] = ("tfc:metal/black_steel", 1485), ["blue_steel"] = ("tfc:metal/blue_steel", 1540),
            ["red_steel"] = ("tfc:metal/red_steel", 1540),
        };
        // item_heat (forging/welding temps per metal; heat_capacity 2.857 for all, = 100 mB) — REQUIRED so the lamp
        // can gain temperature and the melting recipe can actually fire. Mirrors TFC's lamp item_heats.
        var lampHeat = new Dictionary<string, (int forge, int weld)>
        {
            ["copper"] = (648, 864), ["bronze"] = (570, 760), ["bismuth_bronze"] = (591, 788),
            ["black_bronze"] = (642, 856), ["wrought_iron"] = (921, 1228), ["steel"] = (924, 1232),
            ["black_steel"] = (891, 1188), ["blue_steel"] = (924, 1232), ["red_steel"] = (924, 1232),
        };

        foreach (var metal in metals)
        {
            string SL(string s) => s.Replace("MODID", MODID).Replace("METAL", metal);
            File.WriteAllText(Path.Combine(slBs, metal + ".json"), SL(
                """{"variants":{"hanging=false,lit=false":{"model":"MODID:block/soul_lamp/METAL_off"},"hanging=true,lit=false":{"model":"MODID:block/soul_lamp/METAL_hanging_off"},"hanging=false,lit=true":{"model":"MODID:block/soul_lamp/METAL_on"},"hanging=true,lit=true":{"model":"MODID:block/soul_lamp/METAL_hanging_on"}}}"""));
            File.WriteAllText(Path.Combine(slMd, metal + "_off.json"), SL(
                """{"parent":"tfc:block/lamp","textures":{"metal":"tfc:block/metal/smooth/METAL","lamp":"MODID:block/soul_lamp_off"}}"""));
            File.WriteAllText(Path.Combine(slMd, metal + "_on.json"), SL(
                """{"parent":"tfc:block/lamp","textures":{"metal":"tfc:block/metal/smooth/METAL","lamp":"MODID:block/soul_lamp"}}"""));
            File.WriteAllText(Path.Combine(slMd, metal + "_hanging_off.json"), SL(
                """{"parent":"tfc:block/lamp_hanging","textures":{"metal":"tfc:block/metal/smooth/METAL","chain":"tfc:block/metal/chain/METAL","lamp":"MODID:block/soul_lamp_off"}}"""));
            File.WriteAllText(Path.Combine(slMd, metal + "_hanging_on.json"), SL(
                """{"parent":"tfc:block/lamp_hanging","textures":{"metal":"tfc:block/metal/smooth/METAL","chain":"tfc:block/metal/chain/METAL","lamp":"MODID:block/soul_lamp"}}"""));
            // Item icon: TFC's lamp item texture with the soul-glass overlay composited over it (metal kept). Build a
            // NEW image (the loaded-image indexer setter doesn't reliably survive Save in this ImageSharp build — the
            // generator's CLUT path always emits a fresh image too). Manual source-over (NOT Mutate.DrawImage, which
            // blended additively, blowing the teal to white over warm metals); the opaque overlay replaces the glass.
            using (var baseItem = Load("tfc", Path.Combine("lamp_item", metal + ".png")))
            using (var outItem = new Image<Rgba32>(baseItem.Width, baseItem.Height))
            {
                for (int y = 0; y < baseItem.Height; y++)
                for (int x = 0; x < baseItem.Width; x++)
                {
                    Rgba32 dst = baseItem[x, y];
                    Rgba32 ov = soulItemOverlay[x % soulItemOverlay.Width, y % soulItemOverlay.Height];
                    if (ov.A == 0) { outItem[x, y] = dst; continue; } // keep the lamp's metal
                    float a = ov.A / 255f;
                    outItem[x, y] = new Rgba32(
                        (byte) (ov.R * a + dst.R * (1 - a)),
                        (byte) (ov.G * a + dst.G * (1 - a)),
                        (byte) (ov.B * a + dst.B * (1 - a)),
                        (byte) Math.Min(255, ov.A + dst.A * (1 - a)));
                }
                outItem.Save(Path.Combine(slItemTex, metal + ".png"));
            }
            File.WriteAllText(Path.Combine(slIt, metal + ".json"), SL("""{"parent":"item/generated","textures":{"layer0":"MODID:item/soul_lamp/METAL"}}"""));
            // loot: drop self + tfc:copy_fluid (keep fuel on break, exactly like TFC lamps).
            File.WriteAllText(Path.Combine(slLoot, metal + ".json"), SL(
                """{"type":"minecraft:block","pools":[{"name":"loot_pool","rolls":1,"entries":[{"type":"minecraft:item","name":"MODID:soul_lamp/METAL","functions":[{"function":"tfc:copy_fluid"}]}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]}"""));
            // craft: normal lamp + a catalyst-tag item -> soul lamp (empty; right-click is the fuel-preserving path).
            File.WriteAllText(Path.Combine(slRec, metal + ".json"), SL(
                """{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"tfc:metal/lamp/METAL"},{"tag":"MODID:soul_lamp_catalyst"}],"result":{"item":"MODID:soul_lamp/METAL"}}"""));
            // melt: same fluid/temperature as the TFC lamp (amount 100).
            File.WriteAllText(Path.Combine(slHeat, metal + ".json"),
                HeatingMelt($"{MODID}:soul_lamp/{metal}", lampMelt[metal].fluid, 100, lampMelt[metal].temp));
            // item_heat: lets the lamp heat up (else melting can never trigger). Mirrors TFC's lamp item_heat.
            File.WriteAllText(Path.Combine(slItemHeat, metal + ".json"),
                SL("""{"ingredient":{"item":"MODID:soul_lamp/METAL"},"heat_capacity":2.857,"forging_temperature":FORGE,"welding_temperature":WELD}""")
                    .Replace("FORGE", lampHeat[metal].forge.ToString()).Replace("WELD", lampHeat[metal].weld.ToString()));
            soulLamps++;
        }
        // Catalyst item tag (datapack-overridable): seed with TFC sulfur + native-copper powders.
        // (soul lamps are added to minecraft:mineable/pickaxe in MineableTag().)
        WriteTag("firmavanilla", "items", "soul_lamp_catalyst", """{"replace":false,"values":["tfc:powder/sulfur","tfc:powder/native_copper"]}""");
        // CRITICAL for lighting/fuelling: a TFC LampFuel only works in lamps listed by its `valid_lamps`. olive_oil +
        // tallow use `#tfc:lamps`, so add our soul lamps to that block tag (else getFuel()==null → can't be lit).
        WriteTag("tfc", "blocks", "lamps",
            "{\"replace\":false,\"values\":[" + string.Join(",", metals.Select(m => "\"" + MODID + ":soul_lamp/" + m + "\"")) + "]}");
        // Lava is restricted to blue_steel in TFC (explicit, not the tag); mirror that for the soul blue_steel lamp
        // via our own LampFuel entry (TFC's DataManager merges across namespaces).
        string lampFuelDir = Path.Combine(resRoot, "data", MODID, "tfc", "lamp_fuels");
        Directory.CreateDirectory(lampFuelDir);
        File.WriteAllText(Path.Combine(lampFuelDir, "soul_lava.json"),
            """{"fluid":"minecraft:lava","burn_rate":-1,"valid_lamps":"MODID:soul_lamp/blue_steel"}""".Replace("MODID", MODID));
        Console.WriteLine($"  soul lamps: {soulLamps} metals (glass = lamp CLUT'd through soul_fire) + blockstates/models/loot/recipes + heating + tfc:lamps + lava fuel");
    }
}
