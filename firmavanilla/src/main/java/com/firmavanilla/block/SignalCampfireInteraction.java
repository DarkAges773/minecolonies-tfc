package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In-world conversion mirroring the soul torch: right-click a (normal) {@link SignalCampfires#SIGNAL_CAMPFIRE}
 * while holding any item in {@link SoulLamps#CATALYST} → turn it into the {@link SignalCampfires#SOUL_SIGNAL_CAMPFIRE},
 * carrying over facing/lit/waterlogged/signal-fire and resetting the burn timer (a freshly-lit one then gets the
 * soul's 2× life). Consumes one catalyst (free in creative). Safe to intercept: the signal campfire's {@code use()}
 * is a no-op (no cooking), and we cancel the event so flint &amp; steel etc. still work when not holding a catalyst.
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SignalCampfireInteraction
{
    private SignalCampfireInteraction() {}

    @SubscribeEvent
    public static void onRightClick(final PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // avoid the off-hand double-fire
        final ItemStack held = event.getItemStack();
        if (!held.is(SoulLamps.CATALYST)) return;

        final Level level = event.getLevel();
        final BlockState state = level.getBlockState(event.getPos());
        if (state.getBlock() != SignalCampfires.SIGNAL_CAMPFIRE.get()) return; // only the normal one converts → soul

        if (!level.isClientSide)
        {
            // withPropertiesOf carries facing/lit/waterlogged/signal-fire across to the soul variant.
            level.setBlockAndUpdate(event.getPos(), SignalCampfires.SOUL_SIGNAL_CAMPFIRE.get().withPropertiesOf(state));
            level.getBlockEntity(event.getPos(), SignalCampfires.SIGNAL_CAMPFIRE_BE.get()).ifPresent(TickCounterBlockEntity::resetCounter);
            final Player player = event.getEntity();
            if (player == null || !player.getAbilities().instabuild) held.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
