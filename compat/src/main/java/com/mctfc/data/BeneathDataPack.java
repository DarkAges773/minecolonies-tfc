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
 * Registers an <b>optional</b> built-in datapack ({@code beneath_datapack/} inside our jar) that is enabled
 * <b>only when the {@code beneath} mod is loaded</b>. It ships Beneath-specific data (currently
 * {@code data/mctfc/block_substitutions/beneath.json}); because the pack isn't registered at all when Beneath
 * is absent, those {@code beneath:*} rules never load and never log "unknown block" warnings in the common
 * (Beneath-absent) case.
 *
 * <p>Done with {@link AddPackFindersEvent} + a {@link PathPackResources} rooted at the jar sub-folder, gated on
 * {@link ModList#isLoaded}. Same forced built-in {@link PackType#SERVER_DATA} mechanism as
 * {@link MortaredCobbleData}, just conditional and reading static files instead of generated ones. To add more
 * Beneath data (tags, recipes, …) drop files under {@code beneath_datapack/data/…}.
 */
public final class BeneathDataPack
{
    private BeneathDataPack() {}

    private static final String BENEATH_MODID = "beneath";
    private static final String FOLDER = "beneath_datapack";

    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded(BENEATH_MODID))
        {
            return;
        }
        final Path source = ModList.get().getModFileById(MineColoniesTFC.MODID).getFile().findResource(FOLDER);
        MineColoniesTFC.LOGGER.info("Beneath detected — enabling optional Beneath datapack.");
        event.addRepositorySource(consumer ->
        {
            final Pack pack = Pack.readMetaAndCreate(
                    "mctfc_beneath",
                    Component.literal("MineColonies x TFC — Beneath compat"),
                    true, // forced on whenever Beneath is present
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
