package com.mctfc.mixin;

import com.mctfc.cook.ChefForgeTender;
import com.mctfc.cook.CookRecipes;
import com.mctfc.forge.ForgeController;
import com.mctfc.forge.ForgeTender;
import com.mctfc.forge.ForgeUserModule;
import com.mctfc.forge.HeatForgeBlockEntity;
import com.mctfc.furnace.FurnaceFuelScope;
import com.mctfc.furnace.FurnaceHeating;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the MineColonies <b>Chef</b> (Kitchen) cook with <b>TFC heating</b> instead of vanilla furnace smelting (which
 * never fires for TFC food — no vanilla recipe). The Chef is a request crafter ({@code AbstractEntityAIRequestSmelter}),
 * <b>not</b> an {@code AbstractEntityAIUsesFurnace} worker, so the {@code FurnaceBehavior} dispatcher doesn't reach it —
 * this mixin drives it directly. It hosts <b>two</b> paths, gated to the Kitchen ({@code building instanceof
 * BuildingKitchen}) so other request-smelters (Baker, …) keep vanilla behaviour, with {@code remap = false} (MineColonies'
 * own class/members):
 *
 * <ul>
 *   <li><b>Heat-forge path (primary)</b> — see docs/tfc-forge-multiblock.md §14. When the current recipe is
 *       FURNACE-intermediate and the Kitchen has heat-forges (our {@link ForgeUserModule}), we <b>replace</b> the native
 *       furnace dispatch ({@code executeCraftingAction}) with a device tend: stock the request's raw ingredient + firepit
 *       fuel, {@link ForgeTender#tend} each controller (the forge self-processes), and deliver the finished product
 *       straight to the request — {@code addDelivery} + craft-counter + {@code finalizeCraftingTask}, mirroring native
 *       {@code retrieveProductFromFurnace}. Grid crafting (sandwiches / composed dishes, non-FURNACE) falls through to
 *       native untouched.</li>
 *   <li><b>Legacy vanilla-furnace path</b> — until the furnace→forge switchover, a Kitchen with plain furnaces still
 *       cooks: {@link #mctfc$igniteTfcCooks} lights any TFC-cookable furnace via {@link FurnaceHeating}, and the fuel
 *       fallback below lets it burn firepit fuels. When forges are present the forge path takes over and this no-ops
 *       (empty {@code FurnaceUserModule}); it is retired with the switchover.</li>
 * </ul>
 */
@Mixin(value = AbstractEntityAIRequestSmelter.class, remap = false)
public abstract class MixinAbstractEntityAIRequestSmelter
{
    @Shadow protected IRecipeStorage currentRecipeStorage;
    @Shadow public IRequest<? extends PublicCrafting> currentRequest;

    @Shadow public abstract IAIState finalizeCraftingTask();

    @Unique private static final double MCTFC_XP_PER_DELIVERY = 5.0;

    @Unique private AbstractJobCrafter<?, ?> mctfc$job;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mctfc$captureJob(final AbstractJobCrafter smelteryJob, final CallbackInfo ci)
    {
        this.mctfc$job = smelteryJob;
    }

    /**
     * <b>Heat-forge Chef path.</b> Replaces the native furnace dispatch for FURNACE-intermediate recipes in a Kitchen
     * that has heat-forges: tends every controller (the block self-cooks) and delivers finished product to the request.
     * Returns {@code CRAFT} to keep tending, or {@link #finalizeCraftingTask()} when the batch is done. Falls through to
     * native (no cancel) for non-Kitchen / non-FURNACE / no-forge cases (so the legacy vanilla-furnace path still runs).
     */
    @Inject(method = "executeCraftingAction", at = @At("HEAD"), cancellable = true)
    private void mctfc$driveForgeChef(final int progress, final CallbackInfoReturnable<IAIState> cir)
    {
        final IBuilding building = mctfc$building();
        if (!(building instanceof BuildingKitchen))
        {
            return;
        }
        final IRecipeStorage recipe = this.currentRecipeStorage;
        if (recipe == null || recipe.getIntermediate() != Blocks.FURNACE)
        {
            return; // grid crafting (sandwiches / composed dishes) → native
        }
        final ForgeUserModule module = building.getFirstModuleOccurance(ForgeUserModule.class);
        final Level world = building.getColony().getWorld();
        if (module == null || world == null)
        {
            return;
        }
        final List<BlockPos> controllerPositions = module.getControllers(world);
        if (controllerPositions.isEmpty())
        {
            return; // no forge in this Kitchen — leave the legacy vanilla-furnace path to run
        }

        final AbstractEntityCitizen worker = mctfc$worker();
        if (worker == null)
        {
            cir.setReturnValue(AIWorkerState.START_WORKING);
            return;
        }
        final List<ForgeController> controllers = new ArrayList<>();
        for (final BlockPos pos : controllerPositions)
        {
            if (world.getBlockEntity(pos) instanceof HeatForgeBlockEntity be)
            {
                controllers.add(be);
            }
        }
        if (controllers.isEmpty())
        {
            cir.setReturnValue(AIWorkerState.START_WORKING); // forges not loaded yet
            return;
        }

        final ItemStack input = mctfc$firstInput(recipe);
        final ItemStack primary = recipe.getPrimaryOutput();
        if (input.isEmpty() || primary.isEmpty())
        {
            return;
        }

        // Target count: the base usually sets it, but init defensively from the request (like native retrieve does).
        if (mctfc$job.getMaxCraftingCount() == 0 && this.currentRequest != null)
        {
            mctfc$job.setMaxCraftingCount(this.currentRequest.getRequest().getCount());
        }

        final ChefForgeTender chef = new ChefForgeTender(world, building.getBuildingLevel(),
                List.of(worker.getInventoryCitizen()), mctfc$racks(building, world),
                input, CookRecipes.cookTemp(input), building);
        final ForgeTender tender = new ForgeTender(chef, chef);

        // Stage the request's raw ingredient + firepit fuel from the hut racks into the worker inventory.
        tender.stage(controllers);

        // Tend each forge (fuel / light / load / drain) and deliver the finished product to the request.
        for (final ForgeController controller : controllers)
        {
            for (final ItemStack finished : tender.tend(controller))
            {
                if (finished.isEmpty())
                {
                    continue;
                }
                if (finished.getItem() != primary.getItem())
                {
                    ForgeTender.insert(chef.racks(), finished); // not this request's product — stow it
                    continue;
                }
                final int produced = finished.getCount();
                final ItemStack delivery = finished.copy();
                final ItemStack leftover = ItemHandlerHelper.insertItem(worker.getInventoryCitizen(), finished, false);
                if (!leftover.isEmpty())
                {
                    ForgeTender.insert(chef.racks(), leftover);
                }
                if (this.currentRequest != null)
                {
                    this.currentRequest.addDelivery(delivery);
                }
                mctfc$job.setCraftCounter(mctfc$job.getCraftCounter() + produced);
                worker.getCitizenExperienceHandler().addExperience(MCTFC_XP_PER_DELIVERY);
                if (mctfc$job.getMaxCraftingCount() > 0 && mctfc$job.getCraftCounter() >= mctfc$job.getMaxCraftingCount())
                {
                    cir.setReturnValue(this.finalizeCraftingTask());
                    return;
                }
            }
        }
        cir.setReturnValue(AIWorkerState.CRAFT); // keep tending until the batch is done
    }

    /**
     * <b>Legacy vanilla-furnace path.</b> Light every Kitchen furnace that has a TFC-cookable raw food loaded but is still
     * idle/unlit. Runs at the head of the vanilla per-second acceleration event, so the same call then accelerates the
     * cook it just started. No-ops once the Kitchen's furnaces have become heat-forges (empty {@code FurnaceUserModule}).
     */
    @Inject(method = "accelerateFurnaces", at = @At("HEAD"))
    private void mctfc$igniteTfcCooks(final CallbackInfoReturnable<Boolean> cir)
    {
        final IBuilding building = mctfc$building();
        if (!(building instanceof BuildingKitchen))
        {
            return;
        }
        final Level world = building.getColony().getWorld();
        if (world == null)
        {
            return;
        }
        final int level = building.getBuildingLevel();
        for (final BlockPos pos : building.getFirstModuleOccurance(FurnaceUserModule.class).getFurnaces())
        {
            if (!WorldUtil.isBlockLoaded(world, pos) || !(world.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace))
            {
                continue;
            }
            final FurnaceProcess cap = FurnaceProcessCapability.get(furnace);
            if (cap == null || cap.phase() != FurnaceProcess.Phase.IDLE
                  || ((FurnaceBlockEntityAccessor) furnace).getLitTime() > 0
                  || !CookRecipes.isCookable(furnace.getItem(0)))
            {
                continue;
            }
            FurnaceHeating.igniteInPlace(furnace, cap, level, FurnaceHeating.COOK_FUEL);
        }
    }

    /**
     * When the Kitchen's fuel list is empty, allow any TFC firepit fuel so the Chef cooks out of the box (the player
     * can still restrict it via the list). Mirrors the Cook's empty-list fallback; other huts keep their own logic.
     */
    @Inject(method = "getAllowedFuel", at = @At("RETURN"), cancellable = true)
    private void mctfc$firepitFuelFallback(final CallbackInfoReturnable<List<ItemStack>> cir)
    {
        if (!(mctfc$building() instanceof BuildingKitchen))
        {
            return;
        }
        final List<ItemStack> current = cir.getReturnValue();
        if (current != null && !current.isEmpty())
        {
            return; // player configured fuels — respect them
        }
        final ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null)
        {
            return;
        }
        final List<ItemStack> firepit = new ArrayList<>();
        for (final Item item : tags.getTag(FurnaceFuelScope.COOK))
        {
            final ItemStack stack = new ItemStack(item);
            stack.setCount(stack.getMaxStackSize());
            firepit.add(stack);
        }
        if (!firepit.isEmpty())
        {
            cir.setReturnValue(firepit);
        }
    }

    @Unique
    private ItemStack mctfc$firstInput(final IRecipeStorage recipe)
    {
        for (final ItemStorage in : recipe.getCleanedInput())
        {
            if (!in.getItemStack().isEmpty())
            {
                return in.getItemStack();
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private AbstractEntityCitizen mctfc$worker()
    {
        return mctfc$job == null || mctfc$job.getCitizen() == null ? null
                : mctfc$job.getCitizen().getEntity().orElse(null);
    }

    @Unique
    private List<IItemHandler> mctfc$racks(final IBuilding building, final Level world)
    {
        final List<IItemHandler> handlers = new ArrayList<>();
        for (final BlockPos pos : building.getContainers())
        {
            final var be = world.getBlockEntity(pos);
            if (be != null)
            {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    @Unique
    private IBuilding mctfc$building()
    {
        return mctfc$job == null || mctfc$job.getCitizen() == null ? null : mctfc$job.getCitizen().getWorkBuilding();
    }
}
