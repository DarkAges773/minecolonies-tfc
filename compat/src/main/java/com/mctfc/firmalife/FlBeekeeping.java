package com.mctfc.firmalife;

import com.eerussianguy.firmalife.common.blockentities.FLBeehiveBlockEntity;
import com.eerussianguy.firmalife.common.blocks.FLBeehiveBlock;
import com.eerussianguy.firmalife.common.capabilities.bee.BeeCapability;
import com.eerussianguy.firmalife.common.capabilities.bee.IBee;
import com.eerussianguy.firmalife.common.items.FLItems;
import com.mctfc.MineColoniesTFC;
import net.dries007.tfc.common.blockentities.InventoryBlockEntity;
import net.dries007.tfc.common.items.TFCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * FirmaLife beekeeping bridge for the MineColonies Beekeeper.
 *
 * <p>TerraFirmaCraft itself has <b>no beekeeping</b>; FirmaLife adds it, and its hive is a completely different
 * model from vanilla's: a TFC {@code TickableInventoryBlockEntity} with an integer {@code honey} counter (0..12)
 * and boolean {@code HONEY}/{@code BEES} blockstate props — <i>not</i> vanilla's {@code BeehiveBlock} /
 * {@code BeehiveBlockEntity} / {@code honey_level} (0..5). The worker harvests two products: <b>honey</b>, taken
 * into a {@code tfc:empty_jar} which becomes a {@code firmalife:jar/honey} (exactly what FirmaLife's own
 * empty-jar right-click does), and <b>beeswax</b>, scraped off a queened frame with a knife (which resets that
 * frame to a fresh queenless one — so wax is a controlled, per-slot harvest). It also tops up empty frame slots.
 * Bees live as a capability on the frame items and breed <b>autonomously</b>, so the worker never breeds them.
 *
 * <p>This class is the <b>only</b> place that names FirmaLife types, so it is loaded lazily and referenced solely
 * behind {@code ModList.isLoaded("firmalife")} guards (see {@code MixinEntityAIWorkBeekeeper} /
 * {@code MixinItemScepterBeekeeper}). Worlds without FirmaLife never load it — no {@code NoClassDefFoundError}.
 * Frame slots (0–3) are reached through TFC's {@code InventoryBlockEntity#inventory} (FirmaLife exposes only the
 * jar slots on the Forge capability); after a frame write we call the BE's public {@code setAndUpdateSlots(int)}
 * to refresh the bee cache, blockstate props and client sync. See {@code docs/tfc-beekeeper-worker.md}.
 */
public final class FlBeekeeping
{
    private FlBeekeeping() {}

    /** True for a FirmaLife beehive block ({@code firmalife:beehive}). */
    public static boolean isHive(final BlockState state)
    {
        return state.getBlock() instanceof FLBeehiveBlock;
    }

    /** True for the FirmaLife beehive {@link net.minecraft.world.level.block.Block} itself. */
    public static boolean isHiveBlock(final net.minecraft.world.level.block.Block block)
    {
        return block instanceof FLBeehiveBlock;
    }

    public static boolean isHive(final Level world, final BlockPos pos)
    {
        return isHive(world.getBlockState(pos));
    }

