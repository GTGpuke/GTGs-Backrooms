package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("LUT d'etalonnage d'un look")
class LookLutTest {

    private static final double EPSILON = 1.0e-12;
    private static final int VALUES = LookLut.SIZE * LookLut.SIZE * LookLut.SIZE * LookLut.CHANNELS;
    private static final String GRADE_LIBRARY = "assets/gtgs_backrooms/shaders/include/look_grade.glsl";
    private static final String POST_SHADER = "assets/gtgs_backrooms/shaders/core/look_post.fsh";
    private static final String GRADE_START = "uniform sampler3D gtgs_LookLut;";
    private static final String GRADE_END = "return texture(gtgs_LookLut, p / size).rgb;\n}";

    @Test
    @DisplayName("Le Level 0 n'a pas de filtre : un gris reste presque gris, une couleur garde la sienne, le noir et le blanc ne bougent pas")
    void levelZeroHasNoFilter() throws IOException {
        LookProfile look;
        try (InputStream in = LookLutTest.class.getClassLoader().getResourceAsStream("assets/gtgs_backrooms/look_profiles/level_0.json")) {
            assertNotNull(in, "Profil du Level 0.");
            look = LookProfile.fromJson(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        }
        double[] out = new double[3];
        LookLut.grade(look, 0.5, 0.5, 0.5, out);
        assertTrue((out[0] - out[2]) / out[0] < 0.1, "Un gris reste presque gris : " + Arrays.toString(out) + ".");
        LookLut.grade(look, 0.6, 0.1, 0.1, out);
        assertTrue(out[0] > out[1] * 4.0, "Un rouge reste rouge : " + Arrays.toString(out) + ".");
        LookLut.grade(look, 0.1, 0.2, 0.6, out);
        assertTrue(out[2] > out[1] * 2.0, "Un bleu reste bleu : " + Arrays.toString(out) + ".");
        LookLut.grade(look, 1.0, 1.0, 1.0, out);
        assertEquals(1.0, out[2], EPSILON, "Le blanc d'une dalle reste blanc.");
        LookLut.grade(look, 0.0, 0.0, 0.0, out);
        assertEquals(0.0, out[0], EPSILON, "Le noir reste noir.");
    }

    @Test
    @DisplayName("Le look vanilla et un etalonnage neutre laissent chaque couleur telle quelle")
    void neutralIsIdentity() {
        LookProfile neutral = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": {} }");
        double[] out = new double[3];
        for (LookProfile look : new LookProfile[] {LookProfile.VANILLA, neutral}) {
            for (double r = 0.0; r <= 1.0; r += 0.125) {
                for (double b = 0.0; b <= 1.0; b += 0.25) {
                    LookLut.grade(look, r, 0.4, b, out);
                    assertEquals(r, out[0], EPSILON, "Rouge inchange.");
                    assertEquals(0.4, out[1], EPSILON, "Vert inchange.");
                    assertEquals(b, out[2], EPSILON, "Bleu inchange.");
                }
            }
        }
    }

