package com.structurizereplacements.client.gui;

import com.structurizereplacements.client.preset.PresetLibrary;
import com.structurizereplacements.preset.Preset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ReplacementChoiceContext} for editing a user-library {@link Preset} in place. Unlike the blueprint-backed
 * contexts, its rows <i>are</i> the preset's own picks (so {@link #sources()} is the pick keys), retargeting a row
 * reuses the same candidate-pool picker, and deleting a row removes that pick. Every change is written straight back
 * to the {@link PresetLibrary} JSON, so edits persist without a separate save step. Built-in presets are never edited
 * here — they're cloned into the library first.
 */
public class PresetEditChoiceContext implements ReplacementChoiceContext
{
    private final String id;
    private String name;
    private final String folder;
    private final Block icon;
    private final Map<Block, Block> picks;
    private Runnable reloader = () -> {};

    public PresetEditChoiceContext(final Preset preset)
    {
        this.id = preset.id();
        this.name = preset.displayName().getString();
        this.folder = preset.folder();
        this.icon = preset.icon();
        this.picks = new LinkedHashMap<>(preset.picks());
    }

    @Override
    public List<Block> sources()
    {
        return new ArrayList<>(picks.keySet());
    }

    @Override
    public Map<Block, Block> current()
    {
        return picks;
    }

    @Override
    public void choose(final Block source, final Block target)
    {
        if (target == null)
        {
            picks.remove(source);
        }
        else
        {
            picks.put(source, target);
        }
        persist();
    }

    @Override
    public void removePick(final Block source)
    {
        picks.remove(source);
        persist();
        // The row set changed — ask the window to re-read sources() so the deleted row disappears.
        reloader.run();
    }

    @Override
    public void reset()
    {
        picks.clear();
        persist();
    }

    @Override
    public boolean allowRowDelete()
    {
        return true;
    }

    @Override
    public boolean offersPresetMenu()
    {
        return false;
    }

    @Override
    public Component titleComponent()
    {
        return Component.translatable("structurizereplacements.gui.preset.edit.title", name);
    }

    @Override
    public String editableName()
    {
        return name;
    }

    @Override
    public void setName(final String newName)
    {
        this.name = newName;
        persist();
    }

    @Override
    public void setReloader(final Runnable reloader)
    {
        this.reloader = reloader;
    }

    private void persist()
    {
        PresetLibrary.update(new Preset(id, Component.literal(name), folder, icon, picks, true));
    }
}
