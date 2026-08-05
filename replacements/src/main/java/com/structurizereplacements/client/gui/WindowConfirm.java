package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.structurizereplacements.StructurizeReplacements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A small modal yes/no confirmation dialog, so a destructive action (e.g. deleting a user preset) can't be
 * triggered by a single mis-click. Confirming runs the supplied action and returns to {@code parent}; cancelling
 * just returns. The caller is responsible for refreshing {@code parent} inside its action if needed (BlockUI
 * reuses the parent's cached screen, so its {@code onOpened} won't re-fire on return).
 */
public class WindowConfirm extends AbstractWindowSkeleton
{
    private static final String RESOURCE = "gui/windowconfirm.xml";

    private final BOWindow parent;
    private final Runnable onConfirm;

    public WindowConfirm(final BOWindow parent, final Component title, final Component message,
                         final Component confirmLabel, final Runnable onConfirm)
    {
        super(new ResourceLocation(StructurizeReplacements.MODID, RESOURCE));
        this.parent = parent;
        this.onConfirm = onConfirm;
        findPaneOfTypeByID("title", Text.class).setText(title);
        findPaneOfTypeByID("message", Text.class).setText(message);
        findPaneOfTypeByID("confirm", ButtonImage.class).setText(confirmLabel);
        registerButton("confirm", this::confirm);
        registerButton("cancel", this::returnToParent);
    }

    private void confirm()
    {
        onConfirm.run();
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
