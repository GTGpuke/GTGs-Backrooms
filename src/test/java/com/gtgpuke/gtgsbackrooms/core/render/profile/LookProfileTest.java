package com.gtgpuke.gtgsbackrooms.core.render.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;

@DisplayName("Profil de look d'une dimension")
class LookProfileTest {

    private static final String LEVEL_0 = "assets/gtgs_backrooms/look_profiles/level_0.json";
    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Le profil du Level 0 livre avec le mod se lit entierement")
    void levelZeroProfileIsRead() throws IOException {
        LookProfile profile = LookProfile.fromJson(JsonParser.parseString(resource(LEVEL_0)).getAsJsonObject());
        assertEquals(Identifier.of("gtgs_backrooms", "backrooms_level_0"), profile.dimension(), "Dimension.");
        assertEquals(1, profile.id(), "Identifiant.");
        assertFalse(profile.vanilla(), "Pas vanilla.");
        assertEquals(1.06f, profile.tintRed(), EPSILON, "Teinte rouge.");
        assertEquals(0.82f, profile.tintBlue(), EPSILON, "Teinte bleue.");
        assertEquals(0.35f, profile.tintStrength(), EPSILON, "Force de la teinte.");
        assertEquals(0.15f, profile.desaturation(), EPSILON, "Desaturation.");
        assertEquals(0.02f, profile.grain(), EPSILON, "Grain.");
        assertEquals(0.0f, profile.flicker(), EPSILON, "Vacillement : mis de cote (decision de l'auteur du 27 octobre 2026).");
        assertEquals(new LookProfile.Lightmap(0.08f, 0.2f, 0.92f), profile.lightmap(), "Lightmap.");
        assertFalse(profile.lightmap().vanilla(), "Courbe active.");
        assertTrue(profile.fog().enabled(), "Brouillard du profil.");
        assertEquals(new Vec3d(0.48f, 0.44f, 0.30f), profile.fog().color(), "Couleur du brouillard.");
        assertEquals(4.0f, profile.fog().start(), EPSILON, "Debut du brouillard.");
        assertEquals(48.0f, profile.fog().end(), EPSILON, "Fin du brouillard.");
        assertEquals(new LookProfile.Post(0.25f, 0.0f, true, 0.6f, 0.8f), profile.post(), "Post.");
        assertFalse(profile.post().none(), "Une vignette a dessiner.");
        assertTrue(profile.neons().any(), "Des neons.");
        assertEquals(Identifier.of("gtgs_backrooms", "level_zero_fluorescent_light"), profile.neons().block(), "Le neon du Level 0.");
        assertEquals(8.0f, profile.neons().radius(), EPSILON, "Portee.");
        assertEquals(0.6f, profile.neons().strength(), EPSILON, "Force.");
        assertEquals(0.0f, profile.neons().flickering(), EPSILON, "Part qui vacille : mise de cote.");
        assertEquals(0.0f, profile.neons().dead(), EPSILON, "Part morte : mise de cote.");
    }

    @Test
    @DisplayName("Le bloc de neon du profil du Level 0 existe au registre et emet de la lumiere")
    void levelZeroNeonBlockIsRegistered() throws IOException {
        MinecraftTestBootstrap.ensure();
        LookProfile profile = LookProfile.fromJson(JsonParser.parseString(resource(LEVEL_0)).getAsJsonObject());
        Identifier block = profile.neons().block();
        assertNotNull(block, "Le Level 0 a des neons.");
        assertTrue(Registries.BLOCK.containsId(block), "Le bloc " + block + " est enregistre : un nom faux laisserait le Level 0 sans lumiere de neon.");
        assertSame(ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT, Registries.BLOCK.get(block), "Le neon fluorescent du Level 0.");
        assertTrue(Registries.BLOCK.get(block).getDefaultState().getLuminance() > 0, "Un neon emet aussi sa lumiere de bloc vanilla.");
    }

