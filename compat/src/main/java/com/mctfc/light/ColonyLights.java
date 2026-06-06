package com.mctfc.light;

import com.minecolonies.api.colony.IColonyManager;
import com.mctfc.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared gate for the "TFC light sources never burn out inside a colony" feature (see {@code MixinTfcLightBlocks}
 * for torches / candles / jack-o'-lanterns and {@code MixinLampBlockEntity} for metal lamps).
 *
 * <p>{@link #keepLit} answers "should the light at this position be prevented from burning out?": only server-side,
 * only when the feature is enabled, and only when the position lies inside a MineColonies colony's claimed area
 * ({@link IColonyManager#getColonyByPosFromWorld} — the chunk-owning-colony capability, reliable on the server).
 * The burn-out hooks run on random ticks, so this lookup is infrequent and cheap.
 */
public final class ColonyLights
{
    private ColonyLights() {}

    /** True if a TFC light source at {@code pos} should be kept from burning out (in-colony, server-side, enabled). */
    public static boolean keepLit(final Level level, final BlockPos pos)
    {
        if (!Config.keepColonyLightsLit || level == null || level.isClientSide)
        {
            return false;
        }
        return IColonyManager.getInstance().getColonyByPosFromWorld(level, pos) != null;
    }
}
