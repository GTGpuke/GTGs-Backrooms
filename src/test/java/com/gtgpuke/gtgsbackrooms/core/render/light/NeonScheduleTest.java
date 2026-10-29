package com.gtgpuke.gtgsbackrooms.core.render.light;

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

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

@DisplayName("Calendrier deterministe des neons")
class NeonScheduleTest {

    private static final int NEONS = 2000;
    private static final float EPSILON = 1.0e-6f;
    private static final int SEED = 42;
    private static final String VECTORS = "/neon_schedule_vectors.json";

    @Test
    @DisplayName("Java donne exactement les vecteurs communs avec le GLSL, tires d'une implementation independante")
    void matchesSharedVectors() throws IOException {
        JsonObject document = vectors();
        assertEquals(NeonSchedule.OFF, document.get("off").getAsFloat(), EPSILON, "Meme intensite d'un neon coupe.");
        assertEquals(NeonSchedule.seed(Identifier.of("gtgs_backrooms", "backrooms_level_0")), document.get("level0_seed").getAsInt(),
                "Meme graine pour le Level 0.");
        JsonArray vectors = document.getAsJsonArray("vectors");
        assertTrue(vectors.size() > 500, "Assez de vecteurs : " + vectors.size() + ".");
        int[] kinds = new int[3];
        for (int i = 0; i < vectors.size(); i++) {
            JsonObject vector = vectors.get(i).getAsJsonObject();
            int x = vector.get("x").getAsInt();
            int y = vector.get("y").getAsInt();
            int z = vector.get("z").getAsInt();
            int seed = vector.get("seed").getAsInt();
            int tick = vector.get("tick").getAsInt();
            float flickering = vector.get("flickering").getAsFloat();
            float dead = vector.get("dead").getAsFloat();
            int kind = NeonSchedule.kind(NeonSchedule.character(x, y, z, seed), flickering, dead);
            String expected = vector.get("kind").getAsString();
            assertEquals(expected, name(kind), "Caractere du vecteur " + i + " : " + vector + ".");
            kinds[kind]++;
            assertEquals(vector.get("intensity").getAsFloat(), NeonSchedule.intensity(x, y, z, seed, tick, flickering, dead), EPSILON,
                    "Intensite du vecteur " + i + " : " + vector + ".");
        }
        assertTrue(kinds[NeonSchedule.STEADY] > 0 && kinds[NeonSchedule.FLICKERING] > 0 && kinds[NeonSchedule.DEAD] > 0,
                "Les vecteurs couvrent les trois caracteres.");
    }

    @Test
    @DisplayName("Meme position et meme temps : meme etat, et sans part qui vacille ni morte, tout brille")
    void deterministic() {
        assertEquals(NeonSchedule.intensity(1993, 5, 2042, SEED, 1234, 0.3f, 0.1f), NeonSchedule.intensity(1993, 5, 2042, SEED, 1234, 0.3f, 0.1f),
                "Deterministe.");
        for (int i = 0; i < NEONS; i++) {
            assertEquals(1.0f, NeonSchedule.intensity(i * 7, 5, i * 3, SEED, i * 11, 0.0f, 0.0f), "Tout brille.");
        }
        long packed = BlockPos.asLong(-12, 64, 300);
        assertEquals(NeonSchedule.intensity(-12, 64, 300, SEED, 77, 0.5f, 0.2f), NeonSchedule.intensity(packed, SEED, 77, 0.5f, 0.2f),
                "Position empaquetee ou en trois entiers : pareil.");
    }

    @Test
    @DisplayName("Les parts qui vacillent et mortes sont tenues sur beaucoup de neons, les autres brillent")
    void sharesAreHonoured() {
        int dead = 0;
        int flickering = 0;
        for (int i = 0; i < NEONS; i++) {
            int character = NeonSchedule.character(i * 5 - 400, 5, 2000 + i * 3, SEED);
            assertEquals(NeonSchedule.STEADY, NeonSchedule.kind(character, 0.0f, 0.0f), "Sans parts, il brille.");
            int kind = NeonSchedule.kind(character, 0.25f, 0.25f);
            if (kind == NeonSchedule.DEAD) {
                dead++;
            } else if (kind == NeonSchedule.FLICKERING) {
                flickering++;
            }
        }
        assertTrue(dead > NEONS * 0.2 && dead < NEONS * 0.3, "Un quart de neons morts, a peu pres : " + dead + ".");
        assertTrue(flickering > NEONS * 0.2 && flickering < NEONS * 0.3, "Un quart qui vacillent, a peu pres : " + flickering + ".");
        assertEquals(NeonSchedule.OFF, NeonSchedule.intensity(3, 4, 5, SEED, 77, 0.0f, 1.0f), "Tous morts.");
    }

