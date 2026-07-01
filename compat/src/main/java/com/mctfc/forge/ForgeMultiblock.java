package com.mctfc.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic membership for the heat-forge multiblock (see {@code docs/tfc-forge-multiblock.md} §4). A connected
 * region of face-adjacent {@link HeatForgeBlock} blocks is partitioned into controllers of <b>≤ {@value #CAP} members</b>:
 * a BFS from the region's lowest {@link BlockPos} claims up to {@value #CAP} connected blocks (→ one controller), then
 * the process repeats on the remainder. Because it's driven purely by geometry (a stable {@link BlockPos#asLong()}
 * total order), membership is <b>stable across placement order and reload</b> — a region larger than {@value #CAP} is
 * simply several adjacent-but-separate devices.
 *
 * <p>Pure and stateless: {@link #groupOf} is recomputed on demand (the region is tiny — bounded by {@link #SCAN_CAP}),
 * so there is no cross-block cache to keep coherent.
 */
public final class ForgeMultiblock
{
    private ForgeMultiblock() {}

    /** Maximum members in one controller — a 1-to-1 match with TFC's charcoal forge input cap. */
    public static final int CAP = 5;

    /** Safety bound on the connected-region flood fill, so a pathological sprawl can't stall a tick. */
    private static final int SCAN_CAP = 64;

    /** The controller position of a group and its members (sorted by {@link BlockPos#asLong()}). */
    public record Group(BlockPos controller, List<BlockPos> members) {}

    /** Total order over positions — the "lowest {@link BlockPos}" the partition elects controllers by. */
    private static final Comparator<BlockPos> ORDER = Comparator.comparingLong(BlockPos::asLong);

    /**
     * The group {@code start} belongs to: its controller (the group's lowest {@link BlockPos}) and its ≤{@value #CAP}
     * members. {@code start} must itself be a forge block; the returned group always contains it.
     */
    public static Group groupOf(final BlockGetter level, final BlockPos start)
    {
        final TreeSet<BlockPos> remaining = new TreeSet<>(ORDER);
        remaining.addAll(region(level, start));

        while (!remaining.isEmpty())
        {
            final BlockPos min = remaining.first();
            final List<BlockPos> claimed = claim(level, min, remaining);
            for (final BlockPos pos : claimed)
            {
                remaining.remove(pos);
            }
            if (claimed.contains(start.immutable()))
            {
                claimed.sort(ORDER);
                return new Group(min, claimed);
            }
        }
        // start wasn't a forge / not found — degenerate single-member group.
        return new Group(start.immutable(), List.of(start.immutable()));
    }

    /** The whole connected region of forge blocks reachable from {@code start} (bounded by {@link #SCAN_CAP}). */
    private static Set<BlockPos> region(final BlockGetter level, final BlockPos start)
    {
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        final BlockPos origin = start.immutable();
        if (!isForge(level, origin))
        {
            seen.add(origin);
            return seen;
        }
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && seen.size() < SCAN_CAP)
        {
            final BlockPos cur = queue.poll();
            for (final Direction dir : Direction.values())
            {
                final BlockPos next = cur.relative(dir);
                if (!seen.contains(next) && isForge(level, next))
                {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        return seen;
    }

    /**
     * BFS from {@code min} over the still-{@code remaining} positions, claiming up to {@value #CAP} of them (the frontier
     * expands lowest-{@link BlockPos}-first so the claimed set is deterministic regardless of insertion order).
     */
    private static List<BlockPos> claim(final BlockGetter level, final BlockPos min, final Set<BlockPos> remaining)
    {
        final List<BlockPos> claimed = new ArrayList<>();
        final Set<BlockPos> visited = new HashSet<>();
        final TreeSet<BlockPos> frontier = new TreeSet<>(ORDER);
        frontier.add(min);
        visited.add(min);
        while (!frontier.isEmpty() && claimed.size() < CAP)
        {
            final BlockPos cur = frontier.pollFirst();
            claimed.add(cur);
            for (final Direction dir : Direction.values())
            {
                final BlockPos next = cur.relative(dir).immutable();
                if (remaining.contains(next) && !visited.contains(next))
                {
                    visited.add(next);
                    frontier.add(next);
                }
            }
        }
        return claimed;
    }

    /** Whether the block at {@code pos} is one of our heat-forge blocks. */
    public static boolean isForge(final BlockGetter level, final BlockPos pos)
    {
        return level.getBlockState(pos).getBlock() instanceof HeatForgeBlock;
    }
}
