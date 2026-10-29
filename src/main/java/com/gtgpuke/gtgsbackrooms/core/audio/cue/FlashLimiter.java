package com.gtgpuke.gtgsbackrooms.core.audio.cue;

/**
 * Le plafond de clignotement pour les joueurs photosensibles : l'effet visuel final (lumiere qui baisse, pulsation,
 * neons perturbes, en cumul) ne varie jamais fortement plus de {@value #MAX_STRONG_PER_SECOND} fois par seconde. Au-dela,
 * la valeur suivie ne bouge plus que par petits pas (une baisse douce) ; l'option de confort impose les petits pas
 * tout le temps. Le son n'est jamais touche. Logique pure, un limiteur pour tout l'ecran.
 */
public final class FlashLimiter {

    /** Variations fortes par seconde, au plus. */
    public static final int MAX_STRONG_PER_SECOND = 3;
    /** Une variation est forte a partir de cet ecart entre deux images. */
    static final float STRONG = 0.2f;
    /** Pas maximal d'une image a l'autre une fois le plafond atteint. */
    static final float SOFT_STEP = 0.02f;
    private static final long SECOND_MS = 1000L;

    private final long[] strongAt = new long[MAX_STRONG_PER_SECOND];
    private final boolean soft;
    private float value;

    /**
     * @param soft {@code true} pour ne jamais varier fortement (option de confort : reduire les flashs)
     */
    public FlashLimiter(boolean soft) {
        this.soft = soft;
    }

    /**
     * La valeur a montrer pour la valeur voulue, a cet instant.
     *
     * @param wanted valeur voulue, de 0 a 1
     * @param nowMillis instant de l'image, en millisecondes
     */
    public float limit(float wanted, long nowMillis) {
        float delta = wanted - this.value;
        if (Math.abs(delta) < STRONG) {
            this.value = wanted;
            return this.value;
        }
        if (!this.soft && !saturated(nowMillis)) {
            // Une variation forte de plus : notee, et laissee passer.
            shift();
            this.strongAt[MAX_STRONG_PER_SECOND - 1] = nowMillis;
            this.value = wanted;
            return this.value;
        }
        this.value += Math.signum(delta) * SOFT_STEP;
        return this.value;
    }

    /**
     * La variation de lumiere demandee a tout l'ecran : chaque vue de portail compte la charge de sa dimension a
     * proportion de la part de l'ecran qu'elle couvre, et le monde du joueur le reste. Une vue vers les Backrooms vue
     * depuis l'Overworld partage ainsi le meme plafond, a sa surface.
     *
     * @param areas part de l'ecran de chaque vue, de 0 a 1
     * @param loads charge de la dimension de chaque vue, de 0 a 1
     * @param count nombre de vues
     * @param playerLoad charge de la dimension du joueur
     * @return la charge de l'ecran, de 0 a 1
     */
    public static float screenLoad(float[] areas, float[] loads, int count, float playerLoad) {
        float covered = 0.0f;
        float load = 0.0f;
        for (int i = 0; i < count; i++) {
            load += areas[i] * loads[i];
            covered += areas[i];
        }
        return Math.min(1.0f, load + Math.max(0.0f, 1.0f - covered) * playerLoad);
    }

    /** Valeur montree. */
    public float value() {
        return this.value;
    }

    /** Remise a zero : fin de partie. */
    public void reset() {
        this.value = 0.0f;
        for (int i = 0; i < MAX_STRONG_PER_SECOND; i++) {
            this.strongAt[i] = 0L;
        }
    }

    /** {@code true} si trois variations fortes ont deja eu lieu dans la derniere seconde. */
    private boolean saturated(long nowMillis) {
        return this.strongAt[0] != 0L && nowMillis - this.strongAt[0] < SECOND_MS;
    }

    private void shift() {
        for (int i = 1; i < MAX_STRONG_PER_SECOND; i++) {
            this.strongAt[i - 1] = this.strongAt[i];
        }
    }
}
