package com.mctfc.forge;

import com.mctfc.MineColoniesTFC;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registration for the heat-forge: the {@link HeatForgeBlock}, its {@link BlockItem}, and the
 * {@link HeatForgeBlockEntity} type. Wired from the mod constructor via {@link #init(IEventBus)}.
 *
 * <p>The block is furnace-shaped (copies {@code minecraft:furnace} properties) and emits light while its shared burn
 * is lit. Its item is added to the vanilla <i>Functional Blocks</i> creative tab so it's grabbable and — crucially —
 * discoverable by MineColonies' item pickers (which only see items that appear in some creative tab).
 */
public final class HeatForgeBlocks
{
    private HeatForgeBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineColoniesTFC.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineColoniesTFC.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineColoniesTFC.MODID);

    public static final RegistryObject<Block> HEAT_FORGE = BLOCKS.register("heat_forge",
            () -> new HeatForgeBlock(BlockBehaviour.Properties.copy(Blocks.FURNACE)
                    .lightLevel(state -> state.getValue(HeatForgeBlock.LIT) ? 13 : 0)));

    public static final RegistryObject<Item> HEAT_FORGE_ITEM = ITEMS.register("heat_forge",
            () -> new BlockItem(HEAT_FORGE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<HeatForgeBlockEntity>> HEAT_FORGE_BE =
            BLOCK_ENTITY_TYPES.register("heat_forge",
                    () -> BlockEntityType.Builder.of(HeatForgeBlockEntity::new, HEAT_FORGE.get()).build(null));

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(HeatForgeBlocks::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(final BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
        {
            event.accept(HEAT_FORGE_ITEM);
        }
    }
}
