package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In-world conversion: right-click a cobble block holding mortar to turn it into its non-falling
 * {@link MortaredCobbleBlock} twin, consuming {@value #MORTAR_COST} mortar (mirrors the crafting cost).
 * Works for any cobble we registered a twin for.
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MortaredCobbleInteraction
{
    private MortaredCobbleInteraction() {}

    private static final TagKey<Item> MORTAR = TagKey.create(Registries.ITEM, new ResourceLocation("tfc", "mortar"));
    private static final int MORTAR_COST = 4;

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event)
    {
        final ItemStack held = event.getItemStack();
        if (!held.is(MORTAR))
        {
            return;
        }
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final BlockState state = level.getBlockState(pos);
        final MortaredCobbleBlock twin = MortaredCobbleRegistry.twinFor(state.getBlock());
        if (twin == null)
        {
            return;
        }

        final Player player = event.getEntity();
        if (!player.isCreative() && held.getCount() < MORTAR_COST)
        {
            return; // not enough mortar — let other interactions proceed
        }

        if (!level.isClientSide)
        {
            level.setBlockAndUpdate(pos, twin.defaultBlockState());
            if (!player.isCreative())
            {
                held.shrink(MORTAR_COST);
            }
            level.playSound(null, pos, twin.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        else
        {
            player.swing(event.getHand(), true);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
