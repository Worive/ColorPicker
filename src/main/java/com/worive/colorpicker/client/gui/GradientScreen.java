package com.worive.colorpicker.client.gui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.worive.colorpicker.client.color.BlockColors;
import com.worive.colorpicker.client.color.Gradient;
import com.worive.colorpicker.client.color.TexturePattern;

public final class GradientScreen extends ToolScreen {

    private static final int WIDTH = 306, HEIGHT = 186;
    private static final int ROW_X = PANEL_INSET, TRASH_X = ROW_X + Gradient.SLOTS * SLOT + 6;
    private static final int INPUT_Y = 45, OUTPUT_Y = 81, HOTBAR_Y = HEIGHT - PANEL_INSET - SLOT;
    private static final int FILTER_X = WIDTH - PANEL_INSET - FILTER_WIDTH, FILTER_Y = 33;
    private static final int PATTERN_WIDTH = 100;
    private static final SteppedSlider PATTERN_SLIDER = new SteppedSlider(
        WIDTH - PANEL_INSET - PATTERN_WIDTH,
        FILTER_Y + FILTERS_HEIGHT + 13,
        PATTERN_WIDTH,
        PickerSettings.PATTERN_STEP_COUNT);
    private static final int APPLY_W = 95, APPLY_X = WIDTH - PANEL_INSET - APPLY_W;
    private static final int REFRESH_Y = OUTPUT_Y + SLOT + 2, REFRESH_H = 12;

    private final BlockColors colors;
    private final PickerSettings settings;
    private final Gradient gradient;
    private ItemStack held;
    private int heldFrom = -1;
    private int startX, startY;
    private boolean pickedUpThisClick;
    private boolean patternDragging, patternFocused;
    private int pointerX, pointerY;
    private int noticeTicks;

