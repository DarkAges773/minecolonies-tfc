package com.mctfc.mixin;

import com.mctfc.cook.ChefForgeTender;
import com.mctfc.cook.CookRecipes;
import com.mctfc.cook.CraftingAiAccess;
import com.mctfc.forge.ForgeController;
import com.mctfc.forge.ForgeTender;
import com.mctfc.forge.ForgeUserModule;
import com.mctfc.forge.HeatForgeBlockEntity;
import com.mctfc.furnace.FurnaceFuelScope;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the MineColonies <b>Chef</b> (Kitchen) cook with <b>TFC heating</b> on the hut's heat-forges instead of vanilla
 * furnace smelting (which never fires for TFC food — no vanilla recipe). The Chef is a request crafter
 * ({@code AbstractEntityAIRequestSmelter}), <b>not</b> an {@code AbstractEntityAIUsesFurnace} worker, so the
 * {@code FurnaceBehavior} dispatcher doesn't reach it — this mixin drives it directly. Gated to the Kitchen
 * ({@code building instanceof BuildingKitchen}) so other request-smelters (Baker, …) keep vanilla behaviour, with
 * {@code remap = false} (MineColonies' own class/members). See docs/tfc-forge-multiblock.md §14.
 *
 * <p>When the current recipe is FURNACE-intermediate and the Kitchen has heat-forges (our {@link ForgeUserModule}), we
 * <b>replace</b> the native furnace dispatch ({@code executeCraftingAction}) with a device tend: stock the request's raw
 * ingredient + firepit fuel, {@link ForgeTender#tend} each controller (the forge self-processes), and deliver the
 * finished product straight to the request — {@code addDelivery} + craft-counter + {@code finalizeCraftingTask},
 * mirroring native {@code retrieveProductFromFurnace}. Grid crafting (sandwiches / composed dishes, non-FURNACE) falls
 * through to native untouched. The empty-fuel-list fallback below lets the Chef burn TFC firepit fuels out of the box.
 */
@Mixin(value = AbstractEntityAIRequestSmelter.class, remap = false)
public abstract class MixinAbstractEntityAIRequestSmelter
{
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
     * Returns {@code CRAFT} to keep tending, or {@code finalizeCraftingTask} when the batch is done. Falls through to
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
        // Read the crafting-AI state via the accessor mixin on the declaring class (AbstractEntityAICrafting) — a
        // @Shadow of these inherited members fails to resolve at apply time in this dev setup (see CraftingAiAccess).
        final CraftingAiAccess access = (CraftingAiAccess) (Object) this;
        final IRecipeStorage recipe = access.mctfc$recipe();
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
        final IRequest<? extends PublicCrafting> request = access.mctfc$request();
        if (mctfc$job.getMaxCraftingCount() == 0 && request != null)
        {
            mctfc$job.setMaxCraftingCount(request.getRequest().getCount());
        }

        final ChefForgeTender chef = new ChefForgeTender(world, building.getBuildingLevel(),
                List.of(worker.getInventoryCitizen()), mctfc$racks(building, world),
                input, CookRecipes.cookTemp(input), building);
        final ForgeTender tender = new ForgeTender(chef, chef);

        // Stage the request's raw ingredient + firepit fuel from the hut racks into the worker inventory.
        tender.stage(controllers);

        // Phase 1 — DRAIN + DELIVER, before loading anything. Pull finished product from every forge and deliver it to
        // the request, counting each. Draining first (not the bundled ForgeTender.tend) is what lets us cap the reload to
        // the outstanding count below: otherwise we'd refill the freed slots on the very cycle the request completes and
        // abandon that batch cooking forever (§14).
        for (final ForgeController controller : controllers)
        {
            for (final ItemStack finished : tender.drain(controller))
            {
                if (finished.isEmpty())
                {
                    continue;
                }
                if (finished.getItem() != primary.getItem()
                        || mctfc$job.getCraftCounter() >= mctfc$job.getMaxCraftingCount())
                {
                    // not this request's product, or the request is already met — stow it rather than lose it
                    ForgeTender.insert(chef.racks(), finished);
                    continue;
                }
                final int produced = finished.getCount();
                final ItemStack delivery = finished.copy();
                final ItemStack leftover = ItemHandlerHelper.insertItem(worker.getInventoryCitizen(), finished, false);
                if (!leftover.isEmpty())
                {
                    ForgeTender.insert(chef.racks(), leftover);
                }
                if (request != null)
                {
                    request.addDelivery(delivery);
                }
                mctfc$job.setCraftCounter(mctfc$job.getCraftCounter() + produced);
                worker.getCitizenExperienceHandler().addExperience(MCTFC_XP_PER_DELIVERY);
            }
        }

        // Conclude BEFORE loading anything, so we never leave a batch cooking after the request is met.
        if (mctfc$job.getMaxCraftingCount() > 0 && mctfc$job.getCraftCounter() >= mctfc$job.getMaxCraftingCount())
        {
            cir.setReturnValue(access.mctfc$finalizeCraft());
            return;
        }

        // Phase 2 — LOAD only the deficit: (still needed) − (already cooking), so we never over-produce a batch that
        // would be abandoned when the request completes. stockAndLoad still refuels / lights / keep-warms every forge.
        // Count only THIS request's raw actually cooking — not every occupied heat slot. A foreign occupant (a player-
        // dropped high-melt ore the fuel can't finish) must NOT inflate inFlight, or it drives the budget to 0 and pins
        // the Chef at CRAFT forever with nothing loading (review CHEF-2a).
        int inFlight = 0;
        for (final ForgeController controller : controllers)
        {
            for (final ItemStack heat : controller.heatItems())
            {
                if (chef.accepts(heat))
                {
                    inFlight++;
                }
            }
        }
        int budget = Math.max(0, mctfc$job.getMaxCraftingCount() - mctfc$job.getCraftCounter() - inFlight);
        for (final ForgeController controller : controllers)
        {
            budget -= tender.stockAndLoad(controller, budget);
        }
        cir.setReturnValue(AIWorkerState.CRAFT); // keep tending until the batch is done
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
        // Keep only TFC firepit fuel. Native seeds the fuel list with [coal, charcoal] (out of firepit scope), so
        // without this the Chef's checkFurnaceFuel requests — and the forge can never burn — vanilla coal. Drop any
        // out-of-scope fuel; if the player HAS listed firepit fuels, respect exactly those.
        final List<ItemStack> current = cir.getReturnValue();
        if (current != null)
        {
            final List<ItemStack> listed = new ArrayList<>();
            for (final ItemStack stack : current)
            {
                if (stack.is(FurnaceFuelScope.COOK))
                {
                    listed.add(stack);
                }
            }
            if (!listed.isEmpty())
            {
                cir.setReturnValue(listed);
                return;
            }
        }
        // Nothing in scope (e.g. the default [coal, charcoal] seed) → offer every TFC firepit fuel so the Chef cooks
        // out of the box; the courier fulfils the StackList with whatever firepit fuel the colony has.
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
