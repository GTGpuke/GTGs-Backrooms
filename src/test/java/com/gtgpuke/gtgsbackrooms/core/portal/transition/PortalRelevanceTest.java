package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

@DisplayName("Pertinence d'un portail pour un observateur")
class PortalRelevanceTest {

    private static final Vec3d EYE = new Vec3d(0.0, 65.0, 0.0);
    private static final Vec3d LOOK_NORTH = new Vec3d(0.0, 0.0, -1.0);

    @Test
    @DisplayName("Un regard donne en angles juge comme le vecteur de regard qu'il designe")
    void lookGivenAsAngles() {
        double activation = PortalRelevance.ALWAYS_RELEVANT_DISTANCE;
        // Lacet 180 : vers le nord, comme LOOK_NORTH ; lacet 0 : vers le sud.
        assertTrue(PortalRelevance.isRelevant(EYE, 0.0f, 180.0f, new Vec3d(0.0, 65.0, -20.0), 64.0), "Au nord, regarde vers le nord.");
        assertFalse(PortalRelevance.isRelevant(EYE, 0.0f, 0.0f, new Vec3d(0.0, 65.0, -(activation + 4.0)), 64.0),
                "Au nord, regarde vers le sud, au-dela de l'activation.");
        assertTrue(PortalRelevance.isRelevant(EYE, 0.0f, 0.0f, new Vec3d(0.0, 65.0, -(activation - 1.0)), 64.0),
                "Au nord, dans le dos mais a portee d'activation.");
        assertFalse(PortalRelevance.isRelevant(EYE, 0.0f, 180.0f, new Vec3d(0.0, 65.0, -100.0), 64.0), "Trop loin.");
    }

    @Test
    @DisplayName("Un portail devant l'observateur et a portee compte")
    void inFrontAndInRange() {
        assertTrue(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, -20.0), 64.0), "Devant, a 20 blocs.");
        assertTrue(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(30.0, 65.0, -1.0), 64.0), "A peine devant, sur le cote.");
    }

    @Test
    @DisplayName("Un portail dans le dos ne compte pas, sauf a portee d'activation")
    void behindIsIgnoredUnlessWithinActivationRange() {
        double activation = PortalRelevance.ALWAYS_RELEVANT_DISTANCE;
        assertFalse(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, activation + 4.0), 64.0), "Derriere, au-dela de l'activation.");
        assertTrue(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, activation - 1.0), 64.0),
                "Derriere mais a portee d'activation : on peut s'y engager a reculons.");
        assertFalse(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, activation + 0.5), 64.0),
                "Derriere, juste au-dela de la distance d'activation.");
    }

    @Test
    @DisplayName("A portee, un portail compte pour la synchronisation quelle que soit la direction du regard")
    void inRangeRegardlessOfLook() {
        assertTrue(PortalRelevance.isInRange(EYE, new Vec3d(0.0, 65.0, 40.0), 64.0), "Derriere, a 40 blocs : a portee.");
        assertTrue(PortalRelevance.isInRange(EYE, new Vec3d(0.0, 65.0, 64.0), 64.0), "Exactement a la limite.");
        assertFalse(PortalRelevance.isInRange(EYE, new Vec3d(0.0, 65.0, 64.5), 64.0), "Juste au-dela.");
        assertFalse(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, 40.0), 64.0), "Le rendu, lui, ne compte pas un portail dans le dos.");
    }

    @Test
    @DisplayName("Au-dela de la distance maximale, rien ne compte")
    void outOfRangeIsIgnored() {
        assertFalse(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, -65.0), 64.0), "Devant mais trop loin.");
        assertTrue(PortalRelevance.isRelevant(EYE, LOOK_NORTH, new Vec3d(0.0, 65.0, -64.0), 64.0), "Exactement a la limite.");
    }
}
