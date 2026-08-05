package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
 * Decorative full-block bookshelves ({@link DecorativeBookshelfBlock}) in every wood TFC — and optionally
 * AFC/Beneath — ships a chiseled bookshelf for. Block id {@code firmavanilla:bookshelf/<wood>}; the sides
 * reuse the source mod's {@code <wood>_bookshelf_occupied} texture, top/bottom its planks (generated models).
 *
 * <p><b>TFC woods register unconditionally</b> (hard dep). <b>AFC/Beneath woods register only when those mods
 * are present</b> — their textures/planks exist only then. The matching client assets (blockstate/model/lang)
 * ship unconditionally but go unused when the block isn't registered; their recipes carry a
 * {@code forge:mod_loaded} condition and their {@code mineable/axe} tag entries are {@code required:false},
 * so nothing errors when the mod is absent.
 */
public final class BookshelfBlocks
{
    private BookshelfBlocks() {}

    /** TFC's 20 woods (always present). */
    public static final List<String> TFC_WOODS = List.of(
            "acacia", "ash", "aspen", "birch", "blackwood", "chestnut", "douglas_fir", "hickory", "kapok", "mangrove",
            "maple", "oak", "palm", "pine", "rosewood", "sequoia", "spruce", "sycamore", "white_cedar", "willow");
    /** ArborFirmaCraft's 10 woods (only when 'afc' is loaded). */
    public static final List<String> AFC_WOODS = List.of(
            "baobab", "cypress", "eucalyptus", "fig", "hevea", "ipe", "ironwood", "mahogany", "teak", "tualang");
    /** Beneath's nether woods (only when 'beneath' is loaded). */
    public static final List<String> BENEATH_WOODS = List.of("crimson", "warped");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Registered decorative bookshelves, in registration order (drives the creative tab). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    public static void init(final IEventBus modBus)
    {
        registerWoods(TFC_WOODS);
        if (ModList.get().isLoaded("afc")) { registerWoods(AFC_WOODS); }
        if (ModList.get().isLoaded("beneath")) { registerWoods(BENEATH_WOODS); }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FirmaVanilla.LOGGER.info("Registered {} decorative bookshelf block(s).", ALL.size());
    }

    private static void registerWoods(final List<String> woods)
    {
        for (final String wood : woods)
        {
            final String name = "bookshelf/" + wood;
            final RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new DecorativeBookshelfBlock(BlockBehaviour.Properties.copy(Blocks.BOOKSHELF)));
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
            ALL.add(block);
        }
    }
}
