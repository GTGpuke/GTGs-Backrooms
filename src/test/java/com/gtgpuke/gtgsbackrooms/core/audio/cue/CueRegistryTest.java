package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Registre des liens son-image")
class CueRegistryTest {

    private static final Identifier SCREAM = Identifier.of("m", "scream");
    private static final Identifier HOWL = Identifier.of("m", "howl");

    @AfterEach
    void clear() {
        CueRegistry.clear();
    }

    @Test
    @DisplayName("Un son mene a son lien ; un son reclame par deux liens reste au premier")
    void soundsLeadToTheirCue() {
        Cue first = cue("m:first", SCREAM);
        Cue second = cue("m:second", SCREAM, HOWL);
        CueRegistry.load(List.of(first, second));
        assertEquals(2, CueRegistry.size(), "Deux liens charges.");
        assertSame(first, CueRegistry.of(SCREAM), "Le premier garde le cri.");
        assertSame(second, CueRegistry.of(HOWL), "Le second a le hurlement.");
        assertNull(CueRegistry.of(Identifier.of("m", "step")), "Un son ordinaire n'a pas de lien.");
        assertEquals(List.of(first, second), CueRegistry.cues(), "Dans l'ordre de chargement.");
        CueRegistry.clear();
        assertEquals(0, CueRegistry.size(), "Plus rien.");
        assertNull(CueRegistry.of(SCREAM), "Plus de lien.");
    }

    private static Cue cue(String id, Identifier... sounds) {
        return new Cue(Identifier.of(id), List.of(sounds), false, List.of(new Cue.Reaction(Cue.Type.SCREEN_PULSE, 0.0f, 1.0f, true, 64.0f, Easing.LINEAR)));
    }
}
