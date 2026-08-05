package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.client.preset.PresetLibrary;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.preset.Preset;
import com.structurizereplacements.substitution.BlockSubstitutions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The preset hub, opened from {@link WindowReplacements}'s "Presets" button. Presets are organised into
 * <b>folders</b> (subdirectories for the user library; datapack subpaths for built-ins), and this window is
 * navigable: folder rows drill in, an "Up" row climbs out, and {@link #path} tracks the current folder. Within a
 * folder it lists the user's editable presets followed by the read-only built-ins, each with an icon, and lets the
 * player:
 * <ul>
 *   <li><b>Save current</b> — snapshot the originating context's current picks under a typed name (into this folder;
 *       a name with {@code /} nests further).</li>
 *   <li><b>Load</b> — merge a preset's picks into the originating context (keeping picks it doesn't mention).</li>
 *   <li><b>Edit</b> (library presets) — open {@link WindowReplacements} on a {@link PresetEditChoiceContext}.</li>
 *   <li><b>Clone</b> (built-in presets) — copy into the editable library (in this folder).</li>
 *   <li><b>Delete</b> (library presets).</li>
 * </ul>
 */
public class WindowPresetList extends AbstractWindowSkeleton
{
    private static final String RESOURCE = "gui/windowpresetlist.xml";

    /** A row is either a folder (drill-in / the ".." up row) or a preset. */
    private record Row(@Nullable Preset preset, @Nullable String folder, boolean up) {}

    /** The picker we save from / load into (build wand or a building). */
    private final ReplacementChoiceContext target;
    private final BOWindow parent;
    private final ScrollingList list;
    /** Current folder ({@code ""} = library root). */
    private String path = "";
    private List<Row> rows = List.of();

    public WindowPresetList(final ReplacementChoiceContext target, final BOWindow parent)
    {
        super(ResourceLocation.fromNamespaceAndPath(StructurizeReplacements.MODID, RESOURCE));
        this.target = target;
        this.parent = parent;
        registerButton("done", this::returnToParent);
        registerButton("save", this::saveCurrent);
        this.list = findPaneOfTypeByID("presets", ScrollingList.class);
        this.list.setDataProvider(() -> rows.size(), this::updateRow);

        // The placeholder Text sits over the name field; disable it so it never captures clicks
        // (canHandleClick is gated on enabled) — clicks fall through to the input behind it.
        final Text hint = findPaneOfTypeByID("nameHint", Text.class);
        if (hint != null)
        {
            hint.setEnabled(false);
        }
    }

    @Override
    public void onOpened()
    {
        refresh();
        super.onOpened();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        // Placeholder: show the gray hint only while the name field is empty (BlockUI's TextField has no native
        // placeholder, so we overlay a Text and toggle it here).
        final TextField input = findPaneOfTypeByID("presetName", TextField.class);
        final Text hint = findPaneOfTypeByID("nameHint", Text.class);
        if (input != null && hint != null)
        {
            final boolean empty = input.getText() == null || input.getText().isEmpty();
            if (empty)
            {
                hint.show();
            }
            else
            {
                hint.hide();
            }
        }
    }

    /**
     * Rebuild the rows for the current folder: an "Up" row (unless root), then subfolders, then presets. Re-reads
     * the library from disk, so it reflects edits made in a child editor window. Package-visible so
     * {@link WindowReplacements} can refresh this list when its preset editor returns here ({@code BOWindow.open()}
     * reuses the cached screen, so the parent's {@code onOpened} doesn't re-fire).
     */
    void refresh()
    {
        final List<Preset> all = new ArrayList<>(PresetLibrary.all());
        all.addAll(BuiltinPresets.all());

        final TreeSet<String> subfolders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final List<Preset> here = new ArrayList<>();
        for (final Preset preset : all)
        {
            final String folder = preset.folder();
            if (folder.equals(path))
            {
                here.add(preset);
            }
            else if (isUnderPath(folder))
            {
                subfolders.add(childSegment(folder));
            }
        }

        final List<Row> built = new ArrayList<>();
        if (!path.isEmpty())
        {
            built.add(new Row(null, null, true));
        }
        for (final String sub : subfolders)
        {
            built.add(new Row(null, sub, false));
        }
        for (final Preset preset : here)
        {
            built.add(new Row(preset, null, false));
        }
        this.rows = built;
        this.list.refreshElementPanes();
    }

