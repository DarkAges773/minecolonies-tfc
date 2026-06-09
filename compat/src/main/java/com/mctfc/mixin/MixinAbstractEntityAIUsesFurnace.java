package com.mctfc.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.modules.FurnaceUserModule;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.mctfc.furnace.FurnaceBehavior;
import com.mctfc.furnace.FurnaceBehaviors;
import com.mctfc.furnace.FurnaceWorker;

import static com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract.RENDER_META_WORKING;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
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
 * <p>It implements {@link FurnaceWorker}, the bridge a behavior drives the worker through. We deliberately
 * avoid {@code @Shadow} of MineColonies' AI internals: the worker/building/world come from the captured
 * {@code job}, the AI's <b>public</b> helpers ({@code setDelay}/{@code getState}/{@code registerTarget}) are
 * reached by casting to their declaring classes, and the <b>protected</b> navigation helpers go through
 * {@link AbstractEntityAIBasicInvoker}. (A plain {@code @Shadow} of these inherited members fails to apply —
 * "method … was not located in the target class" — see CLAUDE.md.)
 */
@Mixin(AbstractEntityAIUsesFurnace.class)
public abstract class MixinAbstractEntityAIUsesFurnace implements FurnaceWorker
{
    @Unique private AbstractJob<?, ?> mctfc$job;
    @Unique private FurnaceBehavior mctfc$behavior;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void mctfc$installBehavior(final AbstractJob<?, ?> job, final CallbackInfo ci)
    {
        this.mctfc$job = job;
        this.mctfc$behavior = FurnaceBehaviors.create(this, this);
        if (this.mctfc$behavior != null)
        {
            final AbstractAISkeleton<?> skeleton = (AbstractAISkeleton<?>) (Object) this;
            for (final AITarget<IAIState> target : this.mctfc$behavior.targets())
            {
                skeleton.registerTarget(target);
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

    /**
     * The base furnace worker dumps after every action (returns 1). For our behaviors that would mean a trek
     * back to the hut per single output, so batch the dump to {@link FurnaceBehavior#actionsUntilDump()}.
     * Vanilla furnace workers (no behavior installed) keep their value.
     */
    @Inject(method = "getActionsDoneUntilDumping", at = @At("HEAD"), cancellable = true, remap = false)
    private void mctfc$dumpCadence(final CallbackInfoReturnable<Integer> cir)
    {
        if (this.mctfc$behavior != null)
        {
            cir.setReturnValue(this.mctfc$behavior.actionsUntilDump());
        }
    }

    /**
     * Override the (always-{@code false}) {@code AbstractEntityAIBasic#canGoIdle()} so MineColonies' CitizenAI
     * lets the citizen wander / idle (and pauses the work AI) when our behavior has no work — the seam the
     * farmer uses. Furnace workers without a behavior keep the vanilla default.
     */
    public boolean canGoIdle()
    {
        final boolean idle = this.mctfc$behavior != null && this.mctfc$behavior.canGoIdle();
        // Own the citizen's working/idle look ourselves. The base sets it off the work-AI state, but CitizenAI
        // pauses our work AI while idle (so it never clears the "working" texture) and our cycle briefly dips
        // through IDLE while working (so it flickers). canGoIdle is polled continuously by CitizenAI in both
        // states, so it's the reliable single point: working ⇒ glasses-down texture, idle ⇒ default.
        if (this.mctfc$behavior != null)
        {
            final AbstractEntityCitizen citizen = worker();
            if (citizen != null)
            {
                citizen.setRenderMetadata(idle ? "" : RENDER_META_WORKING);
            }
        }
        return idle;
    }

    // --- FurnaceWorker bridge (no @Shadow: cast for public members, invoker for protected) ---

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
    public int buildingLevel()
    {
        final IBuilding building = building();
        return building == null ? 0 : building.getBuildingLevel();
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
        return ((AbstractEntityAIBasicInvoker) (Object) this).mctfc$walkToBuilding();
    }

    @Override
    public boolean gotoWorkPos(final BlockPos pos)
    {
        return ((AbstractEntityAIBasicInvoker) (Object) this).mctfc$walkToWorkPos(pos);
    }

    @Override
    public void delay(final int ticks)
    {
        ((AbstractEntityAIBasic<?, ?>) (Object) this).setDelay(ticks);
    }

    @Override
    public void countAction()
    {
        ((AbstractEntityAIBasic<?, ?>) (Object) this).incrementActionsDoneAndDecSaturation();
    }

    @Override
    public IAIState state()
    {
        return ((AbstractAISkeleton<?>) (Object) this).getState();
    }
}
