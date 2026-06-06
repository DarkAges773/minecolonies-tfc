package com.structurizereplacements.mixin.minecolonies;

import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Persists per-decoration replacement choices on the MineColonies decoration controller block entity (the
 * anchor placed for a decoration that has no hut block). This is the decoration analogue of
 * {@link MixinAbstractBuilding}: there is no {@code AbstractBuilding} for a decoration, but the controller
 * <b>is</b> a block entity at the build origin (the structure handler's {@code worldPos} ==
 * {@code workOrder.getLocation()} == this controller's position — see {@code BuildingStructureHandler}), it
 * persists for the decoration's whole lifetime, and it already syncs to the client via the standard block
 * entity update packet ({@code getUpdateTag} -> {@code saveAdditional}; {@code onDataPacket} -> {@code load}).
 *
 * <p>So we store the choice map in the controller's NBT — which gives both colony-save persistence and
 * client sync for free — and let {@code BuildingChoiceResolver} read it (server: adopt the placing player's
 * staged choices on first resolve; client: read the synced copy). The build wand's session picks staged at
 * placement ({@code MixinBlueprintPlacementHandling} -> {@code StagedChoices}, keyed by the placement
 * position) are thus adopted onto the controller, so the builder follows them.
 *
 * <p>{@code load}/{@code saveAdditional} are vanilla {@code BlockEntity} methods (the controller overrides
 * them), so these injectors are <b>remapped</b> (the default) — unlike most of this package's mixins, which
 * target MineColonies' own members with {@code remap = false}. Part of the optional MineColonies integration.
 */
@Mixin(TileEntityDecorationController.class)
public class MixinTileEntityDecorationController implements PlacementChoiceHolder
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

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void structurizereplacements$writeChoices(final CompoundTag compound, final CallbackInfo ci)
    {
        ChoiceCodec.writeNbt(compound, SREP_KEY, structurizereplacements$choices);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void structurizereplacements$readChoices(final CompoundTag compound, final CallbackInfo ci)
    {
        this.structurizereplacements$choices = ChoiceCodec.readNbt(compound, SREP_KEY);
    }
}
