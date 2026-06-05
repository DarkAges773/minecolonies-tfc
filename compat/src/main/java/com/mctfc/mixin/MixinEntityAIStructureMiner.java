package com.mctfc.mixin;

import com.mctfc.collapse.BuildAreaSupport;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two TFC fixes on the miner:
 *
 * <ol>
 *   <li><b>Ladder back-fill honours the fill-block setting</b> — {@code getLadderBackFillBlock()} is hardcoded to
 *       vanilla cobblestone (which landslides / is unobtainable in TFC); we redirect it to the configurable
 *       {@code FILL_BLOCK} setting ({@code getMainFillBlock()}), so the whole shaft uses the GUI-chosen block.</li>
 *   <li><b>Shaft excavation is collapse-proofed</b> — the miner's raw vertical-shaft digging runs in its own AI states
 *       ({@code doShaftMining}/{@code repairLadder}/{@code doShaftBuilding}) with {@code structurePlacer == null}, so it
 *       bypasses the schematic-area registration in {@code MixinAbstractEntityAIStructure}. We register the open-shaft
 *       column (the 7&times;7 {@code SHAFT_RADIUS} cross-section, offset toward the dig direction, from the current
 *       bottom up to the hut) into {@link BuildAreaSupport} while those states run, so TFC collapse is suppressed there
 *       too. The box self-clears via the registry TTL (and the next node's {@code resetCurrentStructure}).</li>
 * </ol>
 *
 * {@code @Mixin(remap = false)} — MineColonies' own class/members. {@code getMainFillBlock} is a private member of
 * {@code EntityAIStructureMiner} (shadowed); {@code getWorker()} is a <i>public</i> method inherited from
 * {@code AbstractEntityAIStructure}, so it's reached by a cast to the superclass rather than a shadow (a {@code @Shadow}
 * of the inherited method does not resolve against this subclass target).
 */
@Mixin(value = EntityAIStructureMiner.class, remap = false)
public class MixinEntityAIStructureMiner
{
    @Shadow
    private Block getMainFillBlock()
    {
        throw new AssertionError("shadow");
    }

    @Inject(method = "getLadderBackFillBlock", at = @At("RETURN"), cancellable = true)
    private void mctfc$ladderBackfillFromSetting(final CallbackInfoReturnable<Block> cir)
    {
        cir.setReturnValue(getMainFillBlock());
    }

    @Inject(method = "doShaftMining", at = @At("HEAD"))
    private void mctfc$shaftMineSupport(final CallbackInfoReturnable<IAIState> cir)
    {
        mctfc$registerShaftArea();
    }

    @Inject(method = "repairLadder", at = @At("HEAD"))
    private void mctfc$shaftRepairSupport(final CallbackInfoReturnable<IAIState> cir)
    {
        mctfc$registerShaftArea();
    }

    @Inject(method = "doShaftBuilding", at = @At("HEAD"))
    private void mctfc$shaftBuildSupport(final CallbackInfoReturnable<IAIState> cir)
    {
        mctfc$registerShaftArea();
    }

    /** Register the open-shaft column as a collapse-proof build area (refreshed each shaft step; TTL-cleared). */
    private void mctfc$registerShaftArea()
    {
        final AbstractEntityCitizen worker = ((AbstractEntityAIStructure) (Object) this).getWorker();
        if (worker == null)
        {
            return;
        }
        final ICitizenData data = worker.getCitizenData();
        if (data == null)
        {
            return;
        }
        final IBuilding workBuilding = data.getWorkBuilding();
        if (!(workBuilding instanceof BuildingMiner miner))
        {
            return;
        }
        final BlockPos ladder = miner.getLadderLocation();
        final BlockPos cobble = miner.getCobbleLocation();
        if (ladder == null || cobble == null)
        {
            return;
        }
        final Level level = worker.level();
        final int bottom = WorkerUtil.getLastLadder(ladder, level);
        final int top = miner.getPosition().getY();

        // The shaft cross-section is offset from the ladder toward the dig direction (away from the cobble), matching
        // the AI's own scan; pad by 2 to cover the water-removal margin / edge mining.
        final BlockPos dir = ladder.subtract(cobble);
        final int xOff = EntityAIStructureMiner.SHAFT_RADIUS * dir.getX();
        final int zOff = EntityAIStructureMiner.SHAFT_RADIUS * dir.getZ();
        final int r = EntityAIStructureMiner.SHAFT_RADIUS + 2;
        final int cx = ladder.getX() + xOff;
        final int cz = ladder.getZ() + zOff;

        final BoundingBox box = BoundingBox.fromCorners(
                new BlockPos(cx - r, Math.min(bottom, top) - 2, cz - r),
                new BlockPos(cx + r, Math.max(bottom, top) + 1, cz + r));
        BuildAreaSupport.register(worker.getUUID(), level.dimension(), box, level.getGameTime());
    }
}
