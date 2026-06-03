package com.structurizereplacements.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Hook for resolving per-placement choices that live outside this (Structurize-only) mod — e.g. choices
 * stored on a MineColonies building (server) or its synced client view. The structure handler (see
 * {@code MixinAbstractStructureHandler#getReplacementChoices}) calls {@link #resolve} when it has no
 * choices of its own, on <b>both</b> sides:
 * <ul>
 *   <li><b>server</b> — so the builder picks up the building's persisted choices;</li>
 *   <li><b>client</b> — so the "Build Options" material list / preview reflect the building's synced
 *       choices (the build-wand preview, where no building exists at the position, returns {@code null}
 *       here and falls back to the session picks).</li>
 * </ul>
 *
 * <p>Consumers register a {@link Resolver} once at mod init; {@code null}/empty results mean "no choices
 * here, fall back to datapack rules / session picks". The resolver itself decides side-specific behaviour.
 */
public final class ChoiceResolver
{
    private ChoiceResolver() {}

    /** Maps a build position to the choices stored for whatever lives there (a building, …). */
    @FunctionalInterface
    public interface Resolver
    {
        Map<Block, Block> resolve(Level world, BlockPos worldPos);
    }

    private static volatile Resolver resolver;

    public static void set(final Resolver r)
    {
        resolver = r;
    }

    public static Map<Block, Block> resolve(final Level world, final BlockPos worldPos)
    {
        final Resolver r = resolver;
        return (r == null || world == null || worldPos == null) ? null : r.resolve(world, worldPos);
    }
}
