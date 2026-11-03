package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Les corps qui portent une ombre : les cubes de leur modele, ou la boite de leur collision, dans leur enveloppe")
class ShadowCastersTest {

    private static final float EPSILON = 1.0e-5f;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int RADIUS = 3;
    private static final int TOP = 4;
    private static final int BOXES = 5;
    private static final int LONGEST = 4;
    private static final int SECOND = 8;
    private static final int THIRD = 12;

    private static float bounds(ShadowCasters casters, int body, int field) {
        return casters.values()[body * ShadowCasters.BODY_VALUES + field];
    }

    private static float box(ShadowCasters casters, int body, int box, int field) {
        return casters.values()[body * ShadowCasters.BODY_VALUES + ShadowCasters.BOUNDS_VALUES + box * ShadowCasters.BOX_VALUES + field];
    }

    /** Une boite droite, centree en ce point, de ces demi-cotes. */
    private static void cube(ShadowCasters casters, double x, double y, double z, double halfX, double halfY, double halfZ) {
        casters.box(x, y, z, halfX, 0.0, 0.0, 0.0, halfY, 0.0, 0.0, 0.0, halfZ);
    }

    /** Chaque coin de chaque boite tient dans l'enveloppe de son corps : le shader ecarte un rayon sur elle seule. */
    private static void assertContained(ShadowCasters casters, int body) {
        for (int k = 0; k < (int) bounds(casters, body, BOXES); k++) {
            for (int corner = 0; corner < 8; corner++) {
                double[] point = {box(casters, body, k, X), box(casters, body, k, Y), box(casters, body, k, Z)};
                for (int a = 0; a < 3; a++) {
                    double sign = (corner >> a & 1) == 0 ? -1.0 : 1.0;
                    for (int c = 0; c < 3; c++) {
                        point[c] += sign * box(casters, body, k, LONGEST + a * 4 + c);
                    }
                }
                double across = Math.hypot(point[0] - bounds(casters, body, X), point[2] - bounds(casters, body, Z));
                assertTrue(across <= bounds(casters, body, RADIUS) + EPSILON, "Le coin " + corner + " de la boite " + k + " tient dans le rayon.");
                assertTrue(point[1] >= bounds(casters, body, Y) - EPSILON && point[1] <= bounds(casters, body, TOP) + EPSILON,
                        "Le coin " + corner + " de la boite " + k + " tient dans la hauteur.");
            }
        }
    }

    @Test
    @DisplayName("Une boite garde son centre et ses demi-axes, le plus long d'abord ; l'enveloppe part des pieds et la contient")
    void oneBox() {
        ShadowCasters casters = new ShadowCasters();
        assertTrue(casters.begin(3.0, 1.0, -2.0), "De la place.");
        cube(casters, 3.0, 1.75, -2.0, 0.125, 0.375, 0.0625);
        casters.end();
        assertEquals(1, casters.bodies(), "Un corps.");
        assertEquals(1, (int) bounds(casters, 0, BOXES), "Une boite.");
        assertEquals(1.75f, box(casters, 0, 0, Y), EPSILON, "Son centre.");
        assertEquals(0.375f, box(casters, 0, 0, LONGEST + Y), EPSILON, "Le demi-axe le plus long d'abord.");
        assertEquals(0.125f, box(casters, 0, 0, SECOND + X), EPSILON, "Puis les autres, dans leur ordre.");
        assertEquals(0.0625f, box(casters, 0, 0, THIRD + Z), EPSILON, "Puis les autres, dans leur ordre.");
        assertEquals((float) Math.sqrt(0.125 * 0.0625), box(casters, 0, 0, RADIUS), EPSILON, "La sphere qui la resume a la taille de sa section.");
        assertEquals(3.0f, bounds(casters, 0, X), EPSILON, "L'enveloppe part des pieds.");
        assertEquals(-2.0f, bounds(casters, 0, Z), EPSILON, "L'enveloppe part des pieds.");
        assertEquals(1.375f, bounds(casters, 0, Y), EPSILON, "Elle commence au bas de la boite.");
        assertEquals(2.125f, bounds(casters, 0, TOP), EPSILON, "Elle finit en haut de la boite.");
        assertEquals(0.125f + 0.0625f, bounds(casters, 0, RADIUS), EPSILON, "Son rayon va au bord de la boite.");
        assertContained(casters, 0);
    }

