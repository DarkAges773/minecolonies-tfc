package com.mctfc.forge;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IModuleWithExternalBlocks;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovery module for the heat-forge — the fork-agnostic parallel of MineColonies' {@code FurnaceUserModule}, tracking
 * {@link HeatForgeBlock} positions registered to a hut (Smeltery / Restaurant / Kitchen) instead of vanilla furnaces.
 * Grafted onto those buildings (they don't declare it in their {@code BuildingEntry}) by
 * {@code MixinAbstractBuilding}; the tend-AI reads {@link #getControllers(Level)} to service each multiblock as one.
 *
 * <p>Chosen over mixing into {@code FurnaceUserModule} so the native furnace path never sees forge positions it can't
 * drive (see {@code docs/tfc-forge-multiblock.md} §12).
 */
public class ForgeUserModule extends AbstractBuildingModule implements IPersistentModule, IModuleWithExternalBlocks
{
    /**
     * The module producer — required by {@code AbstractBuilding#registerModule} (it keys the module by the producer's
     * runtime id and namespaces its NBT by the producer {@code key}). No view producer (server-only, like
     * {@code FurnaceUserModule}). The {@code "mctfc_forge"} key is the stable save id — never change it. Created once
     * (its ctor self-registers in MineColonies' module registry); grafted onto buildings by {@code MixinAbstractBuilding}.
     */
    public static final BuildingEntry.ModuleProducer<ForgeUserModule, IBuildingModuleView> PRODUCER =
            new BuildingEntry.ModuleProducer<>("mctfc_forge", ForgeUserModule::new, null);

    private static final String TAG_FORGES = "mctfcForges";
    private static final String TAG_POS = "pos";

    /** Every registered forge block (a member, not necessarily a controller). */
    private final List<BlockPos> forges = new ArrayList<>();

    @Override
    public void onBlockPlacedInBuilding(@NotNull final BlockState blockState, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        if (blockState.getBlock() instanceof HeatForgeBlock && !forges.contains(pos))
        {
            forges.add(pos.immutable());
            markDirty();
        }
    }

    @Override
    public List<BlockPos> getRegisteredBlocks()
    {
        return new ArrayList<>(forges);
    }

    /** Every registered forge block position (a defensive copy). */
    public List<BlockPos> getForges()
    {
        return new ArrayList<>(forges);
    }

    /**
     * The distinct <b>controller</b> positions among the registered forges — one per multiblock, so the tend-AI
     * services each group once. Prunes positions whose block is no longer a forge (broken/replaced) as it goes.
     */
    public List<BlockPos> getControllers(final Level level)
    {
        final Set<BlockPos> controllers = new LinkedHashSet<>();
        final List<BlockPos> stale = new ArrayList<>();
        for (final BlockPos pos : forges)
        {
            if (!ForgeMultiblock.isForge(level, pos))
            {
                stale.add(pos);
                continue;
            }
            controllers.add(ForgeMultiblock.groupOf(level, pos).controller());
        }
        if (!stale.isEmpty())
        {
            forges.removeAll(stale);
            markDirty();
        }
        return new ArrayList<>(controllers);
    }

    /** The controller BE at {@code pos}, or {@code null} if it isn't a loaded forge controller. */
    public HeatForgeBlockEntity controllerAt(final Level level, final BlockPos pos)
    {
        return level.getBlockEntity(pos) instanceof HeatForgeBlockEntity be ? be : null;
    }

    /** Drop a registered forge position (mirrors {@code FurnaceUserModule#removeFromFurnaces}). */
    public void removeForge(final BlockPos pos)
    {
        if (forges.remove(pos))
        {
            markDirty();
        }
    }

    @Override
    public void serializeNBT(final CompoundTag compound)
    {
        final ListTag list = new ListTag();
        for (final BlockPos pos : forges)
        {
            final CompoundTag entry = new CompoundTag();
            entry.put(TAG_POS, NbtUtils.writeBlockPos(pos));
            list.add(entry);
        }
        compound.put(TAG_FORGES, list);
    }

    @Override
    public void deserializeNBT(final CompoundTag compound)
    {
        forges.clear();
        final ListTag list = compound.getList(TAG_FORGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            if (entry.contains(TAG_POS))
            {
                forges.add(NbtUtils.readBlockPos(entry.getCompound(TAG_POS)));
            }
        }
    }
}
