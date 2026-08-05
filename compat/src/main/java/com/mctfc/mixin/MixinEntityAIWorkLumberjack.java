package com.mctfc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.ai.workers.production.EntityAIWorkLumberjack;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.util.AxeLoggingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Two TFC fixes for the Lumberjack. (The worker already <i>finds and chops</i> TFC trees with no help — TFC
 * logs/leaves/saplings ride the vanilla {@code #minecraft:logs}/{@code leaves}/{@code saplings} tags that drive
 * {@code minecolonies:tree}.)
 *
 * <p><b>1 — Replant on TFC ground.</b> {@code placeSaplings} plants only when
 * {@code block.canSustainPlant(soilBelow, …)} is true, and Forge's default {@code canSustainPlant} for a
 * {@code PlantType.PLAINS} sapling requires the soil ∈ {@code BlockTags.DIRT}. TFC's {@code #minecraft:dirt}
 * contains only {@code #tfc:dirt} (bare dirt/rooted/muddy), <b>not</b> {@code #tfc:grass} — yet wild TFC trees grow
 * on grass, so vanilla rejected them and the worker pulled the stump without replanting. We wrap that call: keep its
 * result, else ask the plant itself via {@code BlockState#canSurvive} (for a TFC sapling that defers to
 * {@code TFCSaplingBlock#mayPlaceOn}, accepting {@code #tfc:bush_plantable_on} = dirt + grass + farmland).
 *
 * <p><b>2 — TFC whole-tree felling.</b> TFC gives every axe a felling behaviour (break one trunk log → the whole
 * connected tree drops), wired to a {@code BlockEvent.BreakEvent} that only fires for a real player; the citizen
 * isn't one, so it never triggers and the worker instead climbs the trunk log-by-log (slow, and prone to getting
 * stuck on tall trees). We wrap the per-log {@code mineBlock} call in {@code chopTree}: when TFC's own
 * {@code AxeLoggingHelper.shouldLog} approves (logging axe + natural trunk), we first spend a chop delay scaled by
 * the whole tree's log count ({@code findLogs().size()} × the normal per-log {@code getBlockMiningTime}, so a bigger
 * tree — or a worse axe — takes longer), then call {@code doLogging} with the worker's
 * {@linkplain AbstractEntityAIBasicInvoker#mctfc$getFakePlayer() fake player} and its actual axe stack — felling the
 * trunk in one go, dropping the logs at their positions (the gathering phase collects them) and wearing the axe per
 * log, exactly like a player. The leaf path, non-trunk logs, and 2×2 trunks (where {@code shouldLog} is false) fall
 * through to the unchanged single-block break.
 *
 * <p>{@code remap = false}: the target methods are MineColonies' own and the wrapped {@code Block#canSustainPlant}
 * is a Forge-added method — none are SRG-mapped. TFC is a mandatory dependency of mctfc, so {@code AxeLoggingHelper}
 * is always present.
 */
@Mixin(EntityAIWorkLumberjack.class)
public abstract class MixinEntityAIWorkLumberjack
{
    /**
     * Start a freshly-planted TFC sapling's growth timer. The worker places saplings with {@code Level#setBlockAndUpdate}
     * — which never calls {@code setPlacedBy}, the only place {@code TFCSaplingBlock} resets its {@code TickCounterBlockEntity}.
     * Left unreset, the counter sits at its sentinel, reads as ancient, and the sapling grows on its first random tick
     * instead of after its {@code daysToGrow}. We reset it right after a successful placement (a no-op for any block
     * without that counter BE, e.g. vanilla saplings or the nylium below a fungus). {@code remap = false} for the
     * MineColonies method selector; {@code remap = true} on the {@code @At} because {@code setBlockAndUpdate} is vanilla.
     */
    @WrapOperation(
      method = "placeSaplings",
      at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        remap = true),
      remap = false)
    private boolean mctfc$startSaplingGrowthTimer(
      final Level level,
      final BlockPos pos,
      final BlockState placed,
      final Operation<Boolean> original)
    {
        final boolean placedOk = original.call(level, pos, placed);
        if (placedOk)
        {
            TickCounterBlockEntity.reset(level, pos);
        }
        return placedOk;
    }

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

    @WrapOperation(
      method = "chopTree",
      at = @At(
        value = "INVOKE",
        target = "Lcom/minecolonies/core/entity/ai/workers/production/EntityAIWorkLumberjack;mineBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Z"),
      remap = false)
    private boolean mctfc$fellTfcTree(
      final EntityAIWorkLumberjack self,
      final BlockPos toMine,
      final BlockPos safeStand,
      final Operation<Boolean> original)
    {
        final AbstractEntityAIBasicInvoker ai = (AbstractEntityAIBasicInvoker) (Object) this;
        final FakePlayer faker = ai.mctfc$getFakePlayer();
        final Level level = faker.level();
        final ItemStack axe = ((AbstractAISkeletonAccessor) (Object) this).mctfc$worker().getMainHandItem();
        final BlockState state = level.getBlockState(toMine);
        if (!AxeLoggingHelper.shouldLog(level, toMine, state, axe))
        {
            return original.call(self, toMine, safeStand);
        }
        // Spend chop time proportional to the whole tree's log count, × the normal per-log mining time (which itself
        // scales with axe tier + worker skill + research) — so a bigger tree, or a worse axe, takes longer to fell.
        final int logs = AxeLoggingHelper.findLogs(level, toMine).size();
        final int perLog = ((AbstractEntityAIInteract) (Object) this).getBlockMiningTime(state, toMine);
        // Point the worker at the base log so MineColonies' waitingForSomething() swings the axe each tick of the
        // delay (it auto-calls hitBlockWithToolInHand on currentWorkingLocation) — otherwise it stands idle.
        ai.mctfc$setCurrentWorkingLocation(toMine);
        if (ai.mctfc$hasNotDelayed(Math.max(1, logs * perLog)))
        {
            return false; // still cutting the base — keep waiting (worker swings via waitingForSomething)
        }
        // Delay elapsed: drop the whole connected trunk. Logs drop at their own positions (gathering collects them);
        // the axe wears per log. Remaining tracked logs are now air and drain a tick each.
        AxeLoggingHelper.doLogging(level, toMine, faker, axe);
        return true;
    }
}
