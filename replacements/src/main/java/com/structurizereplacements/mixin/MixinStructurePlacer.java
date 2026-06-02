package com.structurizereplacements.mixin;

import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.placement.PlacementChoiceHolder;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Applies block substitution at the single point where Structurize turns a blueprint's stored
 * {@link BlockInfo} into a placed block, and carries the placing player's per-placement choices.
 *
 * <p>The choice map is attached by {@link MixinPlaceStructureOperation} when the placement operation
 * is created (it has both the placer and the player). It lives on this placer instance for the whole
 * ticked placement, so {@code handleBlockPlacement} can read it without needing a player reference.
 *
 * <p>{@code remap = false}: handleBlockPlacement is Structurize's own (non-Minecraft) method.
 */
@Mixin(StructurePlacer.class)
public class MixinStructurePlacer implements PlacementChoiceHolder
{
    @Unique
    private Map<Block, Block> structurizereplacements$choices;

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

    @ModifyVariable(method = "handleBlockPlacement", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockInfo structurizereplacements$substituteBlueprintBlock(final BlockInfo blockInfo)
    {
        return BlockSubstitutions.apply(blockInfo, this.structurizereplacements$choices);
    }

    /**
     * The builder/quarrier compute what materials to request through {@code getResourceRequirements};
     * substitute the blueprint state here too so they request the block that will actually be placed.
     */
    @ModifyVariable(method = "getResourceRequirements", at = @At("HEAD"), argsOnly = true, remap = false)
    private BlockState structurizereplacements$substituteRequirement(final BlockState state)
    {
        return BlockSubstitutions.applyState(state, this.structurizereplacements$choices);
    }
}
