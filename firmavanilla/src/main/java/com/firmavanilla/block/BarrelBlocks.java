package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * <p><b>It's a plain {@link BarrelBlock}</b> (not a subclass) with {@code Properties.copy(Blocks.BARREL)}, so it
 * inherits the full vanilla barrel behaviour — the 27-slot container, the {@code facing}/{@code open} states, the
 * open/close animation and sounds — and reuses vanilla's {@code BlockEntityType.BARREL} block-entity verbatim. (In
 * 1.20.1 Forge the BE place/load/tick paths don't gate on {@code BlockEntityType.isValid}, so reusing vanilla's
 * type for our blocks just works — the same reuse the soul lamps rely on for TFC's lamp BE.) No behaviour code.
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

    /** Registered barrels, in registration order (drives the creative tab). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    public static void init(final IEventBus modBus)
    {
        registerWoods(TFC_WOODS);
        if (ModList.get().isLoaded("afc")) { registerWoods(AFC_WOODS); }
        if (ModList.get().isLoaded("beneath")) { registerWoods(BENEATH_WOODS); }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FirmaVanilla.LOGGER.info("Registered {} barrel block(s).", ALL.size());
    }

    private static void registerWoods(final List<String> woods)
    {
        for (final String wood : woods)
        {
            final String name = "barrel/" + wood;
            final RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new BarrelBlock(BlockBehaviour.Properties.copy(Blocks.BARREL)));
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
            ALL.add(block);
        }
    }
}
