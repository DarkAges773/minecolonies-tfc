package com.mctfc;

import com.mctfc.builder.BuildingChoiceResolver;
import com.mctfc.network.Network;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Compatibility bridge between MineColonies and TerraFirmaCraft (Forge 1.20.1).
 *
 * <p>Block substitution for Structurize blueprints now lives in the standalone
 * {@code structurizereplacements} mod (a dependency). This mod supplies TFC-specific substitution
 * rules as a datapack ({@code data/mctfc/block_substitutions/}) and will house the remaining
 * MineColonies&lt;-&gt;TFC bridging (food/nutrition, requests/progression, etc.).
 */
@Mod(MineColoniesTFC.MODID)
public class MineColoniesTFC
{
    public static final String MODID = "mctfc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineColoniesTFC(FMLJavaModLoadingContext context)
    {
        LOGGER.info("MineColonies x TerraFirmaCraft bridge loaded.");
        // Let the builder's Structurize handlers resolve the player's choices off the MineColonies building.
        BuildingChoiceResolver.register();
        // Channel for per-building replacement edits from the Build Options GUI.
        Network.register();
    }
}
