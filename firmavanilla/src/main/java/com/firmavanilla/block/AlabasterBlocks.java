package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Alabaster decorative blocks in each of TFC's 16 dye colours — vanilla's purpur block / pillar recoloured (CLUT)
 * through each colour's {@code tfc:alabaster/bricks/<colour>} palette, a form TFC doesn't ship. Per colour:
 * {@code firmavanilla:alabaster_tile/<colour>} (full cube, from purpur_block) and
 * {@code firmavanilla:alabaster_pillar/<colour>} ({@link RotatedPillarBlock}, from purpur_pillar). All
 * {@code Properties.copy(Blocks.STONE_BRICKS)}. Textures are machine-generated (tools/generate-textures).
 */
public final class AlabasterBlocks
{
    private AlabasterBlocks() {}

    /** TFC's 16 alabaster dye colours, vanilla DyeColor order (drives the creative tab). */
    public static final List<String> COLORS = List.of(
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Tile (full cube) and pillar block per colour, in {@link #COLORS} order. */
    public static final List<RegistryObject<Block>> TILES = new ArrayList<>();
    public static final List<RegistryObject<Block>> PILLARS = new ArrayList<>();

    static
    {
        for (final String color : COLORS)
        {
            TILES.add(register("alabaster_tile/" + color, () -> new Block(props())));
            PILLARS.add(register("alabaster_pillar/" + color, () -> new RotatedPillarBlock(props())));
        }
    }

    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS);
    }

    private static RegistryObject<Block> register(final String name, final Supplier<Block> sup)
    {
        final RegistryObject<Block> block = BLOCKS.register(name, sup);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
