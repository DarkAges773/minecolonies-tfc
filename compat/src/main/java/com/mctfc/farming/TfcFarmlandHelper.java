package com.mctfc.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for teaching the MineColonies farmer to work TFC soil. The colony farmer
 * ({@code EntityAIWorkFarmer}) was written for vanilla: it only recognizes {@code minecraft:dirt}
 * blocks as hoeable and always places vanilla {@code Blocks.FARMLAND}. TFC uses its own grass/dirt
 * blocks and {@code tfc:farmland/<soil>}; this class bridges the two via {@link MixinEntityAIWorkFarmer}.
 */
public final class TfcFarmlandHelper
{
    private TfcFarmlandHelper() {}

    /**
     * Surface blocks the colony farmer may till even though they are NOT in {@code minecraft:dirt}.
     * Ships (data/mctfc/tags/blocks/farmer_tillable.json) the TFC grass variants that have a farmland
     * twin — {@code tfc:grass/<soil>} and {@code tfc:clay_grass/<soil>} — which are the actual surface
     * blocks in a TFC world. TFC bare dirt already sits in {@code minecraft:dirt} (via {@code #tfc:dirt}),
     * so it doesn't need listing here; this tag is mainly about grass. Data-driven so packs can extend it.
     */
    public static final TagKey<Block> FARMER_TILLABLE =
            TagKey.create(Registries.BLOCK, new ResourceLocation("mctfc", "farmer_tillable"));

    /** TFC's farmland block tag ({@code tfc:farmland/<soil>}) — what tilling produces and what TFC crops
     *  require below them ({@code CropBlock.canSurvive} checks this tag). */
    public static final TagKey<Block> TFC_FARMLAND =
            TagKey.create(Registries.BLOCK, new ResourceLocation("tfc", "farmland"));

    /**
     * Whether {@code seed} plants a {@link CropBlock} (vanilla's class — TFC's own crops extend it). Used
     * to decide that TFC farmland is "the right farmland" for the colony farmer to plant on: the MineColonies
     * AI otherwise only accepts a vanilla {@code FarmBlock}. Pairing this with a {@link #TFC_FARMLAND} check
     * keeps it tight — a vanilla crop on TFC farmland still won't pass the AI's own {@code canSurvive} guard,
     * so nothing is mis-planted.
     */
    public static boolean plantsCrop(final ItemStack seed)
    {
        return seed.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock;
    }

    /**
     * The farmland a hoe would turn {@code state} into at {@code pos}, or {@code null} if the block has
     * no hoe-till behavior. Delegates to the block's own {@link BlockState#getToolModifiedState} with
     * {@link ToolActions#HOE_TILL}, so TFC dirt/grass yield the matching {@code tfc:farmland/<soil>} and
     * honour TFC's own config + air-above checks. The hoe is a throwaway stack — the block only needs the
     * item to advertise HOE_TILL. Note vanilla soil also answers here (with {@code Blocks.FARMLAND}); the
     * caller distinguishes that case so MineColonies' crop-preferred farmland logic is preserved.
     */
    @Nullable
    public static BlockState tilledFarmland(final Level level, final BlockPos pos, final BlockState state)
    {
        final BlockHitResult hit = new BlockHitResult(Vec3.ZERO, Direction.UP, pos, false);
        final UseOnContext context =
                new UseOnContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE), hit);
        return state.getToolModifiedState(context, ToolActions.HOE_TILL, true);
    }
}
