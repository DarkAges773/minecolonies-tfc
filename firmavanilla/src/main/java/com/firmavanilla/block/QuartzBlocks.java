package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Raw quartz column — a vanilla-quartz-pillar-style directional block ({@link RotatedPillarBlock}) given TFC's
 * raw-rock behaviour: mined directly it drops nether quartz (1–4), but if it ends up <b>isolated</b> (no
 * connected neighbours) it pops off as the block itself, exactly like TFC raw stone.
 *
 * <p>Nothing is re-implemented: the isolation behaviour is entirely TFC's, driven by the
 * {@code tfc:breaks_when_isolated} block tag (TFC's {@code WorldTracker} pops tagged blocks that become isolated
 * and supplies the {@code ISOLATED} loot param) plus a loot table whose {@code tfc:is_isolated} branch drops the
 * block and whose fallback drops the quartz. So this is a plain {@code RotatedPillarBlock} — the tag + loot table
 * (machine-generated) do the rest.
 */
public final class QuartzBlocks
{
    private QuartzBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    public static final RegistryObject<Block> RAW_QUARTZ_COLUMN = register("raw_quartz_column",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.QUARTZ_PILLAR)));

    /**
     * The self-shaping cave crystal placed by the quartz-cluster worldgen feature — a connected {@link PipeBlock}
     * (core + per-side arms). Not a full cube, so {@code noOcclusion}; amethyst-cluster sound for the crystal feel.
     */
    public static final RegistryObject<Block> QUARTZ_CLUSTER = register("quartz_cluster",
            () -> new QuartzClusterBlock(BlockBehaviour.Properties.copy(Blocks.QUARTZ_BLOCK)
                    .noOcclusion().sound(SoundType.AMETHYST_CLUSTER)));

    /**
     * Quartz brick — a plain item (vanilla brick icon recoloured through the quartz palette). Chiselled from
     * nether quartz, it + mortar crafts {@code minecraft:quartz_bricks}, the start of the chisel chain that makes
     * the whole vanilla quartz block family reachable under TFC (all recipes machine-generated).
     */
    public static final RegistryObject<Item> QUARTZ_BRICK = ITEMS.register("quartz_brick",
            () -> new Item(new Item.Properties()));

    private static RegistryObject<Block> register(final String name, final java.util.function.Supplier<Block> sup)
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
