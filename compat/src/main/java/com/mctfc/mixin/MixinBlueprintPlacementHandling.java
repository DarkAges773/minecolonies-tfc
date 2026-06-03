package com.mctfc.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.network.messages.BuildToolPlacementMessage;
import com.ldtteam.structurize.storage.BlueprintPlacementHandling;
import com.mctfc.MineColoniesTFC;
import com.mctfc.builder.StagedChoices;
import com.structurizereplacements.placement.ServerPlacementChoices;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Capture point for builder substitutions: when the build tool places a structure server-side
 * ({@code BlueprintPlacementHandling.process}, the single entry for survival "build"/"delegate to
 * builder", creative-anchor huts, and creative paste), stash the placing player's current GUI choices
 * keyed by the placement position, so the resulting building can adopt them.
 *
 * <p>{@code remap = false}: Structurize's own members.
 */
@Mixin(BlueprintPlacementHandling.class)
public class MixinBlueprintPlacementHandling
{
    @Inject(method = "process", at = @At("HEAD"), remap = false)
    private static void mctfc$stageChoices(final Blueprint blueprint, final BuildToolPlacementMessage msg, final CallbackInfo ci)
    {
        if (msg.player == null)
        {
            return;
        }
        final Map<Block, Block> choices = ServerPlacementChoices.forPlayer(msg.player);
        if (choices.isEmpty())
        {
            return;
        }
        StagedChoices.stage(msg.pos, choices);
        MineColoniesTFC.LOGGER.debug("[mctfc] staged {} replacement choice(s) at {}", choices.size(), msg.pos);
    }
}
