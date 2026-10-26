package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewDetail.Tier;

@DisplayName("Paliers de detail des vues de portail")
class ViewDetailTest {

    /** Champ vertical de 70 degres. */
    private static final double SCALE = 1.0 / Math.tan(Math.toRadians(35.0));
    private static final double EPSILON = 1.0e-9;

    @Test
    @DisplayName("Un bloc a l'ecran grandit avec la resolution et le zoom, retrecit avec la distance")
    void blockPixelsFollowResolutionZoomAndDistance() {
        assertEquals(1080 * 0.5 * SCALE / 32.0, ViewDetail.blockPixels(32.0, SCALE, 1080), EPSILON, "En 1080 lignes, a 32 blocs.");
        assertEquals(2.0 * ViewDetail.blockPixels(32.0, SCALE, 1080), ViewDetail.blockPixels(16.0, SCALE, 1080), EPSILON, "Deux fois plus pres.");
        assertEquals(2.0 * ViewDetail.blockPixels(32.0, SCALE, 1080), ViewDetail.blockPixels(32.0, SCALE, 2160), EPSILON, "Deux fois plus de lignes.");
        assertTrue(ViewDetail.blockPixels(32.0, 10.0 * SCALE, 1080) > 5.0 * ViewDetail.blockPixels(32.0, SCALE, 1080), "Un zoom grandit chaque bloc.");
        assertEquals(Double.POSITIVE_INFINITY, ViewDetail.blockPixels(0.0, SCALE, 1080), "Contre la camera : sans limite.");
    }

    @Test
    @DisplayName("Une vue est pleine pour de grands blocs, allegee en dessous, figee pour de tout petits")
    void viewTierFollowsBlockSize() {
        assertEquals(Tier.FULL, ViewDetail.tier(Tier.REDUCED, 0, 30.0), "Grands blocs : pleine, aussitot.");
        assertEquals(Tier.REDUCED, ViewDetail.tier(Tier.FULL, 1000, 10.0), "Blocs moyens : allegee.");
        assertEquals(Tier.FROZEN, ViewDetail.tier(Tier.FULL, 1000, 3.0), "Tout petits blocs : image figee.");
        assertEquals(Tier.REDUCED, ViewDetail.tier(Tier.FROZEN, 0, 7.0), "Une image figee repart aussitot en vue allegee.");
    }

    @Test
    @DisplayName("Une vue ne descend qu'une fois le seuil nettement passe, et quelques images apres son dernier changement")
    void viewTierGoesDownWithMarginAndDelay() {
        assertEquals(Tier.FULL, ViewDetail.tier(Tier.FULL, 1000, 22.0), "Juste sous le seuil : encore pleine.");
        assertEquals(Tier.FULL, ViewDetail.tier(Tier.FULL, ViewDetail.HOLD_FRAMES - 1, 10.0), "Changement trop recent.");
        assertEquals(Tier.REDUCED, ViewDetail.tier(Tier.FULL, ViewDetail.HOLD_FRAMES, 10.0), "Assez d'images depuis le dernier changement.");
        assertEquals(Tier.REDUCED, ViewDetail.tier(Tier.REDUCED, 1000, 5.5), "Juste sous le seuil de l'image : encore allegee.");
        assertEquals(Tier.FROZEN, ViewDetail.tier(Tier.REDUCED, 1000, 4.0), "Nettement sous lui : figee.");
        assertEquals(Tier.REDUCED, ViewDetail.tier(Tier.FULL, 1000, 5.5), "Descente d'un palier seulement si le second n'est pas nettement passe.");
    }

    @Test
    @DisplayName("Un rang de palier se relit tel quel")
    void tierOrdinalRoundTrips() {
        for (Tier tier : Tier.values()) {
            assertEquals(tier, ViewDetail.tierOf(tier.ordinal()), "Rang de " + tier + ".");
        }
    }

    @Test
    @DisplayName("Une section se simplifie en carres de deux puis de quatre blocs, et remonte aussitot")
    void sectionLevelFollowsBlockSize() {
        assertEquals(ViewDetail.FULL_LEVEL, ViewDetail.sectionLevel(ViewDetail.COARSE_LEVEL, 30.0), "Grands blocs : pleine, aussitot.");
        assertEquals(ViewDetail.FULL_LEVEL, ViewDetail.sectionLevel(ViewDetail.FULL_LEVEL, 13.0),
                "Sous le seuil d'une vue pleine, mais pas sous celui d'une section : maillage vanilla.");
        assertEquals(ViewDetail.FULL_LEVEL, ViewDetail.sectionLevel(1, 12.5), "Au-dessus du seuil d'une section : pleine, aussitot.");
        assertEquals(1, ViewDetail.sectionLevel(ViewDetail.FULL_LEVEL, 9.0), "Blocs moyens : carres de deux.");
        assertEquals(ViewDetail.COARSE_LEVEL, ViewDetail.sectionLevel(1, 4.0), "Petits blocs : carres de quatre.");
        assertEquals(ViewDetail.COARSE_LEVEL, ViewDetail.sectionLevel(ViewDetail.COARSE_LEVEL, 7.0),
                "Entre le seuil de l'image et celui des carres de quatre : carres de quatre.");
        assertEquals(ViewDetail.FULL_LEVEL, ViewDetail.sectionLevel(ViewDetail.FULL_LEVEL, 11.0), "Juste sous le seuil : encore pleine.");
        assertEquals(1, ViewDetail.sectionLevel(1, 7.5), "Juste sous le second seuil : encore carres de deux.");
        assertEquals(1, ViewDetail.sectionLevel(ViewDetail.COARSE_LEVEL, 9.0), "Remontee d'un niveau, aussitot.");
    }
}
