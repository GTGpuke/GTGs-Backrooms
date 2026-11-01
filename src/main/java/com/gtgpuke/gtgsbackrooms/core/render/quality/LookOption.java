package com.gtgpuke.gtgsbackrooms.core.render.quality;

import org.jetbrains.annotations.Nullable;

/** Les reglages du menu qui s'activent ou se coupent un par un : effets de la partie Avance, puis confort. */
public enum LookOption {

    /** Lumiere directe au pixel de texture, aux paliers qui l'ont. */
    SURFACE_CACHE("surface_cache"),
    /** Rayonnement des parties les plus claires de l'image. */
    BLOOM("bloom"),
    /** Halo de l'air autour des dalles. */
    HALOS("halos"),
    /** Reflet des dalles sur les matieres lisses. */
    SHEEN("sheen"),
    /** Relief que le cache de surfaces tire des textures. */
    RELIEF("relief"),
    /** Assombrissement des bords de l'image. */
    VIGNETTE("vignette"),
    /** Plafond de clignotement renforce : aucune variation forte de lumiere, aucun tube qui vacille. */
    REDUCE_FLASHES("reduce_flashes"),
    /** Aucun tremblement de camera. */
    REDUCE_MOTION("reduce_motion"),
    /** Ni grain, ni aberration chromatique. */
    REDUCE_NOISE("reduce_noise");

    private final String key;

    LookOption(String key) {
        this.key = key;
    }

    /** Nom du reglage, dans le fichier de configuration et les cles de langue. */
    public String key() {
        return this.key;
    }

    /** Masque de ce reglage parmi les autres. */
    int bit() {
        return 1 << ordinal();
    }

    /** Le reglage de ce nom, ou {@code null}. */
    public static @Nullable LookOption byKey(String key) {
        for (LookOption option : values()) {
            if (option.key.equals(key)) {
                return option;
            }
        }
        return null;
    }
}
