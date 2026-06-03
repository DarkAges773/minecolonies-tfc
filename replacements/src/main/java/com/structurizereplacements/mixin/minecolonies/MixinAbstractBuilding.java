package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists per-building replacement choices on the MineColonies building (colony NBT), so the builder
 * uses the player's GUI picks and they survive restarts and upgrades/rebuilds. The building implements
 * {@link PlacementChoiceHolder}; {@code BuildingChoiceResolver} (registered as the engine's
 * {@code ChoiceResolver}) reads these and feeds them to the builder's structure handler on demand. The
 * map is also synced to the client building view ({@code serializeToView} → {@code MixinAbstractBuildingView})
 * so the Build Options GUI can show/edit it. Part of the optional MineColonies integration.
 *
 * <p>{@code remap = false}: {@code serializeNBT}/{@code deserializeNBT} are Forge {@code INBTSerializable}
 * methods (stable names), {@code serializeToView} is MineColonies' own.
 */
@Mixin(AbstractBuilding.class)
public class MixinAbstractBuilding implements PlacementChoiceHolder
{
    @Unique private static final String SREP_KEY = "structurizereplacements_choices";

    @Unique private Map<Block, Block> structurizereplacements$choices;

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

    /**
     * Append the choice map to the building's client-sync buffer so the client view can display it (and
     * the Build Options list/preview can reflect it). Symmetric with
     * {@code MixinAbstractBuildingView#deserialize}; always writes a count (self-describing) so it stays
     * aligned with whatever MineColonies wrote before us.
     */
    @Inject(method = "serializeToView", at = @At("TAIL"), remap = false)
    private void structurizereplacements$writeChoicesToView(final FriendlyByteBuf buf, final boolean fullSync, final CallbackInfo ci)
    {
        ChoiceCodec.write(buf, structurizereplacements$choices);
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void structurizereplacements$writeChoices(final CallbackInfoReturnable<CompoundTag> cir)
    {
        if (structurizereplacements$choices == null || structurizereplacements$choices.isEmpty())
        {
            return;
        }
        final ListTag list = new ListTag();
        structurizereplacements$choices.forEach((from, to) -> {
            final ResourceLocation f = ForgeRegistries.BLOCKS.getKey(from);
            final ResourceLocation t = ForgeRegistries.BLOCKS.getKey(to);
            if (f != null && t != null)
            {
                final CompoundTag entry = new CompoundTag();
                entry.putString("from", f.toString());
                entry.putString("to", t.toString());
                list.add(entry);
            }
        });
        if (!list.isEmpty())
        {
            cir.getReturnValue().put(SREP_KEY, list);
        }
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = false)
    private void structurizereplacements$readChoices(final CompoundTag tag, final CallbackInfo ci)
    {
        if (!tag.contains(SREP_KEY, Tag.TAG_LIST))
        {
            return;
        }
        final Map<Block, Block> read = new HashMap<>();
        final ListTag list = tag.getList(SREP_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            final Block from = structurizereplacements$block(entry.getString("from"));
            final Block to = structurizereplacements$block(entry.getString("to"));
            if (from != null && to != null)
            {
                read.put(from, to);
            }
        }
        this.structurizereplacements$choices = read.isEmpty() ? null : read;
    }

    @Unique
    private static Block structurizereplacements$block(final String id)
    {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        return (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) ? ForgeRegistries.BLOCKS.getValue(rl) : null;
    }
}
