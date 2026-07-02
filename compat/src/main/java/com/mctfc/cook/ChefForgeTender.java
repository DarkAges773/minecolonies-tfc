package com.mctfc.cook;

import com.mctfc.forge.ForgeTender;
import com.mctfc.furnace.FurnaceFuel;
import com.mctfc.furnace.FurnaceFuelScope;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;

/**
 * {@link ForgeTender} Context + Policy for the <b>Chef</b> (Kitchen request-crafter), mirroring the Cook's forge policy
 * but keyed to the <b>current craft's recipe input</b> rather than the whole restaurant menu: it feeds only the
 * request's raw ingredient, at that ingredient's TFC heating temperature, burning Kitchen <b>firepit</b> fuels
 * ({@link FurnaceFuelScope#COOK}, list-constrained like the Cook). Built fresh per craft cycle (the input changes per
 * request) by {@code MixinAbstractEntityAIRequestSmelter}. See {@code docs/tfc-forge-multiblock.md} §14.
 */
public class ChefForgeTender implements ForgeTender.Context, ForgeTender.Policy
{
    /** Fuel staged per free heat slot. */
    private static final int FUEL_PER_SLOT = 2;
    /** Raw input staged per free heat slot (a small carry buffer so the worker reloads several times). */
    private static final int INPUT_PER_SLOT = 8;

    private final Level world;
    private final int buildingLevel;
    private final List<IItemHandler> inventory;
    private final List<IItemHandler> racks;
    private final ItemStack input;
    private final float cookTemp;
    private final IBuilding building;

    public ChefForgeTender(final Level world, final int buildingLevel, final List<IItemHandler> inventory,
            final List<IItemHandler> racks, final ItemStack input, final float cookTemp, final IBuilding building)
    {
        this.world = world;
        this.buildingLevel = buildingLevel;
        this.inventory = inventory;
        this.racks = racks;
        this.input = input;
        this.cookTemp = cookTemp;
        this.building = building;
    }

    // --- ForgeTender.Context ------------------------------------------------------------------------------

    @Override
    public Level world()
    {
        return world;
    }

    @Override
    public int buildingLevel()
    {
        return buildingLevel;
    }

    @Override
    public List<IItemHandler> inventory()
    {
        return inventory;
    }

    @Override
    public List<IItemHandler> racks()
    {
        return racks;
    }

    // --- ForgeTender.Policy -------------------------------------------------------------------------------

    /** Only the current recipe's raw ingredient (by item — the raw food carries TFC decay NBT the recipe input lacks). */
    @Override
    public boolean accepts(final ItemStack stack)
    {
        return !stack.isEmpty() && !input.isEmpty() && stack.getItem() == input.getItem();
    }

    @Override
    public float requiredTemp(final ItemStack stack)
    {
        return cookTemp;
    }

    /**
     * A TFC <b>firepit</b> fuel ({@link FurnaceFuelScope#COOK}) the player permits via the hut's fuel list — identical to
     * the Cook's rule, so the Kitchen never burns coals and the list only constrains once it names a firepit fuel.
     */
    @Override
    public boolean fuelAllowed(final ItemStack stack)
    {
        if (!FurnaceFuel.isFuel(stack) || !stack.is(FurnaceFuelScope.COOK))
        {
            return false;
        }
        final ItemListModule allowed = building == null ? null
                : building.getModuleMatching(ItemListModule.class, m -> m.getId().equals(FUEL_LIST));
        if (allowed == null)
        {
            return true;
        }
        final List<ItemStorage> list = allowed.getList();
        boolean constrains = false;
        for (final ItemStorage entry : list)
        {
            if (FurnaceFuel.isFuel(entry.getItemStack()))
            {
                constrains = true;
                break;
            }
        }
        return !constrains || list.contains(new ItemStorage(stack));
    }

    @Override
    public int fuelPerSlot()
    {
        return FUEL_PER_SLOT;
    }

    @Override
    public int inputPerSlot()
    {
        return INPUT_PER_SLOT;
    }
}
