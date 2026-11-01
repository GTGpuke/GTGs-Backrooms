package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

/**
 * Un rayon dans la grille des blocs, cellule par cellule : seuls les blocs pleins l'arretent. Le dernier arret se lit dans
 * ses champs : la derniere cellule d'air, la face touchee (sa normale sort vers cette cellule) et la matiere du bloc.
 */
final class BlockRay {

    private static final float NEVER = Float.POSITIVE_INFINITY;

    private int cellX;
    private int cellY;
    private int cellZ;
    private int face;
    private byte block;
    private float distance;

    /**
     * Marche jusqu'au premier bloc opaque.
     *
     * @param window blocs autour du rayon
     * @param ox depart, dans une cellule d'air
     * @param oy depart
     * @param oz depart
     * @param dx direction : la longueur de ce vecteur vaut un pas de {@code limit}
     * @param dy direction
     * @param dz direction
     * @param limit longueur de la marche, en longueurs de la direction
     * @return {@code true} si un bloc opaque arrete le rayon
     */
    boolean march(LightWindow window, float ox, float oy, float oz, float dx, float dy, float dz, float limit) {
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        int stepX = dx > 0.0f ? 1 : -1;
        int stepY = dy > 0.0f ? 1 : -1;
        int stepZ = dz > 0.0f ? 1 : -1;
        float deltaX = dx == 0.0f ? NEVER : Math.abs(1.0f / dx);
        float deltaY = dy == 0.0f ? NEVER : Math.abs(1.0f / dy);
        float deltaZ = dz == 0.0f ? NEVER : Math.abs(1.0f / dz);
        float nextX = dx == 0.0f ? NEVER : (dx > 0.0f ? x + 1 - ox : ox - x) * deltaX;
        float nextY = dy == 0.0f ? NEVER : (dy > 0.0f ? y + 1 - oy : oy - y) * deltaY;
        float nextZ = dz == 0.0f ? NEVER : (dz > 0.0f ? z + 1 - oz : oz - z) * deltaZ;
        while (true) {
            int airX = x;
            int airY = y;
            int airZ = z;
            int hit;
            float distance;
            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                nextX += deltaX;
                x += stepX;
                hit = stepX > 0 ? 1 : 0;
            } else if (nextY <= nextZ) {
                distance = nextY;
                nextY += deltaY;
                y += stepY;
                hit = stepY > 0 ? 3 : 2;
            } else {
                distance = nextZ;
                nextZ += deltaZ;
                z += stepZ;
                hit = stepZ > 0 ? 5 : 4;
            }
            if (distance > limit) {
                return false;
            }
            byte material = window.block(x, y, z);
            if (material != LightBlocks.AIR) {
                this.cellX = airX;
                this.cellY = airY;
                this.cellZ = airZ;
                this.face = hit;
                this.block = material;
                this.distance = distance;
                return true;
            }
        }
    }

    /** {@code true} si aucun bloc opaque ne coupe le segment entre deux points. */
    boolean clear(LightWindow window, float ax, float ay, float az, float bx, float by, float bz) {
        return !march(window, ax, ay, az, bx - ax, by - ay, bz - az, 1.0f);
    }

    /** Derniere cellule d'air, sur X. */
    int cellX() {
        return this.cellX;
    }

    /** Derniere cellule d'air, sur Y. */
    int cellY() {
        return this.cellY;
    }

    /** Derniere cellule d'air, sur Z. */
    int cellZ() {
        return this.cellZ;
    }

    /** Face touchee ; sa normale sort vers la derniere cellule d'air. */
    int face() {
        return this.face;
    }

    /** Longueur de la marche jusqu'au bloc touche, en longueurs de la direction. */
    float distance() {
        return this.distance;
    }

    /** Matiere du bloc touche. */
    byte block() {
        return this.block;
    }
}
