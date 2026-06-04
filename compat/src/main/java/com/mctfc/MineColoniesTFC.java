package com.mctfc;

import com.mctfc.block.MortaredCobbleRegistry;
import com.mctfc.data.MortaredCobbleData;
import com.mctfc.network.McFarmingNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Compatibility bridge between MineColonies and TerraFirmaCraft (Forge 1.20.1).
 *
 * <p>The Structurize block-substitution engine <i>and</i> its (optional) MineColonies builder/Build-Options
 * integration both live in the standalone {@code structurizereplacements} mod (a dependency). This mod
 * supplies TFC-specific substitution rules as a datapack ({@code data/mctfc/block_substitutions/}) and will
 * house the rest of the MineColonies&lt;-&gt;TFC bridging (food/nutrition, farming, smithing, …) — including
 * its own mixins, which is why the MixinGradle setup and {@code mctfc.mixins.json} are kept.
 */
@Mod(MineColoniesTFC.MODID)
public class MineColoniesTFC
{
    public static final String MODID = "mctfc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineColoniesTFC(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        final IEventBus modBus = context.getModEventBus();
        // Scan the block registry and register a non-falling twin for every cobble block (so TFC's
        // collapsing cobble doesn't wreck MineColonies builds). Substitution targets these via the
        // mctfc:mortared_cobblestone tag.
        modBus.addListener(MortaredCobbleRegistry::onRegister);
        // Runtime data pack: the mctfc:mortared_cobblestone tag (every twin) + a mortar recipe per twin.
        modBus.addListener(MortaredCobbleData::onAddPackFinders);
        // Network channel for the farming bridge (per-field harvest-mode toggle from the field GUI).
        McFarmingNetwork.register();
        LOGGER.info("MineColonies x TerraFirmaCraft bridge loaded.");
    }
}
