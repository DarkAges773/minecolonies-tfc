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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.registries.ForgeRegistries;

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

    private final ReplacementChoiceContext context;
    private final BOWindow parent;
    private final ScrollingList list;
    private List<Block> sources = List.of();

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
        this.list.refreshElementPanes();
    }

    private void updateRow(final int index, final Pane row)
    {
        final Block source = sources.get(index);
        row.findPaneOfTypeByID("srcIcon", ItemIcon.class).setItem(iconFor(source));
        row.findPaneOfTypeByID("srcName", Text.class).setText(source.getName());

        final Block chosen = context.current().get(source);
        final ItemIcon dstIcon = row.findPaneOfTypeByID("dstIcon", ItemIcon.class);
        final Text dstName = row.findPaneOfTypeByID("dstName", Text.class);
        if (chosen != null)
        {
            dstIcon.setItem(iconFor(chosen));
            dstName.setText(chosen.getName());
        }
        else
        {
            dstIcon.setItem(ItemStack.EMPTY);
            dstName.setText(Component.literal("?"));
        }

        row.findPaneOfTypeByID("change", ButtonImage.class).setHandler(b -> openPickerFor(source));
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
     * The icon to display for a candidate/source block. Normally {@code new ItemStack(block)}, but some blocks
     * have no item ({@code block.asItem() == AIR}) — notably TFC's potted plants, registered with no BlockItem.
     * For a {@link FlowerPotBlock} we fall back to the contained plant's item (which does exist and is distinct
     * per pot, so it also makes the pick round-trip unambiguous). Returns {@link ItemStack#EMPTY} if nothing
     * can represent the block.
     */
    private static ItemStack iconFor(final Block block)
    {
        final ItemStack direct = new ItemStack(block);
        if (!direct.isEmpty())
        {
            return direct;
        }
        if (block instanceof FlowerPotBlock pot)
        {
            final ItemStack content = new ItemStack(pot.getContent());
            if (!content.isEmpty())
            {
                return content;
            }
        }
        return ItemStack.EMPTY;
    }
}
