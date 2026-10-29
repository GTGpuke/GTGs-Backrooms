package com.gtgpuke.gtgsbackrooms.core.render.quality;

import java.util.Locale;

/**
 * Qualite du moteur de shaders : legere (petites configurations, cartes graphiques integrees) ou complete. Choisie par
 * l'option {@code gtgsbackrooms.look.quality} ({@code light}, {@code full} ou {@code auto}, qui prend la qualite
 * complete sauf sur une carte integree, reconnue a la chaine du pilote).
 */
public enum LookQuality {

    /** Huit neons par vue, pas de bloom. */
    LIGHT(8, false),
    /** Trente-deux neons par vue, bloom. */
    FULL(32, true);

    /** Valeur de l'option qui laisse le choix a la detection de la carte. */
    public static final String AUTO = "auto";

    private final int neonCount;
    private final boolean bloom;

    LookQuality(int neonCount, boolean bloom) {
        this.neonCount = neonCount;
        this.bloom = bloom;
    }

    /** Nombre de neons eclairants par vue, au plus. */
    public int neonCount() {
        return this.neonCount;
    }

    /** {@code true} si le bloom se dessine. */
    public boolean bloom() {
        return this.bloom;
    }

    /**
     * Qualite demandee par l'option, ou choisie d'apres la carte.
     *
     * @param option valeur de l'option, {@code light}, {@code full} ou {@code auto} (toute autre valeur vaut {@code auto})
     * @param renderer chaine {@code GL_RENDERER} du pilote, ou {@code null} si inconnue
     */
    public static LookQuality choose(String option, String renderer) {
        String wanted = option.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals("light")) {
            return LIGHT;
        }
        if (wanted.equals("full")) {
            return FULL;
        }
        return integrated(renderer) ? LIGHT : FULL;
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
