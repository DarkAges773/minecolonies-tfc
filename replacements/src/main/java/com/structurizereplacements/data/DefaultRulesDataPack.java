package com.structurizereplacements.data;

import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Path;

/**
 * Registers an <b>optional, opt-in</b> built-in datapack ({@code default_rules_datapack/} inside our jar)
 * that ships a ready-made set of substitution rules so the standalone mod is useful out of the box.
 *
 * <p>Unlike the published library's stance of shipping <b>no active rules</b> (live rules would rewrite every
 * consumer's blueprints), this pack is <b>disabled by default</b> ({@code required = false} in
 * {@link Pack#readMetaAndCreate}). It appears in the world's "Data Packs" selection screen (and via
 * {@code /datapack enable}), but loads nothing until the player turns it on — so consumers who only want the
 * engine are unaffected, while a player running {@code structurizereplacements} on its own can flip it on for
 * an instant working "Replace" picker.
 *
 * <p>Its rules are all <b>interactive candidate pools</b> ({@code from_tag == to_tag}) — they auto-rewrite
 * nothing; each adds a row to the build-tool <i>Replace</i> GUI letting the player swap any block in the
 * family. Families covered: the full vanilla wood set (planks/logs/stairs/slabs/fences/gates/doors/trapdoors/
 * buttons/pressure-plates/signs), wool, terracotta, glazed terracotta, stained glass, concrete, concrete
 * powder, beds, and flowers (small + tall, kept as separate pools). Wood/wool/small-flower pools reuse vanilla
 * tags; the dye-color families and tall flowers use {@code structurizereplacements:subst/*} tags shipped inside
 * this pack folder (so they only exist when the pack is enabled).
 *
 * <p>Same forced-built-in {@link PackType#SERVER_DATA} mechanism as the {@code :compat} optional packs, but
 * {@code required = false} (off by default) rather than {@code true} (forced on). To extend it, drop more files
 * under {@code default_rules_datapack/data/…}.
 */
public final class DefaultRulesDataPack
{
    private DefaultRulesDataPack() {}

    private static final String FOLDER = "default_rules_datapack";

    /**
     * A {@link PackSource} that does <b>not</b> auto-enable the pack for new worlds. Forge's
     * {@code MinecraftServer.configurePackRepository} auto-selects every discovered pack whose
     * {@code PackSource.shouldAddAutomatically()} is {@code true} — and {@link PackSource#BUILT_IN} is one
     * of those, which is why a plain built-in pack shows up pre-selected. This source returns {@code false}
     * (like the vanilla {@code FEATURE} source) so the pack stays in <i>Available</i> until the player opts
     * in, while keeping an undecorated display name.
     */
    private static final PackSource OPT_IN = PackSource.create(PackSource.NO_DECORATION, false);

    public static void onAddPackFinders(final AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA)
        {
            return;
        }
        final Path source = ModList.get().getModFileById(StructurizeReplacements.MODID).getFile().findResource(FOLDER);
        event.addRepositorySource(consumer ->
        {
            final Pack pack = Pack.readMetaAndCreate(
                    "structurizereplacements_defaults",
                    Component.literal("Structurize Replacements — default pick pools"),
                    false, // optional: available in the Data Packs screen, OFF by default (opt-in)
                    id -> new PathPackResources(id, true, source),
                    PackType.SERVER_DATA,
                    Pack.Position.TOP,
                    OPT_IN);
            if (pack != null)
            {
                consumer.accept(pack);
            }
        });
    }
}
