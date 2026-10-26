package com.gtgpuke.gtgsbackrooms.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Configuration du mod")
class ModConfigTest {

    @Test
    @DisplayName("Les valeurs par defaut sont jouables : debug desactive, portails actifs")
    void defaults() {
        assertTrue(ModConfig.ENABLE_LEVEL_ZERO_NOISE_GEOMETRY, "La micro-geometrie est active par defaut.");
        assertFalse(ModConfig.DEBUG_LEVEL_ZERO_MICRO_GEOMETRY, "Le debug geometrie est inactif par defaut.");
        assertFalse(ModConfig.DEBUG_LEVEL_ZERO_WALL_PROPS, "Le debug des details muraux est inactif par defaut.");
        assertFalse(ModConfig.DEBUG_PERFORMANCE_MONITOR, "Le moniteur de performance est inactif par defaut.");
        assertTrue(ModConfig.ENABLE_PORTAL_FEATURE, "Les portails sont actifs par defaut.");
        assertFalse(ModConfig.DEBUG_PORTAL_RENDERING, "Le debug du rendu portail est inactif par defaut.");
        assertTrue(ModConfig.ENABLE_PORTAL_OCCLUSION, "Une vue cachee derriere un mur ne se dessine pas, par defaut.");
        assertTrue(ModConfig.ENABLE_PORTAL_LOD, "La vue d'un cadre lointain s'allege, par defaut.");
        assertEquals(10, ModConfig.PERFORMANCE_LOG_INTERVAL_SECONDS, "Dix secondes entre deux resumes.");
        assertEquals(0, ModConfig.PORTAL_TELEPORT_COOLDOWN_TICKS, "Aucun delai entre deux traversees.");
        assertEquals(16, ModConfig.PORTAL_ACTIVATION_RADIUS_BLOCKS, "Rayon d'activation.");
        assertEquals(3, ModConfig.PORTAL_PRELOAD_RADIUS_CHUNKS, "Rayon de prechargement.");
        assertEquals(60, ModConfig.PORTAL_REMOTE_WORLD_RELEASE_SECONDS, "Une minute sans portail a portee avant de liberer un monde distant.");
        assertEquals(64, ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS, "Distance de rendu.");
    }

    @Test
    @DisplayName("Le parsing des booleens accepte true/false et retombe sur la valeur par defaut")
    void parseBoolean() {
        assertTrue(ModConfig.parseBoolean("true", false), "Valeur true explicite.");
        assertTrue(ModConfig.parseBoolean(" TRUE ", false), "Insensible a la casse et aux espaces.");
        assertFalse(ModConfig.parseBoolean("false", true), "Valeur false explicite.");
        assertFalse(ModConfig.parseBoolean("oui", true), "Toute autre valeur vaut false, comme Boolean.parseBoolean.");
        assertTrue(ModConfig.parseBoolean(null, true), "Valeur absente : defaut.");
        assertTrue(ModConfig.parseBoolean("  ", true), "Valeur vide : defaut.");
    }

    @Test
    @DisplayName("Le parsing des entiers ignore les valeurs invalides")
    void parseInt() {
        assertEquals(42, ModConfig.parseInt("42", 7), "Entier valide.");
        assertEquals(-3, ModConfig.parseInt(" -3 ", 7), "Entier negatif avec espaces.");
        assertEquals(7, ModConfig.parseInt("abc", 7), "Texte invalide : defaut.");
        assertEquals(7, ModConfig.parseInt("4.5", 7), "Decimal invalide : defaut.");
        assertEquals(7, ModConfig.parseInt(null, 7), "Valeur absente : defaut.");
        assertEquals(7, ModConfig.parseInt("", 7), "Valeur vide : defaut.");
    }
}
