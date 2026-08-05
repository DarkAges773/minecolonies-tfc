package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.BlockPlacementResult;
import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.ldtteam.structurize.util.ChangeStorage;
import com.structurizereplacements.integration.colony.ColonyIntegration;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.SubstitutedMatch;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.DomumMaterialRewriter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Applies block substitution where Structurize turns a blueprint into placed blocks and requested
 * materials. The per-placement choice map lives on the structure handler (see
 * {@link MixinAbstractStructureHandler}); here we read it off {@code this.getHandler()} so the same
 * choices drive placement and material requests (and {@link MixinAbstractBlueprintIterator} drives the
 * match). For the build tool the handler carries the player's GUI picks; for the builder it carries the
 * building's choices (or null → datapack rules only).
 *
 * <p>{@code remap = false}: these are Structurize's own methods.
 */
@Mixin(StructurePlacer.class)
public class MixinStructurePlacer
{
    /**
     * Whether the current {@code handleBlockPlacement} invocation's blueprint tile data was changed by
     * substitution (set at HEAD, read by the match redirect below within the same invocation — the placer
     * runs on the server thread). Needed because the match redirect receives the ALREADY-substituted
     * {@code blockInfo}, where re-applying substitution is a no-op and can't signal the change itself.
     */
    @Unique
    private boolean structurizereplacements$placementTileSubstituted;

