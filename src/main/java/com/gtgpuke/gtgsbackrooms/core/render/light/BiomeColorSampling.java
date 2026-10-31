package com.gtgpuke.gtgsbackrooms.core.render.light;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Couleur moyenne des biomes autour d'un point (ciel, brouillard), comme {@code CubicSampler.sampleColor} de vanilla :
 * meme grille de 6 x 6 x 6 points, memes poids, memes operations dans le meme ordre, donc le meme resultat au bit pres
 * ({@code BiomeColorSamplingTest} le compare a vanilla). Vanilla cree trois a quatre vecteurs par point, plus de huit cents
 * par appel, et l'appelle pour le ciel et le brouillard a chaque image et a chaque vue de portail ; ici, un seul vecteur,
 * le resultat.
 *
 * <p>Chaque point vaut la couleur de son biome ({@link Source}) ramenee a [0, 1], multipliee composante par composante par
 * l'echelle donnee : la correction de la dimension pour le brouillard ({@code DimensionEffects.adjustFogColor} de vanilla),
 * 1 pour le ciel. Une couleur imposee ({@code fixed}) remplace celle de chaque point : celle du profil d'un monde a look.
 */
public final class BiomeColorSampling {

    /** Poids des points de la grille le long d'un axe, selon la partie fractionnaire de la position (vanilla). */
    private static final double[] DENSITY_CURVE = new double[] {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};
    private static final int SIZE = 6;
    private static final int HALF = 2;

    /** Couleur d'un point de la grille des biomes, en entier RGB. */
    @FunctionalInterface
    public interface Source {

        /** Couleur RGB au point de bruit ({@code x}, {@code y}, {@code z}). */
        int rgb(int x, int y, int z);
    }

    private BiomeColorSampling() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Couleur moyenne autour de {@code pos} (coordonnees de la grille des biomes, comme le {@code pos} de vanilla).
     *
     * @param pos centre de l'echantillonnage
     * @param source couleur de chaque point
     * @param scaleRed echelle de la composante rouge de chaque point
     * @param scaleGreen echelle de la composante verte
     * @param scaleBlue echelle de la composante bleue
     * @param fixed couleur imposee a chaque point, ou {@code null}
     * @return la couleur moyenne, chaque composante dans [0, 1] pour des echelles dans [0, 1]
     */
    public static Vec3d sample(Vec3d pos, Source source, double scaleRed, double scaleGreen, double scaleBlue, @Nullable Vec3d fixed) {
        int baseX = MathHelper.floor(pos.getX());
        int baseY = MathHelper.floor(pos.getY());
        int baseZ = MathHelper.floor(pos.getZ());
        double fractionX = pos.getX() - baseX;
        double fractionY = pos.getY() - baseY;
        double fractionZ = pos.getZ() - baseZ;
        double weights = 0.0;
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        for (int i = 0; i < SIZE; i++) {
            double weightX = MathHelper.lerp(fractionX, DENSITY_CURVE[i + 1], DENSITY_CURVE[i]);
            int x = baseX - HALF + i;
            for (int j = 0; j < SIZE; j++) {
                double weightY = MathHelper.lerp(fractionY, DENSITY_CURVE[j + 1], DENSITY_CURVE[j]);
                int y = baseY - HALF + j;
                for (int k = 0; k < SIZE; k++) {
                    double weightZ = MathHelper.lerp(fractionZ, DENSITY_CURVE[k + 1], DENSITY_CURVE[k]);
                    int z = baseZ - HALF + k;
                    double weight = weightX * weightY * weightZ;
                    weights += weight;
                    double pointRed;
                    double pointGreen;
                    double pointBlue;
                    if (fixed != null) {
                        pointRed = fixed.x;
                        pointGreen = fixed.y;
                        pointBlue = fixed.z;
                    } else {
                        // Vec3d.unpackRgb, puis Vec3d.multiply par l'echelle : memes operations, sans vecteur.
                        int rgb = source.rgb(x, y, z);
                        pointRed = (rgb >> 16 & 0xFF) / 255.0 * scaleRed;
                        pointGreen = (rgb >> 8 & 0xFF) / 255.0 * scaleGreen;
                        pointBlue = (rgb & 0xFF) / 255.0 * scaleBlue;
                    }
                    // vec3d.add(point.multiply(weight)) : le produit d'abord, puis la somme.
                    red += pointRed * weight;
                    green += pointGreen * weight;
                    blue += pointBlue * weight;
                }
            }
        }
        double inverse = 1.0 / weights;
        return new Vec3d(red * inverse, green * inverse, blue * inverse);
    }
}
