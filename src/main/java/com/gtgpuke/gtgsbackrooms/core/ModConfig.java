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

    /** Active les logs detailles du rendu de portail. */
    public static final boolean DEBUG_PORTAL_RENDERING =
            booleanProperty("gtgsbackrooms.portal.debugRendering", false);

    private ModConfig() {
        throw new UnsupportedOperationException("Classe de configuration.");
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        return parseBoolean(System.getProperty(key), defaultValue);
    }

    private static int intProperty(String key, int defaultValue) {
        return parseInt(System.getProperty(key), defaultValue);
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
