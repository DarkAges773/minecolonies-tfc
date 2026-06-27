package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.client.preset.PresetLibrary;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.preset.Preset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The preset hub, opened from {@link WindowReplacements}'s "Presets" button. Lists the player's editable library
 * presets followed by the read-only built-in ones, and lets the player:
 * <ul>
 *   <li><b>Save current</b> — snapshot the originating context's current picks under a typed name (new library preset).</li>
 *   <li><b>Load</b> — merge a preset's picks into the originating context (keeping picks for sources it doesn't mention).</li>
 *   <li><b>Edit</b> (library presets) — open {@link WindowReplacements} on a {@link PresetEditChoiceContext}.</li>
 *   <li><b>Clone</b> (built-in presets) — copy into the editable library.</li>
 *   <li><b>Delete</b> (library presets).</li>
 * </ul>
 */
public class WindowPresetList extends AbstractWindowSkeleton
{
    private static final String RESOURCE = "gui/windowpresetlist.xml";

    /** The picker we save from / load into (build wand or a building). */
    private final ReplacementChoiceContext target;
    private final BOWindow parent;
    private final ScrollingList list;
    private List<Preset> presets = List.of();

    public WindowPresetList(final ReplacementChoiceContext target, final BOWindow parent)
    {
        super(new ResourceLocation(StructurizeReplacements.MODID, RESOURCE));
        this.target = target;
        this.parent = parent;
        registerButton("done", this::returnToParent);
        registerButton("save", this::saveCurrent);
        this.list = findPaneOfTypeByID("presets", ScrollingList.class);
        this.list.setDataProvider(() -> presets.size(), this::updateRow);
    }

    @Override
    public void onOpened()
    {
        refreshList();
        super.onOpened();
    }

    /** User presets first (editable), then the read-only built-ins. */
    private void refreshList()
    {
        final List<Preset> all = new ArrayList<>(PresetLibrary.all());
        all.addAll(BuiltinPresets.all());
        this.presets = all;
        this.list.refreshElementPanes();
    }

    private void saveCurrent()
    {
        final Map<Block, Block> picks = new LinkedHashMap<>(target.current());
        if (picks.isEmpty())
        {
            // Nothing picked yet — don't create an empty preset.
            return;
        }
        final TextField input = findPaneOfTypeByID("presetName", TextField.class);
        final String name = input.getText() == null ? "" : input.getText().trim();
        PresetLibrary.create(name, picks);
        input.setText("");
        refreshList();
    }

    private void updateRow(final int index, final Pane row)
    {
        final Preset preset = presets.get(index);
        row.findPaneOfTypeByID("name", Text.class).setText(preset.displayName());

        row.findPaneOfTypeByID("load", ButtonImage.class).setHandler(b -> load(preset));

        // One shared action column: "Edit" for an editable library preset, "Clone" for a read-only built-in.
        final ButtonImage primary = row.findPaneOfTypeByID("primary", ButtonImage.class);
        final ButtonImage delete = row.findPaneOfTypeByID("delete", ButtonImage.class);
        if (preset.editable())
        {
            primary.setText(Component.translatable("structurizereplacements.gui.preset.edit"));
            primary.setHandler(b -> new WindowReplacements(new PresetEditChoiceContext(preset), this).open());
            delete.show();
            delete.setHandler(b -> { PresetLibrary.delete(preset.id()); refreshList(); });
        }
        else
        {
            primary.setText(Component.translatable("structurizereplacements.gui.preset.clone"));
            primary.setHandler(b -> {
                PresetLibrary.create(preset.displayName().getString() + " "
                        + Component.translatable("structurizereplacements.gui.preset.clone_suffix").getString(), preset.picks());
                refreshList();
            });
            delete.hide();
        }
    }

    /** Merge the preset's picks into the originating context, then return to it (it refreshes on open). */
    private void load(final Preset preset)
    {
        preset.picks().forEach(target::choose);
        returnToParent();
    }

    private void returnToParent()
    {
        if (parent != null)
        {
            parent.open();
        }
        else
        {
            close();
        }
    }
}
