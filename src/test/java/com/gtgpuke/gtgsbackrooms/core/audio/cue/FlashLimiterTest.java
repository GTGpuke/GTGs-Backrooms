package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Plafond de clignotement")
class FlashLimiterTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Trois variations fortes par seconde passent ; la quatrieme est adoucie, puis une seconde plus tard une forte passe de nouveau")
    void strongVariationsAreCapped() {
        FlashLimiter limiter = new FlashLimiter(false);
        assertEquals(1.0f, limiter.limit(1.0f, 100L), EPSILON, "Premiere variation forte : passe.");
        assertEquals(0.0f, limiter.limit(0.0f, 200L), EPSILON, "Deuxieme : passe.");
        assertEquals(1.0f, limiter.limit(1.0f, 300L), EPSILON, "Troisieme : passe.");
        float softened = limiter.limit(0.0f, 400L);
        assertEquals(1.0f - FlashLimiter.SOFT_STEP, softened, EPSILON, "Quatrieme dans la seconde : un petit pas seulement.");
        assertEquals(softened - FlashLimiter.SOFT_STEP, limiter.limit(0.0f, 420L), EPSILON, "Et encore un petit pas.");
        float before = limiter.value();
        assertEquals(before + 0.1f, limiter.limit(before + 0.1f, 440L), EPSILON, "Une petite variation suit sans compter.");
        assertEquals(0.0f, limiter.limit(0.0f, 1200L), EPSILON, "Plus d'une seconde apres la premiere : une forte passe de nouveau.");
    }

    @Test
    @DisplayName("Petites variations libres ; option de confort : jamais de variation forte ; remise a zero")
    void softModeAndReset() {
        FlashLimiter free = new FlashLimiter(false);
        assertEquals(0.1f, free.limit(0.1f, 0L), EPSILON, "Une petite variation passe telle quelle.");
        assertEquals(0.25f, free.limit(0.25f, 10L), EPSILON, "Une autre aussi.");
        FlashLimiter soft = new FlashLimiter(true);
        assertEquals(FlashLimiter.SOFT_STEP, soft.limit(1.0f, 0L), EPSILON, "Reduire les flashs : un petit pas des la premiere.");
        assertTrue(soft.value() < 0.1f, "Loin de la valeur voulue.");
        soft.reset();
        assertEquals(0.0f, soft.value(), EPSILON, "Remis a zero.");
        free.limit(1.0f, 100L);
        free.limit(0.0f, 200L);
        free.limit(1.0f, 300L);
        free.reset();
        assertEquals(0.0f, free.value(), EPSILON, "Remis a zero aussi.");
        assertEquals(1.0f, free.limit(1.0f, 310L), EPSILON, "Les variations fortes notees sont oubliees avec.");
    }

    @Test
    @DisplayName("La charge de l'ecran pese chaque vue a sa surface, le monde du joueur au reste, sans depasser 1")
    void screenLoadWeighsViewsByArea() {
        float[] areas = {0.1f, 0.3f};
        float[] loads = {1.0f, 0.5f};
        assertEquals(0.0f, FlashLimiter.screenLoad(areas, loads, 0, 0.0f), 1.0e-6f, "Rien a l'ecran.");
        assertEquals(0.4f, FlashLimiter.screenLoad(areas, loads, 0, 0.4f), 1.0e-6f, "Sans vue : la charge du joueur sur tout l'ecran.");
        assertEquals(0.1f, FlashLimiter.screenLoad(areas, loads, 1, 0.0f), 1.0e-6f, "Un cri du Level 0 vu par un petit cadre depuis l'Overworld.");
        assertEquals(0.1f + 0.15f + 0.6f * 0.5f, FlashLimiter.screenLoad(areas, loads, 2, 0.5f), 1.0e-6f, "Deux vues et le reste.");
        float[] full = {0.8f, 0.7f};
        assertEquals(1.0f, FlashLimiter.screenLoad(full, new float[] {1.0f, 1.0f}, 2, 1.0f), 1.0e-6f, "Borne a 1, vues qui se recouvrent comprises.");
    }
}