    public GradientScreen(GuiScreen parent, BlockColors colors, PickerSettings settings, Gradient gradient, int openKey,
        Consumer<Boolean> openTool) {
        super(parent, openKey, openTool, true, WIDTH, HEIGHT);
        this.colors = colors;
        this.settings = settings;
        this.gradient = gradient;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        if (!canOpenGradient()) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (held == null) {
            drawDetails(mouseX, mouseY, pointerX, pointerY, refreshAt(pointerX, pointerY));
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        gradient.update(colors, settings.filters());
        int x = localX(mouseX), y = localY(mouseY);
        pointerX = x;
        pointerY = y;
        beginPanel("Gradient Helper", x, y);
        boolean hasOutput = hasOutput();
        fontRendererObj.drawString("Inputs", ROW_X, INPUT_Y - 12, LABEL);
        if (noticeTicks > 0) {
            fontRendererObj.drawString("Choose a block", 52, INPUT_Y - 12, LABEL);
        } else if (!hasOutput) {
            fontRendererObj.drawString("Drag blocks here", 52, INPUT_Y - 12, MUTED);
        }
        fontRendererObj.drawString(colors.isIndexed() ? "Outputs" : "Indexing blocks...", ROW_X, OUTPUT_Y - 12, LABEL);
        drawRect(ROW_X, HOTBAR_Y - 17, TRASH_X + SLOT, HOTBAR_Y - 16, BORDER);
        fontRendererObj.drawString("Hotbar", ROW_X, HOTBAR_Y - 12, LABEL);

        int refresh = refreshAt(x, y);
        for (int i = 0; i < Gradient.SLOTS; i++) {
            int sx = ROW_X + i * SLOT;
            drawSlot(gradient.input(i), sx, INPUT_Y, x, y, false);
            drawSlot(gradient.output(i), sx, OUTPUT_Y, x, y, false);
            drawSlot(heldFrom == i ? null : hotbar(i), sx, HOTBAR_Y, x, y, true);
            if (gradient.rank(i) >= 0) {
                drawRefresh(sx, i == refresh);
            }
        }
        drawTrash(TRASH_X, HOTBAR_Y, creative());
        highlight(TRASH_X, HOTBAR_Y, x, y);

        drawFilters(FILTER_X, FILTER_Y, settings.filters(), x, y);
        TexturePattern pattern = settings.pattern();
        drawSteppedSlider(
            PATTERN_SLIDER,
            "Pattern",
            pattern.label,
            pattern.ordinal(),
            patternFocused,
            patternDragging || PATTERN_SLIDER.contains(x, y));
        drawAction("Apply to hotbar", APPLY_X, HOTBAR_Y, APPLY_W, SLOT, x, y, creative() && hasOutput);
        drawCursor(held, x, y);
        endPanel();
    }

    private void drawSlot(ItemStack stack, int sx, int sy, int x, int y, boolean count) {
        drawInventorySlot(sx, sy);
        drawStack(stack, sx + 1, sy + 1, count);
        highlight(sx, sy, x, y);
    }

    private void drawRefresh(int x, boolean hovered) {
        int y = REFRESH_Y;
        int ink = hovered ? 0xffffffA0 : 0xffeeeeee;
        drawButtonSkin(x, y, SLOT, REFRESH_H, hovered ? 2 : 1);
        drawRect(x + 5, y + 3, x + 11, y + 4, ink);
        drawRect(x + 4, y + 4, x + 5, y + 8, ink);
        drawRect(x + 5, y + 8, x + 11, y + 9, ink);
        drawRect(x + 11, y + 4, x + 12, y + 6, ink);
        drawRect(x + 9, y + 5, x + 13, y + 6, ink);
    }

    @Override
    ItemStack hoveredStack(int x, int y) {
        if (held != null) {
            return null;
        }
        int input = slotAt(x, y, INPUT_Y);
        if (input >= 0) {
            return gradient.input(input);
        }
        int output = slotAt(x, y, OUTPUT_Y);
        if (output >= 0) {
            return gradient.output(output);
        }
        int hotbar = slotAt(x, y, HOTBAR_Y);
        return hotbar >= 0 ? hotbar(hotbar) : null;
    }

    private void drawDetails(int mouseX, int mouseY, int x, int y, int refresh) {
        int filter = filterAt(x, y, FILTER_X, FILTER_Y);
        int input = slotAt(x, y, INPUT_Y), output = slotAt(x, y, OUTPUT_Y);
        int hotbar = slotAt(x, y, HOTBAR_Y);
        List<String> lines = null;
        if (refresh >= 0) {
            lines = Arrays.asList(
                "Match " + (gradient.rank(refresh) + 1),
                "Left-click for the next match",
                "Right-click for the previous match");
        } else if (filter != 0) {
            lines = filterTooltip(filter, settings.filters());
        } else if (!patternDragging && PATTERN_SLIDER.contains(x, y)) {
            lines = PATTERN_TOOLTIP;
        } else if (inside(x, y, APPLY_X, HOTBAR_Y, APPLY_W, SLOT)) {
            if (!creative()) {
                lines = Arrays.asList("Available in Creative or Creative+");
            } else if (colors.isIndexed()) {
                lines = Arrays.asList(hasOutput() ? "Clears hotbar slots with no output" : "Add an input block first");
            }
        } else if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
            lines = creative() ? Arrays.asList("Destroy Item", "Shift-click to clear the hotbar")
                : Arrays.asList("Available in Creative mode");
        } else if (input >= 0) {
            ItemStack stack = gradient.input(input);
            lines = stack == null ? Arrays.asList("Input", "Drop a block here")
                : Arrays.asList(stack.getDisplayName(), "Right-click to remove this input");
        } else if (output >= 0 && gradient.output(output) != null) {
            renderToolTip(gradient.output(output), mouseX, mouseY);
        } else if (hotbar >= 0 && hotbar(hotbar) != null) {
            renderToolTip(hotbar(hotbar), mouseX, mouseY);
        }
        if (lines != null) {
            drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    void clickPanel(int x, int y, int button) {
        if (!canOpenGradient()) {
            return;
        }
        patternFocused = false;
        gradient.update(colors, settings.filters());
        int refresh = refreshAt(x, y);
        if (refresh >= 0 && button <= 1) {
            gradient.cycle(refresh, button == 0 ? 1 : -1);
            return;
        }
        int input = slotAt(x, y, INPUT_Y);
        if (input >= 0) {
            ItemStack stack = gradient.input(input);
            if (button == 1 || isShiftKeyDown()) {
                gradient.setInput(input, null, colors);
            } else if (held != null) {
                placeInput(input);
            } else if (stack != null) {
                startHolding(stack, -1, x, y, button == 2);
                if (button == 0) {
                    gradient.setInput(input, null, colors);
                }
            }
            return;
        }
        int output = slotAt(x, y, OUTPUT_Y);
        if (output >= 0) {
            ItemStack stack = gradient.output(output);
            if (stack != null && button != 1) {
                startHolding(stack, -1, x, y, button == 0 && isShiftKeyDown());
            }
            return;
        }
        int hotbar = slotAt(x, y, HOTBAR_Y);
        if (hotbar >= 0 && button == 0) {
            if (creative() && isShiftKeyDown()) {
                putInventory(hotbar, null);
                clearHeld();
            } else if (held != null) {
                placeHotbar(hotbar);
            } else if (hotbar(hotbar) != null) {
                startHolding(hotbar(hotbar), creative() ? hotbar : -1, x, y, false);
            }
            return;
        }
        if (button != 0) {
            return;
        }
        int filter = filterAt(x, y, FILTER_X, FILTER_Y);
        if (filter != 0) {
            settings.toggleFilter(filter);
        } else if (PATTERN_SLIDER.contains(x, y)) {
            patternDragging = true;
            patternFocused = true;
            settings.setPatternStep(PATTERN_SLIDER.mouseStep(x));
        } else if (inside(x, y, APPLY_X, HOTBAR_Y, APPLY_W, SLOT)) {
            if (creative() && hasOutput()) {
                for (int i = 0; i < Gradient.SLOTS; i++) {
                    ItemStack result = gradient.output(i);
                    putInventory(i, result == null ? null : result.copy());
                }
                clearHeld();
            }
        } else if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
            if (creative() && isShiftKeyDown()) {
                for (int i = 0; i < Gradient.SLOTS; i++) {
                    putInventory(i, null);
                }
            }
            discardHeld();
        } else {
            clearHeld();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        super.mouseClickMove(mouseX, mouseY, button, elapsed);
        if (button == 0 && patternDragging && canOpenGradient()) {
            settings.setPatternStep(PATTERN_SLIDER.mouseStep(localX(mouseX)));
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        if (button == 0) {
            patternDragging = false;
        }
        if (button != 0 || !canOpenGradient() || !pickedUpThisClick || held == null) {
            return;
        }
        pickedUpThisClick = false;
        int x = localX(mouseX), y = localY(mouseY);
        if (Math.abs(x - startX) < 3 && Math.abs(y - startY) < 3) {
            return;
        }
        int input = slotAt(x, y, INPUT_Y);
        int hotbar = slotAt(x, y, HOTBAR_Y);
        if (input >= 0) {
            placeInput(input);
        } else if (hotbar >= 0) {
            placeHotbar(hotbar);
        } else if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
            discardHeld();
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == Keyboard.KEY_ESCAPE || key == openKey || key == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (!canOpenGradient()) {
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            patternFocused = true;
            return;
        }
        if (patternFocused) {
            int step = PATTERN_SLIDER.keyStep(
                settings.pattern()
                    .ordinal(),
                key);
            if (step >= 0) {
                settings.setPatternStep(step);
                return;
            }
        }
        if (!creative()) {
            super.keyTyped(typed, key);
            return;
        }
        for (int i = 0; i < Gradient.SLOTS; i++) {
            if (key == mc.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                int input = slotAt(pointerX, pointerY, INPUT_Y);
                int output = slotAt(pointerX, pointerY, OUTPUT_Y);
                ItemStack stack = input >= 0 ? gradient.input(input) : output >= 0 ? gradient.output(output) : null;
                if (held != null) {
                    placeHotbar(i);
                } else if (stack != null) {
                    ItemStack copy = stack.copy();
                    copy.stackSize = copy.getMaxStackSize();
                    putInventory(i, copy);
                }
                return;
            }
        }
        super.keyTyped(typed, key);
    }

    private void startHolding(ItemStack stack, int from, int x, int y, boolean fullStack) {
        held = stack.copy();
        if (fullStack) {
            held.stackSize = held.getMaxStackSize();
        }
        heldFrom = from;
        startX = x;
        startY = y;
        pickedUpThisClick = true;
    }

    @Override
    boolean dropGhostStack(int x, int y, ItemStack stack) {
        int slot = slotAt(x, y, INPUT_Y);
        if (slot < 0 || !canOpenGradient()) {
            return false;
        }
        if (!gradient.setInput(slot, stack, colors)) {
            noticeTicks = 50;
            return false;
        }
        clearHeld();
        return true;
    }

    private void placeInput(int slot) {
        ItemStack stack = Gradient.same(held, gradient.input(slot)) ? null : held;
        if (gradient.setInput(slot, stack, colors)) {
            clearHeld();
        } else {
            noticeTicks = 50;
        }
    }

    private void placeHotbar(int slot) {
        if (creative() && slot != heldFrom) {
            if (heldFrom >= 0) {
                putInventory(heldFrom, hotbar(slot));
            }
            putInventory(slot, held);
        }
        clearHeld();
    }

    private void discardHeld() {
        if (creative() && heldFrom >= 0) {
            putInventory(heldFrom, null);
        }
        clearHeld();
    }

    private void clearHeld() {
        held = null;
        heldFrom = -1;
        pickedUpThisClick = false;
    }

    private ItemStack hotbar(int slot) {
        return mc.thePlayer.inventory.getStackInSlot(slot);
    }

    private boolean hasOutput() {
        if (!colors.isIndexed()) {
            return false;
        }
        for (int i = 0; i < Gradient.SLOTS; i++) {
            if (gradient.output(i) != null) {
                return true;
            }
        }
        return false;
    }

    private int refreshAt(int x, int y) {
        if (!inside(x, y, ROW_X, REFRESH_Y, SLOT * Gradient.SLOTS, REFRESH_H)) {
            return -1;
        }
        int index = (x - ROW_X) / SLOT;
        return gradient.rank(index) >= 0 ? index : -1;
    }

    private static int slotAt(int x, int y, int row) {
        return inside(x, y, ROW_X, row, SLOT * Gradient.SLOTS, SLOT) ? (x - ROW_X) / SLOT : -1;
    }
}
