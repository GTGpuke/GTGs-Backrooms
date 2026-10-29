package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import org.jetbrains.annotations.Nullable;

/**
 * Courbes classiques qui font passer une valeur de 0 a 1 : une reaction a un son lie suit l'enveloppe du son a travers
 * l'une d'elles (une montee franche, un sursaut aux pics). Chaque courbe vaut 0 en 0 et 1 en 1 ; l'entree est bornee a
 * [0, 1].
 */
public enum Easing {

    /** Telle quelle. */
    LINEAR("linear") {
        @Override
        float curve(float t) {
            return t;
        }
    },
    /** Lente au depart : seuls les pics se voient. */
    IN_QUAD("in_quad") {
        @Override
        float curve(float t) {
            return t * t;
        }
    },
    /** Rapide au depart : la reaction monte tot et reste. */
    OUT_QUAD("out_quad") {
        @Override
        float curve(float t) {
            return 1.0f - (1.0f - t) * (1.0f - t);
        }
    },
    /** Lente aux deux bouts. */
    IN_OUT_QUAD("in_out_quad") {
        @Override
        float curve(float t) {
            return t < HALF ? 2.0f * t * t : 1.0f - 2.0f * (1.0f - t) * (1.0f - t);
        }
    },
    /** Plus lente encore au depart que {@link #IN_QUAD}. */
    IN_CUBIC("in_cubic") {
        @Override
        float curve(float t) {
            return t * t * t;
        }
    },
    /** Plus rapide encore au depart que {@link #OUT_QUAD}. */
    OUT_CUBIC("out_cubic") {
        @Override
        float curve(float t) {
            return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        }
    },
    /** Lente aux deux bouts, en douceur. */
    IN_OUT_SINE("in_out_sine") {
        @Override
        float curve(float t) {
            return (float) (HALF - HALF * Math.cos(Math.PI * t));
        }
    };

    private static final float HALF = 0.5f;
    private final String key;

    Easing(String key) {
        this.key = key;
    }

    /** Nom de la courbe dans les fichiers. */
    public String key() {
        return this.key;
    }

    /** La courbe d'un nom, ou {@code null} s'il est inconnu. */
    public static @Nullable Easing of(String key) {
        for (Easing easing : values()) {
            if (easing.key.equals(key)) {
                return easing;
            }
        }
        return null;
    }

    /**
     * La valeur de la courbe.
     *
     * @param value entree, bornee a [0, 1]
     */
    public float apply(float value) {
        return curve(Math.max(0.0f, Math.min(1.0f, value)));
    }

    /** La courbe sur [0, 1]. */
    abstract float curve(float t);
}
