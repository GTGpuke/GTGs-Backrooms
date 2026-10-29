package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import java.util.Locale;

import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;

/**
 * Qualite du calcul acoustique : legere (petites configurations) ou complete. Choisie par l'option
 * {@code gtgsbackrooms.audio.quality} ({@code light}, {@code full} ou {@code auto}, qui suit la qualite du moteur de
 * shaders, elle-meme choisie d'apres la carte graphique).
 */
public enum AudioQuality {

    /** Seize rayons, deux rebonds, le seul chemin direct. */
    LIGHT(16, 2, 1),
    /** Trente-deux rayons, quatre rebonds, le chemin direct et quatre detours. */
    FULL(32, 4, 5);

    private final int rays;
    private final int bounces;
    private final int paths;

    AudioQuality(int rays, int bounces, int paths) {
        this.rays = rays;
        this.bounces = bounces;
        this.paths = paths;
    }

    /** Rayons de reverb par evaluation. */
    public int rays() {
        return this.rays;
    }

    /** Rebonds de chaque rayon, au plus. */
    public int bounces() {
        return this.bounces;
    }

    /** Chemins d'occlusion, le direct compris. */
    public int paths() {
        return this.paths;
    }

    /**
     * Qualite demandee par l'option, ou alignee sur celle du moteur de shaders.
     *
     * @param option valeur de l'option, {@code light}, {@code full} ou {@code auto} (toute autre valeur vaut {@code auto})
     * @param look qualite du moteur de shaders
     */
    public static AudioQuality choose(String option, LookQuality look) {
        String wanted = option.trim().toLowerCase(Locale.ROOT);
        if (wanted.equals("light")) {
            return LIGHT;
        }
        if (wanted.equals("full")) {
            return FULL;
        }
        return look == LookQuality.FULL ? FULL : LIGHT;
    }
}
