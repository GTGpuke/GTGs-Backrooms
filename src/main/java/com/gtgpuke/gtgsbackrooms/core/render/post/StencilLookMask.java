package com.gtgpuke.gtgsbackrooms.core.render.post;

/**
 * Disposition du stencil du rendu vanilla des portails : les deux bits bas portent le niveau d'imbrication de la vue
 * (0 monde du joueur, 1 vue, 2 vue imbriquee), les trois bits suivants l'identifiant du look de la dimension que le
 * pixel montre (0 vanilla). Les tests de niveau ne comparent que leurs bits ({@link #LEVEL_MASK}) ; le post-traitement
 * ne regarde que ceux du look ({@link #LOOK_MASK}). Au retour d'une vue a son niveau, ses bits de look survivent : en fin
 * de rendu, chaque pixel porte le look de la vue la plus profonde qui s'y voit. Les bits hauts restent au chemin sous un
 * pack de shaders, qui a sa propre disposition.
 */
public final class StencilLookMask {

    /** Bits du niveau d'imbrication. */
    public static final int LEVEL_MASK = 0x03;
    /** Bits de l'identifiant du look. */
    public static final int LOOK_MASK = 0x1C;
    /** Decalage de l'identifiant du look. */
    public static final int LOOK_SHIFT = 2;
    /** Plus grand identifiant de look. */
    public static final int MAX_LOOK = LOOK_MASK >> LOOK_SHIFT;
    /** Bits ecrits en marquant une vue : niveau et look. */
    public static final int MARK_MASK = LEVEL_MASK | LOOK_MASK;
    /** Tous les bits. */
    public static final int FULL_MASK = 0xFF;

    private StencilLookMask() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Valeur du stencil d'un pixel a ce niveau montrant ce look. */
    public static int value(int level, int look) {
        return (level & LEVEL_MASK) | (look << LOOK_SHIFT & LOOK_MASK);
    }

    /** Niveau d'imbrication porte par une valeur. */
    public static int level(int value) {
        return value & LEVEL_MASK;
    }

    /** Identifiant du look porte par une valeur. */
    public static int look(int value) {
        return (value & LOOK_MASK) >> LOOK_SHIFT;
    }
}
