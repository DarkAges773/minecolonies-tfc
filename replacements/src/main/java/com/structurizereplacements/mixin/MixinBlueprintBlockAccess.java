package com.structurizereplacements.mixin;

import com.ldtteam.structurize.client.fakelevel.BlueprintBlockAccess;
import com.structurizereplacements.placement.PlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: makes the Structurize placement preview (the hologram) show substituted blocks.
 *
 * <p>{@code BlueprintBlockAccess} is the fake level the blueprint renderer reads block states from.
 * Its block-state cache is built without the substitution applied (the server-side
 * {@code StructurePlacer#handleBlockPlacement} mixin only affects actual placement), so we substitute
 * the returned state here so the preview matches what will be placed.
 *
 * <p>Note: {@code getBlockState} overrides the vanilla {@code BlockGetter} method, so unlike our
 * Structurize-own-method mixins this one must be remapped (remap defaults to true).
 *
 * <p>Caveat: substitution rules load on the (logical) server's datapack reload. In single-player the
 * integrated server shares this JVM so the preview substitutes correctly; on a dedicated server the
 * client has no rules until they are synced (a planned follow-up), so the preview won't reflect them
 * there even though placement still will.
 */
@Mixin(BlueprintBlockAccess.class)
public class MixinBlueprintBlockAccess
{
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void structurizereplacements$substitutePreview(final BlockPos pos, final CallbackInfoReturnable<BlockState> cir)
    {
        cir.setReturnValue(BlockSubstitutions.applyState(cir.getReturnValue(), PlacementChoices.client()));
    }
}
