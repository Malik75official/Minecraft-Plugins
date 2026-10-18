package dev.codex.compressedblocks;

import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/** Simulates inventory inserts on clones so callers can commit them atomically. */
final class InventoryPlanner {
    private InventoryPlanner() {
    }

    static Optional<ItemStack[]> addAll(
            ItemStack[] original,
            List<ItemStack> additions,
            int inventoryMaxStackSize
    ) {
        ItemStack[] planned = cloneContents(original);
        for (ItemStack addition : additions) {
            if (!add(planned, addition, inventoryMaxStackSize)) {
                return Optional.empty();
            }
        }
        return Optional.of(planned);
    }

    static ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            clone[slot] = source[slot] == null ? null : source[slot].clone();
        }
        return clone;
    }

    private static boolean add(
            ItemStack[] contents,
            ItemStack addition,
            int inventoryMaxStackSize
    ) {
        if (addition == null || addition.isEmpty() || addition.getAmount() < 1) {
            return false;
        }

        int remaining = addition.getAmount();
        ItemStack template = addition.asOne();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack current = contents[slot];
            if (current == null || current.isEmpty() || !current.isSimilar(template)) {
                continue;
            }
            int maximum = Math.min(inventoryMaxStackSize, current.getMaxStackSize());
            int inserted = Math.min(remaining, Math.max(0, maximum - current.getAmount()));
            if (inserted > 0) {
                contents[slot] = current.asQuantity(current.getAmount() + inserted);
                remaining -= inserted;
            }
        }

        int maximum = Math.min(inventoryMaxStackSize, template.getMaxStackSize());
        if (maximum < 1) {
            return false;
        }
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack current = contents[slot];
            if (current != null && !current.isEmpty()) {
                continue;
            }
            int inserted = Math.min(remaining, maximum);
            contents[slot] = template.asQuantity(inserted);
            remaining -= inserted;
        }
        return remaining == 0;
    }
}
