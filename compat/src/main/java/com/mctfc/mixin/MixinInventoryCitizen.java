package com.mctfc.mixin;

import com.mctfc.food.FoodStackingHelper;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the colony citizen inventory's stack-merging freshness-aware for TFC food.
 *
 * <p>{@code InventoryCitizen#insertItem} (a custom {@link net.minecraftforge.items.IItemHandlerModifiable}, not
 * vanilla {@code ItemStackHandler}) decides whether an incoming stack may merge into an occupied slot via
 * {@code ItemStackUtils.compareItemStacksIgnoreStackSize}, which only reads the vanilla item {@code tag} and is
 * therefore blind to TFC's capability-based food decay (see {@link FoodStackingHelper}). It then grows the
 * <i>existing</i> slot, so a fresh harvest dropped onto an older/rotten stack inherits the older caps and rots.
 *
 * <p>This redirect leaves the original comparison intact and merely AND-s in {@link FoodStackingHelper#canMerge}
 * at that single call site: TFC food only merges when genuinely stackable including its food capability,
 * everything else is unchanged. The base AI's other inventory paths funnel their real merges through this same
 * {@code insertItem} (racks already use the caps-aware vanilla insert), so this one hook covers the harvest →
 * citizen → dump flow. {@code remap = false}: the target is MineColonies' own method.
 */
@Mixin(value = InventoryCitizen.class, remap = false)
public abstract class MixinInventoryCitizen
{
    @Redirect(
        method = "insertItem",
        at = @At(value = "INVOKE",
            target = "Lcom/minecolonies/api/util/ItemStackUtils;compareItemStacksIgnoreStackSize(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Ljava/lang/Boolean;"))
    private Boolean mctfc$capsAwareMerge(final ItemStack existing, final ItemStack incoming)
    {
        return ItemStackUtils.compareItemStacksIgnoreStackSize(existing, incoming) && FoodStackingHelper.canMerge(existing, incoming);
    }
}
