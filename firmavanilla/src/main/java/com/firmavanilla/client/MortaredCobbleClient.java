package com.firmavanilla.client;

import com.firmavanilla.FirmaVanilla;
import com.firmavanilla.block.MortaredCobbleBlock;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Client-only: the dynamically-registered {@link MortaredCobbleBlock} twins ship no blockstate/model JSON,
 * so after model baking we point each twin's baked block + item model at its <i>source</i> block's baked
 * model. The twin therefore renders identically to the cobble it replaces, with zero per-twin assets.
 *
 * <p>(The model bakery logs a "missing model" for each twin while loading — harmless; we overwrite it here.)
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MortaredCobbleClient
{
    private MortaredCobbleClient() {}

    @SubscribeEvent
    public static void onModifyBakingResult(final ModelEvent.ModifyBakingResult event)
    {
        final Map<ResourceLocation, BakedModel> models = event.getModels();
        for (final Map.Entry<Block, MortaredCobbleBlock> entry : com.firmavanilla.block.MortaredCobbleRegistry.twins().entrySet())
        {
            final Block source = entry.getKey();
            final MortaredCobbleBlock twin = entry.getValue();
            final ResourceLocation sourceId = ForgeRegistries.BLOCKS.getKey(source);
            final ResourceLocation twinId = ForgeRegistries.BLOCKS.getKey(twin);
            if (sourceId == null || twinId == null)
            {
                continue;
            }

            // Block model: delegate the twin's (single) state model to the source's default-state model.
            final BakedModel sourceModel = models.get(BlockModelShaper.stateToModelLocation(source.defaultBlockState()));
            if (sourceModel != null)
            {
                models.put(BlockModelShaper.stateToModelLocation(twin.defaultBlockState()), sourceModel);
            }

            // Item model: delegate the twin's inventory model to the source item's inventory model.
            final BakedModel sourceItem = models.get(new ModelResourceLocation(sourceId, "inventory"));
            if (sourceItem != null)
            {
                models.put(new ModelResourceLocation(twinId, "inventory"), sourceItem);
            }
        }
    }
}
