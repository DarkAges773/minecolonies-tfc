package com.structurizereplacements.mixin.slimcolonies;

import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.api.colony.IColony;
import no.monopixel.slimcolonies.api.colony.workorders.IWorkManager;
import no.monopixel.slimcolonies.core.colony.workorders.AbstractWorkOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * SlimColonies twin of the MineColonies {@code MixinAbstractWorkOrder} — see that class for the full design
 * rationale. Carries the player's replacement choices on a <b>work order</b> — the reliable home for a
 * <b>decoration</b> build, which (unlike a hut) has no {@code AbstractBuilding}. Adopts the staged choices
 * at {@code onAdded} and persists them in the work order NBT ({@code write}/{@code read}) so they survive a
 * save mid-build. {@code remap = false}: the colony mod's own members.
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

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lno/monopixel/slimcolonies/api/colony/workorders/IWorkManager;)V", at = @At("TAIL"))
    private void structurizereplacements$readChoices(final CompoundTag compound, final IWorkManager manager, final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(compound, ChoiceCodec.CHOICES_KEY);
    }
}