    /** Whether any of the building's registered hives is a FirmaLife hive (i.e. this is a FirmaLife apiary). */
    public static boolean hasFlHive(final Level world, final Collection<BlockPos> hives)
    {
        for (final BlockPos pos : hives)
        {
            if (isHive(world, pos))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this hive needs the worker's attention: harvestable honey, a scrapeable queen in a wax-enabled
     * slot ({@code waxMask}), or an empty frame slot to refill.
     */
    public static boolean needsService(final Level world, final BlockPos pos, final int waxMask)
    {
        final FLBeehiveBlockEntity be = hive(world, pos);
        if (be == null)
        {
            return false;
        }
        if (be.getHoney() > 0)
        {
            return true;
        }
        final IItemHandlerModifiable frames = frames(be);
        if (frames == null)
        {
            return false;
        }
        for (int slot = 0; slot < FLBeehiveBlockEntity.FRAME_SLOTS; slot++)
        {
            final ItemStack frame = frames.getStackInSlot(slot);
            if (frame.isEmpty())
            {
                return true;
            }
            if ((waxMask & (1 << slot)) != 0 && hasQueen(frame))
            {
                return true;
            }
        }
        return false;
    }

    /** First registered FirmaLife hive that needs service, or {@code null}. */
    public static BlockPos firstServiceableHive(final Level world, final Collection<BlockPos> hives, final int waxMask)
    {
        for (final BlockPos pos : hives)
        {
            if (needsService(world, pos, waxMask))
            {
                return pos;
            }
        }
        return null;
    }

    /** Whether any registered FirmaLife hive has an empty frame slot (so the worker should stock empty frames). */
    public static boolean anyEmptyFrameSlot(final Level world, final Collection<BlockPos> hives)
    {
        for (final BlockPos pos : hives)
        {
            final FLBeehiveBlockEntity be = hive(world, pos);
            if (be != null && emptyFrameSlot(be) != -1)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Pull up to {@code max} honey from the hive (one jar's worth each), returning how many were taken. Mirrors
     * FirmaLife's empty-jar {@code use} path — fills one jar per honey and never angers the bees.
     */
    public static int takeHoney(final Level world, final BlockPos pos, final int max)
    {
        final FLBeehiveBlockEntity be = hive(world, pos);
        return (max > 0 && be != null) ? be.takeHoney(max) : 0;
    }

    /** First wax-enabled ({@code waxMask}) frame slot that holds a queened frame, or -1. */
    public static int queenWaxSlot(final Level world, final BlockPos pos, final int waxMask)
    {
        final FLBeehiveBlockEntity be = hive(world, pos);
        if (be == null)
        {
            return -1;
        }
        final IItemHandlerModifiable frames = frames(be);
        if (frames == null)
        {
            return -1;
        }
        for (int slot = 0; slot < FLBeehiveBlockEntity.FRAME_SLOTS; slot++)
        {
            if ((waxMask & (1 << slot)) != 0 && hasQueen(frames.getStackInSlot(slot)))
            {
                return slot;
            }
        }
        return -1;
    }

    /** First empty frame slot, or -1. */
    public static int emptyFrameSlot(final Level world, final BlockPos pos)
    {
        final FLBeehiveBlockEntity be = hive(world, pos);
        return be == null ? -1 : emptyFrameSlot(be);
    }

    /**
     * Replace the frame in {@code slot} with a fresh (queenless) {@code firmalife:beehive_frame}, refreshing the
     * BE. This is exactly what FirmaLife's knife-scrape does to a queened frame — and equally the right thing for
     * topping up an empty slot. The caller banks the beeswax (scrape) or consumes the frame from its stock (refill).
     */
    public static void putFreshFrame(final Level world, final BlockPos pos, final int slot)
    {
        final FLBeehiveBlockEntity be = hive(world, pos);
        if (be == null)
        {
            return;
        }
        final IItemHandlerModifiable frames = frames(be);
        if (frames != null)
        {
            frames.setStackInSlot(slot, new ItemStack(FLItems.BEEHIVE_FRAME.get()));
            be.setAndUpdateSlots(slot);
        }
    }

    /** TFC's empty jar — the container a honey harvest consumes. */
    public static Item emptyJar()
    {
        return TFCItems.EMPTY_JAR.get();
    }

    public static boolean isEmptyJar(final ItemStack stack)
    {
        return stack.is(TFCItems.EMPTY_JAR.get());
    }

    public static ItemStack emptyJarStack(final int count)
    {
        return new ItemStack(TFCItems.EMPTY_JAR.get(), count);
    }

    /** FirmaLife's jar of honey — the honey harvest product ({@code firmalife:jar/honey}). */
    public static ItemStack honeyJar(final int count)
    {
        return new ItemStack(FLItems.HONEY_JAR.get(), count);
    }

    /** FirmaLife beeswax — the wax-scrape product. */
    public static ItemStack beeswax(final int count)
    {
        return new ItemStack(FLItems.BEESWAX.get(), count);
    }

    public static boolean isFrame(final ItemStack stack)
    {
        return stack.is(FLItems.BEEHIVE_FRAME.get());
    }

    public static ItemStack frameStack(final int count)
    {
        return new ItemStack(FLItems.BEEHIVE_FRAME.get(), count);
    }

    // ---- internals -------------------------------------------------------------------------------------------

    private static FLBeehiveBlockEntity hive(final Level world, final BlockPos pos)
    {
        final BlockEntity be = world.getBlockEntity(pos);
        return be instanceof FLBeehiveBlockEntity fl ? fl : null;
    }

    /**
     * TFC's {@code InventoryBlockEntity#inventory} (the full 6-slot handler), reached reflectively. A Mixin
     * {@code @Accessor} can't bind it — the field is declared as a type variable ({@code C inventory}), which the
     * Mixin annotation processor rejects ("could not locate target") even though its erased descriptor is
     * {@code IItemHandlerModifiable}. The field name is TFC's own (never remapped), so this works in dev and prod.
     */
    private static final Field INVENTORY_FIELD = resolveInventoryField();

    private static Field resolveInventoryField()
    {
        try
        {
            final Field field = InventoryBlockEntity.class.getDeclaredField("inventory");
            field.setAccessible(true);
            return field;
        }
        catch (final NoSuchFieldException e)
        {
            MineColoniesTFC.LOGGER.error("Could not find TFC InventoryBlockEntity#inventory — FirmaLife hive frames unavailable.", e);
            return null;
        }
    }

    private static IItemHandlerModifiable frames(final FLBeehiveBlockEntity be)
    {
        if (INVENTORY_FIELD == null)
        {
            return null;
        }
        try
        {
            return (IItemHandlerModifiable) INVENTORY_FIELD.get(be);
        }
        catch (final IllegalAccessException e)
        {
            return null;
        }
    }

    private static int emptyFrameSlot(final FLBeehiveBlockEntity be)
    {
        final IItemHandlerModifiable frames = frames(be);
        if (frames == null)
        {
            return -1;
        }
        for (int slot = 0; slot < FLBeehiveBlockEntity.FRAME_SLOTS; slot++)
        {
            if (frames.getStackInSlot(slot).isEmpty())
            {
                return slot;
            }
        }
        return -1;
    }

    private static boolean hasQueen(final ItemStack frame)
    {
        return frame.getCapability(BeeCapability.CAPABILITY).map(IBee::hasQueen).orElse(false);
    }
}
