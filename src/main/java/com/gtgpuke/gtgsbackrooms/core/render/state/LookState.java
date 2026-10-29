package com.gtgpuke.gtgsbackrooms.core.render.state;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Le look courant du rendu : une pile de profils, le monde du joueur au fond, un profil empile en entree de chaque
 * vue de portail et retire en sortie, comme le plan de decoupe. Chaque entree porte aussi la camera de sa vue (bloc
 * entier et reste, pour retrouver le bloc d'un fragment loin de l'origine) et le tick de son monde dans la periode de
 * l'horloge commune (le jumeau GLSL du calendrier des neons les lit). Chaque shader active lit le sommet de la pile
 * ({@code MixinShaderProgram}), et ne le renvoie que si la version a change depuis son dernier envoi : la version
 * avance a chaque changement de sommet et a chaque image (le temps des effets animes). Hors du rendu du monde,
 * l'interface comprise, le sommet est le profil vanilla.
 */
public final class LookState {

    /** Monde du joueur, une vue, une vue imbriquee, et une de marge pour une vue qui couvre l'ecran. */
    private static final int MAX_DEPTH = 3;
    private static final LookProfile[] STACK = new LookProfile[MAX_DEPTH + 1];
    private static final int[] CAMERA_X = new int[MAX_DEPTH + 1];
    private static final int[] CAMERA_Y = new int[MAX_DEPTH + 1];
    private static final int[] CAMERA_Z = new int[MAX_DEPTH + 1];
    private static final float[] FRACT_X = new float[MAX_DEPTH + 1];
    private static final float[] FRACT_Y = new float[MAX_DEPTH + 1];
    private static final float[] FRACT_Z = new float[MAX_DEPTH + 1];
    private static final int[] TICK = new int[MAX_DEPTH + 1];
    private static int depth;
    private static int version;
    private static float time;
    /** Looks vus dans l'image en cours, un bit par identifiant : ceux dont le post-traitement se dessine en fin de rendu. */
    private static int usedLooks;
    private static int neonCount;
    /** Looks vus et neons du monde du joueur de la derniere image terminee, pour le diagnostic entre deux images. */
    private static int lastFrameLooks;
    private static int lastFrameNeons;
    /** {@code false} quand un mod dessine le terrain par ses propres shaders : l'etage 1 passe alors dans le post. */
    private static boolean shadersCarryLook = true;

    static {
        clear();
    }

    private LookState() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Debut du rendu du monde du joueur : son profil au fond de la pile, le temps de l'image pour les effets animes.
     * Une pile non vide a ce moment est une erreur du rendu precedent : journalisee, puis remise a zero.
     *
     * @param base profil du monde du joueur
     * @param timeSeconds temps de l'image, en secondes, borne pour garder sa precision en flottant
     * @param cameraX camera du joueur
     * @param cameraY camera du joueur
     * @param cameraZ camera du joueur
     * @param tick tick du monde du joueur dans la periode de l'horloge commune
     */
    public static void beginWorld(LookProfile base, float timeSeconds, double cameraX, double cameraY, double cameraZ, int tick) {
        if (depth != 0) {
            ModConstants.LOGGER.warn("[Look] Pile de looks non vide au debut du rendu ({} vues) : remise a zero.", depth);
            depth = 0;
        }
        STACK[0] = base;
        place(0, cameraX, cameraY, cameraZ, tick);
        time = timeSeconds;
        usedLooks = 1 << base.id();
        version++;
    }

    /**
     * Entree d'une vue : son profil au sommet, avec sa camera et le tick de son monde.
     *
     * @param profile profil du monde de la vue
     * @param cameraX camera de la vue
     * @param cameraY camera de la vue
     * @param cameraZ camera de la vue
     * @param tick tick du monde de la vue dans la periode de l'horloge commune
     */
    public static void push(LookProfile profile, double cameraX, double cameraY, double cameraZ, int tick) {
        if (depth == MAX_DEPTH) {
            throw new IllegalStateException("Plus de " + MAX_DEPTH + " looks empiles.");
        }
        STACK[++depth] = profile;
        place(depth, cameraX, cameraY, cameraZ, tick);
        usedLooks |= 1 << profile.id();
        version++;
    }

