package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Perturbations en cours")
class DisturbancesTest {

    private static final float EPSILON = 1.0e-6f;
    private static final Identifier LEVEL_0 = Identifier.of("gtgs_backrooms", "backrooms_level_0");
    private static final Identifier OVERWORLD = Identifier.of("minecraft", "overworld");
    private static final Cue SCREAM = new Cue(Identifier.of("m", "scream"), List.of(Identifier.of("m", "s")), true, List.of(
            new Cue.Reaction(Cue.Type.NEON_DISTURBANCE, 10.0f, 0.8f, true, 64.0f, Easing.LINEAR),
            new Cue.Reaction(Cue.Type.SCREEN_PULSE, 0.0f, 0.5f, false, 20.0f, Easing.LINEAR),
            new Cue.Reaction(Cue.Type.LIGHTMAP_FLICKER, 0.0f, 0.4f, true, 64.0f, Easing.LINEAR)));
    private static final Cue PULSE_ONLY = new Cue(Identifier.of("m", "pulse"), List.of(Identifier.of("m", "p")), false,
            List.of(new Cue.Reaction(Cue.Type.SCREEN_PULSE, 0.0f, 0.2f, true, 64.0f, Easing.LINEAR)));
    private static final Cue STEADY = new Cue(Identifier.of("m", "steady"), List.of(Identifier.of("m", "t")), false, List.of(
            new Cue.Reaction(Cue.Type.NEON_DISTURBANCE, 4.0f, 0.5f, false, 64.0f, Easing.LINEAR),
            new Cue.Reaction(Cue.Type.LIGHTMAP_FLICKER, 0.0f, 0.3f, false, 64.0f, Easing.LINEAR)));

