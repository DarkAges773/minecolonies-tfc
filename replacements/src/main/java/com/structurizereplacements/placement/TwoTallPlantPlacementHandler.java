package com.structurizereplacements.placement;

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Places a <b>two-tall plant</b> — a plant block whose state has a {@code lower}/{@code upper} "part"-style
 * property (e.g. TFC's tall flowers, {@code part=lower/upper}) — the way vanilla's
 * {@code DoublePlantPlacementHandler} places vanilla double plants: <b>both halves in a single
 * {@code handle()} call</b>.
 *
 * <p>Why this is needed: the MineColonies builder places cells one per tick, so a lone half of a two-tall
 * plant fails its {@code canSurvive}/{@code updateShape} (which require the matching partner directly
 * adjacent) and is destroyed before its partner is placed — leaving only the top, only the bottom, or
 * nothing. Vanilla {@code DoublePlantBlock}s already have Structurize's own atomic handler; this covers the
 * <i>non-vanilla</i> two-tall plants our substitution engine produces (a vanilla double flower swapped to a
 * TFC tall flower, which uses {@code part} instead of {@code half} and is not a {@code DoublePlantBlock}).
 *
 * <p>Generic, no mod coupling: triggers on any {@link BushBlock} that is not a vanilla {@link DoublePlantBlock}
 * and has an enum property with both {@code lower} and {@code upper} values. Registered before
 * {@code GeneralBlockPlacementHandler} (see {@code StructurizeReplacements}).
 */
public class TwoTallPlantPlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(final Level world, final BlockPos pos, final BlockState state)
    {
        return state.getBlock() instanceof BushBlock
                && !(state.getBlock() instanceof DoublePlantBlock)
                && partProperty(state) != null;
    }

    @Override
    public ActionProcessingResult handle(final Level world, final BlockPos pos, final BlockState state,
                                         @Nullable final CompoundTag tileEntityData, final IPlacementContext context)
    {
        final Property<?> part = partProperty(state);
        if (part == null)
        {
            return ActionProcessingResult.PASS;
        }
        if (!"lower".equals(currentName(state, part)))
        {
            // Upper cell: the lower cell already placed both halves, so this is a no-op (handled, not passed
            // to the generic handler — which would place a lone, self-destructing upper).
            return ActionProcessingResult.SUCCESS;
        }
        world.setBlock(pos, state, 3);
        world.setBlock(pos.above(), setFromString(state, part, "upper"), 3);
        return ActionProcessingResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getRequiredItems(final Level world, final BlockPos pos, final BlockState state,
                                            @Nullable final CompoundTag tileEntityData, final IPlacementContext context)
    {
        final Property<?> part = partProperty(state);
        if (part != null && !"lower".equals(currentName(state, part)))
        {
            // Only the lower cell carries the material cost for the pair — so one plant is requested per
            // two-tall plant, not two.
            return Collections.emptyList();
        }
        return Collections.singletonList(BlockUtils.getItemStackFromBlockState(state));
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(final BlockState worldState, final BlockState blueprintState,
                                                     @Nullable final Tuple<BlockEntity, CompoundTag> blockEntityData, final IPlacementContext context)
    {
        if (worldState.getBlock() != blueprintState.getBlock())
        {
            return false;
        }
        final Property<?> wp = partProperty(worldState);
        final Property<?> bp = partProperty(blueprintState);
        if (wp == null || bp == null)
        {
            return worldState.equals(blueprintState);
        }
        // Match on block + part only: TFC plants carry a dynamic growth `stage`/`age` that drifts from the
        // blueprint's stored value, which would otherwise make the builder think the plant is never finished.
        return currentName(worldState, wp).equals(currentName(blueprintState, bp));
    }

    /** The first enum property whose values include both {@code lower} and {@code upper} (the two-tall "part"). */
    @Nullable
    private static Property<?> partProperty(final BlockState state)
    {
        for (final Property<?> p : state.getProperties())
        {
            if (hasValuesNamed(p, "lower", "upper"))
            {
                return p;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> boolean hasValuesNamed(final Property<T> property, final String a, final String b)
    {
        boolean foundA = false;
        boolean foundB = false;
        for (final T value : property.getPossibleValues())
        {
            final String name = property.getName(value);
            if (name.equals(a))
            {
                foundA = true;
            }
            else if (name.equals(b))
            {
                foundB = true;
            }
        }
        return foundA && foundB;
    }

    private static <T extends Comparable<T>> String currentName(final BlockState state, final Property<T> property)
    {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState setFromString(final BlockState state, final Property<T> property, final String value)
    {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
