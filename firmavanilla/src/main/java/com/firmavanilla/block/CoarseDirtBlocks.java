package com.firmavanilla.block;

import com.firmavanilla.FirmaVanilla;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Coarse dirt per TFC soil type — vanilla coarse-dirt's gravelly look in TFC's soil palettes
 * ({@code firmavanilla:coarse_dirt/<soil>} for loam / sandy_loam / silt / silty_loam, textures machine-generated).
 *
 * <p>These match TFC dirt's <b>properties</b> ({@code MapColor.DIRT}, strength 1.4, gravel sound) and its
 * <b>tags</b> (added to {@code tfc:dirt} — which cascades into {@code minecraft:dirt}/sniffer/{@code can_carve} —
 * plus {@code mineable/shovel} and {@code tfc:can_landslide}), but are deliberately a <b>plain {@link Block}</b> —
 * NOT a TFC {@code DirtBlock} and NOT {@code IDirtBlock}. That's the whole point: TFC gates every soil
 * transformation on the block type, not a tag — grass spreads only onto {@code IDirtBlock}, and shovel→path /
 * hoe→farmland live in {@code DirtBlock} — so a plain block carrying the dirt tags behaves like dirt yet can
 * <b>never</b> turn into grass, path or farmland (exactly like vanilla coarse dirt).
 *
 * <p>Crafted like vanilla coarse dirt — a 2×2 checkerboard of the matching {@code tfc:dirt/<soil>} and any
 * {@code #forge:gravel} (so TFC's rock gravels work) → 4. No worldgen; landslide (collapse) is the TFC tag's doing,
 * not a {@code FallingBlock}.
 */
public final class CoarseDirtBlocks
{
    private CoarseDirtBlocks() {}

    /** TFC soil variants (matches {@code tfc:dirt/<soil>}); same order as the generated assets. */
    public static final List<String> SOILS = List.of("loam", "sandy_loam", "silt", "silty_loam");

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FirmaVanilla.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FirmaVanilla.MODID);

    /** One coarse-dirt block per soil, in {@link #SOILS} order (drives the creative tab). */
    public static final List<RegistryObject<Block>> ALL = new ArrayList<>();

    static
    {
        for (final String soil : SOILS)
        {
            ALL.add(register("coarse_dirt/" + soil));
        }
    }

    /** Plain {@link Block} with TFC dirt's properties (no transforms — not a DirtBlock/IDirtBlock). */
    private static RegistryObject<Block> register(final String name)
    {
        final RegistryObject<Block> block = BLOCKS.register(name, () -> new Block(
                BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(1.4F).sound(SoundType.GRAVEL)));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void init(final IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
