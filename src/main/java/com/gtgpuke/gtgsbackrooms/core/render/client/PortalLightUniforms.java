package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;

import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;

/**
 * Les portails d'une zone pour les shaders, ceux a portee d'un point : les cadres, qui arretent les rayons de leur monde (centre
 * et face, demi-largeur et 1 pour un passage qui a sa zone de l'autre cote, demi-hauteur : trois vec4), et les neons vus par eux
 * (centre et face, puis couleur et place de leur cadre dans la liste : deux vec4). Positions relatives a un bloc d'origine, pour
 * garder la precision loin de l'origine du monde. Les memes tableaux servent au bloc d'uniformes des corps ({@link NeonLights}), au
 * calcul des tuiles ({@link LightSurfaces}) et a celui des rebonds ({@link LightBounces}).
 */
final class PortalLightUniforms {

    /** Cadres au plus, et valeurs de chacun. */
    static final int FRAMES = 4;
    static final int FRAME_FLOATS = 12;
    /** Neons vus par un portail au plus, et valeurs de chacun. */
    static final int SEEN = 16;
    static final int SEEN_FLOATS = 8;
    /** Rangs d'element suivis au plus : au-dela, un element est ignore. */
    private static final int TRACKED = 256;
    private static final int[] SLOTS = new int[TRACKED];
    /** Le rang dans la liste des portails de chaque cadre range. */
    private static final int[] FRAME_OPENINGS = new int[FRAMES];
    private static int frames;
    private static int seen;

    private PortalLightUniforms() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Range les cadres a portee d'un point, puis les neons qui passent par eux.
     *
     * @param openings portails de la zone
     * @param origin bloc d'origine : x, y, z
     * @param x point
     * @param y point
     * @param z point
     * @param range distance du point au centre d'un cadre, en blocs
     * @param frameValues cadres, {@value #FRAMES} fois {@value #FRAME_FLOATS} valeurs
     * @param seenValues neons vus, {@value #SEEN} fois {@value #SEEN_FLOATS} valeurs
     */
    static void pack(PortalOpenings openings, int[] origin, double x, double y, double z, double range, float[] frameValues, float[] seenValues) {
        frames = 0;
        seen = 0;
        int count = Math.min(openings.count(), TRACKED);
        Arrays.fill(SLOTS, 0, count, -1);
        for (int i = 0; i < count && frames < FRAMES; i++) {
            if (!openings.isFrame(i) || !inRange(openings, i, x, y, z, range)) {
                continue;
            }
            int at = frames * FRAME_FLOATS;
            for (int axis = 0; axis < 3; axis++) {
                frameValues[at + axis] = openings.center(i, axis) - origin[axis];
                // Demi-axes : du premier coin au deuxieme, du deuxieme au troisieme.
                frameValues[at + 4 + axis] = (openings.corner(i, 1, axis) - openings.corner(i, 0, axis)) * 0.5f;
                frameValues[at + 8 + axis] = (openings.corner(i, 2, axis) - openings.corner(i, 1, axis)) * 0.5f;
            }
            frameValues[at + 3] = openings.face(i);
            frameValues[at + 7] = openings.kind(i) == PortalOpenings.PASSAGE && openings.zone(i) != null ? 1.0f : 0.0f;
            frameValues[at + 11] = 0.0f;
            FRAME_OPENINGS[frames] = i;
            SLOTS[i] = frames++;
        }
        for (int i = 0; i < count && seen < SEEN; i++) {
            int passage = openings.passage(i);
            if (openings.kind(i) != PortalOpenings.VIRTUAL || passage >= count || SLOTS[passage] < 0) {
                continue;
            }
            int at = seen * SEEN_FLOATS;
            for (int axis = 0; axis < 3; axis++) {
                seenValues[at + axis] = openings.center(i, axis) - origin[axis];
                seenValues[at + 4 + axis] = openings.color(i, axis);
            }
            seenValues[at + 3] = openings.face(i);
            seenValues[at + 7] = SLOTS[passage];
            seen++;
        }
    }

    private static boolean inRange(PortalOpenings openings, int index, double x, double y, double z, double range) {
        double dx = openings.center(index, 0) - x;
        double dy = openings.center(index, 1) - y;
        double dz = openings.center(index, 2) - z;
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    /** Cadres ranges par le dernier {@link #pack}. */
    static int frames() {
        return frames;
    }

    /** Le rang dans la liste des portails d'un cadre range par le dernier {@link #pack}. */
    static int frameOpening(int slot) {
        return FRAME_OPENINGS[slot];
    }

    /** Neons vus ranges par le dernier {@link #pack}. */
    static int seen() {
        return seen;
    }
}
