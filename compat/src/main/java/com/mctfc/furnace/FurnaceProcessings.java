package com.mctfc.furnace;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of {@link FurnaceProcessing} completers, keyed by the {@code kind} a worker stamps into a furnace's
 * {@link FurnaceProcess} cap when it loads it. {@code MixinAbstractFurnaceBlockEntity} consults this when a melting
 * furnace's flame dies: it runs the completer for that furnace's kind, which turns the loaded inputs into the
 * finished result in place.
 *
 * <p>Register each converted hut's completer from the mod's setup ({@code MineColoniesTFC} ctor), alongside its
 * {@link FurnaceBehaviors} entry. The <b>first</b> kind registered is the back-compat default used for an empty or
 * unknown kind — i.e. an operation saved before kinds existed (only the smelter did), which the smelter (registered
 * first) then correctly finishes.
 */
public final class FurnaceProcessings
{
    private FurnaceProcessings() {}

    /** kind → the completer that finishes that kind of operation. */
    private static final Map<String, FurnaceProcessing> COMPLETERS = new ConcurrentHashMap<>();

    /** The kind used when a furnace's stored kind is empty/unknown (a pre-{@code kind} save) — the first registered. */
    private static volatile String defaultKind = "";

    /** Register a completer for a kind (e.g. {@code "smelt"}, {@code "cook"}). The first registration also becomes
     * the back-compat default for empty/unknown kinds. */
    public static void register(final String kind, final FurnaceProcessing completer)
    {
        COMPLETERS.put(kind, completer);
        if (defaultKind.isEmpty())
        {
            defaultKind = kind;
        }
    }

    /** Finish the operation in this furnace using the completer for {@code kind} (or the default if it's
     * empty/unknown). No-op if nothing is registered yet. */
    public static void complete(final String kind, final AbstractFurnaceBlockEntity furnace)
    {
        FurnaceProcessing completer = COMPLETERS.get(kind);
        if (completer == null)
        {
            completer = COMPLETERS.get(defaultKind);
        }
        if (completer != null)
        {
            completer.complete(furnace);
        }
    }
}
