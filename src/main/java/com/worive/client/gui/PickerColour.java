package com.worive.client.gui;

import java.util.Locale;

import com.worive.client.colour.ColourMath;

public final class PickerColour {

    private static final float[] MAX = { 255, 255, 255, 360, 100, 100 };

    private int rgb;
    private float hue, saturation, brightness;
    boolean hsbChannels;

    public PickerColour() {
        setRgb(0x808080);
    }

    int rgb() {
        return rgb;
    }

    float hue() {
        return hue;
    }

    float saturation() {
        return saturation;
    }

    float brightness() {
        return brightness;
    }

    void setRgb(int rgb) {
        this.rgb = rgb;
        float[] hsb = ColourMath.toHsb(rgb, hue);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    void setHsb(float hue, float saturation, float brightness) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        rgb = ColourMath.hsb(hue, saturation, brightness);
    }

    int channelColour(int channel, float fraction) {
        if (channel < 3) {
            int shift = 16 - channel * 8;
            return (rgb & ~(255 << shift)) | (Math.round(fraction * 255) << shift);
        }
        return ColourMath.hsb(
            channel == 3 ? fraction : hue,
            channel == 4 ? fraction : saturation,
            channel == 5 ? fraction : brightness);
    }

    void setChannel(int channel, float fraction) {
        if (channel < 3) {
            setRgb(channelColour(channel, fraction));
        } else {
            setHsb(
                channel == 3 ? fraction : hue,
                channel == 4 ? fraction : saturation,
                channel == 5 ? fraction : brightness);
        }
    }

    float value(int index) {
        switch (index) {
            case 3:
                return hue;
            case 4:
                return saturation;
            case 5:
                return brightness;
            default:
                return ColourMath.channel(rgb, index) / 255f;
        }
    }

    static Float parseChannel(String text, int index) {
        if (!text.matches("[0-9]*\\.?[0-9]+|[0-9]+\\.")) {
            return null;
        }
        float number = Float.parseFloat(text);
        return number <= MAX[index] ? number / MAX[index] : null;
    }

    String channelText(int index) {
        return String.format(Locale.ROOT, index < 3 ? "%.0f" : "%.1f", value(index) * MAX[index]);
    }
}
