package com.mctfc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minecolonies.core.entity.ai.workers.production.EntityAIWorkLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the Lumberjack <b>replant</b> on TFC ground. The Lumberjack already <i>finds and chops</i> TFC trees fine
 * (TFC logs/leaves piggyback the vanilla {@code #minecraft:logs}/{@code #minecraft:leaves} tags that drive
 * {@code minecolonies:tree}), but {@code placeSaplings} only plants when
 * {@code block.canSustainPlant(soilBelow, …)} is true — and Forge's default {@code canSustainPlant} for a
 * {@code PlantType.PLAINS} sapling requires the soil to be in {@code BlockTags.DIRT}. TFC's {@code #minecraft:dirt}
 * contains only {@code #tfc:dirt} (bare dirt/rooted/muddy), <b>not</b> {@code #tfc:grass} — yet naturally-grown TFC
 * trees stand on grass. So vanilla rejects every grass-grown TFC tree and the Lumberjack removes the stump without
 * replanting, depleting the forest.
 *
 * <p>We wrap that single {@code canSustainPlant} call: keep its result (so vanilla/other-mod soils are unchanged),
 * but if it says no, ask the plant itself whether it could survive on this soil via {@code BlockState#canSurvive}.
 * For a TFC sapling that defers to {@code TFCSaplingBlock#mayPlaceOn}, which accepts {@code #tfc:bush_plantable_on}
 * ({@code #minecraft:dirt} + {@code #tfc:grass} + {@code #tfc:farmland}) — i.e. TFC grass. This is general and
 * conservative: it only allows a replant where the sapling can genuinely live, so it never plants where it
 * shouldn't, and needs no hard-coded TFC tag.
 *
 * <p>{@code remap = false}: the target method {@code placeSaplings} is MineColonies' own, and the wrapped
 * {@code Block#canSustainPlant} is a Forge-added method — neither is SRG-mapped.
 */
@Mixin(EntityAIWorkLumberjack.class)
public abstract class MixinEntityAIWorkLumberjack
{
    @WrapOperation(
      method = "placeSaplings",
      at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/Block;canSustainPlant(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraftforge/common/IPlantable;)Z"),
      remap = false)
    private boolean mctfc$tfcGroundSustainsSapling(
      final Block block,
      final BlockState soil,
      final BlockGetter level,
      final BlockPos soilPos,
      final Direction face,
      final IPlantable plantable,
      final Operation<Boolean> original)
    {
        if (original.call(block, soil, level, soilPos, face, plantable))
        {
            return true;
        }
        // soilPos is the block BELOW the stump; the sapling itself goes one above. Let the plant judge the soil.
        final BlockPos plantPos = soilPos.above();
        return level instanceof LevelReader reader && plantable.getPlant(level, plantPos).canSurvive(reader, plantPos);
    }
}
