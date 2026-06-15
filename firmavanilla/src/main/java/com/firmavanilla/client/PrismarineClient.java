package com.firmavanilla.client;

import com.firmavanilla.FirmaVanilla;
import com.firmavanilla.block.PrismarineDeposits;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only: register the prismarine deposits' <b>pan-stage item models</b> as additional models so they get
 * baked. TFC's pan renderer ({@code PanItemRenderer}) looks these up from the model manager by resource location
 * while a deposit is being panned/sluiced; a model that wasn't baked resolves to the missing-model placeholder
 * (the black-and-magenta cube shown in the pan). They aren't blockstate variants or registered-item models, so —
 * exactly as TFC does for its own deposit pan models in {@code ClientEventHandler#registerSpecialModels} — they
 * must be registered here. The three stages per rock mirror {@code generate.cs}:
 * {@code item/pan/prismarine/<rock>_full}, {@code _half}, and the shared {@code item/pan/prismarine/result}.
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PrismarineClient
{
    private PrismarineClient() {}

    @SubscribeEvent
    public static void onRegisterAdditional(final ModelEvent.RegisterAdditional event)
    {
        for (final String rock : PrismarineDeposits.ROCKS)
        {
            event.register(new ResourceLocation(FirmaVanilla.MODID, "item/pan/prismarine/" + rock + "_full"));
            event.register(new ResourceLocation(FirmaVanilla.MODID, "item/pan/prismarine/" + rock + "_half"));
        }
        // Shared "washed out" result stage (shows prismarine) — one model for all rocks.
        event.register(new ResourceLocation(FirmaVanilla.MODID, "item/pan/prismarine/result"));
    }
}
