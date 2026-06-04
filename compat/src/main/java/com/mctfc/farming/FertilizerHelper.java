package com.mctfc.farming;

import com.mctfc.Config;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blockentities.IFarmland;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.util.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * TFC soil-nutrient fertilizing for the colony farmer. Unlike vanilla (compost/bone meal = faster growth),
 * TFC fertilizers raise the farmland's N/P/K nutrients ({@link IFarmland}); each crop drains its own
 * {@code primaryNutrient} ({@link ICropBlock#getPrimaryNutrient}), and low nutrient = low yield. So the
 * farmer keeps a field's crop-specific nutrient topped up by applying a matching {@link Fertilizer} (a
 * datapack item → N/P/K). The farmer auto-picks the best-matching fertilizer it carries; it fertilizes at
 * plant time when the nutrient has dropped below the configured threshold ({@link Config#fertilizeBelow}).
 */
public final class FertilizerHelper
{
    private FertilizerHelper() {}

    /** Safety cap on applications per plant (a full stack of a weak fertilizer can't more than fill it). */
    private static final int MAX_APPLICATIONS = 64;

    /** The nutrient the crop a seed plants drains, or {@code null} if the seed isn't a TFC crop. */
    @Nullable
    public static NutrientType neededNutrient(final ItemStack seed)
    {
        if (seed.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ICropBlock crop)
        {
            return crop.getPrimaryNutrient();
        }
        return null;
    }

    /** Any TFC fertilizer item? */
    public static boolean isFertilizer(final ItemStack stack)
    {
        return Fertilizer.get(stack) != null;
    }

    /** Is {@code stack} a TFC fertilizer that supplies {@code nutrient}? Used so the farmer counts/requests
     *  only fertilizer it can actually use for the current crop (a nitrogen fertilizer doesn't count toward a
     *  phosphorus field, and stray bone meal — a phosphorus fertilizer — doesn't block a nitrogen field). */
    public static boolean providesNutrient(final ItemStack stack, final NutrientType nutrient)
    {
        final Fertilizer fertilizer = Fertilizer.get(stack);
        return fertilizer != null && fertilizer.getNutrient(nutrient) > 0.0f;
    }

    /**
     * One representative stack per item of every {@link Fertilizer} that provides {@code nutrient} — the
     * candidate list for a "request fertilizer" {@code StackList} (the farmer asks for any of them). Empty
     * if no fertilizer supplies that nutrient.
     */
    public static List<ItemStack> fertilizersFor(final NutrientType nutrient)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        for (final Fertilizer fertilizer : Fertilizer.MANAGER.getValues())
        {
            if (fertilizer.getNutrient(nutrient) > 0.0f)
            {
                for (final Item item : fertilizer.getValidItems())
                {
                    stacks.add(new ItemStack(item));
                }
            }
        }
        return stacks;
    }

    /**
     * If the soil at {@code farmlandPos} has run low on the crop's primary nutrient, top it back up by
     * applying matching fertilizer from {@code inventory} until the nutrient reaches {@link Config#fertilizeTarget}
     * (or the farmer runs out of matching fertilizer). Hysteresis: only kicks in below {@link Config#fertilizeBelow},
     * so it isn't re-applied every plant. The best (strongest) match is re-picked each application, so one
     * guano/compost refills in a step while weak pure powders take several — but the soil still ends up full.
     */
    public static void fertilizeForSeed(final Level level, final BlockPos farmlandPos, final ItemStack seed, final IItemHandler inventory)
    {
        final NutrientType nutrient = neededNutrient(seed);
        if (nutrient != null)
        {
            fertilize(level, farmlandPos, nutrient, inventory);
        }
    }

    /**
     * Core top-up: if the farmland at {@code farmlandPos} has run low on {@code nutrient}, apply matching
     * fertilizer from {@code inventory} until it reaches {@link Config#fertilizeTarget} (or the farmer runs
     * out). Hysteresis at {@link Config#fertilizeBelow} so it isn't re-applied while the soil is still fine.
     * Used both at plant time ({@link #fertilizeForSeed}) and opportunistically when the farmer visits a
     * growing crop.
     */
    public static void fertilize(final Level level, final BlockPos farmlandPos, final NutrientType nutrient, final IItemHandler inventory)
    {
        if (!(level.getBlockEntity(farmlandPos) instanceof IFarmland farmland) || farmland.getNutrient(nutrient) >= Config.fertilizeBelow)
        {
            return;
        }
        for (int applied = 0; applied < MAX_APPLICATIONS && farmland.getNutrient(nutrient) < Config.fertilizeTarget; applied++)
        {
            final int slot = bestFertilizerSlot(inventory, nutrient);
            if (slot < 0)
            {
                break;
            }
            final Fertilizer fertilizer = Fertilizer.get(inventory.getStackInSlot(slot));
            if (fertilizer == null)
            {
                break;
            }
            farmland.addNutrients(fertilizer);
            inventory.extractItem(slot, 1, false);
        }
    }

    /** Inventory slot of the fertilizer providing the most of {@code nutrient}, or {@code -1} if none. */
    private static int bestFertilizerSlot(final IItemHandler inventory, final NutrientType nutrient)
    {
        int bestSlot = -1;
        float bestValue = 0.0f;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
            {
                continue;
            }
            final Fertilizer fertilizer = Fertilizer.get(stack);
            if (fertilizer == null)
            {
                continue;
            }
            final float value = fertilizer.getNutrient(nutrient);
            if (value > bestValue)
            {
                bestValue = value;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
