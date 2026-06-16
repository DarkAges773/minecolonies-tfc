package com.firmavanilla.worldgen;

import com.firmavanilla.FirmaVanilla;
import com.firmavanilla.block.QuartzClusterBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code firmavanilla:quartz_cluster} — a cave-decoration worldgen feature that grows {@link QuartzClusterBlock}
 * veins <b>out of cave surfaces</b> (the "quartz cave" look). The block is a self-shaping connected block (core +
 * per-side arms), so a placed vein renders as thin struts that chain together and plug into the rock.
 *
 * <p><b>Gated to volcanoes.</b> The placed feature adds TFC's {@code tfc:volcano} placement modifier, so quartz
 * caves only generate within the footprint of a TFC volcano (the volcanic biomes — {@code volcanic_mountains},
 * {@code volcanic_oceanic_mountains}, {@code canyons}, …). Those are rare and regional, and their rock is the
 * igneous-extrusive set whose felsic members (rhyolite, dacite) are quartz-bearing — so this is a rare,
 * concentrated, lore-fitting place to find quartz, with no need for a big-pocket/cross-chunk mechanism.
 *
 * <p>It places blocks only into <b>existing cave void</b> (never carves rock): the placed feature also gates with
 * {@code tfc:carving_mask} (step {@code air}), so every origin is already an air block in a carved cave; the
 * feature only ever {@code setBlock}s air → quartz. (Block-only — never spawns entities.)
 *
 * <p><b>Per origin</b> it: (1) requires the spot to be <i>against a surface</i> with an adjacent quartz-bearing-rock
 * face (the {@code firmavanilla:quartz_cluster_host} tag — the "near a block" anchor + strata gate); (2) builds a
 * vein direction purely from the <i>open</i> faces (one per axis, 1–3 axes, sign uniform — unbiased — so it points
 * into open cave and can be straight, diagonal or vertical); (3) lays cluster blocks out along it for a random
 * length up to {@code maxReach}, stopping at a wall, then <b>finalises connections</b> so each block self-shapes to
 * its neighbours. Because {@code place()} runs per carved position, a volcano's caves fill with crisscrossing
 * quartz veins.
 */
