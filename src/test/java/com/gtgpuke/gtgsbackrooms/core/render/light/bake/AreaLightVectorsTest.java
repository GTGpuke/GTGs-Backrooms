package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("La dalle suit les vecteurs communs, ecrits par un programme independant : le shader, en jeu, suit les memes")
class AreaLightVectorsTest {

    private static final int DOWN = 3;

    @Test
    @DisplayName("Chaque vecteur donne la valeur attendue, et assez de points recoivent de la lumiere")
    void sameAsVectors() throws IOException {
        JsonArray vectors;
        try (InputStream in = AreaLightVectorsTest.class.getResourceAsStream("/area_light_vectors.json")) {
            assertNotNull(in, "Les vecteurs sont versionnes avec les tests.");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                vectors = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("vectors");
            }
        }
        float[] rects = new float[AreaLight.VALUES];
        AreaLight.blockFace(rects, 0, 0, 10, 0, DOWN, 1.0f);
        int lit = 0;
        for (int i = 0; i < vectors.size(); i++) {
            JsonObject vector = vectors.get(i).getAsJsonObject();
            float expected = vector.get("received").getAsFloat();
            float value = AreaLight.received(rects, 0, DOWN, vector.get("x").getAsFloat(), vector.get("y").getAsFloat(), vector.get("z").getAsFloat(),
                    vector.get("nx").getAsFloat(), vector.get("ny").getAsFloat(), vector.get("nz").getAsFloat(), vector.get("reach").getAsFloat(), vector.get("cone").getAsFloat());
            assertEquals(expected, value, 2.0e-5f, "Vecteur " + i + ".");
            lit += expected > 0.0f ? 1 : 0;
        }
        assertEquals(64, vectors.size(), "Autant qu'un dessin de test en porte.");
        assertTrue(lit >= 12, "Assez de points eclaires : " + lit + ".");
    }
}
