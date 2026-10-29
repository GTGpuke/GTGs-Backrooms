package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;

@DisplayName("Qualite du calcul acoustique")
class AudioQualityTest {

    @Test
    @DisplayName("L'option impose une qualite ; sinon elle suit celle du moteur de shaders")
    void optionOrLookQuality() {
        assertSame(AudioQuality.LIGHT, AudioQuality.choose("light", LookQuality.FULL), "Legere demandee.");
        assertSame(AudioQuality.FULL, AudioQuality.choose(" Full ", LookQuality.LIGHT), "Complete demandee, majuscules et espaces ignores.");
        assertSame(AudioQuality.FULL, AudioQuality.choose("auto", LookQuality.FULL), "Auto : comme le rendu.");
        assertSame(AudioQuality.LIGHT, AudioQuality.choose("auto", LookQuality.LIGHT), "Auto sur une carte integree : legere.");
        assertSame(AudioQuality.LIGHT, AudioQuality.choose("n'importe quoi", LookQuality.LIGHT), "Une valeur inconnue vaut auto.");
    }

    @Test
    @DisplayName("La qualite complete lance plus de rayons, rebondit plus et cherche des detours")
    void fullQualityDoesMore() {
        assertTrue(AudioQuality.FULL.rays() > AudioQuality.LIGHT.rays(), "Plus de rayons.");
        assertTrue(AudioQuality.FULL.bounces() > AudioQuality.LIGHT.bounces(), "Plus de rebonds.");
        assertEquals(1, AudioQuality.LIGHT.paths(), "Le seul chemin direct en qualite legere.");
        assertEquals(5, AudioQuality.FULL.paths(), "Le direct et quatre detours en qualite complete.");
    }
}
