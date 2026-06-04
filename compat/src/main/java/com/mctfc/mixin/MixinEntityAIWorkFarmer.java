package com.mctfc.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import com.mctfc.farming.FarmFieldHarvestMode;
import com.mctfc.farming.FertilizerHelper;
import com.mctfc.farming.HarvestMode;
import com.mctfc.farming.TfcFarmlandHelper;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
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

import java.util.List;
import java.util.function.Predicate;

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
 *       growth is climate/time-driven, not bone-meal). Also opportunistically tops up a live crop's soil
 *       nutrient here, since this scan visits every cell of a planted field.</li>
 *   <li>{@link #mctfc$fertilizerCountsAsCompost} — make the farmer's "has fertilizer?" count/gather
 *       nutrient-specific: only TFC fertilizer supplying the current crop's nutrient counts, so wrong-nutrient
 *       stock (or stray bone meal) doesn't suppress the request for the fertilizer the field actually needs.</li>
 *   <li>{@link #mctfc$requestTfcFertilizer} — redirect the farmer's fertilizer request to the TFC fertilizers
 *       that supply the current crop's nutrient (not MC compost it can't use), still under the hut's
 *       "request fertilizer" toggle.</li>
 *   <li>{@link #mctfc$fertilizeOnPlant} — at plant time, top up the soil's crop-specific nutrient (TFC's
 *       per-crop N/P/K) with the best matching fertilizer on hand. See {@link FertilizerHelper}.</li>
 *   <li>{@link #mctfc$skipEmptyPlanting} — only enter {@code FARMER_PLANT} when a cell is actually plantable
 *       (the base AI doesn't check), so a full field of growing crops doesn't make the farmer walk it pointlessly.</li>
 *   <li>{@link #mctfc$keepMatureDeadCrops} — treat a mature {@link DeadCropBlock} as not-plantable so the farmer
 *       doesn't overwrite (and destroy the seeds of) a crop gone to seed.</li>
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

    /** Scans the field's cells for one matching the predicate (sets the working offset to it), as the base
     *  AI does to gate hoeing/harvesting. Private on the target, shadowed via a stub body. */
    @Shadow
    private boolean checkIfShouldExecute(final FarmField farmField, final Predicate<BlockPos> predicate)
    {
        throw new AssertionError("shadow");
    }

    /** The base AI's "is this cell plantable" test (part of field, empty above, right farmland, …). */
    @Shadow
    private BlockPos findPlantableSurface(final BlockPos position, final FarmField farmField)
    {
        throw new AssertionError("shadow");
    }

    /** The harvest mode of the field the AI is currently working — captured whenever the AI fetches that
     *  field (the two redirects below), then read by the harvest hook. Defaults to Fruiting. */
    @Unique
    private HarvestMode mctfc$activeHarvestMode = HarvestMode.FRUITING;

    /** The nutrient the current field's crop drains — captured alongside the mode, read by the fertilizer
     *  request redirect. {@code null} when the field has no TFC crop seed. */
    @Unique
    private NutrientType mctfc$neededNutrient = null;

    @Unique
    private void mctfc$captureField(final IBuildingExtension extension)
    {
        mctfc$activeHarvestMode = extension instanceof FarmFieldHarvestMode holder
                ? holder.mctfc$getHarvestMode() : HarvestMode.FRUITING;
        mctfc$neededNutrient = extension instanceof FarmField field
                ? FertilizerHelper.neededNutrient(field.getSeed()) : null;
    }

    /**
     * Capture the field (mode + crop nutrient) at the FIRST module call in {@code prepareForFarming}
     * ({@code getOwnedExtensions}, the advancement check) — which is before the fertilizer count/request
     * block, so the nutrient is known when {@link #mctfc$fertilizerCountsAsCompost} runs. The field is sticky
     * ({@code getExtensionToWorkOn} returns the current one), so reading it early doesn't change selection.
     */
    @Redirect(
            method = "prepareForFarming()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule;getOwnedExtensions()Ljava/util/List;"))
    private List<IBuildingExtension> mctfc$captureBeforeCount(final BuildingExtensionsModule module)
    {
        mctfc$captureField(module.getExtensionToWorkOn());
        return module.getOwnedExtensions();
    }

    /** Capture the field (mode + crop nutrient) before the per-cell work (incl. harvest) in {@code workAtField}. */
    @Redirect(
            method = "workAtField()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule;getCurrentExtension()Lcom/minecolonies/api/colony/buildingextensions/IBuildingExtension;"))
    private IBuildingExtension mctfc$captureCurrentExtension(final BuildingExtensionsModule module)
    {
        final IBuildingExtension extension = module.getCurrentExtension();
        mctfc$captureField(extension);
        return extension;
    }

    /**
     * Don't plant over a seed-bearing dead crop. {@code findPlantableSurface} only treats vanilla
     * {@code CropBlock}/{@code StemBlock}/{@code MinecoloniesCropBlock} above a cell as "occupied" — a TFC
     * {@link DeadCropBlock} (a bush block) reads as empty, so the farmer would overwrite it and lose its
     * seeds (the seeding stage that the harvest pass collects, and that Seeding mode exists to produce). Treat
     * a <i>mature</i> dead crop as not plantable so it survives for harvest; a non-mature dead crop (no
     * worthwhile drops) is left plantable so the cell can be reclaimed.
     */
    @Inject(
            method = "findPlantableSurface(Lnet/minecraft/core/BlockPos;Lcom/minecolonies/core/colony/buildingextensions/FarmField;)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"),
            cancellable = true)
    private void mctfc$keepMatureDeadCrops(final BlockPos position, final FarmField farmField, final CallbackInfoReturnable<BlockPos> cir)
    {
        final BlockPos surface = getSurfacePos(position);
        if (surface == null)
        {
            return;
        }
        final BlockState above = getCitizen().level().getBlockState(surface.above());
        if (above.getBlock() instanceof DeadCropBlock && above.getValue(DeadCropBlock.MATURE))
        {
            cir.setReturnValue(null);
        }
    }

    /**
     * Gate planting on actual work. The base AI enters {@code FARMER_PLANT} via {@code canGoPlanting} whenever
     * a seed is available — unlike hoeing/harvesting, it never checks whether any cell is actually plantable.
     * On a fully-planted field of still-growing crops that makes the farmer pointlessly walk every cell each
     * stage cycle (worse under TFC's slow growth). So if no cell is plantable, skip planting and advance the
     * field stage instead — mirroring how {@code findHoeableSurface}/{@code findHarvestableSurface} gate the
     * other two states via {@code checkIfShouldExecute}.
     */
    @Inject(
            method = "canGoPlanting(Lcom/minecolonies/core/colony/buildingextensions/FarmField;)Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At("HEAD"),
            cancellable = true)
    private void mctfc$skipEmptyPlanting(final FarmField farmField, final CallbackInfoReturnable<IAIState> cir)
    {
        if (!checkIfShouldExecute(farmField, pos -> findPlantableSurface(pos, farmField) != null))
        {
            farmField.nextState();
            cir.setReturnValue(AIWorkerState.PREPARING);
        }
    }

    /**
     * Request the right fertilizer. The base AI requests its compost/bone-meal blend; we redirect that
     * request to a {@code StackList} of TFC fertilizers that supply the current crop's primary nutrient, so
     * the farmer asks for (and the player can supply) any matching fertilizer — not normal fertilizer it
     * can't use. This sits inside the AI's existing {@code building.requestFertilizer()} gate, so the hut's
     * "request fertilizer" toggle still governs it. Falls back to the original request for a non-TFC crop or
     * if no fertilizer supplies the nutrient.
     */
    @Redirect(
            method = "prepareForFarming()Lcom/minecolonies/api/entity/ai/statemachine/states/IAIState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/ICitizenData;createRequestAsync(Lcom/minecolonies/api/colony/requestsystem/requestable/IRequestable;)Lcom/minecolonies/api/colony/requestsystem/token/IToken;"))
    private IToken<?> mctfc$requestTfcFertilizer(final ICitizenData data, final IRequestable original)
    {
        if (mctfc$neededNutrient != null)
        {
            final List<ItemStack> fertilizers = FertilizerHelper.fertilizersFor(mctfc$neededNutrient);
            if (!fertilizers.isEmpty())
            {
                return data.createRequestAsync(new StackList(fertilizers, "com.minecolonies.coremod.request.fertilizer", 64, 1));
            }
        }
        return data.createRequestAsync(original);
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
        final Level level = getCitizen().level();
        final BlockState above = level.getBlockState(surface.above());
        final Block block = above.getBlock();
        if (block instanceof DeadCropBlock && above.getValue(DeadCropBlock.MATURE))
        {
            // Both modes collect the seeding stage.
            cir.setReturnValue(surface);
        }
        else if (block instanceof CropBlock tfcCrop)
        {
            // Opportunistic: this harvest scan visits every cell of a planted field, so top up the live
            // crop's soil nutrient here too (not just at plant) — mirrors how the base AI applied compost
            // during this same scan.
            FertilizerHelper.fertilize(level, surface, tfcCrop.getPrimaryNutrient(), (IItemHandler) getCitizen().getInventoryCitizen());
            // We own the harvest decision so the base AI's compost/bone-meal growth-forcing never runs (TFC
            // growth is climate/time-driven, not bone-meal). Harvest only when ripe, and only in Fruiting;
            // otherwise leave it to grow/ripen naturally.
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
        // When working a TFC-crop field, "fertilizer the farmer has" means fertilizer for THAT crop's nutrient
        // — so wrong-nutrient stock doesn't satisfy the count and block the right request. (Non-TFC field:
        // fall through to the base AI's compost/bone-meal check.)
        if (mctfc$neededNutrient != null)
        {
            cir.setReturnValue(FertilizerHelper.providesNutrient(stack, mctfc$neededNutrient));
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
