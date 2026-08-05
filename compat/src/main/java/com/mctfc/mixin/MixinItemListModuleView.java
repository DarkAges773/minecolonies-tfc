package com.mctfc.mixin;

import com.mctfc.furnace.FurnaceFuelScope;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBeekeeper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import static com.minecolonies.api.util.constant.BuildingConstants.BUILDING_FLOWER_LIST;
import static com.minecolonies.api.util.constant.BuildingConstants.FUEL_LIST;

/**
 * Two client-side tweaks to MineColonies' item-list module view:
 *
 * <p><b>1. Hides the Beekeeper's flower list tab in a FirmaLife world.</b> The vanilla apiary keeps a hut flower
 * list (the {@code "flowers"} list) to drive bee breeding; a FirmaLife apiary breeds from <i>world</i> flowers near
 * the hive instead, so that whole screen is inert (and {@code MixinEntityAIWorkBeekeeper} already bypasses the
 * vanilla flower requirement). {@code ItemListModuleView} doesn't override {@code isPageVisible()} (it inherits the
 * {@code true} default), so we add an override that returns {@code false} for the beekeeper's flower list when
 * FirmaLife is loaded; {@code AbstractBuildingWindow} skips pages whose view reports {@code !isPageVisible()}.
 *
 * <p><b>2. Scopes the fuel list per furnace hut to TFC's device fuel tags</b> (see {@link FurnaceFuelScope}). The
 * shared {@code itemlist_fuel} module offers the same global TFC-fuel candidate set to every furnace hut; we filter
 * that set down to the hut's TFC device tag — the Smeltery (charcoal forge) to {@code forge_fuel} (coals), the Cook
 * (firepit) to {@code firepit_fuel} (woods) — so each hut only lists fuels its TFC counterpart would burn. Other
 * huts are left untouched.
 *
 * <p>{@code remap = false} — MineColonies' own (client) class.
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

    /**
     * Filter the fuel list's candidate items to the hut's TFC device fuel tag. {@code getAllItems()} returns the
     * candidate-supplier function; we wrap it so the GUI only offers fuels in the scoped tag (for the Smeltery /
     * Cook). Non-fuel lists and unscoped huts are returned unchanged.
     */
    @Inject(method = "getAllItems", at = @At("RETURN"), cancellable = true)
    private void mctfc$scopeFuelList(final CallbackInfoReturnable<Function<IBuildingView, Set<ItemStorage>>> cir)
    {
        final ItemListModuleView self = (ItemListModuleView) (Object) this;
        final IBuildingView view = self.getBuildingView();
        if (!FUEL_LIST.equals(self.getId()) || view == null || view.getBuildingType() == null)
        {
            return;
        }
        final TagKey<Item> tag = FurnaceFuelScope.tagFor(view.getBuildingType().getRegistryName().getPath());
        if (tag == null)
        {
            return;
        }
        final Function<IBuildingView, Set<ItemStorage>> base = cir.getReturnValue();
        cir.setReturnValue(bv -> {
            final Set<ItemStorage> scoped = new LinkedHashSet<>();
            for (final ItemStorage storage : base.apply(bv))
            {
                if (storage.getItemStack().is(tag))
                {
                    scoped.add(storage);
                }
            }
            return scoped;
        });
    }
}
