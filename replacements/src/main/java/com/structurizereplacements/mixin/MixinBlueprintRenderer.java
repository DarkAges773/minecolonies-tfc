package com.structurizereplacements.mixin;

import com.ldtteam.structurize.client.BlueprintRenderer;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.PlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
