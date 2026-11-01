package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Textures de matiere des blocs du Level 0")
class BlockMaterialTexturesTest {

    private static final String WALLPAPER = "level_zero_wallpaper";
    private static final String WHITE_WALLPAPER = "level_zero_wallpaper_aged";
    private static final String[] CARPETS = {"level_zero_damp_carpet", "level_zero_damp_carpet_aged"};
    private static final String[] ALL = {WALLPAPER, WHITE_WALLPAPER, CARPETS[0], CARPETS[1]};
    /** Part des pixels les plus sombres de chaque cote : le coeur de l'encre et le coeur du papier. */
    private static final double CORE = 0.25;
    private static final int SMOOTH_INK = 80;
    private static final double NORMAL_ERROR = 0.02;
    private static final int MIN_GRAIN = 2;
    private static final double MIN_NOISE_LINK = 0.95;

    private static BufferedImage image(String name) throws IOException {
        try (InputStream in = BlockMaterialTexturesTest.class.getResourceAsStream("/assets/gtgs_backrooms/textures/block/" + name + ".png")) {
            assertNotNull(in, "Texture absente : " + name + ".");
            return ImageIO.read(in);
        }
    }

    private static double luminance(int rgb) {
        return 0.299 * (rgb >> 16 & 0xFF) + 0.587 * (rgb >> 8 & 0xFF) + 0.114 * (rgb & 0xFF);
    }

    private static int smoothness(BufferedImage properties, int x, int y) {
        return properties.getRGB(x, y) >> 16 & 0xFF;
    }

    @Test
    @DisplayName("Chaque matiere a la taille de sa texture, pour tomber pixel pour pixel sur elle")
    void sameSize() throws IOException {
        for (String name : ALL) {
            BufferedImage block = image(name);
            for (String suffix : new String[] {"_n", "_s"}) {
                BufferedImage material = image(name + suffix);
                assertEquals(block.getWidth(), material.getWidth(), name + suffix + ".");
                assertEquals(block.getHeight(), material.getHeight(), name + suffix + ".");
            }
        }
    }

    @Test
    @DisplayName("La moquette est mate partout : aucun pixel n'a de lissage")
    void carpetIsMatte() throws IOException {
        for (String name : CARPETS) {
            BufferedImage properties = image(name + "_s");
            for (int y = 0; y < properties.getHeight(); y++) {
                for (int x = 0; x < properties.getWidth(); x++) {
                    assertEquals(0, smoothness(properties, x, y), name + " en " + x + ", " + y + ".");
                }
            }
        }
    }

    @Test
    @DisplayName("La hauteur de la moquette est le bruit de sa texture : un pixel clair est une pointe, un pixel sombre un creux")
    void carpetHeightIsItsNoise() throws IOException {
        for (String name : CARPETS) {
            BufferedImage block = image(name);
            BufferedImage normals = image(name + "_n");
            int count = block.getWidth() * block.getHeight();
            double[] lum = new double[count];
            double[] height = new double[count];
            double meanLum = 0.0;
            double meanHeight = 0.0;
            for (int i = 0; i < count; i++) {
                lum[i] = luminance(block.getRGB(i % block.getWidth(), i / block.getWidth()));
                height[i] = normals.getRGB(i % block.getWidth(), i / block.getWidth()) >>> 24;
                meanLum += lum[i] / count;
                meanHeight += height[i] / count;
            }
            double cross = 0.0;
            double squareLum = 0.0;
            double squareHeight = 0.0;
            for (int i = 0; i < count; i++) {
                cross += (lum[i] - meanLum) * (height[i] - meanHeight);
                squareLum += (lum[i] - meanLum) * (lum[i] - meanLum);
                squareHeight += (height[i] - meanHeight) * (height[i] - meanHeight);
            }
            assertTrue(cross / Math.sqrt(squareLum * squareHeight) > MIN_NOISE_LINK, name + " : sa hauteur ne suit pas sa texture.");
        }
    }

