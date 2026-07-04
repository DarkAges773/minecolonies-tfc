package com.mctfc.item;

import com.mctfc.bloomery.BloomeryUserModule;
import com.mctfc.bloomery.TfcBloomery;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.IBlockOverlayItem;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSmeltery;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;

/**
 * The <b>bloomery wand</b> ({@code mctfc:bloomery_scepter}) — taken from the Smeltery GUI (a reused MineColonies tool
 * tab; the {@code GiveToolMessage} stamps the colony id + hut pos onto the stack) — that a player right-clicks TFC
 * bloomeries with to mark them for the Smelter to tend for iron (see {@code docs/tfc-bloomery-smelter.md}). The
 * {@code useOn} logic mirrors MineColonies' Beekeeper scepter, retargeted to {@link TfcBloomery} with a <b>mark-time
 * {@code isFormed} gate</b> and the per-level cap ({@link BloomeryUserModule#getMaximumBloomeries}).
 *
 * <p>Marking asymmetry (strict on entry, lenient on retention): a <b>malformed</b> bloomery is rejected with feedback but
 * <b>not consumed</b> (it's a real target the player intended — let them finish the structure and re-click), while a
 * non-bloomery click dismisses the wand (Beekeeper convention). Cap reached ⇒ consume; feature not unlocked at this hut
 * level (cap 0) ⇒ a "level up" message, wand kept.
 */
public class ItemBloomeryScepter extends Item implements IBlockOverlayItem
{
    private static final int RED_OVERLAY = 0xFFFF0000;

    private static final String MSG_ADDED     = "com.mctfc.bloomery.scepter.added";
    private static final String MSG_REMOVED   = "com.mctfc.bloomery.scepter.removed";
    private static final String MSG_NOTFORMED = "com.mctfc.bloomery.scepter.notformed";
    private static final String MSG_LEVEL     = "com.mctfc.bloomery.scepter.level";
    private static final String MSG_MAX       = "com.mctfc.bloomery.scepter.max";

    public ItemBloomeryScepter(final Properties properties)
    {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(final UseOnContext ctx)
    {
        if (ctx.getLevel().isClientSide)
        {
            return InteractionResult.FAIL;
        }
        final Player player = ctx.getPlayer();
        if (player == null)
        {
            return InteractionResult.FAIL;
        }

        final ItemStack scepter = player.getItemInHand(ctx.getHand());
        final CompoundTag tag = scepter.getOrCreateTag();
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(tag.getInt(TAG_ID), ctx.getLevel());
        if (colony == null)
        {
            return super.useOn(ctx);
        }
        final IBuilding hut = colony.getServerBuildingManager().getBuilding(BlockPosUtil.read(tag, TAG_POS));
        if (!(hut instanceof BuildingSmeltery))
        {
            return super.useOn(ctx);
        }
        final BloomeryUserModule module = hut.getFirstModuleOccurance(BloomeryUserModule.class);
        if (module == null)
        {
            return super.useOn(ctx);
        }

        final Level level = ctx.getLevel();
        final BlockPos pos = ctx.getClickedPos();

        if (!TfcBloomery.isBloomery(level.getBlockState(pos)))
        {
            player.getInventory().removeItem(scepter); // clicked a non-bloomery — dismiss the wand (Beekeeper convention)
            return super.useOn(ctx);
        }
        if (module.contains(pos))
        {
            module.remove(pos);
            MessageUtils.format(MSG_REMOVED).sendTo(player);
            SoundUtils.playSoundForPlayer((ServerPlayer) player, SoundEvents.NOTE_BLOCK_BELL.get(), (float) SoundUtils.VOLUME * 2, 0.5f);
            return super.useOn(ctx);
        }
        if (!TfcBloomery.isFormed(level, pos))
        {
            MessageUtils.format(MSG_NOTFORMED).sendTo(player); // reject, keep the wand — let them finish the structure
            return super.useOn(ctx);
        }
        final int cap = module.getMaximumBloomeries();
        if (cap <= 0)
        {
            MessageUtils.format(MSG_LEVEL).sendTo(player); // feature not unlocked at this hut level — keep the wand
            return super.useOn(ctx);
        }
        if (module.getBloomeries().size() >= cap)
        {
            MessageUtils.format(MSG_MAX).sendTo(player);
            player.getInventory().removeItem(scepter); // at the cap — consume (Beekeeper convention)
            return super.useOn(ctx);
        }

        module.add(pos);
        MessageUtils.format(MSG_ADDED).sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return super.useOn(ctx);
    }

    /**
     * Client-side overlay. Slice 1 renders only a <b>red box on the bound hut</b> (from the always-present client
     * building view) so the player sees which Smeltery the wand serves; the <b>yellow marked-bloomery boxes</b> arrive
     * with the dedicated overlay-sync packet in a later slice (the marked positions are server-only for now).
     */
    @NotNull
    @Override
    public List<OverlayBox> getOverlayBoxes(@NotNull final Level world, @NotNull final Player player, @NotNull final ItemStack stack)
    {
        final CompoundTag tag = stack.getOrCreateTag();
        final IColonyView colony = IColonyManager.getInstance().getColonyView(tag.getInt(TAG_ID), world.dimension());
        final BlockPos hutPos = BlockPosUtil.read(tag, TAG_POS);
        if (colony != null && colony.getClientBuildingManager().getBuilding(hutPos) != null)
        {
            return List.of(new OverlayBox(new AABB(hutPos), RED_OVERLAY, 0.02f, true));
        }
        return Collections.emptyList();
    }
}