public class QuartzClusterFeature extends Feature<QuartzClusterFeature.Config>
{
    /**
     * @param crystal  the connected cluster block to place (a {@link QuartzClusterBlock}).
     * @param host     block tag of quartz-bearing rock a vein must grow from (anchor + strata gate).
     * @param maxReach max length (in blocks) of each vein before the limit stops it.
     */
    public record Config(Block crystal, TagKey<Block> host, int maxReach) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("crystal").forGetter(Config::crystal),
                TagKey.codec(Registries.BLOCK).fieldOf("host").forGetter(Config::host),
                Codec.intRange(1, 16).optionalFieldOf("max_reach", 10).forGetter(Config::maxReach)
        ).apply(i, Config::new));
    }

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, FirmaVanilla.MODID);

    public static final RegistryObject<Feature<Config>> QUARTZ_CLUSTER =
            FEATURES.register("quartz_cluster", () -> new QuartzClusterFeature(Config.CODEC));

    public static void init(final IEventBus modBus)
    {
        FEATURES.register(modBus);
    }

    public QuartzClusterFeature(final Codec<Config> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(final FeaturePlaceContext<Config> ctx)
    {
        final WorldGenLevel level = ctx.level();
        final BlockPos origin = ctx.origin();
        final RandomSource rand = ctx.random();
        final Config cfg = ctx.config();

        if (!isOpen(level, origin)) return false;

        // "Near a block": collect the open faces (where we can shoot) and require an adjacent host-rock face.
        final List<Direction> open = new ArrayList<>(6);
        boolean anchoredOnHost = false;
        for (final Direction d : Direction.values())
        {
            final BlockPos n = origin.relative(d);
            if (isOpen(level, n)) open.add(d);
            else if (level.getBlockState(n).is(cfg.host())) anchoredOnHost = true;
        }
        if (!anchoredOnHost || open.isEmpty()) return false;

        // Direction from open faces only, at most one face per axis — primary axis always, others by a coin flip; the
        // sign is uniform among that axis's open faces (unbiased). Every component points into open cave; the primary
        // axis (always included) also drives the grain.
        final List<List<Direction>> openByAxis = new ArrayList<>(3);
        for (final Direction.Axis ax : Direction.Axis.values())
        {
            final List<Direction> faces = new ArrayList<>(2);
            for (final Direction d : open) if (d.getAxis() == ax) faces.add(d);
            if (!faces.isEmpty()) openByAxis.add(faces);
        }
        final List<Direction> primaryAxisFaces = openByAxis.get(rand.nextInt(openByAxis.size()));
        final Direction.Axis primaryAxis = primaryAxisFaces.get(0).getAxis();
        int dx = 0, dy = 0, dz = 0;
        for (final List<Direction> axisFaces : openByAxis)
        {
            if (axisFaces == primaryAxisFaces || rand.nextBoolean())
            {
                final Direction d = axisFaces.get(rand.nextInt(axisFaces.size()));
                dx += d.getStepX();
                dy += d.getStepY();
                dz += d.getStepZ();
            }
        }

        // Lay the vein (default state for now), collecting positions. Grain runs along the vein's primary axis.
        BlockState base = cfg.crystal().defaultBlockState();
        if (base.hasProperty(BlockStateProperties.AXIS)) base = base.setValue(BlockStateProperties.AXIS, primaryAxis);
        final List<BlockPos> placed = new ArrayList<>();
        level.setBlock(origin, waterlog(base, level, origin), 2);
        placed.add(origin.immutable());
        walkArm(level, origin, dx, dy, dz, cfg.maxReach(), base, rand, placed);

        // Finalise self-shaping connections now that the whole vein and the surrounding rock are in place. Read the
        // placed state back so each block keeps its own grain axis AND waterlogged flag while connections recompute.
        if (cfg.crystal() instanceof QuartzClusterBlock cluster)
        {
            for (final BlockPos p : placed)
            {
                final BlockState s = level.getBlockState(p);
                if (s.is(cfg.crystal())) level.setBlock(p, cluster.withConnections(s, level, p), 2);
            }
        }
        return true;
    }

    /**
     * Lay the vein from {@code center} along {@code (dx,dy,dz)} for a random length (1..{@code maxReach}), stopping
     * early at the first wall. Diagonals are walked one axis at a time (a face-connected staircase) so the vein never
     * breaks into corner-only steps; the per-block connection state is computed afterwards.
     */
    private static void walkArm(final WorldGenLevel level, final BlockPos center, final int dx, final int dy,
                                final int dz, final int maxReach, final BlockState block, final RandomSource rand,
                                final List<BlockPos> placed)
    {
        final List<Direction.Axis> cycle = nonzeroAxes(dx, dy, dz);
        final BlockPos.MutableBlockPos cur = center.mutable();
        int rot = rand.nextInt(cycle.size()); // vary which axis a diagonal staircase leads with
        final int len = 1 + rand.nextInt(maxReach); // randomize length 1..maxReach (don't always run the full reach)
        for (int i = 0; i < len; i++)
        {
            final Direction.Axis axis = cycle.get(rot++ % cycle.size());
            final int comp = axis == Direction.Axis.X ? dx : axis == Direction.Axis.Y ? dy : dz; // ±1 (axis is non-zero)
            final Direction.AxisDirection ad =
                    comp > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
            cur.move(Direction.fromAxisAndDirection(axis, ad));
            if (!isOpen(level, cur)) break; // hit a wall (rock or lava) — stop
            final BlockPos p = cur.immutable();
            level.setBlock(p, waterlog(block, level, p), 2);
            placed.add(p);
        }
    }

    private static List<Direction.Axis> nonzeroAxes(final int dx, final int dy, final int dz)
    {
        final List<Direction.Axis> axes = new ArrayList<>(3);
        if (dx != 0) axes.add(Direction.Axis.X);
        if (dy != 0) axes.add(Direction.Axis.Y);
        if (dz != 0) axes.add(Direction.Axis.Z);
        return axes;
    }

    /**
     * Open = an air block OR a water block — the carved cave void, which below sea level may be flooded. We grow
     * veins through both (water-filled spots get a waterlogged cluster); rock and <b>lava</b> are NOT open, so veins
     * stop at them and lava is never replaced.
     */
    private static boolean isOpen(final WorldGenLevel level, final BlockPos pos)
    {
        final BlockState s = level.getBlockState(pos);
        return s.isAir() || s.getFluidState().is(FluidTags.WATER);
    }

    /** Whether {@code pos} currently holds water (used to waterlog a cluster placed there). */
    private static boolean isWater(final WorldGenLevel level, final BlockPos pos)
    {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** {@code base} with WATERLOGGED set to match whether {@code pos} currently holds water. */
    private static BlockState waterlog(final BlockState base, final WorldGenLevel level, final BlockPos pos)
    {
        return base.hasProperty(BlockStateProperties.WATERLOGGED)
                ? base.setValue(BlockStateProperties.WATERLOGGED, isWater(level, pos)) : base;
    }
}
