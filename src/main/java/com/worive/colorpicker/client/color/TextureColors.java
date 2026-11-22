package com.worive.colorpicker.client.color;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

final class TextureColors {

    private static final int FIRST_HORIZONTAL_SIDE = 2;
    private static final double CONTRAST_DISTANCE_SQUARED = 0.18 * 0.18;
    private static final CompletableFuture<PixelColor> MISSING = CompletableFuture.completedFuture(null);

    private final Map<ResourceLocation, CompletableFuture<PixelColor>> textures = new HashMap<>();
    private final Executor decoder = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ColorPicker texture decoder");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    void clearCache() {
        cancelPending();
        textures.clear();
    }

    void cancelPending() {
        textures.values()
            .removeIf(texture -> !texture.isDone() && texture.cancel(false));
    }

    Integer sampleItem(ItemStack stack) {
        Item item = stack.getItem();
        String folder = item.getSpriteNumber() == 0 ? "blocks" : "items";
        IIcon icon = item.getIconIndex(stack);
        PixelColor pixel = texture(icon == null ? null : icon.getIconName(), folder, false).join();
        return pixel == null ? null : pixel.tint(item.getColorFromItemStack(stack, 0));
    }

    Faces faces(Block block, int metadata, int tint) {
        return faceTextures(block, metadata, false).colors(tint);
    }

    FaceTextures faceTextures(Block block, int metadata, boolean background) {
        String[] names = new String[6];
        List<CompletableFuture<PixelColor>> pixels = new ArrayList<>(names.length);
        for (int side = 0; side < names.length; side++) {
            IIcon icon = block.getIcon(side, metadata);
            names[side] = icon == null ? null : icon.getIconName();
            pixels.add(texture(names[side], "blocks", background));
        }
        return new FaceTextures(names, pixels);
    }

    private CompletableFuture<PixelColor> texture(String icon, String folder, boolean background) {
        if (icon == null) {
            return MISSING;
        }
        ResourceLocation name = new ResourceLocation(icon);
        ResourceLocation path = new ResourceLocation(
            name.getResourceDomain(),
            "textures/" + folder + "/" + name.getResourcePath() + ".png");
        CompletableFuture<PixelColor> cached = textures.get(path);
        if (cached != null) {
            // The eyedropper can sample while the decoder still has a queue.
            return background || cached.isDone() ? cached : CompletableFuture.completedFuture(readTexture(path));
        }
        cached = background ? decodeLater(path) : CompletableFuture.completedFuture(readTexture(path));
        textures.put(path, cached);
        return cached;
    }

    private CompletableFuture<PixelColor> decodeLater(ResourceLocation path) {
        byte[] png;
        try (InputStream stream = openTexture(path)) {
            // Resource packs can be reloaded or closed on the client thread.
            png = IOUtils.toByteArray(stream);
        } catch (IOException missingTexture) {
            return MISSING;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return readTexture(new ByteArrayInputStream(png));
            } catch (IOException invalidTexture) {
                return null;
            }
        }, decoder);
    }

    private static InputStream openTexture(ResourceLocation path) throws IOException {
        return Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(path)
            .getInputStream();
    }

    private static PixelColor readTexture(ResourceLocation path) {
        try (InputStream stream = openTexture(path)) {
            return readTexture(stream);
        } catch (IOException missingTexture) {
            return null;
        }
    }

    private static PixelColor readTexture(InputStream stream) throws IOException {
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
    }

    static final class FaceTextures {

        private final String[] names;
        private final List<CompletableFuture<PixelColor>> pixels;

        FaceTextures(String[] names, List<CompletableFuture<PixelColor>> pixels) {
            this.names = names;
            this.pixels = pixels;
        }

        boolean isReady() {
            for (CompletableFuture<PixelColor> pixel : pixels) {
                if (!pixel.isDone()) {
                    return false;
                }
            }
            return true;
        }

        Faces colors(int tint) {
            int red = 0, green = 0, blue = 0, count = 0;
            boolean opaque = true, uniform = true, lowContrast = true;
            boolean patterned = false;
            int outlinedSides = 0;
            String first = null;
            for (int side = 0; side < names.length; side++) {
                PixelColor pixel = pixels.get(side)
                    .join();
                if (pixel == null) {
                    opaque = false;
                    uniform = false;
                    continue;
                }
                String name = names[side];
                if (first == null) {
                    first = name;
                }
                uniform &= first.equals(name);
                opaque &= pixel.opaque;
                lowContrast &= pixel.lowContrast;
                patterned |= pixel.pattern != TexturePattern.SMOOTH;
                if (side >= FIRST_HORIZONTAL_SIDE && pixel.pattern == TexturePattern.OUTLINED) {
                    outlinedSides++;
                }
                int rgb = pixel.tint(tint);
                red += ColorMath.channel(rgb, 0);
                green += ColorMath.channel(rgb, 1);
                blue += ColorMath.channel(rgb, 2);
                count++;
            }
            TexturePattern pattern = outlinedSides == 4 ? TexturePattern.OUTLINED
                : patterned ? TexturePattern.PATTERNED : TexturePattern.SMOOTH;
            return count == 0 ? null
                : new Faces(
                    ColorMath.rgb(red / count, green / count, blue / count),
                    opaque,
                    uniform,
                    lowContrast,
                    pattern);
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
