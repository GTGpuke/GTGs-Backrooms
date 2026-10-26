package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Volume visible a travers un cadre : la pyramide qui part de la camera de la vue et s'appuie sur les quatre aretes
 * du cadre d'arrivee, fermee par le plan du cadre du cote du contenu.
 *
 * <p>Rien de ce qui est hors de ce volume ne peut apparaitre dans la zone de vue : sections, entities et portails
 * imbriques sont ecartes avant d'etre dessines, la ou le frustum seul garderait tout le champ de la camera. Camera
 * dans le plan du cadre (au moment de la traversee), les aretes ne definissent plus de pyramide : seul le plan compte.
 * Rien n'est alloue : une instance se reutilise d'une image a l'autre.
 */
public final class ViewCone {

    private static final int CORNERS = 4;
    private static final int PLANES = CORNERS + 1;
    /** En deca de cette distance au plan, la camera est consideree dans le plan du cadre. */
    private static final double MIN_APEX_DISTANCE = 1.0e-3;
    /** Composante au-dela de laquelle une normale unitaire suit un axe (les autres sont alors nulles). */
    private static final double AXIS = 0.5;

    private final double[] normalX = new double[PLANES];
    private final double[] normalY = new double[PLANES];
    private final double[] normalZ = new double[PLANES];
    private final double[] offset = new double[PLANES];
    private final double[] cornerX = new double[CORNERS];
    private final double[] cornerY = new double[CORNERS];
    private final double[] cornerZ = new double[CORNERS];
    /** Boite des quatre coins du cadre, par axe (x, y, z) : la ou la pyramide franchit le plan du cadre. */
    private final double[] frameMin = new double[3];
    private final double[] frameMax = new double[3];
    private int planeCount;
    /** Camera du cote coupe du plan du cadre, a l'oppose du contenu : il y a un volume entre elle et le cadre. */
    private boolean apexBeforeFrame;

