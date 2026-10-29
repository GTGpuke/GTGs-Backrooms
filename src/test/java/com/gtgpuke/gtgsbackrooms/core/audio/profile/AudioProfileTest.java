package com.gtgpuke.gtgsbackrooms.core.audio.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.core.registry.ModSounds;

@DisplayName("Profil audio d'une dimension")
class AudioProfileTest {

    private static final String LEVEL_0 = "assets/gtgs_backrooms/audio_profiles/level_0.json";
    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Le profil du Level 0 livre avec le mod se lit entierement et son bourdonnement est un son du mod")
    void levelZeroProfileIsRead() throws IOException {
        MinecraftTestBootstrap.ensure();
        AudioProfile profile = AudioProfile.fromJson(JsonParser.parseString(resource(LEVEL_0)).getAsJsonObject());
        assertEquals(Identifier.of("gtgs_backrooms", "backrooms_level_0"), profile.dimension(), "Dimension.");
        assertFalse(profile.vanilla(), "Pas vanilla.");
        assertSame(AudioProfile.Acoustics.GENERATED, profile.acoustics(), "Le Level 0 tire son acoustique de sa generation.");
        assertEquals(new AudioProfile.Reverb(1.1f, 0.8f, 0.9f, 0.32f, 0.7f), profile.reverb(), "Reverb de base.");
        assertTrue(profile.reverb().any(), "Une reverb a entendre.");
        assertEquals(new AudioProfile.Occlusion(0.6f, 0.25f, 0.85f), profile.occlusion(), "Occlusion.");
        assertTrue(profile.occlusion().any(), "Les murs etouffent.");
        assertEquals(0.6f, profile.airAbsorption(), EPSILON, "Absorption de l'air.");
        assertEquals(new AudioProfile.Medium(0.8f, 0.15f), profile.underwater(), "Sous l'eau, les aigus s'eteignent.");
        assertTrue(profile.ambience().any(), "Les neons bourdonnent.");
        assertEquals(0.25f, profile.ambience().humVolume(), EPSILON, "Volume du bourdonnement.");
        Identifier hum = profile.ambience().hum();
        assertNotNull(hum, "Un son de bourdonnement.");
        assertTrue(Registries.SOUND_EVENT.containsId(hum), "Le son " + hum + " est enregistre : un nom faux laisserait le Level 0 muet.");
        assertSame(ModSounds.NEON_HUM, Registries.SOUND_EVENT.get(hum), "Le bourdonnement des neons.");
        assertSame(ModSounds.NEON_CLICK_OFF, Registries.SOUND_EVENT.get(profile.ambience().clickOff()), "Le claquement d'un neon qui s'eteint.");
        assertSame(ModSounds.NEON_CLICK_ON, Registries.SOUND_EVENT.get(profile.ambience().clickOn()), "Le claquement d'un neon qui se rallume.");
    }

    @Test
    @DisplayName("Les claquements des neons sont facultatifs, et refuses s'ils ne sont pas des noms de son")
    void clicksAreOptional() {
        AudioProfile.Ambience ambience = parse("{ \"dimension\": \"m:d\", \"ambience\": { \"hum\": \"m:h\" } }").ambience();
        assertNull(ambience.clickOff(), "Sans claquement a l'extinction.");
        assertNull(ambience.clickOn(), "Ni au rallumage.");
        assertTrue(ambience.any(), "Le bourdonnement suffit a l'ambiance.");
        assertEquals(Identifier.of("m", "off"), parse("{ \"dimension\": \"m:d\", \"ambience\": { \"hum\": \"m:h\", \"click_off\": \"m:off\" } }").ambience().clickOff(),
                "Un claquement lu.");
        assertThrows(JsonParseException.class, () -> parse("{ \"dimension\": \"m:d\", \"ambience\": { \"hum\": \"m:h\", \"click_on\": \"M:ON\" } }"),
                "Un claquement invalide est refuse.");
    }

    @Test
    @DisplayName("Un profil minimal n'a que sa dimension : ni reverb, ni occlusion, ni ambiance")
    void minimalProfileHasNoTreatment() {
        AudioProfile profile = parse("{ \"dimension\": \"m:d\" }");
        assertFalse(profile.vanilla(), "Un profil, meme vide.");
        assertSame(AudioProfile.Acoustics.NONE, profile.acoustics(), "Sans calcul : la reverb de base partout.");
        assertSame(AudioProfile.Acoustics.NONE, parse("{ \"dimension\": \"m:d\", \"acoustics\": \"none\" }").acoustics(), "Dit explicitement.");
        assertSame(AudioProfile.Acoustics.RAYCAST, parse("{ \"dimension\": \"m:d\", \"acoustics\": \"raycast\" }").acoustics(), "Par rayons.");
        assertSame(AudioProfile.Acoustics.GENERATED, parse("{ \"dimension\": \"m:d\", \"acoustics\": \"generated\" }").acoustics(),
                "La forme des salles tiree de la generation.");
        assertThrows(JsonParseException.class, () -> parse("{ \"dimension\": \"m:d\", \"acoustics\": \"magic\" }"), "Acoustique inconnue refusee.");
        assertFalse(AudioProfile.Acoustics.NONE.computed(), "La reverb de base se pose sans calcul.");
        assertTrue(AudioProfile.Acoustics.RAYCAST.computed(), "Les rayons se calculent sur une copie du monde.");
        assertTrue(AudioProfile.Acoustics.GENERATED.computed(), "La forme generee aussi (rayons la ou elle ne suffit pas).");
        assertSame(AudioProfile.Reverb.NONE, profile.reverb(), "Sans reverb.");
        assertFalse(profile.reverb().any(), "Rien a entendre.");
        assertSame(AudioProfile.Occlusion.NONE, profile.occlusion(), "Sans occlusion.");
        assertFalse(profile.occlusion().any(), "Rien a retirer.");
        assertEquals(0.0f, profile.airAbsorption(), "Sans absorption.");
        assertSame(AudioProfile.Medium.NONE, profile.underwater(), "Sans effet de l'eau.");
        assertEquals(0.0f, profile.perceivedDirection(), "Sans direction percue.");
        assertEquals(0.4f, parse("{ \"dimension\": \"m:d\", \"perceived_direction\": 0.4 }").perceivedDirection(), 1.0e-6f, "Lue.");
        assertFalse(profile.underwater().any(), "L'eau laisse tout passer.");
        assertSame(AudioProfile.Ambience.NONE, profile.ambience(), "Sans ambiance.");
        assertFalse(profile.ambience().any(), "Silence.");
        assertNull(AudioProfile.VANILLA.dimension(), "Le profil vanilla n'a pas de dimension.");
        assertTrue(AudioProfile.VANILLA.vanilla(), "Et il est vanilla.");
    }

