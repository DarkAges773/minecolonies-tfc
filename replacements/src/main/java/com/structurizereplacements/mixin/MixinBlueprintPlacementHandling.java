package com.structurizereplacements.mixin;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.network.messages.BuildToolPlacementMessage;
import com.ldtteam.structurize.storage.BlueprintPlacementHandling;
import com.structurizereplacements.placement.ServerPlacementChoices;
import com.structurizereplacements.placement.StagedChoices;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Capture point for integrations that attach the placing player's choices to a structure placed via the
 * build tool ({@code BlueprintPlacementHandling.process} — the single server entry for survival
 * "build"/"delegate to builder", creative-anchor huts, and creative paste): stash the player's current
 * session choices keyed by the placement position, so e.g. the MineColonies integration can adopt them
 * onto the resulting building.
 *
 * <p>Targets Structurize (always present), so it lives in the main config; it no-ops unless an
 * integration that consumes {@link StagedChoices} is loaded (currently only MineColonies), to avoid
 * staging choices nothing will take. {@code remap = false}: Structurize's own members.
 */
@Mixin(BlueprintPlacementHandling.class)
public class MixinBlueprintPlacementHandling
{
    @Inject(method = "process", at = @At("HEAD"), remap = false)
    private static void structurizereplacements$stageChoices(final Blueprint blueprint, final BuildToolPlacementMessage msg, final CallbackInfo ci)
    {
        if (msg.player == null || !ModList.get().isLoaded("minecolonies"))
        {
            return;
        }
        final Map<Block, Block> choices = ServerPlacementChoices.forPlayer(msg.player);
        if (!choices.isEmpty())
        {
            StagedChoices.stage(msg.player.level().dimension(), msg.pos, choices);
        }
    }
}
