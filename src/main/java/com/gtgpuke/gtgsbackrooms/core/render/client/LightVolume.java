package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Les zones de lumiere cuite que lisent les shaders du terrain ({@link ZoneTextures}) : celle du joueur, autour de sa camera (ou de
 * l'arrivee du premier portail visible quand son monde n'a pas de lumiere cuite), et celle d'une vue de portail, autour de la camera de
 * cette vue, quand la premiere ne la couvre pas (une autre dimension, ou le meme monde, loin). Chaque vue lit celle qui couvre sa
 * camera : ses textures sont liees a l'entree de la vue et celles de la vue qui la contient reviennent a sa sortie. Une vue que ni l'une
 * ni l'autre ne couvre garde la lightmap.
 */
public final class LightVolume {

    /** Unite de texture de la zone. */
    public static final int UNIT = 8;
    /** Echantillonneur de la zone dans les shaders patches. */
    public static final String SAMPLER = "gtgs_LightVolume";
    /** Blocs de cote de la zone, en X et en Z, dans la texture. */
    public static final int WIDTH = 128;
    /** Blocs de haut de la zone. */
    public static final int ROWS = LightZone.HEIGHT * 16;
    /** Vues imbriquees, au plus, plus la vue du joueur. */
    private static final int DEPTHS = 4;
    private static final ZoneTextures PLAYER = new ZoneTextures(LightSurfaces.PLAYER);
    private static final ZoneTextures VIEW = new ZoneTextures(LightSurfaces.VIEW);
    /** La zone liee a chaque niveau de vue, celle du joueur au niveau 0. */
    private static final ZoneTextures[] BOUND = {PLAYER, PLAYER, PLAYER, PLAYER};
    private static int depth;
    /** Vues de l'image en cours qui lisent la zone d'une vue, et de la derniere image finie (tests du client). */
    private static int viewEntries;
    private static int lastViewEntries;
    /** Parmi elles, celles dont la zone avait son cache de surfaces : la qualite choisie dans la vue (tests du client). */
    private static int viewSurfaceEntries;
    private static int lastViewSurfaceEntries;
    /** Dernier instant ou une zone de vue etait montree, en millisecondes. */
    private static long viewShownAt;
    /**
     * Delai sans vue avant de rendre la memoire graphique de sa zone, en millisecondes : jamais sous cinq secondes, comme pour un
     * monde distant, sans quoi une vue qui clignote (cadre cache puis revu) recreerait tout a chaque image.
     */
    private static final long VIEW_RELEASE_MS = Math.max(5, ModConfig.LOOK_VIEW_LIGHT_RELEASE_SECONDS) * 1000L;

    private LightVolume() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * La zone du joueur (thread de rendu).
     *
     * @param next zone, {@code null} pour n'en montrer aucune
     * @param world dimension de la zone
     * @param sectionX section d'ancrage
     * @param baseSection premiere section de la zone, a la verticale
     * @param sectionZ section d'ancrage
     * @param blockY Y de l'ancrage
     */
    public static void target(@Nullable LightZone next, @Nullable Identifier world, int sectionX, int baseSection, int sectionZ, int blockY) {
        PLAYER.target(next, world, sectionX, baseSection, sectionZ, blockY);
    }

    /** La zone d'une vue de portail (thread de rendu), aux memes conventions que {@link #target}. */
    public static void targetView(@Nullable LightZone next, @Nullable Identifier world, int sectionX, int baseSection, int sectionZ, int blockY) {
        VIEW.target(next, world, sectionX, baseSection, sectionZ, blockY);
    }

    /**
     * Debut du rendu du monde : chaque zone efface ce qui est sorti et envoie ce qui est cuit, celle du joueur d'abord (le cache d'une vue
     * prend le temps qu'elle laisse), puis celle du joueur est liee.
     */
    public static void frame() {
        LightSurfaces.beginFrame();
        PLAYER.frame();
        VIEW.frame();
        releaseIdleView();
        PLAYER.bind();
        depth = 0;
        BOUND[0] = PLAYER;
        lastViewEntries = viewEntries;
        viewEntries = 0;
        lastViewSurfaceEntries = viewSurfaceEntries;
        viewSurfaceEntries = 0;
    }

    /**
     * Entree d'une vue : la zone qui couvre sa camera est liee, celle du joueur d'abord ; aucune, la zone liee reste et la vue lit la
     * lightmap. A rendre par {@link #leaveView()}.
     *
     * @param world dimension de la vue
     * @param cameraX bloc de la camera de la vue
     * @param cameraY bloc de la camera de la vue
     * @param cameraZ bloc de la camera de la vue
     */
    public static void enterView(Identifier world, int cameraX, int cameraY, int cameraZ) {
        ZoneTextures outer = BOUND[depth];
        ZoneTextures zone = PLAYER.covers(world, cameraX, cameraY, cameraZ) ? PLAYER : VIEW.covers(world, cameraX, cameraY, cameraZ) ? VIEW : outer;
        depth = Math.min(depth + 1, DEPTHS - 1);
        BOUND[depth] = zone;
        if (zone == VIEW) {
            viewEntries++;
            viewSurfaceEntries += VIEW.surfaces() ? 1 : 0;
        }
        if (zone != outer) {
            zone.bind();
        }
    }

