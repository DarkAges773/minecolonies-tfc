package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Prismarine gravel deposits — a TFC-style way to obtain the (otherwise unreachable under TFC) vanilla
 * prismarine family. TFC leaves the vanilla prismarine blocks and recipes intact but never generates
 * guardians/monuments, so prismarine <em>shards</em> and <em>crystals</em> are the missing inputs.
 *
 * <p>This adds one panable/sluiceable deposit block per TFC rock — {@code firmavanilla:deposit/prismarine/<rock>}
 * — mirroring TFC's own {@code native_copper} ore deposits exactly. A {@code tfc:soil_disc} worldgen feature
 * swaps deep-ocean floor gravel ({@code tfc:rock/gravel/<rock>}) for the matching deposit; the player mines it
 * and <strong>pans or sluices</strong> it (TFC's data-driven {@code panning}/{@code sluicing} managers) for
 * prismarine shards (common) and crystals (rare). The vanilla crafting recipes then build the prismarine
 * family from there.
 *
 * <p>Plain non-falling {@link Block} with gravel properties, exactly like TFC's deposit block (a bare
 * {@code Block}); the crystal sheen is a single model overlay ({@code firmavanilla:block/deposit/prismarine})
 * layered over each rock's gravel by the {@code tfc:block/ore} parent model — no per-rock textures. All the
 * blockstates/models/loot/panning/sluicing/worldgen JSON is machine-generated (tools/generate-textures).
 */
public final class PrismarineDeposits
{
    private PrismarineDeposits() {}

    /** TFC's 20 rock types (matches {@code tfc:rock/gravel/<rock>}); reuses {@link TileBlocks#ROCKS}. */
    public static final List<String> ROCKS = TileBlocks.ROCKS;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** Deposit block per rock, in {@link #ROCKS} order (drives the creative tab + worldgen mapping). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    static
    {
        for (final String rock : ROCKS)
        {
            ALL.add(register("deposit/prismarine/" + rock, () -> new Block(props())));
        }
    }

    /** Plain {@link Block} with gravel properties (non-falling, shovel-mineable), matching TFC's deposit block. */
    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.copy(Blocks.GRAVEL);
    }

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
