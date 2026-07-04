package com.mctfc.bloomery;

import com.mctfc.Config;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSmeltery;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores the TFC bloomeries a player has <b>wand-marked</b> onto a Smeltery for the Smelter to tend (see
 * {@code docs/tfc-bloomery-smelter.md}). The parallel of {@code ForgeUserModule} for the iron path, but the positions
 * are <b>player-assigned</b> (via {@link com.mctfc.item.ItemBloomeryScepter}), not builder-placed — so there is no
 * {@code onBlockPlacedInBuilding}, and the module is <b>not</b> an {@code IModuleWithExternalBlocks}.
 *
 * <p><b>Server-only, not synced.</b> Its producer ({@link BloomeryModules#STORAGE}) has a {@code null} view producer, so
 * the module is never serialized to the client (exactly like {@code ForgeUserModule}). That deliberately avoids the
 * fragile module-runtime-id sync; the wand's overlay boxes will be fed by a dedicated packet in a later slice.
 *
 * <p><b>Pruning is self-healing but conservative</b> (unlike MineColonies' Beekeeper, which strips a hive the instant a
 * block read isn't a beehive — no chunk-load guard, on any non-beehive state). {@link #pruneStale} keeps a mark through
 * chunk-unloads (can't verify → keep) and transient unformed structures (mid-rebuild), removing only a position a
 * loaded chunk confirms is no longer a bloomery block.
 */
public class BloomeryUserModule extends AbstractBuildingModule implements IPersistentModule
{
    private static final String TAG_BLOOMERIES = "mctfcBloomeries";
    private static final String TAG_POS        = "pos";

    /** Interaction key: a marked bloomery whose structure is broken/incomplete (auto-clears once every mark is formed). */
    public static final String MALFORMED_INTERACTION = "com.mctfc.interaction.bloomery.malformed";

    /** The marked bloomery positions (insertion-ordered so the AI services them deterministically). */
    private final Set<BlockPos> bloomeries = new LinkedHashSet<>();

    /** The marked bloomery positions (an unmodifiable snapshot). */
    public Set<BlockPos> getBloomeries()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(bloomeries));
    }

    /** Whether {@code pos} is currently marked. */
    public boolean contains(final BlockPos pos)
    {
        return bloomeries.contains(pos);
    }

    /** Mark a bloomery (idempotent). */
    public void add(final BlockPos pos)
    {
        if (bloomeries.add(pos.immutable()))
        {
            markDirty();
        }
    }

    /** Unmark a bloomery. */
    public void remove(final BlockPos pos)
    {
        if (bloomeries.remove(pos))
        {
            markDirty();
        }
    }

    /** How many bloomeries this hut may mark at its current level (from {@link Config#maxBloomeries}); 0 disables it. */
    public int getMaximumBloomeries()
    {
        return building == null ? 0 : Config.maxBloomeries(building.getBuildingLevel());
    }

    /**
     * Drop marks that a <b>loaded</b> chunk confirms are no longer a bloomery block (player removed/replaced it). Keeps a
     * mark whose chunk is unloaded (unverifiable) or whose bloomery block is present but not yet a formed multiblock
     * (mid-rebuild) — strict at mark time (the wand requires {@code isFormed}), lenient on retention. Cheap: bounded by
     * the small mark set, and {@code hasChunkAt} avoids force-loading a remote chunk just to validate.
     */
    public void pruneStale(final Level level)
    {
        if (level == null || bloomeries.isEmpty())
        {
            return;
        }
        final List<BlockPos> stale = new ArrayList<>();
        for (final BlockPos pos : bloomeries)
        {
            if (level.hasChunkAt(pos) && !TfcBloomery.isBloomery(level.getBlockState(pos)))
            {
                stale.add(pos);
            }
        }
        if (!stale.isEmpty())
        {
            bloomeries.removeAll(stale);
            markDirty();
        }
    }

    /**
     * Whether any marked bloomery is present in the world but <b>not a formed multiblock</b> (structure broken/incomplete
     * after marking — the mark-time gate blocks marking a malformed one, so this only arises from later damage). Drives
     * the {@link #MALFORMED_INTERACTION} worker warning; unloaded/removed marks don't count (they're pruned or unverifiable).
     */
    public boolean hasMalformed(final Level level)
    {
        if (level == null || bloomeries.isEmpty())
        {
            return false;
        }
        for (final BlockPos pos : bloomeries)
        {
            if (level.hasChunkAt(pos) && TfcBloomery.isBloomery(level.getBlockState(pos)) && !TfcBloomery.isFormed(level, pos))
            {
                return true;
            }
        }
        return false;
    }

    /** {@link #hasMalformed} for a building — resolves the module + world; false for a non-Smeltery or module-less hut. */
    public static boolean hasMalformedMarked(final IBuilding building)
    {
        if (!(building instanceof BuildingSmeltery))
        {
            return false;
        }
        final BloomeryUserModule module = building.getFirstModuleOccurance(BloomeryUserModule.class);
        return module != null && module.hasMalformed(building.getColony().getWorld());
    }

    @Override
    public void serializeNBT(final CompoundTag compound)
    {
        final ListTag list = new ListTag();
        for (final BlockPos pos : bloomeries)
        {
            final CompoundTag entry = new CompoundTag();
            entry.put(TAG_POS, NbtUtils.writeBlockPos(pos));
            list.add(entry);
        }
        compound.put(TAG_BLOOMERIES, list);
    }

    @Override
    public void deserializeNBT(final CompoundTag compound)
    {
        bloomeries.clear();
        final ListTag list = compound.getList(TAG_BLOOMERIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            if (entry.contains(TAG_POS))
            {
                bloomeries.add(NbtUtils.readBlockPos(entry.getCompound(TAG_POS)));
            }
        }
    }
}
