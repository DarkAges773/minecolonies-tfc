package com.mctfc.mixin;

import com.mctfc.Config;
import com.mctfc.bloomery.BloomeryModules;
import com.mctfc.item.ModItems;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grafts the "give bloomery wand" tab ({@link BloomeryModules#TOOL}, a reused {@link ToolModuleView}) onto the
 * <b>Smeltery's building view</b> — MineColonies' own {@code produceBuildingView} only registers the entry's declared
 * module views, and the Smeltery is MineColonies-owned, so we add ours at construction. This is the client-side twin of
 * {@code MixinAbstractBuilding} grafting {@link com.mctfc.bloomery.BloomeryUserModule} server-side.
 *
 * <p>Matched by the entry's <b>registry name</b> ({@code "smeltery"}), not a view class: the Smeltery uses the shared
 * {@code EmptyView}, so there's no {@code BuildingSmeltery.View} to {@code instanceof}. Gated on the config not disabling
 * the feature (empty cap list ⇒ no tab). The tool view is <b>never synced</b> (no server counterpart), and its producer's
 * globally-unique runtime id can't collide with a MineColonies module, so registering it is safe. {@code remap = false}:
 * {@code produceBuildingView} / {@code getRegistryName} / {@code registerModule} are MineColonies' own methods; this runs
 * client-side (building views are client-only).
 */
@Mixin(BuildingEntry.class)
public abstract class MixinBuildingEntry
{
    @Inject(method = "produceBuildingView", at = @At("RETURN"), remap = false)
    private void mctfc$graftBloomeryTool(final BlockPos position, final IColonyView colony,
            final CallbackInfoReturnable<IBuildingView> cir)
    {
        final BuildingEntry self = (BuildingEntry) (Object) this;
        if (Config.bloomeryCapPerLevel.isEmpty()
                || self.getRegistryName() == null
                || !"smeltery".equals(self.getRegistryName().getPath()))
        {
            return;
        }
        final IBuildingView view = cir.getReturnValue();
        if (view != null)
        {
            view.registerModule(new ToolModuleView(ModItems.BLOOMERY_SCEPTER.get()).setProducer(BloomeryModules.TOOL));
        }
    }
}
