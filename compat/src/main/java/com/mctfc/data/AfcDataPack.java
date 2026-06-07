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
 * Registers an <b>optional</b> built-in datapack ({@code afc_datapack/} inside our jar) that is enabled
 * <b>only when ArborFirmaCraft (mod id {@code afc}) is loaded</b>. It ships AFC-specific substitution data
 * ({@code data/mctfc/block_substitutions/afc.json} + additive {@code subst/wood/*} pool tags); because the
 * pack isn't registered when AFC is absent, those {@code afc:*} rules never load and never warn.
 *
 * <p>The AFC rules use {@code "priority": 1}, so when AFC is present they <b>override</b> the base TFC wood
 * mapping (tfc_wood.json, priority 0) — e.g. {@code birch -> afc:eucalyptus} instead of {@code douglas_fir}
 * (see {@code BlockSubstitutions#bestFixedRule}). The additive (replace:false) pool tags merge AFC's woods
 * into the base {@code mctfc:subst/wood/<form>} candidate pools so they're pickable in the GUI like any wood.
 *
 * <p>Same forced built-in {@link PackType#SERVER_DATA} mechanism as {@link BeneathDataPack}, gated on
 * {@link ModList#isLoaded}. To add more AFC data (recipes, other substitutions, …) drop files under
 * {@code afc_datapack/data/…}.
 */
public final class AfcDataPack
{
    private AfcDataPack() {}

    private static final String AFC_MODID = "afc";
    private static final String FOLDER = "afc_datapack";

    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded(AFC_MODID))
        {
            return;
        }
        final Path source = ModList.get().getModFileById(MineColoniesTFC.MODID).getFile().findResource(FOLDER);
        MineColoniesTFC.LOGGER.info("ArborFirmaCraft detected — enabling optional AFC datapack.");
        event.addRepositorySource(consumer ->
        {
            final Pack pack = Pack.readMetaAndCreate(
                    "mctfc_afc",
                    Component.literal("MineColonies x TFC — ArborFirmaCraft compat"),
                    true, // forced on whenever AFC is present
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
