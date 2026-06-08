package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.MineshaftChoiceHolder;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Client mirror of the building's replacement choices: reads the trailer
 * {@code MixinAbstractBuilding#serializeToView} appends. The view implements {@link PlacementChoiceHolder}
 * so the client-side structure handler's {@code ChoiceResolver} (and the Build Options picker) can read
 * the building's actual stored choices — not just the global session picks.
 *
 * <p>{@code remap = false}: {@code deserialize} is MineColonies' own method.
 */
@Mixin(AbstractBuildingView.class)
public class MixinAbstractBuildingView implements PlacementChoiceHolder, MineshaftChoiceHolder
{
    @Unique
    private Map<Block, Block> structurizereplacements$choices;

    @Unique
    private Map<Block, Block> structurizereplacements$mineshaftChoices;

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
     * Read both choice maps in the same order {@code MixinAbstractBuilding#serializeToView} wrote them
     * (hut, then mineshaft).
     */
    @Inject(method = "deserialize", at = @At("TAIL"), remap = false)
    private void structurizereplacements$readChoicesFromBuffer(final FriendlyByteBuf buf, final CallbackInfo ci)
    {
        final Map<Block, Block> hut = ChoiceCodec.read(buf);
        this.structurizereplacements$choices = hut.isEmpty() ? null : hut;
        final Map<Block, Block> mineshaft = ChoiceCodec.read(buf);
        this.structurizereplacements$mineshaftChoices = mineshaft.isEmpty() ? null : mineshaft;
    }
}
