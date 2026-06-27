package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ldtteam.blockui.controls.Tooltip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Tooltip} that draws a small block/item icon to the left of selected text rows — used by the
 * Replace picker's "affects" tooltip so each affected block shows its icon (and, for Domum Ornamentum
 * blocks, its actual textured material, since the host {@link ItemStack} carries the material NBT) beside
 * its name.
 *
 * <p>It reuses {@link Tooltip} wholesale for cursor positioning, the background box, sizing and text
 * rendering; this class only overlays the icons. Each row's label is indented past a fixed left gutter (see
 * {@link #indent()}) so the icon never overlaps the text, and icons are aligned to their row by re-counting
 * how many (possibly wrapped) lines each preceding row occupies — so a name that happens to wrap doesn't
 * shift every icon below it.
 */
public class IconTooltip extends Tooltip
{
    private static final int ICON_SIZE = 16;
    /** Reserved left gutter (px) that each row's text is indented past, leaving room for the icon. */
    private static final int ICON_COLUMN = 20;
    /** Icon left edge within the gutter (centers a 16px icon in the 20px column). */
    private static final int ICON_X = 6;
    /** Extra px between lines so a 16px icon fits alongside one ~9px text line without overlapping the next. */
    private static final int LINESPACE = 9;
    /** Z above {@link Tooltip}'s own text/background pass (200) so the icons sit on top of the box. */
    private static final double ICON_Z = 250.0d;

    /** Icon per input row, index-aligned to {@link #rowTexts}; {@code null}/empty = no icon (a header row). */
    private final List<ItemStack> rowIcons = new ArrayList<>();
    /** The input row components (pre-wrapping), re-measured in {@link #drawSelfLast} to place icons by line. */
    private final List<MutableComponent> rowTexts = new ArrayList<>();
    /** Whether any row actually carries an icon — gates the box-height compensation in {@link #recalcPreparedTextBox()}. */
    private boolean hasIcon;

    @SuppressWarnings("deprecation") // builder-less construction; we set rows directly
    public IconTooltip()
    {
        super();
        setTextLinespace(LINESPACE);
    }

    /**
     * The indent string a caller must prepend to every row label so the text clears the icon gutter. Sized in
     * spaces to span {@link #ICON_COLUMN} px at the current font.
     */
    public static String indent()
    {
        final int spaceWidth = Math.max(1, Minecraft.getInstance().font.width(" "));
        return " ".repeat(Math.max(1, (ICON_COLUMN + spaceWidth - 1) / spaceWidth));
    }

    /** Set the rows: {@code texts} are the (already-indented) labels, {@code icons} the per-row icon (null = none). */
    public void setRows(final List<MutableComponent> texts, final List<ItemStack> icons)
    {
        rowTexts.clear();
        rowTexts.addAll(texts);
        rowIcons.clear();
        rowIcons.addAll(icons);
        hasIcon = icons.stream().anyMatch(s -> s != null && !s.isEmpty());
        setText(new ArrayList<>(texts));
    }

    @Override
    public void recalcPreparedTextBox()
    {
        super.recalcPreparedTextBox();
        // The base height ends at the last text line and even drops the trailing linespace; the last row's icon
        // is taller than its text line and would otherwise spill past the bottom border. Add back the icon's
        // below-the-line overhang (plus a px of margin) so the box encloses it.
        if (hasIcon)
        {
            renderedTextHeight += (ICON_SIZE - mc.font.lineHeight + 1) / 2 + 1;
        }
    }

    @Override
    public void drawSelfLast(final BOGuiGraphics target, final double mx, final double my)
    {
        super.drawSelfLast(target, mx, my); // box + text; also sets this.x / this.y to the placed position
        if (preparedText.isEmpty() || !enabled)
        {
            return;
        }
        // The exact width the text was wrapped at, so our per-row line count matches the rendered lines.
        final int splitWidth = Math.max(1, (int) (textWidth / textScale) - (textShadow ? 1 : 0));
        final int pitch = mc.font.lineHeight + textLinespace;
        final int iconLift = (ICON_SIZE - mc.font.lineHeight) / 2; // center the 16px icon on its ~9px text line

        final PoseStack pose = target.pose();
        pose.pushPose();
        pose.translate(x, y, ICON_Z);
        int line = 0;
        for (int i = 0; i < rowTexts.size(); i++)
        {
            final ItemStack icon = i < rowIcons.size() ? rowIcons.get(i) : null;
            if (icon != null && !icon.isEmpty())
            {
                final int lineTop = textOffsetY + line * pitch;
                target.renderItem(icon, ICON_X, lineTop - iconLift);
            }
            line += Math.max(1, mc.font.split(rowTexts.get(i), splitWidth).size());
        }
        pose.popPose();
    }
}
