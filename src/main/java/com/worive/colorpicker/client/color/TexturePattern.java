package com.worive.colorpicker.client.color;

import java.awt.image.BufferedImage;

public enum TexturePattern {

    ANY("Any", 0),
    SMOOTH("Smooth", 128),
    PATTERNED("Patterned", 256),
    OUTLINED("Outlined", 512);

    private static final TexturePattern[] STEPS = values();
    public static final int FILTERS = SMOOTH.bit | PATTERNED.bit | OUTLINED.bit;
    private static final double BORDER_CONTRAST = 0.03;
    private static final double BEVEL_CONTRAST = 0.14;
    private static final double SEAM_COLOR_TOLERANCE = 0.1;
    private static final int FAINT_SEAM = 1;
    private static final int CLEAR_SEAM = 2;

    public final String label;
    public final int bit;

    TexturePattern(String label, int bit) {
        this.label = label;
        this.bit = bit;
    }

    public static TexturePattern fromFilters(int filters) {
        int selected = filters & FILTERS;
        for (TexturePattern pattern : STEPS) {
            if (pattern.bit == selected) {
                return pattern;
            }
        }
        return ANY;
    }

    static TexturePattern classify(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        // Minecraft packs animated textures as a vertical strip of square frames.
        int frameHeight = height % width == 0 ? width : height;
        TexturePattern pattern = SMOOTH;
        for (int top = 0; top < height; top += frameHeight) {
            TexturePattern frame = classifyFrame(image, top, width, frameHeight);
            if (frame == OUTLINED) {
                return frame;
            }
            if (frame == PATTERNED) {
                pattern = frame;
            }
        }
        return pattern;
    }

    private static TexturePattern classifyFrame(BufferedImage image, int top, int width, int height) {
        double north = borderContrast(image, top, height, width, true, 0, 1);
        double south = borderContrast(image, top, height, width, true, height - 1, -1);
        double west = borderContrast(image, top, width, height, false, 0, 1);
        double east = borderContrast(image, top, width, height, false, width - 1, -1);
        boolean framed = north != 0 && south != 0 && west != 0 && east != 0;
        if (framed && clearBorder(north, south) && clearBorder(west, east)) {
            boolean tiled = hasTileSeam(image, top, height, width, true, north, south)
                || hasTileSeam(image, top, width, height, false, west, east);
            return tiled ? PATTERNED : OUTLINED;
        }
        int horizontalSeam = seamStrength(image, top, height, width, true, framed);
        boolean patterned = horizontalSeam == CLEAR_SEAM
            || horizontalSeam + seamStrength(image, top, width, height, false, framed) >= CLEAR_SEAM;
        return patterned ? PATTERNED : SMOOTH;
    }

    private static boolean clearBorder(double first, double last) {
        return first * last > 0 || Math.max(Math.abs(first), Math.abs(last)) >= BEVEL_CONTRAST;
    }

    private static double borderContrast(BufferedImage image, int top, int lines, int length, boolean rows, int edge,
        int step) {
        if (lines < 3 || length < 3) {
            return 0;
        }
        int corners = Math.max(1, length / 8);
        int span = length - 2 * corners;
        int bevelDepth = Math.max(1, lines / 8);
        int room = step > 0 ? lines - edge - 1 : edge;
        int maxDepth = Math.min(room, Math.max(1, lines / 4));
        double strongest = 0;
        for (int inset = 1; inset <= maxDepth; inset++) {
            int lighter = 0, darker = 0, moreOpaque = 0;
            double difference = 0, insideBrightness = 0, opacityDifference = 0;
            for (int offset = corners; offset < length - corners; offset++) {
                int outer = pixel(image, top, rows, edge, offset);
                int inner = pixel(image, top, rows, edge + inset * step, offset);
                insideBrightness += brightness(inner);
                if (inset > bevelDepth) {
                    int lastRimPixel = pixel(image, top, rows, edge + (inset - 1) * step, offset);
                    if (Math.abs(contrast(outer, lastRimPixel)) >= BORDER_CONTRAST) {
                        continue;
                    }
                }
                double opacity = ((outer >>> 24) - (inner >>> 24)) / 255.0;
                if (opacity >= 0.15) {
                    moreOpaque++;
                }
                opacityDifference += opacity;
                double contrast = colorContrast(outer, inner);
                if (contrast >= BORDER_CONTRAST) {
                    lighter++;
                } else if (contrast <= -BORDER_CONTRAST) {
                    darker++;
                }
                difference += contrast;
            }
            // Glass borders can have gaps while still tracing each edge.
            if (2 * moreOpaque > span && opacityDifference / span >= 0.15) {
                return opacityDifference / span;
            }
            // A smaller change in brightness can make a visible bevel on a dark block.
            double scale = Math.max(0.4, Math.min(1, 2 * insideBrightness / span));
            double border = difference / (span * scale);
            if (4 * Math.max(lighter, darker) >= 3 * span && Math.abs(border) > Math.abs(strongest)) {
                strongest = border;
            }
        }
        return strongest;
    }

