package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

/**
 * Les reverbs preparees d'une dimension : un petit nombre fixe d'effets EFX, partages par toutes ses sources, chacune
 * envoyee vers celui qui ressemble le plus a son acoustique ({@link AcousticResult}). Quatre tailles de salle (d'apres
 * le libre parcours moyen des rayons) et deux ouvertures (salle fermee, espace ouvert) : huit reglages tires de la
 * reverb de base du profil, celle de ses salles telles que la generation les fait (taille {@link #BASE}).
 */
public final class ReverbPresets {

    /** Nombre de reglages par dimension. */
    public static final int COUNT = 8;
    /** Le reglage de base : une petite salle fermee, la reverb du profil telle quelle. */
    public static final int BASE = 1;
    /** Libre parcours moyen, en blocs, au-dela duquel la salle passe a la taille suivante. */
    static final float[] SIZE_LIMITS = {4.0f, 8.0f, 16.0f};
    /** Duree de decroissance par taille, par rapport a celle du profil. */
    static final float[] DECAY_SCALE = {0.5f, 1.0f, 1.6f, 2.5f};
    /** Part de rayons sortis a partir de laquelle l'espace est ouvert. */
    static final float OPEN_THRESHOLD = 0.35f;
    /** Reverb d'un espace ouvert : plus courte et plus faible. */
    static final float OPEN_DECAY_SCALE = 0.7f;
    static final float OPEN_GAIN_SCALE = 0.4f;
    private static final int OPEN = 4;

    private ReverbPresets() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Le reglage le plus proche d'une acoustique : sa taille, et {@code +4} si l'espace est ouvert. */
    public static int index(AcousticResult result) {
        int size = sizeClass(result.roomSize());
        return open(result) ? size + OPEN : size;
    }

    /** La taille d'une salle, de 0 (placard) a 3 (grande salle), d'apres son libre parcours moyen. */
    public static int sizeClass(float roomSize) {
        int size = 0;
        while (size < SIZE_LIMITS.length && roomSize >= SIZE_LIMITS[size]) {
            size++;
        }
        return size;
    }

    /** {@code true} si l'espace est ouvert. */
    public static boolean open(AcousticResult result) {
        return result.openness() >= OPEN_THRESHOLD;
    }

    /**
     * Les reglages EFX d'un reglage prepare d'une reverb de profil.
     *
     * @param reverb reverb de base du profil
     * @param preset reglage, de 0 a {@value #COUNT} exclu
     */
    public static ReverbParameters parameters(AudioProfile.Reverb reverb, int preset) {
        int size = preset % OPEN;
        boolean open = preset >= OPEN;
        float decay = reverb.decay() * DECAY_SCALE[size] * (open ? OPEN_DECAY_SCALE : 1.0f);
        float gain = reverb.gain() * (open ? OPEN_GAIN_SCALE : 1.0f);
        return ReverbParameters.of(new AudioProfile.Reverb(decay, reverb.density(), reverb.diffusion(), gain, reverb.brightness()));
    }
}
