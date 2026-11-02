package com.gtgpuke.gtgsbackrooms.core.render.light;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * LUT d'etalonnage d'un look : pour chaque couleur d'une grille de {@value #SIZE} pas par composante, la couleur qu'en fait
 * le profil, desaturation, teinte, puis son etalonnage ({@link LookProfile.Grade}). Le shader du monde la lit dans une
 * texture 3D, interpolee entre les pas, et l'applique a la couleur finale de chaque pixel du look : toute la palette d'une
 * dimension tient dans cette table, la meme a tous les paliers de qualite. Une operation lineaire (desaturation, teinte,
 * pente, decalage) y est exacte entre les pas ; une puissance y est approchee.
 */
public final class LookLut {

    /** Pas par composante. */
    public static final int SIZE = 32;
    /** Composantes d'une couleur de la LUT. */
    public static final int CHANNELS = 3;
    /** Poids de la luminance, ceux que le look a toujours utilises. */
    private static final double LUMA_RED = 0.299;
    private static final double LUMA_GREEN = 0.587;
    private static final double LUMA_BLUE = 0.114;

    private LookLut() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Couleur etalonnee par un look.
     *
     * @param look profil du look
     * @param red composante rouge, de 0 a 1
     * @param green composante verte, de 0 a 1
     * @param blue composante bleue, de 0 a 1
     * @param out couleur etalonnee (trois composantes, jamais negatives), ecrite ici
     */
    public static void grade(LookProfile look, double red, double green, double blue, double[] out) {
        double luma = luma(red, green, blue);
        double r = red + (luma - red) * look.desaturation();
        double g = green + (luma - green) * look.desaturation();
        double b = blue + (luma - blue) * look.desaturation();
        r += (r * look.tintRed() - r) * look.tintStrength();
        g += (g * look.tintGreen() - g) * look.tintStrength();
        b += (b * look.tintBlue() - b) * look.tintStrength();
        LookProfile.Grade grade = look.grade();
        r = Math.pow(Math.max(r * grade.slope().x + grade.offset().x, 0.0), grade.power().x);
        g = Math.pow(Math.max(g * grade.slope().y + grade.offset().y, 0.0), grade.power().y);
        b = Math.pow(Math.max(b * grade.slope().z + grade.offset().z, 0.0), grade.power().z);
        double graded = luma(r, g, b);
        out[0] = Math.max(graded + (r - graded) * grade.saturation(), 0.0);
        out[1] = Math.max(graded + (g - graded) * grade.saturation(), 0.0);
        out[2] = Math.max(graded + (b - graded) * grade.saturation(), 0.0);
    }

    /**
     * Remplit la LUT d'un look : {@value #SIZE} au cube couleurs de {@value #CHANNELS} composantes, le rouge variant le plus
     * vite, puis le vert, puis le bleu (l'ordre d'une texture 3D). Au rechargement des profils seulement.
     *
     * @param look profil du look
     * @param out tableau a remplir
     * @param offset premier indice ecrit
     */
    public static void fill(LookProfile look, float[] out, int offset) {
        double[] color = new double[CHANNELS];
        double step = 1.0 / (SIZE - 1);
        int index = offset;
        for (int b = 0; b < SIZE; b++) {
            for (int g = 0; g < SIZE; g++) {
                for (int r = 0; r < SIZE; r++) {
                    grade(look, r * step, g * step, b * step, color);
                    out[index++] = (float) color[0];
                    out[index++] = (float) color[1];
                    out[index++] = (float) color[2];
                }
            }
        }
    }

    private static double luma(double red, double green, double blue) {
        return red * LUMA_RED + green * LUMA_GREEN + blue * LUMA_BLUE;
    }
}
