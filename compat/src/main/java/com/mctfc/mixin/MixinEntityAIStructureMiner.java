package com.mctfc.mixin;

import com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the miner's vertical-shaft ladder back-fill honour the hut's <b>fill-block</b> setting instead of always
 * using vanilla cobblestone.
 *
 * <p>The miner already routes its main shaft/water-wall fill through the configurable {@code FILL_BLOCK} setting
 * ({@code getMainFillBlock()} / {@code getSolidSubstitution()}), which the player can set to any block (incl. a TFC
 * one) in the miner hut GUI — so request, inventory-consume and placement all agree on a block the player can supply.
 * But {@code getLadderBackFillBlock()} is <b>hardcoded</b> to {@code Blocks.COBBLESTONE}/{@code NETHERRACK}, ignoring
 * that setting. Under TFC plain cobblestone <i>landslides</i>, so a miner left on the default collapses its own shaft,
 * and in a TFC world the player can't even supply vanilla cobblestone to fulfil the request.
 *
 * <p>We redirect the ladder back-fill to {@code getMainFillBlock()} so the <i>whole</i> shaft uses the GUI-chosen
 * fill block. {@code @Mixin(remap = false)} — MineColonies' own class/methods; both getters are private members of
 * {@code EntityAIStructureMiner}, so they bind reliably.
 */
@Mixin(value = EntityAIStructureMiner.class, remap = false)
public class MixinEntityAIStructureMiner
{
    @Shadow
    private Block getMainFillBlock()
    {
        throw new AssertionError("shadow");
    }

    @Inject(method = "getLadderBackFillBlock", at = @At("RETURN"), cancellable = true)
    private void mctfc$ladderBackfillFromSetting(final CallbackInfoReturnable<Block> cir)
    {
        cir.setReturnValue(getMainFillBlock());
    }
}
