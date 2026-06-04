package com.mctfc.farming;

import com.mctfc.Config;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blockentities.IFarmland;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.util.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

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
        if (nutrient == null || !(level.getBlockEntity(farmlandPos) instanceof IFarmland farmland))
        {
            return;
        }
        if (farmland.getNutrient(nutrient) >= Config.fertilizeBelow)
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
