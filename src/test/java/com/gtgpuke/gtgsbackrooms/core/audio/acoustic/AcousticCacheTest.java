package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Derniere acoustique connue par cellule")
class AcousticCacheTest {

    @Test
    @DisplayName("Une cellule evaluee garde son dernier resultat, fraiche un temps, puis a revoir")
    void cellsKeepTheirLastResult() {
        AcousticCache cache = new AcousticCache();
        long cell = AcousticCache.cellOf(5.5, 66.2, -3.7);
        assertNull(cache.get(cell), "Rien au depart.");
        assertFalse(cache.fresh(cell, 10L, 100), "Jamais evaluee : a evaluer.");
        AcousticResult first = new AcousticResult(6.0f, 0.1f, 0.0f, 0.0f);
        cache.put(cell, first, 10L);
        assertSame(first, cache.get(cell), "Le resultat pose.");
        assertTrue(cache.fresh(cell, 50L, 100), "Quarante ticks plus tard, encore fraiche.");
        assertFalse(cache.fresh(cell, 110L, 100), "Cent ticks plus tard, a revoir.");
        AcousticResult second = first.withOcclusion(0.5f);
        cache.put(cell, second, 120L);
        assertSame(second, cache.get(cell), "Remplace dans la meme entree.");
        assertTrue(cache.fresh(cell, 150L, 100), "Et la date suit.");
        assertEquals(1, cache.size(), "Une seule cellule.");
        cache.clear();
        assertEquals(0, cache.size(), "Videe.");
    }

    @Test
    @DisplayName("Les cellules font quatre blocs de cote, coordonnees negatives comprises, et l'occlusion se remplace sans toucher a l'espace")
    void cellsAreFourBlocksWide() {
        assertEquals(AcousticCache.cellOf(0.1, 0.1, 0.1), AcousticCache.cellOf(3.9, 3.9, 3.9), "Le meme cube de quatre.");
        assertNotEquals(AcousticCache.cellOf(3.9, 0.1, 0.1), AcousticCache.cellOf(4.1, 0.1, 0.1), "Le cube d'a cote.");
        assertEquals(AcousticCache.cellOf(-0.5, -0.5, -0.5), AcousticCache.cellOf(-3.5, -3.5, -3.5), "Les negatifs se rangent sans trou.");
        assertNotEquals(AcousticCache.cellOf(-0.5, 0.0, 0.0), AcousticCache.cellOf(0.5, 0.0, 0.0), "De part et d'autre de zero.");
        AcousticResult result = new AcousticResult(12.0f, 0.4f, 0.0f, 0.0f).withOcclusion(0.3f);
        assertEquals(new AcousticResult(12.0f, 0.4f, 0.0f, 0.3f), result, "Taille et ouverture gardees, occlusion posee.");
        assertEquals(AcousticCache.CELL, 1 << 2, "Quatre blocs.");
        assertTrue(new AcousticResult(12.0f, 0.4f, AcousticResult.MAX_UNKNOWN, 0.0f).reliable(), "Un quart de rayons perdus : encore fiable.");
        assertFalse(new AcousticResult(12.0f, 0.4f, 0.3f, 0.0f).reliable(), "Plus : le monde n'est pas assez copie.");
    }

    @Test
    @DisplayName("Au plafond, les cellules anciennes sont oubliees, puis toutes si cela ne suffit pas")
    void cellsAreBounded() {
        AcousticResult result = new AcousticResult(8.0f, 0.2f, 0.0f, 0.0f);
        AcousticCache cache = new AcousticCache();
        for (int cell = 0; cell < AcousticCache.MAX_CELLS; cell++) {
            cache.put(cell, result, cell < AcousticCache.MAX_CELLS / 2 ? 0L : AcousticCache.PRUNE_AGE_TICKS);
        }
        assertEquals(AcousticCache.MAX_CELLS, cache.size(), "Jusqu'au plafond, tout est garde.");
        cache.put(AcousticCache.MAX_CELLS, result, AcousticCache.PRUNE_AGE_TICKS + 1L);
        assertEquals(AcousticCache.MAX_CELLS / 2 + 1, cache.size(), "La moitie ancienne est oubliee, la nouvelle cellule gardee.");
        assertNull(cache.get(0L), "Une cellule ancienne n'est plus connue.");

        AcousticCache recent = new AcousticCache();
        for (int cell = 0; cell < AcousticCache.MAX_CELLS; cell++) {
            recent.put(cell, result, 10L);
        }
        recent.put(AcousticCache.MAX_CELLS, result, 11L);
        assertEquals(1, recent.size(), "Rien d'assez ancien : tout est oublie, sauf la nouvelle cellule.");
    }
}
