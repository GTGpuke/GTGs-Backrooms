package com.gtgpuke.gtgsbackrooms.core.audio.cue;

/**
 * Le plafond de clignotement pour les joueurs photosensibles : l'effet visuel final (lumiere qui baisse, pulsation,
 * neons perturbes, en cumul) ne varie jamais fortement plus de {@value #MAX_STRONG_PER_SECOND} fois par seconde. Une
 * variation se mesure depuis le dernier extreme de la valeur montree (un changement de sens le deplace), pas d'une image
 * a la suivante : une montee rapide en petits pas, a haute cadence, reste une variation forte. Au-dela du plafond, la
 * valeur suivie ne bouge plus qu'a vitesse bornee, par seconde (une baisse douce, la meme a toute cadence) ; l'option de
 * confort l'impose tout le temps. Le son n'est jamais touche. Logique pure, un limiteur pour tout l'ecran.
 */
public final class FlashLimiter {

    /** Variations fortes par seconde, au plus. */
    public static final int MAX_STRONG_PER_SECOND = 3;
    /** Une variation est forte a partir de cet ecart avec le dernier extreme de la valeur montree. */
    static final float STRONG = 0.2f;
    /** Vitesse maximale de la valeur montree une fois le plafond atteint, par seconde (0,02 par image a 60 images par seconde). */
    static final float SOFT_RATE = 1.2f;
    /** Plus long intervalle compte entre deux images : apres une pause, la valeur ne rattrape pas d'un coup. */
    static final long MAX_FRAME_MS = 100L;
    private static final float SECOND_MS = 1000.0f;

    private final long[] strongAt = new long[MAX_STRONG_PER_SECOND];
    private boolean soft;
    private float value;
    /** Dernier extreme de la valeur montree, d'ou se mesure la variation en cours. */
    private float anchor;
    /** Sens de la variation en cours : -1, 0 ou 1. */
    private float direction;
    private long lastMillis = -1L;

    /**
     * @param soft {@code true} pour ne jamais varier fortement (option de confort : reduire les flashs)
     */
    public FlashLimiter(boolean soft) {
        this.soft = soft;
    }

    /** Le joueur change l'option de confort. */
    public void setSoft(boolean next) {
        this.soft = next;
    }

    /**
     * La valeur a montrer pour la valeur voulue, a cet instant.
     *
     * @param wanted valeur voulue, de 0 a 1
     * @param nowMillis instant de l'image, en millisecondes
     */
    public float limit(float wanted, long nowMillis) {
        long elapsed = this.lastMillis < 0L ? 0L : Math.clamp(nowMillis - this.lastMillis, 0L, MAX_FRAME_MS);
        this.lastMillis = nowMillis;
        float delta = wanted - this.value;
        float sign = Math.signum(delta);
        if (sign != 0.0f && sign != this.direction) {
            // Changement de sens : la valeur montree est un extreme, d'ou se mesure la variation suivante.
            this.anchor = this.value;
            this.direction = sign;
        }
        if (Math.abs(wanted - this.anchor) < STRONG) {
            this.value = wanted;
            return this.value;
        }
        if (!this.soft && !saturated(nowMillis)) {
            // Une variation forte de plus : notee, et laissee passer.
            shift();
            this.strongAt[MAX_STRONG_PER_SECOND - 1] = nowMillis;
            this.value = wanted;
            this.anchor = wanted;
            return this.value;
        }
        float step = SOFT_RATE * elapsed / SECOND_MS;
        this.value += Math.clamp(delta, -step, step);
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
        this.anchor = 0.0f;
        this.direction = 0.0f;
        this.lastMillis = -1L;
        for (int i = 0; i < MAX_STRONG_PER_SECOND; i++) {
            this.strongAt[i] = 0L;
        }
    }

    /** {@code true} si trois variations fortes ont deja eu lieu dans la derniere seconde. */
    private boolean saturated(long nowMillis) {
        return this.strongAt[0] != 0L && nowMillis - this.strongAt[0] < (long) SECOND_MS;
    }

    private void shift() {
        for (int i = 1; i < MAX_STRONG_PER_SECOND; i++) {
            this.strongAt[i - 1] = this.strongAt[i];
        }
    }
}