    private static boolean hasTileSeam(BufferedImage image, int top, int lines, int length, boolean rows,
        double firstBorder, double lastBorder) {
        int corners = Math.max(1, length / 8);
        int span = length - 2 * corners;
        int inset = Math.max(2, lines / 4);
        // The wraparound seam can recur between tiles even when their interiors use different grain.
        for (int seam = inset; seam <= lines - inset; seam++) {
            int matchingFirst = 0, matchingLast = 0;
            double firstShift = 0, lastShift = 0;
            for (int offset = corners; offset < length - corners; offset++) {
                int first = pixel(image, top, rows, 0, offset);
                int last = pixel(image, top, rows, lines - 1, offset);
                double firstDifference = contrast(first, pixel(image, top, rows, seam, offset));
                double lastDifference = contrast(last, pixel(image, top, rows, seam - 1, offset));
                firstShift += firstDifference;
                lastShift += lastDifference;
                if (Math.abs(firstDifference) <= SEAM_COLOR_TOLERANCE) {
                    matchingFirst++;
                }
                if (Math.abs(lastDifference) <= SEAM_COLOR_TOLERANCE) {
                    matchingLast++;
                }
            }
            if (4 * Math.min(matchingFirst, matchingLast) >= 3 * span
                && Math.max(Math.abs(firstShift), Math.abs(lastShift)) <= span * 0.04
                && matchesBorder(borderContrast(image, top, lines, length, rows, seam, 1), firstBorder)
                && matchesBorder(borderContrast(image, top, lines, length, rows, seam - 1, -1), lastBorder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBorder(double interior, double exterior) {
        double ratio = interior / exterior;
        return ratio >= 0.5 && ratio <= 1.5;
    }

    private static int seamStrength(BufferedImage image, int top, int lines, int length, boolean rows,
        boolean ignoreBevel) {
        int inset = ignoreBevel ? (lines + 3) / 4 : lines / 8;
        int transitions = lines - 2 * inset - 1;
        if (transitions <= 0 || length < 2) {
            return 0;
        }
        double[] differences = new double[transitions * length];
        double total = 0;
        for (int line = 0; line < transitions; line++) {
            for (int offset = 0; offset < length; offset++) {
                int first = pixel(image, top, rows, inset + line, offset);
                int second = pixel(image, top, rows, inset + line + 1, offset);
                double difference = contrast(first, second);
                differences[line * length + offset] = difference;
                total += Math.abs(difference);
            }
        }
        double grain = total / differences.length;
        int span = Math.min(length, Math.max(3, (length + 3) / 4));
        double localSeam = strongestSeam(differences, length, span, 0);
        double diagonalThreshold = Math.max(0.15, 2.5 * grain + 0.02);
        if (localSeam >= Math.max(0.15, 2 * grain + 0.01)
            || strongestSeam(differences, length, length, 0) >= Math.max(0.04, 1.25 * grain + 0.005)
            || strongestSeam(differences, length, span, 1) >= diagonalThreshold
            || strongestSeam(differences, length, span, -1) >= diagonalThreshold) {
            return CLEAR_SEAM;
        }
        return localSeam >= Math.max(0.04, 1.8 * grain + 0.01) ? FAINT_SEAM : 0;
    }

    private static double strongestSeam(double[] differences, int width, int span, int slope) {
        int height = differences.length / width;
        int stride = 1 + slope * width;
        double strongest = 0;
        for (int line = 0; line < height; line++) {
            int length = slope == 0 ? width : Math.min(width, slope > 0 ? height - line : line + 1);
            strongest = Math.max(strongest, strongestOnLine(differences, line * width, stride, length, span));
        }
        if (slope != 0) {
            int edgeRowStart = slope > 0 ? 0 : (height - 1) * width;
            for (int offset = 1; offset < width; offset++) {
                int length = Math.min(width - offset, height);
                strongest = Math
                    .max(strongest, strongestOnLine(differences, edgeRowStart + offset, stride, length, span));
            }
        }
        return strongest / span;
    }

    private static double strongestOnLine(double[] differences, int start, int stride, int length, int span) {
        double strongest = 0, sum = 0;
        int lighter = 0, darker = 0;
        for (int offset = 0; offset < length; offset++) {
            double change = differences[start + offset * stride];
            sum += change;
            lighter += change >= 0.02 ? 1 : 0;
            darker += change <= -0.02 ? 1 : 0;
            if (offset >= span) {
                double previous = differences[start + (offset - span) * stride];
                sum -= previous;
                lighter -= previous >= 0.02 ? 1 : 0;
                darker -= previous <= -0.02 ? 1 : 0;
            }
            if (offset >= span - 1 && 4 * (sum > 0 ? lighter : darker) >= 3 * span) {
                strongest = Math.max(strongest, Math.abs(sum));
            }
        }
        return strongest;
    }

    private static double contrast(int first, int second) {
        double opacityDifference = ((first >>> 24) - (second >>> 24)) / 255.0;
        double color = colorContrast(first, second);
        return Math.abs(color) > Math.abs(opacityDifference) ? color : opacityDifference;
    }

    private static double colorContrast(int first, int second) {
        double opacity = Math.min(first >>> 24, second >>> 24) / 255.0;
        double luma = brightness(first) - brightness(second);
        double redChroma = (ColorMath.channel(first, 0) - ColorMath.channel(second, 0)) / 255.0 - luma;
        double blueChroma = (ColorMath.channel(first, 2) - ColorMath.channel(second, 2)) / 255.0 - luma;
        double chroma = Math.abs(redChroma) > Math.abs(blueChroma) ? redChroma : blueChroma;
        return opacity * (Math.abs(luma) >= 0.7 * Math.abs(chroma) ? luma : 0.7 * chroma);
    }

    private static int pixel(BufferedImage image, int top, boolean rows, int line, int offset) {
        return image.getRGB(rows ? offset : line, top + (rows ? line : offset));
    }

    private static double brightness(int rgb) {
        return (0.2126 * ColorMath.channel(rgb, 0) + 0.7152 * ColorMath.channel(rgb, 1)
            + 0.0722 * ColorMath.channel(rgb, 2)) / 255;
    }
}
