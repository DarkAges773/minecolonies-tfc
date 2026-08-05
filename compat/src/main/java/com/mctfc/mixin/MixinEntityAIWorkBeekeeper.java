package com.mctfc.mixin;

import com.mctfc.firmalife.FlBeekeeping;
import com.mctfc.settings.BeeFrameSetting;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.dries007.tfc.common.TFCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.BEEKEEPER_HARVEST;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

/**
 * Makes the MineColonies Beekeeper service a <b>FirmaLife</b> apiary. MineColonies' beekeeper is hardcoded to the
 * vanilla bee model at every layer (a {@code BeehiveBlock} in {@code #minecraft:beehives}, a
 * {@code BeehiveBlockEntity}, {@code honey_level} 0–5, {@code Bee} entities, shears/glass-bottle harvest,
 * worker-driven breeding). FirmaLife shares none of it — its hive is a TFC device with an integer honey counter,
 * frame-and-capability bees, a {@code firmalife:jar/honey} product (consuming {@code tfc:empty_jar}), knife-scraped
 * {@code firmalife:beeswax}, and <b>autonomous</b> breeding. Several vanilla paths would also {@code ClassCastException}
 * on the FirmaLife BE.
 *
 * <p>Three HEAD-cancellable injects, each a no-op unless FirmaLife is loaded <b>and</b> the building is a FirmaLife
 * apiary, take over for FirmaLife hives and otherwise fall through to the unchanged vanilla code:
 * <ul>
 *   <li>{@code prepareForHerding} — request {@code tfc:empty_jar} (honey), a TFC knife (only if any wax slot is
 *       enabled), and {@code firmalife:beehive_frame} (only if a hive has an empty slot); skip the vanilla
 *       shears/glass-bottle requests.</li>
 *   <li>{@code decideWhatToDo} — a hive needing service → {@code BEEKEEPER_HARVEST}, else {@code START_WORKING};
 *       bypasses the vanilla flower-list / no-bees / breeding branches (which key on {@code Bee} entities and a hut
 *       flower list FirmaLife doesn't use), so no misleading blocking interactions fire.</li>
 *   <li>{@code harvestHoney} — the "service the hive" visit: harvest honey → jars, scrape queened frames in the
 *       designated wax slots → beeswax (with a knife), and top up empty frame slots from stock.</li>
 * </ul>
 * The vanilla {@code getHiveToHarvest}/{@code getBeesInHives} (which read/cast as vanilla and would crash on a
 * FirmaLife BE) are never reached on the FirmaLife path. {@code @Mixin(remap = false)} — MineColonies' own class.
 * No mixed-world support: a FirmaLife apiary is assumed. See {@code docs/tfc-beekeeper-worker.md}.
 */
@Mixin(value = EntityAIWorkBeekeeper.class, remap = false)
public abstract class MixinEntityAIWorkBeekeeper
{
    private static final String FIRMALIFE = "firmalife";

    /** Matches the vanilla beekeeper's EXP_PER_HARVEST. */
    private static final double FL_EXP_PER_HARVEST = 5.0;

    /** Matches the vanilla beekeeper's DECIDING_DELAY. */
    private static final int FL_DECIDING_DELAY = 40;

    @Inject(method = "prepareForHerding", at = @At("HEAD"), cancellable = true)
    private void mctfc$flPrepare(final CallbackInfoReturnable<IAIState> cir)
    {
        if (!ModList.get().isLoaded(FIRMALIFE))
        {
            return;
        }
        final BuildingBeekeeper building = (BuildingBeekeeper) ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        final Level world = mctfc$world();
        if (!FlBeekeeping.hasFlHive(world, building.getHives()))
        {
            return; // not a FirmaLife apiary → let vanilla request shears/glass bottles
        }

        final AbstractEntityAIBasic ai = (AbstractEntityAIBasic) (Object) this;
        ai.setDelay(FL_DECIDING_DELAY);

        final int level = Math.max(1, building.getBuildingLevel());
        ai.checkIfRequestForItemExistOrCreateAsync(FlBeekeeping.emptyJarStack(1), level * 2, 1);
        if (mctfc$waxMask(building) != 0)
        {
            ai.checkIfRequestForTagExistOrCreateAsync(TFCTags.Items.KNIVES, 1);
        }
        if (FlBeekeeping.anyEmptyFrameSlot(world, building.getHives()))
        {
            ai.checkIfRequestForItemExistOrCreateAsync(FlBeekeeping.frameStack(1), BeeFrameSetting.SLOTS, 1);
        }
        cir.setReturnValue(DECIDE);
    }

