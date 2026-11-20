package com.worive.colorpicker.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.worive.colorpicker.client.color.BlockColors;

import codechicken.nei.api.INEIGuiHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;

@Optional.Interface(iface = "codechicken.nei.api.INEIGuiHandler", modid = "NotEnoughItems")
abstract class ToolScreen extends GuiContainer implements INEIGuiHandler {

    static final int PANEL_INSET = 10, SLOT = 18, FILTER_WIDTH = 92;
    static final int SLOT_GREY = 0xff8b8b8b;
    static final int LABEL = 0xff404040, MUTED = 0xff505050, BORDER = 0xffa0a0a0;
    static final List<String> PATTERN_TOOLTIP = Arrays.asList(
        "Any shows all texture patterns",
        "Smooth shows textures like dirt and smooth sandstone",
        "Patterned shows designs like bricks and repeating tiles",
        "Outlined shows a visible border around each block");
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    private static final int SWITCH_Y = 5, SWITCH_W = 95, SWITCH_H = 15;
    private static final int FILTER_FIRST_ROW = 13, FILTER_ROW_H = 12, FILTER_BOX = 9;
    private static final int NEI_MIN_SIDEBAR_WIDTH = 81;
    private static final Filter[] FILTERS = {
        new Filter(BlockColors.FULL, "Full cubes", "Shows full 1x1x1 blocks", "Also allows partial blocks"),
        new Filter(
            BlockColors.SOLID,
            "Collidable",
            "Shows blocks with collision",
            "Also allows blocks without collision"),
        new Filter(
            BlockColors.LOW_CONTRAST,
            "Low contrast",
            "Hides textures with strongly contrasting patches",
            "Allows textures with strongly contrasting patches"),
        new Filter(
            BlockColors.SAME_TEXTURE,
            "One texture",
            "Shows blocks with the same texture on all faces",
            "Also allows blocks with different face textures"),
        new Filter(
            BlockColors.NO_TILE_ENTITIES,
            "No tile entities",
            "Hides blocks with tile entities",
            "Allows blocks with tile entities"),
        new Filter(
            BlockColors.OPAQUE,
            "Opaque",
            "Shows blocks with opaque textures",
            "Also allows transparent textures"),
        new Filter(
            BlockColors.NO_FALLING,
            "No falling",
            "Hides blocks that use Minecraft's standard falling behavior",
            "Allows blocks with standard falling behavior") };
    static final int FILTERS_HEIGHT = FILTER_FIRST_ROW + FILTERS.length * FILTER_ROW_H;

    final GuiScreen parent;
    final int openKey;
    final int panelHeight;
    private final int panelWidth, switchX;
    private final Consumer<Boolean> openTool;
    private final boolean gradient;
    private final RenderItem renderer = new RenderItem();
    private float scale;
    private boolean neiDispatchedDrop;

