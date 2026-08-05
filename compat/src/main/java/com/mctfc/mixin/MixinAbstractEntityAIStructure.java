package com.mctfc.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.mctfc.collapse.BuildAreaSupport;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Registers the world bounds of the schematic a structure worker (builder / miner / quarrier — all share this base) is
 * currently building, so TFC collapse is suppressed there for the duration (see {@link BuildAreaSupport} +
 * {@code MixinSupport}). The schematic lives on the shared {@code structurePlacer} tuple for the whole build, from
 * {@code setStructurePlacer} until {@code resetCurrentStructure}.
 *
 * <p>We refresh on every {@code structureStep} (idempotent map put — also self-heals after a reload) and clear on
 * {@code resetCurrentStructure}, the single point MineColonies nulls the structure on completion <i>and</i> on every
 * abandon/error path. {@code @Mixin(remap = false)} — MineColonies' own class/members.
 */
@Mixin(value = AbstractEntityAIStructure.class, remap = false)
public abstract class MixinAbstractEntityAIStructure
{
    /** Pad the schematic box so edge-mining / access tunnels are covered too. */
    private static final int MCTFC_PAD = 1;

    /**
     * World blocks in this tag are never stripped by the builder's CLEAR phase. Datapack-driven
     * ({@code data/mctfc/tags/blocks/builder_dont_clear.json}); empty by default, so the protection is opt-in.
     */
    private static final TagKey<Block> MCTFC$DONT_CLEAR =
      TagKey.create(Registries.BLOCK, new ResourceLocation("mctfc", "builder_dont_clear"));

    @Shadow
    protected Tuple structurePlacer;

    @Shadow
    public abstract AbstractEntityCitizen getWorker();

    @Inject(method = "structureStep", at = @At("HEAD"))
    private void mctfc$registerBuildArea(final CallbackInfoReturnable<IAIState> cir)
    {
        if (structurePlacer == null)
        {
            return;
        }
        final Object handlerObj = structurePlacer.getB();
        if (!(handlerObj instanceof BuildingStructureHandler<?, ?> handler) || !handler.hasBluePrint())
        {
            return;
        }
        final Blueprint blueprint = handler.getBluePrint();
        if (blueprint == null)
        {
            return;
        }
        final BlockPos a = handler.getProgressPosInWorld(BlockPos.ZERO);
        final BlockPos b = handler.getProgressPosInWorld(new BlockPos(blueprint.getSizeX() - 1, blueprint.getSizeY() - 1, blueprint.getSizeZ() - 1));
        final BoundingBox box = BoundingBox.fromCorners(a, b).inflatedBy(MCTFC_PAD);

        final AbstractEntityCitizen worker = getWorker();
        if (worker != null)
        {
            BuildAreaSupport.register(worker.getUUID(), worker.level().dimension(), box, worker.level().getGameTime());
        }
    }

    /**
     * Protect tagged world blocks from being torn out by the builder's CLEAR phase. {@code skipClearing} is the
     * single gate the CLEAR phase ({@code Operation.BLOCK_REMOVAL}) consults before removing each existing world
     * block where the blueprint cell would clear it; returning {@code true} leaves the block in place. This is the
     * <i>air-strip</i> path only — normal placement (the blueprint laying a real block over the spot) is untouched,
     * and the demolition (REMOVE) phase has its own {@code skipRemoval} gate we deliberately don't hook. The quarrier
     * overrides {@code skipClearing} but calls {@code super} first and returns early when it's {@code true}, so this
     * base-class injection covers the quarrier too. {@code @Mixin(remap = false)} — MineColonies' own method.
     */
    @Inject(method = "skipClearing", at = @At("HEAD"), cancellable = true)
    private void mctfc$protectFromClear(final BlueprintPositionInfo info, final BlockPos pos,
      final IStructureHandler handler, final CallbackInfoReturnable<Boolean> cir)
    {
        if (handler.getWorld().getBlockState(pos).is(MCTFC$DONT_CLEAR))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "resetCurrentStructure", at = @At("HEAD"))
    private void mctfc$unregisterBuildArea(final CallbackInfo ci)
    {
        final AbstractEntityCitizen worker = getWorker();
        if (worker != null)
        {
            BuildAreaSupport.unregister(worker.getUUID());
        }
    }
}
