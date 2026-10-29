package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Courbes d'une reaction")
class EasingTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Chaque courbe va de 0 a 1, monte sans redescendre, et borne son entree")
    void curvesGoFromZeroToOne() {
        for (Easing easing : Easing.values()) {
            assertEquals(0.0f, easing.apply(0.0f), EPSILON, easing + " en 0.");
            assertEquals(1.0f, easing.apply(1.0f), EPSILON, easing + " en 1.");
            assertEquals(0.0f, easing.apply(-3.0f), EPSILON, easing + " borne en bas.");
            assertEquals(1.0f, easing.apply(7.0f), EPSILON, easing + " borne en haut.");
            float previous = 0.0f;
            for (int i = 1; i <= 100; i++) {
                float value = easing.apply(i / 100.0f);
                assertTrue(value >= previous - EPSILON, easing + " ne redescend pas.");
                previous = value;
            }
            assertSame(easing, Easing.of(easing.key()), "Retrouvee par son nom.");
        }
        assertNull(Easing.of("bounce"), "Un nom inconnu.");
    }

    @Test
    @DisplayName("Les courbes ont leur forme : lente ou rapide au depart, symetriques au milieu")
    void curvesHaveTheirShape() {
        assertEquals(0.5f, Easing.LINEAR.apply(0.5f), EPSILON, "Lineaire.");
        assertEquals(0.25f, Easing.IN_QUAD.apply(0.5f), EPSILON, "Carre.");
        assertEquals(0.75f, Easing.OUT_QUAD.apply(0.5f), EPSILON, "Carre inverse.");
        assertEquals(0.5f, Easing.IN_OUT_QUAD.apply(0.5f), EPSILON, "Milieu.");
        assertEquals(0.08f, Easing.IN_OUT_QUAD.apply(0.2f), EPSILON, "Lente au depart.");
        assertEquals(0.92f, Easing.IN_OUT_QUAD.apply(0.8f), EPSILON, "Lente a la fin.");
        assertEquals(0.125f, Easing.IN_CUBIC.apply(0.5f), EPSILON, "Cube.");
        assertEquals(0.875f, Easing.OUT_CUBIC.apply(0.5f), EPSILON, "Cube inverse.");
        assertEquals(0.5f, Easing.IN_OUT_SINE.apply(0.5f), EPSILON, "Sinus au milieu.");
    }
}
