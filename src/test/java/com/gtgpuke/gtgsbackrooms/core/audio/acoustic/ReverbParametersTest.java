package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

@DisplayName("Reglages de la reverb OpenAL d'apres le profil")
class ReverbParametersTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("La reverb du profil devient des reglages dans les plages de l'effet")
    void profileReverbMapsToEffectRanges() {
        ReverbParameters parameters = ReverbParameters.of(new AudioProfile.Reverb(1.1f, 0.8f, 0.9f, 0.32f, 0.7f));
        assertEquals(1.1f, parameters.decayTime(), EPSILON, "Decroissance telle quelle.");
        assertEquals(0.8f, parameters.density(), EPSILON, "Densite.");
        assertEquals(0.9f, parameters.diffusion(), EPSILON, "Diffusion.");
        assertEquals(0.32f, parameters.gain(), EPSILON, "Gain.");
        assertEquals(0.73f, parameters.gainHf(), EPSILON, "Les aigus gardes suivent la clarte.");
        assertEquals(1.14f, parameters.decayHfRatio(), EPSILON, "Les aigus decroissent avec la clarte.");
        assertEquals(1.16f, parameters.lateGain(), EPSILON, "La reverb tardive suit le gain.");
    }

    @Test
    @DisplayName("Les valeurs hors plage sont ramenees dedans")
    void outOfRangeValuesAreClamped() {
        ReverbParameters low = ReverbParameters.of(new AudioProfile.Reverb(0.0f, -1.0f, -1.0f, -1.0f, -1.0f));
        assertEquals(0.1f, low.decayTime(), EPSILON, "Decroissance minimale.");
        assertEquals(0.0f, low.density(), EPSILON, "Densite plancher.");
        assertEquals(0.0f, low.diffusion(), EPSILON, "Diffusion plancher.");
        assertEquals(0.0f, low.gain(), EPSILON, "Gain plancher.");
        assertEquals(0.1f, low.gainHf(), EPSILON, "Une reverb sourde garde un dixieme des aigus.");
        assertEquals(0.3f, low.decayHfRatio(), EPSILON, "Rapport des aigus plancher.");
        assertEquals(1.0f, low.lateGain(), EPSILON, "Reverb tardive sans gain.");
        ReverbParameters high = ReverbParameters.of(new AudioProfile.Reverb(50.0f, 2.0f, 2.0f, 2.0f, 2.0f));
        assertEquals(20.0f, high.decayTime(), EPSILON, "Decroissance maximale.");
        assertEquals(1.0f, high.density(), EPSILON, "Densite plafond.");
        assertEquals(1.0f, high.diffusion(), EPSILON, "Diffusion plafond.");
        assertEquals(1.0f, high.gain(), EPSILON, "Gain plafond.");
        assertEquals(1.0f, high.gainHf(), EPSILON, "Tous les aigus.");
        assertEquals(1.5f, high.decayHfRatio(), EPSILON, "Rapport des aigus d'une reverb claire.");
        assertEquals(1.5f, high.lateGain(), EPSILON, "Reverb tardive d'un gain plein.");
    }
}
