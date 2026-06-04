package com.mctfc.food;

import com.mctfc.Config;
import com.mctfc.MineColoniesTFC;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.FoodTrait;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Colony-storage food preservation. TFC food decays from a {@code creationDate} (stored in a Forge capability),
 * modified by {@link FoodTrait}s (the same mechanism TFC's sealed vessels / coolers use). We register one extra
 * trait, {@code mctfc:colony_storage}, whose decay modifier is read <b>live</b> from {@link Config#foodColonyStorageDecay}
 * (a {@code Supplier<Float>}, exactly how TFC wires its own config-driven traits) — so the preservation strength is
 * tunable at runtime (0 = frozen, 1 = no preservation) without a restart.
 *
 * <p>The trait is applied to food while it sits in a <b>colony-owned</b> rack/warehouse and stripped when the food
 * is withdrawn (see {@code MixinRackInventory}), so withdrawn food and player-placed racks decay normally. Because
 * decay is computed from {@code creationDate} + trait modifiers at read time, the preservation keeps working even
 * while the colony chunk is unloaded (a tick-based clock nudge would not).
 */
public final class FoodPreservation
{
    private FoodPreservation() {}

    /** The colony-storage preservation trait; null until {@link #onCommonSetup} registers it. */
    public static FoodTrait COLONY_STORAGE;

    public static void onCommonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(FoodPreservation::register);
    }

    private static synchronized void register()
    {
        if (COLONY_STORAGE != null)
        {
            return;
        }
        COLONY_STORAGE = FoodTrait.register(
                new ResourceLocation(MineColoniesTFC.MODID, "colony_storage"),
                new FoodTrait(() -> Config.foodColonyStorageDecay, "mctfc.food_trait.colony_storage"));
        MineColoniesTFC.LOGGER.info("Registered colony-storage food-preservation trait (decay x{}).", Config.foodColonyStorageDecay);
    }

    /**
     * Mark a stored TFC food stack as colony-preserved, in place (mutates the food capability). No-op for non-food,
     * empty stacks, or stacks that already carry the trait.
     */
    public static void markStored(final ItemStack stack)
    {
        if (COLONY_STORAGE == null || stack.isEmpty())
        {
            return;
        }
        final IFood food = FoodCapability.get(stack);
        if (food != null && !food.hasTrait(COLONY_STORAGE))
        {
            FoodCapability.applyTrait(food, COLONY_STORAGE);
        }
    }

    /**
     * Remove the colony-preservation mark when food leaves colony storage. Returns the (possibly same) stack;
     * no-op for non-food / stacks without the trait.
     */
    public static ItemStack clearStored(final ItemStack stack)
    {
        if (COLONY_STORAGE == null || stack.isEmpty())
        {
            return stack;
        }
        if (FoodCapability.hasTrait(stack, COLONY_STORAGE))
        {
            return FoodCapability.removeTrait(stack, COLONY_STORAGE);
        }
        return stack;
    }
}
