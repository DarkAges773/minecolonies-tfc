package com.mctfc.settings;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.MinimumStockModule;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A tiny registry for seeding <b>default minimum-stock entries</b> onto MineColonies buildings the first time
 * they're built, so e.g. a fresh Smeltery already keeps a stack of molds without the player configuring it.
 * Register at mod setup; {@code MixinAbstractBuilding} calls {@link #seed} from {@code onUpgradeComplete} at
 * level 1 (the first hook where the building's level is high enough for the min-stock module to accept an entry).
 *
 * <p>Seeding only at first-build (level 1) means it's a one-time default: it isn't re-applied on later upgrades,
 * and if the player removes the entry it stays removed. The quantity is in <b>stacks</b> (MineColonies' min-stock
 * keeps {@code quantity × maxStackSize} items). Parallels {@link BuildingSettings}; a new default is one
 * {@link #register} call.
 */
public final class BuildingStockSeeds
{
    private BuildingStockSeeds() {}

    private record Seed(Predicate<IBuilding> appliesTo, Supplier<ItemStack> stack, int stacks) {}

    private static final List<Seed> SEEDS = new ArrayList<>();

    /**
     * Register a default minimum-stock entry to seed onto every building matching {@code appliesTo} when it's
     * first built. {@code stack} supplies the item; {@code stacks} is the number of stacks to keep.
     */
    public static void register(final Predicate<IBuilding> appliesTo, final Supplier<ItemStack> stack, final int stacks)
    {
        SEEDS.add(new Seed(appliesTo, stack, stacks));
    }

    /** Seed matching defaults onto a freshly-built building (skips any item the building already stocks). */
    public static void seed(final IBuilding building)
    {
        final MinimumStockModule module = building.getModule(MinimumStockModule.class);
        if (module == null)
        {
            return;
        }
        for (final Seed entry : SEEDS)
        {
            if (!entry.appliesTo().test(building))
            {
                continue;
            }
            final ItemStack stack = entry.stack().get();
            if (!stack.isEmpty() && !module.isStocked(stack))
            {
                module.addMinimumStock(stack, entry.stacks());
            }
        }
    }
}