    @Test
    @DisplayName("Une reaction qui ne suit pas l'enveloppe garde sa force quelle que soit l'amplitude ; sans pulsation, l'ecran ne pulse pas")
    void steadyReactionsIgnoreAmplitude() {
        Disturbances disturbances = new Disturbances();
        int slot = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, STEADY, 1L);
        disturbances.amplitude(slot, 0.1f);
        assertEquals(1.0f - 0.5f, disturbances.neonFactor(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "Les neons : la force entiere, amplitude ignoree.");
        assertEquals(0.3f, disturbances.lightmapFlicker(LEVEL_0), EPSILON, "La lumiere : la force entiere.");
        assertEquals(0.0f, disturbances.screenPulse(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "Aucune pulsation demandee.");
    }

    @Test
    @DisplayName("Le rendu lit la source, la portee et la force de chaque perturbation qui touche les neons de sa dimension")
    void neonDisturbancesAreReadForTheShaders() {
        Disturbances disturbances = new Disturbances();
        int scream = disturbances.start(LEVEL_0, 1.0, 2.0, 3.0, SCREAM, 1L);
        int pulse = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 2L);
        assertTrue(disturbances.disturbsNeons(scream, LEVEL_0), "Le cri touche les neons du Level 0.");
        assertFalse(disturbances.disturbsNeons(scream, OVERWORLD), "Pas ceux de l'Overworld.");
        assertFalse(disturbances.disturbsNeons(pulse, LEVEL_0), "Une pulsation seule ne touche pas les neons.");
        assertFalse(disturbances.disturbsNeons(3, LEVEL_0), "Un emplacement libre non plus.");
        assertEquals(1.0, disturbances.x(scream), EPSILON, "Source x.");
        assertEquals(2.0, disturbances.y(scream), EPSILON, "Source y.");
        assertEquals(3.0, disturbances.z(scream), EPSILON, "Source z.");
        assertEquals(10.0f, disturbances.neonRadius(scream), EPSILON, "Portee.");
        assertEquals(0.8f, disturbances.neonForce(scream), EPSILON, "Force entiere a l'amplitude 1.");
        disturbances.amplitude(scream, 0.5f);
        assertEquals(0.4f, disturbances.neonForce(scream), EPSILON, "La force suit l'amplitude.");
        int steady = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, STEADY, 3L);
        disturbances.amplitude(steady, 0.1f);
        assertEquals(0.5f, disturbances.neonForce(steady), EPSILON, "Sans suivre l'enveloppe, la force reste entiere.");
    }

    @Test
    @DisplayName("Les neons proches d'un cri perdent de leur intensite avec la distance et l'amplitude, dans sa dimension seulement")
    void neonsNearAScreamDrop() {
        Disturbances disturbances = new Disturbances();
        int slot = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, SCREAM, 100L);
        assertTrue(disturbances.active(slot), "En cours.");
        assertEquals(1, disturbances.count(), "Une perturbation.");
        assertEquals(LEVEL_0, disturbances.dimension(slot), "Dans la dimension du cri.");
        assertEquals(1.0f, disturbances.currentAmplitude(slot), EPSILON, "Amplitude 1 au depart.");
        assertEquals(1.0f - 0.8f, disturbances.neonFactor(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "A la source, toute la force (amplitude 1 au depart).");
        assertEquals(1.0f - 0.4f, disturbances.neonFactor(LEVEL_0, 5.0, 0.0, 0.0), EPSILON, "A mi-rayon, la moitie.");
        assertEquals(1.0f, disturbances.neonFactor(LEVEL_0, 10.0, 0.0, 0.0), EPSILON, "Au rayon, plus rien.");
        assertEquals(1.0f, disturbances.neonFactor(OVERWORLD, 0.0, 0.0, 0.0), EPSILON, "Une autre dimension ne vacille pas.");
        assertEquals(1.0f, disturbances.neonFactor(null, 0.0, 0.0, 0.0), EPSILON, "Sans dimension non plus.");
        disturbances.amplitude(slot, 0.25f);
        assertEquals(1.0f - 0.2f, disturbances.neonFactor(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "Au quart de l'amplitude, le quart de la force.");
        assertEquals(0.4f * 0.25f, disturbances.lightmapFlicker(LEVEL_0), EPSILON, "La lumiere baisse au rythme du cri.");
        assertEquals(0.0f, disturbances.lightmapFlicker(OVERWORLD), EPSILON, "Pas ailleurs.");
        assertEquals(0.5f, disturbances.screenPulse(LEVEL_0, 3.0, 0.0, 0.0), EPSILON, "La pulsation ne suit pas l'enveloppe ici : constante.");
        assertEquals(0.0f, disturbances.screenPulse(LEVEL_0, 30.0, 0.0, 0.0), EPSILON, "Trop loin de l'auditeur.");
        assertEquals(0.0f, disturbances.screenPulse(OVERWORLD, 3.0, 0.0, 0.0), EPSILON, "Pas dans l'Overworld.");
        disturbances.move(slot, 100.0, 0.0, 0.0);
        assertEquals(1.0f, disturbances.neonFactor(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "La source a bouge : loin.");
        assertEquals(1.0f - 0.2f, disturbances.neonFactor(LEVEL_0, 100.0, 0.0, 0.0), EPSILON, "Et vacille la-bas.");
        disturbances.end(slot);
        assertFalse(disturbances.active(slot), "Finie.");
        assertNull(disturbances.dimension(slot), "Plus de dimension.");
        assertEquals(1.0f, disturbances.neonFactor(LEVEL_0, 100.0, 0.0, 0.0), EPSILON, "Plus rien.");
        assertEquals(0.0f, disturbances.lightmapFlicker(LEVEL_0), EPSILON, "Plus rien non plus.");
        assertEquals(0.0f, disturbances.screenPulse(LEVEL_0, 100.0, 0.0, 0.0), EPSILON, "Ni pulsation.");
    }

    @Test
    @DisplayName("Le tremblement suit le cri a portee de l'auditeur, dans sa dimension ; la charge somme les reactions lumineuses")
    void shakeAndLoad() {
        Cue shaking = new Cue(Identifier.of("m", "shake"), List.of(Identifier.of("m", "k")), true, List.of(
                new Cue.Reaction(Cue.Type.CAMERA_SHAKE, 0.0f, 0.6f, true, 16.0f, Easing.IN_QUAD),
                new Cue.Reaction(Cue.Type.LIGHTMAP_FLICKER, 0.0f, 0.0f, true, 64.0f, Easing.LINEAR)));
        Disturbances disturbances = new Disturbances();
        int slot = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, shaking, 1L);
        assertEquals(0.6f, disturbances.cameraShake(LEVEL_0, 4.0, 0.0, 0.0), EPSILON, "A portee : la force entiere a l'amplitude 1.");
        assertEquals(0.0f, disturbances.cameraShake(LEVEL_0, 20.0, 0.0, 0.0), EPSILON, "Trop loin.");
        assertEquals(0.0f, disturbances.cameraShake(OVERWORLD, 0.0, 0.0, 0.0), EPSILON, "Jamais dans une autre dimension.");
        disturbances.amplitude(slot, 0.5f);
        assertEquals(0.6f * 0.25f, disturbances.cameraShake(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "La courbe au carre.");
        assertEquals(0.0f, disturbances.lightmapFlicker(LEVEL_0), EPSILON, "Une reaction de force nulle n'existe pas.");
        assertEquals(0.0f, disturbances.load(LEVEL_0), EPSILON, "Le tremblement n'est pas une variation de lumiere.");
        assertEquals(0.0f, disturbances.neonRadius(slot), EPSILON, "Pas de portee sur les neons.");
        assertEquals(0.0f, disturbances.neonForce(slot), EPSILON, "Ni de force.");
        disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, SCREAM, 2L);
        assertEquals(1.0f, disturbances.load(LEVEL_0), EPSILON, "Neons, pulsation et lumiere d'un cri : borne a 1.");
        assertEquals(0.0f, disturbances.load(OVERWORLD), EPSILON, "Rien dans l'Overworld.");
        disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 3L);
        disturbances.clear();
        disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 4L);
        assertEquals(0.2f, disturbances.load(LEVEL_0), EPSILON, "Une pulsation seule : sa force.");
    }

    @Test
    @DisplayName("Quatre perturbations au plus : la plus ancienne cede sa place ; une reaction absente ne compte pas")
    void oldestGivesWay() {
        Disturbances disturbances = new Disturbances();
        int first = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 10L);
        disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 30L);
        int third = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 5L);
        disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, PULSE_ONLY, 40L);
        assertEquals(Disturbances.CAPACITY, disturbances.count(), "Pleine.");
        assertEquals(1.0f, disturbances.neonFactor(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "Une pulsation seule ne touche pas aux neons.");
        assertEquals(0.0f, disturbances.lightmapFlicker(LEVEL_0), EPSILON, "Ni a la lumiere.");
        disturbances.amplitude(third, 0.5f);
        assertEquals(0.2f, disturbances.screenPulse(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "La plus forte des quatre (amplitude pleine).");
        int replaced = disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, SCREAM, 50L);
        assertEquals(third, replaced, "La plus ancienne (partie a cinq) a cede sa place.");
        assertEquals(Disturbances.CAPACITY, disturbances.count(), "Toujours pleine.");
        assertEquals(0.5f, disturbances.screenPulse(LEVEL_0, 0.0, 0.0, 0.0), EPSILON, "Le cri est la plus forte.");
        disturbances.clear();
        assertEquals(0, disturbances.count(), "Toutes finies.");
        assertEquals(first, disturbances.start(LEVEL_0, 0.0, 0.0, 0.0, SCREAM, 60L), "Le premier emplacement est de nouveau libre.");
    }
}
