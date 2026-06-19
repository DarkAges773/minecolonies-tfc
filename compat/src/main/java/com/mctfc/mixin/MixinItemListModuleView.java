package com.mctfc.mixin;

import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;

import static com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST;

/**
 * Hides the Beekeeper's <b>flower list</b> tab in a FirmaLife world. The vanilla apiary keeps a hut flower list
 * (the {@code "flowers"} {@link ItemListModuleView}) to drive bee breeding; a FirmaLife apiary breeds from
 * <i>world</i> flowers near the hive instead, so that whole screen is inert (and {@code MixinEntityAIWorkBeekeeper}
 * already bypasses the vanilla flower requirement).
 *
 * <p>{@code ItemListModuleView} doesn't override {@code isPageVisible()} (it inherits the {@code true} default), so
 * we add an override that returns {@code false} for the beekeeper's flower list when FirmaLife is loaded.
 * {@code AbstractBuildingWindow} skips pages whose view reports {@code !isPageVisible()}. The
 * {@code BuildingBeekeeper.View} check keeps the Florist's flower list (and every other item list) untouched.
 * {@code remap = false} — MineColonies' own (client) class.
 */
@Mixin(value = ItemListModuleView.class, remap = false)
public abstract class MixinItemListModuleView
{
    public boolean isPageVisible()
    {
        final ItemListModuleView self = (ItemListModuleView) (Object) this;
        if (ModList.get().isLoaded("firmalife")
              && BUILDING_FLOWER_LIST.equals(self.getId())
              && self.getBuildingView() instanceof BuildingBeekeeper.View)
        {
            return false;
        }
        return true;
    }
}