    @Test
    @DisplayName("Les sections se lisent avec leurs defauts")
    void sectionsHaveDefaults() {
        AudioProfile profile = parse("{ \"dimension\": \"m:d\", \"reverb_base\": { \"gain\": 0.5 }, \"occlusion\": { \"wall\": 0.5 }, \"ambience\": { \"hum\": \"m:h\" } }");
        assertEquals(new AudioProfile.Reverb(1.0f, 1.0f, 1.0f, 0.5f, 0.5f), profile.reverb(), "Defauts de la reverb.");
        assertEquals(new AudioProfile.Occlusion(0.5f, 0.0f, 0.0f), profile.occlusion(), "Defauts de l'occlusion.");
        assertFalse(profile.occlusion().any(), "Sans plafond, les murs ne retirent rien.");
        assertTrue(new AudioProfile.Occlusion(0.0f, 0.2f, 0.5f).any(), "Les blocs partiels seuls suffisent.");
        assertFalse(new AudioProfile.Occlusion(0.0f, 0.0f, 0.5f).any(), "Un plafond sans poids ne retire rien.");
        assertEquals(0.25f, profile.ambience().humVolume(), "Volume par defaut du bourdonnement.");
        assertFalse(parse("{ \"dimension\": \"m:d\", \"ambience\": { } }").ambience().any(), "Ambiance sans son : rien.");
        assertFalse(new AudioProfile.Ambience(Identifier.of("m", "h"), 0.0f, null, null).any(), "Volume nul : rien.");
    }

    @Test
    @DisplayName("L'eau filtre le son d'apres le profil")
    void underwaterMediumIsRead() {
        AudioProfile.Medium water = parse("{ \"dimension\": \"m:d\", \"underwater\": { \"gain\": 0.8, \"gain_hf\": 0.2 } }").underwater();
        assertEquals(new AudioProfile.Medium(0.8f, 0.2f), water, "Gain et aigus gardes sous l'eau.");
        assertTrue(water.any(), "L'eau retire quelque chose.");
        assertEquals(AudioProfile.Medium.NONE, parse("{ \"dimension\": \"m:d\", \"underwater\": { } }").underwater(), "Defauts : rien de retire.");
        assertTrue(new AudioProfile.Medium(1.0f, 0.5f).any(), "Les aigus seuls suffisent.");
        assertTrue(new AudioProfile.Medium(0.5f, 1.0f).any(), "Le gain seul aussi.");
        assertRefused("{ \"dimension\": \"m:d\", \"underwater\": { \"gain_hf\": 2 } }", "gain_hf hors de");
    }

    @Test
    @DisplayName("Un profil invalide est refuse avec sa raison")
    void invalidProfilesAreRefused() {
        assertRefused("{ }", "Champ manquant : dimension");
        assertRefused("{ \"dimension\": { } }", "Champ manquant : dimension");
        assertRefused("{ \"dimension\": \"m d\" }", "Dimension invalide");
        assertRefused("{ \"dimension\": \"m:d\", \"air_absorption\": 2 }", "air_absorption hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"reverb_base\": { \"decay\": 0 } }", "reverb_base.decay hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"reverb_base\": { \"gain\": 1.5 } }", "gain hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"occlusion\": { \"max\": -1 } }", "max hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"occlusion\": { \"max\": 1.0 } }", "Occlusion max hors de [0, 1[");
        assertRefused("{ \"dimension\": \"m:d\", \"ambience\": { \"hum\": \"m h\" } }", "Son du bourdonnement invalide");
        assertRefused("{ \"dimension\": \"m:d\", \"ambience\": { \"hum\": \"m:h\", \"hum_volume\": 3 } }", "hum_volume hors de");
    }

    private static void assertRefused(String json, String reason) {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> parse(json), reason);
        assertTrue(failure.getMessage().contains(reason), "Raison : " + failure.getMessage() + ".");
    }

    private static AudioProfile parse(String json) {
        return AudioProfile.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = AudioProfileTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
