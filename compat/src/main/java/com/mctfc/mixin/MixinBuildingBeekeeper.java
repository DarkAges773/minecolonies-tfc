package com.mctfc.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Drops the vanilla Beekeeper's auto-kept consumables for a <b>FirmaLife</b> apiary. The constructor seeds three
 * {@code keepX} entries — keep 1 shears, 4 glass bottles, a stack of flowers — which make the hut perpetually
 * request those items. None apply to a FirmaLife apiary: the worker harvests honey into {@code tfc:empty_jar},
 * scrapes wax with a knife, and FirmaLife breeds bees from <i>world</i> flowers (not a hut list) — all requested
 * (or not needed) by {@code MixinEntityAIWorkBeekeeper}, not these keeps.
 *
 * <p>The three are the only {@code Map.put} calls in {@code <init>}, so we redirect that put and skip it when
 * FirmaLife is present. {@code keepX} isn't serialized — it's rebuilt in {@code <init>} on every load — so this
 * also cleans up pre-existing huts. {@code remap = false} — MineColonies' own class; {@code Map.put} is a JDK
 * method (unaffected by remap).
 */
@Mixin(value = BuildingBeekeeper.class, remap = false)
public abstract class MixinBuildingBeekeeper
{
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object mctfc$skipVanillaKeepsForFirmaLife(final Map<Object, Object> map, final Object key, final Object value)
    {
        if (ModList.get().isLoaded("firmalife"))
        {
            return null; // FirmaLife apiary: no shears/glass-bottle/flower keeps
        }
        return map.put(key, value);
    }
}
