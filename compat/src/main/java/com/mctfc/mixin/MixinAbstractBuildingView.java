package com.mctfc.mixin;

import com.mctfc.builder.ChoiceCodec;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
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
public class MixinAbstractBuildingView implements PlacementChoiceHolder
{
    @Unique
    private Map<Block, Block> mctfc$choices;

    @Override
    public void setReplacementChoices(final Map<Block, Block> choices)
    {
        this.mctfc$choices = choices;
    }

    @Override
    public Map<Block, Block> getReplacementChoices()
    {
        return this.mctfc$choices;
    }

    @Inject(method = "deserialize", at = @At("TAIL"), remap = false)
    private void mctfc$readChoicesFromBuffer(final FriendlyByteBuf buf, final CallbackInfo ci)
    {
        final Map<Block, Block> read = ChoiceCodec.read(buf);
        this.mctfc$choices = read.isEmpty() ? null : read;
    }
}