    private void updateRow(final int index, final Pane row)
    {
        final Row entry = rows.get(index);
        final ItemIcon icon = row.findPaneOfTypeByID("icon", ItemIcon.class);
        final Text name = row.findPaneOfTypeByID("name", Text.class);
        final ButtonImage load = row.findPaneOfTypeByID("load", ButtonImage.class);
        final ButtonImage primary = row.findPaneOfTypeByID("primary", ButtonImage.class);
        final ButtonImage delete = row.findPaneOfTypeByID("delete", ButtonImage.class);
        final ButtonImage open = row.findPaneOfTypeByID("open", ButtonImage.class);

        if (entry.preset() == null)
        {
            // Folder or "Up" row: a chest icon, the folder name (or ".."), and a single wide "Open" button.
            icon.setItem(new ItemStack(Items.CHEST));
            name.setText(entry.up() ? Component.literal("..") : folderLabel(entry.folder()));
            load.hide();
            primary.hide();
            delete.hide();
            open.show();
            // The "Up" row goes back out of the current folder; a subfolder row opens (drills in).
            open.setText(Component.translatable(entry.up()
                    ? "structurizereplacements.gui.preset.back"
                    : "structurizereplacements.gui.preset.open"));
            open.setHandler(b -> { navigate(entry); });
            return;
        }

        final Preset preset = entry.preset();
        open.hide();
        icon.setItem(iconFor(preset));
        name.setText(preset.displayName());
        load.show();
        load.setHandler(b -> load(preset));

        primary.show();
        if (preset.editable())
        {
            primary.setText(Component.translatable("structurizereplacements.gui.preset.edit"));
            primary.setHandler(b -> new WindowReplacements(
                    new PresetEditChoiceContext(preset, target::current), this).open());
            delete.show();
            delete.setHandler(b -> new WindowConfirm(this,
                    Component.translatable("structurizereplacements.gui.preset.delete.title"),
                    Component.translatable("structurizereplacements.gui.preset.delete.confirm", preset.displayName()),
                    Component.translatable("structurizereplacements.gui.preset.delete"),
                    () -> { PresetLibrary.delete(preset.id()); refresh(); }).open());
        }
        else
        {
            primary.setText(Component.translatable("structurizereplacements.gui.preset.clone"));
            primary.setHandler(b -> {
                final String copyName = preset.displayName().getString() + " "
                        + Component.translatable("structurizereplacements.gui.preset.clone_suffix").getString();
                // Clones always land in the library root (the player's own space), not inside the read-only
                // built-in folder they were cloned from.
                PresetLibrary.create("", copyName, preset.picks(), preset.icon());
                refresh();
            });
            delete.hide();
        }
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
        PresetLibrary.create(path, name, picks);
        input.setText("");
        refresh();
    }

    /** Merge the preset's picks into the originating context, then return to it (it refreshes on open). */
    private void load(final Preset preset)
    {
        preset.picks().forEach(target::choose);
        returnToParent();
    }

    private void navigate(final Row entry)
    {
        if (entry.up())
        {
            final int slash = path.lastIndexOf('/');
            path = slash < 0 ? "" : path.substring(0, slash);
        }
        else
        {
            path = path.isEmpty() ? entry.folder() : path + "/" + entry.folder();
        }
        refresh();
    }

    private void returnToParent()
    {
        if (parent != null)
        {
            // The picker's onOpened won't re-fire on reopen (BOWindow reuses its cached screen), so refresh its rows
            // explicitly — otherwise a just-loaded preset's picks wouldn't show when we return to it.
            if (parent instanceof WindowReplacements picker)
            {
                picker.reload();
            }
            parent.open();
        }
        else
        {
            close();
        }
    }

    // --- helpers --------------------------------------------------------------------------------------------

    /** Whether {@code folder} is strictly below the current {@link #path}. */
    private boolean isUnderPath(final String folder)
    {
        return path.isEmpty() ? !folder.isEmpty() : folder.startsWith(path + "/");
    }

    /** The immediate subfolder name of {@code folder} under the current {@link #path}. */
    private String childSegment(final String folder)
    {
        final String rest = path.isEmpty() ? folder : folder.substring(path.length() + 1);
        final int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static ItemStack iconFor(final Preset preset)
    {
        final Block block = preset.iconBlock();
        return block == null ? ItemStack.EMPTY : BlockSubstitutions.iconStack(block);
    }

    /** Pretty folder label: {@code rock_types} → {@code Rock Types}. */
    private static Component folderLabel(final String segment)
    {
        final StringBuilder out = new StringBuilder();
        for (final String word : segment.replace('_', ' ').trim().split(" "))
        {
            if (word.isEmpty())
            {
                continue;
            }
            if (out.length() > 0)
            {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.literal(out.toString());
    }
}
