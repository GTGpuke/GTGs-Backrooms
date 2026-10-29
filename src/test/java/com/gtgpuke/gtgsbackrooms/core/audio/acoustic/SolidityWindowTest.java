package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fenetre de sections copiees lue par le calcul acoustique")
class SolidityWindowTest {

    @Test
    @DisplayName("Une section posee dans la fenetre se lit bloc par bloc ; hors de la fenetre, rien n'est pose")
    void sectionsInsideTheWindowAreReadable() {
        SolidityWindow window = new SolidityWindow();
        window.center(10, 2, -5);
        byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
        blocks[SolidityWindow.blockIndex(3, 7, 11)] = OcclusionTrace.FULL;
        blocks[SolidityWindow.blockIndex(0, 0, 0)] = OcclusionTrace.PARTIAL;
        assertTrue(window.set(10 + SolidityWindow.RADIUS, 2 - SolidityWindow.RADIUS, -5, blocks), "Au bord de la fenetre, encore dedans.");
        int baseX = (10 + SolidityWindow.RADIUS) * 16;
        int baseY = (2 - SolidityWindow.RADIUS) * 16;
        int baseZ = -5 * 16;
        assertEquals(OcclusionTrace.FULL, window.at(baseX + 3, baseY + 7, baseZ + 11), "Le bloc plein, a sa place.");
        assertEquals(OcclusionTrace.PARTIAL, window.at(baseX, baseY, baseZ), "Le bloc partiel, au coin.");
        assertEquals(OcclusionTrace.AIR, window.at(baseX + 1, baseY, baseZ), "Ailleurs dans la section, de l'air.");
        assertTrue(window.known(baseX + 1, baseY, baseZ), "La section est connue.");
        assertFalse(window.known(baseX - 1, baseY, baseZ), "La section voisine, jamais posee, ne l'est pas.");
        assertEquals(OcclusionTrace.AIR, window.at(baseX - 1, baseY, baseZ), "Et se lit comme de l'air.");
        assertFalse(window.set(10 + SolidityWindow.RADIUS + 1, 2, -5, blocks), "Trop loin en x.");
        assertFalse(window.set(10 - SolidityWindow.RADIUS - 1, 2, -5, blocks), "Trop loin en -x.");
        assertFalse(window.set(10, 2 + SolidityWindow.RADIUS + 1, -5, blocks), "Trop loin en y.");
        assertFalse(window.set(10, 2 - SolidityWindow.RADIUS - 1, -5, blocks), "Trop loin en -y.");
        assertFalse(window.set(10, 2, -5 + SolidityWindow.RADIUS + 1, blocks), "Trop loin en z.");
        assertFalse(window.set(10, 2, -5 - SolidityWindow.RADIUS - 1, blocks), "Trop loin en -z.");
        assertFalse(window.known((10 + SolidityWindow.RADIUS + 1) * 16, 2 * 16, -5 * 16), "Hors de la fenetre en x : inconnu.");
        assertFalse(window.known(10 * 16, (2 - SolidityWindow.RADIUS - 1) * 16, -5 * 16), "Hors de la fenetre en y : inconnu.");
        assertFalse(window.known(10 * 16, 2 * 16, (-5 + SolidityWindow.RADIUS + 1) * 16), "Hors de la fenetre en z : inconnu.");
    }

    @Test
    @DisplayName("Recentrer la fenetre oublie ses sections")
    void recenteringForgetsSections() {
        SolidityWindow window = new SolidityWindow();
        window.center(0, 0, 0);
        assertTrue(window.set(0, 0, 0, SolidityGrid.EMPTY), "Posee au centre.");
        assertTrue(window.known(5, 5, 5), "Connue.");
        window.center(0, 0, 1);
        assertFalse(window.known(5, 5, 5), "Oubliee par le recentrage, meme si la section reste dans la fenetre.");
        assertEquals(SolidityWindow.SIDE, 2 * SolidityWindow.RADIUS + 1, "Un cube de sections centre sur une section.");
    }

    @Test
    @DisplayName("L'index d'un bloc dans sa section suit y, puis z, puis x, et ignore la section")
    void blockIndexIsLocal() {
        assertEquals(0, SolidityWindow.blockIndex(0, 0, 0), "Le coin.");
        assertEquals(1, SolidityWindow.blockIndex(1, 0, 0), "x en premier.");
        assertEquals(16, SolidityWindow.blockIndex(0, 0, 1), "Puis z.");
        assertEquals(256, SolidityWindow.blockIndex(0, 1, 0), "Puis y.");
        assertEquals(SolidityWindow.SECTION_BLOCKS - 1, SolidityWindow.blockIndex(15, 15, 15), "Le dernier bloc.");
        assertEquals(SolidityWindow.blockIndex(3, 4, 5), SolidityWindow.blockIndex(-13, 20, 37), "Les coordonnees du monde se ramenent a la section.");
    }
}
