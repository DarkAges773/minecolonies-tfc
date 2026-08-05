package com.structurizereplacements.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtils;
import com.ldtteam.structurize.client.BlueprintRenderer;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.PlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Map;

/**
 * Client-only: makes the Structurize placement preview render substituted blocks.
 *
 * <p>{@code BlueprintRenderer#init} bakes the hologram by iterating {@code blueprint.getBlockInfoAsList()}
 * and rendering each {@code BlockInfo.getState()} via {@code BlockRenderDispatcher#renderBatched} — the
 * fake level is only the tint/light context, NOT the source of the rendered block. So we redirect that
 * {@code getState()} read to apply substitution; that's what actually changes the previewed block model.
 * ({@link MixinBlueprintBlockAccess} additionally substitutes the context reads so neighbour-based tint
 * stays consistent.)
 *
 * <p>{@code remap = false}: {@code init} and {@code BlockInfo#getState} are Structurize's own members,
 * stable across dev/production; only the class names in the descriptor are involved, which are stable too.
 */
@Mixin(BlueprintRenderer.class)
public class MixinBlueprintRenderer
{
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/util/BlockInfo;getState()Lnet/minecraft/world/level/block/state/BlockState;"),
            remap = false)
    private BlockState structurizereplacements$substituteRenderedBlock(final BlockInfo blockInfo)
    {
        return BlockSubstitutions.applyState(blockInfo.getState(), PlacementChoices.client());
    }

    /**
     * Substitute the preview's <b>block entities</b> (chests, etc.). The static block model is handled by the
     * {@code getState} redirect above, but block entities render via their own {@code BlockEntityRenderer} from the
     * BE <i>instance</i> built in {@code BlueprintUtils.instantiateTileEntities} — which keys the BE type off the
     * blueprint's stored TE-NBT id, so a substituted chest still previews as the original. We can't substitute the
     * shared {@code constructTileEntity} (it's also used by {@code Blueprint#getBlockEntity} on build paths), so we
     * post-process here — the only caller of {@code instantiateTileEntities} is this (client preview) renderer.
     *
     * <p>For each preview BE whose block was substituted to a <i>different</i> entity-block, we swap in a fresh BE of
     * the substituted type (so the right renderer + model is used); the BE's state carries the copied facing/chest-type.
     * If it substituted to a non-BE block (or BE creation fails) we drop the preview TE — the (already substituted)
     * block model covers it.
     */
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/blueprints/v1/BlueprintUtils;instantiateTileEntities(Lcom/ldtteam/structurize/blueprints/v1/Blueprint;Lnet/minecraft/world/level/Level;Ljava/util/Map;)Ljava/util/Map;"),
            remap = false)
    private Map<BlockPos, BlockEntity> structurizereplacements$substitutePreviewTileEntities(final Blueprint blueprint, final Level beLevel, final Map<BlockPos, ModelData> teModelData)
    {
        final Map<BlockPos, BlockEntity> tileEntities = BlueprintUtils.instantiateTileEntities(blueprint, beLevel, teModelData);
        final Map<BlockPos, BlockInfo> infos = blueprint.getBlockInfoAsMap();
        final var choices = PlacementChoices.client();

        for (final BlockPos pos : new ArrayList<>(tileEntities.keySet()))
        {
            final BlockInfo info = infos.get(pos);
            if (info == null)
            {
                continue;
            }
            final BlockState orig = info.getState();
            final BlockState sub = BlockSubstitutions.applyState(orig, choices);
            if (sub.getBlock() == orig.getBlock())
            {
                continue; // block unchanged -> the existing preview BE is already correct
            }
            if (sub.getBlock() instanceof EntityBlock entityBlock)
            {
                final BlockEntity be = entityBlock.newBlockEntity(pos, sub);
                if (be != null)
                {
                    if (beLevel != null)
                    {
                        be.setLevel(beLevel);
                    }
                    teModelData.put(pos, be.getModelData());
                    tileEntities.put(pos, be);
                    continue;
                }
            }
            tileEntities.remove(pos);
            teModelData.remove(pos);
        }
        return tileEntities;
    }
}
