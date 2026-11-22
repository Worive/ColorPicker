package com.worive.colorpicker.client.color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
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
    private static final int PENDING_VARIANT_LIMIT = 64;

    private final Map<Item, List<Entry>> byItem = new IdentityHashMap<>();
    private final TextureColors textures = new TextureColors();
    private final BlockShapes shapes = new BlockShapes();
    private final List<Entry> entries = new ArrayList<>();
    private final Deque<PendingVariant> pending = new ArrayDeque<>();
    private Iterator<?> items;
    private Iterator<ItemStack> variants = Collections.emptyIterator();
    private Item indexingItem;
    private Block indexingBlock;
    private boolean indexed;
    private long revision;

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        resetEntries();
        textures.clearCache();
    }

    /** Invalidates world-dependent variants and keeps decoded textures from the active resource pack. */
    public void resetWorldIndex() {
        resetEntries();
        textures.cancelPending();
    }

    private void resetEntries() {
        revision++;
        indexed = false;
        items = null;
        variants = Collections.emptyIterator();
        indexingItem = null;
        indexingBlock = null;
        entries.clear();
        pending.clear();
        byItem.clear();
    }

    public long revision() {
        return revision;
    }

    public boolean isIndexed() {
        return indexed;
    }

    /** Advances the index on the client thread, checking the time allowance in nanoseconds between steps. */
    public void advanceIndex(long budgetNanos) {
        if (indexed) {
            return;
        }
        if (items == null) {
            items = Item.itemRegistry.iterator();
        }
        long start = System.nanoTime();
        do {
            if (!indexNext()) {
                return;
            }
        } while (System.nanoTime() - start < budgetNanos);
    }

    List<Entry> closest(double[] lab, int filter, int limit) {
        return closest(lab, filter, limit, Double.POSITIVE_INFINITY);
    }

    /** Returns matching blocks ordered by squared Oklab distance, or an empty list while indexing. */
    public List<Entry> closest(int rgb, int filter, int limit, double maxDistanceSquared) {
        return closest(ColorMath.lab(rgb), filter, limit, maxDistanceSquared);
    }

    private List<Entry> closest(double[] lab, int filter, int limit, double maxDistanceSquared) {
        if (!indexed) {
            return Collections.emptyList();
        }
        PriorityQueue<Match> nearest = new PriorityQueue<>(limit, MATCH_ORDER.reversed());
        int order = 0;
        for (Entry entry : entries) {
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

    private boolean indexNext() {
        PendingVariant next = pending.peekFirst();
        if (next != null && next.faces.isReady()) {
            pending.removeFirst();
            try {
                Entry entry = readBlock(next.stack, next.block, next.metadata, next.faces.colors(next.tint));
                if (entry != null) {
                    entries.add(entry);
                    byItem.computeIfAbsent(next.stack.getItem(), item -> new ArrayList<>())
                        .add(entry);
                }
            } catch (RuntimeException failure) {
                LOG.debug("Could not sample {}", Item.itemRegistry.getNameForObject(next.stack.getItem()), failure);
            }
            return true;
        }
        if (pending.size() >= PENDING_VARIANT_LIMIT) {
            return false;
        }
        if (variants.hasNext()) {
            ItemStack variant = variants.next();
            if (variant != null && variant.getItem() == indexingItem) {
                try {
                    ItemStack stack = single(variant);
                    int metadata = indexingItem.getMetadata(stack.getItemDamage());
                    int tint = indexingBlock.getRenderColor(metadata);
                    pending.addLast(
                        new PendingVariant(
                            stack,
                            indexingBlock,
                            metadata,
                            tint,
                            textures.faceTextures(indexingBlock, metadata, true)));
                } catch (RuntimeException failure) {
                    LOG.debug("Could not sample {}", Item.itemRegistry.getNameForObject(indexingItem), failure);
                }
            }
            return true;
        }
        if (!items.hasNext()) {
            if (next != null) {
                return false;
            }
            indexed = true;
            items = null;
            variants = Collections.emptyIterator();
            indexingItem = null;
            indexingBlock = null;
            revision++;
            LOG.debug("Indexed {} block variants", entries.size());
            return false;
        }
        indexingItem = (Item) items.next();
        indexingBlock = blockFor(indexingItem);
        if (indexingBlock == null || indexingBlock == Blocks.air) {
            return true;
        }
        List<ItemStack> stacks = new ArrayList<>();
        try {
            for (CreativeTabs tab : indexingItem.getCreativeTabs()) {
                if (tab != null) {
                    indexingItem.getSubItems(indexingItem, tab, stacks);
                    if (stacks.isEmpty()) {
                        stacks.add(new ItemStack(indexingItem));
                    }
                    break;
                }
            }
        } catch (RuntimeException failure) {
            LOG.debug("Could not enumerate {}", Item.itemRegistry.getNameForObject(indexingItem), failure);
        }
        variants = stacks.iterator();
        return true;
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
        return readBlock(stack, block, metadata, textures.faces(block, metadata, block.getRenderColor(metadata)));
    }

    private Entry readBlock(ItemStack stack, Block block, int metadata, TextureColors.Faces faces) {
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

    private static final class PendingVariant {

        final ItemStack stack;
        final Block block;
        final int metadata;
        final int tint;
        final TextureColors.FaceTextures faces;

        PendingVariant(ItemStack stack, Block block, int metadata, int tint, TextureColors.FaceTextures faces) {
            this.stack = stack;
            this.block = block;
            this.metadata = metadata;
            this.tint = tint;
            this.faces = faces;
        }
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
