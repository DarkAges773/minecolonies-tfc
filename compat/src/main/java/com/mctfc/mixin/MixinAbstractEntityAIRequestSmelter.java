package com.mctfc.mixin;

import com.mctfc.cook.CookRecipes;
import com.mctfc.furnace.FurnaceFuelScope;
import com.mctfc.furnace.FurnaceHeating;
import com.mctfc.furnace.FurnaceProcess;
import com.mctfc.furnace.FurnaceProcessCapability;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAIRequestSmelter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
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
 * Makes the MineColonies <b>Chef</b> (Kitchen) drive its furnaces with <b>TFC heating</b> — the same mechanics the
 * dining-hall Cook uses (see docs/tfc-furnace-workers.md §6) — instead of vanilla furnace smelting, which never
 * fires for TFC food (no vanilla recipe → the furnace never lights or produces).
 *
 * <p>The Chef is a request crafter ({@code AbstractEntityAIRequestSmelter}), <b>not</b> an
 * {@code AbstractEntityAIUsesFurnace} worker, so the {@code FurnaceBehavior} dispatcher doesn't reach it. Its own
 * furnace states already load the input + fuel and haul the result out of the result slot; the only missing piece is
 * <b>ignition + production</b>, which we supply here, reusing the shared {@link FurnaceHeating} /
 * {@link com.mctfc.cook.CookProcessing} framework:
 *
 * <ul>
 *   <li><b>Ignite</b> — piggy-backing on the per-second {@code accelerateFurnaces} background event: any Kitchen
 *       furnace holding a TFC-cookable raw food in its input slot, idle and unlit, is lit via {@link FurnaceHeating}
 *       (fuel pool + cook kind + {@code litTime}). The same event's acceleration loop then speeds it, the furnace
 *       finishes each piece itself ({@code MixinAbstractFurnaceBlockEntity} → {@code CookProcessing}, one piece per
 *       cycle, re-igniting for the rest of a loaded stack), and the Chef's {@code retrieveProductFromFurnace} hauls
 *       the cooked food out to fulfil the request — all unchanged.</li>
 *   <li><b>Fuel</b> — the Chef fetches/stocks fuel from its hut fuel list; when that list is empty we fall back to
 *       TFC <b>firepit</b> fuels ({@link FurnaceFuelScope#COOK}) so the Chef cooks out of the box (mirroring the
 *       Cook's fallback), while the fuel-list GUI is scoped to firepit fuels for the Kitchen.</li>
 * </ul>
 *
 * <p>Gated to the Kitchen ({@code building instanceof BuildingKitchen}) so the other request-smelters (Baker, …)
 * keep vanilla behaviour untouched. {@code remap = false} — MineColonies' own class/methods.
 */
@Mixin(value = AbstractEntityAIRequestSmelter.class, remap = false)
public abstract class MixinAbstractEntityAIRequestSmelter
{
    @Unique private AbstractJobCrafter<?, ?> mctfc$job;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mctfc$captureJob(final AbstractJobCrafter smelteryJob, final CallbackInfo ci)
    {
        this.mctfc$job = smelteryJob;
    }

    /**
     * Light every Kitchen furnace that has a TFC-cookable raw food loaded but is still idle/unlit. Runs at the head
     * of the vanilla per-second acceleration event, so the same call then accelerates the cook it just started.
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
    private IBuilding mctfc$building()
    {
        return mctfc$job == null || mctfc$job.getCitizen() == null ? null : mctfc$job.getCitizen().getWorkBuilding();
    }
}
