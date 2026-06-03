package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.Pane;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

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
        this.list = findPaneOfTypeByID("rows", ScrollingList.class);
        this.list.setDataProvider(() -> sources.size(), this::updateRow);
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
        row.findPaneOfTypeByID("srcIcon", ItemIcon.class).setItem(new ItemStack(source));
        row.findPaneOfTypeByID("srcName", Text.class).setText(source.getName());

        final Block chosen = context.current().get(source);
        final ItemIcon dstIcon = row.findPaneOfTypeByID("dstIcon", ItemIcon.class);
        final Text dstName = row.findPaneOfTypeByID("dstName", Text.class);
        if (chosen != null)
        {
            dstIcon.setItem(new ItemStack(chosen));
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

        final List<ItemStack> pool = new ArrayList<>();
        ForgeRegistries.BLOCKS.tags().getTag(rule.toTag()).forEach(block -> {
            if (block != Blocks.AIR)
            {
                pool.add(new ItemStack(block));
            }
        });

        final Block current = context.current().get(source);
        final Component title = Component.translatable("com.ldtteam.structurize.gui.scantool.replace")
                .append(Component.literal(" ")).append(source.getName());
        new WindowSelectRes(this, title, new ItemStack(current != null ? current : source), pool,
                (stack, count) -> choose(source, stack)).open();
    }

    private void choose(final Block source, final ItemStack stack)
    {
        final Block target = Block.byItem(stack.getItem());
        context.choose(source, target == Blocks.AIR ? null : target);
        // Re-read rows so the new pick shows when we return from the candidate picker.
        this.list.refreshElementPanes();
    }
}
