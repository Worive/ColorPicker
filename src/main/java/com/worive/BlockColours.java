package com.worive;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemStack;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

final class BlockColours implements IResourceManagerReloadListener {

    static final int FULL = 1;
    static final int SOLID = 2;
    static final int OPAQUE = 4;
    static final int SAME_TEXTURE = 8;
    static final int NO_TILE_ENTITIES = 16;
    static final int LOW_CONTRAST = 32;
    static final int NO_FALLING = 64;
    static final int ALL_FILTERS = FULL | SOLID
        | OPAQUE
        | SAME_TEXTURE
        | NO_TILE_ENTITIES
        | LOW_CONTRAST
        | NO_FALLING
        | TexturePattern.FILTERS;
    private static final int FIRST_HORIZONTAL_SIDE = 2;
    private static final double CONTRAST_DISTANCE_SQUARED = 0.18 * 0.18;
    private static final Comparator<Entry> ENTRY_ORDER = Comparator.comparing((Entry entry) -> entry.id)
        .thenComparingInt(entry -> entry.stack.getItemDamage());
    private static final Comparator<Match> MATCH_ORDER = Comparator.comparingDouble((Match match) -> match.distance)
        .thenComparing(match -> match.entry, ENTRY_ORDER)
        .thenComparingInt(match -> match.order);

