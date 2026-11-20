package com.worive;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

final class NeiHoverHandler implements IContainerObjectHandler, IContainerTooltipHandler {

    static void register() {
        NeiHoverHandler handler = new NeiHoverHandler();
        GuiContainerManager.addObjectHandler(handler);
        GuiContainerManager.addTooltipHandler(handler);
    }

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        if (gui instanceof ToolScreen) {
            ToolScreen tool = (ToolScreen) gui;
            return tool.hoveredStack(tool.localX(mouseX), tool.localY(mouseY));
        }
        return null;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int mouseX, int mouseY,
        List<String> tooltip) {
        if (stack != null && stack == getStackUnderMouse(gui, mouseX, mouseY)) {
            // The tools draw their own tooltips, and Item Zoom still needs shouldShowTooltip to return true.
            tooltip.clear();
        }
        return tooltip;
    }

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        return true;
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        return false;
    }

    @Override
    public void guiTick(GuiContainer gui) {}

    @Override
    public void refresh(GuiContainer gui) {}

    @Override
    public void load(GuiContainer gui) {}
}
