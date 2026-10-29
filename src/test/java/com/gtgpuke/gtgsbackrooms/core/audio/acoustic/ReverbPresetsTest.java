package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

@DisplayName("Reverbs preparees d'une dimension")
class ReverbPresetsTest {

    private static final float EPSILON = 1.0e-6f;
    private static final AudioProfile.Reverb BASE = new AudioProfile.Reverb(1.1f, 0.8f, 0.9f, 0.32f, 0.7f);

    @Test
    @DisplayName("Le reglage suit la taille de la salle, puis l'ouverture")
    void presetFollowsSizeAndOpenness() {
        assertEquals(0, ReverbPresets.index(new AcousticResult(2.0f, 0.0f, 0.0f, 0.0f)), "Un placard.");
        assertEquals(1, ReverbPresets.index(new AcousticResult(4.0f, 0.0f, 0.0f, 0.0f)), "Un couloir : le reglage de base.");
        assertEquals(ReverbPresets.BASE, ReverbPresets.index(new AcousticResult(7.9f, 0.3f, 0.0f, 0.0f)), "Encore le couloir, presque ouvert.");
        assertEquals(2, ReverbPresets.index(new AcousticResult(8.0f, 0.0f, 0.0f, 0.0f)), "Une salle.");
        assertEquals(3, ReverbPresets.index(new AcousticResult(40.0f, 0.0f, 0.0f, 0.0f)), "Une grande salle.");
        assertEquals(5, ReverbPresets.index(new AcousticResult(6.0f, 0.35f, 0.0f, 0.0f)), "Un couloir ouvert.");
        assertEquals(7, ReverbPresets.index(new AcousticResult(48.0f, 1.0f, 0.0f, 0.0f)), "Le plein air.");
        assertTrue(ReverbPresets.index(new AcousticResult(48.0f, 1.0f, 0.0f, 0.0f)) < ReverbPresets.COUNT, "Dans les huit reglages.");
    }

    @Test
    @DisplayName("Le reglage de base est la reverb du profil ; les autres en tirent leur duree et leur gain")
    void parametersDeriveFromTheProfile() {
        assertEquals(ReverbParameters.of(BASE), ReverbPresets.parameters(BASE, ReverbPresets.BASE), "Telle quelle.");
        ReverbParameters closet = ReverbPresets.parameters(BASE, 0);
        assertEquals(0.55f, closet.decayTime(), EPSILON, "Un placard : moitie moins long.");
        assertEquals(0.32f, closet.gain(), EPSILON, "Meme gain, ferme.");
        ReverbParameters hall = ReverbPresets.parameters(BASE, 3);
        assertEquals(2.75f, hall.decayTime(), EPSILON, "Une grande salle : plus long.");
        ReverbParameters open = ReverbPresets.parameters(BASE, 5);
        assertEquals(1.1f * 0.7f, open.decayTime(), EPSILON, "Ouvert : plus court que le couloir ferme.");
        assertEquals(0.32f * 0.4f, open.gain(), EPSILON, "Et plus faible.");
        assertEquals(BASE.density(), open.density(), EPSILON, "Densite, diffusion et clarte du profil.");
        assertEquals(ReverbParameters.of(BASE).gainHf(), open.gainHf(), EPSILON, "La clarte aussi.");
    }
}
