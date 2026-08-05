package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.UiRenderMacros;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.util.records.SizeI;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;

/**
 * A {@link ButtonImage} that draws its background image (a button frame) and then overlays a separate
 * icon texture on top, centred with an inset, at the icon's <i>full source resolution</i>.
 *
 * <p>Why not bake the icon into one small composite texture: doing so shrinks the icon to the button's
 * pixel size up-front, capping its detail before the GPU ever draws it ("compounding" two shrink steps).
 * Here the icon stays at its native resolution and is downsampled only once — by the GPU at draw time —
 * so it stays crisp at higher GUI scales / on hi-DPI displays. It also keeps the icon a plain editable
 * asset (no generation step) and lets the background be another mod's button texture, referenced directly.
 *
 * <p>The overlay is drawn in {@link #postDrawBackground} — the hook {@code ButtonImage} calls right after
 * blitting its background, while blend is still enabled and the shader colour already carries the hover
 * brighten, so the icon tints consistently with the frame on hover.
 */
public class ButtonImageWithIcon extends ButtonImage
{
    private final ResourceLocation icon;
    private final int               iconSrcW;
    private final int               iconSrcH;
    private final int               inset;

    /**
     * @param icon  the icon texture to overlay (drawn at full source resolution).
     * @param inset pixels of background frame left visible on each side (the icon fills the rest).
     */
    public ButtonImageWithIcon(final ResourceLocation icon, final int inset)
    {
        this.icon = icon;
        this.inset = inset;
        final SizeI dim = Image.getImageDimensions(icon);
        this.iconSrcW = dim.width();
        this.iconSrcH = dim.height();
    }

    /**
     * BlockUI's 1.21.1 hook takes only the graphics + mouse position — the background's geometry and texture
     * arguments are gone. {@code ButtonImage#drawSelf} still blits its background at the pane's absolute
     * {@code x}/{@code y}/{@code width}/{@code height} (the protected {@code Pane} fields) and calls this
     * straight after with blend enabled and the hover tint already on the shader colour, so the overlay just
     * reads that geometry off {@code this} instead of off parameters.
     */
    @Override
    public void postDrawBackground(final BOGuiGraphics target, final double mx, final double my)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Whole icon (0,0..src) blitted into the inset rect; GPU does the single downscale.
        UiRenderMacros.blit(target.pose(), icon,
          x + inset, y + inset, width - 2 * inset, height - 2 * inset,
          0, 0, iconSrcW, iconSrcH, iconSrcW, iconSrcH);
    }
}
