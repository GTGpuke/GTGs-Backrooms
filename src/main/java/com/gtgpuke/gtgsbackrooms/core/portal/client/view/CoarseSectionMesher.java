package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

/**
 * Terrain simplifie d'une section de seize blocs (LOD des blocs, {@link ViewDetail}) : la geometrie reste exacte au bloc
 * pres (un couloir garde sa hauteur, un mur fin son epaisseur), seules les faces visibles des blocs pleins sont emises, et
 * les faces coplanaires d'un meme etat se fusionnent en carres d'au plus {@code maxSize} blocs de cote, sur lesquels la
 * texture s'etire : seul le motif grandit. Chaque carre se pose au premier bloc libre du plan, le plus grand qui tient.
 *
 * <p>Une section qui contient un bloc que ce terrain ne sait pas dessiner ({@link #UNREPRESENTABLE} : ni vide ni cube plein
 * et opaque) n'est pas simplifiee. Les faces du bord de la section se jugent sur les blocs de la section voisine, ou un tel
 * bloc compte comme vide.
 *
 * <p>Logique pure : les blocs se lisent par cles ({@link Blocks}), les faces partent vers un {@link Faces}. Sans allocation,
 * sur le thread de rendu seulement.
 */
public final class CoarseSectionMesher {

    /** Blocs d'un monde, en coordonnees monde. */
    @FunctionalInterface
    public interface Blocks {

        /** Cle de l'etat du bloc : 0 s'il est vide, {@link #UNREPRESENTABLE} si ce terrain ne sait pas le dessiner, sinon une cle propre a l'etat. */
        int key(int x, int y, int z);
    }

    /** Faces du terrain simplifie. */
    @FunctionalInterface
    public interface Faces {

        /**
         * Une face visible, carree dans son plan, d'un bloc d'epaisseur.
         *
         * @param direction direction de la face : 0 bas, 1 haut, 2 nord, 3 sud, 4 ouest, 5 est
         * @param x premier bloc de la face, depuis l'origine de la section
         * @param y premier bloc de la face, depuis l'origine de la section
         * @param z premier bloc de la face, depuis l'origine de la section
         * @param size cote de la face dans son plan, en blocs
         * @param key cle de l'etat de ses blocs
         */
        void face(int direction, int x, int y, int z, int size, int key);
    }

    /** Cote d'une section, en blocs. */
    public static final int SECTION = 16;
    /** Cle d'un bloc que ce terrain ne sait pas dessiner : sa section reste au rendu vanilla. */
    public static final int UNREPRESENTABLE = -1;
    private static final int DIRECTIONS = 6;
    /** Decalage vers le bloc voisin, par direction (bas, haut, nord, sud, ouest, est). */
    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** Axe normal de chaque direction (0 x, 1 y, 2 z), puis les deux axes de son plan. */
    private static final int[] NORMAL = {1, 1, 2, 2, 0, 0};
    private static final int[] FIRST = {0, 0, 0, 0, 2, 2};
    private static final int[] SECOND = {2, 2, 1, 1, 1, 1};
    /** Cles des blocs de la section, lues une fois. */
    private static final int[] KEYS = new int[SECTION * SECTION * SECTION];
    /** Cles des faces visibles du plan en cours, 0 la ou il n'y en a pas ou plus. */
    private static final int[] PLANE = new int[SECTION * SECTION];
    /** Position d'un bloc du plan en cours, par axe. */
    private static final int[] POS = new int[3];

    private CoarseSectionMesher() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Emet les faces du terrain simplifie d'une section.
     *
     * @param originX origine de la section, en blocs (multiple de seize)
     * @param originY origine de la section, en blocs
     * @param originZ origine de la section, en blocs
     * @param maxSize cote du plus grand carre de faces fusionnees : 2 ou 4 blocs
     * @return nombre de faces emises, ou {@link #UNREPRESENTABLE} (et aucune face) si la section ne se simplifie pas
     */
    public static int mesh(int originX, int originY, int originZ, int maxSize, Blocks blocks, Faces faces) {
        for (int x = 0; x < SECTION; x++) {
            for (int y = 0; y < SECTION; y++) {
                for (int z = 0; z < SECTION; z++) {
                    int key = blocks.key(originX + x, originY + y, originZ + z);
                    if (key == UNREPRESENTABLE) {
                        return UNREPRESENTABLE;
                    }
                    KEYS[index(x, y, z)] = key;
                }
            }
        }
        int emitted = 0;
        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int depth = 0; depth < SECTION; depth++) {
                fillPlane(originX, originY, originZ, direction, depth, blocks);
                emitted += mergePlane(direction, depth, maxSize, faces);
            }
        }
        return emitted;
    }

    /** Releve les faces visibles d'un plan : blocs pleins de cette tranche dont le voisin dans la direction est vide. */
    private static void fillPlane(int originX, int originY, int originZ, int direction, int depth, Blocks blocks) {
        POS[NORMAL[direction]] = depth;
        for (int a = 0; a < SECTION; a++) {
            POS[FIRST[direction]] = a;
            for (int b = 0; b < SECTION; b++) {
                POS[SECOND[direction]] = b;
                int key = KEYS[index(POS[0], POS[1], POS[2])];
                boolean visible = key > 0
                        && !solid(originX, originY, originZ, POS[0] + DX[direction], POS[1] + DY[direction], POS[2] + DZ[direction], blocks);
                PLANE[a * SECTION + b] = visible ? key : 0;
            }
        }
    }

    /** Fusionne les faces visibles d'un plan en carres, les plus grands d'abord, et les emet. */
    private static int mergePlane(int direction, int depth, int maxSize, Faces faces) {
        int emitted = 0;
        POS[NORMAL[direction]] = depth;
        for (int a = 0; a < SECTION; a++) {
            for (int b = 0; b < SECTION; b++) {
                int key = PLANE[a * SECTION + b];
                if (key == 0) {
                    continue;
                }
                int size = largestSquare(a, b, key, maxSize);
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        PLANE[(a + i) * SECTION + b + j] = 0;
                    }
                }
                POS[FIRST[direction]] = a;
                POS[SECOND[direction]] = b;
                faces.face(direction, POS[0], POS[1], POS[2], size, key);
                emitted++;
            }
        }
        return emitted;
    }

    /** Cote du plus grand carre de faces de cette cle qui part de (a, b) dans le plan, 1 au moins. */
    private static int largestSquare(int a, int b, int key, int maxSize) {
        for (int size = maxSize; size > 1; size /= 2) {
            if (square(a, b, size, key)) {
                return size;
            }
        }
        return 1;
    }

    private static boolean square(int a, int b, int size, int key) {
        if (a + size > SECTION || b + size > SECTION) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (PLANE[(a + i) * SECTION + b + j] != key) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code true} si le bloc (coordonnees depuis l'origine, eventuellement hors de la section) est plein. */
    private static boolean solid(int originX, int originY, int originZ, int x, int y, int z, Blocks blocks) {
        if (x >= 0 && x < SECTION && y >= 0 && y < SECTION && z >= 0 && z < SECTION) {
            return KEYS[index(x, y, z)] > 0;
        }
        return blocks.key(originX + x, originY + y, originZ + z) > 0;
    }

    private static int index(int x, int y, int z) {
        return (x * SECTION + y) * SECTION + z;
    }
}
