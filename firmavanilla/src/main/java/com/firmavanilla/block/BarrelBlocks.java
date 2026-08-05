package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Functional vanilla-style storage barrels in every wood TFC — and optionally AFC/Beneath — ships planks for.
 * Block id {@code firmavanilla:barrel/<wood>}; the faces are vanilla's barrel relief recoloured into that wood's
 * planks palette (machine-generated, see {@code tools/generate-textures/barrels.cs}). TFC has no vanilla-style
 * (item-storage, openable) barrel — its own barrel is a fluid-sealing device — so this is a net-new form.
 *
 * <p><b>It's a {@link BarrelBlockFV}</b> (a {@code BarrelBlock} backed by {@link BarrelBlockEntityFV}) with
 * {@code Properties.copy(Blocks.BARREL)}, so it keeps the full vanilla barrel behaviour — the {@code facing}/{@code
 * open} states, the open/close animation and sounds — but its block-entity shrinks the container to <b>TFC's
 * small-chest rules</b> (18 slots + item-size limit), the way TFC ships its own small chests rather than by mixing
 * into vanilla. Each block needs its own {@link #BARREL_BE} block-entity type (vanilla's {@code BARREL} factory
 * would build a plain 27-slot barrel on load).
 *
 * <p><b>TFC woods register unconditionally</b> (hard dep). <b>AFC/Beneath woods register only when those mods are
 * present</b> — their plank textures exist only then. The client assets (blockstate/model/lang) ship for all woods
 * (unused when a block isn't registered), mirroring the decorative bookshelves ({@link BookshelfBlocks}).
 */
public final class BarrelBlocks
{
    private BarrelBlocks() {}

    /** Same wood sets as the decorative bookshelves; keep in sync with {@code barrels.cs}. */
    public static final List<String> TFC_WOODS = BookshelfBlocks.TFC_WOODS;
    public static final List<String> AFC_WOODS = BookshelfBlocks.AFC_WOODS;
    public static final List<String> BENEATH_WOODS = BookshelfBlocks.BENEATH_WOODS;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FirmaVanilla.MODID);

    /** Registered barrels, in registration order (drives the creative tab). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    /**
     * One shared block-entity type for every wood barrel ({@link BarrelBlockEntityFV}). The block registry fires
     * before the block-entity registry, so {@link #ALL} is fully populated by the time this supplier runs and can
     * be the type's valid-blocks list.
     */
    public static final RegistryObject<BlockEntityType<BarrelBlockEntityFV>> BARREL_BE = BLOCK_ENTITIES.register(
            "barrel",
            () -> BlockEntityType.Builder.of(BarrelBlockEntityFV::new, ALL.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static void init(final IEventBus modBus)
    {
        registerWoods(TFC_WOODS);
        if (ModList.get().isLoaded("afc")) { registerWoods(AFC_WOODS); }
        if (ModList.get().isLoaded("beneath")) { registerWoods(BENEATH_WOODS); }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FirmaVanilla.LOGGER.info("Registered {} barrel block(s).", ALL.size());
    }

    private static void registerWoods(final List<String> woods)
    {
        for (final String wood : woods)
        {
            final String name = "barrel/" + wood;
            final RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new BarrelBlockFV(BlockBehaviour.Properties.copy(Blocks.BARREL)));
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
            ALL.add(block);
        }
    }
}