    @Test
    @DisplayName("Le papier peint n'a pas d'epaisseur : le regard n'y entre pas")
    void wallpaperIsFlat() throws IOException {
        for (String name : new String[] {WALLPAPER, WHITE_WALLPAPER}) {
            BufferedImage normals = image(name + "_n");
            for (int y = 0; y < normals.getHeight(); y++) {
                for (int x = 0; x < normals.getWidth(); x++) {
                    assertEquals(0xFF, normals.getRGB(x, y) >>> 24, name + " en " + x + ", " + y + ".");
                }
            }
        }
    }

    @Test
    @DisplayName("Sur le papier peint, le sombre du motif est lisse et le fond qu'il recouvre reste mat")
    void onlyTheInkIsSmooth() throws IOException {
        for (String name : new String[] {WALLPAPER, WHITE_WALLPAPER}) {
            BufferedImage block = image(name);
            BufferedImage properties = image(name + "_s");
            int count = block.getWidth() * block.getHeight();
            double[] sorted = new double[count];
            for (int i = 0; i < count; i++) {
                sorted[i] = luminance(block.getRGB(i % block.getWidth(), i / block.getWidth()));
            }
            Arrays.sort(sorted);
            double dark = sorted[(int) (count * CORE)];
            double light = sorted[(int) (count * (1.0 - CORE))];
            for (int y = 0; y < block.getHeight(); y++) {
                for (int x = 0; x < block.getWidth(); x++) {
                    double lum = luminance(block.getRGB(x, y));
                    if (lum < dark) {
                        assertTrue(smoothness(properties, x, y) >= SMOOTH_INK, name + " : encre mate en " + x + ", " + y + ".");
                    } else if (lum > light) {
                        assertEquals(0, smoothness(properties, x, y), name + " : papier lisse en " + x + ", " + y + ".");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Chaque pixel de texture de normales est une vraie normale, tournee hors de la surface")
    void normalsAreNormals() throws IOException {
        for (String name : ALL) {
            BufferedImage normals = image(name + "_n");
            for (int y = 0; y < normals.getHeight(); y++) {
                for (int x = 0; x < normals.getWidth(); x++) {
                    int rgb = normals.getRGB(x, y);
                    double nx = ((rgb >> 16 & 0xFF) - 128) / 127.0;
                    double ny = ((rgb >> 8 & 0xFF) - 128) / 127.0;
                    double nz = ((rgb & 0xFF) - 128) / 127.0;
                    assertEquals(1.0, nx * nx + ny * ny + nz * nz, NORMAL_ERROR, name + " en " + x + ", " + y + ".");
                    assertTrue(nz > 0.0, name + " : une pente regarde dans le bloc.");
                }
            }
        }
    }

    @Test
    @DisplayName("Le papier est rugueux meme ou sa texture est unie : son grain ne vient pas de la luminance")
    void paperHasItsOwnGrain() throws IOException {
        for (String name : new String[] {WALLPAPER, WHITE_WALLPAPER}) {
            BufferedImage block = image(name);
            BufferedImage normals = image(name + "_n");
            int width = block.getWidth();
            int height = block.getHeight();
            int flat = 0;
            int grained = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int color = block.getRGB(x, y);
                    if (color == block.getRGB((x + 1) % width, y) && color == block.getRGB((x + width - 1) % width, y)
                            && color == block.getRGB(x, (y + 1) % height) && color == block.getRGB(x, (y + height - 1) % height)) {
                        flat++;
                        int rgb = normals.getRGB(x, y);
                        if (Math.abs((rgb >> 16 & 0xFF) - 128) + Math.abs((rgb >> 8 & 0xFF) - 128) >= MIN_GRAIN) {
                            grained++;
                        }
                    }
                }
            }
            assertTrue(flat > 0, name + " : aucun pixel uni.");
            assertTrue(grained * 2 > flat, name + " : " + grained + " pixels unis sur " + flat + " ont un grain.");
        }
    }
}
