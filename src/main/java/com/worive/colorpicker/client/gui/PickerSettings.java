package com.worive.colorpicker.client.gui;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.worive.colorpicker.client.color.BlockColors;
import com.worive.colorpicker.client.color.TexturePattern;

public final class PickerSettings {

    private static final TexturePattern[] PATTERN_STEPS = TexturePattern.values();
    private static final Tolerance[] TOLERANCE_STEPS = Tolerance.values();
    static final int PATTERN_STEP_COUNT = PATTERN_STEPS.length;
    static final int TOLERANCE_STEP_COUNT = TOLERANCE_STEPS.length;

    private final Configuration config;
    private final Property matchTolerance;
    private final Property enabledFilters;
    private Tolerance tolerance;
    private int filters;

    /** Loads preferences from the game's config directory, including installs using the original filename. */
    public PickerSettings(File gameDirectory) {
        config = new Configuration(configFile(gameDirectory));
        enabledFilters = config.get(
            Configuration.CATEGORY_GENERAL,
            "enabledFilters",
            0,
            "Enabled filters. Add the values for Full cubes (1), Collidable (2), Opaque (4), One texture (8), "
                + "No tile entities (16), Low contrast (32), and No falling (64). "
                + "For Pattern, add 128 for Smooth, 256 for Patterned, or 512 for Outlined.");
        filters = enabledFilters.getInt(0) & BlockColors.ALL_FILTERS;
        if (Integer.bitCount(filters & TexturePattern.FILTERS) > 1) {
            filters &= ~TexturePattern.FILTERS;
            enabledFilters.set(filters);
        }
        matchTolerance = config.get(
            Configuration.CATEGORY_GENERAL,
            "matchTolerance",
            Tolerance.STANDARD.name(),
            "Maximum Oklab distance for ColorPicker matches. EXACT: 0, CLOSE: 0.05, STANDARD: 0.10, WIDE: 0.20, ANY: unlimited.");
        try {
            tolerance = Tolerance.valueOf(matchTolerance.getString());
        } catch (IllegalArgumentException invalid) {
            tolerance = Tolerance.STANDARD;
            matchTolerance.set(tolerance.name());
        }
        if (config.hasChanged()) {
            config.save();
        }
    }

    private static File configFile(File gameDirectory) {
        File config = new File(gameDirectory, "config/colorpicker.cfg");
        File legacyConfig = new File(gameDirectory, "config/colourpicker.cfg");
        return !config.exists() && legacyConfig.isFile() ? legacyConfig : config;
    }

    int filters() {
        return filters;
    }

    Tolerance tolerance() {
        return tolerance;
    }

    void toggleFilter(int filter) {
        saveFilters(filters ^ filter);
    }

    TexturePattern pattern() {
        return TexturePattern.fromFilters(filters);
    }

    void setPatternStep(int step) {
        int selected = (filters & ~TexturePattern.FILTERS) | PATTERN_STEPS[step].bit;
        if (selected != filters) {
            saveFilters(selected);
        }
    }

    private void saveFilters(int selected) {
        filters = selected;
        enabledFilters.set(filters);
        config.save();
    }

    void setToleranceStep(int step) {
        Tolerance preset = TOLERANCE_STEPS[step];
        if (tolerance == preset) {
            return;
        }
        tolerance = preset;
        matchTolerance.set(tolerance.name());
        config.save();
    }

    enum Tolerance {

        ANY("Any", Double.POSITIVE_INFINITY),
        WIDE("Wide", 0.20),
        STANDARD("Standard", 0.10),
        CLOSE("Close", 0.05),
        EXACT("Strict", 0);

        final String label;
        final double distanceSquared;

        Tolerance(String label, double distance) {
            this.label = label;
            distanceSquared = distance * distance;
        }
    }
}
