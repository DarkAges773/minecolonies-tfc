package com.firmavanilla;

import com.firmavanilla.block.AlabasterBlocks;
import com.firmavanilla.block.BookshelfBlocks;
import com.firmavanilla.block.CopperWeathering;
import com.firmavanilla.block.MortaredCobbleRegistry;
import com.firmavanilla.block.SandstoneBlocks;
import com.firmavanilla.block.TileBlocks;
import com.firmavanilla.data.MortaredCobbleData;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * TFC Vanilla Building Blocks — a standalone TerraFirmaCraft companion that ships decorative variants of
 * vanilla building blocks (recoloured to TFC's rock/sand palettes) plus the non-falling "cemented" cobble
 * twins.
 *
 * <p>Depends ONLY on TerraFirmaCraft. The MineColonies × TerraFirmaCraft bridge ({@code mctfc}) hard-depends
 * on this mod and points its Structurize substitution rules at these blocks (so MineColonies blueprints get
 * proper TFC-styled targets for the vanilla forms TFC itself doesn't ship, e.g. chiseled sandstone).
 */
@Mod(FirmaVanilla.MODID)
public class FirmaVanilla
{
    public static final String MODID = "firmavanilla";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FirmaVanilla(FMLJavaModLoadingContext context)
    {
        final IEventBus modBus = context.getModEventBus();
        // Static decorative blocks: chiseled sandstone in TFC's seven sand colours (the form TFC lacks).
        SandstoneBlocks.init(modBus);
        // Decorative full-block bookshelves (enchanting power) per wood — TFC always, AFC/Beneath when present.
        BookshelfBlocks.init(modBus);
        // Decorative "tiles" blocks (deepslate-tiles style) per TFC rock — CLUT-generated textures.
        TileBlocks.init(modBus);
        // Alabaster tile + pillar (purpur recoloured) in TFC's 16 dye colours — CLUT-generated textures.
        AlabasterBlocks.init(modBus);
        // Weathering copper forms (bars, plated block + stairs/slab, chains, trapdoors) — TFC's copper given
        // vanilla copper's oxidation/scrape/wax/lightning lifecycle, by splicing into vanilla's copper maps.
        CopperWeathering.init(modBus);
        // Scan the block registry and register a non-falling twin for every cobble block (so TFC's
        // collapsing cobble doesn't wreck builds). Substitution targets these via the
        // firmavanilla:mortared_cobblestone tag.
        modBus.addListener(MortaredCobbleRegistry::onRegister);
        // Creative tab holding the mod's blocks. Beyond being grabbable, this is what makes them
        // discoverable by MineColonies (its item pickers — incl. the miner fill-block setting — only see
        // items that appear in some creative tab).
        FirmaVanillaCreativeTab.TABS.register(modBus);
        // Runtime data pack: the firmavanilla:mortared_cobblestone tag (every twin) + a mortar recipe per
        // twin (the twin set isn't known ahead of time, so these can't ship as static files).
        modBus.addListener(MortaredCobbleData::onAddPackFinders);
        LOGGER.info("TFC Vanilla Building Blocks loaded.");
    }
}