    @Test
    @DisplayName("Sans etalonnage, desaturation puis teinte, exactement le calcul que faisait le shader du monde")
    void desaturationThenTint() {
        LookProfile look = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [1.06, 1.0, 0.82, 0.35], \"desaturation\": 0.15 }");
        double red = 0.8;
        double green = 0.5;
        double blue = 0.2;
        double luma = red * 0.299 + green * 0.587 + blue * 0.114;
        double r = red + (luma - red) * 0.15;
        double g = green + (luma - green) * 0.15;
        double b = blue + (luma - blue) * 0.15;
        double[] out = new double[3];
        LookLut.grade(look, red, green, blue, out);
        assertEquals(r + (r * 1.06 - r) * 0.35, out[0], 1.0e-6, "Rouge : desature puis teinte.");
        assertEquals(g, out[1], 1.0e-6, "Vert : teinte neutre.");
        assertEquals(b + (b * 0.82 - b) * 0.35, out[2], 1.0e-6, "Bleu : desature puis teinte.");
    }

    @Test
    @DisplayName("L'etalonnage applique pente, decalage et puissance par composante, puis la saturation")
    void gradeSteps() {
        double[] out = new double[3];
        LookProfile curve = parse("{ \"dimension\": \"m:d\", \"id\": 1,"
                + " \"grade\": { \"slope\": [2, 1, 0.5], \"offset\": [0.1, -0.2, 0], \"power\": [1, 2, 0.5] } }");
        LookLut.grade(curve, 0.25, 0.5, 0.64, out);
        assertEquals(0.6, out[0], 1.0e-6, "Rouge : 0,25 x 2 + 0,1.");
        assertEquals(0.09, out[1], 1.0e-6, "Vert : (0,5 - 0,2) au carre.");
        assertEquals(Math.sqrt(0.32), out[2], 1.0e-6, "Bleu : racine de 0,64 x 0,5.");
        LookLut.grade(curve, 0.1, 0.1, 0.0, out);
        assertEquals(0.0, out[1], EPSILON, "Un decalage sous zero s'arrete a zero, sans puissance d'un negatif.");
        LookProfile grey = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"saturation\": 0 } }");
        LookLut.grade(grey, 0.9, 0.3, 0.1, out);
        double luma = 0.9 * 0.299 + 0.3 * 0.587 + 0.1 * 0.114;
        for (int i = 0; i < 3; i++) {
            assertEquals(luma, out[i], 1.0e-9, "Saturation nulle : le gris de meme luminance.");
        }
        LookProfile vivid = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"saturation\": 2 } }");
        LookLut.grade(vivid, 0.9, 0.3, 0.1, out);
        assertEquals(luma + (0.9 - luma) * 2.0, out[0], 1.0e-9, "Saturation doublee autour de la luminance.");
        assertEquals(0.0, out[2], EPSILON, "Une composante poussee sous zero s'arrete a zero.");
    }

    @Test
    @DisplayName("La table suit l'ordre d'une texture 3D, rouge le plus vite, et n'ecrit que sa plage")
    void fillOrder() {
        LookProfile look = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [1.2, 0.9, 0.7, 0.5], \"desaturation\": 0.3,"
                + " \"grade\": { \"slope\": [1.1, 1, 0.9], \"power\": [1.2, 1, 0.8], \"saturation\": 0.8 } }");
        int offset = 5;
        float[] table = new float[VALUES + 2 * offset];
        Arrays.fill(table, -1.0f);
        LookLut.fill(look, table, offset);
        for (int i = 0; i < offset; i++) {
            assertEquals(-1.0f, table[i], "Rien avant la plage.");
            assertEquals(-1.0f, table[offset + VALUES + i], "Rien apres la plage.");
        }
        double step = 1.0 / (LookLut.SIZE - 1);
        double[] out = new double[3];
        int[][] samples = {{0, 0, 0}, {31, 0, 0}, {0, 31, 0}, {0, 0, 31}, {3, 17, 29}, {31, 31, 31}};
        for (int[] sample : samples) {
            LookLut.grade(look, sample[0] * step, sample[1] * step, sample[2] * step, out);
            int index = offset + ((sample[2] * LookLut.SIZE + sample[1]) * LookLut.SIZE + sample[0]) * LookLut.CHANNELS;
            for (int c = 0; c < LookLut.CHANNELS; c++) {
                assertEquals((float) out[c], table[index + c], "Pas " + sample[0] + ", " + sample[1] + ", " + sample[2] + ", composante " + c + ".");
            }
        }
    }

    @Test
    @DisplayName("Le shader du post garde une copie a l'identique de la lecture de la LUT")
    void postShaderCopiesGrade() throws IOException {
        String library = grade(resource(GRADE_LIBRARY));
        String post = grade(resource(POST_SHADER));
        assertEquals(library, post, "Meme fonction gtgs_grade dans look_grade.glsl et look_post.fsh.");
    }

    private static String grade(String source) {
        String text = source.replace("\r\n", "\n");
        int start = text.indexOf(GRADE_START);
        int end = text.indexOf(GRADE_END);
        assertTrue(start >= 0 && end > start, "Fonction gtgs_grade trouvee.");
        return text.substring(start, end + GRADE_END.length());
    }

    private static LookProfile parse(String json) {
        return LookProfile.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = LookLutTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
