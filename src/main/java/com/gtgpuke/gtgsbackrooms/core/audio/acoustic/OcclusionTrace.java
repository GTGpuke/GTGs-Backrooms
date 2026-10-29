package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

/**
 * Occlusion d'un son par les blocs entre sa source et l'auditeur : un rayon droit parcourt les blocs qu'il traverse
 * (parcours de voxels, un pas par face franchie), chaque bloc plein ou partiel retire sa part, jusqu'au plafond du
 * profil. Le bloc de la source et celui de l'auditeur ne comptent pas (un neon est dans son bloc, une tete dans l'air
 * d'un bloc). Logique pure : la solidite des blocs vient de l'appelant, sans rien allouer.
 */
public final class OcclusionTrace {

    /** Bloc traverse sans rien retirer. */
    public static final int AIR = 0;
    /** Bloc partiel : dalle, escalier, vitre, grille. */
    public static final int PARTIAL = 1;
    /** Bloc plein et opaque. */
    public static final int FULL = 2;
    /** Au-dela, le rayon s'arrete : un son si loin est de toute facon presque inaudible. */
    static final int MAX_STEPS = 64;

    private OcclusionTrace() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Solidite d'un bloc, par ses coordonnees : {@link #AIR}, {@link #PARTIAL} ou {@link #FULL}. */
    @FunctionalInterface
    public interface Solidity {

        int at(int x, int y, int z);
    }

    /**
     * Occlusion entre deux points, de 0 (rien entre eux) a {@code rule.max()}.
     *
     * @param sx source
     * @param sy source
     * @param sz source
     * @param lx auditeur
     * @param ly auditeur
     * @param lz auditeur
     * @param solidity solidite des blocs du monde du trajet
     * @param rule poids des blocs et plafond
     */
    public static float occlusion(double sx, double sy, double sz, double lx, double ly, double lz, Solidity solidity, AudioProfile.Occlusion rule) {
        if (!rule.any()) {
            return 0.0f;
        }
        int x = floor(sx);
        int y = floor(sy);
        int z = floor(sz);
        int endX = floor(lx);
        int endY = floor(ly);
        int endZ = floor(lz);
        if (x == endX && y == endY && z == endZ) {
            return 0.0f;
        }
        double dx = lx - sx;
        double dy = ly - sy;
        double dz = lz - sz;
        int stepX = step(dx);
        int stepY = step(dy);
        int stepZ = step(dz);
        double tMaxX = boundary(sx, x, dx, stepX);
        double tMaxY = boundary(sy, y, dy, stepY);
        double tMaxZ = boundary(sz, z, dz, stepZ);
        double tDeltaX = delta(dx);
        double tDeltaY = delta(dy);
        double tDeltaZ = delta(dz);
        float total = 0.0f;
        for (int steps = 0; steps < MAX_STEPS; steps++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }
            int solid = solidity.at(x, y, z);
            if (solid == FULL) {
                total += rule.wall();
            } else if (solid == PARTIAL) {
                total += rule.partialBlock();
            }
            if (total >= rule.max()) {
                return rule.max();
            }
        }
        return total;
    }

    /**
     * Occlusion de deux segments d'un meme trajet (de la source au cadre d'un portail dans son monde, puis du cadre a
     * l'auditeur dans celui du joueur) : ce que le second laisse passer de ce que le premier a laisse, sous le plafond.
     */
    public static float combine(float first, float second, AudioProfile.Occlusion rule) {
        return Math.min(rule.max(), 1.0f - (1.0f - first) * (1.0f - second));
    }

    /** Gain direct du filtre passe-bas pour une occlusion : les graves passent encore un peu a travers un mur. */
    public static float lowPassGain(float occlusion) {
        return 1.0f - occlusion;
    }

    /** Gain des aigus du filtre passe-bas : ils sont les premiers coupes par les blocs. */
    public static float lowPassGainHf(float occlusion) {
        float kept = 1.0f - occlusion;
        return kept * kept;
    }

    /**
     * Gain direct du filtre d'un son : son occlusion, puis le milieu ou l'auditeur ou la source est plonge.
     *
     * @param occlusion occlusion du son
     * @param medium part du gain que le milieu garde (1 hors de l'eau)
     */
    public static float filterGain(float occlusion, float medium) {
        return lowPassGain(occlusion) * medium;
    }

    /**
     * Gain des aigus du filtre d'un son : son occlusion, puis le milieu.
     *
     * @param occlusion occlusion du son
     * @param mediumHf part des aigus que le milieu garde (1 hors de l'eau)
     */
    public static float filterGainHf(float occlusion, float mediumHf) {
        return lowPassGainHf(occlusion) * mediumHf;
    }

    /**
     * Sans filtre (pas d'extension EFX) : la perte du filtre rendue par le seul volume du son, moyenne de ce que le filtre
     * garderait des graves et des aigus.
     *
     * @param gain gain direct du filtre
     * @param gainHf gain des aigus du filtre
     */
    public static float fallbackGain(float gain, float gainHf) {
        return 0.5f * (gain + gainHf);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int step(double direction) {
        return direction > 0.0 ? 1 : direction < 0.0 ? -1 : 0;
    }

    /** Distance (en fraction du trajet) jusqu'a la prochaine face franchie sur cet axe ; infinie si l'axe ne bouge pas. */
    private static double boundary(double start, int cell, double direction, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double next = step > 0 ? cell + 1.0 : cell;
        return (next - start) / direction;
    }

    private static double delta(double direction) {
        return direction == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction);
    }
}
