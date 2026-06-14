package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Patina'd copper bars — vanilla's {@link IronBarsBlock} retextured with TFC's copper bars recoloured through the
 * extracted copper-oxidation LUTs (see {@code tools/generate-textures/patina_<stage>.png}). One block per weathering
 * stage: {@code firmavanilla:copper_bars/<stage>} for {@code exposed}/{@code weathered}/{@code oxidized}. Purely
 * decorative (no oxidation progression) — TFC ships only bright copper bars, so these add the aged look. Behaviour
 * is {@code Properties.copy(Blocks.IRON_BARS)}; the recoloured grate + smooth-edge textures and the iron-bars
 * multipart models/blockstate are machine-generated (tools/generate-textures).
 */
public final class CopperBarsBlocks
{
    private CopperBarsBlocks() {}

    /** Copper oxidation stages, light -> heavy patina (mirrors {@code COPPER_STAGES} in generate.cs). */
    public static final List<String> STAGES = List.of("exposed", "weathered", "oxidized");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** One bar block per stage, in {@link #STAGES} order. */
    public static final List<RegistryObject<Block>> BARS = new ArrayList<>();

    static
    {
        for (final String stage : STAGES)
        {
            BARS.add(register("copper_bars/" + stage, () -> new IronBarsBlock(props())));
        }
    }

    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.copy(Blocks.IRON_BARS);
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
