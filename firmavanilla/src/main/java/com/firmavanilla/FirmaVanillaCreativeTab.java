package com.firmavanilla;

import com.firmavanilla.block.AlabasterBlocks;
import com.firmavanilla.block.BookshelfBlocks;
import com.firmavanilla.block.CopperWeathering;
import com.firmavanilla.block.MortaredCobbleBlock;
import com.firmavanilla.block.MortaredCobbleRegistry;
import com.firmavanilla.block.SandstoneBlocks;
import com.firmavanilla.block.TileBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's creative-mode tab. Beyond letting players grab the content, being in a creative tab is what makes
 * blocks <i>discoverable by MineColonies</i>: its {@code CompatibilityManager} builds its master item list
 * (used by the fill-block setting, candidate pickers, recipe UI, …) purely from the union of all creative
 * tabs' contents — an item in no tab is invisible to those menus. The mortared-cobble twins are registered
 * dynamically with no tab of their own, so without this they never showed up as a miner fill block.
 *
 * <p>The dynamic twins are generated lazily from {@link MortaredCobbleRegistry#twins()} (populated at
 * {@code RegisterEvent}, well before tabs are built), so every twin — across vanilla, TFC and any earlier
 * mod — appears automatically. Statically-registered blocks (e.g. chiseled sandstone) are added alongside.
 */
public final class FirmaVanillaCreativeTab
{
    private FirmaVanillaCreativeTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FirmaVanilla.MODID);

    public static final RegistryObject<CreativeModeTab> FIRMAVANILLA = TABS.register("firmavanilla", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.firmavanilla"))
                    .icon(FirmaVanillaCreativeTab::icon)
                    .displayItems((params, output) -> {
                        // Static blocks first (stable order), then the dynamic cobble twins.
                        for (final RegistryObject<Block> chiseled : SandstoneBlocks.CHISELED_SANDSTONE)
                        {
                            output.accept(chiseled.get());
                        }
                        for (final RegistryObject<Block> bookshelf : BookshelfBlocks.ALL)
                        {
                            output.accept(bookshelf.get());
                        }
                        // Per rock, group the plain tile with its shape family, then the cracked tile.
                        for (int i = 0; i < TileBlocks.ALL.size(); i++)
                        {
                            output.accept(TileBlocks.ALL.get(i).get());
                            output.accept(TileBlocks.STAIRS.get(i).get());
                            output.accept(TileBlocks.SLABS.get(i).get());
                            output.accept(TileBlocks.WALLS.get(i).get());
                            output.accept(TileBlocks.CRACKED.get(i).get());
                        }
                        // Alabaster: the uncoloured base tile + pillar, then each colour's tile, shape family, and pillar.
                        output.accept(AlabasterBlocks.UNCOLORED_TILE.get());
                        output.accept(AlabasterBlocks.UNCOLORED_PILLAR.get());
                        for (int i = 0; i < AlabasterBlocks.TILES.size(); i++)
                        {
                            output.accept(AlabasterBlocks.TILES.get(i).get());
                            output.accept(AlabasterBlocks.STAIRS.get(i).get());
                            output.accept(AlabasterBlocks.SLABS.get(i).get());
                            output.accept(AlabasterBlocks.WALLS.get(i).get());
                            output.accept(AlabasterBlocks.PILLARS.get(i).get());
                        }
                        // Weathering copper forms: each form's aged stages (bright uses TFC's item), then its waxed twins.
                        for (final String form : CopperWeathering.WEATHERING.keySet())
                        {
                            for (final RegistryObject<Block> b : CopperWeathering.WEATHERING.get(form))
                            {
                                if (b.get().asItem() != net.minecraft.world.item.Items.AIR) output.accept(b.get());
                            }
                            for (final RegistryObject<Block> b : CopperWeathering.WAXED.get(form))
                            {
                                output.accept(b.get());
                            }
                        }
                        for (final MortaredCobbleBlock twin : MortaredCobbleRegistry.twins().values())
                        {
                            output.accept(twin);
                        }
                    })
                    .build());

    /** Tab icon: the mortared twin of vanilla cobblestone, falling back to plain cobblestone if absent. */
    private static ItemStack icon()
    {
        final MortaredCobbleBlock vanillaTwin = MortaredCobbleRegistry.twinFor(Blocks.COBBLESTONE);
        return new ItemStack(vanillaTwin != null ? vanillaTwin : Blocks.COBBLESTONE);
    }
}
