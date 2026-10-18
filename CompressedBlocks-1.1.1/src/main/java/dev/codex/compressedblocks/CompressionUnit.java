package dev.codex.compressedblocks;

import dev.codex.compressedblocks.token.TokenData;
import java.math.BigInteger;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * A normalized view of one inventory stack.
 *
 * <p>The base item always has an amount of one. The {@code stackAmount} is the
 * number of level-specific units in the inspected inventory stack. Level zero
 * represents an ordinary item; positive levels represent compressed tokens.
 */
public record CompressionUnit(TokenData data, int stackAmount) {
    public CompressionUnit {
        data = Objects.requireNonNull(data, "data");
        if (data.level() < 0) {
            throw new IllegalArgumentException("level must not be negative");
        }
        if (stackAmount < 1) {
            throw new IllegalArgumentException("stackAmount must be positive");
        }
    }

    public ItemStack baseItem() {
        return data.baseItem();
    }

    public int level() {
        return data.level();
    }

    public boolean compressed() {
        return level() > 0;
    }

    public BigInteger unitsPerItem() {
        return CompressionMath.unitsAtLevel(level());
    }

    public BigInteger unitCount() {
        return unitsPerItem().multiply(BigInteger.valueOf(stackAmount));
    }
}
