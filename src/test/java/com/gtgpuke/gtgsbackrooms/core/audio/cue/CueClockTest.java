package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Horloge commune du son et de l'image")
class CueClockTest {

    private static final float EPSILON = 1.0e-4f;

    @Test
    @DisplayName("Le temps vient du temps du monde et de la fraction de tick, et repart de zero sans perdre sa precision")
    void timeFollowsTheWorld() {
        assertEquals(0.0f, CueClock.seconds(0L, 0.0f), EPSILON, "Le debut du monde.");
        assertEquals(1.025f, CueClock.seconds(20L, 0.5f), EPSILON, "Vingt ticks et demi : une seconde et quelques.");
        assertEquals(CueClock.seconds(7L, 0.25f), CueClock.seconds(7L + CueClock.PERIOD_TICKS, 0.25f), EPSILON, "Une periode plus tard : pareil.");
        assertTrue(CueClock.seconds(CueClock.PERIOD_TICKS - 1L, 0.9f) < CueClock.PERIOD_TICKS / 20.0f, "Jamais au-dela de la periode.");
        assertEquals(CueClock.seconds(-5L, 0.0f), CueClock.seconds(CueClock.PERIOD_TICKS - 5L, 0.0f), EPSILON, "Un temps negatif se range dans la periode.");
        float before = CueClock.seconds(1_000_000L, 0.0f);
        float next = CueClock.seconds(1_000_000L, 0.05f);
        assertTrue(next > before, "Une fraction de tick se distingue encore apres des heures de jeu.");
    }

    @Test
    @DisplayName("Le tick entier des fonctions deterministes reste dans la periode, positif")
    void tickStaysInThePeriod() {
        assertEquals(0, CueClock.tick(0L), "Le debut du monde.");
        assertEquals(20, CueClock.tick(20L), "Vingt ticks.");
        assertEquals(7, CueClock.tick(7L + CueClock.PERIOD_TICKS), "Une periode plus tard : pareil.");
        assertEquals((int) (CueClock.PERIOD_TICKS - 5L), CueClock.tick(-5L), "Un temps negatif se range dans la periode.");
        assertEquals((int) (CueClock.PERIOD_TICKS - 1L), CueClock.tick(CueClock.PERIOD_TICKS * 3L - 1L), "Jamais au-dela de la periode.");
    }
}
