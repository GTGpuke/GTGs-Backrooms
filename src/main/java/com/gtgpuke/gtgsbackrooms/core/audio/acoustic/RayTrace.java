package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

/**
 * Les segments des rayons d'un calcul acoustique, pour les voir en jeu (option de debogage) : chaque segment, ses deux
 * bouts, ce qui l'a arrete ({@link #REFLECT}, {@link #ESCAPE}, {@link #PORTAL}, {@link #LOST}) ou le chemin d'occlusion
 * qu'il trace ({@link #OCCLUSION}, avec son occlusion). Capacite fixe, tableaux prepares une fois : un segment de trop
 * est ignore. Rempli par le thread de calcul, recopie sous verrou pour le rendu par l'appelant.
 */
public final class RayTrace {

    /** Un rayon de reverb qui touche un bloc et rebondit. */
    public static final int REFLECT = 0;
    /** Un rayon de reverb sorti sans rencontrer de mur. */
    public static final int ESCAPE = 1;
    /** Un rayon de reverb entre dans un cadre de portail. */
    public static final int PORTAL = 2;
    /** Un rayon de reverb sorti du monde copie. */
    public static final int LOST = 3;
    /** Un chemin d'occlusion : le direct ou un detour, avec son occlusion. */
    public static final int OCCLUSION = 4;
    private static final int FIELDS = 8;

    private final float[] data;
    private final int capacity;
    private int count;

    /**
     * @param capacity segments au plus
     */
    public RayTrace(int capacity) {
        this.capacity = capacity;
        this.data = new float[capacity * FIELDS];
    }

    /** Plus aucun segment. */
    public void clear() {
        this.count = 0;
    }

    /**
     * Ajoute un segment, s'il reste de la place.
     *
     * @param kind ce qui l'a arrete, ou {@link #OCCLUSION}
     * @param value occlusion du chemin, 0 pour un rayon de reverb
     */
    public void add(double x0, double y0, double z0, double x1, double y1, double z1, int kind, float value) {
        if (this.count == this.capacity) {
            return;
        }
        int at = this.count++ * FIELDS;
        this.data[at] = (float) x0;
        this.data[at + 1] = (float) y0;
        this.data[at + 2] = (float) z0;
        this.data[at + 3] = (float) x1;
        this.data[at + 4] = (float) y1;
        this.data[at + 5] = (float) z1;
        this.data[at + 6] = kind;
        this.data[at + 7] = value;
    }

    /** Ajoute les segments d'un autre enregistrement a la suite, dans la place qui reste. */
    public void append(RayTrace other) {
        int copied = Math.min(other.count, this.capacity - this.count);
        System.arraycopy(other.data, 0, this.data, this.count * FIELDS, copied * FIELDS);
        this.count += copied;
    }

    /** Nombre de segments. */
    public int count() {
        return this.count;
    }

    /**
     * Un bout d'un segment, sur un axe.
     *
     * @param segment index du segment
     * @param field 0 a 2 pour le premier bout (x, y, z), 3 a 5 pour le second
     */
    public float coordinate(int segment, int field) {
        return this.data[segment * FIELDS + field];
    }

    /** Ce qui a arrete un segment. */
    public int kind(int segment) {
        return (int) this.data[segment * FIELDS + 6];
    }

    /** Occlusion d'un chemin, 0 pour un rayon de reverb. */
    public float value(int segment) {
        return this.data[segment * FIELDS + 7];
    }
}