    @Test
    @DisplayName("Un neon qui vacille coupe par creneaux de quatre ticks, une fois sur huit en moyenne, jamais plus de cinq fois par seconde")
    void flickerIsSlottedAndRare() {
        int character = NeonSchedule.character(10, 5, 10, SEED);
        int cuts = 0;
        int transitions = 0;
        boolean previous = NeonSchedule.flickers(character, 0);
        for (int tick = 0; tick < 80_000; tick++) {
            boolean cut = NeonSchedule.flickers(character, tick);
            assertEquals(cut, NeonSchedule.flickers(character, tick - tick % NeonSchedule.SLOT_TICKS), "Constant dans son creneau.");
            if (cut) {
                cuts++;
            }
            if (cut != previous) {
                transitions++;
            }
            previous = cut;
        }
        assertTrue(cuts > 80_000 * 0.08 && cuts < 80_000 * 0.17, "Environ un huitieme du temps coupe : " + cuts + ".");
        assertTrue(transitions <= 80_000 / NeonSchedule.SLOT_TICKS, "Au plus une transition par creneau : " + transitions + ".");
        boolean seenOn = false;
        boolean seenOff = false;
        for (int tick = 0; tick < 4000; tick++) {
            float intensity = NeonSchedule.intensity(10, 5, 10, SEED, tick, 1.0f, 0.0f);
            seenOn |= intensity == 1.0f;
            seenOff |= intensity == NeonSchedule.OFF;
        }
        assertTrue(seenOn && seenOff, "Avec toute la part qui vacille, ce neon brille et coupe tour a tour.");
    }

    @Test
    @DisplayName("Deux neons voisins ne coupent pas ensemble, et deux dimensions n'ont pas le meme calendrier")
    void neighboursAndDimensionsAreDecorrelated() {
        int first = NeonSchedule.character(10, 5, 10, SEED);
        int second = NeonSchedule.character(11, 5, 10, SEED);
        int together = 0;
        int alone = 0;
        for (int tick = 0; tick < 80_000; tick += NeonSchedule.SLOT_TICKS) {
            boolean a = NeonSchedule.flickers(first, tick);
            boolean b = NeonSchedule.flickers(second, tick);
            if (a && b) {
                together++;
            } else if (a || b) {
                alone++;
            }
        }
        assertTrue(together * 4 < alone, "Rarement ensemble : " + together + " contre " + alone + ".");
        assertTrue(NeonSchedule.seed(Identifier.of("m", "a")) != NeonSchedule.seed(Identifier.of("m", "b")), "Deux graines.");
        assertTrue(NeonSchedule.character(1, 2, 3, NeonSchedule.seed(Identifier.of("m", "a")))
                != NeonSchedule.character(1, 2, 3, NeonSchedule.seed(Identifier.of("m", "b"))), "Deux caracteres pour un meme bloc.");
    }

    @Test
    @DisplayName("Le melange d'entiers disperse les positions voisines")
    void mixSpreadsNeighbours() {
        int a = NeonSchedule.character(0, 0, 0, 0);
        int b = NeonSchedule.character(1, 0, 0, 0);
        int c = NeonSchedule.character(0, 0, 1, 0);
        assertTrue(a != b && b != c && a != c, "Trois valeurs distinctes.");
        assertTrue(Integer.bitCount(a ^ b) > 8, "Beaucoup de bits changent entre voisins.");
        assertEquals(0, NeonSchedule.mix(0), "Zero reste zero : le melange n'ajoute pas de constante.");
    }

    private static String name(int kind) {
        return kind == NeonSchedule.STEADY ? "steady" : kind == NeonSchedule.FLICKERING ? "flickering" : "dead";
    }

    private static JsonObject vectors() throws IOException {
        try (InputStream input = NeonScheduleTest.class.getResourceAsStream(VECTORS)) {
            assertNotNull(input, "Vecteurs communs absents : " + VECTORS + ".");
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
