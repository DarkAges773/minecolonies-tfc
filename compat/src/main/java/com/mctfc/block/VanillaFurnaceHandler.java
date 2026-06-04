package com.mctfc.block;

import com.mctfc.Config;
import com.mctfc.MineColoniesTFC;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Makes the vanilla furnace / smoker / blast furnace <b>decorative</b>: cancels the player's right-click on
 * them so their smelting GUI never opens, so they can't be used to bypass TerraFirmaCraft's smelting/cooking
 * progression. The block stays placed and breakable.
 *
 * <p>We <b>cancel the whole event</b> (rather than {@code setUseBlock(DENY)}, which didn't gate the menu in
 * this interaction path) for <b>every</b> right-click — including while sneaking, since vanilla still opens
 * the menu on a sneak-click with an empty hand. The cost is that you can't place a block by aiming directly
 * at a furnace face (place against a neighbour instead); that's an acceptable trade for "no interactions".
 * MineColonies worker AI drives its furnaces through the block entity directly (not this player interaction),
 * so colony automation is unaffected.
 *
 * <p>Only the three exact vanilla blocks are matched (not {@code AbstractFurnaceBlock} subclasses, so modded
 * furnaces are untouched). Governed by {@link Config#decorativeVanillaFurnaces} — set it {@code false} to
 * restore normal vanilla furnace use.
 */
@Mod.EventBusSubscriber(modid = MineColoniesTFC.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VanillaFurnaceHandler
{
    private VanillaFurnaceHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event)
    {
        if (!Config.decorativeVanillaFurnaces)
        {
            return;
        }
        final Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block == Blocks.FURNACE || block == Blocks.SMOKER || block == Blocks.BLAST_FURNACE)
        {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
}
