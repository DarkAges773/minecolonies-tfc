package com.mctfc.mixin;

import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import net.dries007.tfc.common.TFCTiers;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiPredicate;

/**
 * Re-baselines MineColonies' <b>shears</b> tool-level so TFC metal shears are usable. MineColonies scores the shears
 * equipment level by durability relative to <i>vanilla</i> shears:
 * {@code durabilityBasedLevel(stack, Items.SHEARS.getMaxDamage()=238)} = {@code maxDamage / 238}. Every TFC metal
 * shear sets its durability to its metal tier's {@code uses} (copper = 600, bronze = 1300, …), so they all score
 * <b>≥ level 2</b> — and a low hut (cap {@code BASIC_TOOL_LEVEL = 1}) rejects them, only accepting vanilla shears
 * (238 → level 1). Meanwhile axes use the tier's actual level (copper = 1), so copper axes work at level 1 but copper
 * shears don't — an inconsistency.
 *
 * <p>We recompute the shears level with the divisor = <b>TFC copper shears' durability</b> ({@code TFCTiers.COPPER.getUses()}),
 * so copper shears = {@code 600/600 = 1} (matching the copper axe), bronze ≈ 2, wrought iron ≈ 3, steel+ → capped 5,
 * and vanilla shears = {@code 238/600 = 0}. Hut-level gating is preserved (better shears still need better huts),
 * just on a TFC baseline. Only the shears type is affected; every other equipment type falls through unchanged.
 * {@code @Mixin(remap = false)} — MineColonies' own method.
 */
@Mixin(value = EquipmentTypeEntry.class, remap = false)
public abstract class MixinEquipmentTypeEntry
{
    @Shadow
    @Final
    private BiPredicate<ItemStack, EquipmentTypeEntry> isEquipment;

    @Inject(method = "getMiningLevel", at = @At("HEAD"), cancellable = true)
    private void mctfc$shearsByCopperDurability(final ItemStack stack, final CallbackInfoReturnable<Integer> cir)
    {
        final EquipmentTypeEntry self = (EquipmentTypeEntry) (Object) this;
        if (self != ModEquipmentTypes.shears.get())
        {
            return; // only the shears type is re-baselined; everything else keeps its level
        }
        if (!isEquipment.test(stack, self))
        {
            cir.setReturnValue(-1);
            return;
        }
        final int divisor = TFCTiers.COPPER.getUses(); // TFC copper shears' durability (600) instead of vanilla's 238
        cir.setReturnValue(stack.isDamageableItem() ? Math.min(stack.getMaxDamage() / divisor, 5) : 5);
    }
}
