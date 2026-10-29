package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

/**
 * Les reglages d'un effet de reverberation OpenAL (EFX), tires de la reverb d'un profil : les plages sont celles de
 * l'effet standard, les valeurs hors plage ramenees dedans. Logique pure : la colle OpenAL ne fait que les poser.
 *
 * @param decayTime duree de decroissance, en secondes, de 0,1 a 20
 * @param density densite, de 0 a 1
 * @param diffusion diffusion, de 0 a 1
 * @param gain gain de l'effet, de 0 a 1
 * @param gainHf gain des aigus a l'entree, de 0 a 1
 * @param decayHfRatio decroissance des aigus par rapport aux graves, de 0,1 a 2
 * @param lateGain gain de la reverb tardive, de 0 a 10
 */
public record ReverbParameters(float decayTime, float density, float diffusion, float gain, float gainHf, float decayHfRatio, float lateGain) {

    private static final float MIN_DECAY = 0.1f;
    private static final float MAX_DECAY = 20.0f;
    private static final float MIN_HF_RATIO = 0.1f;
    private static final float MAX_HF_RATIO = 2.0f;
    private static final float MAX_LATE_GAIN = 10.0f;

    /** Les reglages d'une reverb de profil. */
    public static ReverbParameters of(AudioProfile.Reverb reverb) {
        float brightness = clamp(reverb.brightness(), 0.0f, 1.0f);
        return new ReverbParameters(
                clamp(reverb.decay(), MIN_DECAY, MAX_DECAY),
                clamp(reverb.density(), 0.0f, 1.0f),
                clamp(reverb.diffusion(), 0.0f, 1.0f),
                clamp(reverb.gain(), 0.0f, 1.0f),
                // Une reverb sourde garde un dixieme des aigus, une reverb claire les garde tous.
                0.1f + 0.9f * brightness,
                clamp(0.3f + 1.2f * brightness, MIN_HF_RATIO, MAX_HF_RATIO),
                clamp(1.0f + 0.5f * clamp(reverb.gain(), 0.0f, 1.0f), 0.0f, MAX_LATE_GAIN));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
