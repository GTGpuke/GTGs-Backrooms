package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Preuve qu'un cadre est cache derriere des blocs, refaite a chaque image : une tranche d'un bloc d'epaisseur, entre la
 * camera et le cadre, dont chaque case que traverse le cone de la camera vers le cadre est un bloc plein et opaque deja
 * affiche, devant une case libre du cote de la camera. Tout rayon de la camera vers un point du cadre entre dans l'un de
 * ces blocs par sa face tournee vers la camera, dessinee puisque la case d'ou il vient n'est pas un bloc plein (vanilla ne
 * dessine pas la face commune a deux blocs pleins) : aucun pixel du cadre ne se voit, et la vue n'a rien a dessiner.
 *
 * <p>La preuve est prudente : elle ne declare cache que ce qui l'est, et ce qu'elle ne sait pas trancher (escaliers,
 * dalles, verre, entities) reste dessine. Une tranche se teste sur un plan de bloc perpendiculaire a un axe, strictement
 * entre la camera et le cadre : celui ou les rayons entrent dans la tranche. La section du cone par ce plan est le
 * quadrilatere ou passent les rayons vers les quatre coins ; ses cases sont celles de son rectangle englobant, elargi d'un
 * epsilon (un rayon sur l'arete commune de deux cases touche les deux). Une camera dans un bloc plein et opaque voit a
 * travers lui, les faces entre deux blocs pleins ne se dessinant pas : rien n'est alors prouve.
 *
 * <p>Sans allocation, sur le thread de rendu seulement.
 */
public final class FrameOcclusion {

    /** Cases d'un monde, telles qu'elles sont et telles qu'elles sont deja affichees. */
    public interface Occluders {

        /** {@code true} si la case est un bloc plein et opaque, maintenant. */
        boolean opaque(int x, int y, int z);

        /** {@code true} si la case est un bloc plein et opaque, deja affiche dans sa version courante. */
        boolean shown(int x, int y, int z);
    }

    /** Elargissement du rectangle de section : un rayon sur une arete de case touche les deux cases. */
    private static final double EDGE_EPSILON = 1.0e-6;
    /** Ecart minimal entre le plan d'entree et le cadre : la zone du cadre se dessine un dixieme de millimetre en avant de lui. */
    private static final double FRAME_MARGIN = 1.0e-3;
    private static final int AXES = 3;
    private static final int CORNERS = 4;
    /** Coins du cadre, trois coordonnees par coin, puis la camera : etat de travail du thread de rendu. */
    private static final double[] CORNER = new double[CORNERS * AXES];
    private static final double[] CAMERA = new double[AXES];
    private static final int[] CELL = new int[AXES];

    private FrameOcclusion() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} si une tranche de blocs pleins et opaques separe la camera du cadre entier.
     *
     * @param camera position de la camera
     * @param center centre du cadre
     * @param axisW direction de sa largeur, unitaire
     * @param axisH direction de sa hauteur, unitaire
     * @param halfWidth demi-largeur du cadre
     * @param halfHeight demi-hauteur du cadre
     * @param occluders cases du monde du cadre
     */
    public static boolean hidden(Vec3d camera, Vec3d center, Vec3d axisW, Vec3d axisH, double halfWidth, double halfHeight, Occluders occluders) {
        if (occluders.opaque(MathHelper.floor(camera.x), MathHelper.floor(camera.y), MathHelper.floor(camera.z))) {
            return false;
        }
        CAMERA[0] = camera.x;
        CAMERA[1] = camera.y;
        CAMERA[2] = camera.z;
        for (int i = 0; i < CORNERS; i++) {
            double w = (i & 1) == 0 ? -halfWidth : halfWidth;
            double h = (i & 2) == 0 ? -halfHeight : halfHeight;
            CORNER[i * AXES] = center.x + axisW.x * w + axisH.x * h;
            CORNER[i * AXES + 1] = center.y + axisW.y * w + axisH.y * h;
            CORNER[i * AXES + 2] = center.z + axisW.z * w + axisH.z * h;
        }
        for (int axis = 0; axis < AXES; axis++) {
            if (hiddenAlong(axis, occluders)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tranches perpendiculaires a un axe, de la camera vers le cadre, quand le cadre entier est d'un meme cote de la camera
     * sur cet axe : la premiere toute pleine prouve le cadre cache.
     */
    private static boolean hiddenAlong(int axis, Occluders occluders) {
        double camera = CAMERA[axis];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < CORNERS; i++) {
            min = Math.min(min, CORNER[i * AXES + axis]);
            max = Math.max(max, CORNER[i * AXES + axis]);
        }
        if (camera < min) {
            // Rayons vers les coordonnees croissantes : ils entrent dans la tranche [e, e + 1) par le plan e.
            int last = MathHelper.floor(min - FRAME_MARGIN);
            for (int plane = MathHelper.floor(camera) + 1; plane <= last; plane++) {
                if (planeOpaque(axis, plane, plane, plane - 1, occluders)) {
                    return true;
                }
            }
        } else if (camera > max) {
            // Rayons vers les coordonnees decroissantes : ils entrent dans la tranche [e - 1, e) par le plan e.
            int last = MathHelper.ceil(max + FRAME_MARGIN);
            for (int plane = MathHelper.ceil(camera) - 1; plane >= last; plane--) {
                if (planeOpaque(axis, plane, plane - 1, plane, occluders)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code true} si toutes les cases de la tranche {@code layer} que les rayons vers le cadre traversent en passant le plan
     * {@code plane} sont des blocs pleins et opaques deja affiches, chacun devant une case libre de la tranche {@code front},
     * du cote de la camera ; s'arrete a la premiere qui ne l'est pas.
     */
    private static boolean planeOpaque(int axis, int plane, int layer, int front, Occluders occluders) {
        int u = (axis + 1) % AXES;
        int v = (axis + 2) % AXES;
        double camera = CAMERA[axis];
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < CORNERS; i++) {
            // Fraction du rayon vers ce coin a laquelle il passe le plan : entre 0 et 1, le coin etant au-dela du plan.
            double t = (plane - camera) / (CORNER[i * AXES + axis] - camera);
            double pointU = CAMERA[u] + t * (CORNER[i * AXES + u] - CAMERA[u]);
            double pointV = CAMERA[v] + t * (CORNER[i * AXES + v] - CAMERA[v]);
            minU = Math.min(minU, pointU);
            maxU = Math.max(maxU, pointU);
            minV = Math.min(minV, pointV);
            maxV = Math.max(maxV, pointV);
        }
        int lastU = MathHelper.floor(maxU + EDGE_EPSILON);
        int lastV = MathHelper.floor(maxV + EDGE_EPSILON);
        for (int cellU = MathHelper.floor(minU - EDGE_EPSILON); cellU <= lastU; cellU++) {
            for (int cellV = MathHelper.floor(minV - EDGE_EPSILON); cellV <= lastV; cellV++) {
                CELL[u] = cellU;
                CELL[v] = cellV;
                CELL[axis] = layer;
                if (!occluders.shown(CELL[0], CELL[1], CELL[2])) {
                    return false;
                }
                // Un bloc plein devant lui : leur face commune ne se dessine pas, et le bloc de devant n'est peut-etre pas
                // encore affiche ; un rayon pourrait passer par les deux.
                CELL[axis] = front;
                if (occluders.opaque(CELL[0], CELL[1], CELL[2])) {
                    return false;
                }
            }
        }
        return true;
    }
}
