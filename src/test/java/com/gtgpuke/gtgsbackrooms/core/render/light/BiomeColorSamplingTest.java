package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.Vec3d;

@DisplayName("Couleur des biomes : la meme que vanilla, au bit pres")
class BiomeColorSamplingTest {

    private static final int CASES = 4000;
    private static final double FAR = 3.0e6;

    /** Couleur d'un biome fictif en chaque point de la grille : pseudo-aleatoire, stable. */
    private static int rgb(int x, int y, int z) {
        int hash = x * 0x632BE5AB ^ y * 0x85157AF5 ^ z * 0x1B873593;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        return hash ^ hash >>> 12;
    }

    /** Une position de la grille : proche de l'origine, ou tres loin, negative comprise. */
    private static Vec3d position(Random random, int index) {
        double range = index % 2 == 0 ? 64.0 : FAR;
        return new Vec3d((random.nextDouble() - 0.5) * range, (random.nextDouble() - 0.5) * 128.0, (random.nextDouble() - 0.5) * range);
    }

    /** Les deux couleurs sont les memes au bit pres, composante par composante. */
    private static void requireSameBits(Vec3d expected, Vec3d actual, String what) {
        assertEquals(Double.doubleToRawLongBits(expected.x), Double.doubleToRawLongBits(actual.x), what + " : rouge " + expected + " / " + actual + ".");
        assertEquals(Double.doubleToRawLongBits(expected.y), Double.doubleToRawLongBits(actual.y), what + " : vert " + expected + " / " + actual + ".");
        assertEquals(Double.doubleToRawLongBits(expected.z), Double.doubleToRawLongBits(actual.z), what + " : bleu " + expected + " / " + actual + ".");
    }

    @Test
    @DisplayName("Brouillard de l'Overworld, du Nether et de l'End, avec leurs effets de dimension vanilla")
    void fogMatchesVanillaForEachDimension() {
        DimensionEffects overworld = new DimensionEffects.Overworld();
        DimensionEffects nether = new DimensionEffects.Nether();
        DimensionEffects end = new DimensionEffects.End();
        Random random = new Random(0L);
        for (int i = 0; i < CASES; i++) {
            Vec3d pos = position(random, i);
            float sun = i % 7 == 0 ? (float) (i % 2) : random.nextFloat();
            requireSameBits(CubicSampler.sampleColor(pos, (x, y, z) -> overworld.adjustFogColor(Vec3d.unpackRgb(rgb(x, y, z)), sun)),
                    BiomeColorSampling.sample(pos, BiomeColorSamplingTest::rgb, sun * 0.94F + 0.06F, sun * 0.94F + 0.06F, sun * 0.91F + 0.09F, null),
                    "Overworld en " + pos + ", soleil " + sun);
            requireSameBits(CubicSampler.sampleColor(pos, (x, y, z) -> nether.adjustFogColor(Vec3d.unpackRgb(rgb(x, y, z)), sun)),
                    BiomeColorSampling.sample(pos, BiomeColorSamplingTest::rgb, 1.0, 1.0, 1.0, null), "Nether en " + pos);
            requireSameBits(CubicSampler.sampleColor(pos, (x, y, z) -> end.adjustFogColor(Vec3d.unpackRgb(rgb(x, y, z)), sun)),
                    BiomeColorSampling.sample(pos, BiomeColorSamplingTest::rgb, 0.15F, 0.15F, 0.15F, null), "End en " + pos);
        }
    }

    @Test
    @DisplayName("Ciel sans correction, et couleur imposee a chaque point (brouillard d'un profil)")
    void skyAndFixedColorMatchVanilla() {
        Random random = new Random(1L);
        Vec3d fixed = new Vec3d(0.31, 0.27, 0.12);
        for (int i = 0; i < CASES; i++) {
            Vec3d pos = position(random, i);
            requireSameBits(CubicSampler.sampleColor(pos, (x, y, z) -> Vec3d.unpackRgb(rgb(x, y, z))),
                    BiomeColorSampling.sample(pos, BiomeColorSamplingTest::rgb, 1.0, 1.0, 1.0, null), "Ciel en " + pos);
            requireSameBits(CubicSampler.sampleColor(pos, (x, y, z) -> fixed), BiomeColorSampling.sample(pos, BiomeColorSamplingTest::rgb, 1.0, 1.0, 1.0, fixed),
                    "Couleur imposee en " + pos);
        }
    }
}