    private static void place(int at, double cameraX, double cameraY, double cameraZ, int tick) {
        CAMERA_X[at] = (int) Math.floor(cameraX);
        CAMERA_Y[at] = (int) Math.floor(cameraY);
        CAMERA_Z[at] = (int) Math.floor(cameraZ);
        FRACT_X[at] = (float) (cameraX - CAMERA_X[at]);
        FRACT_Y[at] = (float) (cameraY - CAMERA_Y[at]);
        FRACT_Z[at] = (float) (cameraZ - CAMERA_Z[at]);
        TICK[at] = tick;
    }

    /** Sortie d'une vue : le profil de la vue qui la contient reprend. */
    public static void pop() {
        if (depth == 0) {
            throw new IllegalStateException("Aucun look a retirer.");
        }
        depth--;
        version++;
    }

    /** Fin du rendu du monde : tout ce qui suit (interface, menus) est vanilla. Une pile non vide est journalisee et videe. */
    public static void endWorld() {
        if (depth != 0) {
            ModConstants.LOGGER.warn("[Look] Pile de looks non vide a la fin du rendu ({} vues) : remise a zero.", depth);
        }
        lastFrameLooks = usedLooks;
        lastFrameNeons = neonCount;
        clear();
    }

    /** Looks vus dans la derniere image terminee, un bit par identifiant. */
    public static int lastFrameLooks() {
        return lastFrameLooks;
    }

    /** Neons envoyes pour le monde du joueur dans la derniere image terminee. */
    public static int lastFrameNeons() {
        return lastFrameNeons;
    }

    /** Pile vide, look vanilla, sans journal. */
    public static void clear() {
        depth = 0;
        STACK[0] = LookProfile.VANILLA;
        place(0, 0.0, 0.0, 0.0, 0);
        usedLooks = 0;
        neonCount = 0;
        shadersCarryLook = true;
        version++;
    }

    /** Looks vus dans l'image en cours, un bit par identifiant (le bit 0 pour vanilla). */
    public static int usedLooks() {
        return usedLooks;
    }

    /** Nombre de neons dans le bloc d'uniformes de la vue courante : change la version, les shaders le relisent. */
    public static void setNeonCount(int count) {
        if (neonCount != count) {
            neonCount = count;
            version++;
        }
    }

    /** Nombre de neons dans le bloc d'uniformes de la vue courante. */
    public static int neonCount() {
        return neonCount;
    }

    /**
     * Ou l'etage 1 s'applique pour cette image : dans les shaders patches ({@code true}), ou dans le post sur tout ce
     * qui porte le look ({@code false}, quand un mod dessine le terrain par ses propres shaders, que le patch n'atteint
     * pas : le terrain et les entites recoivent alors le meme look, sans la lumiere des neons).
     */
    public static void setShadersCarryLook(boolean carry) {
        if (shadersCarryLook != carry) {
            shadersCarryLook = carry;
            version++;
        }
    }

    /** {@code true} si les shaders patches appliquent l'etage 1 ; {@code false} si le post s'en charge. */
    public static boolean shadersCarryLook() {
        return shadersCarryLook;
    }

    /** Profil au sommet de la pile. */
    public static LookProfile current() {
        return STACK[depth];
    }

    /** Bloc de la camera de la vue courante, axe x. */
    public static int cameraBlockX() {
        return CAMERA_X[depth];
    }

    /** Bloc de la camera de la vue courante, axe y. */
    public static int cameraBlockY() {
        return CAMERA_Y[depth];
    }

    /** Bloc de la camera de la vue courante, axe z. */
    public static int cameraBlockZ() {
        return CAMERA_Z[depth];
    }

    /** Reste de la camera de la vue courante dans son bloc, axe x, de 0 a 1. */
    public static float cameraFractX() {
        return FRACT_X[depth];
    }

    /** Reste de la camera de la vue courante dans son bloc, axe y, de 0 a 1. */
    public static float cameraFractY() {
        return FRACT_Y[depth];
    }

    /** Reste de la camera de la vue courante dans son bloc, axe z, de 0 a 1. */
    public static float cameraFractZ() {
        return FRACT_Z[depth];
    }

    /** Tick du monde de la vue courante dans la periode de l'horloge commune. */
    public static int tick() {
        return TICK[depth];
    }

    /** Nombre de vues empilees. */
    public static int depth() {
        return depth;
    }

    /** Version du sommet : change a chaque empilement, retrait et debut d'image. */
    public static int version() {
        return version;
    }

    /** Temps de l'image en secondes, pour les effets animes. */
    public static float time() {
        return time;
    }
}
