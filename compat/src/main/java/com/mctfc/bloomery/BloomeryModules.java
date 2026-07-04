package com.mctfc.bloomery;

import com.mctfc.item.ModItems;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;

/**
 * The two module producers for the Smeltery's bloomery feature (see {@code docs/tfc-bloomery-smelter.md}). Deliberately
 * split so <b>neither is synced</b> — sidestepping MineColonies' fragile module-runtime-id sync (a mismatch there
 * corrupts the whole building-view deserialize), exactly as {@code ForgeUserModule} and MineColonies' own tool tabs do:
 *
 * <ul>
 *   <li>{@link #STORAGE} — the server-side {@link BloomeryUserModule} (marked positions + NBT). {@code viewProducer =
 *       null} ⇒ {@code hasView()} false ⇒ never serialized to the client. Grafted onto {@code BuildingSmeltery} by
 *       {@code MixinAbstractBuilding}.</li>
 *   <li>{@link #TOOL} — the client-only "give scepter" tab (a reused {@link ToolModuleView} carrying our wand).
 *       {@code moduleProducer = null} ⇒ no server module ⇒ the server never serializes it; the client having it extra is
 *       harmless. Grafted onto the Smeltery <b>view</b> by {@code MixinBuildingEntry}.</li>
 * </ul>
 *
 * <p>Server-safe: {@link ToolModuleView} and {@link ModItems} are named only inside {@link #TOOL}'s deferred view lambda,
 * which runs solely client-side (at building-view creation), so constructing these producers loads neither class.
 */
public final class BloomeryModules
{
    private BloomeryModules() {}

    /** Server store of marked bloomery positions ({@link BloomeryUserModule}); not synced (no view producer). */
    public static final BuildingEntry.ModuleProducer<BloomeryUserModule, IBuildingModuleView> STORAGE =
            new BuildingEntry.ModuleProducer<>("mctfc_bloomery", BloomeryUserModule::new, null);

    /** Client-only "give bloomery wand" tab (reused {@link ToolModuleView}); no server module, so never synced. */
    public static final BuildingEntry.ModuleProducer<IBuildingModule, ToolModuleView> TOOL =
            new BuildingEntry.ModuleProducer<>("mctfc_bloomery_tool", null,
                    () -> () -> new ToolModuleView(ModItems.BLOOMERY_SCEPTER.get()));
}
