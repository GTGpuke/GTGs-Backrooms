package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Motifs 3x3 du Level 0")
class LevelZeroCellMicroPatternTest {

    @Test
    @DisplayName("Les motifs pleins ouvrent ou ferment les neuf blocs")
    void fullPatterns() {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                assertTrue(LevelZeroCellMicroPattern.isOpen(LevelZeroCellMicroPattern.FULL_OPEN, x, z), "Ouvert en " + x + "," + z);
                assertFalse(LevelZeroCellMicroPattern.isOpen(LevelZeroCellMicroPattern.FULL_CLOSED, x, z), "Ferme en " + x + "," + z);
            }
        }
    }

    @Test
    @DisplayName("Un bit correspond a un seul bloc, en ordre ligne-major")
    void bitLayout() {
        assertEquals(1, LevelZeroCellMicroPattern.bit(0, 0), "Premier bloc.");
        assertEquals(1 << 4, LevelZeroCellMicroPattern.bit(1, 1), "Centre.");
        assertEquals(1 << 8, LevelZeroCellMicroPattern.bit(2, 2), "Dernier bloc.");
        int pattern = LevelZeroCellMicroPattern.bit(1, 1);
        assertTrue(LevelZeroCellMicroPattern.isOpen(pattern, 1, 1), "Le centre est ouvert.");
        assertFalse(LevelZeroCellMicroPattern.isOpen(pattern, 0, 1), "Le bord est ferme.");
    }
}
