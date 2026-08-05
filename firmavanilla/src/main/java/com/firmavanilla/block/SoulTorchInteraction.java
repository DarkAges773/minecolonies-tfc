package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In-world conversion: right-click a lit TFC torch (standing or wall) while holding any item in
 * {@link SoulLamps#CATALYST} → turn it into the matching soul torch, keeping its wall facing, and resetting the
 * burn timer so it starts a fresh (doubled) life. Consumes one catalyst (free in creative). Mirrors the soul-lamp
 * conversion. Safe to intercept: TFC's torch {@code use()} only reacts to items in {@code CAN_BE_LIT_ON_TORCH}
 * (lighting another torch), not the powder catalyst, and we cancel the event so nothing else fires.
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulTorchInteraction
{
    private SoulTorchInteraction() {}

    @SubscribeEvent
    public static void onRightClick(final PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // avoid the off-hand double-fire
        final ItemStack held = event.getItemStack();
        if (!held.is(SoulLamps.CATALYST)) return;

        final Level level = event.getLevel();
        final BlockState state = level.getBlockState(event.getPos());
        final Block soul = SoulTorches.soulFor(state.getBlock());
        if (soul == null) return; // not a convertible (normal TFC) torch

        if (!level.isClientSide)
        {
            // withPropertiesOf carries the wall torch's FACING across (no-op for the standing torch).
            level.setBlockAndUpdate(event.getPos(), soul.withPropertiesOf(state));
            TickCounterBlockEntity.reset(level, event.getPos()); // fresh full (doubled) burn for the new soul torch
            final Player player = event.getEntity();
            if (player == null || !player.getAbilities().instabuild) held.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