    @Inject(method = "decideWhatToDo", at = @At("HEAD"), cancellable = true)
    private void mctfc$flDecide(final CallbackInfoReturnable<IAIState> cir)
    {
        if (!ModList.get().isLoaded(FIRMALIFE))
        {
            return;
        }
        final BuildingBeekeeper building = (BuildingBeekeeper) ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        final Level world = mctfc$world();
        if (!FlBeekeeping.hasFlHive(world, building.getHives()))
        {
            return;
        }

        final AbstractEntityAIBasic ai = (AbstractEntityAIBasic) (Object) this;
        ai.setDelay(FL_DECIDING_DELAY);
        final BlockPos hive = FlBeekeeping.firstServiceableHive(world, building.getHives(), mctfc$waxMask(building));
        cir.setReturnValue(hive != null ? BEEKEEPER_HARVEST : START_WORKING);
    }

    @Inject(method = "harvestHoney", at = @At("HEAD"), cancellable = true)
    private void mctfc$flHarvest(final CallbackInfoReturnable<IAIState> cir)
    {
        if (!ModList.get().isLoaded(FIRMALIFE))
        {
            return;
        }
        final BuildingBeekeeper building = (BuildingBeekeeper) ((AbstractEntityAIBasicInvoker) this).mctfc$building();
        final Level world = mctfc$world();
        final int waxMask = mctfc$waxMask(building);
        final BlockPos hive = FlBeekeeping.firstServiceableHive(world, building.getHives(), waxMask);
        if (hive == null)
        {
            return; // nothing FirmaLife to do → let vanilla harvest run (vanilla hives)
        }

        if (!((AbstractEntityAIBasicInvoker) this).mctfc$walkToWorkPos(hive))
        {
            cir.setReturnValue(((AbstractEntityAIBasic) (Object) this).getState());
            return;
        }

        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) this).mctfc$worker();
        worker.swing(InteractionHand.MAIN_HAND);
        boolean did = false;

        // 1) Honey: one jar of firmalife:jar/honey per honey, limited by the empty jars on hand.
        final int jars = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), FlBeekeeping::isEmptyJar);
        if (jars > 0)
        {
            final int taken = FlBeekeeping.takeHoney(world, hive, jars);
            if (taken > 0)
            {
                InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), FlBeekeeping.emptyJarStack(taken));
                InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(FlBeekeeping.honeyJar(taken), worker.getItemHandlerCitizen());
                did = true;
            }
        }

        // 2) Wax: scrape queened frames in the designated slots (needs a knife; each scrape consumes the queen).
        if (waxMask != 0)
        {
            int slot;
            while ((slot = FlBeekeeping.queenWaxSlot(world, hive, waxMask)) != -1)
            {
                final int knifeSlot = InventoryUtils.findFirstSlotInItemHandlerWith(
                  worker.getInventoryCitizen(), s -> s.is(TFCTags.Items.KNIVES));
                if (knifeSlot == -1)
                {
                    break; // no knife → can't scrape
                }
                CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, knifeSlot);
                FlBeekeeping.putFreshFrame(world, hive, slot);
                InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(FlBeekeeping.beeswax(1), worker.getItemHandlerCitizen());
                CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
                did = true;
            }
        }

        // 3) Refill: top up empty frame slots from the worker's stock so every slot can breed.
        int empty;
        while ((empty = FlBeekeeping.emptyFrameSlot(world, hive)) != -1)
        {
            if (!InventoryUtils.tryRemoveStackFromItemHandler(worker.getInventoryCitizen(), FlBeekeeping.frameStack(1)))
            {
                break; // out of empty frames
            }
            FlBeekeeping.putFreshFrame(world, hive, empty);
            did = true;
        }

        if (did)
        {
            worker.getCitizenExperienceHandler().addExperience(FL_EXP_PER_HARVEST);
            ((AbstractEntityAIBasic) (Object) this).incrementActionsDoneAndDecSaturation();
        }
        cir.setReturnValue(START_WORKING);
    }

    private Level mctfc$world()
    {
        return ((AbstractAISkeletonAccessor) this).mctfc$worker().level();
    }

    /** The Beekeeper's wax-frame bitmask setting (0 when absent, e.g. FirmaLife not present at attach time). */
    private int mctfc$waxMask(final IBuilding building)
    {
        return building.getSettingValueOrDefault(BeeFrameSetting.KEY, 0);
    }
}
