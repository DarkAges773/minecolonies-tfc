package com.mctfc.client.gui;

import com.mctfc.cook.DishType;
import com.mctfc.inventory.ComposeDishMenu;
import com.mctfc.network.ComposeDishNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The Chef dish-teaching screen — a native {@link AbstractContainerScreen} over {@link ComposeDishMenu}, mirroring
 * MineColonies' {@code WindowCrafting}: the player drags TFC foods into the ghost ingredient slots (with their
 * inventory right there) and clicks <b>Teach</b>. Both salad and soup take 5 ingredients + a bowl, so both reuse TFC's
 * {@code salad.png} directly (referenced from the TFC jar — a hard dependency — so there is nothing to copy or ship).
 */
public class ComposeDishScreen extends AbstractContainerScreen<ComposeDishMenu>
{
    private static final ResourceLocation TEXTURE = new ResourceLocation("tfc", "textures/gui/salad.png");

    public ComposeDishScreen(final ComposeDishMenu menu, final Inventory inv, final Component title)
    {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init()
    {
        super.init();

        // Teach + mode-switch buttons, just below the panel (like WindowCrafting's Done button).
        addRenderableWidget(Button.builder(Component.translatable("mctfc.gui.compose.teach"), b -> ComposeDishNetwork.sendTeach())
            .pos(leftPos + 2, topPos + 170).size(84, 18).build());

        final DishType other = menu.getDishType().other();
        addRenderableWidget(Button.builder(other.switchToLabel(),
                b -> ComposeDishNetwork.sendOpen(menu.getPos(), menu.getModuleId(), other))
            .pos(leftPos + 90, topPos + 170).size(84, 18).build());
    }

    @Override
    protected void renderBg(@NotNull final GuiGraphics graphics, final float partialTicks, final int mouseX, final int mouseY)
    {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(@NotNull final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks)
    {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
