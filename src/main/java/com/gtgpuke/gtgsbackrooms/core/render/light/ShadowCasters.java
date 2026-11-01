package com.gtgpuke.gtgsbackrooms.core.render.light;

/**
 * Les corps qui portent une ombre sur le terrain, en coordonnees relatives au bloc d'une camera : une liste bornee de cylindres
 * debout, sans rien allouer apres sa creation. Un corps debout se decoupe en jambes, torse et tete, plus etroites que lui : son
 * ombre ressemble a un corps, et non a un pave.
 */
public final class ShadowCasters {

    /** Cylindres gardes, au plus : ceux que declare {@code look.glsl}. */
    public static final int MAX_CYLINDERS = 24;
    /** Valeurs d'un cylindre : centre du bas et rayon, puis haut, en deux groupes de quatre pour le bloc d'uniformes. */
    public static final int CYLINDER_VALUES = 8;
    /** Valeurs de la liste. */
    public static final int VALUES = MAX_CYLINDERS * CYLINDER_VALUES;
    /** Hauteur, en largeurs, a partir de laquelle un corps est debout. */
    private static final double STANDING = 1.5;
    private static final double LEGS_HEIGHT = 0.38;
    private static final double LEGS_WIDTH = 0.6;
    private static final double HEAD_HEIGHT = 0.25;
    private static final double HEAD_WIDTH = 0.8;

    private final float[] values = new float[VALUES];
    private int cylinders;

    /** Vide la liste. */
    public void clear() {
        this.cylinders = 0;
    }

    /**
     * Ajoute un corps.
     *
     * @param x centre des pieds
     * @param y pieds
     * @param z centre des pieds
     * @param width largeur
     * @param height hauteur
     */
    public void add(double x, double y, double z, double width, double height) {
        if (height < width * STANDING) {
            cylinder(x, y, z, width, 0.0, height);
            return;
        }
        double legs = height * LEGS_HEIGHT;
        double head = height * (1.0 - HEAD_HEIGHT);
        cylinder(x, y, z, width * LEGS_WIDTH, 0.0, legs);
        cylinder(x, y, z, width, legs, head);
        cylinder(x, y, z, width * HEAD_WIDTH, head, height);
    }

    private void cylinder(double x, double y, double z, double width, double bottom, double top) {
        if (this.cylinders == MAX_CYLINDERS) {
            return;
        }
        int at = this.cylinders * CYLINDER_VALUES;
        this.values[at] = (float) x;
        this.values[at + 1] = (float) (y + bottom);
        this.values[at + 2] = (float) z;
        this.values[at + 3] = (float) (width * 0.5);
        this.values[at + 4] = (float) (y + top);
        this.cylinders++;
    }

    /** Nombre de cylindres. */
    public int cylinders() {
        return this.cylinders;
    }

    /** Les cylindres, {@value #CYLINDER_VALUES} valeurs chacun : a ne pas modifier. */
    public float[] values() {
        return this.values;
    }
}
