package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Static decorative blocks: chiseled sandstone in each of TFC's seven sand colours — the form TFC itself
 * doesn't ship (it has raw/cut/smooth but no chiseled). Each block is registered as
 * {@code firmavanilla:chiseled_sandstone/<colour>}.
 *
 * <p>Unlike the runtime {@link MortaredCobbleRegistry} cobble twins, these are a known, fixed set, so they
 * register statically via {@link DeferredRegister} and ship normal blockstate/model/loot/recipe JSON +
 * textures. Those assets (and the textures specifically) are machine-generated: each colour carries one of
 * vanilla's two chiseled reliefs — the "creeper" face (yellow/white/pink/green) or the "wither" motif
 * (red/black/brown) — recoloured onto TFC's matching {@code cut_sandstone} base. That motif split lives in
 * the asset generator (tools/generate-textures), not here; the block code is colour-agnostic.
 */
public final class SandstoneBlocks
{
    private SandstoneBlocks() {}

    /** TFC's seven sand/sandstone colours (matches {@code tfc:cut_sandstone/<colour>}). */
    public static final List<String> SANDSTONE_COLORS =
            List.of("black", "brown", "green", "pink", "red", "white", "yellow");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Chiseled-sandstone block per colour, in {@link #SANDSTONE_COLORS} order (drives the creative tab). */
    public static final List<RegistryObject<Block>> CHISELED_SANDSTONE = registerChiseledSandstone();

    private static List<RegistryObject<Block>> registerChiseledSandstone()
    {
        final List<RegistryObject<Block>> blocks = new ArrayList<>();
        for (final String color : SANDSTONE_COLORS)
        {
            final String name = "chiseled_sandstone/" + color;
            final RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new Block(BlockBehaviour.Properties.copy(Blocks.CHISELED_SANDSTONE)));
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
            blocks.add(block);
        }
        return blocks;
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
