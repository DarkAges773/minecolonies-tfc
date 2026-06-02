package com.structurizereplacements.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.client.gui.AbstractWindowSkeleton;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.util.BlockInfo;
import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.ClientPlacementChoices;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.CandidateRule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-placement replacement picker window: one row per distinct source block in the loaded
 * blueprint that matches a candidate rule. Each row shows the source → current choice and a button
 * that opens Structurize's {@link WindowSelectRes} restricted to the candidate {@code to_tag} pool;
 * the pick is stored in {@link ClientPlacementChoices} (which syncs to the server). Passing {@code this}
 * as the picker's origin makes it reopen this window after a pick, refreshing the rows.
 */
public class WindowReplacements extends AbstractWindowSkeleton
{
    private static final String RESOURCE = "gui/windowreplacements.xml";

    private final ScrollingList list;
    private List<Block> sources = List.of();

    public WindowReplacements()
    {
        super(new ResourceLocation(StructurizeReplacements.MODID, RESOURCE));
        registerButton("done", this::close);
        this.list = findPaneOfTypeByID("rows", ScrollingList.class);
        this.list.setDataProvider(() -> sources.size(), this::updateRow);
    }

    @Override
    public void onOpened()
    {
        this.sources = gatherSources();
        super.onOpened();
        this.list.refreshElementPanes();
    }

    private void updateRow(final int index, final Pane row)
    {
        final Block source = sources.get(index);
        row.findPaneOfTypeByID("srcIcon", ItemIcon.class).setItem(new ItemStack(source));
        row.findPaneOfTypeByID("srcName", Text.class).setText(source.getName());

        final Block chosen = ClientPlacementChoices.current().get(source);
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

        final Block current = ClientPlacementChoices.current().get(source);
        final Component title = Component.translatable("com.ldtteam.structurize.gui.scantool.replace")
                .append(Component.literal(" ")).append(source.getName());
        new WindowSelectRes(this, title, new ItemStack(current != null ? current : source), pool,
                (stack, count) -> choose(source, stack)).open();
    }

    private void choose(final Block source, final ItemStack stack)
    {
        final Block target = Block.byItem(stack.getItem());
        final Map<Block, Block> map = new HashMap<>(ClientPlacementChoices.current());
        if (target == Blocks.AIR)
        {
            map.remove(source);
        }
        else
        {
            map.put(source, target);
        }
        ClientPlacementChoices.set(map);
        // Force the blueprint preview to re-bake so the hologram reflects the new choice immediately.
        BlueprintHandler.getInstance().clearCache();
    }

    private static List<Block> gatherSources()
    {
        final Blueprint blueprint = currentBlueprint();
        if (blueprint == null)
        {
            return List.of();
        }
        final Set<Block> distinct = new LinkedHashSet<>();
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            final BlockState state = info.getState();
            if (state != null && BlockSubstitutions.candidateFor(state.getBlock()).isPresent())
            {
                distinct.add(state.getBlock());
            }
        }
        return new ArrayList<>(distinct);
    }

    private static Blueprint currentBlueprint()
    {
        for (final BlueprintPreviewData data : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = data.getBlueprint();
            if (blueprint != null)
            {
                return blueprint;
            }
        }
        return null;
    }
}
