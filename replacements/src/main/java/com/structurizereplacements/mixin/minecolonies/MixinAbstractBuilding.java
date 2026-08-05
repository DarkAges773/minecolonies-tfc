package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Persists per-building replacement choices on the MineColonies building (colony NBT), so the builder
 * uses the player's GUI picks and they survive restarts and upgrades/rebuilds. The building implements
 * {@link PlacementChoiceHolder}; {@code BuildingChoiceResolver} (registered as the engine's
 * {@code ChoiceResolver}) reads these and feeds them to the builder's structure handler on demand. The
 * map is also synced to the client building view ({@code serializeToView} → {@code MixinAbstractBuildingView})
 * so the Build Options GUI can show/edit it. Part of the optional MineColonies integration.
 *
 * <p>{@code remap = false}: {@code serializeNBT}/{@code deserializeNBT} are {@code INBTSerializable}
 * methods (stable names), {@code serializeToView} is MineColonies' own.
 *
 * <p><b>1.21 signatures.</b> All three targets moved: the NBT pair now threads a
 * {@link HolderLookup.Provider} ({@code serializeNBT(Provider)} / {@code deserializeNBT(Provider, CompoundTag)})
 * and the view buffer is a {@link RegistryFriendlyByteBuf}. The explicit descriptors below matter — the class
 * carries {@code Tag}-returning bridge overloads of both NBT methods, so a bare method name is ambiguous.
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
     * Append both choice maps to the building's client-sync buffer so the client view can display them (the
     * Build Options list/preview reflects the hut map; the miner's settings picker reads the mineshaft map).
     * Symmetric with {@code MixinAbstractBuildingView#deserialize} — the two maps are written and read in
     * the <b>same order</b> (hut, then mineshaft); each is self-describing (writes a count), so the buffer
     * stays aligned with whatever MineColonies wrote before us.
     */
    @Inject(method = "serializeToView", at = @At("TAIL"), remap = false)
    private void structurizereplacements$writeChoicesToView(final RegistryFriendlyByteBuf buf, final boolean fullSync, final CallbackInfo ci)
    {
        ChoiceCodec.write(buf, structurizereplacements$choices);
        ChoiceCodec.write(buf, structurizereplacements$mineshaftChoices);
    }

    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"), remap = false)
    private void structurizereplacements$writeChoices(final HolderLookup.Provider provider,
                                                      final CallbackInfoReturnable<CompoundTag> cir)
    {
        // Both maps use the shared ChoiceCodec NBT shape (keyed, so order is irrelevant). Write them
        // independently — a building may have only one of them (e.g. the miner sets mineshaft picks but no
        // hut-building picks), so neither write may short-circuit the other.
        final CompoundTag tag = cir.getReturnValue();
        ChoiceCodec.writeNbt(tag, ChoiceCodec.CHOICES_KEY, structurizereplacements$choices);
        ChoiceCodec.writeNbt(tag, ChoiceCodec.MINESHAFT_CHOICES_KEY, structurizereplacements$mineshaftChoices);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("TAIL"), remap = false)
    private void structurizereplacements$readChoices(final HolderLookup.Provider provider, final CompoundTag tag,
                                                     final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(tag, ChoiceCodec.CHOICES_KEY);
        this.structurizereplacements$mineshaftChoices = ChoiceCodec.readNbt(tag, ChoiceCodec.MINESHAFT_CHOICES_KEY);
    }
}
