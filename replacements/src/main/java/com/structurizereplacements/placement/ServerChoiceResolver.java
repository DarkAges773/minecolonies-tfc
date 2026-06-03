package com.structurizereplacements.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/**
 * Server-side hook for resolving per-placement choices that live outside this (Structurize-only) mod —
 * e.g. choices persisted on a MineColonies building. The structure handler (see
 * {@code MixinAbstractStructureHandler#getReplacementChoices}) calls {@link #resolve} lazily when it has
 * no choices of its own and is running server-side, so the builder picks up the player's stored choices
 * without this mod needing to know about MineColonies.
 *
 * <p>Consumers register a {@link Resolver} once at mod init; {@code null}/empty results mean "no choices
 * here, fall back to datapack rules".
 */
public final class ServerChoiceResolver
{
    private ServerChoiceResolver() {}

    /** Maps a server-side build position to the choices stored for whatever lives there (a building, …). */
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
