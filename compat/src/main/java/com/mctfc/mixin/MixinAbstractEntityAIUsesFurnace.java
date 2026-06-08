package com.mctfc.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceBehaviors;
import com.mctfc.furnace.FurnaceWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Thin dispatcher that lets {@code :compat} replace a MineColonies furnace worker's behaviour with a
 * TFC-flavoured one, without the base AI becoming an {@code instanceof} chokepoint. On construction it asks
 * {@link FurnaceBehaviors} for a behavior registered against this AI's exact class; if one exists it installs
 * the behavior's states and routes {@code startWorking} to it, otherwise the worker runs vanilla unchanged
 * (so the Cook and any non-customised furnace user are untouched until they get their own behavior).
 *
 * <p>It also implements {@link FurnaceWorker}, the bridge a behavior drives the worker through. The worker /
 * building / world are derived lazily from the captured {@code job} (so we never shadow the deeply-inherited
 * {@code worker}/{@code world} fields that fail to resolve at apply time — see CLAUDE.md); only the AI's own
 * navigation/pacing methods are shadowed ({@code remap = false}: MineColonies' own members).
 */
@Mixin(AbstractEntityAIUsesFurnace.class)
public abstract class MixinAbstractEntityAIUsesFurnace implements FurnaceWorker
{
    @Unique private AbstractJob<?, ?> mctfc$job;
    @Unique private FurnaceBehavior mctfc$behavior;

    @Shadow(remap = false) protected abstract boolean walkToBuilding();

    @Shadow(remap = false) protected abstract boolean walkToWorkPos(BlockPos pos);

    @Shadow(remap = false) public abstract void setDelay(int timeout);

    @Shadow(remap = false) public abstract IAIState getState();

    @Shadow(remap = false) public abstract void registerTarget(TickingTransition<IAIState> target);

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void mctfc$installBehavior(final AbstractJob<?, ?> job, final CallbackInfo ci)
    {
        this.mctfc$job = job;
        this.mctfc$behavior = FurnaceBehaviors.create(this, this);
        if (this.mctfc$behavior != null)
        {
            for (final AITarget<IAIState> target : this.mctfc$behavior.targets())
            {
                registerTarget(target);
            }
        }
    }

    @Inject(method = "startWorking", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$startWorking(final CallbackInfoReturnable<IAIState> cir)
    {
        if (this.mctfc$behavior != null)
        {
            cir.setReturnValue(this.mctfc$behavior.startWorking());
        }
    }

    // --- FurnaceWorker bridge (derived from the captured job; no field shadows) ---

    @Override
    public AbstractEntityCitizen worker()
    {
        return mctfc$job.getCitizen().getEntity().orElse(null);
    }

    @Override
    public IBuilding building()
    {
        return mctfc$job.getCitizen().getWorkBuilding();
    }

    @Override
    public Level world()
    {
        final AbstractEntityCitizen w = worker();
        return w == null ? null : w.level();
    }

    @Override
    public List<BlockPos> furnaces()
    {
        final IBuilding building = building();
        return building == null ? List.of() : building.getFirstModuleOccurance(FurnaceUserModule.class).getFurnaces();
    }

    @Override
    public boolean gotoBuilding()
    {
        return walkToBuilding();
    }

    @Override
    public boolean gotoWorkPos(final BlockPos pos)
    {
        return walkToWorkPos(pos);
    }

    @Override
    public void delay(final int ticks)
    {
        setDelay(ticks);
    }

    @Override
    public IAIState state()
    {
        return getState();
    }
}
