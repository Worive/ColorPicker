package com.worive.colorpicker.client.gui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.worive.colorpicker.client.color.BlockColors;
import com.worive.colorpicker.client.color.ColorMath;
import com.worive.colorpicker.client.color.TexturePattern;
import com.worive.colorpicker.client.gui.PickerSettings.Tolerance;

public final class PickerScreen extends ToolScreen {

    private static final Logger LOG = LogManager.getLogger("ColorPicker");

    private static final int WIDTH = 282, HEIGHT = 276;
    private static final int COLUMNS = 12, ROWS = 2, CELL = 20, MATCH_LIMIT = 100;
    private static final int MATCH_X = 10, MATCH_Y = 202, MATCH_LABEL_Y = MATCH_Y - 14, GRID_HEIGHT = ROWS * CELL;
    private static final int SCROLL_W = 14, SCROLL_X = WIDTH - 10 - SCROLL_W, THUMB_H = 15;
    private static final ResourceLocation CREATIVE_TABS = new ResourceLocation(
        "textures/gui/container/creative_inventory/tabs.png");
    private static final int SV_X = 10, SV_Y = 33, SV_W = 100, SV_H = 56, HUE_X = 115, HUE_W = 16;
    private static final int SWATCH_Y = 93, SWATCH_W = 20, EYE_X = 113;
    private static final int HEX_X = 34, HEX_W = 74, HEX_FIELD = 6;
    private static final int MODE_Y = 116, MODE_W = 35, MODE_H = 14, RGB_X = 59, HSB_X = 96;
    private static final int MODE_FOCUS = 7, TOLERANCE_FOCUS = 8, PATTERN_FOCUS = 9, CHANNELS_PER_MODE = 3;
    private static final int SLIDER_X = 36, SLIDER_Y = 135, SLIDER_W = 54, CHANNEL_H = 11, SLIDER_INSET = 4;
    private static final int SLIDER_RANGE = SLIDER_W - 2 * SLIDER_INSET - 1;
    private static final int FIELD_X = 94, FIELD_W = 37;
    private static final int SETTINGS_WIDTH = 132, FILTER_X = WIDTH - PANEL_INSET - SETTINGS_WIDTH, FILTER_Y = SV_Y;
    private static final int TOLERANCE_Y = FILTER_Y + FILTERS_HEIGHT + 13, PATTERN_Y = TOLERANCE_Y + 24;
    private static final SteppedSlider TOLERANCE_SLIDER = new SteppedSlider(
        FILTER_X,
        TOLERANCE_Y,
        SETTINGS_WIDTH,
        PickerSettings.TOLERANCE_STEP_COUNT);
    private static final SteppedSlider PATTERN_SLIDER = new SteppedSlider(
        FILTER_X,
        PATTERN_Y,
        SETTINGS_WIDTH,
        PickerSettings.PATTERN_STEP_COUNT);
    private static final int HOTBAR_X = 10, HOTBAR_Y = 251, TRASH_X = 178;
    private static final String[] CHANNEL_NAMES = { "R", "G", "B", "Hue", "Sat", "Bri" };
    private static final int DRAG_SV = 6, DRAG_HUE = 7, DRAG_SCROLL = 8, DRAG_TOLERANCE = 9, DRAG_PATTERN = 10;

    private final BlockColors colors;
    private final PickerSettings settings;
    private final PickerColor color;
    private final MovingObjectPosition worldTarget;
    private final GuiTextField[] fields = new GuiTextField[7];
    private List<BlockColors.Entry> matches = Collections.emptyList();
    private int rankedRgb = -1, rankedFilters = -1;
    private long rankedRevision = -1;
    private Tolerance rankedTolerance;
    private int buttonFocus = -1;
    private boolean sliderFocusVisible;
    private int firstRow;
    private int dragControl = -1;
    private ItemStack held;
    private int heldFrom = -1;
    private ItemStack sampled;
    private int sourceX, sourceY;
    private boolean pickedUpThisClick;
    private String notice = "";
    private int noticeTicks;

    public PickerScreen(GuiScreen parent, BlockColors colors, PickerSettings settings, PickerColor color,
        MovingObjectPosition worldTarget, int openKey, Consumer<Boolean> openTool) {
        super(parent, openKey, openTool, false, WIDTH, HEIGHT);
        this.colors = colors;
        this.settings = settings;
        this.color = color;
        this.worldTarget = worldTarget;
    }

