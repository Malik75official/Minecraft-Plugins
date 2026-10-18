package dev.codex.compressedblocks.token;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public record TokenData(ItemStack baseItem, int level, byte[] payload) {
    public TokenData {
        baseItem = baseItem.asOne();
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public ItemStack baseItem() {
        return baseItem.clone();
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