    ToolScreen(GuiScreen parent, int openKey, Consumer<Boolean> openTool, boolean gradient, int panelWidth,
        int panelHeight) {
        // NEI uses the GuiContainer for panel bounds, while the tool manages its own slots.
        super(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        });
        this.parent = parent;
        this.openKey = openKey;
        this.openTool = openTool;
        this.gradient = gradient;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.switchX = panelWidth - PANEL_INSET - SWITCH_W;
    }

    @Override
    public void initGui() {
        boolean hasNei = Loader.isModLoaded("NotEnoughItems");
        int spaceForOverlay = hasNei ? 44 : 10;
        scale = Math.min(1, Math.min((width - 8f) / panelWidth, (height - spaceForOverlay) / (float) panelHeight));
        if (gradient && hasNei) {
            float scaleWithSidebars = (width - 2f * NEI_MIN_SIDEBAR_WIDTH) / panelWidth;
            if (scaleWithSidebars >= 2f / 3) {
                scale = Math.min(scale, scaleWithSidebars);
            }
        }
        xSize = Math.round(panelWidth * scale);
        ySize = Math.round(panelHeight * scale);
        guiLeft = (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
    }

    @Override
    public void onGuiClosed() {
        // A client-side tool must not close or drop the cursor of the player's real container.
    }

    final void beginPanel(String title, int mouseX, int mouseY) {
        GL11.glPushMatrix();
        GL11.glTranslatef(guiLeft, guiTop, 0);
        GL11.glScalef(scale, scale, scale);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawInventoryPanel();
        fontRendererObj.drawString(title, PANEL_INSET, 9, LABEL);
        if (creative()) {
            drawAction(
                gradient ? "ColorPicker" : "Gradient Helper",
                switchX,
                SWITCH_Y,
                SWITCH_W,
                SWITCH_H,
                mouseX,
                mouseY,
                true);
        }
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    private void drawInventoryPanel() {
        mc.getTextureManager()
            .bindTexture(INVENTORY);
        GL11.glColor4f(1, 1, 1, 1);
        int edge = 7, right = panelWidth - edge, bottom = panelHeight - edge;
        int textureRight = 176 - edge, textureBottom = 222 - edge;
        drawTexturedModalRect(0, 0, 0, 0, edge, edge);
        drawTexturedModalRect(right, 0, textureRight, 0, edge, edge);
        drawTexturedModalRect(0, bottom, 0, textureBottom, edge, edge);
        drawTexturedModalRect(right, bottom, textureRight, textureBottom, edge, edge);
        func_152125_a(edge, 0, edge, 0, 1, edge, right - edge, edge, 256, 256);
        func_152125_a(edge, bottom, edge, textureBottom, 1, edge, right - edge, edge, 256, 256);
        func_152125_a(0, edge, 0, 8, edge, 1, edge, bottom - edge, 256, 256);
        func_152125_a(right, edge, textureRight, 8, edge, 1, edge, bottom - edge, 256, 256);
        func_152125_a(edge, edge, 8, 8, 1, 1, right - edge, bottom - edge, 256, 256);
    }

    final void drawInventorySlot(int x, int y) {
        mc.getTextureManager()
            .bindTexture(INVENTORY);
        GL11.glColor4f(1, 1, 1, 1);
        drawTexturedModalRect(x, y, 7, 17, SLOT, SLOT);
    }

    final void endPanel() {
        GL11.glPopMatrix();
    }

    @Override
    protected void drawHoveringText(List<String> lines, int x, int y, FontRenderer font) {
        int limit = Math.min(172, width - 10);
        List<String> wrapped = new ArrayList<>();
        int textWidth = 0;
        for (String line : lines) {
            if (font.getStringWidth(line) <= limit) {
                wrapped.add(line);
            } else {
                wrapped.addAll(font.listFormattedStringToWidth(line, limit));
            }
        }
        for (String line : wrapped) {
            textWidth = Math.max(textWidth, font.getStringWidth(line));
        }
        int left = x + 12 + textWidth <= width - 5 ? x + 12 : x - textWidth - 16;
        left = Math.max(5, Math.min(width - textWidth - 5, left));
        super.drawHoveringText(wrapped, left - 12, Math.max(16, y), font);
    }

    @Override
    protected final void mouseClicked(int mouseX, int mouseY, int button) {
        neiDispatchedDrop = false;
        // NEI can dispatch a ghost drop inside GuiContainer.mouseClicked.
        super.mouseClicked(mouseX, mouseY, button);
        if (!neiDispatchedDrop && mc.currentScreen == this) {
            int x = localX(mouseX), y = localY(mouseY);
            if (button == 0 && creative() && inside(x, y, switchX, SWITCH_Y, SWITCH_W, SWITCH_H)) {
                openTool.accept(!gradient);
            } else {
                clickPanel(x, y, button);
            }
        }
    }

    abstract void clickPanel(int x, int y, int button);

    abstract ItemStack hoveredStack(int x, int y);

    @Override
    public final boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack stack, int button) {
        if (gui != this) {
            return false;
        }
        neiDispatchedDrop = true;
        if (!dropGhostStack(localX(mouseX), localY(mouseY), stack)) {
            return false;
        }
        // NEI clears the ghost cursor when the handler consumes the stack.
        stack.stackSize = 0;
        return true;
    }

    abstract boolean dropGhostStack(int x, int y, ItemStack stack);

    @Override
    protected final void handleMouseClick(Slot slot, int slotId, int clickedButton, int clickType) {
        // Vanilla click packets target real inventory slots. Tool slots are handled by clickPanel.
    }

    final void drawStack(ItemStack stack, int x, int y, boolean count) {
        ItemPreviews.draw(mc, renderer, stack, x, y, count);
    }

    final void drawCursor(ItemStack stack, int x, int y) {
        renderer.zLevel = 200;
        drawStack(stack, x - 8, y - 8, true);
        renderer.zLevel = 0;
    }

    final void drawFilters(int x, int y, int flags, int pointerX, int pointerY) {
        fontRendererObj.drawString("Filters", x, y, LABEL);
        int hoveredFilter = filterAt(pointerX, pointerY, x, y);
        for (int i = 0; i < FILTERS.length; i++) {
            Filter filter = FILTERS[i];
            boolean active = (flags & filter.bit) != 0;
            boolean hovered = hoveredFilter == filter.bit;
            int by = y + FILTER_FIRST_ROW + i * FILTER_ROW_H;
            int markY = by + 1;
            int fill = hovered ? 0xffeeeeee : active ? 0xffd8d8d8 : SLOT_GREY;
            int ink = hovered ? 0xff202020 : LABEL;
            frame(x, markY, FILTER_BOX, FILTER_BOX, fill, true);
            if (active) {
                drawRect(x + 2, markY + 4, x + 4, markY + 6, ink);
                drawRect(x + 4, markY + 5, x + 5, markY + 7, ink);
                drawRect(x + 5, markY + 4, x + 6, markY + 6, ink);
                drawRect(x + 6, markY + 3, x + 7, markY + 5, ink);
            }
            fontRendererObj.drawString(filter.name, x + FILTER_BOX + 3, by + 2, ink);
        }
    }

    final void drawSteppedSlider(SteppedSlider slider, String label, String value, int step, boolean focused,
        boolean highlighted) {
        fontRendererObj.drawString(label, slider.x, slider.y - 11, LABEL);
        fontRendererObj
            .drawString(value, slider.x + slider.width - fontRendererObj.getStringWidth(value), slider.y - 11, LABEL);
        if (focused) {
            drawButtonFocus(slider.x, slider.y, slider.width, SteppedSlider.HEIGHT);
        }
        frame(slider.x, slider.y, slider.width, SteppedSlider.HEIGHT, SLOT_GREY, true);
        for (int i = 0; i <= slider.lastStep; i++) {
            int tick = slider.stepX(i);
            drawRect(tick, slider.y + 3, tick + 1, slider.y + SteppedSlider.HEIGHT - 3, LABEL);
        }
        drawButtonSkin(slider.stepX(step) - 3, slider.y, 7, SteppedSlider.HEIGHT, highlighted ? 2 : 1);
    }

    static void drawButtonFocus(int x, int y, int width, int height) {
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, LABEL);
    }

    static final class SteppedSlider {

        private static final int HEIGHT = 11, INSET = 4;
        private final int x, y, width, range, lastStep;

        SteppedSlider(int x, int y, int width, int steps) {
            this.x = x;
            this.y = y;
            this.width = width;
            range = width - 2 * INSET - 1;
            lastStep = steps - 1;
        }

        boolean contains(int pointerX, int pointerY) {
            return inside(pointerX, pointerY, x, y, width, HEIGHT);
        }

        int mouseStep(int pointerX) {
            return clamp(Math.round((pointerX - x - INSET) * lastStep / (float) range));
        }

        int keyStep(int step, int key) {
            switch (key) {
                case Keyboard.KEY_LEFT:
                    return clamp(step - 1);
                case Keyboard.KEY_RIGHT:
                    return clamp(step + 1);
                case Keyboard.KEY_HOME:
                    return 0;
                case Keyboard.KEY_END:
                    return lastStep;
                default:
                    return -1;
            }
        }

        private int clamp(int step) {
            return Math.max(0, Math.min(lastStep, step));
        }

        private int stepX(int step) {
            return x + INSET + Math.round(step * range / (float) lastStep);
        }
    }

    final void drawAction(String label, int x, int y, int w, int h, int mouseX, int mouseY, boolean enabled) {
        boolean highlighted = enabled && inside(mouseX, mouseY, x, y, w, h);
        drawButtonSkin(x, y, w, h, enabled ? (highlighted ? 2 : 1) : 0);
        drawCenteredString(
            fontRendererObj,
            label,
            x + w / 2,
            y + (h - 8) / 2,
            enabled ? (highlighted ? 0xffffa0 : 0xe0e0e0) : 0xa0a0a0);
    }

    final void drawButtonSkin(int x, int y, int w, int h, int state) {
        mc.getTextureManager()
            .bindTexture(WIDGETS);
        GL11.glColor4f(1, 1, 1, 1);
        int v = 46 + state * 20;
        int left = w / 2, right = w - left, top = h / 2, bottom = h - top;
        drawTexturedModalRect(x, y, 0, v, left, top);
        drawTexturedModalRect(x + left, y, 200 - right, v, right, top);
        drawTexturedModalRect(x, y + top, 0, v + 20 - bottom, left, bottom);
        drawTexturedModalRect(x + left, y + top, 200 - right, v + 20 - bottom, right, bottom);
    }

    static void outline(int x, int y, int w, int h, int fill, int edge) {
        drawRect(x, y, x + w, y + h, edge);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    static int filterAt(int x, int y, int left, int top) {
        int firstRow = top + FILTER_FIRST_ROW;
        if (inside(x, y, left, firstRow, FILTER_WIDTH, FILTERS.length * FILTER_ROW_H)) {
            return FILTERS[(y - firstRow) / FILTER_ROW_H].bit;
        }
        return 0;
    }

    static List<String> filterTooltip(int bit, int flags) {
        for (Filter filter : FILTERS) {
            if (filter.bit == bit) {
                boolean active = (flags & bit) != 0;
                return Arrays.asList(
                    filter.name,
                    active ? EnumChatFormatting.GREEN + filter.enabled : EnumChatFormatting.RED + filter.disabled);
            }
        }
        return Collections.emptyList();
    }

    private static final class Filter {

        final int bit;
        final String name, enabled, disabled;

        Filter(int bit, String name, String enabled, String disabled) {
            this.bit = bit;
            this.name = name;
            this.enabled = enabled;
            this.disabled = disabled;
        }
    }

    final void drawTrash(int x, int y, boolean enabled) {
        drawInventorySlot(x, y);
        int ink = enabled ? LABEL : 0xff767676;
        for (int i = 0; i < 12; i++) {
            drawRect(x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, ink);
            drawRect(x + 14 - i, y + 3 + i, x + 15 - i, y + 4 + i, ink);
        }
    }

    static void highlight(int x, int y, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
            drawRect(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x50ffffff);
        }
    }

    final boolean creative() {
        return mc.playerController != null && mc.playerController.isInCreativeMode();
    }

    final void putInventory(int slot, ItemStack stack) {
        mc.thePlayer.inventory.setInventorySlotContents(slot, stack);
        mc.playerController.sendSlotPacket(stack, slot < 9 ? 36 + slot : slot);
    }

    final int localX(int x) {
        return (int) Math.floor((x - guiLeft) / scale);
    }

    final int localY(int y) {
        return (int) Math.floor((y - guiTop) / scale);
    }

    static boolean inside(int x, int y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    static void frame(int x, int y, int w, int h, int fill, boolean inset) {
        int light = 0xffffffff, dark = 0xff424242;
        drawRect(x, y, x + w, y + h, inset ? dark : light);
        drawRect(x + 1, y + 1, x + w, y + h, inset ? light : dark);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
}
