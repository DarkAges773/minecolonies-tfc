package com.mctfc.mixin;

import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import com.mctfc.farming.TfcFarmlandHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches the MineColonies farmer to till TFC soil into TFC farmland.
 *
 * <p>The vanilla-oriented farmer only recognizes {@code minecraft:dirt} blocks as hoeable and always
 * hoes them into vanilla {@code Blocks.FARMLAND}. In a TFC world the surface is TFC grass (not in
 * {@code minecraft:dirt}) and crops want {@code tfc:farmland/<soil>}. Two surgical changes:
 * <ol>
 *   <li>{@link #mctfc$alsoTfcTillable} widens the recognition gate to also accept our
 *       {@code #mctfc:farmer_tillable} tag (the TFC grass variants).</li>
 *   <li>{@link #mctfc$tfcFarmland} replaces the placed farmland with what a hoe would actually make of
 *       the soil — {@code tfc:farmland/<soil>} — leaving vanilla soil (and MineColonies' crop-preferred
 *       farmland) untouched.</li>
 * </ol>
 *
 * <p>Scope: tilling only. Planting/harvesting TFC crops on the resulting TFC farmland is a separate
 * follow-up (the farmer's plant logic doesn't know TFC crops, and TFC farmland isn't a vanilla
 * {@code FarmBlock}). All members use {@code remap = false} — this targets MineColonies' own class
 * and methods; only the inner Minecraft calls are remapped (see each {@code @At}). Both hooks are
 * {@code @Redirect}s so no {@code @Shadow} of MineColonies' inherited {@code world} field is needed
 * (it lives several superclasses up and doesn't resolve as a shadow at runtime).
 */
@Mixin(value = EntityAIWorkFarmer.class, remap = false)
public abstract class MixinEntityAIWorkFarmer
{
    /**
     * Recognition gate. {@code findHoeableSurface} bails unless the surface block is in
     * {@code minecraft:dirt} (or is vanilla/MC farmland). OR-in {@code #mctfc:farmer_tillable} so TFC
     * grass counts too. The {@code BlockState#is(TagKey)} call is Minecraft's, so its {@code @At} target
     * is remapped even though the enclosing injector isn't.
     */
    @Redirect(
            method = "findHoeableSurface(Lnet/minecraft/core/BlockPos;Lcom/minecolonies/core/colony/buildingextensions/FarmField;)Lnet/minecraft/core/BlockPos;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z",
                    remap = true))
    private boolean mctfc$alsoTfcTillable(final BlockState state, final TagKey<Block> tag)
    {
        return state.is(tag) || state.is(TfcFarmlandHelper.FARMER_TILLABLE);
    }

    /**
     * Farmland type. {@code createCorrectFarmlandForSeed} places vanilla {@code Blocks.FARMLAND} (or a
     * MineColonies crop's preferred farmland) via {@code Level#setBlockAndUpdate}. Intercept that call:
     * if the soil's own hoe result is a non-vanilla farmland (i.e. TFC's {@code tfc:farmland/<soil>}),
     * place that instead; otherwise place exactly what MineColonies intended. Redirecting the placement
     * (rather than the method) hands us the {@code Level} as receiver, so we need no {@code @Shadow}.
     */
    @Redirect(
            method = "createCorrectFarmlandForSeed(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    remap = true))
    private boolean mctfc$placeTfcFarmland(final Level level, final BlockPos pos, final BlockState intended)
    {
        final BlockState tfc = TfcFarmlandHelper.tilledFarmland(level, pos, level.getBlockState(pos));
        if (tfc != null && !tfc.is(Blocks.FARMLAND))
        {
            return level.setBlockAndUpdate(pos, tfc);
        }
        return level.setBlockAndUpdate(pos, intended);
    }

    /**
     * Plantable gate. {@code isRightFarmLandForCrop} only treats a vanilla {@code FarmBlock} as valid for
     * non-MineColonies seeds, so the farmer never plants on the {@code tfc:farmland/<soil>} it just tilled.
     * Accept TFC farmland when the field's seed plants a {@link net.minecraft.world.level.block.CropBlock}
     * (TFC crops extend it). The AI's downstream {@code plantCrop} still runs the crop's own
     * {@code canSurvive} check, so an incompatible crop simply isn't placed. Returning {@code true} here also
     * makes {@code findHoeableSurface} correctly stop re-hoeing land that's already TFC farmland.
     */
    @Inject(
            method = "isRightFarmLandForCrop(Lcom/minecolonies/core/colony/buildingextensions/FarmField;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void mctfc$tfcFarmlandIsRight(final FarmField farmField, final BlockState state, final CallbackInfoReturnable<Boolean> cir)
    {
        if (state.is(TfcFarmlandHelper.TFC_FARMLAND) && TfcFarmlandHelper.plantsCrop(farmField.getSeed()))
        {
            cir.setReturnValue(true);
        }
    }
}
