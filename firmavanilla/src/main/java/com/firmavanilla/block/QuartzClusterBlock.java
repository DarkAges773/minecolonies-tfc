package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The cave "quartz cluster" block placed by {@link com.firmavanilla.worldgen.QuartzClusterFeature} — a connected,
 * self-shaping block with the six directional boolean properties (the {@link PipeBlock} property set, like a wall
 * or a modded pipe, but driving a richer shape).
 *
 * <p><b>Shape = the union of a half-slab per connected side.</b> Each side it connects to fills the half of the
 * cube adjacent to that side, and that single rule yields the whole stair/slab family:
 * <ul>
 *   <li>1 side → one half-slab = <b>slab / vertical slab</b>;</li>
 *   <li>2 <i>adjacent</i> sides → two perpendicular halves = <b>stair</b> (L cross-section, 3/4 cube);</li>
 *   <li>3 adjacent sides → <b>corner stair</b> (7/8, one corner missing);</li>
 *   <li>2 <i>opposite</i> sides → the two halves fill the cube = <b>full block</b> (grain along that axis);</li>
 *   <li>0 sides → also a <b>full block</b>, rendered as the original quartz pillar ({@code raw_quartz_column}'s
 *       {@code cube_column} model, y-axis grain);</li>
 *   <li>and every remaining combination falls out the same way.</li>
 * </ul>
 * The blockstate is multipart (one half-slab model per {@code true} side); the collision/outline {@link VoxelShape}
 * is the matching union, precomputed for all 64 combinations.
 *
 * <p>It also carries an {@code AXIS} property like the vanilla quartz pillar — set from the clicked face on
 * placement (and from the vein direction in worldgen) — which orients the quartz <i>grain</i> (which faces show
 * the {@code #end} quartz-top cap) <b>independently of the shape</b>: the shape is purely the connections, the axis
 * is purely the grain, so the multipart picks the per-axis model variant for each connected side.
 *
 * <p>It is <b>waterloggable</b> ({@link SimpleWaterloggedBlock}) — placed in water (or grown into a flooded cave by
 * the worldgen feature) it keeps the water around its open shape.
 *
 * <p><b>Connection rule:</b> it connects to a neighbour that is either (a) another quartz block — anything in
 * {@code #firmavanilla:quartz_cluster_connectable} (this block + {@code raw_quartz_column}) — or (b) a solid sturdy
 * face (the cave wall/floor/ceiling it plugs into). Connections recompute dynamically
 * ({@link #getStateForPlacement}/{@link #updateShape}); the worldgen feature finalises them after laying a vein.
 *
 * <p>It shares the {@code raw_quartz_column}'s TFC raw-rock behaviour: it joins {@code tfc:breaks_when_isolated}
 * and uses a {@code tfc:is_isolated} loot table, so mined with support it drops nether quartz, but left
 * unsupported (isolated) it pops off and drops itself — and an unsupported cluster is already the 0-connection
 * full-block pillar, so it behaves exactly like the column.
 */
public class QuartzClusterBlock extends Block implements SimpleWaterloggedBlock
{
    /** Blocks a cluster connects to directly (besides any solid sturdy face): this block + {@code raw_quartz_column}. */
    public static final TagKey<Block> CONNECTABLE =
            BlockTags.create(new ResourceLocation(FirmaVanilla.MODID, "quartz_cluster_connectable"));

    private static final Map<Direction, BooleanProperty> PROPS = PipeBlock.PROPERTY_BY_DIRECTION;

    /** One half-slab box per direction (the half of the cube adjacent to that side). */
    private static final Map<Direction, VoxelShape> HALVES = Map.of(
            Direction.DOWN, Block.box(0, 0, 0, 16, 8, 16),
            Direction.UP, Block.box(0, 8, 0, 16, 16, 16),
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 8),
            Direction.SOUTH, Block.box(0, 0, 8, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 8, 16, 16),
            Direction.EAST, Block.box(8, 0, 0, 16, 16, 16));

    /** Precomputed union shape for every combination of the six sides, indexed by {@link #index}. */
    private static final VoxelShape[] SHAPES = makeShapes();

    public QuartzClusterBlock(final Properties props)
    {
        super(props);
        BlockState state = stateDefinition.any()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y)
                .setValue(BlockStateProperties.WATERLOGGED, false);
        for (final BooleanProperty p : PROPS.values()) state = state.setValue(p, false);
        registerDefaultState(state);
    }

    private static VoxelShape[] makeShapes()
    {
        final VoxelShape[] out = new VoxelShape[64];
        for (int i = 0; i < out.length; i++)
        {
            VoxelShape s = Shapes.empty();
            for (final Direction d : Direction.values())
            {
                if ((i & (1 << d.ordinal())) != 0) s = Shapes.or(s, HALVES.get(d));
            }
            out[i] = s.isEmpty() ? Shapes.block() : s; // no connections → full block (rendered as the pillar)
        }
        return out;
    }

    private static int index(final BlockState state)
    {
        int i = 0;
        for (final Direction d : Direction.values())
        {
            if (state.getValue(PROPS.get(d))) i |= 1 << d.ordinal();
        }
        return i;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.AXIS, BlockStateProperties.WATERLOGGED,
                PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx)
    {
        return SHAPES[index(state)];
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext ctx)
    {
        // Axis from the clicked face, exactly like a vanilla quartz pillar; waterlogged if placed in water;
        // connections fill in from there.
        final BlockState state = defaultBlockState()
                .setValue(BlockStateProperties.AXIS, ctx.getClickedFace().getAxis())
                .setValue(BlockStateProperties.WATERLOGGED,
                        ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER);
        return withConnections(state, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(final BlockState state, final Direction dir, final BlockState neighbor,
                                  final LevelAccessor level, final BlockPos pos, final BlockPos neighborPos)
    {
        if (state.getValue(BlockStateProperties.WATERLOGGED))
        {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(PROPS.get(dir), connects(neighbor, level, neighborPos, dir));
    }

    @Override
    public FluidState getFluidState(final BlockState state)
    {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /** Recompute all six connections from the world around {@code pos} (used by placement and the worldgen feature). */
    public BlockState withConnections(BlockState state, final BlockGetter level, final BlockPos pos)
    {
        for (final Direction d : Direction.values())
        {
            final BlockPos np = pos.relative(d);
            state = state.setValue(PROPS.get(d), connects(level.getBlockState(np), level, np, d));
        }
        return state;
    }

    private static boolean connects(final BlockState neighbor, final BlockGetter level, final BlockPos npos, final Direction dir)
    {
        return neighbor.is(CONNECTABLE) || neighbor.isFaceSturdy(level, npos, dir.getOpposite());
    }
}
