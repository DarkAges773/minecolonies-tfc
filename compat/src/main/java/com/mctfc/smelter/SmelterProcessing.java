package com.mctfc.smelter;

import com.mctfc.smelter.SmelterRecipes.Output;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * The smelter's <b>completion</b> step, run by the furnace itself (via {@code MixinAbstractFurnaceBlockEntity})
 * when a melt's {@code litTime} burns out — not by the worker. It turns the ore + mold sitting in the furnace
 * into the finished casting <b>in place</b>: the result-slot mold is filled with the cast ingot heated to just
 * below melting (iron instead becomes a hot raw bloom), and the input ore is consumed. The worker later hauls
 * the finished item out to the racks.
 */
public final class SmelterProcessing implements com.mctfc.furnace.FurnaceProcessing
{
    /** The {@link com.mctfc.furnace.FurnaceProcessings} kind the smelter stamps onto a furnace it loads. */
    public static final String KIND = "smelt";

    private static final int INPUT  = 0;
    private static final int RESULT = 2;
    /** Heat the just-cast ingot/bloom to this many °C below its melting point — hot, but solidified. */
    private static final float BELOW_MELT = 1.0f;

    @Override
    public void complete(final AbstractFurnaceBlockEntity furnace)
    {
        completeMelt(furnace);
    }

    /** Finish the melt loaded in this furnace, placing the result in its result slot. */
    public static void completeMelt(final AbstractFurnaceBlockEntity furnace)
    {
        final ItemStack ore = furnace.getItem(INPUT);
        final Output output = SmelterRecipes.outputFor(ore);
        if (output == null)
        {
            return; // nothing recognisable to finish
        }
        final HeatingRecipe recipe = HeatingRecipe.getRecipe(ore);
        final float meltTemp = recipe != null ? recipe.getTemperature() : 1000f;
        final float temp = Math.max(1f, meltTemp - BELOW_MELT);

        if (output.bloom())
        {
            final ItemStack bloom = new ItemStack(SmelterRecipes.item(output.result()));
            HeatCapability.setTemperature(bloom, temp);
            furnace.setItem(RESULT, bloom);
        }
        else
        {
            final Fluid fluid = recipe != null && !recipe.getDisplayOutputFluid().isEmpty() ? recipe.getDisplayOutputFluid().getFluid() : null;
            final ItemStack mold = furnace.getItem(RESULT).copy();
            final IFluidHandlerItem handler = mold.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (handler != null && fluid != null)
            {
                handler.fill(new FluidStack(fluid, SmelterRecipes.UNITS_PER_OUTPUT), IFluidHandler.FluidAction.EXECUTE);
                final ItemStack filled = handler.getContainer();
                HeatCapability.setTemperature(filled, temp);
                furnace.setItem(RESULT, filled);
            }
            // else: leave the empty mold in the result slot (fallback — worker still hauls it out)
        }
        furnace.setItem(INPUT, ItemStack.EMPTY);
        furnace.setChanged();
    }
}
