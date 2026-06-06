package com.mctfc.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.dries007.tfc.util.EnvironmentHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the citizen "rest because of bad weather" decision respect TFC's <b>localized</b> precipitation.
 *
 * <p>MineColonies' {@code CitizenAI#calculateNextState} keys this off the <b>global</b> vanilla flag
 * {@code Level#isRaining()}. Under TFC that flag is only the dimension-wide on/off of the weather cycle —
 * rain actually falls per-position, gated by the local annual rainfall and temperature (so an arid colony
 * gets nothing and a cold one gets snow even while the global flag is on). Reading the global flag therefore
 * makes citizens rest during any rain cycle regardless of whether it's raining on them.
 *
 * <p>We redirect that {@code isRaining()} call to TFC's {@link EnvironmentHelpers#isRainingOrSnowing} at a
 * <b>fixed anchor</b> — the citizen's work building (else the colony center) — <i>not</i> the citizen's live
 * position. A fixed anchor is deliberate: TFC rain is localized, so sampling the moving citizen would create
 * a feedback loop at a rain border (in rain → rest → wander to a dry spot → work → walk back into rain →
 * rest …), flip-flopping every decide cycle (~0.5 s). Anchoring to the worksite means "is it raining on my
 * job?" — stable wherever the citizen wanders, so the only transitions are when the local weather genuinely
 * starts/stops (TFC's intensity ramp crosses the site's rainfall threshold ~once up, once down per event).
 *
 * <p>{@code isRainingOrSnowing} is true for rain <i>or</i> snow actually falling at the anchor, matching
 * vanilla's behaviour (its global flag is likewise on during snow) — just localized. {@code @Mixin(remap =
 * false)} targets MineColonies' own class/method; only the inner vanilla {@code isRaining} call is remapped
 * (its {@code @At}).
 */
@Mixin(value = CitizenAI.class, remap = false)
public abstract class MixinCitizenAI
{
    @Shadow
    @Final
    private EntityCitizen citizen;

    @Redirect(
            method = "calculateNextState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isRaining()Z",
                    remap = true))
    private boolean mctfc$localizedRainAtWorksite(final Level world)
    {
        return EnvironmentHelpers.isRainingOrSnowing(world, mctfc$rainAnchor());
    }

    /** A fixed position to test rain at: the work building, else the colony center, else (last resort) the citizen. */
    private BlockPos mctfc$rainAnchor()
    {
        final IBuilding work = citizen.getCitizenData().getWorkBuilding();
        if (work != null)
        {
            return work.getPosition();
        }
        final IColony colony = citizen.getCitizenColonyHandler().getColonyOrRegister();
        if (colony != null)
        {
            return colony.getCenter();
        }
        return citizen.blockPosition();
    }
}
