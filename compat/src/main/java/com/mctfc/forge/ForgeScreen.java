package com.mctfc.forge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * The heat-forge's screen — a native {@link AbstractContainerScreen} over {@link ForgeMenu}, drawn procedurally (no
 * ship-a-PNG dependency): a light panel, dark slot wells for the fuel column + processing rows, and a vertical
 * temperature gauge whose fill tracks the live {@code deviceTemp} (and glows while lit). The heating item's own rising
 * heat is the crafting progress (no per-row progress bar), matching a real TFC forge.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu>
{
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF555555;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_WELL = 0xFF373737;
    private static final int GAUGE_BG = 0xFF202020;
    private static final int GAUGE_COOL = 0xFF803010;

    public ForgeScreen(final ForgeMenu menu, final Inventory inv, final Component title)
    {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(@NotNull final GuiGraphics graphics, final float partialTicks, final int mouseX, final int mouseY)
    {
        final int x = this.leftPos;
        final int y = this.topPos;

        // Panel with a bevelled border.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        graphics.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
        graphics.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);

        // Slot wells behind every machine slot (fuel column + processing rows).
        for (final Slot slot : menu.slots)
        {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory)
            {
                continue; // player inventory slots keep the flat panel
            }
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT_WELL);
        }

        drawGauge(graphics, x, y);
    }

    /** A vertical temperature gauge: dark well filled bottom-up in proportion to the live device temperature. */
    private void drawGauge(final GuiGraphics graphics, final int x, final int y)
    {
        final int gx = x + ForgeMenu.GAUGE_X;
        final int gy = y + ForgeMenu.GAUGE_Y;
        graphics.fill(gx - 1, gy - 1, gx + ForgeMenu.GAUGE_W + 1, gy + ForgeMenu.GAUGE_H + 1, SLOT_WELL);
        graphics.fill(gx, gy, gx + ForgeMenu.GAUGE_W, gy + ForgeMenu.GAUGE_H, GAUGE_BG);

        final float frac = Math.max(0f, Math.min(1f, menu.displayTemp() / ForgeMenu.DISPLAY_MAX_TEMP));
        final int filled = Math.round(frac * ForgeMenu.GAUGE_H);
        if (filled > 0)
        {
            graphics.fill(gx, gy + ForgeMenu.GAUGE_H - filled, gx + ForgeMenu.GAUGE_W, gy + ForgeMenu.GAUGE_H, heatColor(frac));
        }
        if (menu.displayLit())
        {
            // A warm outline while burning.
            final int glow = 0xFFFFA020;
            graphics.fill(gx - 1, gy - 1, gx + ForgeMenu.GAUGE_W + 1, gy, glow);
            graphics.fill(gx - 1, gy + ForgeMenu.GAUGE_H, gx + ForgeMenu.GAUGE_W + 1, gy + ForgeMenu.GAUGE_H + 1, glow);
        }
    }

    /** Cool-to-hot gradient (dull orange → bright yellow-white) for the gauge fill. */
    private static int heatColor(final float frac)
    {
        if (frac <= 0.5f)
        {
            return GAUGE_COOL;
        }
        final float t = (frac - 0.5f) / 0.5f;
        final int r = 0xC0 + Math.round(t * 0x3F);
        final int g = 0x40 + Math.round(t * 0xB0);
        final int b = 0x10 + Math.round(t * 0x30);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderLabels(@NotNull final GuiGraphics graphics, final int mouseX, final int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, menu.displayTemp() + "°C", ForgeMenu.GAUGE_X - 2, ForgeMenu.GAUGE_Y + ForgeMenu.GAUGE_H + 3, 0x404040, false);
    }

    @Override
    public void render(@NotNull final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
