package com.structurizereplacements.integration.colony;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.Box;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.ScrollingListContainer.RowSizeModifier;
import com.structurizereplacements.client.gui.ButtonImageWithIcon;
import com.structurizereplacements.client.gui.WindowReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Wraps the miner settings window's {@link ScrollingList.DataProvider} to append <b>one extra row</b> at the
 * bottom of the scrollable settings list: an "Edit Mineshaft Palette" button that opens the candidate-block
 * picker scoped to the miner's mineshaft palette ({@link MineshaftChoiceContext}). Installed by the fork's
 * {@code MixinSettingsModuleWindow} (miner only) via {@code @ModifyArg} on the list's {@code setDataProvider}
 * call, so the real settings rows keep rendering through the colony mod's own provider ({@link #delegate}).
 * {@code buildingView} is the fork's building view (passed through to the context); the mini-button frame
 * texture is borrowed from the loaded fork's asset namespace ({@link ColonyBridge#assetNamespace}).
 *
 * <p>The extra row honours blockui's row-recycling: a row pane carries an {@code id} {@link Text} that the
 * settings provider compares against the setting key to decide whether to rebuild a recycled pane. Our row
 * stamps that field with {@link #MARKER} (never a real setting id) so a recycled <i>setting</i> row is
 * rebuilt correctly, and we rebuild our own row only when the marker is absent.
 */
public class MineshaftSettingsListProvider implements ScrollingList.DataProvider
{
    private static final String MARKER = "structurizereplacements:mineshaft_row";
    private static final String LABEL  = "structurizereplacements.gui.replace.mineshaft.button";

    private final ScrollingList.DataProvider delegate;
    private final Object buildingView;
    private final BOWindow parent;

    public MineshaftSettingsListProvider(final ScrollingList.DataProvider delegate,
                                         final Object buildingView,
                                         final BOWindow parent)
    {
        this.delegate = delegate;
        this.buildingView = buildingView;
        this.parent = parent;
    }

    /** The synthetic row is always the last index, after all the real settings. */
    private boolean isExtraRow(final int index)
    {
        return index == delegate.getElementCount();
    }

    @Override
    public int getElementCount()
    {
        return delegate.getElementCount() + 1;
    }

    @Override
    public boolean shouldUpdate()
    {
        return delegate.shouldUpdate();
    }

    @Override
    public boolean shouldUpdate(final int index)
    {
        return isExtraRow(index) || delegate.shouldUpdate(index);
    }

    @Override
    public void modifyRowSize(final int index, final RowSizeModifier modifier)
    {
        if (!isExtraRow(index))
        {
            delegate.modifyRowSize(index, modifier);
        }
    }

    @Override
    public void updateElement(final int index, final Pane rowPane)
    {
        if (isExtraRow(index))
        {
            renderButtonRow(rowPane);
        }
        else
        {
            delegate.updateElement(index, rowPane);
        }
    }

    private void renderButtonRow(final Pane rowPane)
    {
        final Box box = rowPane.findPaneOfTypeByID("box", Box.class);
        if (box == null)
        {
            return;
        }
        final Text existing = box.findPaneOfTypeByID("id", Text.class);
        if (existing != null && MARKER.equals(existing.getTextAsString()))
        {
            return; // already our row (recycled back to itself) — nothing to rebuild
        }

        // Pane was fresh or recycled from a setting row: rebuild it as our row, mirroring the native
        // setting-row layout (hidden "id" marker + black "desc" label + a control below).
        box.getChildren().clear();

        // Recycling marker: native rows carry a hidden "id" text that the settings provider compares to the
        // setting key to decide whether to rebuild a recycled pane; ours holds a sentinel that never matches.
        final Text marker = new Text();
        marker.setID("id");
        marker.setText(Component.literal(MARKER));
        marker.setSize(0, 0);
        box.addChild(marker);

        // Row label — same placement/colour as a native setting's description.
        final Text desc = new Text();
        desc.setID("desc");
        desc.setText(Component.translatable(LABEL));
        // setColors (not setTextColor): sets the base, disabled AND hover colour all to black, matching the
        // native rows' color="black" — otherwise the hover colour stays at its default and the label shifts
        // colour on mouse-over, unlike every other label in the list.
        desc.setColors(0x000000);
        desc.setTextAlignment(Alignment.MIDDLE_LEFT);
        desc.setSize(154, 15);
        desc.setPosition(5, 5);
        box.addChild(desc);

        // Action control — our replace icon over the colony mod's mini-button frame, the same icon-button
        // style as the "Replace" button on the Build Options window (2px frame visible, icon fills the rest).
        final ButtonImageWithIcon button = new ButtonImageWithIcon(
                ResourceLocation.fromNamespaceAndPath("structurizereplacements", "textures/gui/replace_button.png"), 2);
        button.setID("structurizereplacements:mineshaftPalette");
        button.setImage(ResourceLocation.fromNamespaceAndPath(ColonyIntegration.bridge().assetNamespace(),
                "textures/gui/builderhut/builder_button_mini.png"));
        button.setSize(16, 16);
        button.setPosition(5, 24);
        button.setHandler(b -> new WindowReplacements(new MineshaftChoiceContext(buildingView), parent).open());
        box.addChild(button);
    }
}
