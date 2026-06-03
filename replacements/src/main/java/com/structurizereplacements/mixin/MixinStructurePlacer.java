package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.PlacementChoiceHolder;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

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
    @ModifyVariable(method = "handleBlockPlacement", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockInfo structurizereplacements$substituteBlueprintBlock(final BlockInfo blockInfo)
    {
        return BlockSubstitutions.apply(blockInfo, structurizereplacements$choices());
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

    @Unique
    private Map<Block, Block> structurizereplacements$choices()
    {
        final IStructureHandler handler = ((StructurePlacer) (Object) this).getHandler();
        return (handler instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
    }
}