    @Test
    @DisplayName("La section des neons se lit avec ses defauts et refuse ses valeurs impossibles")
    void neonsSection() {
        LookProfile none = parse("{ \"dimension\": \"m:d\", \"id\": 1 }");
        assertFalse(none.neons().any(), "Sans section, aucun neon.");
        LookProfile minimal = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\" } }");
        assertTrue(minimal.neons().any(), "Un bloc suffit.");
        assertEquals(6.0f, minimal.neons().radius(), "Portee par defaut.");
        assertEquals(1.0f, minimal.neons().green(), "Blanc par defaut.");
        assertEquals(0.5f, minimal.neons().strength(), "Force par defaut.");
        assertEquals(0.0f, minimal.neons().flickering() + minimal.neons().dead(), "Tous brillent par defaut.");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { } }", "Champ manquant : block");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m tube\" } }", "Bloc des neons invalide");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\", \"radius\": 0.1 } }", "neons.radius hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\", \"color\": [1, 2, 1] } }", "neons.color hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\", \"strength\": 3 } }", "neons.strength hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"neons\": { \"block\": \"m:tube\", \"flickering\": 0.6, \"dead\": 0.6 } }", "depassent 1");
    }

    @Test
    @DisplayName("Un profil minimal a tout de vanilla sauf sa dimension et son identifiant")
    void minimalProfileHasVanillaDefaults() {
        LookProfile profile = parse("{ \"dimension\": \"m:d\", \"id\": 7 }");
        assertEquals(7, profile.id(), "Identifiant.");
        assertEquals(0.0f, profile.tintStrength(), "Sans teinte.");
        assertEquals(1.0f, profile.tintRed(), "Teinte neutre.");
        assertSame(LookProfile.Lightmap.VANILLA, profile.lightmap(), "Lightmap vanilla.");
        assertTrue(profile.lightmap().vanilla(), "Courbe inactive.");
        assertSame(LookProfile.Fog.VANILLA, profile.fog(), "Brouillard vanilla.");
        assertSame(LookProfile.Post.NONE, profile.post(), "Aucun post.");
        assertTrue(profile.post().none(), "Rien a dessiner.");
        assertTrue(LookProfile.VANILLA.vanilla(), "Le profil vanilla.");
        LookProfile sections = parse("{ \"dimension\": \"m:d\", \"id\": 2, \"lightmap\": {}, \"fog\": { \"end\": 10 }, \"post\": {} }");
        assertTrue(sections.lightmap().vanilla(), "Section de lightmap vide : vanilla.");
        assertEquals(0.0f, sections.fog().start(), "Debut du brouillard a zero par defaut.");
        assertEquals(10.0f, sections.fog().end(), "Fin lue.");
        assertEquals(Vec3d.ZERO, sections.fog().color(), "Couleur noire par defaut.");
        assertTrue(sections.post().none(), "Section de post vide : rien.");
        LookProfile bloom = parse("{ \"dimension\": \"m:d\", \"id\": 3, \"post\": { \"bloom\": true } }");
        assertTrue(bloom.post().bloom(), "Bloom lu.");
        assertEquals(0.7f, bloom.post().bloomThreshold(), "Seuil du bloom par defaut.");
        assertEquals(0.6f, bloom.post().bloomStrength(), "Force du bloom par defaut.");
        assertFalse(bloom.post().none(), "Le bloom seul est a dessiner.");
        assertFalse(parse("{ \"dimension\": \"m:d\", \"id\": 3, \"post\": { \"bloom\": false, \"chromatic\": 0.2 } }").post().none(),
                "L'aberration seule est a dessiner.");
        assertFalse(new LookProfile.Lightmap(0.0f, 0.5f, 1.0f).vanilla(), "La chaleur seule fait une courbe.");
        assertFalse(new LookProfile.Lightmap(0.0f, 0.0f, 0.9f).vanilla(), "Le plafond seul fait une courbe.");
    }

    @Test
    @DisplayName("Les options de confort retirent flashs et bruit visuel, et rien d'autre")
    void comfortOptions() {
        LookProfile full = parse("{ \"dimension\": \"m:d\", \"id\": 2, \"tint\": [1.0, 0.9, 0.7, 0.5], \"desaturation\": 0.3, \"grain\": 0.2,"
                + " \"flicker\": 0.1, \"post\": { \"vignette\": 0.4, \"chromatic\": 0.03, \"bloom\": true },"
                + " \"neons\": { \"block\": \"m:neon\", \"radius\": 8, \"flickering\": 0.2, \"dead\": 0.05 } }");
        assertSame(full, full.comfort(false, false), "Sans option, le profil tel quel.");
        LookProfile calm = full.comfort(true, false);
        assertEquals(0.0f, calm.flicker(), "Sans flashs : la lumiere de l'ecran ne vacille plus.");
        assertEquals(0.0f, calm.neons().flickering(), "Aucun tube ne vacille.");
        assertEquals(0.05f, calm.neons().dead(), EPSILON, "Les tubes morts restent.");
        assertEquals(0.2f, calm.grain(), EPSILON, "Le grain reste.");
        assertEquals(0.03f, calm.post().chromatic(), EPSILON, "L'aberration reste.");
        LookProfile quiet = full.comfort(false, true);
        assertEquals(0.0f, quiet.grain(), "Sans bruit visuel : pas de grain.");
        assertEquals(0.0f, quiet.flicker(), "Ni vacillement de la lumiere.");
        assertEquals(0.0f, quiet.post().chromatic(), "Ni aberration.");
        assertEquals(0.2f, quiet.neons().flickering(), EPSILON, "Les tubes gardent leur calendrier.");
        LookProfile both = full.comfort(true, true);
        assertEquals(0.0f, both.grain(), "Les deux : pas de grain.");
        assertEquals(0.0f, both.neons().flickering(), "Les deux : aucun tube ne vacille.");
        for (LookProfile profile : new LookProfile[] {calm, quiet, both}) {
            assertEquals(full.dimension(), profile.dimension(), "Meme dimension.");
            assertEquals(full.id(), profile.id(), "Meme look.");
            assertEquals(full.tintStrength(), profile.tintStrength(), "Meme teinte.");
            assertEquals(full.desaturation(), profile.desaturation(), "Meme desaturation.");
            assertSame(full.lightmap(), profile.lightmap(), "Meme lumiere.");
            assertSame(full.fog(), profile.fog(), "Meme brouillard.");
            assertEquals(full.post().vignette(), profile.post().vignette(), "Meme vignette.");
            assertTrue(profile.post().bloom(), "Meme bloom.");
            assertEquals(full.neons().radius(), profile.neons().radius(), "Meme lumiere des neons.");
        }
    }

    @Test
    @DisplayName("Un profil invalide est refuse avec sa raison")
    void invalidProfilesAreRefused() {
        assertRefused("{ \"id\": 1 }", "Champ manquant : dimension");
        assertRefused("{ \"dimension\": { }, \"id\": 1 }", "Champ manquant : dimension");
        assertRefused("{ \"dimension\": \"m d\", \"id\": 1 }", "Dimension invalide");
        assertRefused("{ \"dimension\": \"m:d\" }", "Identifiant de look hors bornes");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 0 }", "Identifiant de look hors bornes");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 8 }", "Identifiant de look hors bornes");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [1, 1, 1] }", "tint : 4 valeurs attendues");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [1, 1, 1, 2] }", "tint[3] hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"tint\": [-1, 1, 1, 0] }", "tint hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grain\": 1.5 }", "grain hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"lightmap\": { \"max_brightness\": 2 } }", "max_brightness hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"lightmap\": { \"floor\": 0.5, \"max_brightness\": 0.4 } }", "depasse max_brightness");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"fog\": { \"color\": [2, 0, 0], \"end\": 1 } }", "fog.color hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"fog\": { \"start\": 5, \"end\": 5 } }", "incoherents");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"fog\": { } }", "incoherents");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"fog\": { \"start\": -1, \"end\": 5 } }", "incoherents");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"post\": { \"vignette\": -0.1 } }", "vignette hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"post\": { \"bloom_threshold\": 1.5 } }", "post.bloom_threshold hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"post\": { \"bloom_strength\": 3 } }", "post.bloom_strength hors de");
    }

    private static void assertRefused(String json, String reason) {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> parse(json), reason);
        assertTrue(failure.getMessage().contains(reason), "Raison : " + failure.getMessage() + ".");
    }

    private static LookProfile parse(String json) {
        return LookProfile.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = LookProfileTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
