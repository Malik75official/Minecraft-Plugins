package dev.codex.compressedblocks;

import java.math.BigInteger;

public final class CompressionMath {
    public static final int RATIO = 9;
    public static final int MAX_TIER = 9;

    private CompressionMath() {
    }

    public static BigInteger unitsAtLevel(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative");
        }
        return BigInteger.valueOf(RATIO).pow(level);
    }

    /** Returns the player-facing tier name (for example {@code 1x} or {@code 9x}). */
    public static String tierLabel(int level) {
        if (level < 1 || level > MAX_TIER) {
            throw new IllegalArgumentException(
                    "level must be between 1 and " + MAX_TIER
            );
        }
        return level + "x";
    }
}