    @Override
    public void initGui() {
        super.initGui();
        for (int i = 0; i < CHANNEL_NAMES.length; i++) {
            fields[i] = textField(FIELD_X + 3, channelY(i) + 2, FIELD_W - 6, 6);
        }
        fields[HEX_FIELD] = textField(HEX_X + 5, SWATCH_Y + 5, HEX_W - 10, 7);
        syncFields();
        Keyboard.enableRepeatEvents(true);
    }

    private GuiTextField textField(int x, int y, int w, int maxChars) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, w, 10);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(0xffffff);
        field.setMaxStringLength(maxChars);
        return field;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField field : fields) {
            field.updateCursorCounter();
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (held == null) {
            drawTooltip(mouseX, mouseY, localX(mouseX), localY(mouseY));
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        updateResults();
        int x = localX(mouseX), y = localY(mouseY);
        beginPanel("ColorPicker", x, y);
        drawColorControls(x, y);
        drawFilters(FILTER_X, FILTER_Y, settings.filters(), x, y);
        Tolerance tolerance = settings.tolerance();
        drawSteppedSlider(
            TOLERANCE_SLIDER,
            "Tolerance",
            tolerance.label,
            tolerance.ordinal(),
            buttonFocus == TOLERANCE_FOCUS && sliderFocusVisible,
            dragControl == DRAG_TOLERANCE || TOLERANCE_SLIDER.contains(x, y));
        TexturePattern pattern = settings.pattern();
        drawSteppedSlider(
            PATTERN_SLIDER,
            "Pattern",
            pattern.label,
            pattern.ordinal(),
            buttonFocus == PATTERN_FOCUS && sliderFocusVisible,
            dragControl == DRAG_PATTERN || PATTERN_SLIDER.contains(x, y));
        drawRect(MATCH_X, 180, WIDTH - 10, 181, BORDER);
        drawResults(x, y);
        if (noticeTicks > 0) {
            fontRendererObj
                .drawString(notice, WIDTH - 10 - fontRendererObj.getStringWidth(notice), MATCH_LABEL_Y, LABEL);
        }
        drawHotbar(x, y);
        drawCursor(held, x, y);
        endPanel();
    }

    private void updateResults() {
        int rgb = color.rgb();
        int filters = settings.filters();
        Tolerance tolerance = settings.tolerance();
        boolean queryChanged = rankedRgb != rgb || rankedFilters != filters || rankedTolerance != tolerance;
        if (queryChanged || rankedRevision != colors.revision()) {
            matches = colors.closest(rgb, filters, MATCH_LIMIT, tolerance.distanceSquared);
            firstRow = queryChanged ? 0 : Math.min(firstRow, lastRow());
            rankedRgb = rgb;
            rankedFilters = filters;
            rankedTolerance = tolerance;
            rankedRevision = colors.revision();
        }
    }

    private void drawColorControls(int x, int y) {
        frame(SV_X, SV_Y, SV_W, SV_H, SLOT_GREY, true);
        int hueRgb = ColorMath.hsb(color.hue(), 1, 1);
        gradient(SV_X + 1, SV_Y + 1, SV_W - 2, SV_H - 2, 0xffffff, hueRgb, 0xffffff, hueRgb);
        drawGradientRect(SV_X + 1, SV_Y + 1, SV_X + SV_W - 1, SV_Y + SV_H - 1, 0, 0xff000000);
        int saturationMarker = SV_X + 1 + Math.round(color.saturation() * (SV_W - 3));
        int brightnessMarker = SV_Y + 1 + Math.round((1 - color.brightness()) * (SV_H - 3));
        outline(saturationMarker - 3, brightnessMarker - 3, 7, 7, 0xffffffff, 0xff181818);
        drawRect(
            saturationMarker - 1,
            brightnessMarker - 1,
            saturationMarker + 2,
            brightnessMarker + 2,
            solid(color.rgb()));

        frame(HUE_X, SV_Y, HUE_W, SV_H, SLOT_GREY, true);
        for (int i = 0; i < 6; i++) {
            int from = SV_Y + 1 + i * (SV_H - 2) / 6;
            int to = SV_Y + 1 + (i + 1) * (SV_H - 2) / 6;
            int start = ColorMath.hsb(i / 6f, 1, 1), end = ColorMath.hsb((i + 1) / 6f, 1, 1);
            gradient(HUE_X + 1, from, HUE_W - 2, to - from, start, start, end, end);
        }
        int hueMarker = SV_Y + 1 + Math.round(color.hue() * (SV_H - 3));
        outline(HUE_X - 1, hueMarker - 1, HUE_W + 2, 3, 0xffffffff, 0xff181818);

        outline(
            SV_X,
            SWATCH_Y,
            SWATCH_W,
            SLOT,
            solid(color.rgb()),
            inside(x, y, SV_X, SWATCH_Y, SWATCH_W, SLOT) ? LABEL : MUTED);
        drawInventorySlot(EYE_X, SWATCH_Y);
        if (sampled == null) {
            drawDropper(EYE_X + 5, SWATCH_Y + 4, LABEL);
        } else {
            drawStack(sampled, EYE_X + 1, SWATCH_Y + 1, false);
        }
        highlight(EYE_X, SWATCH_Y, x, y);
        drawField(HEX_FIELD, HEX_X, SWATCH_Y, HEX_W, SLOT);

        fontRendererObj.drawString("Channels", SV_X, MODE_Y + 3, LABEL);
        drawChannelMode("RGB", RGB_X, !color.hsbChannels, x, y);
        drawChannelMode("HSB", HSB_X, color.hsbChannels, x, y);

        int first = firstChannel();
        for (int i = first; i < first + CHANNELS_PER_MODE; i++) {
            int row = channelY(i);
            fontRendererObj.drawString(CHANNEL_NAMES[i], SV_X, row + 2, LABEL);
            drawChannel(i, row, x, y);
            drawField(i, FIELD_X, row, FIELD_W, CHANNEL_H);
        }
    }

    private void drawChannelMode(String label, int left, boolean selected, int x, int y) {
        if (selected) {
            if (buttonFocus == MODE_FOCUS) {
                drawButtonFocus(left, MODE_Y, MODE_W, MODE_H);
            }
            frame(left, MODE_Y, MODE_W, MODE_H, 0xffd8d8d8, true);
            int textX = left + (MODE_W - fontRendererObj.getStringWidth(label)) / 2;
            fontRendererObj.drawString(label, textX, MODE_Y + 3, LABEL);
        } else {
            drawAction(label, left, MODE_Y, MODE_W, MODE_H, x, y, true);
        }
    }

    private int firstChannel() {
        return color.hsbChannels ? CHANNELS_PER_MODE : 0;
    }

    private static int channelY(int channel) {
        return SLIDER_Y + channel % CHANNELS_PER_MODE * 14;
    }

    private void drawChannel(int channel, int y, int mouseX, int mouseY) {
        frame(SLIDER_X, y, SLIDER_W, CHANNEL_H, SLOT_GREY, true);
        int railWidth = SLIDER_W - 2;
        if (channel == 3) {
            for (int i = 0; i < 6; i++) {
                int from = SLIDER_X + 1 + i * railWidth / 6;
                int to = SLIDER_X + 1 + (i + 1) * railWidth / 6;
                int start = ColorMath.hsb(i / 6f, 1, 1), end = ColorMath.hsb((i + 1) / 6f, 1, 1);
                gradient(from, y + 1, to - from, CHANNEL_H - 2, start, end, start, end);
            }
        } else {
            int start = color.channelColor(channel, 0), end = color.channelColor(channel, 1);
            gradient(SLIDER_X + 1, y + 1, railWidth, CHANNEL_H - 2, start, end, start, end);
        }
        int thumbX = SLIDER_X + SLIDER_INSET + Math.round(color.value(channel) * SLIDER_RANGE) - 3;
        boolean hovered = dragControl == channel || inside(mouseX, mouseY, SLIDER_X, y, SLIDER_W, CHANNEL_H);
        drawButtonSkin(thumbX, y, 7, CHANNEL_H, hovered ? 2 : 1);
    }

    private void drawField(int i, int x, int y, int w, int h) {
        GuiTextField field = fields[i];
        boolean focused = field.isFocused();
        boolean invalid = focused && (i == HEX_FIELD ? ColorMath.parseHex(field.getText()) == null
            : PickerColor.parseChannel(field.getText(), i) == null);
        outline(x, y, w, h, 0xff202020, invalid ? 0xffc04444 : focused ? 0xffeeeeee : 0xff696969);
        field.drawTextBox();
    }

    private void drawResults(int mouseX, int mouseY) {
        String label = matches.isEmpty() ? "Matches"
            : (matches.size() == MATCH_LIMIT ? "Top " : "") + matches.size() + " matches";
        fontRendererObj.drawString(label, MATCH_X, MATCH_LABEL_Y, LABEL);
        for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
            int x = MATCH_X + slot % COLUMNS * CELL + 1;
            int y = MATCH_Y + slot / COLUMNS * CELL + 1;
            drawInventorySlot(x, y);
            int index = firstRow * COLUMNS + slot;
            if (index < matches.size()) {
                drawStack(matches.get(index).stack, x + 1, y + 1, false);
                highlight(x, y, mouseX, mouseY);
            }
        }
        frame(SCROLL_X, MATCH_Y, SCROLL_W, GRID_HEIGHT, SLOT_GREY, true);
        int last = lastRow();
        int thumbY = MATCH_Y + 1;
        if (last > 0) {
            thumbY += Math.round((GRID_HEIGHT - THUMB_H - 2) * firstRow / (float) last);
        }
        mc.getTextureManager()
            .bindTexture(CREATIVE_TABS);
        GL11.glColor4f(1, 1, 1, 1);
        drawTexturedModalRect(SCROLL_X + 1, thumbY, last > 0 ? 232 : 244, 0, 12, THUMB_H);
        if (matches.isEmpty()) {
            String empty = colors.isIndexed() ? "No matches" : "Indexing blocks...";
            int center = MATCH_X + COLUMNS * CELL / 2;
            int textWidth = fontRendererObj.getStringWidth(empty);
            int emptyTop = MATCH_Y + (GRID_HEIGHT - 16) / 2;
            frame(center - textWidth / 2 - 4, emptyTop, textWidth + 8, 16, 0xffc6c6c6, false);
            fontRendererObj.drawString(empty, center - textWidth / 2, emptyTop + 4, LABEL);
        }
    }

    private void drawHotbar(int mouseX, int mouseY) {
        drawRect(HOTBAR_X, HOTBAR_Y - 5, WIDTH - 10, HOTBAR_Y - 4, BORDER);
        for (int slot = 0; slot < 9; slot++) {
            int x = HOTBAR_X + slot * SLOT;
            drawInventorySlot(x, HOTBAR_Y);
            if (heldFrom != slot) {
                drawStack(mc.thePlayer.inventory.getStackInSlot(slot), x + 1, HOTBAR_Y + 1, true);
            }
            highlight(x, HOTBAR_Y, mouseX, mouseY);
        }
        drawTrash(TRASH_X, HOTBAR_Y, creative());
        highlight(TRASH_X, HOTBAR_Y, mouseX, mouseY);
    }

    @Override
    ItemStack hoveredStack(int x, int y) {
        if (held != null) {
            return null;
        }
        int match = matchAt(x, y);
        if (match >= 0 && match < matches.size()) {
            return matches.get(match).stack;
        }
        if (eyedropperAt(x, y)) {
            return sampled;
        }
        int hotbar = hotbarAt(x, y);
        return hotbar >= 0 ? mc.thePlayer.inventory.getStackInSlot(hotbar) : null;
    }

    private void drawTooltip(int mouseX, int mouseY, int x, int y) {
        List<String> text = null;
        int match = matchAt(x, y);
        int hotbar = hotbarAt(x, y);
        if (match >= 0 && match < matches.size()) {
            BlockColors.Entry entry = matches.get(match);
            text = Arrays.asList(
                entry.stack.getDisplayName(),
                EnumChatFormatting.GRAY + ColorMath.hex(entry.rgb),
                creative() ? "Middle-click to take one item" : "Click to sample");
        } else if (eyedropperAt(x, y)) {
            text = Arrays
                .asList("Eyedropper", "Drop an item to sample", "Right-click to sample the block you aimed at");
        } else if (inside(x, y, SV_X, SWATCH_Y, SWATCH_W, SLOT)) {
            text = Arrays.asList(ColorMath.hex(color.rgb()), "Click to copy hex");
        } else if (dragControl != DRAG_TOLERANCE && TOLERANCE_SLIDER.contains(x, y)) {
            text = Arrays.asList("Maximum color difference", "Any on the left, Strict on the right");
        } else if (dragControl != DRAG_PATTERN && PATTERN_SLIDER.contains(x, y)) {
            text = PATTERN_TOOLTIP;
        } else if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
            text = Arrays.asList(
                "Destroy Item",
                creative() ? "Drop a hotbar item here to delete it" : "Available in Creative mode");
        } else if (hotbar >= 0) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(hotbar);
            if (stack != null) {
                text = Arrays.asList(stack.getDisplayName(), "Right-click to sample");
            }
        } else {
            int filter = filterAt(x, y, FILTER_X, FILTER_Y);
            if (filter != 0) {
                text = filterTooltip(filter, settings.filters());
            }
        }
        if (text != null) {
            drawHoveringText(text, mouseX, mouseY, fontRendererObj);
        }
    }

    @Override
    void clickPanel(int x, int y, int button) {
        updateResults();
        buttonFocus = -1;
        int field = fieldAt(x, y);
        for (int i = 0; i < fields.length; i++) {
            if (i != field) {
                fields[i].setFocused(false);
            }
        }
        syncFields();
        if (field >= 0) {
            if (button == 0) {
                int left = field == HEX_FIELD ? HEX_X + 5 : FIELD_X + 3;
                int right = field == HEX_FIELD ? HEX_X + HEX_W - 6 : FIELD_X + FIELD_W - 4;
                int top = field == HEX_FIELD ? SWATCH_Y + 5 : channelY(field) + 2;
                fields[field].mouseClicked(Math.max(left, Math.min(right, x)), top, button);
            }
            return;
        }
        if (button > 2) {
            return;
        }
        if (button == 0 && lastRow() > 0 && inside(x, y, SCROLL_X, MATCH_Y, SCROLL_W, GRID_HEIGHT)) {
            dragControl = DRAG_SCROLL;
            scrollTo(y);
            return;
        }
        if (eyedropperAt(x, y)) {
            if (button == 1 || held == null) {
                sampleWorld();
            } else {
                sampleHeld();
            }
            return;
        }
        int match = matchAt(x, y);
        int hotbar = hotbarAt(x, y);
        if (match >= 0 && match < matches.size()) {
            BlockColors.Entry entry = matches.get(match);
            if (button == 1 || !creative()) {
                sample(entry.stack);
            } else if (button == 2) {
                takeItem(entry);
            } else if (isShiftKeyDown()) {
                putInventory(mc.thePlayer.inventory.currentItem, entry.stack.copy());
                clearHeld();
            } else {
                startHolding(entry.stack.copy(), -1, x, y);
            }
            return;
        }
        if (hotbar >= 0) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(hotbar);
            if (button == 1 || !creative()) {
                if (stack != null) {
                    sample(stack);
                }
            } else if (held != null) {
                placeHeld(hotbar);
            } else if (stack != null) {
                startHolding(stack.copy(), hotbar, x, y);
            }
            return;
        }
        if (button != 0) {
            return;
        }
        if (held != null) {
            if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
                discardHeld();
            } else {
                clearHeld();
            }
            return;
        }
        if (inside(x, y, SV_X, SWATCH_Y, SWATCH_W, SLOT)) {
            copyHex();
            return;
        }
        int filter = filterAt(x, y, FILTER_X, FILTER_Y);
        if (filter != 0) {
            settings.toggleFilter(filter);
            return;
        }
        if (inside(x, y, RGB_X, MODE_Y, MODE_W, MODE_H)) {
            color.hsbChannels = false;
            return;
        }
        if (inside(x, y, HSB_X, MODE_Y, MODE_W, MODE_H)) {
            color.hsbChannels = true;
            return;
        }
        boolean tolerance = TOLERANCE_SLIDER.contains(x, y);
        if (tolerance || PATTERN_SLIDER.contains(x, y)) {
            buttonFocus = tolerance ? TOLERANCE_FOCUS : PATTERN_FOCUS;
            sliderFocusVisible = false;
            dragControl = tolerance ? DRAG_TOLERANCE : DRAG_PATTERN;
            adjust(x, y);
            return;
        }
        int first = firstChannel();
        for (int i = first; i < first + CHANNELS_PER_MODE; i++) {
            if (inside(x, y, SLIDER_X, channelY(i), SLIDER_W, CHANNEL_H)) {
                dragControl = i;
                break;
            }
        }
        if (inside(x, y, SV_X, SV_Y, SV_W, SV_H)) {
            dragControl = DRAG_SV;
        } else if (inside(x, y, HUE_X, SV_Y, HUE_W, SV_H)) {
            dragControl = DRAG_HUE;
        }
        adjust(x, y);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        super.mouseClickMove(mouseX, mouseY, button, elapsed);
        if (button == 0) {
            adjust(localX(mouseX), localY(mouseY));
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        dragControl = -1;
        if (!pickedUpThisClick || held == null) {
            return;
        }
        pickedUpThisClick = false;
        int x = localX(mouseX), y = localY(mouseY);
        if (Math.abs(x - sourceX) < 3 && Math.abs(y - sourceY) < 3) {
            return;
        }
        int slot = hotbarAt(x, y);
        if (eyedropperAt(x, y)) {
            sampleHeld();
        } else if (slot >= 0) {
            placeHeld(slot);
        } else if (inside(x, y, TRASH_X, HOTBAR_Y, SLOT, SLOT)) {
            discardHeld();
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        int focused = focusedField();
        if (focused < 0 && (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT)) {
            int step = key == Keyboard.KEY_PRIOR ? -ROWS : ROWS;
            firstRow = Math.max(0, Math.min(lastRow(), firstRow + step));
            return;
        }
        if (key == Keyboard.KEY_ESCAPE && (focused >= 0 || buttonFocus >= 0)) {
            if (focused >= 0) {
                fields[focused].setFocused(false);
            }
            buttonFocus = -1;
            syncFields();
            return;
        }
        if (key == Keyboard.KEY_ESCAPE
            || (focused < 0 && (key == openKey || key == mc.gameSettings.keyBindInventory.getKeyCode()))) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            tabColorControls(focused);
            return;
        }
        if (buttonFocus == TOLERANCE_FOCUS || buttonFocus == PATTERN_FOCUS) {
            boolean tolerance = buttonFocus == TOLERANCE_FOCUS;
            int step = tolerance ? TOLERANCE_SLIDER.keyStep(
                settings.tolerance()
                    .ordinal(),
                key)
                : PATTERN_SLIDER.keyStep(
                    settings.pattern()
                        .ordinal(),
                    key);
            if (step >= 0) {
                sliderFocusVisible = true;
                if (tolerance) {
                    settings.setToleranceStep(step);
                } else {
                    settings.setPatternStep(step);
                }
                return;
            }
        } else if (buttonFocus == MODE_FOCUS) {
            switch (key) {
                case Keyboard.KEY_LEFT:
                    color.hsbChannels = false;
                    return;
                case Keyboard.KEY_RIGHT:
                    color.hsbChannels = true;
                    return;
                case Keyboard.KEY_RETURN:
                case Keyboard.KEY_NUMPADENTER:
                case Keyboard.KEY_SPACE:
                    color.hsbChannels = !color.hsbChannels;
                    return;
                default:
                    break;
            }
        }
        if (focused >= 0) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                fields[focused].setFocused(false);
                syncFields();
            } else if (inputAllowed(typed, focused) && editField(fields[focused], editCharacter(typed, key), key)) {
                if (focused == HEX_FIELD) {
                    Integer rgb = ColorMath.parseHex(fields[focused].getText());
                    if (rgb != null) {
                        color.setRgb(rgb);
                    }
                } else {
                    Float value = PickerColor.parseChannel(fields[focused].getText(), focused);
                    if (value != null) {
                        color.setChannel(focused, value);
                    }
                }
                syncFields();
            }
            return;
        }
        if (isCtrlKeyDown() && key == Keyboard.KEY_C) {
            copyHex();
            return;
        }
        if (held != null && creative()) {
            for (int i = 0; i < 9; i++) {
                if (key == mc.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                    placeHeld(i);
                    return;
                }
            }
        }
        super.keyTyped(typed, key);
    }

    private void tabColorControls(int focused) {
        int first = firstChannel();
        List<Integer> order = Arrays
            .asList(HEX_FIELD, MODE_FOCUS, first, first + 1, first + 2, TOLERANCE_FOCUS, PATTERN_FOCUS);
        int index = order.indexOf(buttonFocus >= 0 ? buttonFocus : focused);
        int step = isShiftKeyDown() ? -1 : 1;
        int next = index < 0 ? (step < 0 ? order.size() - 1 : 0) : Math.floorMod(index + step, order.size());
        if (focused >= 0) {
            fields[focused].setFocused(false);
        }
        syncFields();
        int control = order.get(next);
        buttonFocus = control >= fields.length ? control : -1;
        sliderFocusVisible = control == TOLERANCE_FOCUS || control == PATTERN_FOCUS;
        if (buttonFocus < 0) {
            fields[control].setFocused(true);
        }
    }

    private static char editCharacter(char typed, int key) {
        if (isCtrlKeyDown()) {
            switch (key) {
                case Keyboard.KEY_A:
                    return 1;
                case Keyboard.KEY_C:
                    return 3;
                case Keyboard.KEY_V:
                    return 22;
                case Keyboard.KEY_X:
                    return 24;
                default:
                    break;
            }
        }
        return typed;
    }

    private static boolean editField(GuiTextField field, char typed, int key) {
        int cursor = field.getCursorPosition();
        int selectionEnd = field.getSelectionEnd();
        if (cursor > selectionEnd && (typed == 22 || ChatAllowedCharacters.isAllowedCharacter(typed))) {
            // Minecraft 1.7.10 needs the cursor at the left edge of the selection to replace it at max length.
            field.setCursorPosition(selectionEnd);
            field.setSelectionPos(cursor);
        }
        return field.textboxKeyTyped(typed, key);
    }

    private static boolean inputAllowed(char typed, int field) {
        return isCtrlKeyDown() || !ChatAllowedCharacters.isAllowedCharacter(typed)
            || (field == HEX_FIELD ? typed == '#' || Character.digit(typed, 16) >= 0
                : (typed >= '0' && typed <= '9') || typed == '.');
    }

    private void adjust(int x, int y) {
        if (dragControl == DRAG_SCROLL) {
            scrollTo(y);
            return;
        }
        if (dragControl == DRAG_TOLERANCE) {
            settings.setToleranceStep(TOLERANCE_SLIDER.mouseStep(x));
            return;
        }
        if (dragControl == DRAG_PATTERN) {
            settings.setPatternStep(PATTERN_SLIDER.mouseStep(x));
            return;
        }
        if (dragControl == DRAG_SV) {
            color.setHsb(color.hue(), fraction(x, SV_X + 1, SV_W - 3), 1 - fraction(y, SV_Y + 1, SV_H - 3));
        } else if (dragControl == DRAG_HUE) {
            color.setHsb(fraction(y, SV_Y + 1, SV_H - 3), color.saturation(), color.brightness());
        } else if (dragControl >= 0) {
            color.setChannel(dragControl, fraction(x, SLIDER_X + SLIDER_INSET, SLIDER_RANGE));
        } else {
            return;
        }
        syncFields();
    }

    private void syncFields() {
        for (int i = 0; i < fields.length; i++) {
            if (!fields[i].isFocused()) {
                fields[i].setText(i == HEX_FIELD ? ColorMath.hex(color.rgb()) : color.channelText(i));
            }
        }
    }

    private boolean eyedropperAt(int x, int y) {
        return inside(x, y, EYE_X, SWATCH_Y, SLOT, SLOT);
    }

    @Override
    boolean dropGhostStack(int x, int y, ItemStack stack) {
        if (!eyedropperAt(x, y)) {
            return false;
        }
        for (GuiTextField field : fields) {
            field.setFocused(false);
        }
        buttonFocus = -1;
        sample(stack);
        clearHeld();
        return true;
    }

    private void sample(ItemStack stack) {
        Integer rgb;
        try {
            rgb = colors.sample(stack);
        } catch (RuntimeException failure) {
            LOG.debug("Could not sample item", failure);
            rgb = null;
        }
        if (rgb != null) {
            sampled = stack.copy();
            color.setRgb(rgb);
            syncFields();
        } else {
            notifyUser("No color found");
        }
    }

    private void sampleWorld() {
        if (worldTarget == null || worldTarget.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            notifyUser("No target block");
            return;
        }
        int x = worldTarget.blockX, y = worldTarget.blockY, z = worldTarget.blockZ;
        Block block = mc.theWorld.getBlock(x, y, z);
        Integer rgb;
        try {
            rgb = colors
                .sample(block, mc.theWorld.getBlockMetadata(x, y, z), block.colorMultiplier(mc.theWorld, x, y, z));
        } catch (RuntimeException failure) {
            LOG.debug("Could not sample block in world", failure);
            rgb = null;
        }
        if (rgb != null) {
            sampled = null;
            color.setRgb(rgb);
            syncFields();
        } else {
            notifyUser("No color found");
        }
    }

    private void takeItem(BlockColors.Entry entry) {
        int slot = mc.thePlayer.inventory.getFirstEmptyStack();
        if (slot < 0) {
            notifyUser("Inventory full");
            return;
        }
        putInventory(slot, entry.stack.copy());
        clearHeld();
        notifyUser(slot < 9 ? "Added to hotbar" : "Added to bag");
    }

    private void startHolding(ItemStack stack, int from, int x, int y) {
        held = stack;
        heldFrom = from;
        sourceX = x;
        sourceY = y;
        pickedUpThisClick = true;
    }

    private void placeHeld(int slot) {
        if (creative() && slot != heldFrom) {
            if (heldFrom >= 0) {
                putInventory(heldFrom, mc.thePlayer.inventory.getStackInSlot(slot));
            }
            putInventory(slot, held);
        }
        clearHeld();
    }

    private void sampleHeld() {
        sample(held);
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

    private void copyHex() {
        setClipboardString(ColorMath.hex(color.rgb()));
        notifyUser("Copied " + ColorMath.hex(color.rgb()));
    }

    private void notifyUser(String message) {
        notice = message;
        noticeTicks = 50;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && mc.currentScreen == this) {
            int x = localX(Mouse.getEventX() * width / mc.displayWidth);
            int y = localY(height - Mouse.getEventY() * height / mc.displayHeight - 1);
            if (inside(x, y, MATCH_X, MATCH_Y, SCROLL_X + SCROLL_W - MATCH_X, GRID_HEIGHT)) {
                firstRow = Math.max(0, Math.min(lastRow(), firstRow - Integer.signum(wheel)));
            }
        }
    }

    private int lastRow() {
        return Math.max(0, (matches.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    private void scrollTo(int y) {
        int rows = lastRow();
        if (rows > 0) {
            firstRow = Math.round(rows * fraction(y, MATCH_Y + 1 + THUMB_H / 2, GRID_HEIGHT - THUMB_H - 2));
        }
    }

    private int matchAt(int x, int y) {
        return inside(x, y, MATCH_X, MATCH_Y, COLUMNS * CELL, GRID_HEIGHT)
            ? (firstRow + (y - MATCH_Y) / CELL) * COLUMNS + (x - MATCH_X) / CELL
            : -1;
    }

    private int hotbarAt(int x, int y) {
        return inside(x, y, HOTBAR_X, HOTBAR_Y, 9 * SLOT, SLOT) ? (x - HOTBAR_X) / SLOT : -1;
    }

    private int fieldAt(int x, int y) {
        if (inside(x, y, HEX_X, SWATCH_Y, HEX_W, SLOT)) {
            return HEX_FIELD;
        }
        int first = firstChannel();
        for (int i = first; i < first + CHANNELS_PER_MODE; i++) {
            if (inside(x, y, FIELD_X, channelY(i), FIELD_W, CHANNEL_H)) {
                return i;
            }
        }
        return -1;
    }

    private int focusedField() {
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].isFocused()) {
                return i;
            }
        }
        return -1;
    }

    private static float fraction(int value, int origin, int range) {
        return Math.max(0, Math.min(1, (value - origin) / (float) range));
    }

    private static void gradient(int x, int y, int w, int h, int tl, int tr, int bl, int br) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorOpaque_I(solid(bl));
        tess.addVertex(x, y + h, 0);
        tess.setColorOpaque_I(solid(br));
        tess.addVertex(x + w, y + h, 0);
        tess.setColorOpaque_I(solid(tr));
        tess.addVertex(x + w, y, 0);
        tess.setColorOpaque_I(solid(tl));
        tess.addVertex(x, y, 0);
        tess.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static int solid(int rgb) {
        return 0xff000000 | rgb;
    }

    private static void drawDropper(int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            drawRect(x + i, y + 8 - i, x + i + 2, y + 9 - i, color);
        }
        drawRect(x + 6, y, x + 9, y + 2, color);
        drawRect(x + 5, y + 2, x + 9, y + 3, color);
    }
}
