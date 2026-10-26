package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Geometrie partagee entre le serveur et le client pour les chunks distants.
 *
 * <p>Le client range les chunks d'une dimension distante dans une carte carree centree sur le premier
 * chunk recu (regle vanilla : rayon = max(2, distance) + 3). Le serveur ne doit donc envoyer que des
 * chunks qui tiennent dans cette carte, quelle que soit la position de ce premier chunk dans la fenetre
 * du foyer : d'ou la marge de deux rayons de fenetre dans {@link #fitsAround}.
 */
public final class RemoteChunkWindow {

    /** Distance de chargement, en chunks, donnee au monde client distant. */
    public static final int LOAD_DISTANCE = 32;
    /** Rayon de la carte de chunks du client distant, en chunks. */
    public static final int MAP_RADIUS = Math.max(2, LOAD_DISTANCE) + 3;
    /** En deca de cette profondeur, en blocs, la camera de la vue n'est plus devant le cadre : la vue ne se voit pas, pas de coin de vue. */
    public static final double MIN_VIEW_DEPTH = 1.0;
    /** Cote d'un chunk, en blocs. */
    private static final double CHUNK = 16.0;
    /** Un chunk et ses quatre voisins, decalages en chunks. */
    private static final int NEIGHBOURHOOD = 5;
    private static final int[] NEIGHBOUR_X = {0, 1, -1, 0, 0};
    private static final int[] NEIGHBOUR_Z = {0, 0, 0, 1, -1};

