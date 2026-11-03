package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Grille des texels de la lumiere")
class TexelGridTest {

    private static final double EPSILON = 1.0e-12;

    @Test
    @DisplayName("L'option donne 16, 32 ou 0, et le pas par defaut pour toute autre valeur")
    void steps() {
        assertEquals(0, TexelGrid.steps(0), "Lumiere lisse.");
        assertEquals(32, TexelGrid.steps(16), "L'ancienne taille de 16 vaut 32 : oui ou non.");
        assertEquals(32, TexelGrid.steps(32), "Pixels de 32.");
        assertEquals(TexelGrid.DEFAULT_STEPS, TexelGrid.steps(8), "Valeur inconnue.");
        assertEquals(TexelGrid.DEFAULT_STEPS, TexelGrid.steps(-1), "Valeur negative.");
        assertEquals(TexelGrid.DEFAULT_STEPS, TexelGrid.steps(64), "Valeur trop grande.");
    }

    @Test
    @DisplayName("Sans grille, le point reste ou il est")
    void smoothKeepsPosition() {
        double[] out = new double[3];
        TexelGrid.snap(0.123, -4.567, 8.9, 0.0, 1.0, 0.0, 0, out);
        assertArrayEquals(new double[] {0.123, -4.567, 8.9}, out, EPSILON, "Position exacte.");
    }

    @Test
    @DisplayName("Sur chacune des six faces, l'axe de la normale garde la position et les deux autres vont au centre du texel")
    void sixFaces() {
        double[] out = new double[3];
        double x = 3.01;
        double y = 64.49;
        double z = -7.7;
        double sx = (Math.floor(x * 32) + 0.5) / 32;
        double sy = (Math.floor(y * 32) + 0.5) / 32;
        double sz = (Math.floor(z * 32) + 0.5) / 32;
        for (int sign = -1; sign <= 1; sign += 2) {
            TexelGrid.snap(x, y, z, sign, 0.0, 0.0, 32, out);
            assertArrayEquals(new double[] {x, sy, sz}, out, EPSILON, "Face est ou ouest.");
            TexelGrid.snap(x, y, z, 0.0, sign, 0.0, 32, out);
            assertArrayEquals(new double[] {sx, y, sz}, out, EPSILON, "Sol ou plafond.");
            TexelGrid.snap(x, y, z, 0.0, 0.0, sign, 32, out);
            assertArrayEquals(new double[] {sx, sy, z}, out, EPSILON, "Face nord ou sud.");
        }
    }

    @Test
    @DisplayName("Le centre du texel tient sous zero et au pas de 16")
    void negativeAndSixteen() {
        double[] out = new double[3];
        TexelGrid.snap(-0.01, 0.99, 2.0, 1.0, 0.0, 0.0, 32, out);
        assertEquals(-0.01, out[0], EPSILON, "L'axe de la normale garde sa position, meme negative.");
        assertEquals(31.5 / 32, out[1], EPSILON, "Dernier texel du bloc.");
        assertEquals(2.0 + 0.5 / 32, out[2], EPSILON, "Un bord de texel exact est le debut du suivant.");
        TexelGrid.snap(1.0, -0.01, 0.2, 0.0, 0.0, -1.0, 16, out);
        assertEquals(1.0 + 0.5 / 16, out[0], EPSILON, "Pas de 16.");
        assertEquals(-0.5 / 16, out[1], EPSILON, "Sous zero, le texel d'avant.");
        assertEquals(0.2, out[2], EPSILON, "Face nord : z garde.");
    }

    @Test
    @DisplayName("Une normale en biais garde l'axe de sa plus grande composante, x puis y puis z a egalite, comme le shader")
    void slantedNormals() {
        double[] out = new double[3];
        TexelGrid.snap(0.3, 0.3, 0.3, 0.7, 0.7, 0.0, 32, out);
        assertEquals(0.3, out[0], EPSILON, "Egalite x et y : x garde.");
        TexelGrid.snap(0.3, 0.3, 0.3, 0.0, -0.7, 0.7, 32, out);
        assertEquals(0.3, out[1], EPSILON, "Egalite y et z : y garde.");
        TexelGrid.snap(0.3, 0.3, 0.3, 0.6, 0.6, 0.6, 32, out);
        assertEquals(0.3, out[0], EPSILON, "Egalite des trois : x garde.");
        TexelGrid.snap(0.3, 0.3, 0.3, 0.2, 0.3, -0.9, 32, out);
        assertEquals(0.3, out[2], EPSILON, "z dominant : z garde.");
        assertEquals(9.5 / 32, out[0], EPSILON, "x au centre du texel.");
    }
}
