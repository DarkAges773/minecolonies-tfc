package com.mctfc.data;

import com.mctfc.MineColoniesTFC;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Path;

/**
 * Registers an <b>optional</b> built-in datapack ({@code firmalife_datapack/} inside our jar) that is enabled
 * <b>only when FirmaLife (mod id {@code firmalife}) is loaded</b>. FirmaLife adds faced carved-pumpkin and
 * lit-pumpkin (jack-o'-lantern) variants that TFC lacks, so this pack maps the vanilla blocks to them:
 * {@code carved_pumpkin -> firmalife:carved_pumpkin/none} and {@code jack_o_lantern -> firmalife:lit_pumpkin/none}
 * (priority 1, so jack o'lantern overrides the base {@code tfc:jack_o_lantern} when FirmaLife is present).
 *
 * <p>Same forced built-in {@link PackType#SERVER_DATA} mechanism as {@link BeneathDataPack}/{@link AfcDataPack},
 * gated on {@link ModList#isLoaded}. To add more FirmaLife compat (other substitutions, recipes, …) drop files
 * under {@code firmalife_datapack/data/…}.
 */
public final class FirmaLifeDataPack
{
    private FirmaLifeDataPack() {}

    private static final String FIRMALIFE_MODID = "firmalife";
    private static final String FOLDER = "firmalife_datapack";

    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded(FIRMALIFE_MODID))
        {
            return;
        }
        final Path source = ModList.get().getModFileById(MineColoniesTFC.MODID).getFile().findResource(FOLDER);
        MineColoniesTFC.LOGGER.info("FirmaLife detected — enabling optional FirmaLife datapack.");
        event.addRepositorySource(consumer ->
        {
            final Pack pack = Pack.readMetaAndCreate(
                    "mctfc_firmalife",
                    Component.literal("MineColonies x TFC — FirmaLife compat"),
                    true, // forced on whenever FirmaLife is present
                    id -> new PathPackResources(id, true, source),
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN);
            if (pack != null)
            {
                consumer.accept(pack);
            }
        });
    }
}
