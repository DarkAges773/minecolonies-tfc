package com.mctfc.mixin;

import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import com.mctfc.farming.FarmFieldHarvestMode;
import com.mctfc.farming.FertilizerHelper;
import com.mctfc.farming.HarvestMode;
import com.mctfc.farming.TfcFarmlandHelper;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches the MineColonies farmer to till TFC soil into TFC farmland.
 *
 * <p>The vanilla-oriented farmer only recognizes {@code minecraft:dirt} blocks as hoeable, hoes them
 * into vanilla {@code Blocks.FARMLAND}, only plants on a vanilla {@code FarmBlock}, and only harvests
 * vanilla/MC crops. In a TFC world the surface is TFC grass and crops live on {@code tfc:farmland/<soil>}.
 * Hooks:
 * <ol>
 *   <li>{@link #mctfc$alsoTfcTillable} — widen the hoe-recognition gate to our {@code #mctfc:farmer_tillable}
 *       tag (the TFC grass variants).</li>
 *   <li>{@link #mctfc$placeTfcFarmland} — place {@code tfc:farmland/<soil>} (what a hoe would make of the
 *       soil) instead of vanilla farmland, leaving vanilla soil / MC crop-preferred farmland untouched.</li>
 *   <li>{@link #mctfc$tfcFarmlandIsRight} — accept TFC farmland as plantable when the field's seed grows a
 *       {@link net.minecraft.world.level.block.CropBlock} (TFC crops extend it).</li>
 *   <li>{@link #mctfc$harvestDeadCrop} — own the harvest decision for TFC crops: harvest ripe crops (Fruiting)
 *       and crops gone to seed (a mature {@link DeadCropBlock}); in {@code SEEDING} leave ripe crops to die
 *       first. By owning it, the base AI's compost/bone-meal growth-forcing never runs on TFC crops (TFC
 *       growth is climate/time-driven, not bone-meal).</li>
 *   <li>{@link #mctfc$fertilizerCountsAsCompost} — let TFC fertilizers ride the farmer's compost
 *       count/gather pipeline (the base AI only knows MC compost + bone meal), so it stocks them.</li>
 *   <li>{@link #mctfc$fertilizeOnPlant} — at plant time, top up the soil's crop-specific nutrient (TFC's
 *       per-crop N/P/K) with the best matching fertilizer on hand. See {@link FertilizerHelper}.</li>
 * </ol>
 *
 * <p>Most members use {@code remap = false} — this targets MineColonies' own class and methods; only the
 * inner Minecraft calls are remapped (see each {@code @At}). The till/plant hooks are {@code @Redirect}s so
 * no {@code @Shadow} of MineColonies' inherited {@code world} field is needed (it lives several superclasses
 * up and does not resolve as a shadow at runtime); the harvest hook instead shadows two methods declared on
 * {@code EntityAIWorkFarmer} itself ({@code getCitizen()} for the level, {@code getSurfacePos()} for the
 * crop position), which resolve reliably.
 *
 * <p>Harvest behaviour follows the field's per-field {@link HarvestMode} (set in the field GUI, stored on the
 * {@code FarmField} — see {@code MixinFarmField}/{@code MixinWindowField}). The mode is captured into
 * {@link #mctfc$activeHarvestMode} whenever the AI fetches the field it's working (the two {@code @Redirect}s
 * on the extension module), then read by the harvest hook. {@code FRUITING} (default) harvests ripe crops +
 * mature dead crops; {@code SEEDING} harvests only mature dead crops.
 */
@Mixin(value = EntityAIWorkFarmer.class, remap = false)
public abstract class MixinEntityAIWorkFarmer
{
    /** The working citizen — declared on {@code EntityAIWorkFarmer}, so it shadows reliably (unlike the
     *  inherited {@code world}/{@code worker} fields). Used to reach the {@link Level}. */
    @Shadow
    public abstract AbstractEntityCitizen getCitizen();

    /** Walks down/up from a field cell to the actual surface block — the same resolution the harvest
     *  scan uses, so our dead-crop check looks at the right position. Private on the target, shadowed
     *  via a stub body. */
    @Shadow
    private BlockPos getSurfacePos(final BlockPos position)
    {
        throw new AssertionError("shadow");
    }

    /** The harvest mode of the field the AI is currently working — captured whenever the AI fetches that
     *  field (the two redirects below), then read by the harvest hook. Defaults to Fruiting. */
    @Unique
    private HarvestMode mctfc$activeHarvestMode = HarvestMode.FRUITING;

    @Unique
    private void mctfc$captureMode(final IBuildingExtension extension)
    {
        mctfc$activeHarvestMode = extension instanceof FarmFieldHarvestMode holder
                ? holder.mctfc$getHarvestMode() : HarvestMode.FRUITING;
    }

    /** Capture the mode before the planting/hoeing/harvest dispatch in {@code prepareForFarming}. */
    @Redirect(
            method = "prepareForFarming()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule;getExtensionToWorkOn()Lcom/minecolonies/api/colony/buildingextensions/IBuildingExtension;"))
    private IBuildingExtension mctfc$captureWorkExtension(final BuildingExtensionsModule module)
    {
        final IBuildingExtension extension = module.getExtensionToWorkOn();
        mctfc$captureMode(extension);
        return extension;
    }

    /** Capture the mode before the per-cell work (incl. harvest) in {@code workAtField}. */
    @Redirect(
            method = "workAtField()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule;getCurrentExtension()Lcom/minecolonies/api/colony/buildingextensions/IBuildingExtension;"))
    private IBuildingExtension mctfc$captureCurrentExtension(final BuildingExtensionsModule module)
    {
        final IBuildingExtension extension = module.getCurrentExtension();
        mctfc$captureMode(extension);
        return extension;
    }

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

    /**
     * Harvest gate for TFC crops that have gone to seed. {@code findHarvestableSurface} returns a position
     * when there's something to harvest above it; the base AI handles ripe vanilla-style crops (TFC's live
     * {@code CropBlock} included, via {@code isMaxAge}) but is blind to a {@link DeadCropBlock} — TFC's
     * seeding stage, which extends a bush block and drops extra seeds. When the block above is a <i>mature</i>
     * dead crop, return its position so the farmer harvests it (collecting the seeds and clearing the cell
     * for replanting). Non-dead-crop cases fall through to the base AI untouched (vanilla/MC crops, ripe TFC
     * crops). Immature dead crops are skipped — they carry no worthwhile drops.
     */
    @Inject(
            method = "findHarvestableSurface(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"),
            cancellable = true)
    private void mctfc$harvestDeadCrop(final BlockPos position, final CallbackInfoReturnable<BlockPos> cir)
    {
        final BlockPos surface = getSurfacePos(position);
        if (surface == null)
        {
            return;
        }
        final BlockState above = getCitizen().level().getBlockState(surface.above());
        final Block block = above.getBlock();
        if (block instanceof DeadCropBlock && above.getValue(DeadCropBlock.MATURE))
        {
            // Both modes collect the seeding stage.
            cir.setReturnValue(surface);
        }
        else if (block instanceof CropBlock tfcCrop)
        {
            // Live TFC crop: we own the decision so the base AI's compost/bone-meal growth-forcing never
            // runs (TFC growth is climate/time-driven, not bone-meal). Harvest only when ripe, and only in
            // Fruiting; otherwise leave it to grow/ripen naturally.
            cir.setReturnValue(mctfc$activeHarvestMode != HarvestMode.SEEDING && tfcCrop.isMaxAge(above) ? surface : null);
        }
        // Vanilla / MineColonies crops fall through to the base AI.
    }

    /**
     * Treat TFC fertilizers as the farmer's "compost" so its count/gather pipeline stocks them (the base AI
     * only recognizes MineColonies compost + bone meal). This is what lets the farmer carry TFC fertilizer to
     * apply to soil. (The base AI's compost-for-growth use is already bypassed for TFC crops by the harvest
     * hook above, so recognizing fertilizers here doesn't resurrect growth-forcing on them.)
     */
    @Inject(method = "isCompost(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void mctfc$fertilizerCountsAsCompost(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir)
    {
        if (FertilizerHelper.isFertilizer(stack))
        {
            cir.setReturnValue(true);
        }
    }

    /**
     * When planting, top up the soil's crop-specific nutrient if it's run low — applying the best matching
     * TFC fertilizer the farmer carries. {@code position} is the farmland (the crop goes above it). No-op for
     * non-TFC crops, well-stocked soil, or when no matching fertilizer is on hand.
     */
    @Inject(method = "plantCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"))
    private void mctfc$fertilizeOnPlant(final ItemStack seed, final BlockPos position, final CallbackInfoReturnable<Boolean> cir)
    {
        FertilizerHelper.fertilizeForSeed(getCitizen().level(), position, seed, (IItemHandler) getCitizen().getInventoryCitizen());
    }
}
