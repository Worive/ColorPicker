package com.worive.colorpicker.client.color;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

final class TextureColors {

    private static final int FIRST_HORIZONTAL_SIDE = 2;
    private static final double CONTRAST_DISTANCE_SQUARED = 0.18 * 0.18;

    private final Map<ResourceLocation, PixelColor> textures = new HashMap<>();

    void clearCache() {
        textures.clear();
    }

    Integer sampleItem(ItemStack stack) {
        Item item = stack.getItem();
        String folder = item.getSpriteNumber() == 0 ? "blocks" : "items";
        PixelColor pixel = texture(item.getIconIndex(stack), folder);
        return pixel == null ? null : pixel.tint(item.getColorFromItemStack(stack, 0));
    }

    Faces faces(Block block, int metadata, int tint) {
        int red = 0, green = 0, blue = 0, count = 0;
        boolean opaque = true, uniform = true, lowContrast = true;
        boolean patterned = false;
        int outlinedSides = 0;
        String first = null;
        for (int side = 0; side < 6; side++) {
            IIcon icon = block.getIcon(side, metadata);
            PixelColor pixels = texture(icon, "blocks");
            if (pixels == null) {
                opaque = false;
                uniform = false;
                continue;
            }
            String name = icon.getIconName();
            if (first == null) {
                first = name;
            }
            uniform &= first.equals(name);
            opaque &= pixels.opaque;
            lowContrast &= pixels.lowContrast;
            patterned |= pixels.pattern != TexturePattern.SMOOTH;
            if (side >= FIRST_HORIZONTAL_SIDE && pixels.pattern == TexturePattern.OUTLINED) {
                outlinedSides++;
            }
            int rgb = pixels.tint(tint);
            red += ColorMath.channel(rgb, 0);
            green += ColorMath.channel(rgb, 1);
            blue += ColorMath.channel(rgb, 2);
            count++;
        }
        TexturePattern pattern = outlinedSides == 4 ? TexturePattern.OUTLINED
            : patterned ? TexturePattern.PATTERNED : TexturePattern.SMOOTH;
        return count == 0 ? null
            : new Faces(ColorMath.rgb(red / count, green / count, blue / count), opaque, uniform, lowContrast, pattern);
    }

    private PixelColor texture(IIcon icon, String folder) {
        if (icon == null) {
            return null;
        }
        ResourceLocation name = new ResourceLocation(icon.getIconName());
        ResourceLocation path = new ResourceLocation(
            name.getResourceDomain(),
            "textures/" + folder + "/" + name.getResourcePath() + ".png");
        if (!textures.containsKey(path)) {
            textures.put(path, readTexture(path));
        }
        return textures.get(path);
    }

    private PixelColor readTexture(ResourceLocation path) {
        try (InputStream stream = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(path)
            .getInputStream()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                return null;
            }
            long alpha = 0, red = 0, green = 0, blue = 0;
            boolean opaque = true;
            int width = image.getWidth();
            int height = image.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int weight = argb >>> 24;
                    alpha += weight;
                    red += ColorMath.channel(argb, 0) * weight;
                    green += ColorMath.channel(argb, 1) * weight;
                    blue += ColorMath.channel(argb, 2) * weight;
                    opaque &= weight == 255;
                }
            }
            if (alpha == 0) {
                return null;
            }
            int rgb = ColorMath.rgb((int) (red / alpha), (int) (green / alpha), (int) (blue / alpha));
            return new PixelColor(rgb, opaque, isLowContrast(image, rgb, alpha), TexturePattern.classify(image));
        } catch (IOException missingTexture) {
            return null;
        }
    }

    private static boolean isLowContrast(BufferedImage image, int averageRgb, long totalOpacity) {
        double[] average = ColorMath.lab(averageRgb);
        long contrastingOpacity = 0;
        long maxContrastingOpacity = totalOpacity / 16;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int weight = argb >>> 24;
                if (weight != 0 && ColorMath.distance(ColorMath.lab(argb), average) > CONTRAST_DISTANCE_SQUARED) {
                    contrastingOpacity += weight;
                    if (contrastingOpacity > maxContrastingOpacity) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static final class Faces {

        final int rgb;
        final boolean opaque;
        final boolean uniform;
        final boolean lowContrast;
        final TexturePattern pattern;

        Faces(int rgb, boolean opaque, boolean uniform, boolean lowContrast, TexturePattern pattern) {
            this.rgb = rgb;
            this.opaque = opaque;
            this.uniform = uniform;
            this.lowContrast = lowContrast;
            this.pattern = pattern;
        }
    }

    private static final class PixelColor {

        final int rgb;
        final boolean opaque;
        final boolean lowContrast;
        final TexturePattern pattern;

        PixelColor(int rgb, boolean opaque, boolean lowContrast, TexturePattern pattern) {
            this.rgb = rgb;
            this.opaque = opaque;
            this.lowContrast = lowContrast;
            this.pattern = pattern;
        }

        int tint(int tint) {
            return ColorMath.rgb(
                ColorMath.channel(rgb, 0) * ColorMath.channel(tint, 0) / 255,
                ColorMath.channel(rgb, 1) * ColorMath.channel(tint, 1) / 255,
                ColorMath.channel(rgb, 2) * ColorMath.channel(tint, 2) / 255);
        }
    }
}
