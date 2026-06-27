package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.CandidateRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The replacement picker window: one row per distinct candidate source block, showing source → current
 * choice and a button that opens Structurize's {@link WindowSelectRes} restricted to the candidate
 * {@code to_tag} pool. <i>What</i> it edits is supplied by a {@link ReplacementChoiceContext} — the build
 * wand's global session picks ({@link BuildWandChoiceContext}) or a single MineColonies building
 * (in {@code :compat}). Passing {@code this} as the picker's origin makes it reopen this window after a
 * pick, refreshing the rows.
 */
public class WindowReplacements extends AbstractWindowSkeleton
{
    private static final String RESOURCE = "gui/windowreplacements.xml";

    /** Floor for the gentle auto-shrink in {@link #setFittedText} — below this text gets small, so ellipsize instead. */
    private static final double MIN_TEXT_SCALE = 0.85;

    /** Ellipsis appended to a name truncated to fit its cell; the full name is then offered on hover. */
    private static final String ELLIPSIS = "…";

    private final ReplacementChoiceContext context;
    private final BOWindow parent;
    private final ScrollingList list;
    private List<Block> sources = List.of();
    /** Per-source: the distinct blueprint blocks a swap would affect (for the row tooltip + count badge). */
    private Map<Block, List<ItemStack>> affected = Map.of();

    /** Build-wand default: edits the global session picks. */
    public WindowReplacements(final BOWindow parent)
    {
        this(new BuildWandChoiceContext(), parent);
    }

    /**
     * @param parent the window to reopen when this one is dismissed (so "Done" returns to the build tool /
     *               Build Options window instead of closing to the game); {@code null} just closes.
     */
    public WindowReplacements(final ReplacementChoiceContext context, final BOWindow parent)
    {
        super(new ResourceLocation(StructurizeReplacements.MODID, RESOURCE));
        this.context = context;
        this.parent = parent;
        this.context.setReloader(this::reload);
        registerButton("done", this::returnToParent);
        registerButton("reset", this::resetChoices);
        registerButton("paletteMode", this::togglePaletteMode);
        this.list = findPaneOfTypeByID("rows", ScrollingList.class);
        this.list.setDataProvider(() -> sources.size(), this::updateRow);

        final Text title = findPaneOfTypeByID("title", Text.class);
        if (title != null)
        {
            title.setText(Component.translatable(context.titleKey()));
        }

        final ButtonImage modeButton = findPaneOfTypeByID("paletteMode", ButtonImage.class);
        if (context.hasPaletteModeToggle())
        {
            updatePaletteModeButton(modeButton);
            PaneBuilders.singleLineTooltip(
                    Component.translatable("structurizereplacements.gui.replace.palette.tooltip"), modeButton);
        }
        else
        {
            modeButton.hide();
        }
    }

    private void togglePaletteMode()
    {
        context.setUpdateMode(!context.isUpdateMode());
        updatePaletteModeButton(findPaneOfTypeByID("paletteMode", ButtonImage.class));
        // sources reload asynchronously via the context's reloader; rows redraw then.
    }

    /** Label the toggle with the palette tier it is currently showing. */
    private void updatePaletteModeButton(final ButtonImage button)
    {
        button.setText(Component.translatable(context.isUpdateMode()
                ? "structurizereplacements.gui.replace.palette.update"
                : "structurizereplacements.gui.replace.palette.current"));
    }