    private RemoteChunkWindow() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Ajoute a {@code out} les chunks de la fenetre carree, du centre vers l'exterieur ; un chunk deja present n'est pas
     * repete.
     *
     * @param centerX chunk central X
     * @param centerZ chunk central Z
     * @param radius rayon de la fenetre en chunks (0 = le seul chunk central)
     * @param out cles {@link ChunkPos#toLong(int, int)} a completer ; un ensemble ordonne garde l'ordre d'ajout
     */
    public static void collect(int centerX, int centerZ, int radius, LongSet out) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    out.add(ChunkPos.toLong(centerX + dx, centerZ + dz));
                }
            }
        }
    }

    /**
     * Fenetre d'un cadre debout regarde de loin : au plus autant de chunks que la fenetre carree de rayon {@code radius},
     * mais dans l'axe de ce que montre le cadre (decision de l'auteur du 26 octobre 2026 : au loin, la vue montrait le fond
     * de son ciel au-dela de la fenetre carree). D'abord le carre de rayon 1 autour de l'arrivee ; puis, anneau par anneau
     * autour d'elle, chaque chunk que touche, vu de dessus, le coin de vue au-dela du cadre (celui qui part de la camera de
     * la vue, l'image de l'oeil du joueur dans le monde d'arrivee, et s'appuie sur les deux bords du cadre), avec ses
     * quatre voisins : chez le client, une section ne se construit qu'avec eux. Arret au premier chunk dont l'ajout
     * depasserait le compte, ou quand un voisin sortirait de {@code maxReach}. Camera a moins d'un bloc en deca du plan du
     * cadre (a cote de lui, ou derriere) : pas de coin de vue, rien n'est ajoute. Le compte ne porte que sur les chunks que
     * ce portail ajoute : ceux que la fenetre d'un autre portail y a deja mis sont gratuits.
     *
     * @param apexX camera de la vue, en blocs
     * @param apexZ camera de la vue, en blocs
     * @param centerX centre du cadre d'arrivee, en blocs
     * @param centerZ centre du cadre d'arrivee, en blocs
     * @param axisX axe de la largeur du cadre, unitaire et horizontal
     * @param axisZ axe de la largeur du cadre, unitaire et horizontal
     * @param normalX normale du cadre, horizontale, du cote de ce qu'il montre
     * @param normalZ normale du cadre, horizontale, du cote de ce qu'il montre
     * @param halfWidth demi-largeur du cadre, en blocs
     * @param radius rayon de la fenetre carree dont le compte de chunks est repris
     * @param maxReach ecart maximal, en chunks, entre un chunk ajoute et celui de l'arrivee
     * @param out cles {@link ChunkPos#toLong(int, int)} a completer ; un ensemble ordonne garde l'ordre d'ajout
     * @return nombre de chunks ajoutes, 0 sans coin de vue
     */
    public static int collectCone(double apexX, double apexZ, double centerX, double centerZ, double axisX, double axisZ,
            double normalX, double normalZ, double halfWidth, int radius, int maxReach, LongSet out) {
        if (normalX * (centerX - apexX) + normalZ * (centerZ - apexZ) < MIN_VIEW_DEPTH) {
            return 0;
        }
        int start = out.size();
        int limit = start + (2 * radius + 1) * (2 * radius + 1);
        int arrivalX = ChunkSectionPos.getSectionCoord(centerX);
        int arrivalZ = ChunkSectionPos.getSectionCoord(centerZ);
        collect(arrivalX, arrivalZ, Math.min(1, radius), out);
        // Bords du coin de vue : la droite qui part de la camera par chaque bord du cadre, normale tournee vers l'autre bord.
        double leftX = centerX - axisX * halfWidth;
        double leftZ = centerZ - axisZ * halfWidth;
        double rightX = centerX + axisX * halfWidth;
        double rightZ = centerZ + axisZ * halfWidth;
        double leftNormalX = apexZ - leftZ;
        double leftNormalZ = leftX - apexX;
        if (leftNormalX * (rightX - apexX) + leftNormalZ * (rightZ - apexZ) < 0.0) {
            leftNormalX = -leftNormalX;
            leftNormalZ = -leftNormalZ;
        }
        double rightNormalX = apexZ - rightZ;
        double rightNormalZ = rightX - apexX;
        if (rightNormalX * (leftX - apexX) + rightNormalZ * (leftZ - apexZ) < 0.0) {
            rightNormalX = -rightNormalX;
            rightNormalZ = -rightNormalZ;
        }
        for (int ring = 0; ring < maxReach; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    double minX = ChunkSectionPos.getBlockCoord(arrivalX + dx);
                    double minZ = ChunkSectionPos.getBlockCoord(arrivalZ + dz);
                    if (!touches(normalX, normalZ, centerX, centerZ, minX, minZ)
                            || !touches(leftNormalX, leftNormalZ, apexX, apexZ, minX, minZ)
                            || !touches(rightNormalX, rightNormalZ, apexX, apexZ, minX, minZ)) {
                        continue;
                    }
                    if (!addWithNeighbours(arrivalX + dx, arrivalZ + dz, limit, out)) {
                        return out.size() - start;
                    }
                }
            }
        }
        return out.size() - start;
    }

    /** {@code true} si le chunk dont le coin bas est en ({@code minX}, {@code minZ}) touche le demi-plan : test conservateur sur son coin le plus avance. */
    private static boolean touches(double normalX, double normalZ, double pointX, double pointZ, double minX, double minZ) {
        double farthest = normalX * (normalX > 0.0 ? minX + CHUNK : minX) + normalZ * (normalZ > 0.0 ? minZ + CHUNK : minZ);
        return farthest >= normalX * pointX + normalZ * pointZ;
    }

    /** Ajoute un chunk et ses quatre voisins si {@code out} reste sous {@code limit} ; {@code false} sinon, sans rien ajouter. */
    private static boolean addWithNeighbours(int x, int z, int limit, LongSet out) {
        int missing = 0;
        for (int i = 0; i < NEIGHBOURHOOD; i++) {
            if (!out.contains(ChunkPos.toLong(x + NEIGHBOUR_X[i], z + NEIGHBOUR_Z[i]))) {
                missing++;
            }
        }
        if (out.size() + missing > limit) {
            return false;
        }
        for (int i = 0; i < NEIGHBOURHOOD; i++) {
            out.add(ChunkPos.toLong(x + NEIGHBOUR_X[i], z + NEIGHBOUR_Z[i]));
        }
        return true;
    }

    /**
     * {@code true} si une fenetre de rayon {@code radius} centree en ({@code x}, {@code z}) tient dans
     * la carte client, celle-ci etant centree quelque part dans la fenetre du foyer.
     *
     * @param anchorX chunk central du foyer X
     * @param anchorZ chunk central du foyer Z
     * @param x chunk central de la fenetre candidate X
     * @param z chunk central de la fenetre candidate Z
     * @param radius rayon des fenetres en chunks
     * @return {@code true} si tous les chunks de la fenetre candidate seront acceptes par le client
     */
    public static boolean fitsAround(int anchorX, int anchorZ, int x, int z, int radius) {
        int distance = Math.max(Math.abs(x - anchorX), Math.abs(z - anchorZ));
        return distance + 2 * radius <= MAP_RADIUS;
    }
}
