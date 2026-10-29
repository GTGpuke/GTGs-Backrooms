package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Lien son-image")
class CueTest {

    private static final float EPSILON = 1.0e-6f;
    private static final String SCREAM = "assets/gtgs_backrooms/sound_cues/scream.json";

    @Test
    @DisplayName("Le lien du cri livre avec le mod se lit : son enveloppe, ses reactions")
    void screamCueIsRead() throws IOException {
        Cue cue = Cue.fromJson(JsonParser.parseString(resource(SCREAM)).getAsJsonObject());
        assertEquals(Identifier.of("gtgs_backrooms", "scream"), cue.id(), "Identifiant.");
        assertEquals(1, cue.sounds().size(), "Un son.");
        assertEquals(Identifier.of("gtgs_backrooms", "entity.howler.scream"), cue.sounds().get(0), "Le cri.");
        assertTrue(cue.envelope(), "Son enveloppe est lue.");
        assertEquals(4, cue.reactions().size(), "Quatre reactions.");
        Cue.Reaction neons = cue.reactions().get(0);
        assertEquals(Cue.Type.NEON_DISTURBANCE, neons.type(), "Les neons proches vacillent.");
        assertEquals(12.0f, neons.radius(), EPSILON, "A douze blocs.");
        assertEquals(0.8f, neons.strength(), EPSILON, "Fort.");
        assertTrue(neons.followEnvelope(), "Au rythme du cri.");
        Cue.Reaction pulse = cue.reactions().get(1);
        assertEquals(Cue.Type.SCREEN_PULSE, pulse.type(), "L'ecran pulse.");
        assertEquals(24.0f, pulse.maxDistance(), EPSILON, "A moins de vingt-quatre blocs.");
        assertEquals(Cue.Type.LIGHTMAP_FLICKER, cue.reactions().get(2).type(), "La lumiere baisse.");
        Cue.Reaction shake = cue.reactions().get(3);
        assertEquals(Cue.Type.CAMERA_SHAKE, shake.type(), "La camera tremble (si l'option l'autorise).");
        assertEquals(16.0f, shake.maxDistance(), EPSILON, "A moins de seize blocs.");
        assertEquals(Easing.OUT_QUAD, shake.easing(), "Le tremblement monte tot.");
        assertEquals(Easing.LINEAR, neons.easing(), "Les neons suivent le cri tel quel.");
    }

    @Test
    @DisplayName("La force du moment suit l'amplitude par la courbe, ou reste pleine sans enveloppe")
    void forceFollowsTheCurve() {
        Cue.Reaction curved = new Cue.Reaction(Cue.Type.CAMERA_SHAKE, 0.0f, 0.5f, true, 64.0f, Easing.IN_QUAD);
        assertEquals(0.5f * 0.25f, curved.force(0.5f), EPSILON, "La moitie de l'amplitude au carre.");
        Cue.Reaction steady = new Cue.Reaction(Cue.Type.CAMERA_SHAKE, 0.0f, 0.5f, false, 64.0f, Easing.IN_QUAD);
        assertEquals(0.5f, steady.force(0.1f), EPSILON, "Sans enveloppe : la force entiere.");
        Cue parsed = parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"neon_disturbance\", \"easing\": \"in_out_sine\" }] }");
        assertEquals(Easing.IN_OUT_SINE, parsed.reactions().get(0).easing(), "Courbe lue.");
    }

    @Test
    @DisplayName("Les champs ont leurs defauts : sans enveloppe, force pleine, rayon nul, portee maximale, suit l'enveloppe")
    void defaults() {
        Cue cue = parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\" }] }");
        assertFalse(cue.envelope(), "Sans enveloppe.");
        assertFalse(parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"envelope\": false, \"reactions\": [{ \"type\": \"screen_pulse\" }] }").envelope(),
                "Enveloppe refusee explicitement.");
        Cue.Reaction reaction = cue.reactions().get(0);
        assertEquals(1.0f, reaction.strength(), EPSILON, "Force pleine.");
        assertEquals(0.0f, reaction.radius(), EPSILON, "Rayon nul.");
        assertEquals(64.0f, reaction.maxDistance(), EPSILON, "Portee maximale.");
        assertTrue(reaction.followEnvelope(), "Suit l'enveloppe par defaut.");
        Cue fixed = parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"envelope\": true, \"reactions\": [{ \"type\": \"lightmap_flicker\", "
                + "\"strength\": 0.3, \"follow_envelope\": false }] }");
        assertTrue(fixed.envelope(), "Enveloppe demandee.");
        assertFalse(fixed.reactions().get(0).followEnvelope(), "Force constante.");
        assertEquals("neon_disturbance", Cue.Type.NEON_DISTURBANCE.key(), "Le nom du type dans les fichiers.");
    }

    @Test
    @DisplayName("Un lien invalide est refuse : identifiant, sons, reactions, type, valeurs")
    void invalidCuesAreRefused() {
        assertThrows(JsonParseException.class, () -> parse("{ \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"), "Sans identifiant.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"M:C\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"),
                "Identifiant invalide.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"reactions\": [{ \"type\": \"screen_pulse\" }] }"), "Sans liste de sons.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"), "Aucun son.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"M:S\"], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"),
                "Son invalide.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [{}], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"),
                "Son qui n'est pas un nom.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [] }"), "Aucune reaction.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [\"x\"] }"), "Reaction qui n'est pas un objet.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"lens_flare\" }] }"),
                "Type inconnu.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"camera_shake\", \"easing\": \"bounce\" }] }"),
                "Courbe inconnue.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\", \"strength\": 2 }] }"),
                "Force hors plage.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"neon_disturbance\", \"radius\": 100 }] }"),
                "Rayon hors plage.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"strength\": 1 }] }"), "Sans type.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": {}, \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\" }] }"),
                "Identifiant qui n'est pas un nom.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": \"m:s\", \"reactions\": [{ \"type\": \"screen_pulse\" }] }"),
                "Sons qui ne sont pas une liste.");
        assertThrows(JsonParseException.class, () -> parse("{ \"id\": \"m:c\", \"sounds\": [\"m:s\"], \"reactions\": [{ \"type\": \"screen_pulse\", \"strength\": -1 }] }"),
                "Force negative.");
    }

    private static Cue parse(String json) {
        return Cue.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = CueTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Ressource absente : " + path + ".");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
