package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

/**
 * L'eclairement cuit d'une surface, sur un entier : trois composantes de 10 bits (de 0 a {@value #MAX}) et un drapeau de
 * validite sur les 2 bits de poids fort, le format {@code GL_RGB10_A2} de la texture de zone. Une section cuite tient
 * {@value #VALUES} entiers : un par cellule et par face, a zero pour une face sans surface. Valeurs et validite vont
 * ensemble (couleur a zero sans surface) : interpolees par la texture puis divisees par la validite, seules les surfaces
 * voisines comptent, sans fuite par un bloc plein. Les faces sont nommees par la normale de la surface, qui sort du bloc
 * vers la cellule d'air.
 */
public final class LightCell {

    /** Faces d'une cellule, dans cet ordre : +X, -X, +Y, -Y, +Z, -Z. */
    public static final int FACES = 6;
    /** Cellules d'une section. */
    public static final int SECTION_CELLS = 4096;
    /** Entiers d'une section cuite. */
    public static final int VALUES = FACES * SECTION_CELLS;
    /** Eclairement maximal stocke ; au-dela, il est plafonne. */
    public static final float MAX = 4.0f;
    /** Normale de chaque face, sur X. */
    public static final int[] NORMAL_X = {1, -1, 0, 0, 0, 0};
    /** Normale de chaque face, sur Y. */
    public static final int[] NORMAL_Y = {0, 0, 1, -1, 0, 0};
    /** Normale de chaque face, sur Z. */
    public static final int[] NORMAL_Z = {0, 0, 0, 0, 1, -1};
    private static final int CHANNEL = 1023;
    private static final int GREEN_SHIFT = 10;
    private static final int BLUE_SHIFT = 20;
    private static final int VALID = 3 << 30;

    private LightCell() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Un eclairement valide, plafonne a {@value #MAX} et jamais negatif. */
    public static int pack(float red, float green, float blue) {
        return VALID | channel(red) | channel(green) << GREEN_SHIFT | channel(blue) << BLUE_SHIFT;
    }

    private static int channel(float value) {
        return Math.round(Math.clamp(value / MAX, 0.0f, 1.0f) * CHANNEL);
    }

    /** {@code true} si cette valeur est celle d'une surface. */
    public static boolean valid(int packed) {
        return packed != 0;
    }

    /** Eclairement rouge. */
    public static float red(int packed) {
        return (packed & CHANNEL) * MAX / CHANNEL;
    }

    /** Eclairement vert. */
    public static float green(int packed) {
        return (packed >>> GREEN_SHIFT & CHANNEL) * MAX / CHANNEL;
    }

    /** Eclairement bleu. */
    public static float blue(int packed) {
        return (packed >>> BLUE_SHIFT & CHANNEL) * MAX / CHANNEL;
    }

    /**
     * Indice d'une valeur dans une section cuite : une tranche de {@value #SECTION_CELLS} par face, X variant le plus vite,
     * puis Y, puis Z (l'ordre d'une texture 3D).
     *
     * @param x cellule dans la section, de 0 a 15
     * @param y cellule dans la section
     * @param z cellule dans la section
     * @param face face de la cellule
     */
    public static int index(int x, int y, int z, int face) {
        return face * SECTION_CELLS + (z << 8 | y << 4 | x);
    }

    /** Face de normale opposee. */
    public static int opposite(int face) {
        return face ^ 1;
    }
}
