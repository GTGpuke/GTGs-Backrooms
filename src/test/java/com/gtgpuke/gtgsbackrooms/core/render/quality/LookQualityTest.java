package com.gtgpuke.gtgsbackrooms.core.render.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Qualite du moteur de shaders")
class LookQualityTest {

    @Test
    @DisplayName("L'option impose une qualite ; auto prend la complete sauf sur une carte integree")
    void optionAndDetection() {
        assertSame(LookQuality.LIGHT, LookQuality.choose("light", "NVIDIA GeForce RTX 5070 Ti"), "Legere demandee.");
        assertSame(LookQuality.FULL, LookQuality.choose(" Full ", "Intel(R) UHD Graphics 620"), "Complete demandee, meme sur une integree.");
        assertSame(LookQuality.FULL, LookQuality.choose("auto", "NVIDIA GeForce RTX 5070 Ti"), "Auto sur une carte dediee.");
        assertSame(LookQuality.LIGHT, LookQuality.choose("auto", "Intel(R) UHD Graphics 620"), "Auto sur une integree.");
        assertSame(LookQuality.LIGHT, LookQuality.choose("n'importe quoi", "Mesa llvmpipe"), "Valeur inconnue : auto, rendu logiciel.");
        assertSame(LookQuality.FULL, LookQuality.choose(LookQuality.AUTO, null), "Carte inconnue : complete.");
        assertFalse(LookQuality.integrated("AMD Radeon RX 7800"), "Une carte dediee.");
        assertTrue(LookQuality.integrated("Google SwiftShader"), "Rendu logiciel.");
    }

    @Test
    @DisplayName("Chaque qualite fixe le nombre de neons et le bloom")
    void settings() {
        assertEquals(8, LookQuality.LIGHT.neonCount(), "Huit neons en legere.");
        assertFalse(LookQuality.LIGHT.bloom(), "Pas de bloom en legere.");
        assertEquals(32, LookQuality.FULL.neonCount(), "Trente-deux neons en complete.");
        assertTrue(LookQuality.FULL.bloom(), "Bloom en complete.");
    }
}
