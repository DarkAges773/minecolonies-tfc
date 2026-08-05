package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import com.firmavanilla.weathering.WeatheringMaps;
import net.dries007.tfc.common.blocks.TFCChainBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registers and wires every TFC copper form that gets vanilla copper's weathering lifecycle: bars, the plated
 * block + its stairs/slab, chains, and trapdoors. Each form is a "set" of the four weather stages
 * {@code copper_<form>/<stage>} ({@code WeatheringCopper*Block}) plus four waxed twins
 * {@code waxed_copper_<form>/<stage>} (plain vanilla blocks).
 *
 * <p>The lifecycle itself (oxidise/scrape/wax/lightning) is delivered by {@link WeatheringMaps} splicing our
 * block→block links into vanilla's copper maps — no mixin. Each form's bright (UNAFFECTED) stage has no item of
 * its own: TFC's matching item ({@code metal/...}) places it (one {@link BlockEvent.EntityPlaceEvent} listener
 * swaps any registered TFC source block for our bright block), it drops the TFC item, and pick-block returns it.
 * So existing TFC copper simply starts aging — no duplicate items, no conversion recipes. Adding a new weathering
 * form is one {@link #registerForm} call.
 */
public final class CopperWeathering
{
    private CopperWeathering() {}

    private static final WeatherState[] STAGES = WeatherState.values();          // UNAFFECTED, EXPOSED, WEATHERED, OXIDIZED
    private static final String[] STAGE_NAMES = { "unaffected", "exposed", "weathered", "oxidized" };

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Per form id ({@code bars}/{@code block}/{@code block_stairs}/…): the 4 weather stages and 4 waxed twins. */
    public static final Map<String, List<RegistryObject<Block>>> WEATHERING = new LinkedHashMap<>();
    public static final Map<String, List<RegistryObject<Block>>> WAXED = new LinkedHashMap<>();

    /** TFC source block id → our bright (UNAFFECTED) block, for the placement swap. */
    private static final Map<ResourceLocation, RegistryObject<Block>> PLACEMENT_SOURCES = new LinkedHashMap<>();
    /** Resolved at setup: TFC source block instance → our bright block instance (O(1) placement lookup). */
    private static final Map<Block, Block> SWAP = new HashMap<>();

    static
    {
        // TFC-bridged forms: TFC ships the bright block, so its item places ours and ours has no item of its own.
        registerForm("bars", tfc("metal/bars/copper"), tfc("metal/bars/copper"),
                (st, p) -> new WeatheringCopperBarsBlock(st, tfc("metal/bars/copper"), p),
                IronBarsBlock::new);
        registerForm("block", tfc("metal/block/copper"), tfc("metal/block/copper"),
                (st, p) -> new WeatheringCopperCubeBlock(st, tfc("metal/block/copper"), p),
                Block::new);
        registerForm("block_stairs", tfc("metal/block/copper_stairs"), tfc("metal/block/copper_stairs"),
                (st, p) -> new WeatheringCopperStairBlock(st, tfc("metal/block/copper_stairs"), () -> copperBlock(st).defaultBlockState(), p),
                p -> new StairBlock(() -> Blocks.COPPER_BLOCK.defaultBlockState(), p));
        registerForm("block_slab", tfc("metal/block/copper_slab"), tfc("metal/block/copper_slab"),
                (st, p) -> new WeatheringCopperSlabBlock(st, tfc("metal/block/copper_slab"), p),
                SlabBlock::new);
        registerForm("chain", tfc("metal/chain/copper"), tfc("metal/chain/copper"),
                (st, p) -> new WeatheringCopperChainBlock(st, tfc("metal/chain/copper"), p),
                TFCChainBlock::new);
        registerForm("trapdoor", tfc("metal/trapdoor/copper"), tfc("metal/trapdoor/copper"),
                (st, p) -> new WeatheringCopperTrapDoorBlock(st, tfc("metal/trapdoor/copper"), p),
                p -> new TrapDoorBlock(p, BlockSetType.IRON));
        // Cut forms: no TFC equivalent (firmavanilla-only). Bright stage has its own item; cut from the plated block
        // via saw + stonecutter. Properties copied from TFC's plated block (same material); no placement swap.
        registerForm("cut", tfc("metal/block/copper"), null,
                (st, p) -> new WeatheringCopperCubeBlock(st, null, p),
                Block::new);
        registerForm("cut_stairs", tfc("metal/block/copper_stairs"), null,
                (st, p) -> new WeatheringCopperStairBlock(st, null, () -> cutCopper(st).defaultBlockState(), p),
                p -> new StairBlock(() -> Blocks.CUT_COPPER.defaultBlockState(), p));
        registerForm("cut_slab", tfc("metal/block/copper_slab"), null,
                (st, p) -> new WeatheringCopperSlabBlock(st, null, p),
                SlabBlock::new);
    }

    /**
     * Register {@code copper_<idBase>/<stage>} (weathering) + {@code waxed_copper_<idBase>/<stage>}. Properties are
     * copied from {@code propsSource}. When {@code tfcBridge} is non-null the bright stage stands in for that TFC
     * block: it gets no item of its own and TFC's block placement-swaps to it; when null (cut forms) every stage is
     * a normal item with no swap.
     */
    private static void registerForm(final String idBase, final ResourceLocation propsSource, @Nullable final ResourceLocation tfcBridge,
            final BiFunction<WeatherState, BlockBehaviour.Properties, Block> weatheringFactory,
            final Function<BlockBehaviour.Properties, Block> waxedFactory)
    {
        final List<RegistryObject<Block>> weathering = new ArrayList<>();
        final List<RegistryObject<Block>> waxed = new ArrayList<>();
        for (int i = 0; i < STAGE_NAMES.length; i++)
        {
            final WeatherState st = STAGES[i];
            final String stage = STAGE_NAMES[i];
            final boolean itemless = tfcBridge != null && st == WeatherState.UNAFFECTED; // only a bridged bright stage has no item
            weathering.add(register("copper_" + idBase + "/" + stage, () -> weatheringFactory.apply(st, props(propsSource)), !itemless));
            waxed.add(register("waxed_copper_" + idBase + "/" + stage, () -> waxedFactory.apply(props(propsSource)), true));
        }
        WEATHERING.put(idBase, weathering);
        WAXED.put(idBase, waxed);
        if (tfcBridge != null) PLACEMENT_SOURCES.put(tfcBridge, weathering.get(0));
    }

    private static ResourceLocation tfc(final String path) { return new ResourceLocation("tfc", path); }

    /** Vanilla plain copper block for a stage (the plated-block stairs' base state). */
    private static Block copperBlock(final WeatherState st)
    {
        return switch (st)
        {
            case UNAFFECTED -> Blocks.COPPER_BLOCK;
            case EXPOSED -> Blocks.EXPOSED_COPPER;
            case WEATHERED -> Blocks.WEATHERED_COPPER;
            case OXIDIZED -> Blocks.OXIDIZED_COPPER;
        };
    }

    /** Vanilla cut-copper block for a stage (the cut-stairs' base state). */
    private static Block cutCopper(final WeatherState st)
    {
        return switch (st)
        {
            case UNAFFECTED -> Blocks.CUT_COPPER;
            case EXPOSED -> Blocks.EXPOSED_CUT_COPPER;
            case WEATHERED -> Blocks.WEATHERED_CUT_COPPER;
            case OXIDIZED -> Blocks.OXIDIZED_CUT_COPPER;
        };
    }

    /** Copy the TFC source block's properties (sound/strength/tool/occlusion); fall back to a copper block. */
    private static BlockBehaviour.Properties props(final ResourceLocation tfcSource)
    {
        final Block tfc = ForgeRegistries.BLOCKS.getValue(tfcSource);
        return BlockBehaviour.Properties.copy(tfc != null ? tfc : Blocks.COPPER_BLOCK);
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
        modBus.addListener((final FMLCommonSetupEvent e) -> e.enqueueWork(CopperWeathering::onSetup));
        MinecraftForge.EVENT_BUS.addListener(CopperWeathering::onBlockPlaced);
    }

    /** Wire every form's oxidation chain + wax pairs into vanilla's maps, and resolve the placement-swap table. */
    private static void onSetup()
    {
        for (final List<RegistryObject<Block>> weathering : WEATHERING.values())
        {
            for (int i = 0; i < STAGE_NAMES.length - 1; i++)
            {
                WeatheringMaps.addOxidationStep(weathering.get(i).get(), weathering.get(i + 1).get());
            }
        }
        for (final String form : WEATHERING.keySet())
        {
            final List<RegistryObject<Block>> weathering = WEATHERING.get(form);
            final List<RegistryObject<Block>> waxed = WAXED.get(form);
            for (int i = 0; i < STAGE_NAMES.length; i++)
            {
                WeatheringMaps.addWax(weathering.get(i).get(), waxed.get(i).get());
            }
        }
        WeatheringMaps.inject();

        PLACEMENT_SOURCES.forEach((id, bright) -> {
            final Block tfc = ForgeRegistries.BLOCKS.getValue(id);
            if (tfc != null) SWAP.put(tfc, bright.get());
        });
    }

    /** Swap a freshly-placed TFC copper block for our matching bright (UNAFFECTED) block so it starts aging. */
    private static void onBlockPlaced(final BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel().isClientSide()) return;
        final Block bright = SWAP.get(event.getPlacedBlock().getBlock());
        if (bright == null) return;
        // Carry over every shared property (facing/half/open/type/axis/waterlogged…); connections recompute on update.
        BlockState ours = bright.defaultBlockState();
        for (final Property<?> p : event.getPlacedBlock().getProperties())
        {
            if (ours.hasProperty(p)) ours = copyProperty(event.getPlacedBlock(), ours, p);
        }
        event.getLevel().setBlock(event.getPos(), ours, Block.UPDATE_ALL);
    }

    private static <T extends Comparable<T>> BlockState copyProperty(final BlockState from, final BlockState to, final Property<T> p)
    {
        return to.setValue(p, from.getValue(p));
    }
}
