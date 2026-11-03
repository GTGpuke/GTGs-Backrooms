package com.gtgpuke.gtgsbackrooms.core.render.light;

/**
 * Le nombre de tuiles qu'une passe calcule par image : autant que le temps permis le laisse, d'apres ce qu'une tuile a coute jusque-la,
 * au processeur comme a la carte graphique, le plus lent des deux. Une carte rapide met a jour la lumiere en quelques images, une carte
 * lente garde des images regulieres. Sans mesure, un depart prudent.
 */
public final class TileBudget {

    /** Tuiles par image avant toute mesure. */
    public static final int START = 96;
    /** Tuiles par image au moins : une carte tres lente avance quand meme. */
    public static final int MIN = 32;
    /** Tuiles par image au plus : un pic de temps ne se mesure qu'apres coup. */
    public static final int MAX = 4096;
    /** Part de chaque mesure dans la moyenne du cout d'une tuile. */
    private static final float SMOOTHING = 0.25f;

    private final float milliseconds;
    private float perTile;

    /**
     * @param milliseconds temps permis a la passe par image
     */
    public TileBudget(float milliseconds) {
        this.milliseconds = milliseconds;
    }

    /**
     * Une passe mesuree.
     *
     * @param tiles tuiles calculees
     * @param milliseconds temps qu'elles ont pris, le plus long du processeur et de la carte graphique
     */
    public void measured(int tiles, float milliseconds) {
        if (tiles <= 0) {
            return;
        }
        float sample = milliseconds / tiles;
        this.perTile = this.perTile <= 0.0f ? sample : this.perTile + (sample - this.perTile) * SMOOTHING;
    }

    /** Tuiles a calculer a la prochaine image. */
    public int tiles() {
        if (this.perTile <= 0.0f) {
            return START;
        }
        return (int) Math.max(MIN, Math.min(MAX, this.milliseconds / this.perTile));
    }

    /** Ce qu'a coute une tuile en moyenne, en millisecondes, 0 sans mesure. */
    public float perTile() {
        return this.perTile;
    }
}
