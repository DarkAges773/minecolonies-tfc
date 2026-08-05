package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-wood ladders — the plain vanilla {@link LadderBlock} (no custom subclass), one per TFC wood and, when those
 * mods are present, per AFC/Beneath wood. Block id {@code firmavanilla:ladder/<wood>}; the face is the
 * hand-painted jungle-ladder relief CLUT-recoloured through each wood's planks (see {@code ladders.cs}).
 *
 * <p><b>TFC woods register unconditionally</b> (hard dep). <b>AFC/Beneath woods register only when those mods are
 * present</b> — their planks (hence the generated textures) exist only then. The client assets
 * (blockstate/model/item) ship unconditionally but go unused when the block isn't registered; their recipes carry a
 * {@code forge:mod_loaded} condition and their {@code minecraft:climbable}/{@code mineable/axe} tag entries are
 * {@code required:false}, so nothing errors when the mod is absent.
 *
 * <p>Climbing is tag-driven: an entity climbs a block iff it is in {@code #minecraft:climbable}, so the generated
 * tag (replace:false) is what actually makes these climbable — see {@code ladders.cs}.
 */
public final class LadderBlocks
{
    private LadderBlocks() {}

    /** Same wood sets as the decorative bookshelves/barrels — keep in sync. */
    public static final List<String> TFC_WOODS = BookshelfBlocks.TFC_WOODS;
    public static final List<String> AFC_WOODS = BookshelfBlocks.AFC_WOODS;
    public static final List<String> BENEATH_WOODS = BookshelfBlocks.BENEATH_WOODS;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Registered ladders, in registration order (drives the creative tab). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    public static void init(final IEventBus modBus)
    {
        registerWoods(TFC_WOODS);
        if (ModList.get().isLoaded("afc")) { registerWoods(AFC_WOODS); }
        if (ModList.get().isLoaded("beneath")) { registerWoods(BENEATH_WOODS); }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FirmaVanilla.LOGGER.info("Registered {} ladder block(s).", ALL.size());
    }

    private static void registerWoods(final List<String> woods)
    {
        for (final String wood : woods)
        {
            final String name = "ladder/" + wood;
            final RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new LadderBlock(BlockBehaviour.Properties.copy(Blocks.LADDER)));
            ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
            ALL.add(block);
        }
    }
}
