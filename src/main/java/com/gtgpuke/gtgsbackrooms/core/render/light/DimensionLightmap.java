package com.gtgpuke.gtgsbackrooms.core.render.light;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Courbe d'un profil appliquee a chaque point de la lightmap d'un monde, au moment ou vanilla la calcule (une fois par
 * tick, 256 points) : plancher et plafond de luminosite, puis chaleur (le rouge monte, le bleu baisse). Rien n'est
 * alloue. La lightmap de chaque monde vu par un portail passe par le meme calcul, avec le profil de ce monde.
 */
public final class DimensionLightmap {

    /** Part de rouge ajoutee a chaleur pleine. */
    static final float WARM_RED = 0.15f;
    /** Part de bleu retiree a chaleur pleine. */
    static final float WARM_BLUE = 0.25f;
    private static final float SCALE = 255.0f;

    private DimensionLightmap() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Applique la courbe a un point de la lightmap, puis la baisse du moment (un cri lie a l'image fait baisser toute
     * la lumiere de sa dimension).
     *
     * @param curve courbe du profil
     * @param argb point tel que vanilla l'ecrit : rouge dans l'octet bas, vert, bleu, alpha opaque
     * @param flicker baisse de lumiere, de 0 (aucune) a 1 (noir)
     * @return le point courbe, ou le point recu si la courbe est celle de vanilla
     */
    public static int apply(LookProfile.Lightmap curve, int argb, float flicker) {
        if (curve.vanilla()) {
            return argb;
        }
        float red = (argb & 0xFF) / SCALE;
        float green = (argb >> 8 & 0xFF) / SCALE;
        float blue = (argb >> 16 & 0xFF) / SCALE;
        float span = curve.maxBrightness() - curve.floor();
        float kept = 1.0f - flicker;
        red = (curve.floor() + span * red) * kept;
        green = (curve.floor() + span * green) * kept;
        blue = (curve.floor() + span * blue) * kept;
        red *= 1.0f + WARM_RED * curve.warmth();
        blue *= 1.0f - WARM_BLUE * curve.warmth();
        return 0xFF000000 | channel(blue) << 16 | channel(green) << 8 | channel(red);
    }

    /**
     * Le vacillement que vanilla tire a chaque tick pour la lumiere de bloc, celui d'une torche. Un monde a courbe n'a que
     * sa propre lumiere : ce tirage ferait trembler a chaque tick tout ce que la lightmap eclaire, comme le fond d'une
     * grande salle.
     *
     * @param curve courbe du profil
     * @param flicker vacillement tire par vanilla
     * @return 0 pour un monde a courbe, le vacillement recu sinon
     */
    public static float blockFlicker(LookProfile.Lightmap curve, float flicker) {
        return curve.vanilla() ? flicker : 0.0f;
    }

    private static int channel(float value) {
        return Math.round(Math.clamp(value, 0.0f, 1.0f) * SCALE);
    }
}