    @ModifyVariable(method = "handleBlockPlacement", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockInfo structurizereplacements$substituteBlueprintBlock(final BlockInfo blockInfo)
    {
        final BlockInfo substituted = BlockSubstitutions.apply(blockInfo, structurizereplacements$choices());
        this.structurizereplacements$placementTileSubstituted =
                substituted.getTileEntityData() != blockInfo.getTileEntityData();
        return substituted;
    }

    /**
     * {@code handleBlockPlacement} ALSO runs its own early "already built?" match and returns SUCCESS
     * before the item check, the removal (which refunds the old block!) and the placement handler. For a
     * Domum Ornamentum material swap (NBT-only — the state matches; SlimColonies' DO match is state-only)
     * that early-out skipped the ENTIRE replace flow: no refund of the old frame, no request/consume of the
     * new one, no tile write. Verifying the DO materials here lets the stock flow handle the replacement
     * end-to-end (refund → require/request → remove → place+write → consume).
     */
    @Redirect(
            method = "handleBlockPlacement",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/placement/handlers/placement/IPlacementHandler;doesWorldStateMatchBlueprintState(Lcom/ldtteam/structurize/util/BlockInfo;Lnet/minecraft/core/BlockPos;Lcom/ldtteam/structurize/placement/structure/IStructureHandler;)Z"),
            remap = false)
    private boolean structurizereplacements$substituteForPlacementMatch(final BlockInfo info, final BlockPos pos, final IStructureHandler handler)
    {
        // info is the already-substituted blockInfo (HEAD hook above) — no second substitution needed.
        final boolean matches = IPlacementHandler.doesWorldStateMatchBlueprintState(info, pos, handler);
        if (matches
                && this.structurizereplacements$placementTileSubstituted
                && ColonyIntegration.placementIgnoresDoMaterials())
        {
            return DomumMaterialRewriter.materialsMatchWorld(
                    handler.getWorld().getBlockEntity(pos), info.getTileEntityData());
        }
        return matches;
    }

    /**
     * The builder/quarrier compute what materials to request through {@code getResourceRequirements};
     * substitute the blueprint state here too so they request the block that will actually be placed.
     */
    @ModifyVariable(method = "getResourceRequirements", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockState structurizereplacements$substituteRequirement(final BlockState state)
    {
        return BlockSubstitutions.applyState(state, structurizereplacements$choices());
    }

    /**
     * The {@code @ModifyVariable} above only rewrites the requested block <i>state</i>. For Domum Ornamentum
     * "materialized" blocks the requested material lives in the {@code tileEntityData} arg passed to
     * {@code getRequiredItems} (the state stays the DO block), so rewrite that NBT here too — otherwise the
     * builder requests the original material while {@code handleBlockPlacement} places the substituted one.
     */
    @Redirect(
            method = "getResourceRequirements",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/placement/handlers/placement/IPlacementHandler;getRequiredItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;Lcom/ldtteam/structurize/placement/IPlacementContext;)Ljava/util/List;"),
            remap = false)
    private List<ItemStack> structurizereplacements$substituteRequestMaterials(
            final IPlacementHandler placementHandler, final Level world, final BlockPos pos,
            final BlockState state, final CompoundTag tileEntityData, final IPlacementContext context)
    {
        final Map<Block, Block> choices = structurizereplacements$choices();
        final CompoundTag newTileData =
                DomumMaterialRewriter.rewrite(state.getBlock(), tileEntityData, block -> BlockSubstitutions.resolveBlock(block, choices));
        return placementHandler.getRequiredItems(world, pos, state, newTileData, context);
    }

    /**
     * {@code getResourceRequirements} ALSO calls the static match itself (separately from the iterator's
     * scan) and returns "needs nothing" for positions it deems already built. Without substituting here, a
     * Domum Ornamentum material swap (NBT-only — the state matches) is judged "already built", the
     * substituted item never enters the builder's material list, and the colony AI loops forever
     * recalculating a list that can never contain the item the placement step demands.
     */
    @Redirect(
            method = "getResourceRequirements",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/ldtteam/structurize/placement/handlers/placement/IPlacementHandler;doesWorldStateMatchBlueprintState(Lcom/ldtteam/structurize/util/BlockInfo;Lnet/minecraft/core/BlockPos;Lcom/ldtteam/structurize/placement/structure/IStructureHandler;)Z"),
            remap = false)
    private boolean structurizereplacements$substituteForRequestMatch(final BlockInfo info, final BlockPos pos, final IStructureHandler handler)
    {
        return SubstitutedMatch.matches(info, pos, handler);
    }

    /**
     * After a successful placement of a Domum Ornamentum block, force the world block entity's materials
     * to match the (substituted) blueprint tag — <b>paid for like any other replacement</b>. SlimColonies'
     * DO placement handler skips the tile-entity write entirely when the block STATE was already correct
     * in the world (it bails before {@code handleTileEntityPlacement} when its {@code setBlockState} is a
     * no-op) — so an NBT-only substitution (the entire DO case) never reached the world on repairs.
     *
     * <p>Economy: the NEW (substituted) materialized item must be in the builder's inventory — otherwise
     * the result is rewritten to {@code MISSING_ITEMS} so the builder requests it through the normal flow —
     * and is consumed before the materials are enforced; the removal refunds the old item. Creative
     * placements stay free. {@code blockInfo} here is the SUBSTITUTED info — the {@code @ModifyVariable}
     * above replaced the argument at HEAD.
     */
    @Inject(method = "handleBlockPlacement", at = @At("RETURN"), remap = false, cancellable = true)
    private void structurizereplacements$enforceDoMaterials(final Level world, final BlockPos worldPos, final ChangeStorage storage,
                                                            final BlockInfo blockInfo, final CallbackInfoReturnable<BlockPlacementResult> cir)
    {
        if (cir.getReturnValue().getResult() != BlockPlacementResult.Result.SUCCESS
                || world.isClientSide
                || !ColonyIntegration.placementIgnoresDoMaterials()
                || blockInfo.getState() == null
                || !DomumMaterialRewriter.isMaterializedBlock(blockInfo.getState().getBlock()))
        {
            return;
        }
        final CompoundTag tag = blockInfo.getTileEntityData();
        if (tag == null || DomumMaterialRewriter.materialsMatchWorld(world.getBlockEntity(worldPos), tag))
        {
            return; // handler did its job (or nothing to fix) — no extra cost
        }
        final IStructureHandler structureHandler = ((StructurePlacer) (Object) this).getHandler();
        if (!structureHandler.isCreative())
        {
            // Charge exactly what the colony mod's own placement handler computes for this (substituted)
            // block — using its item normalization keeps the demanded stack consistent with what the
            // request scan put into the builder's material list.
            final IPlacementHandler placementHandler =
                    com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.getHandler(world, worldPos, blockInfo.getState());
            final List<ItemStack> needed = placementHandler == null ? List.of()
                    : placementHandler.getRequiredItems(world, worldPos, blockInfo.getState(), tag, structureHandler).stream()
                            .filter(stack -> !stack.isEmpty() && !structureHandler.isStackFree(stack))
                            .toList();
            if (!needed.isEmpty())
            {
                if (!structureHandler.hasRequiredItems(needed))
                {
                    cir.setReturnValue(new BlockPlacementResult(worldPos, BlockPlacementResult.Result.MISSING_ITEMS, needed));
                    return;
                }
                structureHandler.consume(needed);
            }
        }
        if (DomumMaterialRewriter.enforceWorldMaterials(world, worldPos, tag))
        {
            com.structurizereplacements.StructurizeReplacements.LOGGER.info(
                    "[Palette Swap] enforced substituted DO materials at {} (placement handler skipped the tile-entity write)", worldPos);
        }
    }

    @Unique
    private Map<Block, Block> structurizereplacements$choices()
    {
        final IStructureHandler handler = ((StructurePlacer) (Object) this).getHandler();
        return (handler instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
    }
}
