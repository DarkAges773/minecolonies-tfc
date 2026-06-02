package com.structurizereplacements.substitution;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;

import java.util.List;

/**
 * Gates the family cascade by <b>block form</b> instead of by name alone.
 *
 * <p>A material-token swap (e.g. {@code oak → spruce}, see {@link FamilyRule}) is only applied when
 * the candidate block and its swapped target are the <i>same building shape</i> — both stairs, both
 * slabs, etc. These shape classes are extended by modded blocks too (e.g. TFC's {@code TFCStairBlock
 * extends StairBlock}), so the check works across vanilla and TFC. Crucially it EXCLUDES logs/wood
 * ({@code RotatedPillarBlock}), leaves ({@code LeavesBlock}, and TFC's plain-{@code Block} leaves),
 * saplings, and the plank base itself — none of which are one of these shapes — so cascading
 * {@code oak → spruce} no longer touches {@code oak_log}/{@code oak_leaves}.
 *
 * <p>Plain-{@code Block} variants (e.g. stone's {@code polished}/{@code bricks}) are intentionally not
 * covered here; use explicit {@code from → to} rules for those.
 */
final class CascadeShapes
{
    private CascadeShapes() {}

    private static final List<Class<? extends Block>> SHAPE_CLASSES = List.of(
            StairBlock.class,
            SlabBlock.class,
            WallBlock.class,
            FenceBlock.class,
            FenceGateBlock.class,
            DoorBlock.class,
            TrapDoorBlock.class,
            ButtonBlock.class,
            PressurePlateBlock.class);

    /** True iff {@code source} and {@code target} are the same cascadable building shape. */
    static boolean shareShape(final Block source, final Block target)
    {
        for (final Class<? extends Block> shape : SHAPE_CLASSES)
        {
            if (shape.isInstance(source) && shape.isInstance(target))
            {
                return true;
            }
        }
        return false;
    }
}
