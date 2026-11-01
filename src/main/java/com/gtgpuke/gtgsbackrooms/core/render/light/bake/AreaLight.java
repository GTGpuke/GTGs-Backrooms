package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

/**
 * Ce qu'une dalle donne a une surface. Une dalle est un rectangle oriente qui n'eclaire que devant lui : rien au-dessus de son
 * plan, rien par ses cotes. La lumiere part du point du rectangle le plus proche de la surface, s'ouvre selon l'angle du profil
 * et s'eteint a sa portee sans cassure : le sol sous une dalle recoit plus qu'un mur qu'elle frole, sans point brulant pres
 * d'elle. Le shader du cache de surfaces fait le meme calcul.
 */
final class AreaLight {

    /** Valeurs d'un rectangle : ses quatre coins. */
    static final int VALUES = 12;
    private static final int CORNERS = 4;
    /** Part du carre de la portee a laquelle la lumiere a perdu la moitie de sa force, avant la fin de la portee. */
    private static final float SOFT_SHARE = 0.16f;

    private AreaLight() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Ce qu'il reste de la lumiere d'une dalle a cette distance : 1 pres d'elle, 0 a sa portee, sans cassure. Une coupure nette
     * dessinerait un arc sur le sol, au bord de la portee de chaque dalle ; une chute en carre de la distance brulerait tout ce
     * qui la frole.
     *
     * @param distance2 carre de la distance a la dalle
     * @param reach2 carre de la portee
     */
    static float falloff(float distance2, float reach2) {
        float share = distance2 / reach2;
        float left = Math.clamp(1.0f - share * share, 0.0f, 1.0f);
        return left * left / (1.0f + share / SOFT_SHARE);
    }

    /**
     * Ce qu'une dalle donne a un point d'une surface, de 0 a 1.
     *
     * @param rects rectangles, {@value #VALUES} valeurs chacun : les quatre coins ecrits par {@link #blockFace}
     * @param at premier indice du rectangle
     * @param face face du bloc qui eclaire
     * @param x point de la surface
     * @param y point de la surface
     * @param z point de la surface
     * @param nx normale de la surface
     * @param ny normale de la surface
     * @param nz normale de la surface
     * @param reach portee de la dalle, en blocs
     * @param cone cosinus de l'angle ou la dalle cesse d'eclairer, compte depuis sa normale
     * @return 0 pour un point derriere la dalle, hors de sa portee ou de son angle, ou une surface qui lui tourne le dos
     */
    static float received(float[] rects, int at, int face, float x, float y, float z, float nx, float ny, float nz, float reach, float cone) {
        float toX = Math.clamp(x, Math.min(rects[at], rects[at + 6]), Math.max(rects[at], rects[at + 6])) - x;
        float toY = Math.clamp(y, Math.min(rects[at + 1], rects[at + 7]), Math.max(rects[at + 1], rects[at + 7])) - y;
        float toZ = Math.clamp(z, Math.min(rects[at + 2], rects[at + 8]), Math.max(rects[at + 2], rects[at + 8])) - z;
        float front = -(toX * LightCell.NORMAL_X[face] + toY * LightCell.NORMAL_Y[face] + toZ * LightCell.NORMAL_Z[face]);
        float distance2 = toX * toX + toY * toY + toZ * toZ;
        if (front <= 0.0f || distance2 >= reach * reach) {
            return 0.0f;
        }
        float distance = (float) Math.sqrt(distance2);
        float facing = (toX * nx + toY * ny + toZ * nz) / distance;
        float emitted = (front / distance - cone) / (1.0f - cone);
        if (facing <= 0.0f || emitted <= 0.0f) {
            return 0.0f;
        }
        return facing * emitted * falloff(distance2, reach * reach);
    }

    /**
     * Ecrit la face d'un bloc comme rectangle.
     *
     * @param rects tableau a remplir
     * @param at premier indice ecrit
     * @param x bloc
     * @param y bloc
     * @param z bloc
     * @param face face du bloc qui eclaire
     * @param size cote du rectangle, en blocs, centre sur la face
     */
    static void blockFace(float[] rects, int at, int x, int y, int z, int face, float size) {
        // Le centre de la face et deux axes dans son plan : v = n x u, donc u x v = n.
        int nx = LightCell.NORMAL_X[face];
        int ny = LightCell.NORMAL_Y[face];
        int nz = LightCell.NORMAL_Z[face];
        float cx = x + 0.5f + nx * 0.5f;
        float cy = y + 0.5f + ny * 0.5f;
        float cz = z + 0.5f + nz * 0.5f;
        int ux = nz != 0 ? 1 : 0;
        int uz = 1 - ux;
        int vx = ny * uz;
        int vy = nz * ux - nx * uz;
        int vz = -ny * ux;
        // Les coins dans l'ordre u puis -v : deux aretes de suite ont pour produit -n, ce qui rend positif le facteur de forme.
        for (int i = 0; i < CORNERS; i++) {
            float su = (i == 0 || i == 3 ? -0.5f : 0.5f) * size;
            float sv = (i < 2 ? 0.5f : -0.5f) * size;
            rects[at + i * 3] = cx + su * ux + sv * vx;
            rects[at + i * 3 + 1] = cy + sv * vy;
            rects[at + i * 3 + 2] = cz + su * uz + sv * vz;
        }
    }
}
