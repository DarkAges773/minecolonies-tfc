package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import com.firmavanilla.weathering.WeatheringMaps;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Weathering copper bars — TFC's bright copper bars given vanilla copper's full oxidation lifecycle. Eight blocks:
 * the four weather stages {@code copper_bars/<stage>} ({@link WeatheringCopperBarsBlock}) and their waxed twins
 * {@code waxed_copper_bars/<stage>} (plain {@link IronBarsBlock}), for {@code unaffected}/{@code exposed}/
 * {@code weathered}/{@code oxidized}.
 *
 * <p>The whole lifecycle (random-tick oxidation, axe-scrape down a stage, honeycomb-wax, axe wax-off, lightning
 * de-oxidation, neighbour influence) is provided by vanilla once our block→block links are spliced into vanilla's
 * copper maps in {@link WeatheringMaps} — no mixin. Stage 0 is bridged to TFC: TFC's {@code metal/bars/copper}
 * item places our {@code copper_bars/unaffected} (a {@link BlockEvent.EntityPlaceEvent} swap), that block drops the
 * TFC item, and it has no item of its own — so there's no duplicate and TFC's bars simply start aging.
 */
public final class CopperBarsBlocks
{
    private CopperBarsBlocks() {}

    /** Weather stages in {@link WeatherState} ordinal order (drives the oxidation chain + generator). */
    private static final WeatherState[] STAGES = WeatherState.values(); // UNAFFECTED, EXPOSED, WEATHERED, OXIDIZED
    private static final String[] STAGE_NAMES = { "unaffected", "exposed", "weathered", "oxidized" };

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** The four weather stages, then the four waxed twins, both in {@link #STAGES} order. */
    public static final List<RegistryObject<Block>> WEATHERING = new ArrayList<>();
    public static final List<RegistryObject<Block>> WAXED = new ArrayList<>();

    static
    {
        for (int i = 0; i < STAGES.length; i++)
        {
            final WeatherState state = STAGES[i];
            final String name = STAGE_NAMES[i];
            // The bright (UNAFFECTED) weather stage has NO item — TFC's copper bars item stands in for it.
            WEATHERING.add(register("copper_bars/" + name,
                    () -> new WeatheringCopperBarsBlock(state, props()), state != WeatherState.UNAFFECTED));
            WAXED.add(register("waxed_copper_bars/" + name, () -> new IronBarsBlock(props()), true));
        }
    }

    /** Copy TFC's copper-bars block properties (sound/strength/tool/map colour); fall back to iron bars. */
    private static BlockBehaviour.Properties props()
    {
        final Block tfc = ForgeRegistries.BLOCKS.getValue(WeatheringCopperBarsBlock.TFC_COPPER_BARS);
        return BlockBehaviour.Properties.copy(tfc != null ? tfc : Blocks.IRON_BARS);
    }

    private static RegistryObject<Block> register(final String name, final Supplier<Block> sup, final boolean withItem)
    {
        final RegistryObject<Block> block = BLOCKS.register(name, sup);
        if (withItem) ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        // Splice our chain into vanilla's copper maps once, at common setup (before any world exists).
        modBus.addListener((final FMLCommonSetupEvent e) -> e.enqueueWork(CopperBarsBlocks::registerWeathering));
        // TFC's copper bars item should place our (aging) bright bars instead.
        MinecraftForge.EVENT_BUS.addListener(CopperBarsBlocks::onBlockPlaced);
    }

    /** Wire the oxidation chain (unaffected→exposed→weathered→oxidized) and the wax pairs, then inject. */
    private static void registerWeathering()
    {
        for (int i = 0; i < STAGES.length - 1; i++)
        {
            WeatheringMaps.addOxidationStep(WEATHERING.get(i).get(), WEATHERING.get(i + 1).get());
        }
        for (int i = 0; i < STAGES.length; i++)
        {
            WeatheringMaps.addWax(WEATHERING.get(i).get(), WAXED.get(i).get());
        }
        WeatheringMaps.inject();
    }

    /** Swap a freshly-placed {@code tfc:metal/bars/copper} for our bright (UNAFFECTED) bars so it starts aging. */
    private static void onBlockPlaced(final BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel().isClientSide()) return;
        final Block tfc = tfcCopperBars();
        if (tfc == null || !event.getPlacedBlock().is(tfc)) return;

        final Block bright = WEATHERING.get(0).get(); // UNAFFECTED
        BlockState ours = bright.defaultBlockState();
        // Carry waterlogging; bar connections recompute on the neighbour updates from setBlock.
        if (event.getPlacedBlock().hasProperty(BlockStateProperties.WATERLOGGED) && ours.hasProperty(BlockStateProperties.WATERLOGGED))
        {
            ours = ours.setValue(BlockStateProperties.WATERLOGGED, event.getPlacedBlock().getValue(BlockStateProperties.WATERLOGGED));
        }
        event.getLevel().setBlock(event.getPos(), ours, Block.UPDATE_ALL);
    }

    @Nullable
    private static Block tfcCopperBars()
    {
        return ForgeRegistries.BLOCKS.getValue(WeatheringCopperBarsBlock.TFC_COPPER_BARS);
    }
}
