package com.gtgpuke.gtgsbackrooms.core.render.light;

/**
 * Grille des texels d'un bloc, sur laquelle se calcule la lumiere : un point d'une face est ramene au centre de son
 * texel ({@code floor(p * N) / N + 0.5 / N}) sur les deux axes de la face, l'axe de sa normale gardant la position. La
 * lumiere suit ainsi les pixels des textures (ombres et eclairage pixelises). Jumeau de {@code gtgs_lightPosition} de
 * {@code look.glsl}, que les tests du client comparent.
 */
public final class TexelGrid {

    /** Pas par bloc par defaut, celui des textures du mod (32 x 32). */
    public static final int DEFAULT_STEPS = 32;
    private static final int SMALL_STEPS = 16;

    private TexelGrid() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Pas de la grille demandes par l'option de la taille des pixels d'ombre.
     *
     * @param option valeur de l'option : 16, 32, ou 0 pour la position exacte
     * @return 16, 32 ou 0 ; toute autre valeur donne le pas par defaut
     */
    public static int steps(int option) {
        return option == 0 || option == SMALL_STEPS || option == DEFAULT_STEPS ? option : DEFAULT_STEPS;
    }

    /**
     * Centre du texel d'un point d'une face.
     *
     * @param x position, relative a un coin de bloc entier
     * @param y position
     * @param z position
     * @param normalX normale de la face (l'axe de sa plus grande composante garde la position)
     * @param normalY normale
     * @param normalZ normale
     * @param steps pas de la grille par bloc, 0 pour la position exacte
     * @param out centre du texel, ecrit ici
     */
    public static void snap(double x, double y, double z, double normalX, double normalY, double normalZ, int steps, double[] out) {
        if (steps <= 0) {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            return;
        }
        double ax = Math.abs(normalX);
        double ay = Math.abs(normalY);
        double az = Math.abs(normalZ);
        boolean keepX = ax >= ay && ax >= az;
        boolean keepY = !keepX && ay >= az;
        boolean keepZ = !keepX && !keepY;
        out[0] = keepX ? x : center(x, steps);
        out[1] = keepY ? y : center(y, steps);
        out[2] = keepZ ? z : center(z, steps);
    }

    private static double center(double value, int steps) {
        return (Math.floor(value * steps) + 0.5) / steps;
    }
}
