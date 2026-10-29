package com.gtgpuke.gtgsbackrooms.core;

/**
 * Configuration du mod, lue au demarrage depuis les proprietes systeme.
 *
 * <p>Toutes les options ont une valeur par defaut jouable ; les toggles de
 * debug se passent en {@code -Dcle=valeur} (voir les {@code -P} de build.gradle).
 */
public final class ModConfig {

    /** Active les gaps de couloir (micro-geometrie) du Level 0. */
    public static final boolean ENABLE_LEVEL_ZERO_NOISE_GEOMETRY =
            booleanProperty("gtgsbackrooms.levelZero.noiseGeometry", true);

    /** Colore en beton les cellules du Level 0 portant une micro-geometrie. */
    public static final boolean DEBUG_LEVEL_ZERO_MICRO_GEOMETRY =
            booleanProperty("gtgsbackrooms.levelZero.debugMicroGeometry", false);

    /** Pose les interrupteurs et prises du Level 0 en blocs de debug. */
    public static final boolean DEBUG_LEVEL_ZERO_WALL_PROPS =
            booleanProperty("gtgsbackrooms.levelZero.debugWallProps", false);

    /** Active le moniteur de performance (logs periodiques et F3). */
    public static final boolean DEBUG_PERFORMANCE_MONITOR =
            booleanProperty("gtgsbackrooms.debugPerformanceMonitor", false);

    /** Intervalle entre deux resumes de performance dans les logs. */
    public static final int PERFORMANCE_LOG_INTERVAL_SECONDS =
            intProperty("gtgsbackrooms.performanceLogIntervalSeconds", 10);

    /** Active les portails Backrooms : bloc portail, teleportation et rendu du monde distant. */
    public static final boolean ENABLE_PORTAL_FEATURE =
            booleanProperty("gtgsbackrooms.portal.enablePortalFeature", true);

    /** Ticks de cooldown apres une traversee de portail par un joueur ; zero par defaut, on enchaine aller et retour librement. */
    public static final int PORTAL_TELEPORT_COOLDOWN_TICKS =
            intProperty("gtgsbackrooms.portal.teleportCooldownTicks", 0);

    /** Distance d'activation du prechargement destination autour d'un portail. */
    public static final int PORTAL_ACTIVATION_RADIUS_BLOCKS =
            intProperty("gtgsbackrooms.portal.activationRadiusBlocks", 16);

    /** Rayon de prechargement des chunks destination autour d'un portail. */
    public static final int PORTAL_PRELOAD_RADIUS_CHUNKS =
            intProperty("gtgsbackrooms.portal.preloadRadiusChunks", 3);

    /** Distance maximale de rendu d'un portail. */
    public static final int PORTAL_RENDER_DISTANCE_BLOCKS =
            intProperty("gtgsbackrooms.portal.renderDistanceBlocks", 64);

    /**
     * Secondes sans aucun portail a portee vers une dimension distante avant que le serveur ne demande au client d'en
     * liberer le monde distant (chunks, entites, renderer) : un reglage de memoire. Un retour apres ce delai voit la vue
     * se reconstruire, le temps de renvoyer la fenetre. Jamais moins que les cinq secondes du dechargement de la fenetre ;
     * une valeur tres grande revient a ne jamais liberer.
     */
    public static final int PORTAL_REMOTE_WORLD_RELEASE_SECONDS =
            intProperty("gtgsbackrooms.portal.remoteWorldReleaseSeconds", 60);

    /**
     * Saute le dessin des vues dont le cadre est prouve cache derriere des blocs pleins et opaques deja affiches
     * ({@code FrameOcclusion}) ; leur terrain reste entretenu. Se coupe pour comparer avant et apres au banc.
     */
    public static final boolean ENABLE_PORTAL_OCCLUSION =
            booleanProperty("gtgsbackrooms.portal.occlusion", true);

    /**
     * Allege la vue d'un cadre lointain et petit a l'ecran ({@code ViewDetail}) : ni meteo ni particules, entites figees.
     * De pres, ou un cadre qui couvre un dixieme de l'ecran, la vue reste en pleine qualite. Se coupe pour comparer.
     */
    public static final boolean ENABLE_PORTAL_LOD =
            booleanProperty("gtgsbackrooms.portal.lod", true);

    /**
     * Cohabitation des vues de portail avec un pack de shaders : {@code auto} (les vues se dessinent sous le pack, par le
     * mod de shaders) ou {@code off} (sous un pack, les cadres sont sombres).
     */
    public static final String PORTAL_SHADER_COHABITATION =
            stringProperty("gtgsbackrooms.portal.shaderCohabitation", "auto");

    /** Active les logs detailles du rendu de portail. */
    public static final boolean DEBUG_PORTAL_RENDERING =
            booleanProperty("gtgsbackrooms.portal.debugRendering", false);

    /**
     * Moteur de shaders maison : les shaders vanilla sont patches au chargement et chaque dimension a profil
     * ({@code look_profiles/*.json}) recoit son look, dans le monde du joueur comme dans les vues de portail. Coupe,
     * rien n'est patche ni charge : rendu vanilla partout, pour comparer au banc.
     */
    public static final boolean ENABLE_LOOK =
            booleanProperty("gtgsbackrooms.look.enabled", true);

    /**
     * Sous un pack de shaders, dans une dimension a look (les Backrooms), le rendu du mod remplace le pack (module de
     * compatibilite du mod de shaders). Coupe : le pack rend aussi les Backrooms, le look se pose sur son image.
     */
    public static final boolean LOOK_REPLACE_SHADER_PACK =
            booleanProperty("gtgsbackrooms.look.replaceShaderPack", true);

