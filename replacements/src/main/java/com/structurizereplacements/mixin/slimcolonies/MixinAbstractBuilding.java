package com.structurizereplacements.mixin.slimcolonies;

import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import no.monopixel.slimcolonies.core.colony.buildings.AbstractBuilding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * SlimColonies twin of the MineColonies {@code MixinAbstractBuilding} — see that class for the full design
 * rationale. Persists per-building replacement choices on the SlimColonies building (colony NBT) and syncs
 * them to the client view ({@code serializeToView} → {@code MixinAbstractBuildingView}). Part of the
 * optional SlimColonies integration.
 *
 * <p>{@code remap = false}: {@code serializeNBT}/{@code deserializeNBT} are Forge {@code INBTSerializable}
 * methods (stable names), {@code serializeToView} is the colony mod's own.
 */
@Mixin(AbstractBuilding.class)
public class MixinAbstractBuilding implements PlacementChoiceHolder, MineshaftChoiceHolder
{
    @Unique private Map<Block, Block> structurizereplacements$choices;
    @Unique private Map<Block, Block> structurizereplacements$mineshaftChoices;

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

    @Override
    public void setMineshaftChoices(final Map<Block, Block> choices)
    {
        this.structurizereplacements$mineshaftChoices = choices;
    }

    @Override
    public Map<Block, Block> getMineshaftChoices()
    {
        return this.structurizereplacements$mineshaftChoices;
    }

    /**
     * Append both choice maps to the building's client-sync buffer so the client view can display them.
     * Symmetric with {@code MixinAbstractBuildingView#deserialize} — the two maps are written and read in
     * the <b>same order</b> (hut, then mineshaft); each is self-describing (writes a count), so the buffer
     * stays aligned with whatever the colony mod wrote before us.
     */
    @Inject(method = "serializeToView", at = @At("TAIL"), remap = false)
    private void structurizereplacements$writeChoicesToView(final FriendlyByteBuf buf, final boolean fullSync, final CallbackInfo ci)
    {
        ChoiceCodec.write(buf, structurizereplacements$choices);
        ChoiceCodec.write(buf, structurizereplacements$mineshaftChoices);
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void structurizereplacements$writeChoices(final CallbackInfoReturnable<CompoundTag> cir)
    {
        // Both maps use the shared ChoiceCodec NBT shape (keyed, so order is irrelevant). Write them
        // independently — a building may have only one of them (e.g. the miner sets mineshaft picks but no
        // hut-building picks), so neither write may short-circuit the other.
        final CompoundTag tag = cir.getReturnValue();
        ChoiceCodec.writeNbt(tag, ChoiceCodec.CHOICES_KEY, structurizereplacements$choices);
        ChoiceCodec.writeNbt(tag, ChoiceCodec.MINESHAFT_CHOICES_KEY, structurizereplacements$mineshaftChoices);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = false)
    private void structurizereplacements$readChoices(final CompoundTag tag, final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(tag, ChoiceCodec.CHOICES_KEY);
        this.structurizereplacements$mineshaftChoices = ChoiceCodec.readNbt(tag, ChoiceCodec.MINESHAFT_CHOICES_KEY);
    }
}
