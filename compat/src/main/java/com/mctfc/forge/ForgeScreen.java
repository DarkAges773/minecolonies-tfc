package com.mctfc.forge;

import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.capabilities.heat.Heat;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The heat-forge's screen. It blits a hand-paintable background texture ({@code textures/gui/heat_forge.png} — the
 * panel, slot wells, and the static heat-gauge gradient) and draws only the dynamic bits on top: the <b>sliding
 * temperature marker</b> over the gauge (positioned by TFC's own {@link Heat#scaleTemperatureForGui}) and, on hover,
 * the <b>TFC heat descriptor</b>. The gauge gradient lives in the texture — exactly like TFC's own forge GUI — so it
 * can be repainted freely without touching code.
 *
 * <p>The texture was machine-generated to match the code layout as a starting point; hand-edit the PNG to restyle.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation("mctfc", "textures/gui/heat_forge.png");

    public ForgeScreen(final ForgeMenu menu, final Inventory inv, final Component title)
    {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 206; // 6px taller: the player inventory + hotbar drop 6px, giving the "Inventory" label room
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(@NotNull final GuiGraphics graphics, final float partialTicks, final int mouseX, final int mouseY)
    {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        drawUnavailableSlots(graphics);
        drawMarker(graphics);
    }

    // The marker sprite lives in the texture's off-GUI region (like TFC's at u=176), so it's hand-paintable too.
    private static final int MARKER_U = 176;
    private static final int MARKER_V = 0;
    private static final int MARKER_H = 5;

    // The "unavailable slot" overlay sprite — also in the off-GUI region so it's hand-paintable. An 18×18 tile that
    // masks a full slot well (border included); it's blitted over the processing rows a forge doesn't (yet) have.
    private static final int SLOT_OVERLAY_U = 176;
    private static final int SLOT_OVERLAY_V = 8;
    private static final int SLOT_OVERLAY_SIZE = 18;

    /**
     * Mask the wells of processing rows this forge doesn't currently have. The panel is painted with a fixed
     * {@link ForgeMultiblock#CAP}-row layout (a controller can grow to that many members), but a smaller multiblock
     * exposes only {@link ForgeMenu#memberRows()} rows of real slots; the rest are blitted with the "unavailable"
     * overlay so the player sees they're inert (and where the forge can still grow). Blitted at well origin
     * (slot − 1px) to cover the 1px border baked into the background.
     */
    private void drawUnavailableSlots(final GuiGraphics graphics)
    {
        final int[] columns = { ForgeMenu.HEAT_X, ForgeMenu.OUTPUT_X, ForgeMenu.OVERFLOW_X };
        for (int r = menu.memberRows(); r < ForgeMultiblock.CAP; r++)
        {
            final int y = this.topPos + ForgeMenu.ROW_Y + r * 18 - 1;
            for (final int cx : columns)
            {
                graphics.blit(TEXTURE, this.leftPos + cx - 1, y, SLOT_OVERLAY_U, SLOT_OVERLAY_V, SLOT_OVERLAY_SIZE, SLOT_OVERLAY_SIZE);
            }
        }
    }

    /**
     * Blit the sliding temperature marker sprite over the gauge (its gradient is baked into the texture) — positioned
     * on TFC's 0..51 scale, centered on the temperature line, and hidden below visible heat like TFC's {@code pixel > 0}.
     */
    private void drawMarker(final GuiGraphics graphics)
    {
        // Exactly like TFC: hide the marker once the temperature drops below TFC's lowest visible heat (scale == 0),
        // rather than parking it at the bottom. So it slides down and then vanishes at the threshold — TFC leaves a
        // little sub-visible residual heat the gauge simply doesn't show.
        final int pixel = Heat.scaleTemperatureForGui(menu.displayTemp());
        if (pixel <= 0)
        {
            return;
        }
        final int gx = this.leftPos + ForgeMenu.GAUGE_X;
        final int gy = this.topPos + ForgeMenu.GAUGE_Y;
        // No bottom clamp (unlike an earlier version): TFC lets the marker ride to the last pixel, its body extending
        // slightly past the scale, so the cold end reaches the bottom instead of stopping ~2px short.
        final int top = Math.max(gy, gy + (ForgeMenu.GAUGE_H - pixel) - MARKER_H / 2);
        graphics.blit(TEXTURE, gx, top, MARKER_U, MARKER_V, ForgeMenu.GAUGE_W, MARKER_H);
    }

    @Override
    public void render(@NotNull final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
        // Hover the gauge → TFC's <b>own</b> config-aware temperature tooltip (the colored hotness word by default, or
        // real degrees if the player set that in TFC's options) — the exact call TFC's forge/firepit screen makes.
        // {@code formatColored} returns null below visible heat (a cold device), so show nothing then (as TFC does).
        // Hover box shifted up 2px (and 2px taller) to cover the gradient (hand-raised 2px in the asset) plus the
        // marker's full travel — the marker stays code-positioned, so this only widens the tooltip trigger area.
        if (RenderHelpers.isInside(mouseX, mouseY, this.leftPos + ForgeMenu.GAUGE_X, this.topPos + ForgeMenu.GAUGE_Y - 2, ForgeMenu.GAUGE_W, ForgeMenu.GAUGE_H + 2))
        {
            final Component heat = TFCConfig.CLIENT.heatTooltipStyle.get().formatColored(menu.displayTemp());
            if (heat != null)
            {
                graphics.renderTooltip(this.font, heat, mouseX, mouseY);
            }
        }
    }
}
