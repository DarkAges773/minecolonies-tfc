package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.workorders.IWorkManager;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Carries the player's replacement choices on a MineColonies <b>work order</b> — the reliable home for a
 * <b>decoration</b> build, which (unlike a hut) has no {@code AbstractBuilding} and no player-accessible
 * controller (a plain decoration's anchor isn't a decoration controller). The work order exists for the
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
 * <p>Scope: the work order is removed when its build completes, so this store is per-build — the picks apply
 * to the build (and survive a save during it). A decoration has no persistent player-facing anchor, so it
 * can't be re-built/upgraded through a GUI anyway; re-placing it via the build tool re-stages the picks.
 */
@Mixin(value = AbstractWorkOrder.class, remap = false)
public class MixinAbstractWorkOrder implements PlacementChoiceHolder
{
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
        final Map<Block, Block> staged = StagedChoices.take(colony.getDimension(), getLocation());
        if (staged != null && !staged.isEmpty())
        {
            this.structurizereplacements$choices = staged;
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void structurizereplacements$writeChoices(final CompoundTag compound, final CallbackInfo ci)
    {
        ChoiceCodec.writeNbt(compound, ChoiceCodec.CHOICES_KEY, structurizereplacements$choices);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lcom/minecolonies/api/colony/workorders/IWorkManager;)V", at = @At("TAIL"))
    private void structurizereplacements$readChoices(final CompoundTag compound, final IWorkManager manager, final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(compound, ChoiceCodec.CHOICES_KEY);
    }
}
