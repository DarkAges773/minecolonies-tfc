package com.structurizereplacements.event;

import com.ldtteam.structurize.client.BlueprintBlockInfoTransformHandler;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.PlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.DomumMaterialRewriter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client mod-bus setup. Wires the substitution preview for Domum Ornamentum "materialized" blocks.
 *
 * <p>A DO block's preview material comes from its block entity's model data, which Structurize builds in
 * {@code BlueprintUtils#instantiateTileEntities} from {@code BlockInfo#getTileEntityData()} — NOT from
 * {@code BlockInfo#getState()}. So the existing {@code getState()} redirect ({@code MixinBlueprintRenderer})
 * recolors ordinary blocks but can't touch DO materials. The only hook that runs during that BE
 * instantiation is Structurize's {@link BlueprintBlockInfoTransformHandler}, so we register a transform
 * there that rewrites the DO {@code textureData} with the current preview choices. That path is
 * preview-only (its sole caller is {@code BlueprintRenderer#init}), so this never double-applies with
 * server placement.
 */
@Mod.EventBusSubscriber(modid = StructurizeReplacements.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents
{
    private ClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event)
    {
        // enqueueWork: the transform registry is a plain HashMap, so register it on the main thread.
        event.enqueueWork(() ->
                BlueprintBlockInfoTransformHandler.getInstance().AddTransformHandler(
                        DomumMaterialRewriter::isMaterialized,
                        info -> BlockSubstitutions.apply(info, PlacementChoices.client())));
    }
}
