package com.worive.colorpicker.client.color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BlockColors implements IResourceManagerReloadListener {

    public static final int FULL = 1;
    public static final int SOLID = 2;
    public static final int OPAQUE = 4;
    public static final int SAME_TEXTURE = 8;
    public static final int NO_TILE_ENTITIES = 16;
    public static final int LOW_CONTRAST = 32;
    public static final int NO_FALLING = 64;
    public static final int ALL_FILTERS = FULL | SOLID
        | OPAQUE
        | SAME_TEXTURE
        | NO_TILE_ENTITIES
        | LOW_CONTRAST
        | NO_FALLING
        | TexturePattern.FILTERS;
    private static final Logger LOG = LogManager.getLogger("ColorPicker");
    private static final Comparator<Entry> ENTRY_ORDER = Comparator.comparing((Entry entry) -> entry.id)
        .thenComparingInt(entry -> entry.stack.getItemDamage());
    private static final Comparator<Match> MATCH_ORDER = Comparator.comparingDouble((Match match) -> match.distance)
        .thenComparing(match -> match.entry, ENTRY_ORDER)
        .thenComparingInt(match -> match.order);

    private final Map<Item, List<Entry>> byItem = new IdentityHashMap<>();
    private final TextureColors textures = new TextureColors();
    private final BlockShapes shapes = new BlockShapes();
    private List<Entry> entries;
    private long revision;

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        revision++;
        entries = null;
        byItem.clear();
        textures.clearCache();
    }

    public long revision() {
        return revision;
    }

    List<Entry> closest(double[] lab, int filter, int limit) {
        return closest(lab, filter, limit, Double.POSITIVE_INFINITY);
    }

    /** Returns the closest blocks satisfying all selected flags within the squared Oklab distance. */
    public List<Entry> closest(int rgb, int filter, int limit, double maxDistanceSquared) {
        return closest(ColorMath.lab(rgb), filter, limit, maxDistanceSquared);
    }

    private List<Entry> closest(double[] lab, int filter, int limit, double maxDistanceSquared) {
        PriorityQueue<Match> nearest = new PriorityQueue<>(limit, MATCH_ORDER.reversed());
        int order = 0;
        for (Entry entry : entries()) {
            if ((entry.flags & filter) != filter) {
                continue;
            }
            double distance = ColorMath.distance(entry.lab, lab);
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

    public Integer sample(ItemStack stack) {
        Entry block = sampleBlock(stack);
        if (block != null) {
            return block.rgb;
        }
        return textures.sampleItem(stack);
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

    public Integer sample(Block block, int metadata, int tint) {
        TextureColors.Faces faces = textures.faces(block, metadata, tint);
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
                LOG.debug("Could not enumerate {}", Item.itemRegistry.getNameForObject(item), failure);
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
                    LOG.debug("Could not sample {}", Item.itemRegistry.getNameForObject(item), failure);
                }
            }
            found.addAll(indexed);
            byItem.put(item, indexed);
        }
        entries = found;
        LOG.debug("Indexed {} block variants", entries.size());
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
        TextureColors.Faces faces = textures.faces(block, metadata, block.getRenderColor(metadata));
        if (faces == null) {
            return null;
        }
        int flags = 0;
        if (BlockShapes.hasCollision(block)) {
            flags |= SOLID;
        }
        if (shapes.fillsCube(block, metadata)) {
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

    public static final class Entry {

        /** Cached stack template. Copy it before modifying it. */
        public final ItemStack stack;
        public final int rgb;
        private final String id;
        private final int flags;
        final double[] lab;

        Entry(ItemStack stack, int rgb, int flags) {
            this.stack = stack;
            this.rgb = rgb;
            this.id = Item.itemRegistry.getNameForObject(stack.getItem());
            this.flags = flags;
            this.lab = ColorMath.lab(rgb);
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
