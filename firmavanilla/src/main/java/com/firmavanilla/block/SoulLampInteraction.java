package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.dries007.tfc.common.blocks.devices.LampBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * In-world conversion: right-click a normal TFC metal lamp while holding any item in
 * {@link SoulLamps#CATALYST} → turn it into its soul variant, <b>preserving its lit state and fuel contents</b>
 * (the block-entity NBT is copied across), consuming one catalyst item (free in creative). Holding a catalyst
 * powder triggers nothing in TFC's own lamp {@code use()}, so intercepting here is safe; we cancel the
 * interaction so no default fires. (The reverse — soul back to normal — happens automatically on burn-out, see
 * {@link SoulLampBlock}.)
 */
@Mod.EventBusSubscriber(modid = FirmaVanilla.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulLampInteraction
{
    private SoulLampInteraction() {}

    @SubscribeEvent
    public static void onRightClick(final PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // avoid the off-hand double-fire
        final ItemStack held = event.getItemStack();
        if (!held.is(SoulLamps.CATALYST)) return;

        final Level level = event.getLevel();
        final BlockState state = level.getBlockState(event.getPos());
        final Block soul = SoulLamps.soulFor(state.getBlock());
        if (soul == null) return; // not a convertible (normal) lamp

        if (!level.isClientSide)
        {
            final BlockEntity oldBe = level.getBlockEntity(event.getPos());
            final CompoundTag fuel = oldBe != null ? oldBe.saveWithoutMetadata() : null;
            level.setBlockAndUpdate(event.getPos(), soul.defaultBlockState()
                    .setValue(LampBlock.LIT, state.getValue(LampBlock.LIT))
                    .setValue(LampBlock.HANGING, state.getValue(LampBlock.HANGING)));
            if (fuel != null)
            {
                final BlockEntity newBe = level.getBlockEntity(event.getPos());
                if (newBe != null) { newBe.load(fuel); newBe.setChanged(); }
            }
            final Player player = event.getEntity();
            if (player == null || !player.getAbilities().instabuild) held.shrink(1);
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