    /**
     * Moteur audio ({@code docs/AUDIO.md}) : chaque son emis dans une dimension a profil audio
     * ({@code audio_profiles/*.json}) recoit sa reverb, son occlusion et son absorption de l'air, et ses neons bourdonnent.
     * Coupe, aucun profil n'est charge : tous les sons restent vanilla.
     */
    public static final boolean ENABLE_AUDIO =
            booleanProperty("gtgsbackrooms.audio.enabled", true);

    /**
     * Extension EFX d'OpenAL pour l'acoustique. Coupee (banc du repli, pilote sans EFX) : l'occlusion se rend par le
     * gain seul (aucune reverb, aucun filtre) et la liaison son-image continue.
     */
    public static final boolean AUDIO_EFX =
            booleanProperty("gtgsbackrooms.audio.efx", true);

    /**
     * Debogage de l'acoustique : les rayons de reverb (jaune : rebond, vert : sortis, magenta : par un cadre, gris : perdus
     * hors de la copie du monde) et les chemins d'occlusion (du vert au rouge selon ce qu'ils retirent) des derniers sons
     * calcules se dessinent dans le monde.
     */
    public static final boolean AUDIO_DEBUG_RAYS =
            booleanProperty("gtgsbackrooms.audio.debugRays", false);

    /**
     * Developpement : dans une dimension a forme generee, les rayons sont aussi lances la ou la generation suffit, et
     * l'accord des deux est journalise (hypothese H9 de la specification audio : la forme generee suffit-elle ?).
     */
    public static final boolean AUDIO_COMPARE_GENERATED =
            booleanProperty("gtgsbackrooms.audio.compareGenerated", false);

    /**
     * Developpement : chaque passe du moteur de shaders verifie qu'elle rend l'etat GL tel qu'elle l'a trouve (lu au
     * pilote avant et apres elle), et journalise une fois chaque ecart. Les lectures attendent le pilote : banc seulement.
     */
    public static final boolean LOOK_CHECK_GL_STATE =
            booleanProperty("gtgsbackrooms.look.checkGlState", false);

    /** En fin de rendu du monde, remplace l'image par le stencil (niveau d'imbrication et look de chaque pixel). */
    public static final boolean DEBUG_LOOK_STENCIL =
            booleanProperty("gtgsbackrooms.look.debugStencil", false);

    /**
     * Qualite du moteur de shaders : {@code light} (huit neons, pas de bloom), {@code full}, ou {@code auto} (complete
     * sauf sur une carte graphique integree, reconnue a la chaine du pilote).
     */
    public static final String LOOK_QUALITY =
            stringProperty("gtgsbackrooms.look.quality", "auto");

    /**
     * Developpement : chemin du fichier des vecteurs communs du calendrier des neons
     * ({@code src/test/resources/neon_schedule_vectors.json}). Donne, le client verifie au premier rechargement que le
     * jumeau GLSL les suit, et le coupe sinon. Vide : pas de verification.
     */
    public static final String LOOK_VERIFY_NEON_SHADER =
            stringProperty("gtgsbackrooms.look.verifyNeonShader", "");

    /**
     * Qualite du calcul acoustique par rayons : {@code light} (seize rayons, deux rebonds, le chemin direct),
     * {@code full} (trente-deux, quatre, et des detours), ou {@code auto} (celle du moteur de shaders).
     */
    public static final String AUDIO_QUALITY =
            stringProperty("gtgsbackrooms.audio.quality", "auto");

    /**
     * Confort (joueurs photosensibles) : les reactions visuelles aux sons ne varient jamais fortement (baisses douces
     * seulement), la pulsation de l'ecran et le tremblement de camera sont coupes, ni la lumiere de l'ecran ni aucun tube
     * ne vacille. Le son des cris ne change pas.
     */
    public static final boolean REDUCE_FLASHES =
            booleanProperty("gtgsbackrooms.audio.reduceFlashes", false);

    /**
     * Confort : le look des Backrooms sans bruit visuel (ni grain, ni vacillement de la lumiere de l'ecran, ni
     * aberration chromatique). Teinte, lumiere, brouillard, vignette et bloom restent.
     */
    public static final boolean REDUCE_VISUAL_NOISE =
            booleanProperty("gtgsbackrooms.look.reduceNoise", false);

    /**
     * Confort : les sons des dimensions a profil audio perdent une part de leurs graves (bourdonnements, grondements), par
     * le filtre de chaque source (passe-bande au lieu de passe-bas). Avec EFX seulement ; les sons vanilla ne changent pas.
     */
    public static final boolean AUDIO_REDUCE_BASS =
            booleanProperty("gtgsbackrooms.audio.reduceBass", false);

    /**
     * Confort : un cri lie fait trembler un peu la camera du joueur ({@code camera_shake}), s'il est lui-meme dans la
     * dimension du cri. Coupe par defaut ; jamais dans une dimension vanilla ni avec l'option qui reduit les flashs, et a
     * l'echelle des effets d'ecran du jeu.
     */
    public static final boolean AUDIO_CAMERA_SHAKE =
            booleanProperty("gtgsbackrooms.audio.cameraShake", false);

    private ModConfig() {
        throw new UnsupportedOperationException("Classe de configuration.");
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        return parseBoolean(System.getProperty(key), defaultValue);
    }

    private static int intProperty(String key, int defaultValue) {
        return parseInt(System.getProperty(key), defaultValue);
    }

    private static String stringProperty(String key, String defaultValue) {
        return parseString(System.getProperty(key), defaultValue);
    }

    static String parseString(String rawValue, String defaultValue) {
        return rawValue == null || rawValue.isBlank() ? defaultValue : rawValue.trim();
    }

    static boolean parseBoolean(String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(rawValue.trim());
    }

    static int parseInt(String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
