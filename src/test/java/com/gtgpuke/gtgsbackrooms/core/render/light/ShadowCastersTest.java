package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Les corps qui portent une ombre : jambes, torse et tete pour un corps debout, un cylindre pour le reste")
class ShadowCastersTest {

    private static final float EPSILON = 1.0e-5f;
    private static final int X = 0;
    private static final int BOTTOM = 1;
    private static final int Z = 2;
    private static final int RADIUS = 3;
    private static final int TOP = 4;

    private static float value(ShadowCasters casters, int cylinder, int field) {
        return casters.values()[cylinder * ShadowCasters.CYLINDER_VALUES + field];
    }

    @Test
    @DisplayName("Un corps debout empile trois cylindres sur toute sa hauteur, le torse au plus large")
    void standingBody() {
        ShadowCasters casters = new ShadowCasters();
        casters.add(3.0, 1.0, -2.0, 0.6, 1.8);
        assertEquals(3, casters.cylinders(), "Jambes, torse, tete.");
        assertEquals(1.0f, value(casters, 0, BOTTOM), EPSILON, "Les jambes partent des pieds.");
        assertEquals(value(casters, 0, TOP), value(casters, 1, BOTTOM), EPSILON, "Le torse suit les jambes.");
        assertEquals(value(casters, 1, TOP), value(casters, 2, BOTTOM), EPSILON, "La tete suit le torse.");
        assertEquals(2.8f, value(casters, 2, TOP), EPSILON, "La tete finit en haut du corps.");
        assertEquals(0.3f, value(casters, 1, RADIUS), EPSILON, "Le torse a la largeur du corps.");
        assertTrue(value(casters, 0, RADIUS) < value(casters, 2, RADIUS) && value(casters, 2, RADIUS) < value(casters, 1, RADIUS), "Jambes, puis tete, puis torse.");
        for (int i = 0; i < 3; i++) {
            assertEquals(3.0f, value(casters, i, X), EPSILON, "Au centre du corps.");
            assertEquals(-2.0f, value(casters, i, Z), EPSILON, "Au centre du corps.");
        }
    }

    @Test
    @DisplayName("Un corps bas ou large reste un seul cylindre")
    void lowBody() {
        ShadowCasters casters = new ShadowCasters();
        casters.add(0.0, 0.0, 0.0, 0.9, 0.9);
        assertEquals(1, casters.cylinders(), "Un seul.");
        assertEquals(0.45f, value(casters, 0, RADIUS), EPSILON, "Sa largeur.");
        assertEquals(0.9f, value(casters, 0, TOP), EPSILON, "Sa hauteur.");
    }

    @Test
    @DisplayName("La liste ne deborde pas : les cylindres en trop sont oublies, et elle se vide")
    void boundedList() {
        ShadowCasters casters = new ShadowCasters();
        for (int i = 0; i < 9; i++) {
            casters.add(i, 0.0, 0.0, 0.6, 1.8);
        }
        assertEquals(ShadowCasters.MAX_CYLINDERS, casters.cylinders(), "Pas plus que ce que declare le shader.");
        assertEquals(7.0f, value(casters, ShadowCasters.MAX_CYLINDERS - 1, X), EPSILON, "Le dernier garde est celui du huitieme corps.");
        casters.clear();
        assertEquals(0, casters.cylinders(), "Vide.");
    }
}
