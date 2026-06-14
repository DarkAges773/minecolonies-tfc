package com.firmavanilla.weathering;

import com.firmavanilla.FirmaVanilla;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The reusable core of the "weathering set" workflow: it splices modded block→block mappings into vanilla's
 * <b>existing</b> copper machinery so every vanilla mechanic works for free, with <b>no mixin</b>.
 *
 * <p>Vanilla keys all of its copper behaviour off three static {@code Supplier<BiMap<Block,Block>>} tables:
 * {@link WeatheringCopper#NEXT_BY_BLOCK} (oxidation; its inverse drives axe-scraping, {@code getFirst} and the
 * lightning de-oxidation) and {@link HoneycombItem}'s {@code WAXABLES} (honeycomb waxing; its inverse drives
 * axe wax-off). They're {@code public static final} memoized immutable maps with no extension hook, so we
 * locate each field <b>by probing its contents</b> (obfuscation-proof — no field names / SRG), rebuild it with
 * vanilla's entries plus ours, and overwrite the field via {@code Unsafe} (defeats {@code final}).
 *
 * <p>Call {@link #addOxidationStep}/{@link #addWax} during registration, then {@link #inject()} once at
 * {@code FMLCommonSetupEvent} (before any world exists, so the maps are still pristine). A block that should
 * oxidise/scrape/clear-by-lightning must also {@code implements WeatheringCopper}; a waxed block must not.
 */
public final class WeatheringMaps
{
    private WeatheringMaps() {}

    private static final Map<Block, Block> OXIDATION = new LinkedHashMap<>(); // unaffected→exposed→weathered→oxidized links
    private static final Map<Block, Block> WAXING = new LinkedHashMap<>();    // unwaxed→waxed links

    /** Register one oxidation transition (the less-oxidised block ages into the more-oxidised one). */
    public static void addOxidationStep(final Block from, final Block to) { OXIDATION.put(from, to); }

    /** Register one waxing pair (honeycomb turns {@code unwaxed} into {@code waxed}; an axe reverses it). */
    public static void addWax(final Block unwaxed, final Block waxed) { WAXING.put(unwaxed, waxed); }

    /** Splice all registered mappings into the vanilla maps. Run once, at common setup (main thread). */
    public static void inject()
    {
        try
        {
            if (!OXIDATION.isEmpty())
            {
                spliceBiMap(
                        field(WeatheringCopper.class, Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER),  // NEXT_BY_BLOCK
                        field(WeatheringCopper.class, Blocks.EXPOSED_COPPER, Blocks.COPPER_BLOCK),  // PREVIOUS_BY_BLOCK (inverse)
                        OXIDATION);
            }
            if (!WAXING.isEmpty())
            {
                spliceBiMap(
                        field(HoneycombItem.class, Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK), // WAXABLES
                        field(HoneycombItem.class, Blocks.WAXED_COPPER_BLOCK, Blocks.COPPER_BLOCK), // WAX_OFF_BY_BLOCK (inverse)
                        WAXING);
            }
            FirmaVanilla.LOGGER.info("Weathering maps injected: {} oxidation step(s), {} wax pair(s).",
                    OXIDATION.size(), WAXING.size());
        }
        catch (final Throwable t)
        {
            FirmaVanilla.LOGGER.error("Failed to inject weathering maps — copper-bar weathering will be inert.", t);
        }
    }

    /** Rebuild {@code forwardField} with vanilla + {@code extra}, and reset {@code inverseField} to its inverse. */
    private static void spliceBiMap(final Field forwardField, final Field inverseField, final Map<Block, Block> extra) throws Exception
    {
        final BiMap<Block, Block> merged = HashBiMap.create(currentMap(forwardField));
        extra.forEach((k, v) -> {
            if (!merged.containsKey(k) && !merged.containsValue(v)) merged.put(k, v);
        });
        final ImmutableBiMap<Block, Block> frozen = ImmutableBiMap.copyOf(merged);
        putStatic(forwardField, (Supplier<BiMap<Block, Block>>) () -> frozen);
        putStatic(inverseField, (Supplier<BiMap<Block, Block>>) frozen::inverse);
    }

    @SuppressWarnings("unchecked")
    private static BiMap<Block, Block> currentMap(final Field f) throws Exception
    {
        return ((Supplier<BiMap<Block, Block>>) f.get(null)).get();
    }

    /** Find the static {@code Supplier<BiMap>} field on {@code owner} whose map sends {@code key}→{@code val}. */
    private static Field field(final Class<?> owner, final Block key, final Block val)
    {
        for (final Field f : owner.getDeclaredFields())
        {
            if (!Modifier.isStatic(f.getModifiers()) || !Supplier.class.isAssignableFrom(f.getType())) continue;
            try
            {
                f.setAccessible(true);
                final Object map = ((Supplier<?>) f.get(null)).get();
                if (map instanceof Map<?, ?> m && m.get(key) == val) return f;
            }
            catch (final Throwable ignored) { /* not the field we want */ }
        }
        throw new IllegalStateException("No weathering BiMap field on " + owner.getName() + " mapping " + key + "→" + val);
    }

    private static void putStatic(final Field f, final Object value)
    {
        final Unsafe u = unsafe();
        u.putObject(u.staticFieldBase(f), u.staticFieldOffset(f), value);
    }

    private static Unsafe unsafe()
    {
        try
        {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        }
        catch (final Exception e)
        {
            throw new IllegalStateException("Unsafe unavailable", e);
        }
    }
}
