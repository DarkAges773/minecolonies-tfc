package com.mctfc.smelter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The data model for the TFC-flavoured MineColonies <b>Smelter</b> (see docs/compat-features.md). MineColonies'
 * vanilla smelter turns one ore into one ingot in a furnace; TFC has no such recipe — ore is <i>melted</i> into
 * liquid metal (mB) and that metal is cast (in a mold) into ingots, while iron instead becomes a bloomery
 * <i>bloom</i> that the player hammers on an anvil. We collapse that pipeline into a single worker action:
 * accumulate {@value #UNITS_PER_OUTPUT} mB of one metal from ore (any grades) and emit one output.
 *
 * <p>This class is <b>pure data/query</b> — no world or AI state. The smelter AI mixin reads it to decide what
 * counts as ore, how much metal a given ore is worth, and what (and how) to produce.
 *
 * <ul>
 *   <li><b>mB per ore</b> depends only on the grade prefix — uniform across every metal in TFC
 *       ({@code small} 10 / {@code poor} 15 / {@code normal} 25 / {@code rich} 35), and {@value #UNITS_PER_OUTPUT}
 *       mB makes one output. So e.g. 4 normal or 3 rich copper ore → one copper ingot.</li>
 *   <li><b>Metal ores</b> ({@link Output#bloom()} == false) cast into an ingot and consume one {@link #MOLDS mold}
 *       (which may break).</li>
 *   <li><b>Iron ores</b> (hematite/magnetite/limonite, {@link Output#bloom()} == true) melt to cast iron and,
 *       via the bloomery path, yield a {@code tfc:raw_iron_bloom} consuming {@value #CHARCOAL_PER_BLOOM} charcoal
 *       (no mold — blooms aren't cast). The player finishes the bloom into wrought iron on an anvil, exactly as
 *       in TFC.</li>
 * </ul>
 */
public final class SmelterRecipes
{
    private SmelterRecipes() {}

    /** Milli-buckets of metal that make one output (one ingot, or one bloom's worth of cast iron). */
    public static final int UNITS_PER_OUTPUT = 100;

    /** Charcoal consumed per iron bloom (the TFC bloomery catalyst cost, scaled to one bloom). */
    public static final int CHARCOAL_PER_BLOOM = 2;

    /** Charcoal item the iron path consumes. */
    public static final ResourceLocation CHARCOAL = new ResourceLocation("minecraft", "charcoal");

    /**
     * The two acceptable casting molds, in preference order, each with its TFC casting break chance. The smelter
     * uses whichever it has; fire-clay lasts ~10× longer.
     */
    public static final List<Mold> MOLDS = List.of(
            new Mold(new ResourceLocation("tfc", "ceramic/fire_ingot_mold"), 0.01f),
            new Mold(new ResourceLocation("tfc", "ceramic/ingot_mold"), 0.10f));

    /** A casting mold and the chance it breaks after a cast. */
    public record Mold(ResourceLocation item, float breakChance) {}

    /** What an ore smelts toward: a result item, and whether it's the iron-bloom path (vs. a cast ingot). */
    public record Output(ResourceLocation result, boolean bloom) {}

    private static final ResourceLocation BLOOM = new ResourceLocation("tfc", "raw_iron_bloom");

    /** ore item id → its output. Built from the (grade × base-ore) cross product below. */
    private static final Map<ResourceLocation, Output> ORE_OUTPUT = build();

    private static final String[] GRADES = {"small", "poor", "normal", "rich"};

    private static Map<ResourceLocation, Output> build()
    {
        final Map<ResourceLocation, Output> map = new HashMap<>();
        // base ore name → ingot metal (a cast ingot output)
        addMetal(map, "copper", "native_copper", "malachite", "tetrahedrite");
        addMetal(map, "tin", "cassiterite");
        addMetal(map, "bismuth", "bismuthinite");
        addMetal(map, "zinc", "sphalerite");
        addMetal(map, "silver", "native_silver");
        addMetal(map, "gold", "native_gold");
        addMetal(map, "nickel", "garnierite");
        // iron ores → raw iron bloom (no ingot, no mold)
        for (final String base : new String[] {"hematite", "magnetite", "limonite"})
        {
            putGrades(map, base, new Output(BLOOM, true));
        }
        return map;
    }

    private static void addMetal(final Map<ResourceLocation, Output> map, final String metal, final String... bases)
    {
        final Output out = new Output(new ResourceLocation("tfc", "metal/ingot/" + metal), false);
        for (final String base : bases)
        {
            putGrades(map, base, out);
        }
    }

    private static void putGrades(final Map<ResourceLocation, Output> map, final String base, final Output out)
    {
        for (final String grade : GRADES)
        {
            map.put(new ResourceLocation("tfc", "ore/" + grade + "_" + base), out);
        }
    }

    /** Whether this stack is an ore the smelter can process. */
    public static boolean isOre(final ItemStack stack)
    {
        return !stack.isEmpty() && ORE_OUTPUT.containsKey(idOf(stack));
    }

    /** The output this ore smelts toward, or {@code null} if it isn't a smelter ore. */
    public static Output outputFor(final ItemStack stack)
    {
        return stack.isEmpty() ? null : ORE_OUTPUT.get(idOf(stack));
    }

    /**
     * Metal value of one of this ore, in mB, from its grade prefix ({@value #UNITS_PER_OUTPUT} mB = one output);
     * 0 if it isn't a smelter ore.
     */
    public static int meltMb(final ItemStack stack)
    {
        final ResourceLocation id = idOf(stack);
        if (id == null || !ORE_OUTPUT.containsKey(id))
        {
            return 0;
        }
        final String path = id.getPath(); // ore/<grade>_<base>
        if (path.startsWith("ore/small_")) return 10;
        if (path.startsWith("ore/poor_")) return 15;
        if (path.startsWith("ore/normal_")) return 25;
        if (path.startsWith("ore/rich_")) return 35;
        return 0;
    }

    private static ResourceLocation idOf(final ItemStack stack)
    {
        final Item item = stack.getItem();
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
