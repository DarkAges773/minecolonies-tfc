package com.firmavanilla;

import com.firmavanilla.block.AlabasterBlocks;
import com.firmavanilla.block.BarrelBlocks;
import com.firmavanilla.block.BookshelfBlocks;
import com.firmavanilla.block.CoarseDirtBlocks;
import com.firmavanilla.block.CopperWeathering;
import com.firmavanilla.block.MortaredCobbleRegistry;
import com.firmavanilla.block.PrismarineDeposits;
import com.firmavanilla.block.QuartzBlocks;
import com.firmavanilla.block.SandstoneBlocks;
import com.firmavanilla.block.SoulLamps;
import com.firmavanilla.block.SoulTorches;
import com.firmavanilla.block.TileBlocks;
import com.firmavanilla.block.WaxBlocks;
import com.firmavanilla.data.MortaredCobbleData;
import com.firmavanilla.worldgen.QuartzClusterFeature;
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
        // Functional vanilla-style storage barrels per wood (plain BarrelBlock, vanilla BE reused) — TFC always,
        // AFC/Beneath when present. Faces are CLUT-generated from each wood's planks.
        BarrelBlocks.init(modBus);
        // Decorative "tiles" blocks (deepslate-tiles style) per TFC rock — CLUT-generated textures.
        TileBlocks.init(modBus);
        // Alabaster tile + pillar (purpur recoloured) in TFC's 16 dye colours — CLUT-generated textures.
        AlabasterBlocks.init(modBus);
        // Weathering copper forms (bars, plated block + stairs/slab, chains, trapdoors) — TFC's copper given
        // vanilla copper's oxidation/scrape/wax/lightning lifecycle, by splicing into vanilla's copper maps.
        CopperWeathering.init(modBus);
        // Prismarine gravel deposits per TFC rock — a TFC-style (pan/sluice) source for the otherwise
        // unreachable vanilla prismarine family. Generated on the deep-ocean floor via a tfc:soil_disc feature.
        PrismarineDeposits.init(modBus);
        // "Soul" variants of TFC's metal lamps (teal glow, dimmer light) — reuse TFC's lamp block-entity, convert
        // to/from the normal lamp via a catalyst tag / burn-out. The Forge-bus interaction self-subscribes.
        SoulLamps.init(modBus);
        // Soul torches — vanilla soul-torch look + TFC's burn-out (into TFC's dead torch), burning 2x as long.
        // Crafted/right-click-converted from a lit TFC torch + the same soul catalyst tag. Interaction self-subscribes.
        SoulTorches.init(modBus);
        // Raw quartz column — quartz-pillar-style block with TFC raw-rock drops (nether quartz; pops as a block
        // when isolated, via the tfc:breaks_when_isolated tag + loot table — no code).
        QuartzBlocks.init(modBus);
        // Quartz-cluster cave feature — crisscross raw-quartz pillars decorating deeper caves cut through
        // quartz-bearing rock (carving_mask + a host-rock tag gate). Block-only worldgen, no entities.
        QuartzClusterFeature.init(modBus);
        // Coarse dirt per TFC soil — vanilla coarse-dirt look in TFC palettes; plain (non-transforming) dirt-like
        // blocks crafted from TFC dirt + gravel. Textures/assets machine-generated.
        CoarseDirtBlocks.init(modBus);
        // Block of beeswax — vanilla honeycomb-block motif recoloured to FirmaLife beeswax's palette (CLUT).
        // Standalone block (no FirmaLife dep); only its recipe (4 firmalife:beeswax) is mod_loaded-gated.
        WaxBlocks.init(modBus);
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
