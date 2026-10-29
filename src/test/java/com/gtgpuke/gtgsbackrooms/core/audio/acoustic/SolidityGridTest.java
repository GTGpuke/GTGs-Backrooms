package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

@DisplayName("Copie du monde par sections pour le calcul acoustique")
class SolidityGridTest {

    @Test
    @DisplayName("Une section posee est tenue, une section oubliee ne l'est plus, une colonne dechargee disparait")
    void sectionsAreHeldAndForgotten() {
        SolidityGrid grid = new SolidityGrid();
        long key = ChunkSectionPos.asLong(1, 2, 3);
        assertFalse(grid.has(key), "Rien au depart.");
        grid.setSection(key, SolidityGrid.EMPTY);
        assertTrue(grid.has(key), "Posee.");
        assertEquals(1, grid.size(), "Une section.");
        grid.setSection(key, null);
        assertFalse(grid.has(key), "Oubliee.");
        grid.setSection(ChunkSectionPos.asLong(4, 0, 4), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(4, 1, 4), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(5, 1, 4), SolidityGrid.EMPTY);
        grid.clearColumn(4, 4, 0, 4);
        assertFalse(grid.has(ChunkSectionPos.asLong(4, 1, 4)), "La colonne est partie.");
        assertTrue(grid.has(ChunkSectionPos.asLong(5, 1, 4)), "La colonne d'a cote reste.");
        grid.clear();
        assertEquals(0, grid.size(), "Plus rien.");
    }

    @Test
    @DisplayName("Les sections trop loin de l'auditeur, sur n'importe quel axe, sont oubliees ; les autres restent")
    void farSectionsAreSwept() {
        SolidityGrid grid = new SolidityGrid();
        grid.setSection(ChunkSectionPos.asLong(0, 0, 0), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(2, 0, 0), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(3, 0, 0), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(0, -3, 0), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(0, 0, 3), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(20, 0, 20), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(20, 3, 20), SolidityGrid.EMPTY);
        int[] anchorX = {0, 20};
        int[] anchorY = {0, 0};
        int[] anchorZ = {0, 20};
        assertEquals(4, grid.retainAround(anchorX, anchorY, anchorZ, 2, 2), "Quatre sections a plus de deux sections de chaque ancre sur un axe.");
        assertEquals(3, grid.size(), "Les deux proches de l'auditeur et celle a l'arrivee du portail restent.");
        assertTrue(grid.has(ChunkSectionPos.asLong(2, 0, 0)), "A la limite, gardee.");
        assertTrue(grid.has(ChunkSectionPos.asLong(20, 0, 20)), "Pres de la seconde ancre, gardee.");
        assertEquals(0, grid.retainAround(anchorX, anchorY, anchorZ, 2, 2), "Rien de plus a oublier.");
        assertEquals(1, grid.retainAround(anchorX, anchorY, anchorZ, 1, 2), "Sans la seconde ancre, sa section part.");
    }

    @Test
    @DisplayName("La fenetre tiree de la grille porte les sections copiees autour du centre, et elles seules")
    void windowIsFilledFromTheGrid() {
        SolidityGrid grid = new SolidityGrid();
        byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
        blocks[SolidityWindow.blockIndex(1, 2, 3)] = OcclusionTrace.FULL;
        grid.setSection(ChunkSectionPos.asLong(0, 0, 0), blocks);
        grid.setSection(ChunkSectionPos.asLong(SolidityWindow.RADIUS, 0, 0), SolidityGrid.EMPTY);
        grid.setSection(ChunkSectionPos.asLong(SolidityWindow.RADIUS + 1, 0, 0), SolidityGrid.EMPTY);
        SolidityWindow window = new SolidityWindow();
        grid.window(0, 0, 0, window);
        assertEquals(OcclusionTrace.FULL, window.at(1, 2, 3), "Le bloc de la section centrale.");
        assertTrue(window.known(SolidityWindow.RADIUS * 16, 0, 0), "La section au bord de la fenetre.");
        assertFalse(window.known((SolidityWindow.RADIUS + 1) * 16, 0, 0), "Celle d'apres est hors de la fenetre.");
        assertFalse(window.known(0, 16, 0), "Une section jamais copiee n'y est pas.");
    }
}
