package com.gtgpuke.gtgsbackrooms.core.render.quality;

import java.util.Locale;

/**
 * Palier de qualite du moteur de shaders. L'option {@code gtgsbackrooms.look.quality} ou le menu du joueur le demandent par
 * sa cle ; {@code auto} prend le palier High, ou Low sur une carte integree, reconnue a la chaine du pilote. La visibilite
 * ne depend jamais du palier : seul le realisme de la lumiere change.
 */
public enum LookQuality {

    /** Le palier desactive des reglages avances : ni lumiere cuite, ni teinte, ni post. */
    OFF("off", 0, false, false, false, 0, 0, 0, 0),
    /** Lumiere cuite par cellule et etalonnage, sans post. */
    LOW("low", 8, false, false, false, 0, 0, 0, 0),
    /** Low, plus vignette et halos. */
    MEDIUM("medium", 8, true, false, true, 0, 0, 0, 0),
    /** Medium, plus bloom et cache de surfaces : lumiere, ombres et relief au pixel de texture, matieres des blocs, et les ombres des corps. */
    HIGH("high", 32, true, true, true, 6, 6, 6, 0),
    /** High, avec la lumiere tracee par rayons : vraies penombres des dalles, rebonds d'une surface a l'autre. */
    ULTRA("ultra", 32, true, true, true, 32, 12, 16, 64);

    /** Valeur qui laisse le choix a la detection de la carte. */
    public static final String AUTO = "auto";

    private final String key;
    private final int neonCount;
    private final boolean surfaceLight;
    private final boolean post;
    private final boolean viewEffects;
    private final int surfaceRays;
    private final int surfaceOcclusionRays;
    private final int casterRays;
    private final int tracedRays;

    LookQuality(String key, int neonCount, boolean post, boolean surfaceLight, boolean viewEffects, int surfaceRays, int surfaceOcclusionRays, int casterRays,
            int tracedRays) {
        this.key = key;
        this.neonCount = neonCount;
        this.post = post;
        this.surfaceLight = surfaceLight;
        this.viewEffects = viewEffects;
        this.surfaceRays = surfaceRays;
        this.surfaceOcclusionRays = surfaceOcclusionRays;
        this.casterRays = casterRays;
        this.tracedRays = tracedRays;
    }

    /** Nom du palier, dans les options et le fichier de configuration. */
    public String key() {
        return this.key;
    }

    /** Nombre de neons par vue, au plus. */
    public int neonCount() {
        return this.neonCount;
    }

    /** {@code true} si le bloom se dessine. */
    public boolean bloom() {
        return this.surfaceLight;
    }

    /** {@code true} si ce palier a une passe post. */
    public boolean post() {
        return this.post;
    }

    /** {@code true} si le cache de surfaces porte la lumiere directe, au pixel de texture. */
    public boolean surfaceLight() {
        return this.surfaceLight;
    }

    /** {@code true} si les halos, qui dependent de la vue, se calculent au fragment. */
    public boolean viewEffects() {
        return this.viewEffects;
    }

    /** Rayons vers chaque dalle, par pixel du cache de surfaces. */
    public int surfaceRays() {
        return this.surfaceRays;
    }

    /** Rayons qui mesurent ce que les blocs proches cachent, par pixel du cache de surfaces. */
    public int surfaceOcclusionRays() {
        return this.surfaceOcclusionRays;
    }

    /** Rayons vers chaque dalle des ombres des corps, par pixel de texture du terrain ; 0 sans ombres des corps. */
    public int casterRays() {
        return this.casterRays;
    }

    /** Rayons d'un pixel d'une tuile de rebond ; 0 sans lumiere tracee. */
    public int tracedRays() {
        return this.tracedRays;
    }

    /** {@code true} si la lumiere se trace : rebonds et penombres vraies. */
    public boolean traced() {
        return this.tracedRays > 0;
    }

    /**
     * Palier demande, ou choisi d'apres la carte.
     *
     * @param option cle d'un palier, ou {@link #AUTO} ; toute valeur inconnue vaut {@link #AUTO}. Les anciens noms
     *     {@code light} et {@code full} valent Medium et High
     * @param renderer chaine {@code GL_RENDERER} du pilote, ou {@code null} si inconnue
     */
    public static LookQuality choose(String option, String renderer) {
        String wanted = option.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals("light")) {
            return MEDIUM;
        }
        if (wanted.equals("full")) {
            return HIGH;
        }
        for (LookQuality quality : values()) {
            if (quality.key.equals(wanted)) {
                return quality;
            }
        }
        return integrated(renderer) ? LOW : HIGH;
    }

    /** {@code true} si la chaine du pilote designe une carte graphique integree. */
    static boolean integrated(String renderer) {
        if (renderer == null) {
            return false;
        }
        String name = renderer.toLowerCase(Locale.ROOT);
        return name.contains("intel") || name.contains("llvmpipe") || name.contains("swiftshader");
    }
}
