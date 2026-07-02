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

import java.util.List;

/**
 * Registration for the heat-forge: the four cosmetic {@link HeatForgeBlock} variants, their {@link BlockItem}s, and the
 * single {@link HeatForgeBlockEntity} type shared by all four. Wired from the mod constructor via {@link #init(IEventBus)}.
 *
 * <p>The four looks (<i>brick / rustic / stone / tile</i>) borrow FirmaLife's four cured-oven textures (top/bottom +
 * side) with the blast-furnace door as a front overlay; they are otherwise identical — same class, same properties, same
 * BE type — so the multiblock merges them freely and every {@code instanceof HeatForgeBlock} check still holds. Each
 * emits light while its shared burn is lit, and each item is added to the vanilla <i>Functional Blocks</i> creative tab
 * so it's grabbable and — crucially — discoverable by MineColonies' item pickers (which only see tab items).
 */
public final class HeatForgeBlocks
{
    private HeatForgeBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineColoniesTFC.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineColoniesTFC.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineColoniesTFC.MODID);

    public static final RegistryObject<Block> HEAT_FORGE_BRICK = block("heat_forge_brick");
    public static final RegistryObject<Block> HEAT_FORGE_RUSTIC = block("heat_forge_rustic");
    public static final RegistryObject<Block> HEAT_FORGE_STONE = block("heat_forge_stone");
    public static final RegistryObject<Block> HEAT_FORGE_TILE = block("heat_forge_tile");

    public static final RegistryObject<Item> HEAT_FORGE_BRICK_ITEM = item(HEAT_FORGE_BRICK);
    public static final RegistryObject<Item> HEAT_FORGE_RUSTIC_ITEM = item(HEAT_FORGE_RUSTIC);
    public static final RegistryObject<Item> HEAT_FORGE_STONE_ITEM = item(HEAT_FORGE_STONE);
    public static final RegistryObject<Item> HEAT_FORGE_TILE_ITEM = item(HEAT_FORGE_TILE);

    /** All four block variants, in registration order — drives the shared BE type and the creative-tab listing. */
    public static final List<RegistryObject<Block>> VARIANTS =
            List.of(HEAT_FORGE_BRICK, HEAT_FORGE_RUSTIC, HEAT_FORGE_STONE, HEAT_FORGE_TILE);
    private static final List<RegistryObject<Item>> VARIANT_ITEMS =
            List.of(HEAT_FORGE_BRICK_ITEM, HEAT_FORGE_RUSTIC_ITEM, HEAT_FORGE_STONE_ITEM, HEAT_FORGE_TILE_ITEM);

    /** One BE type valid for every variant (they share {@link HeatForgeBlockEntity} — the look is purely cosmetic). */
    public static final RegistryObject<BlockEntityType<HeatForgeBlockEntity>> HEAT_FORGE_BE =
            BLOCK_ENTITY_TYPES.register("heat_forge",
                    () -> BlockEntityType.Builder.of(HeatForgeBlockEntity::new,
                            VARIANTS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    /** A forge variant — a plain {@link HeatForgeBlock} with furnace-copied properties + lit-emission (13, like a blast furnace). */
    private static RegistryObject<Block> block(final String id)
    {
        return BLOCKS.register(id, () -> new HeatForgeBlock(BlockBehaviour.Properties.copy(Blocks.FURNACE)
                .lightLevel(state -> state.getValue(HeatForgeBlock.LIT) ? 13 : 0)));
    }

    private static RegistryObject<Item> item(final RegistryObject<Block> block)
    {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

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
            for (final RegistryObject<Item> variantItem : VARIANT_ITEMS)
            {
                event.accept(variantItem);
            }
        }
    }
}
