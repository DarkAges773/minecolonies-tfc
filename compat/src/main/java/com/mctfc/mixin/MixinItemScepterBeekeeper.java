package com.mctfc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mctfc.firmalife.FlBeekeeping;
import com.minecolonies.core.items.ItemScepterBeekeeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the Beekeeper scepter register <b>FirmaLife</b> hives, not just vanilla ones. {@code useOn} only acts on a
 * clicked block that is {@code instanceof BeehiveBlock}; a FirmaLife {@code FLBeehiveBlock} isn't one, so without
 * this the player can't assign FirmaLife hives to the hut at all.
 *
 * <p>Mixin 0.8.5 has no {@code INSTANCEOF} injection point, so we instead modify the {@code BlockState.getBlock()}
 * value feeding that {@code instanceof}: for a FirmaLife hive we hand back vanilla {@code Blocks.BEEHIVE}
 * (FirmaLife-gated), making the check pass. {@code useOn} only uses the clicked <i>position</i> afterward (the
 * actual world block is untouched), so the scepter's add/remove/cap logic then works as-is on FirmaLife hives.
 *
 * <p>{@code remap} is left <b>on</b> (no {@code remap = false}): both targets — {@code useOn} (an override of
 * vanilla {@code Item#useOn}) and {@code BlockState#getBlock()} — are Minecraft methods that need SRG remapping
 * in production. The FirmaLife reference is loaded lazily behind the {@code ModList} guard.
 */
@Mixin(ItemScepterBeekeeper.class)
public abstract class MixinItemScepterBeekeeper
{
    @ModifyExpressionValue(
      method = "useOn",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"))
    private Block mctfc$firmaLifeHiveAsBeehive(final Block original)
    {
        if (!(original instanceof net.minecraft.world.level.block.BeehiveBlock)
              && ModList.get().isLoaded("firmalife")
              && FlBeekeeping.isHiveBlock(original))
        {
            return Blocks.BEEHIVE;
        }
        return original;
    }
}