    private void resetChoices()
    {
        this.context.reset();
        // Re-read rows so every source shows the default ("?") again.
        this.list.refreshElementPanes();
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

    @Override
    public void onOpened()
    {
        this.sources = new ArrayList<>(context.sources());
        this.affected = context.affectedBlocks();
        super.onOpened();
        this.list.refreshElementPanes();
    }

    @Override
    public void onClosed()
    {
        super.onClosed();
        this.context.onClosed();
    }

    /** Re-read sources from the context and redraw (used when sources load asynchronously). */
    public void reload()
    {
        this.sources = new ArrayList<>(context.sources());
        this.affected = context.affectedBlocks();
        this.list.refreshElementPanes();
    }

    private void updateRow(final int index, final Pane row)
    {
        final Block source = sources.get(index);
        final List<ItemStack> hosts = affected.getOrDefault(source, List.of());
        row.findPaneOfTypeByID("srcIcon", ItemIcon.class).setItem(iconFor(source));

        // Name carries an always-shown "(N)" badge when the swap touches more than one blueprint block type
        // (e.g. the bare block plus a Domum Ornamentum frame that contains it); only the name is ellipsized when
        // long, never the badge. The full affected list is in the row tooltip.
        final Text srcName = row.findPaneOfTypeByID("srcName", Text.class);
        final Component badge = hosts.size() > 1 ? Component.literal(" (" + hosts.size() + ")") : Component.empty();
        final boolean srcCut = setFittedText(srcName, source.getName(), badge);
        // Tooltip: the full source name when the cell had to truncate it, then the "affects" list (if any).
        attachRowTooltip(srcName, srcCut ? source.getName() : null, hosts);

        final Block chosen = context.current().get(source);
        final ItemIcon dstIcon = row.findPaneOfTypeByID("dstIcon", ItemIcon.class);
        final Text dstName = row.findPaneOfTypeByID("dstName", Text.class);
        if (chosen != null)
        {
            dstIcon.setItem(iconFor(chosen));
            final boolean dstCut = setFittedText(dstName, chosen.getName(), Component.empty());
            attachRowTooltip(dstName, dstCut ? chosen.getName() : null, List.of());
        }
        else
        {
            dstIcon.setItem(ItemStack.EMPTY);
            setFittedText(dstName, Component.literal("?"), Component.empty());
            attachRowTooltip(dstName, null, List.of());
        }

        row.findPaneOfTypeByID("change", ButtonImage.class).setHandler(b -> openPickerFor(source));
    }

    /**
     * Set a name cell's text on a single line so a long block name (e.g. "Stripped Spruce Wood") stays in its
     * row instead of wrapping and overlapping the row borders. Rendered at full size when it fits; otherwise the
     * scale shrinks gently but only to {@link #MIN_TEXT_SCALE} (kept readable) — and if it still doesn't fit at
     * that floor, the <b>name</b> is ellipsis-truncated. {@code suffix} (e.g. the always-shown {@code "(N)"}
     * badge) is never truncated — its width is reserved so it stays fully visible. The cells are
     * {@code wrap="false"}; scale/text reset every update because the list recycles row panes.
     *
     * @return true if the name had to be truncated (so the caller can offer the full name on hover).
     */
    private static boolean setFittedText(final Text cell, final Component name, final Component suffix)
    {
        final Font font = Minecraft.getInstance().font;
        final int avail = cell.getWidth() - 1;
        final Component full = name.copy().append(suffix);
        final int fullPx = font.width(full);
        if (avail <= 0 || fullPx <= avail)
        {
            cell.setText(full);
            cell.setTextScale(1.0);
            return false;
        }
        final double ratio = (double) avail / fullPx;
        if (ratio >= MIN_TEXT_SCALE)
        {
            cell.setText(full);
            cell.setTextScale(ratio);
            return false;
        }
        // Won't fit even at the scale floor: render at the floor, keep the suffix intact, and ellipsis-truncate
        // only the name to the budget that remains after reserving the suffix and ellipsis.
        cell.setTextScale(MIN_TEXT_SCALE);
        final int budget = (int) (avail / MIN_TEXT_SCALE) - font.width(suffix) - font.width(ELLIPSIS);
        final String head = budget > 0 ? font.plainSubstrByWidth(name.getString(), budget) : "";
        cell.setText(Component.literal(head + ELLIPSIS).append(suffix));
        return true;
    }

    /**
     * Mount (or, on a recycled row, replace) a name cell's hover tooltip: the {@code fullName} first when the
     * cell had to truncate it (so the complete name is one hover away), then — for the source cell — the
     * "affects N blocks" list, each affected block shown with its icon and material-aware name via
     * {@link IconTooltip}. Attached to the <b>name cell</b> only, not the whole row: the item icons carry their
     * own auto tooltips and the Change button its own hover, so a row-wide tooltip would fight them. Cleared
     * when there's nothing to show so a recycled row never carries a stale tooltip. Built here because the hover
     * pane needs the cell already attached to a window — true inside {@code updateRow}.
     */
    private static void attachRowTooltip(final Pane nameCell, @Nullable final Component fullName, final List<ItemStack> hosts)
    {
        if (fullName == null && hosts.isEmpty())
        {
            nameCell.setHoverPane(null);
            return;
        }
        if (hosts.isEmpty())
        {
            // Just the (truncated) full name — a plain text tooltip, no icon column.
            PaneBuilders.tooltipBuilder().append(fullName).hoverPane(nameCell).build();
            return;
        }

        // Affected list with icons: each row's label is indented past the icon gutter (IconTooltip draws the
        // icon there). Header rows (the optional full name + "Affects N:") carry no icon.
        final String indent = IconTooltip.indent();
        final List<MutableComponent> texts = new ArrayList<>();
        final List<ItemStack> icons = new ArrayList<>();
        if (fullName != null)
        {
            texts.add(Component.literal(indent).append(fullName));
            icons.add(null);
        }
        texts.add(Component.literal(indent)
                .append(Component.translatable("structurizereplacements.gui.replace.affects", hosts.size())));
        icons.add(null);
        for (final ItemStack host : hosts)
        {
            // getHoverName() resolves the host's real material-aware name (e.g. a Domum Ornamentum frame's
            // "Oak Panel"); the same stack renders the (textured) icon beside it.
            texts.add(Component.literal(indent).append(host.getHoverName()));
            icons.add(host);
        }
        final IconTooltip tooltip = new IconTooltip();
        tooltip.setRows(texts, icons);
        nameCell.setHoverPane(tooltip);
    }

    private void openPickerFor(final Block source)
    {
        final CandidateRule rule = BlockSubstitutions.candidateFor(source).orElse(null);
        if (rule == null)
        {
            return;
        }

        // The picker is item-based, but some candidate blocks have no item (e.g. TFC's potted plants are
        // registered with no BlockItem). iconFor falls back to the contained block's item; we keep a
        // display-item -> candidate-block map so the pick round-trips back to the right (possibly itemless)
        // block instead of Block.byItem returning AIR. Entries that can't produce any icon are skipped
        // (an air entry would be invisible and unpickable).
        final List<ItemStack> pool = new ArrayList<>();
        final Map<Item, Block> byDisplayItem = new HashMap<>();
        ForgeRegistries.BLOCKS.tags().getTag(rule.toTag()).forEach(block -> {
            // Skip air and Domum Ornamentum materialized blocks: DO registers some (e.g. its door) into
            // vanilla tags like minecraft:wooden_doors, but a bare DO block has no chosen material — it
            // renders as a cycling preview and can't be meaningfully picked as a substitution target.
            if (block == Blocks.AIR || BlockSubstitutions.isMaterializedSource(block))
            {
                return;
            }
            final ItemStack icon = iconFor(block);
            if (!icon.isEmpty() && byDisplayItem.putIfAbsent(icon.getItem(), block) == null)
            {
                pool.add(icon);
            }
        });

        final Block current = context.current().get(source);
        final Component title = Component.translatable("com.ldtteam.structurize.gui.scantool.replace")
                .append(Component.literal(" ")).append(source.getName());
        new WindowSelectRes(this, title, iconFor(current != null ? current : source), pool,
                (stack, count) -> choose(source, byDisplayItem.getOrDefault(stack.getItem(), Block.byItem(stack.getItem())))).open();
    }

    private void choose(final Block source, final Block target)
    {
        context.choose(source, target == null || target == Blocks.AIR ? null : target);
        // Re-read rows so the new pick shows when we return from the candidate picker.
        this.list.refreshElementPanes();
    }

    /**
     * The icon to display for a candidate/source block — delegates to {@link BlockSubstitutions#iconStack}
     * (item-less blocks like TFC potted plants fall back to their contained item; the same helper names the
     * affected-host tooltip entries, so icons and tooltip stay consistent).
     */
    private static ItemStack iconFor(final Block block)
    {
        return BlockSubstitutions.iconStack(block);
    }
}
