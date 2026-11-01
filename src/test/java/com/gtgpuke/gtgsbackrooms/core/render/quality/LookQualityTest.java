package com.gtgpuke.gtgsbackrooms.core.render.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paliers de qualite du moteur de shaders")
class LookQualityTest {

    private static final String DEDICATED = "NVIDIA GeForce RTX 5070 Ti";
    private static final String INTEGRATED = "Intel(R) UHD Graphics 620";

    @Test
    @DisplayName("Un palier se demande par sa cle ; auto prend High, ou Low sur une carte integree ; les anciens noms restent valables")
    void optionAndDetection() {
        for (LookQuality quality : LookQuality.values()) {
            assertSame(quality, LookQuality.choose(quality.key(), INTEGRATED), "Par sa cle : " + quality.key() + ".");
        }
        assertSame(LookQuality.ULTRA, LookQuality.choose(" Ultra ", INTEGRATED), "Majuscules et espaces ignores, meme sur une integree.");
        assertSame(LookQuality.MEDIUM, LookQuality.choose("light", DEDICATED), "Ancien nom : legere.");
        assertSame(LookQuality.HIGH, LookQuality.choose(" Full ", INTEGRATED), "Ancien nom : complete.");
        assertSame(LookQuality.HIGH, LookQuality.choose(LookQuality.AUTO, DEDICATED), "Auto sur une carte dediee.");
        assertSame(LookQuality.LOW, LookQuality.choose("auto", INTEGRATED), "Auto sur une integree.");
        assertSame(LookQuality.LOW, LookQuality.choose("n'importe quoi", "Mesa llvmpipe"), "Valeur inconnue : auto, rendu logiciel.");
        assertSame(LookQuality.HIGH, LookQuality.choose(LookQuality.AUTO, null), "Carte inconnue : High.");
        assertFalse(LookQuality.integrated("AMD Radeon RX 7800"), "Une carte dediee.");
        assertTrue(LookQuality.integrated("Google SwiftShader"), "Rendu logiciel.");
    }

    @Test
    @DisplayName("Chaque palier ajoute au precedent : post et effets de vue, puis cache de surfaces et bloom, puis plus de rayons")
    void tiers() {
        assertEquals(8, LookQuality.LOW.neonCount(), "Huit neons en Low.");
        assertFalse(LookQuality.LOW.post(), "Aucun post en Low.");
        assertFalse(LookQuality.LOW.viewEffects(), "Pas de halos en Low.");
        assertTrue(LookQuality.MEDIUM.post(), "Le post arrive en Medium.");
        assertTrue(LookQuality.MEDIUM.viewEffects(), "Les halos aussi.");
        assertFalse(LookQuality.MEDIUM.surfaceLight(), "Pas encore le cache de surfaces.");
        assertFalse(LookQuality.MEDIUM.bloom(), "Ni le bloom.");
        assertTrue(LookQuality.HIGH.surfaceLight(), "Cache de surfaces en High.");
        assertTrue(LookQuality.HIGH.bloom(), "Bloom en High.");
        assertEquals(32, LookQuality.HIGH.neonCount(), "Trente-deux neons en High.");
        assertTrue(LookQuality.ULTRA.surfaceRays() > LookQuality.HIGH.surfaceRays(), "Plus de rayons vers les dalles en Ultra.");
        assertTrue(LookQuality.ULTRA.surfaceOcclusionRays() > LookQuality.HIGH.surfaceOcclusionRays(), "Et pour les coins.");
        assertTrue(LookQuality.HIGH.casterRays() > 0 && LookQuality.ULTRA.casterRays() > LookQuality.HIGH.casterRays(), "Les corps portent une ombre en High, plus fine en Ultra.");
        assertEquals(0, LookQuality.MEDIUM.casterRays(), "Pas d'ombre de corps sans cache de surfaces.");
        assertEquals(0, LookQuality.LOW.casterRays(), "Ni en Low.");
        assertTrue(LookQuality.ULTRA.traced() && LookQuality.ULTRA.tracedRays() > 0, "L'Ultra trace sa lumiere.");
        assertFalse(LookQuality.HIGH.traced(), "Pas le High.");
        assertFalse(LookQuality.OFF.post(), "Rien au palier desactive.");
        assertFalse(LookQuality.OFF.bloom(), "Rien non plus.");
        assertEquals(0, LookQuality.OFF.neonCount(), "Aucun neon.");
    }
}
