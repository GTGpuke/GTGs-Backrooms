package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.Locale;

import net.minecraft.client.texture.NativeImage;

/** Mesures sur les images capturees par les tests du client : couleur moyenne d'une zone, ecart entre deux images. */
public final class Images {

    private Images() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Couleur moyenne (rouge, vert, bleu, de 0 a 255) d'un rectangle donne en fractions de l'image (0 a 1), un pixel sur
     * deux dans chaque sens.
     */
    public static double[] mean(NativeImage image, double left, double top, double right, double bottom) {
        int x0 = (int) (left * image.getWidth());
        int x1 = Math.max(x0 + 1, (int) (right * image.getWidth()));
        int y0 = (int) (top * image.getHeight());
        int y1 = Math.max(y0 + 1, (int) (bottom * image.getHeight()));
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        int n = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int abgr = image.getColor(x, y);
                r += abgr & 0xFF;
                g += (abgr >> 8) & 0xFF;
                b += (abgr >> 16) & 0xFF;
                n++;
            }
        }
        return new double[] {r / n, g / n, b / n};
    }

    /** Ecart moyen absolu par canal (0 a 255) entre deux images de meme taille, un pixel sur deux dans chaque sens. */
    public static double meanDifference(NativeImage a, NativeImage b) {
        double sum = 0.0;
        int n = 0;
        for (int y = 0; y < a.getHeight(); y += 2) {
            for (int x = 0; x < a.getWidth(); x += 2) {
                int p = a.getColor(x, y);
                int q = b.getColor(x, y);
                sum += Math.abs((p & 0xFF) - (q & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                        + Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
                n += 3;
            }
        }
        return sum / n;
    }

    /**
     * Une couleur du Level 0 : jaune ou jaune-gris (papier peint, moquette, brouillard du profil), rouge au moins egal au
     * vert et au-dessus du bleu, et pas noire. L'herbe (vert au-dessus du rouge) et le ciel (bleu au-dessus) n'y passent pas.
     */
    public static boolean yellowish(double[] rgb) {
        return rgb[0] >= rgb[1] && rgb[0] > rgb[2] && luminance(rgb) > 15.0;
    }

    /** Luminance moyenne (0 a 255) d'une couleur moyenne. */
    public static double luminance(double[] rgb) {
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    /** Une couleur moyenne lisible dans un message d'echec. */
    public static String describe(double[] rgb) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", rgb[0], rgb[1], rgb[2]);
    }
}
