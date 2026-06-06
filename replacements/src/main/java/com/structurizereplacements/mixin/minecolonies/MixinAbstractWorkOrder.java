package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.workorders.IWorkManager;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Carries the player's replacement choices on a MineColonies <b>work order</b> — the reliable home for a
 * <b>decoration</b> build, which (unlike a hut) has no {@code AbstractBuilding} and whose
 * {@code TileEntityDecorationController} is placed <i>during</i> the build (so it does not exist at build
 * start, when the structure handler resolves its choices once). The work order, by contrast, exists for the
 * whole build and its {@code getLocation()} equals the structure handler's {@code worldPos} (see
 * {@code BuildingStructureHandler}'s {@code super(world, workOrder.getLocation(), …)}), so
 * {@code BuildingChoiceResolver} can find it by position.
 *
 * <p>Adoption: when a new work order is added ({@code onAdded}, not reading from NBT) we take the placing
 * player's choices staged by {@code MixinBlueprintPlacementHandling} at the placement position (==
 * {@code getLocation()}) — exactly the hut flow, but onto the work order. Persisted in the work order NBT
 * ({@code write}/{@code read}) so it survives a save mid-build.
 *
 * <p>This is on the base {@code AbstractWorkOrder} (decoration and building work orders share
 * {@code write}/{@code read}/{@code onAdded}); for a building the staged choices were already taken by
 * {@code MixinRegisteredStructureManager} at building creation, so {@code take} returns nothing here and the
 * building's own store stays authoritative. {@code remap = false}: MineColonies' own members.
 *
 * <p>Across rebuilds/upgrades: a work order is removed when its build completes, so on removal we copy the
 * choices onto the decoration's controller block entity (now placed) — which persists for the decoration's
 * lifetime ({@code MixinTileEntityDecorationController}). A re-issued work order then re-adopts them from the
 * controller ({@code onAdded}, when no fresh staged picks exist), so an upgrade/rebuild keeps the picks
 * without the player re-choosing.
 */
@Mixin(value = AbstractWorkOrder.class, remap = false)
public class MixinAbstractWorkOrder implements PlacementChoiceHolder
{
    @Unique private static final String SREP_KEY = "structurizereplacements_choices";

    @Unique private Map<Block, Block> structurizereplacements$choices;

    @Shadow public BlockPos getLocation() { return null; }

    @Override
    public void setReplacementChoices(final Map<Block, Block> choices)
    {
        this.structurizereplacements$choices = choices;
    }

    @Override
    public Map<Block, Block> getReplacementChoices()
    {
        return this.structurizereplacements$choices;
    }

    @Inject(method = "onAdded", at = @At("TAIL"))
    private void structurizereplacements$adoptStaged(final IColony colony, final boolean readingFromNbt, final CallbackInfo ci)
    {
        if (readingFromNbt || (structurizereplacements$choices != null && !structurizereplacements$choices.isEmpty()))
        {
            return;
        }
        // Fresh placement: take the placing player's staged choices.
        final Map<Block, Block> staged = StagedChoices.take(getLocation());
        if (staged != null && !staged.isEmpty())
        {
            this.structurizereplacements$choices = staged;
            return;
        }
        // Rebuild/upgrade: no fresh picks — re-adopt from the controller persisted at this position (set there
        // by a prior build's onRemoved).
        final Map<Block, Block> persisted = structurizereplacements$controllerChoices(colony);
        if (persisted != null && !persisted.isEmpty())
        {
            this.structurizereplacements$choices = persisted;
        }
    }

    /**
     * On completion (work order removed) copy the choices onto the decoration controller now standing at this
     * position, so they persist for the decoration's lifetime and survive into the next build/upgrade.
     */
    @Inject(method = "onRemoved", at = @At("TAIL"))
    private void structurizereplacements$persistToController(final IColony colony, final CallbackInfo ci)
    {
        if (structurizereplacements$choices == null || structurizereplacements$choices.isEmpty())
        {
            return;
        }
        final BlockEntity be = structurizereplacements$controllerAt(colony);
        if (be instanceof PlacementChoiceHolder holder)
        {
            holder.setReplacementChoices(structurizereplacements$choices);
            be.setChanged();
        }
    }

    @Unique
    private Map<Block, Block> structurizereplacements$controllerChoices(final IColony colony)
    {
        final BlockEntity be = structurizereplacements$controllerAt(colony);
        return (be instanceof PlacementChoiceHolder holder) ? holder.getReplacementChoices() : null;
    }

    @Unique
    private BlockEntity structurizereplacements$controllerAt(final IColony colony)
    {
        if (colony == null)
        {
            return null;
        }
        final Level world = colony.getWorld();
        return (world != null && world.isLoaded(getLocation())) ? world.getBlockEntity(getLocation()) : null;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void structurizereplacements$writeChoices(final CompoundTag compound, final CallbackInfo ci)
    {
        ChoiceCodec.writeNbt(compound, SREP_KEY, structurizereplacements$choices);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lcom/minecolonies/api/colony/workorders/IWorkManager;)V", at = @At("TAIL"))
    private void structurizereplacements$readChoices(final CompoundTag compound, final IWorkManager manager, final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(compound, SREP_KEY);
    }
}
