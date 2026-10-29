package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import java.util.Arrays;

import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Les cadres de portail qu'un rayon de reverb peut traverser : jusqu'a {@value #CAPACITY} portails ouverts du monde de
 * la source, chacun avec son rectangle (centre, axes, demi-largeur, demi-hauteur, normale du cote d'entree), son cadre
 * d'arrivee (centre, axes) et la copie du monde d'arrivee. Un rayon qui touche l'ouverture depuis le cote d'entree
 * continue de l'autre cote, transpose comme ce qui traverse (la regle de {@code PortalFacing} : la largeur et la
 * normale s'inversent, la hauteur se garde). Rempli sur le thread de rendu, copie dans la demande, lu par le thread de
 * calcul ; rien n'est alloue apres la creation.
 */
public final class PortalPlanes {

    /** Portails par demande, au plus. */
    public static final int CAPACITY = 4;
    private static final int STRIDE = 26;
    private static final int CENTER = 0;
    private static final int AXIS_W = 3;
    private static final int AXIS_H = 6;
    private static final int NORMAL = 9;
    private static final int HALF_W = 12;
    private static final int HALF_H = 13;
    private static final int DEST_CENTER = 14;
    private static final int DEST_W = 17;
    private static final int DEST_H = 20;
    private static final int DEST_N = 23;
    /** Un cadre plus pres que cela sur le rayon est celui d'ou il part : ignore. */
    private static final double MIN_DISTANCE = 1.0e-6;

    private final double[] data = new double[CAPACITY * STRIDE];
    private final SolidityGrid[] destinations = new SolidityGrid[CAPACITY];
    private int count;
    private double hitDistance;
    private double pointX;
    private double pointY;
    private double pointZ;
    private double directionX;
    private double directionY;
    private double directionZ;

    /** Plus aucun portail. */
    public void clear() {
        this.count = 0;
        Arrays.fill(this.destinations, null);
    }

    /** Nombre de portails. */
    public int count() {
        return this.count;
    }

    /**
     * Ajoute un portail.
     *
     * @param source orientation du cadre traverse
     * @param center centre du cadre traverse
     * @param width largeur du cadre
     * @param height hauteur du cadre
     * @param destination orientation du cadre d'arrivee
     * @param arrival centre du cadre d'arrivee
     * @param grid copie du monde d'arrivee
     * @return {@code false} si la liste est pleine
     */
    public boolean add(PortalFacing source, Vec3d center, float width, float height, PortalFacing destination, Vec3d arrival, SolidityGrid grid) {
        if (this.count == CAPACITY) {
            return false;
        }
        int base = this.count * STRIDE;
        put(base + CENTER, center);
        put(base + AXIS_W, source.axisW);
        put(base + AXIS_H, source.axisH);
        put(base + NORMAL, source.normal);
        this.data[base + HALF_W] = width / 2.0;
        this.data[base + HALF_H] = height / 2.0;
        put(base + DEST_CENTER, arrival);
        put(base + DEST_W, destination.axisW);
        put(base + DEST_H, destination.axisH);
        put(base + DEST_N, destination.normal);
        this.destinations[this.count++] = grid;
        return true;
    }

    /** Reprend les portails d'une autre liste. */
    public void copyFrom(PortalPlanes other) {
        System.arraycopy(other.data, 0, this.data, 0, other.count * STRIDE);
        System.arraycopy(other.destinations, 0, this.destinations, 0, CAPACITY);
        this.count = other.count;
    }

    /** Copie du monde d'arrivee d'un portail. */
    public SolidityGrid destination(int index) {
        return this.destinations[index];
    }

    /** Centre du cadre d'arrivee d'un portail, X. */
    public double arrivalX(int index) {
        return this.data[index * STRIDE + DEST_CENTER];
    }

    /** Centre du cadre d'arrivee d'un portail, Y. */
    public double arrivalY(int index) {
        return this.data[index * STRIDE + DEST_CENTER + 1];
    }

    /** Centre du cadre d'arrivee d'un portail, Z. */
    public double arrivalZ(int index) {
        return this.data[index * STRIDE + DEST_CENTER + 2];
    }

    /**
     * Le premier portail qu'un rayon traverse avant une distance donnee, depuis le cote d'entree : sa distance reste
     * dans {@link #hitDistance()}.
     *
     * @return l'index du portail, ou -1 si le rayon n'en traverse aucun
     */
    public int intersect(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        int best = -1;
        double bestDistance = maxDistance;
        for (int i = 0; i < this.count; i++) {
            int base = i * STRIDE;
            double along = dot(base + NORMAL, dx, dy, dz);
            if (along >= 0.0) {
                // Vers le cote d'entree ou le long du plan : un cadre ne se traverse que d'avant en arriere.
                continue;
            }
            double distance = dot(base + NORMAL, this.data[base + CENTER] - ox, this.data[base + CENTER + 1] - oy, this.data[base + CENTER + 2] - oz) / along;
            if (distance <= MIN_DISTANCE || distance >= bestDistance) {
                continue;
            }
            double rx = ox + dx * distance - this.data[base + CENTER];
            double ry = oy + dy * distance - this.data[base + CENTER + 1];
            double rz = oz + dz * distance - this.data[base + CENTER + 2];
            if (Math.abs(dot(base + AXIS_W, rx, ry, rz)) > this.data[base + HALF_W] || Math.abs(dot(base + AXIS_H, rx, ry, rz)) > this.data[base + HALF_H]) {
                continue;
            }
            best = i;
            bestDistance = distance;
        }
        this.hitDistance = bestDistance;
        return best;
    }

    /** Distance du dernier portail trouve par {@link #intersect}. */
    public double hitDistance() {
        return this.hitDistance;
    }

    /**
     * Fait passer un point du cadre et une direction dans le monde d'arrivee d'un portail : les resultats se lisent
     * dans {@link #pointX()} et {@link #directionX()} (et Y, Z).
     */
    public void transform(int index, double x, double y, double z, double dx, double dy, double dz) {
        int base = index * STRIDE;
        double rx = x - this.data[base + CENTER];
        double ry = y - this.data[base + CENTER + 1];
        double rz = z - this.data[base + CENTER + 2];
        double w = dot(base + AXIS_W, rx, ry, rz);
        double h = dot(base + AXIS_H, rx, ry, rz);
        double n = dot(base + NORMAL, rx, ry, rz);
        this.pointX = this.data[base + DEST_CENTER] + rotated(base, 0, w, h, n);
        this.pointY = this.data[base + DEST_CENTER + 1] + rotated(base, 1, w, h, n);
        this.pointZ = this.data[base + DEST_CENTER + 2] + rotated(base, 2, w, h, n);
        double dw = dot(base + AXIS_W, dx, dy, dz);
        double dh = dot(base + AXIS_H, dx, dy, dz);
        double dn = dot(base + NORMAL, dx, dy, dz);
        this.directionX = rotated(base, 0, dw, dh, dn);
        this.directionY = rotated(base, 1, dw, dh, dn);
        this.directionZ = rotated(base, 2, dw, dh, dn);
    }

    /** Point transpose par {@link #transform}, X. */
    public double pointX() {
        return this.pointX;
    }

    /** Point transpose, Y. */
    public double pointY() {
        return this.pointY;
    }

    /** Point transpose, Z. */
    public double pointZ() {
        return this.pointZ;
    }

    /** Direction transposee par {@link #transform}, X. */
    public double directionX() {
        return this.directionX;
    }

    /** Direction transposee, Y. */
    public double directionY() {
        return this.directionY;
    }

    /** Direction transposee, Z. */
    public double directionZ() {
        return this.directionZ;
    }

    private void put(int at, Vec3d vector) {
        this.data[at] = vector.x;
        this.data[at + 1] = vector.y;
        this.data[at + 2] = vector.z;
    }

    private double dot(int at, double x, double y, double z) {
        return this.data[at] * x + this.data[at + 1] * y + this.data[at + 2] * z;
    }

    /** Une coordonnee dans la base d'arrivee : la largeur et la normale s'inversent, la hauteur se garde. */
    private double rotated(int base, int axis, double w, double h, double n) {
        return this.data[base + DEST_W + axis] * -w + this.data[base + DEST_H + axis] * h + this.data[base + DEST_N + axis] * -n;
    }
}
