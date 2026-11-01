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
        assertEquals(1.0f - FlashLimiter.SOFT_RATE * 0.1f, softened, EPSILON, "Quatrieme dans la seconde : la vitesse douce, pendant les 100 ms ecoulees.");
        assertEquals(softened - FlashLimiter.SOFT_RATE * 0.02f, limiter.limit(0.0f, 420L), EPSILON, "Et encore, pendant 20 ms.");
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
        assertEquals(0.0f, soft.limit(1.0f, 0L), EPSILON, "Reduire les flashs : a la premiere image, aucun temps ecoule.");
        assertEquals(FlashLimiter.SOFT_RATE * 0.05f, soft.limit(1.0f, 50L), EPSILON, "Puis la vitesse douce, des la premiere variation.");
        assertEquals(FlashLimiter.SOFT_RATE * 0.15f, soft.limit(1.0f, 5050L), EPSILON, "Apres une pause, pas plus que 100 ms de rattrapage.");
        assertTrue(soft.value() < 0.2f, "Loin de la valeur voulue.");
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
    @DisplayName("L'option de confort change en pleine partie : coupee, une variation forte passe ; remise, la vitesse redevient douce")
    void softChangesLive() {
        FlashLimiter limiter = new FlashLimiter(true);
        assertEquals(0.0f, limiter.limit(1.0f, 0L), EPSILON, "Reduire les flashs : la valeur ne saute pas.");
        limiter.setSoft(false);
        assertEquals(1.0f, limiter.limit(1.0f, 50L), EPSILON, "Option coupee : la variation forte passe.");
        limiter.setSoft(true);
        assertEquals(1.0f - FlashLimiter.SOFT_RATE * 0.05f, limiter.limit(0.0f, 100L), EPSILON, "Option remise : la vitesse douce, tout de suite.");
    }

    @Test
    @DisplayName("Le plafond tient a toute cadence : une montee rapide en petits pas, a 300 images par seconde, reste forte")
    void capHoldsAtAnyFrameRate() {
        for (int fps : new int[] {30, 60, 144, 300}) {
            FlashLimiter limiter = new FlashLimiter(false);
            int frames = 3 * fps;
            float[] in = new float[frames];
            float[] out = new float[frames];
            long[] times = new long[frames];
            for (int i = 0; i < frames; i++) {
                times[i] = 1000L + i * 1000L / fps;
                in[i] = squareWave(times[i]);
                out[i] = limiter.limit(in[i], times[i]);
            }
            assertTrue(maxSwingsPerSecond(in, times) > FlashLimiter.MAX_STRONG_PER_SECOND, fps + " images par seconde : l'entree depasse le plafond.");
            assertTrue(maxSwingsPerSecond(out, times) <= FlashLimiter.MAX_STRONG_PER_SECOND,
                    fps + " images par seconde : " + maxSwingsPerSecond(out, times) + " grands ecarts dans une seconde.");
        }
    }

    @Test
    @DisplayName("Un vacillement autour d'une valeur moyenne compte d'un extreme a l'autre, pas depuis la moyenne")
    void flickerAroundMidValueIsStrong() {
        FlashLimiter limiter = new FlashLimiter(false);
        int frames = 120;
        float[] out = new float[frames];
        long[] times = new long[frames];
        limiter.limit(0.5f, 1000L);
        for (int i = 0; i < frames; i++) {
            times[i] = 1016L + i * 1000L / 60L;
            // 0,65 puis 0,35, chaque palier trois images : a moins de 0,2 de 0,5, mais 0,3 d'un extreme a l'autre.
            out[i] = limiter.limit((i / 3) % 2 == 0 ? 0.65f : 0.35f, times[i]);
        }
        int reversals = maxReversalsPerSecond(out, times);
        assertTrue(reversals <= FlashLimiter.MAX_STRONG_PER_SECOND, reversals + " ecarts forts d'un extreme a l'autre dans une seconde.");
    }

    /** Plus grand nombre, dans 999 ms, d'ecarts d'au moins {@code STRONG} entre deux extremes successifs de la sortie. */
    private static int maxReversalsPerSecond(float[] values, long[] times) {
        long[] strong = new long[values.length];
        int count = 0;
        int extreme = 0;
        float direction = 0.0f;
        for (int i = 1; i <= values.length; i++) {
            float sign = i < values.length ? Math.signum(values[i] - values[i - 1]) : -direction;
            if (sign != 0.0f && sign != direction) {
                if (direction != 0.0f && Math.abs(values[i - 1] - values[extreme]) >= FlashLimiter.STRONG) {
                    strong[count++] = times[i - 1];
                }
                extreme = i - 1;
                direction = sign;
            }
        }
        int max = 0;
        for (int i = 0; i < count; i++) {
            int j = i;
            while (j < count && strong[j] - strong[i] < 999L) {
                j++;
            }
            max = Math.max(max, j - i);
        }
        return max;
    }

    /** Onde carree de 10 Hz entre 0 et 1, a rampes de 30 ms : a haute cadence, chaque image ne bouge que d'un petit pas. */
    private static float squareWave(long millis) {
        long phase = millis % 100L;
        if (phase < 30L) {
            return phase / 30.0f;
        }
        if (phase < 50L) {
            return 1.0f;
        }
        if (phase < 80L) {
            return 1.0f - (phase - 50L) / 30.0f;
        }
        return 0.0f;
    }

    /** Plus grand nombre de passages d'un bord a l'autre (sous 0,25 puis au-dessus de 0,75, ou l'inverse) dans 999 ms. */
    private static int maxSwingsPerSecond(float[] values, long[] times) {
        long[] swings = new long[values.length];
        int count = 0;
        int side = 0;
        for (int i = 0; i < values.length; i++) {
            int now = values[i] <= 0.25f ? -1 : values[i] >= 0.75f ? 1 : 0;
            if (now != 0 && now != side) {
                if (side != 0) {
                    swings[count++] = times[i];
                }
                side = now;
            }
        }
        int max = 0;
        for (int i = 0; i < count; i++) {
            int j = i;
            while (j < count && swings[j] - swings[i] < 999L) {
                j++;
            }
            max = Math.max(max, j - i);
        }
        return max;
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
