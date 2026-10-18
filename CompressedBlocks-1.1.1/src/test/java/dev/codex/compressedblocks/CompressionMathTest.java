package dev.codex.compressedblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class CompressionMathTest {
    @Test
    void calculatesUnitsWithoutOverflow() {
        assertEquals(BigInteger.ONE, CompressionMath.unitsAtLevel(0));
        assertEquals(BigInteger.valueOf(9), CompressionMath.unitsAtLevel(1));
        assertEquals(BigInteger.valueOf(387_420_489), CompressionMath.unitsAtLevel(9));
    }

    @Test
    void rejectsNegativeLevels() {
        assertThrows(IllegalArgumentException.class, () -> CompressionMath.unitsAtLevel(-1));
    }

    @Test
    void formatsPlayerFacingTiersFromOneToNine() {
        assertEquals("1x", CompressionMath.tierLabel(1));
        assertEquals("9x", CompressionMath.tierLabel(9));
        assertThrows(IllegalArgumentException.class, () -> CompressionMath.tierLabel(0));
        assertThrows(IllegalArgumentException.class, () -> CompressionMath.tierLabel(10));
    }
}
