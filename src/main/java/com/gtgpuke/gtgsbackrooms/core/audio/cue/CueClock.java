package com.gtgpuke.gtgsbackrooms.core.audio.cue;

/**
 * L'horloge commune du son et de l'image : le temps du monde, que le serveur synchronise entre les clients, plus la
 * fraction de tick de l'image. Tous les signaux deterministes (etat des neons, grain) et toutes les reactions en
 * partent : ni le son ni l'image ne commandent l'autre, les deux lisent la meme valeur.
 */
public final class CueClock {

    /** Le temps en secondes repart de zero toutes les 2^20 ticks (plus de quatorze heures) : assez court pour un flottant. */
    static final long PERIOD_TICKS = 1L << 20;
    private static final float TICKS_PER_SECOND = 20.0f;

    private CueClock() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Le tick du monde dans la periode de l'horloge, de 0 a 2^20 - 1 : l'entree entiere des fonctions deterministes,
     * identique en Java et dans les shaders (la fraction de tick passe a part).
     *
     * @param worldTime temps du monde, en ticks
     */
    public static int tick(long worldTime) {
        return (int) Math.floorMod(worldTime, PERIOD_TICKS);
    }

    /**
     * Le temps de l'image, en secondes, pour les effets animes.
     *
     * @param worldTime temps du monde, en ticks
     * @param tickDelta fraction de tick de l'image, de 0 a 1
     */
    public static float seconds(long worldTime, float tickDelta) {
        return (Math.floorMod(worldTime, PERIOD_TICKS) + tickDelta) / TICKS_PER_SECOND;
    }
}