    /**
     * Definit le volume.
     *
     * @param apex camera de la vue
     * @param center centre du cadre d'arrivee
     * @param axisW axe unitaire de la largeur du cadre
     * @param axisH axe unitaire de la hauteur du cadre
     * @param normal normale du cadre, vers le contenu
     * @param halfWidth demi-largeur du cadre
     * @param halfHeight demi-hauteur du cadre
     */
    public void set(Vec3d apex, Vec3d center, Vec3d axisW, Vec3d axisH, Vec3d normal, double halfWidth, double halfHeight) {
        // Plan du cadre : on garde le cote du contenu.
        setPlane(0, normal.x, normal.y, normal.z, center.x, center.y, center.z);
        double apexDistance = normal.x * (apex.x - center.x) + normal.y * (apex.y - center.y) + normal.z * (apex.z - center.z);
        this.apexBeforeFrame = apexDistance < 0.0;
        for (int corner = 0; corner < CORNERS; corner++) {
            double sw = (corner == 1 || corner == 2) ? halfWidth : -halfWidth;
            double sh = corner >= 2 ? halfHeight : -halfHeight;
            this.cornerX[corner] = center.x + axisW.x * sw + axisH.x * sh;
            this.cornerY[corner] = center.y + axisW.y * sw + axisH.y * sh;
            this.cornerZ[corner] = center.z + axisW.z * sw + axisH.z * sh;
        }
        // Les coins opposes 0 et 2 bornent le cadre sur chaque axe.
        this.frameMin[0] = Math.min(this.cornerX[0], this.cornerX[2]);
        this.frameMax[0] = Math.max(this.cornerX[0], this.cornerX[2]);
        this.frameMin[1] = Math.min(this.cornerY[0], this.cornerY[2]);
        this.frameMax[1] = Math.max(this.cornerY[0], this.cornerY[2]);
        this.frameMin[2] = Math.min(this.cornerZ[0], this.cornerZ[2]);
        this.frameMax[2] = Math.max(this.cornerZ[0], this.cornerZ[2]);
        if (Math.abs(apexDistance) < MIN_APEX_DISTANCE) {
            this.planeCount = 1;
            return;
        }
        for (int corner = 0; corner < CORNERS; corner++) {
            int next = (corner + 1) % CORNERS;
            double ax = this.cornerX[corner] - apex.x;
            double ay = this.cornerY[corner] - apex.y;
            double az = this.cornerZ[corner] - apex.z;
            double bx = this.cornerX[next] - apex.x;
            double by = this.cornerY[next] - apex.y;
            double bz = this.cornerZ[next] - apex.z;
            // Plan (camera, arete) ; sa normale regarde vers le centre du cadre.
            double nx = ay * bz - az * by;
            double ny = az * bx - ax * bz;
            double nz = ax * by - ay * bx;
            double towardCenter = nx * (center.x - apex.x) + ny * (center.y - apex.y) + nz * (center.z - apex.z);
            if (towardCenter < 0.0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            setPlane(corner + 1, nx, ny, nz, apex.x, apex.y, apex.z);
        }
        this.planeCount = PLANES;
    }

    /** Aucune limite : tout ce que voit la camera, quand elle est elle-meme passee de l'autre cote du cadre. */
    public void unbounded() {
        this.planeCount = 0;
    }

    /**
     * {@code true} si la camera est en deca du cadre, a distance de son plan : le volume commence au cadre, loin d'elle, et
     * rien de ce qui les separe n'en fait partie. Un parcours des sections du volume part alors du cadre.
     */
    public boolean startsAtFrame() {
        return this.planeCount == PLANES && this.apexBeforeFrame;
    }

    /** Plus petite coordonnee du cadre sur un axe (0 x, 1 y, 2 z). */
    public double frameMin(int axis) {
        return this.frameMin[axis];
    }

    /** Plus grande coordonnee du cadre sur un axe (0 x, 1 y, 2 z). */
    public double frameMax(int axis) {
        return this.frameMax[axis];
    }

    private void setPlane(int index, double nx, double ny, double nz, double px, double py, double pz) {
        this.normalX[index] = nx;
        this.normalY[index] = ny;
        this.normalZ[index] = nz;
        this.offset[index] = -(nx * px + ny * py + nz * pz);
    }

    /** {@code true} si la boite touche le volume : test conservateur sur le coin le plus avance de chaque plan. */
    public boolean intersects(Box box) {
        for (int i = 0; i < this.planeCount; i++) {
            double nx = this.normalX[i];
            double ny = this.normalY[i];
            double nz = this.normalZ[i];
            double farthest = nx * (nx > 0.0 ? box.maxX : box.minX)
                    + ny * (ny > 0.0 ? box.maxY : box.minY)
                    + nz * (nz > 0.0 ? box.maxZ : box.minZ)
                    + this.offset[i];
            if (farthest < 0.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code true} si la boite touche la partie de la pyramide en deca du cadre, entre la camera et son plan : ce qu'une
     * vue dessinee sans coupe au plan d'arrivee montrerait par-dessus son contenu. Toujours faux sans un tel volume (camera
     * du cote du contenu ou dans le plan, volume sans limite). La boite est d'abord coupee au plan du cadre, cote camera,
     * quand la normale suit un axe (celle de tout cadre de portail) : un mur ou un sol a cheval sur le plan, qui borde le
     * cadre sans entrer dans la pyramide, n'y touche pas ; puis meme test conservateur que {@link #intersects}, le plan du
     * cadre retourne. Coordonnees en primitifs : le test parcourt des blocs entiers, sans allocation.
     */
    public boolean intersectsBeforeFrame(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (this.planeCount != PLANES || !this.apexBeforeFrame) {
            return false;
        }
        // Cote camera du plan du cadre : normale . p <= limite.
        double limit = -this.offset[0];
        double lowX = this.normalX[0] < -AXIS ? Math.max(minX, -limit) : minX;
        double highX = this.normalX[0] > AXIS ? Math.min(maxX, limit) : maxX;
        double lowY = this.normalY[0] < -AXIS ? Math.max(minY, -limit) : minY;
        double highY = this.normalY[0] > AXIS ? Math.min(maxY, limit) : maxY;
        double lowZ = this.normalZ[0] < -AXIS ? Math.max(minZ, -limit) : minZ;
        double highZ = this.normalZ[0] > AXIS ? Math.min(maxZ, limit) : maxZ;
        if (lowX > highX || lowY > highY || lowZ > highZ) {
            return false;
        }
        for (int i = 0; i < PLANES; i++) {
            double sign = i == 0 ? -1.0 : 1.0;
            double nx = sign * this.normalX[i];
            double ny = sign * this.normalY[i];
            double nz = sign * this.normalZ[i];
            double farthest = nx * (nx > 0.0 ? highX : lowX)
                    + ny * (ny > 0.0 ? highY : lowY)
                    + nz * (nz > 0.0 ? highZ : lowZ)
                    + sign * this.offset[i];
            if (farthest < 0.0) {
                return false;
            }
        }
        return true;
    }
}
