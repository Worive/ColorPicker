package com.worive.colorpicker.client.color;

import java.awt.Color;
import java.util.Locale;

public final class ColorMath {

    private static final double[] LINEAR = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double value = i / 255.0;
            LINEAR[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
    }

    private ColorMath() {}

    static int rgb(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    public static int channel(int rgb, int index) {
        return (rgb >> (16 - index * 8)) & 255;
    }

    public static int hsb(float hue, float saturation, float brightness) {
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
    }

    public static float[] toHsb(int rgb, float previousHue) {
        float[] hsb = Color.RGBtoHSB(channel(rgb, 0), channel(rgb, 1), channel(rgb, 2), null);
        if (hsb[1] == 0) {
            hsb[0] = previousHue;
        }
        return hsb;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xffffff);
    }

    public static Integer parseHex(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        return digits.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(digits, 16) : null;
    }

    static double[] lab(int rgb) {
        double r = LINEAR[channel(rgb, 0)];
        double g = LINEAR[channel(rgb, 1)];
        double b = LINEAR[channel(rgb, 2)];
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] { 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s };
    }

    static double distance(double[] a, double[] b) {
        double l = a[0] - b[0];
        double m = a[1] - b[1];
        double s = a[2] - b[2];
        return l * l + m * m + s * s;
    }
}
