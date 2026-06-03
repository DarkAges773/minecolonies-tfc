package com.mctfc.mixin;

import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.View;
import com.mctfc.farming.FarmFieldHarvestMode;
import com.mctfc.farming.HarvestMode;
import com.mctfc.network.McFarmingNetwork;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.containers.WindowField;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only: adds a harvest-mode toggle (Fruiting / Seeding) to MineColonies' field GUI
 * ({@code WindowField}, the scarecrow window), below the seed icon in the otherwise-empty left column.
 * Mirrors how the window's own seed selector works — optimistically updates the client field and sends a
 * {@link McFarmingNetwork} message to the server. The label is refreshed every {@code onUpdate}, since the
 * client {@code farmField} is resolved a tick after construction (and re-synced thereafter).
 *
 * <p>Shadows are all members declared on {@code WindowField} itself ({@code farmField}, {@code getCurrentColony})
 * so they resolve reliably. {@code remap = false}: MineColonies' own class/members.
 */
@Mixin(value = WindowField.class, remap = false)
public abstract class MixinWindowField
{
    @Shadow
    @Nullable
    private FarmField farmField;

    @Shadow
    private IColonyView getCurrentColony()
    {
        throw new AssertionError("shadow");
    }

    @Unique
    private ButtonImage mctfc$modeButton;

    @Inject(method = "<init>(Lcom/minecolonies/core/tileentities/TileEntityScarecrow;)V", at = @At("TAIL"))
    private void mctfc$addModeButton(final TileEntityScarecrow scarecrow, final CallbackInfo ci)
    {
        final ButtonImage button = new ButtonImage();
        button.setID("mctfc:harvest-mode");
        button.setImage(new ResourceLocation("minecolonies", "textures/gui/builderhut/builder_button_medium.png"), false);
        button.setSize(86, 17);
        button.setPosition(13, 110);
        // ButtonImage#setSize rescales the text-render box from its previous value, which the no-arg ctor
        // leaves at 0 — so the box stays 0 and the label never draws. Set it explicitly to the button size.
        button.setTextRenderBox(86, 17);
        // The no-arg ButtonImage defaults text to white (invisible on the light button); match the window's
        // XML buttons (black). Alignment already defaults to MIDDLE.
        button.setColors(0x000000);
        button.setTextAlignment(Alignment.MIDDLE);
        button.setHandler(b -> mctfc$toggleMode());
        ((View) (Object) this).addChild(button);
        mctfc$modeButton = button;
        // Tooltip must be attached after the button is in the window (it resolves the hover pane's parent).
        PaneBuilders.singleLineTooltip(Component.translatable("gui.mctfc.field.harvestmode.tooltip"), button);
        mctfc$refreshModeButton();
    }

    @Inject(method = "onUpdate()V", at = @At("TAIL"))
    private void mctfc$refreshOnUpdate(final CallbackInfo ci)
    {
        mctfc$refreshModeButton();
    }

    @Unique
    private void mctfc$refreshModeButton()
    {
        if (mctfc$modeButton == null)
        {
            return;
        }
        final boolean show = this.farmField != null && getCurrentColony() != null;
        mctfc$modeButton.setVisible(show);
        if (show)
        {
            mctfc$modeButton.setText(Component.translatable(mctfc$currentMode().labelKey()));
        }
    }

    @Unique
    private HarvestMode mctfc$currentMode()
    {
        return this.farmField instanceof FarmFieldHarvestMode holder ? holder.mctfc$getHarvestMode() : HarvestMode.FRUITING;
    }

    @Unique
    private void mctfc$toggleMode()
    {
        final IColonyView colony = getCurrentColony();
        if (colony == null || !(this.farmField instanceof FarmFieldHarvestMode holder))
        {
            return;
        }
        final HarvestMode next = holder.mctfc$getHarvestMode().next();
        holder.mctfc$setHarvestMode(next); // optimistic, like the seed selector
        McFarmingNetwork.sendHarvestMode(colony.getDimension(), colony.getID(), this.farmField.getPosition(), next);
        mctfc$refreshModeButton();
    }
}