    @Test
    @DisplayName("Le demi-axe le plus long passe en tete, ou qu'il soit, les autres gardent leur ordre")
    void longestFirst() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(0.0, 0.0, 0.0);
        cube(casters, 0.0, 1.0, 0.0, 0.5, 0.25, 0.125);
        cube(casters, 0.0, 2.0, 0.0, 0.125, 0.25, 0.5);
        cube(casters, 0.0, 3.0, 0.0, 0.125, 0.5, 0.25);
        casters.end();
        assertEquals(0.5f, box(casters, 0, 0, LONGEST + X), EPSILON, "Le premier demi-axe.");
        assertEquals(0.5f, box(casters, 0, 1, LONGEST + Z), EPSILON, "Le troisieme.");
        assertEquals(0.5f, box(casters, 0, 2, LONGEST + Y), EPSILON, "Le deuxieme.");
        assertEquals(0.125f, box(casters, 0, 2, SECOND + X), EPSILON, "Puis les autres, dans leur ordre.");
        assertEquals(0.25f, box(casters, 0, 2, THIRD + Z), EPSILON, "Puis les autres, dans leur ordre.");
    }

    @Test
    @DisplayName("Une boite tournee reste dans l'enveloppe, coins compris")
    void turnedBox() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(0.5, 0.0, 0.5);
        double c = Math.cos(0.7);
        double s = Math.sin(0.7);
        casters.box(1.0, 1.0, 0.25, 0.3 * c, 0.3 * s, 0.0, -0.1 * s, 0.1 * c, 0.0, 0.0, 0.0, 0.2);
        casters.box(0.0, 0.5, 0.75, 0.0, 0.05, 0.0, 0.2 * c, 0.0, 0.2 * s, -0.4 * s, 0.0, 0.4 * c);
        casters.end();
        assertEquals(2, (int) bounds(casters, 0, BOXES), "Deux boites.");
        assertContained(casters, 0);
    }

    @Test
    @DisplayName("Une boite qui tient dans une autre (calque de la peau, corps sous sa blouse) ne compte qu'une fois, la plus grande")
    void containedBoxes() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(0.0, 0.0, 0.0);
        cube(casters, 0.0, 1.0, 0.0, 0.25, 0.25, 0.25);
        cube(casters, 0.0, 1.0, 0.0, 0.25, 0.25, 0.25);
        cube(casters, 0.05, 1.0, 0.0, 0.1, 0.1, 0.1);
        casters.end();
        assertEquals(1, (int) bounds(casters, 0, BOXES), "Une copie exacte, une boite dedans : ignorees.");
        casters.begin(0.0, 0.0, 0.0);
        cube(casters, 0.0, 1.0, 0.0, 0.25, 0.25, 0.25);
        cube(casters, 5.0, 1.0, 0.0, 0.25, 0.25, 0.25);
        cube(casters, 0.0, 1.0, 0.0, 0.25, 0.25, 0.3);
        cube(casters, 9.0, 1.0, 0.0, 0.1, 0.1, 0.1);
        cube(casters, 9.0, 1.2, 0.0, 0.2, 0.4, 0.2);
        casters.box(5.0, 1.0, 0.0, 0.3, 0.3, 0.0, -0.3, 0.3, 0.0, 0.0, 0.0, 0.25);
        casters.end();
        assertEquals(3, (int) bounds(casters, 1, BOXES), "Chaque boite contenue cede sa place a celle qui la contient, premiere ou derniere.");
        assertEquals(9.0f, box(casters, 1, 0, X), EPSILON, "La derniere a pris la place de la premiere, retiree.");
        assertEquals(0.4f, box(casters, 1, 0, LONGEST + Y), EPSILON, "La plus grande des deux.");
        assertEquals(0.0f, box(casters, 1, 1, X), EPSILON, "Celle qui contenait la premiere.");
        assertEquals(0.3f, box(casters, 1, 1, LONGEST + Z), EPSILON, "Celle qui contenait la premiere.");
        assertEquals(5.0f, box(casters, 1, 2, X), EPSILON, "La boite tournee qui contient la deuxieme.");
        assertContained(casters, 1);
    }

    @Test
    @DisplayName("Une boite sans epaisseur est ignoree ; un corps sans boite n'est pas garde")
    void flatBoxes() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(0.0, 0.0, 0.0);
        cube(casters, 0.0, 1.0, 0.0, 0.25, 0.25, 0.25);
        casters.end();
        casters.begin(5.0, 0.0, 0.0);
        cube(casters, 5.0, 1.0, 0.0, 0.25, 0.0, 0.25);
        casters.end();
        assertEquals(1, casters.bodies(), "Un corps sans boite epaisse n'est pas garde.");
    }

    @Test
    @DisplayName("Corps plein, une boite plus grande prend la place de la plus petite, une plus petite est ignoree")
    void fullBody() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(0.0, 0.0, 0.0);
        for (int k = 0; k < ShadowCasters.MAX_BOXES; k++) {
            double half = k == 5 ? 0.01 : 0.1 + k * 0.01;
            cube(casters, k, 1.0, 0.0, half, half, half);
        }
        cube(casters, 50.0, 1.0, 0.0, 0.5, 0.5, 0.5);
        cube(casters, 100.0, 1.0, 0.0, 0.005, 0.005, 0.005);
        casters.end();
        assertEquals(ShadowCasters.MAX_BOXES, (int) bounds(casters, 0, BOXES), "Pas plus que ce que declare le shader.");
        assertEquals(50.0f, box(casters, 0, 5, X), EPSILON, "La grande boite a pris la place de la plus petite.");
        for (int k = 0; k < ShadowCasters.MAX_BOXES; k++) {
            assertTrue(box(casters, 0, k, X) != 100.0f, "La toute petite boite a ete ignoree.");
        }
    }

    @Test
    @DisplayName("Sans modele connu, la boite de collision, debout et tournee selon le corps")
    void collisionBox() {
        ShadowCasters casters = new ShadowCasters();
        casters.begin(1.0, 2.0, 3.0);
        casters.boxOf(0.6, 1.8, 90.0f);
        casters.end();
        assertEquals(2.9f, box(casters, 0, 0, Y), EPSILON, "Au milieu de sa hauteur.");
        assertEquals(0.9f, box(casters, 0, 0, LONGEST + Y), EPSILON, "Debout : la hauteur d'abord.");
        assertEquals(0.3f, box(casters, 0, 0, SECOND + Z), EPSILON, "Regardant l'ouest, sa largeur passe du nord au sud.");
        assertEquals(-0.3f, box(casters, 0, 0, THIRD + X), EPSILON, "Et sa profondeur d'est en ouest.");
        assertEquals(2.0f, bounds(casters, 0, Y), EPSILON, "De ses pieds.");
        assertEquals(3.8f, bounds(casters, 0, TOP), EPSILON, "A sa tete.");
        assertContained(casters, 0);
    }

    @Test
    @DisplayName("La liste ne deborde pas : les corps en trop sont oublies, chacun a sa place, et elle se vide")
    void boundedList() {
        ShadowCasters casters = new ShadowCasters();
        for (int i = 0; i < ShadowCasters.MAX_BODIES; i++) {
            assertTrue(casters.begin(i, 0.0, 0.0), "De la place.");
            casters.boxOf(0.6, 1.8, 0.0f);
            casters.end();
        }
        assertFalse(casters.begin(100.0, 0.0, 0.0), "Plus de place.");
        casters.boxOf(0.6, 1.8, 0.0f);
        casters.end();
        assertEquals(ShadowCasters.MAX_BODIES, casters.bodies(), "Pas plus que ce que declare le shader.");
        assertEquals(ShadowCasters.VALUES, casters.values().length, "La place de tous les corps.");
        for (int i = 0; i < ShadowCasters.MAX_BODIES; i++) {
            assertEquals(i, bounds(casters, i, X), EPSILON, "Chaque corps a sa place.");
            assertEquals(i, box(casters, i, 0, X), EPSILON, "Sa boite est la sienne.");
        }
        casters.clear();
        assertEquals(0, casters.bodies(), "Vide.");
        assertTrue(casters.begin(0.0, 0.0, 0.0), "De nouveau de la place.");
    }
}
