package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tremblement de camera d'un cri")
class CameraShakeTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Rien sans amplitude ; plafonne ; le meme a l'instant donne ; il bouge au fil du temps")
    void shakeIsBoundedAndDeterministic() {
        assertEquals(0.0f, CameraShake.yaw(3.3f, 0.0f), EPSILON, "Sans amplitude, pas de lacet.");
        assertEquals(0.0f, CameraShake.pitch(3.3f, 0.0f), EPSILON, "Ni de tangage.");
        float largestYaw = 0.0f;
        float largestPitch = 0.0f;
        boolean moved = false;
        float previous = CameraShake.yaw(0.0f, 1.0f);
        for (int i = 0; i < 2000; i++) {
            float seconds = i * 0.01f;
            float yaw = CameraShake.yaw(seconds, 1.0f);
            float pitch = CameraShake.pitch(seconds, 1.0f);
            largestYaw = Math.max(largestYaw, Math.abs(yaw));
            largestPitch = Math.max(largestPitch, Math.abs(pitch));
            moved |= Math.abs(yaw - previous) > EPSILON;
            previous = yaw;
            assertEquals(yaw, CameraShake.yaw(seconds, 1.0f), EPSILON, "Le meme a l'instant donne.");
        }
        assertTrue(largestYaw <= CameraShake.MAX_DEGREES + EPSILON, "Lacet plafonne : " + largestYaw + ".");
        assertTrue(largestPitch < largestYaw, "Le tangage tremble moins : " + largestPitch + ".");
        assertTrue(largestYaw > CameraShake.MAX_DEGREES * 0.5f, "Le tremblement se voit a l'amplitude 1 : " + largestYaw + ".");
        assertTrue(moved, "Il bouge au fil du temps.");
        assertEquals(CameraShake.yaw(1.7f, 1.0f) * 0.5f, CameraShake.yaw(1.7f, 0.5f), EPSILON, "Proportionnel a l'amplitude.");
    }
}
