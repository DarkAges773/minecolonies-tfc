package com.structurizereplacements;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Standalone Structurize add-on: datapack-driven, tag- and family-based block substitution applied
 * while placing blueprints. Has no dependency on MineColonies or TerraFirmaCraft — any mod (or
 * datapack) can ship rules under {@code data/<namespace>/block_substitutions/}.
 */
@Mod(StructurizeReplacements.MODID)
public class StructurizeReplacements
{
    public static final String MODID = "structurizereplacements";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StructurizeReplacements(FMLJavaModLoadingContext context)
    {
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("Structurize Replacements loaded.");
    }
}