    private final Map<Item, List<Entry>> byItem = new IdentityHashMap<>();
    private final Map<ResourceLocation, PixelColour> textures = new HashMap<>();
    private ShapeWorld shapeWorld;
    private List<Entry> entries;
    private long revision;

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        revision++;
        entries = null;
        byItem.clear();
        textures.clear();
    }

    long revision() {
        return revision;
    }

    List<Entry> closest(double[] lab, int filter, int limit) {
        return closest(lab, filter, limit, Double.POSITIVE_INFINITY);
    }

    List<Entry> closest(double[] lab, int filter, int limit, double maxDistanceSquared) {
        PriorityQueue<Match> nearest = new PriorityQueue<>(limit, MATCH_ORDER.reversed());
        int order = 0;
        for (Entry entry : entries()) {
            if ((entry.flags & filter) != filter) {
                continue;
            }
            double distance = ColourMath.distance(entry.lab, lab);
            if (distance > maxDistanceSquared || (nearest.size() == limit && distance > nearest.peek().distance)) {
                continue;
            }
            nearest.add(new Match(entry, distance, order++));
            if (nearest.size() > limit) {
                nearest.remove();
            }
        }
        List<Entry> result = new ArrayList<>(nearest.size());
        while (!nearest.isEmpty()) {
            result.add(nearest.remove().entry);
        }
        Collections.reverse(result);
        return result;
    }

    private List<Entry> entries() {
        if (entries == null) {
            index();
        }
        return entries;
    }

    Integer sample(ItemStack stack) {
        Entry block = sampleBlock(stack);
        if (block != null) {
            return block.rgb;
        }
        Item item = stack.getItem();
        String folder = item.getSpriteNumber() == 0 ? "blocks" : "items";
        PixelColour pixel = texture(item.getIconIndex(stack), folder);
        return pixel == null ? null : pixel.tint(item.getColorFromItemStack(stack, 0));
    }

    Entry sampleBlock(ItemStack stack) {
        Item item = stack.getItem();
        List<Entry> variants = byItem.get(item);
        if (variants != null) {
            ItemStack sample = single(stack);
            for (Entry variant : variants) {
                if (ItemStack.areItemStacksEqual(variant.stack, sample)) {
                    return variant;
                }
            }
        }
        Block block = blockFor(item);
        if (block != null && block != Blocks.air) {
            return readBlock(single(stack), block, item.getMetadata(stack.getItemDamage()));
        }
        return null;
    }

    Integer sample(Block block, int metadata, int tint) {
        Faces faces = faces(block, metadata, tint);
        return faces == null ? null : faces.rgb;
    }

    private void index() {
        List<Entry> found = new ArrayList<>();
        for (Object value : Item.itemRegistry) {
            Item item = (Item) value;
            Block block = blockFor(item);
            if (block == null || block == Blocks.air) {
                continue;
            }
            List<ItemStack> variants = new ArrayList<>();
            try {
                for (CreativeTabs tab : item.getCreativeTabs()) {
                    if (tab != null) {
                        item.getSubItems(item, tab, variants);
                        if (variants.isEmpty()) {
                            variants.add(new ItemStack(item));
                        }
                        break;
                    }
                }
            } catch (RuntimeException failure) {
                ColorPickerMod.LOG.debug("Could not enumerate {}", Item.itemRegistry.getNameForObject(item), failure);
            }
            List<Entry> indexed = new ArrayList<>();
            for (ItemStack variant : variants) {
                if (variant == null || variant.getItem() != item) {
                    continue;
                }
                try {
                    ItemStack stack = single(variant);
                    Entry entry = readBlock(stack, block, item.getMetadata(stack.getItemDamage()));
                    if (entry != null) {
                        indexed.add(entry);
                    }
                } catch (RuntimeException failure) {
                    ColorPickerMod.LOG.debug("Could not sample {}", Item.itemRegistry.getNameForObject(item), failure);
                }
            }
            found.addAll(indexed);
            byItem.put(item, indexed);
        }
        entries = found;
        ColorPickerMod.LOG.debug("Indexed {} block variants", entries.size());
    }

    private static Block blockFor(Item item) {
        if (item instanceof ItemBlock) {
            return Block.getBlockFromItem(item);
        }
        if (item instanceof ItemReed || item instanceof ItemBed || item instanceof ItemDoor) {
            return Block.getBlockFromName(Item.itemRegistry.getNameForObject(item));
        }
        return null;
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private Entry readBlock(ItemStack stack, Block block, int metadata) {
        Faces faces = faces(block, metadata, block.getRenderColor(metadata));
        if (faces == null) {
            return null;
        }
        int flags = 0;
        if (hasCollision(block)) {
            flags |= SOLID;
        }
        if (fillsCube(block, metadata)) {
            flags |= FULL;
        }
        if (faces.opaque && block.getRenderBlockPass() == 0) {
            flags |= OPAQUE;
        }
        if (faces.uniform) {
            flags |= SAME_TEXTURE;
        }
        if (!block.hasTileEntity(metadata)) {
            flags |= NO_TILE_ENTITIES;
        }
        if (faces.lowContrast) {
            flags |= LOW_CONTRAST;
        }
        if (!(block instanceof BlockFalling)) {
            flags |= NO_FALLING;
        }
        return new Entry(stack, faces.rgb, flags | faces.pattern.bit);
    }

    private boolean fillsCube(Block block, int metadata) {
        // These blocks can report a full selection box for thin models on inventory metadata.
        if (block instanceof BlockWeb || block instanceof BlockDoublePlant
            || block instanceof BlockLadder
            || block instanceof BlockTorch) {
            return false;
        }
        float minX = (float) block.getBlockBoundsMinX();
        float minY = (float) block.getBlockBoundsMinY();
        float minZ = (float) block.getBlockBoundsMinZ();
        float maxX = (float) block.getBlockBoundsMaxX();
        float maxY = (float) block.getBlockBoundsMaxY();
        float maxZ = (float) block.getBlockBoundsMaxZ();
        try {
            if (shapeWorld == null) {
                shapeWorld = new ShapeWorld();
            }
            shapeWorld.block = block;
            shapeWorld.metadata = metadata;
            block.setBlockBoundsForItemRender();
            block.setBlockBoundsBasedOnState(shapeWorld, 0, ShapeWorld.Y, 0);
            if (!fillsCube(block.getSelectedBoundingBoxFromPool(shapeWorld, 0, ShapeWorld.Y, 0))) {
                return false;
            }
            // Multipart blocks such as stairs can have a full outline with gaps inside it.
            // The outline still counts for blocks such as soul sand, which has a shorter collision box.
            List<AxisAlignedBB> boxes = new ArrayList<>();
            block.addCollisionBoxesToList(shapeWorld, 0, ShapeWorld.Y, 0, ShapeWorld.CUBE.copy(), boxes, null);
            return boxes.size() < 2 || boxes.stream()
                .anyMatch(BlockColours::fillsCube);
        } catch (RuntimeException needsWorldState) {
            ColorPickerMod.LOG.debug(
                "Could not determine the shape of {}:{}",
                Block.blockRegistry.getNameForObject(block),
                metadata,
                needsWorldState);
            return false;
        } finally {
            block.setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static boolean fillsCube(AxisAlignedBB bounds) {
        return bounds != null && bounds.minX == 0
            && bounds.minY == ShapeWorld.Y
            && bounds.minZ == 0
            && bounds.maxX == 1
            && bounds.maxY == ShapeWorld.Y + 1
            && bounds.maxZ == 1;
    }

    private static boolean hasCollision(Block block) {
        try {
            AxisAlignedBB bounds = block.getCollisionBoundingBoxFromPool(null, 0, 0, 0);
            return bounds != null && bounds.maxX > bounds.minX
                && bounds.maxY > bounds.minY
                && bounds.maxZ > bounds.minZ;
        } catch (RuntimeException needsWorld) {
            return block.getMaterial()
                .blocksMovement();
        }
    }

    // The shape hooks can read metadata and neighbors even when the picker is outside a loaded world.
    private static final class ShapeWorld extends World {

        private static final int Y = 64;
        private static final AxisAlignedBB CUBE = AxisAlignedBB.getBoundingBox(0, Y, 0, 1, Y + 1, 1);

        private Block block;
        private int metadata;

        ShapeWorld() {
            super(
                new SaveHandlerMP(),
                "Block Shapes",
                new WorldProviderSurface(),
                new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT),
                new Profiler());
            isRemote = true;
            provider.registerWorld(this);
            chunkProvider = createChunkProvider();
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return x == 0 && y == Y && z == 0 ? block : Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return x == 0 && y == Y && z == 0 ? metadata : 0;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return new ChunkProviderClient(this);
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }

    private Faces faces(Block block, int metadata, int tint) {
        int red = 0, green = 0, blue = 0, count = 0;
        boolean opaque = true, uniform = true, lowContrast = true;
        boolean patterned = false;
        int outlinedSides = 0;
        String first = null;
        for (int side = 0; side < 6; side++) {
            IIcon icon = block.getIcon(side, metadata);
            PixelColour pixels = texture(icon, "blocks");
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
            red += ColourMath.channel(rgb, 0);
            green += ColourMath.channel(rgb, 1);
            blue += ColourMath.channel(rgb, 2);
            count++;
        }
        TexturePattern pattern = outlinedSides == 4 ? TexturePattern.OUTLINED
            : patterned ? TexturePattern.PATTERNED : TexturePattern.SMOOTH;
        return count == 0 ? null
            : new Faces(
                ColourMath.rgb(red / count, green / count, blue / count),
                opaque,
                uniform,
                lowContrast,
                pattern);
    }

    private PixelColour texture(IIcon icon, String folder) {
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

    private PixelColour readTexture(ResourceLocation path) {
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
                    red += ColourMath.channel(argb, 0) * weight;
                    green += ColourMath.channel(argb, 1) * weight;
                    blue += ColourMath.channel(argb, 2) * weight;
                    opaque &= weight == 255;
                }
            }
            if (alpha == 0) {
                return null;
            }
            int rgb = ColourMath.rgb((int) (red / alpha), (int) (green / alpha), (int) (blue / alpha));
            return new PixelColour(rgb, opaque, isLowContrast(image, rgb, alpha), TexturePattern.classify(image));
        } catch (IOException missingTexture) {
            return null;
        }
    }

    private static boolean isLowContrast(BufferedImage image, int averageRgb, long totalOpacity) {
        double[] average = ColourMath.lab(averageRgb);
        long contrastingOpacity = 0;
        long maxContrastingOpacity = totalOpacity / 16;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int weight = argb >>> 24;
                if (weight != 0 && ColourMath.distance(ColourMath.lab(argb), average) > CONTRAST_DISTANCE_SQUARED) {
                    contrastingOpacity += weight;
                    if (contrastingOpacity > maxContrastingOpacity) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static final class Entry {

        final ItemStack stack;
        final int rgb;
        final String id;
        final int flags;
        final double[] lab;

        Entry(ItemStack stack, int rgb, int flags) {
            this.stack = stack;
            this.rgb = rgb;
            this.id = Item.itemRegistry.getNameForObject(stack.getItem());
            this.flags = flags;
            this.lab = ColourMath.lab(rgb);
        }
    }

    private static final class Faces {

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

    private static final class PixelColour {

        final int rgb;
        final boolean opaque;
        final boolean lowContrast;
        final TexturePattern pattern;

        PixelColour(int rgb, boolean opaque, boolean lowContrast, TexturePattern pattern) {
            this.rgb = rgb;
            this.opaque = opaque;
            this.lowContrast = lowContrast;
            this.pattern = pattern;
        }

        int tint(int tint) {
            return ColourMath.rgb(
                ColourMath.channel(rgb, 0) * ColourMath.channel(tint, 0) / 255,
                ColourMath.channel(rgb, 1) * ColourMath.channel(tint, 1) / 255,
                ColourMath.channel(rgb, 2) * ColourMath.channel(tint, 2) / 255);
        }
    }

    private static final class Match {

        final Entry entry;
        final double distance;
        final int order;

        Match(Entry entry, double distance, int order) {
            this.entry = entry;
            this.distance = distance;
            this.order = order;
        }
    }
}