    /** Sortie d'une vue : la zone de la vue qui la contient revient. */
    public static void leaveView() {
        ZoneTextures left = BOUND[depth];
        depth = Math.max(depth - 1, 0);
        if (BOUND[depth] != left) {
            BOUND[depth].bind();
        }
    }

    /**
     * {@code true} si les fragments du monde d'un look, vus par une camera a cet endroit, lisent la zone liee.
     *
     * @param look look de la vue
     * @param cameraX bloc de la camera
     * @param cameraY bloc de la camera
     * @param cameraZ bloc de la camera
     */
    public static boolean covers(LookProfile look, int cameraX, int cameraY, int cameraZ) {
        return BOUND[depth].covers(look, cameraX, cameraY, cameraZ);
    }

    /** {@code true} si la zone du joueur couvre une camera de cette dimension, a cet endroit : une vue la-bas n'a pas besoin de la sienne. */
    public static boolean playerCovers(Identifier world, int cameraX, int cameraY, int cameraZ) {
        return PLAYER.covers(world, cameraX, cameraY, cameraZ);
    }

    /** {@code true} si la zone liee est celle du joueur. */
    public static boolean playerBound() {
        return BOUND[depth] == PLAYER;
    }

    /** Le cache de surfaces de la zone liee a la vue courante, pour la passe des ombres des corps. */
    static LightSurfaces boundSurfaces() {
        return BOUND[depth].cache();
    }

    /** {@code true} si le cache de surfaces de la zone liee porte la lumiere directe du terrain, avec sa grille des blocs. */
    public static boolean surfacesBound() {
        return BOUND[depth].surfaces();
    }

    /** Y du premier bloc de la zone liee. */
    public static int baseY() {
        return BOUND[depth].baseY();
    }

    /** La piece moyenne de la zone liee, {@code null} sans zone ou avant sa premiere section. */
    static float @Nullable [] room() {
        return BOUND[depth].room();
    }

    /** Les reglages changent la lumiere des caches de surfaces : leurs tuiles se recalculent, depuis ce qui est deja cuit. */
    public static void rebake() {
        LightSurfaces.resetAll();
        LightZone zone = PLAYER.zone();
        if (zone != null) {
            zone.republish();
        }
        LightZone seen = VIEW.zone();
        if (seen != null && seen != zone) {
            seen.republish();
        }
    }

    /** Texture de la zone du joueur, 0 avant la premiere (tests du client). */
    public static int texture() {
        return PLAYER.texture();
    }

    /** Texture du loin du joueur, 0 avant le premier (tests du client). */
    public static int farTexture() {
        return PLAYER.farTexture();
    }

    /** Vues de la derniere image finie qui lisaient la zone d'une vue (tests du client). */
    public static int lastViewEntries() {
        return lastViewEntries;
    }

    /** Vues de la derniere image finie qui lisaient la zone d'une vue et son cache de surfaces (tests du client). */
    public static int lastViewSurfaceEntries() {
        return lastViewSurfaceEntries;
    }

    /** Texture de la zone d'une vue, 0 avant la premiere (tests du client). */
    public static int viewTexture() {
        return VIEW.texture();
    }

    /** Fin de partie, ou rechargement des profils : plus de zone ; les textures restent, videes tout de suite. */
    public static void clear() {
        PLAYER.target(null, null, 0, 0, 0, 0);
        VIEW.target(null, null, 0, 0, 0, 0);
    }

    /**
     * La zone d'une vue rend sa memoire graphique (environ 200 Mio) apres {@link ModConfig#LOOK_VIEW_LIGHT_RELEASE_SECONDS} sans vue
     * qui la montre, comme un monde distant que plus aucun portail ne montre : une vue qui revient ensuite la recree et reprend ses
     * tuiles, en gardant le temps de les prendre la lumiere de sa zone. Un aller-retour devant un portail ne coute rien.
     */
    private static void releaseIdleView() {
        long now = Util.getMeasuringTimeMs();
        if (VIEW.showing()) {
            viewShownAt = now;
        } else if (VIEW.allocated() && now - viewShownAt > VIEW_RELEASE_MS) {
            VIEW.freeTextures();
        }
    }

    /** {@code true} si la zone d'une vue tient encore de la memoire graphique (tests du client). */
    public static boolean viewAllocated() {
        return VIEW.allocated();
    }

    /** Plus de zone de vue : aucun portail ne montre d'endroit que la zone du joueur ne couvre pas. */
    public static void clearView() {
        VIEW.target(null, null, 0, 0, 0, 0);
    }
}
