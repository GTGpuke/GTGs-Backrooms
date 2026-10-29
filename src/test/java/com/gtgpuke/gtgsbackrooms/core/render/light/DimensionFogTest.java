package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("Brouillard d'un profil a la place de celui de vanilla")
class DimensionFogTest {

    private static final LookProfile.Fog FOG = new LookProfile.Fog(true, new Vec3d(0.5, 0.4, 0.3), 4.0f, 48.0f);

    @Test
    @DisplayName("Le profil ne remplace vanilla que pour le terrain, hors des fluides, sans cecite ni brouillard epais")
    void profileFogAppliesToPlainTerrainOnly() {
        assertTrue(DimensionFog.applies(FOG, true, false, false, false), "Terrain, camera libre.");
        assertFalse(DimensionFog.applies(LookProfile.Fog.VANILLA, true, false, false, false), "Profil sans brouillard.");
        assertFalse(DimensionFog.applies(FOG, false, false, false, false), "Brouillard du ciel : vanilla.");
        assertFalse(DimensionFog.applies(FOG, true, true, false, false), "Sous l'eau : vanilla.");
        assertFalse(DimensionFog.applies(FOG, true, false, true, false), "Aveugle : vanilla.");
        assertFalse(DimensionFog.applies(FOG, true, false, false, true), "Brouillard epais impose : vanilla.");
    }
}
