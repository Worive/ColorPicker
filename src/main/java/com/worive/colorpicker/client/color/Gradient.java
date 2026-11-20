package com.worive.colorpicker.client.color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Gradient {

    public static final int SLOTS = 9;
    private static final int CANDIDATES = 48;
    private static final Logger LOG = LogManager.getLogger("ColorPicker");

    private final ItemStack[] inputs = new ItemStack[SLOTS];
    private final List<List<BlockColors.Entry>> alternatives = new ArrayList<>(SLOTS);
    private final int[] selected = new int[SLOTS];
    private final int[] manualRanks = new int[SLOTS];
    private boolean dirty = true;
    private int lastFilters;
    private long lastRevision = -1;

    public Gradient() {
        Arrays.fill(selected, -1);
        for (int i = 0; i < SLOTS; i++) {
            alternatives.add(Collections.emptyList());
        }
    }

    public ItemStack input(int i) {
        return inputs[i];
    }

    public ItemStack output(int i) {
        List<BlockColors.Entry> candidates = alternatives.get(i);
        return candidates.isEmpty() ? inputs[i] : candidates.get(rank(i)).stack;
    }

    public int rank(int i) {
        return selected[i];
    }

    public boolean cycle(int i, int direction) {
        int current = rank(i);
        int next = current + direction;
        if (current < 0 || next < 0
            || next >= alternatives.get(i)
                .size()) {
            return false;
        }
        selected[i] = next;
        manualRanks[i] = next;
        return true;
    }

    public boolean setInput(int i, ItemStack stack, BlockColors colors) {
        if (stack != null && sample(colors, stack) == null) {
            return false;
        }
        inputs[i] = stack == null ? null : stack.copy();
        if (inputs[i] != null) {
            inputs[i].stackSize = 1;
        }
        dirty = true;
        return true;
    }

    public void update(BlockColors colors, int filters) {
        if (!dirty && lastFilters == filters && lastRevision == colors.revision()) {
            return;
        }
        if (dirty) {
            Arrays.fill(manualRanks, -1);
        }
        for (int i = 0; i < SLOTS; i++) {
            alternatives.set(i, Collections.emptyList());
        }

        double[] from = null;
        int fromSlot = -1;
        double[] runnerUpCosts = new double[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            if (inputs[i] == null) {
                continue;
            }
            BlockColors.Entry entry = sample(colors, inputs[i]);
            double[] to = entry == null ? null : entry.lab;
            if (from != null && to != null) {
                for (int slot = fromSlot + 1; slot < i; slot++) {
                    double progress = (slot - fromSlot) / (double) (i - fromSlot);
                    double[] target = interpolate(from, to, progress);
                    List<BlockColors.Entry> candidates = colors.closest(target, filters, CANDIDATES);
                    alternatives.set(slot, candidates);
                    if (candidates.size() > 1) {
                        runnerUpCosts[slot] = ColorMath.distance(candidates.get(1).lab, target)
                            - ColorMath.distance(candidates.get(0).lab, target);
                    }
                }
            }
            from = to;
            fromSlot = i;
        }
        selectDefaults(runnerUpCosts);
        dirty = false;
        lastFilters = filters;
        lastRevision = colors.revision();
    }

    private void selectDefaults(double[] runnerUpCosts) {
        int[][] repeats = new int[SLOTS + 1][2];
        double[][] costs = new double[SLOTS + 1][2];
        int[][] nextChoices = new int[SLOTS][2];
        ItemStack[] nextOptions = { null };

        // Score each suffix so a runner-up can avoid repeats on either side of its slot.
        for (int i = SLOTS - 1; i >= 0; i--) {
            List<BlockColors.Entry> candidates = alternatives.get(i);
            selected[i] = Math.min(Math.max(manualRanks[i], 0), candidates.size() - 1);
            ItemStack[] options = manualRanks[i] < 0 && candidates.size() > 1
                ? new ItemStack[] { output(i), candidates.get(1).stack }
                : new ItemStack[] { output(i) };

            for (int choice = 0; choice < options.length; choice++) {
                repeats[i][choice] = Integer.MAX_VALUE;
                double cost = choice == 1 ? runnerUpCosts[i] : 0;
                for (int next = 0; next < nextOptions.length; next++) {
                    int repeatCount = repeats[i + 1][next] + (same(options[choice], nextOptions[next]) ? 1 : 0);
                    double totalCost = cost + costs[i + 1][next];
                    if (repeatCount < repeats[i][choice]
                        || (repeatCount == repeats[i][choice] && totalCost < costs[i][choice])) {
                        repeats[i][choice] = repeatCount;
                        costs[i][choice] = totalCost;
                        nextChoices[i][choice] = next;
                    }
                }
            }
            nextOptions = options;
        }

        // The first slot has a single option because only gaps between inputs are generated.
        int choice = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (choice == 1) {
                selected[i] = 1;
            }
            choice = nextChoices[i][choice];
        }
    }

    private static double[] interpolate(double[] from, double[] to, double progress) {
        return new double[] { from[0] + (to[0] - from[0]) * progress, from[1] + (to[1] - from[1]) * progress,
            from[2] + (to[2] - from[2]) * progress };
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static BlockColors.Entry sample(BlockColors colors, ItemStack stack) {
        try {
            return colors.sampleBlock(stack);
        } catch (RuntimeException failure) {
            LOG.debug("Could not sample gradient input", failure);
            return null;
        }
    }
}
