package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("Courbe de la lightmap d'un profil")
class DimensionLightmapTest {

    @Test
    @DisplayName("Sans courbe, le point ressort tel quel ; avec, il est borne entre plancher et plafond")
    void curveBoundsBrightness() {
        int white = 0xFFFFFFFF;
        int black = 0xFF000000;
        assertEquals(white, DimensionLightmap.apply(LookProfile.Lightmap.VANILLA, white, 0.0f), "Vanilla : inchange.");
        LookProfile.Lightmap curve = new LookProfile.Lightmap(0.2f, 0.0f, 0.8f);
        assertEquals(argb(51, 51, 51), DimensionLightmap.apply(curve, black, 0.0f), "Le noir monte au plancher (0,2 x 255).");
        assertEquals(argb(204, 204, 204), DimensionLightmap.apply(curve, white, 0.0f), "Le blanc descend au plafond (0,8 x 255).");
        assertEquals(argb(128, 128, 128), DimensionLightmap.apply(curve, argb(128, 128, 128), 0.0f), "Le milieu reste au milieu.");
    }

    @Test
    @DisplayName("La chaleur monte le rouge et baisse le bleu, dans les bornes, sans toucher au vert")
    void warmthShiftsChannels() {
        LookProfile.Lightmap warm = new LookProfile.Lightmap(0.0f, 1.0f, 1.0f);
        int gray = argb(200, 200, 200);
        int warmed = DimensionLightmap.apply(warm, gray, 0.0f);
        assertEquals(230, red(warmed), "Rouge x 1,15.");
        assertEquals(200, green(warmed), "Vert intact.");
        assertEquals(150, blue(warmed), "Bleu x 0,75.");
        int bright = DimensionLightmap.apply(warm, argb(255, 255, 255), 0.0f);
        assertEquals(255, red(bright), "Le rouge est borne a 255.");
        assertEquals(0xFF, bright >>> 24, "Alpha opaque.");
    }

    private static int argb(int red, int green, int blue) {
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private static int red(int argb) {
        return argb & 0xFF;
    }

    private static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    private static int blue(int argb) {
        return argb >> 16 & 0xFF;
    }

    @Test
    @DisplayName("Une baisse de lumiere (reaction a un son) assombrit chaque canal apres la courbe, sans toucher a vanilla")
    void flickerDimsAfterTheCurve() {
        LookProfile.Lightmap curve = new LookProfile.Lightmap(0.2f, 0.0f, 0.8f);
        assertEquals(argb(102, 102, 102), DimensionLightmap.apply(curve, argb(255, 255, 255), 0.5f), "Le plafond (204) a moitie.");
        assertEquals(argb(204, 204, 204), DimensionLightmap.apply(curve, argb(255, 255, 255), 0.0f), "Sans baisse, le plafond.");
        assertEquals(argb(255, 255, 255), DimensionLightmap.apply(LookProfile.Lightmap.VANILLA, argb(255, 255, 255), 0.5f),
                "Vanilla : jamais touche, meme pendant un cri.");
    }
}
