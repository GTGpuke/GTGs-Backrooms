package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Direction percue d'un son bouche")
class PerceivedDirectionTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    @DisplayName("La source se deplace vers le detour, a la meme distance de l'auditeur, de la part demandee")
    void sourceTurnsTowardTheOpening() {
        double[] out = new double[3];
        PerceivedDirection.offset(10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 1.0f, out);
        double x = 10.0 + out[0];
        double z = out[2];
        assertEquals(10.0, Math.sqrt(x * x + z * z), 1.0e-6, "Meme distance.");
        assertEquals(x, z, 1.0e-6, "Toute la direction du detour (45 degres).");
        PerceivedDirection.offset(10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 0.5f, out);
        assertTrue(out[2] > 0.0 && out[2] < z, "A moitie : moins tourne.");
        PerceivedDirection.offset(10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, 0.0f, out);
        assertEquals(0.0, out[2], EPSILON, "Part nulle : rien.");
        PerceivedDirection.offset(10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0f, out);
        assertEquals(0.0, out[0], EPSILON, "Pas de detour : rien.");
        PerceivedDirection.offset(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0f, out);
        assertEquals(0.0, out[2], EPSILON, "Auditeur sur la source : rien.");
        PerceivedDirection.offset(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 1.0f, out);
        assertEquals(0.0, out[0], EPSILON, "Detour sur l'auditeur : rien.");
        PerceivedDirection.offset(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, -2.0, 0.0, 0.0, 0.5f, out);
        assertEquals(0.0, out[0], EPSILON, "Directions opposees a parts egales : rien a garder.");
        PerceivedDirection.offset(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0f, out);
        assertTrue(out[1] > 0.0, "Un detour vers le haut tourne aussi.");
        PerceivedDirection.offset(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, 1.0f, out);
        assertTrue(out[2] < 0.0, "Et de l'autre cote.");
    }

    @Test
    @DisplayName("La part suit ce que le detour gagne sur le chemin droit")
    void blendFollowsTheGain() {
        assertEquals(0.0f, PerceivedDirection.blend(0.5f, 0.3f, 0.3f, 0.85f), 1.0e-6f, "Rien gagne : rien.");
        assertEquals(0.0f, PerceivedDirection.blend(0.5f, 0.3f, 0.6f, 0.85f), 1.0e-6f, "Le detour perd : rien.");
        assertEquals(0.5f, PerceivedDirection.blend(0.5f, 0.85f, 0.0f, 0.85f), 1.0e-6f, "Tout gagne : la part du profil.");
        assertEquals(0.25f, PerceivedDirection.blend(0.5f, 0.6f, 0.175f, 0.85f), 1.0e-6f, "A moitie.");
        assertEquals(0.0f, PerceivedDirection.blend(0.5f, 0.6f, 0.0f, 0.0f), 1.0e-6f, "Sans plafond, aucune occlusion : rien.");
    }
}
