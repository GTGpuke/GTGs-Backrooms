package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Selection des neons les plus proches")
class NeonSelectionTest {

    @Test
    @DisplayName("La liste garde les plus proches, tries, et refuse les plus lointains quand elle est pleine")
    void keepsTheNearestSorted() {
        NeonSelection selection = new NeonSelection(3);
        assertEquals(3, selection.capacity(), "Capacite.");
        selection.offer(10L, 9.0);
        selection.offer(11L, 1.0);
        selection.offer(12L, 4.0);
        assertEquals(3, selection.count(), "Pleine.");
        assertEquals(11L, selection.position(0), "Le plus proche en tete.");
        assertEquals(1.0, selection.squaredDistance(0), "Sa distance.");
        assertEquals(12L, selection.position(1), "Puis le suivant.");
        assertEquals(10L, selection.position(2), "Le plus lointain en queue.");
        selection.offer(13L, 25.0);
        assertEquals(10L, selection.position(2), "Plus lointain que la queue : refuse.");
        selection.offer(14L, 9.0);
        assertEquals(10L, selection.position(2), "A egalite avec la queue : refuse.");
        selection.offer(15L, 2.0);
        assertEquals(3, selection.count(), "Toujours pleine.");
        assertEquals(11L, selection.position(0), "Tete inchangee.");
        assertEquals(15L, selection.position(1), "Insere a sa place.");
        assertEquals(12L, selection.position(2), "L'ancienne queue est sortie.");
        selection.clear();
        assertEquals(0, selection.count(), "Videe.");
        selection.offer(16L, 0.0);
        assertEquals(1, selection.count(), "Repart de zero.");
        assertEquals(16L, selection.position(0), "Seul.");
    }
}
