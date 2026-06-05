package com.mctfc.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry of <b>active build areas</b> that should be treated as TFC-supported (collapse-proof) while a
 * MineColonies structure worker (builder / miner / quarrier) is working there.
 *
 * <p>Each working AI registers the world bounding box of what it is building/excavating (refreshed continuously) and
 * removes it when done. TFC's collapse system consults this via two {@code Support} hooks ({@code isSupported} and
 * {@code findUnsupportedPositions}) — see {@code MixinSupport} — so a registered box behaves exactly like a region
 * packed with support beams, for <i>both</i> collapse trigger paths, with no polling: work happens only inside TFC's
 * own (rare, probability-gated) support queries, as a cheap point-in-AABB test over the handful of active boxes.
 *
 * <p>Boxes are keyed by worker UUID and tagged with the worker's dimension (so a box never shields the same coords in
 * another dimension). Removal is driven primarily by the worker's {@code resetCurrentStructure} (immediate, for the
 * builder/quarrier/miner-node schematic path); a <b>{@value #TTL_TICKS}-tick TTL</b> backstops the cases without that
 * signal (the miner's raw shaft excavation, a removed/idle worker), so a box always self-clears shortly after the work
 * stops. The registry is transient (in-memory) — a box simply re-registers on the next work step after a reload.
 */
public final class BuildAreaSupport
{
    private BuildAreaSupport() {}

    /** Backstop expiry: a box not refreshed for this many ticks is dropped (workers refresh far more often while working). */
    private static final long TTL_TICKS = 1200L;

    private record Region(ResourceKey<Level> dimension, BoundingBox box, long stamp) {}

    private static final Map<UUID, Region> REGIONS = new ConcurrentHashMap<>();

    /** Register/refresh the build area for a worker (idempotent — called every work step). */
    public static void register(final UUID worker, final ResourceKey<Level> dimension, final BoundingBox box, final long gameTime)
    {
        REGIONS.put(worker, new Region(dimension, box, gameTime));
    }

    /** Drop a worker's build area when its structure completes or resets. */
    public static void unregister(final UUID worker)
    {
        REGIONS.remove(worker);
    }

    /** Fast-path so the {@code Support} hooks add zero cost when nothing is being built. */
    public static boolean isEmpty()
    {
        return REGIONS.isEmpty();
    }

    /** Whether {@code pos} (in {@code world}'s dimension) is inside any active build area. Prunes expired boxes as it goes. */
    public static boolean isProtected(final BlockGetter world, final BlockPos pos)
    {
        if (REGIONS.isEmpty())
        {
            return false;
        }
        final Level level = (world instanceof Level l) ? l : null;
        final long now = (level != null) ? level.getGameTime() : -1L;
        final ResourceKey<Level> dim = (level != null) ? level.dimension() : null;

        boolean found = false;
        final Iterator<Map.Entry<UUID, Region>> it = REGIONS.entrySet().iterator();
        while (it.hasNext())
        {
            final Region region = it.next().getValue();
            if (now >= 0 && now - region.stamp() > TTL_TICKS)
            {
                it.remove();
                continue;
            }
            if (!found && (dim == null || dim.equals(region.dimension())) && region.box().isInside(pos))
            {
                found = true;
            }
        }
        return found;
    }
}
