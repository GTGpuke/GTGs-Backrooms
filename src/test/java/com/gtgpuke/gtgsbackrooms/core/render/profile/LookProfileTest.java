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
        assertEquals(0.0f, profile.tintStrength(), EPSILON, "Pas de teinte d'ensemble : le jaune vient des murs.");
        assertEquals(0.0f, profile.desaturation(), EPSILON, "Pas de desaturation.");
        assertEquals(1.0f, profile.neons().red(), EPSILON, "La lumiere des neons est blanche.");
        assertEquals(1.0f, profile.neons().green(), EPSILON, "La lumiere des neons est blanche.");
        assertEquals(1.0f, profile.neons().blue(), EPSILON, "La lumiere des neons est blanche.");
        assertEquals(new Vec3d(1.03f, 1.0f, 0.72f), profile.bakedLight().bounceColor(), "La lumiere qui a tourne dans une piece se teinte.");
        assertEquals(0.96f, profile.bakedLight().lightLimit(), EPSILON, "Sous une dalle, une surface a la couleur de sa texture.");
        assertEquals(0.02f, profile.grain(), EPSILON, "Grain.");
        assertEquals(0.0f, profile.flicker(), EPSILON, "Vacillement : mis de cote (decision de l'auteur du 27 octobre 2026).");
        assertEquals(new LookProfile.Lightmap(0.08f, 0.2f, 0.92f), profile.lightmap(), "Lightmap.");
        assertFalse(profile.lightmap().vanilla(), "Courbe active.");
        assertFalse(profile.fog().enabled(), "Pas de brouillard dans le Level 0 (demande de l'auteur) : seul celui de vanilla, au bord de la distance de rendu.");
        assertEquals(new LookProfile.Post(0.25f, 0.0f, true, 0.8f, 1.5f), profile.post(), "Post.");
        assertTrue(profile.bakedLight().any(), "Le Level 0 cuit sa lumiere.");
        assertFalse(profile.post().none(), "Une vignette a dessiner.");
        assertTrue(profile.neons().any(), "Des neons.");
        assertEquals(Identifier.of("gtgs_backrooms", "level_zero_fluorescent_light"), profile.neons().block(), "Le neon du Level 0.");
        assertEquals(8.0f, profile.neons().radius(), EPSILON, "Portee.");
        assertEquals(0.6f, profile.neons().strength(), EPSILON, "Force.");
        assertEquals(0.0f, profile.neons().flickering(), EPSILON, "Part qui vacille : mise de cote.");
        assertEquals(0.0f, profile.neons().dead(), EPSILON, "Part morte : mise de cote.");
        assertEquals(0.0f, profile.neons().blackout(), EPSILON, "Aucun secteur en panne : mis de cote (demande de l'auteur du 1er novembre 2026).");
    }

    @Test
    @DisplayName("La lumiere tracee remplace ce que le profil lui demande, garde le reste, et reste bornee")
    void tracedLight() {
        String neons = "\"neons\": { \"block\": \"m:tube\" }";
        String head = "{ \"dimension\": \"m:d\", \"id\": 1, " + neons + ", \"baked_light\": ";
        LookProfile.BakedLight plain = parse(head + "{ \"shadow_size\": 0.5, \"sheen\": 0.75 } }").bakedLight();
        assertEquals(0.5f, plain.tracedLight().shadowSize(), "Sans section, la lumiere tracee garde les valeurs de la lumiere cuite.");
        assertEquals(1.0f, plain.traced().detail(), "Et ses textures.");
        assertEquals(1.0f, plain.traced().exposure(), "Et son exposition.");
        LookProfile.BakedLight traced = parse(head + "{ \"shadow_size\": 0.5, \"sheen\": 0.75, \"traced\": {"
                + " \"shadow_size\": 3, \"relief\": 0.25, \"sheen\": 0.5, \"bounce\": 1.5, \"toe\": 0.125, \"detail\": 0.25, \"exposure\": 0.5,"
                + " \"direct\": 0.75, \"shoulder\": 0.625 } } }").bakedLight();
        assertEquals(new LookProfile.Traced(3.0f, 0.25f, 0.5f, 1.5f, 0.125f, 0.25f, 0.5f, 0.75f, 0.625f), traced.traced(), "Tout lu.");
        LookProfile.BakedLight applied = traced.tracedLight();
        assertEquals(3.0f, applied.shadowSize(), "Ombres.");
        assertEquals(0.25f, applied.relief(), "Relief.");
        assertEquals(0.5f, applied.sheen(), "Reflet.");
        assertEquals(1.5f, applied.bounce(), "Rebond.");
        assertEquals(0.125f, applied.toe(), "Pied de courbe.");
        assertEquals(0.625f, applied.shoulder(), "Epaule.");
        assertEquals(0.25f, applied.detail(), "Textures du palier.");
        assertEquals(0.5f, applied.exposure(), "Exposition du palier.");
        assertEquals(0.75f, applied.direct(), "Lumiere directe du palier.");
        assertEquals(traced.traced(), applied.traced(), "La section reste.");
        LookProfile.BakedLight empty = parse(head + "{ \"shadow_size\": 0.5, \"traced\": {} } }").bakedLight();
        assertEquals(0.5f, empty.traced().shadowSize(), "Une section vide ne change rien.");
        String[] outOfBounds = {"\"shadow_size\": 9", "\"relief\": 5", "\"sheen\": 3", "\"bounce\": 3", "\"toe\": 2", "\"detail\": 2", "\"exposure\": 5", "\"direct\": 5",
            "\"shoulder\": 2"};
        for (String key : outOfBounds) {
            assertThrows(JsonParseException.class, () -> parse(head + "{ \"traced\": { " + key + " } } }"), key + ".");
        }
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
    @DisplayName("La section d'etalonnage se lit avec ses defauts neutres et refuse ses valeurs impossibles")
    void gradeSection() {
        assertSame(LookProfile.Grade.NEUTRAL, parse("{ \"dimension\": \"m:d\", \"id\": 1 }").grade(), "Sans section, neutre.");
        assertEquals(LookProfile.Grade.NEUTRAL, parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": {} }").grade(), "Section vide : neutre.");
        LookProfile.Grade grade = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"slope\": [1.5, 1, 0.5],"
                + " \"offset\": [-0.25, 0, 0.125], \"power\": [2, 1, 0.5], \"saturation\": 0.75 } }").grade();
        assertEquals(new Vec3d(1.5, 1.0, 0.5), grade.slope(), "Pente.");
        assertEquals(new Vec3d(-0.25, 0.0, 0.125), grade.offset(), "Decalage.");
        assertEquals(new Vec3d(2.0, 1.0, 0.5), grade.power(), "Puissance.");
        assertEquals(0.75f, grade.saturation(), "Saturation.");
        assertEquals(LookProfile.Grade.NEUTRAL, LookProfile.VANILLA.grade(), "Le look vanilla est neutre.");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"slope\": [1, 1] } }", "slope : 3 valeurs attendues");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"slope\": [1, 5, 1] } }", "grade.slope hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"slope\": [-0.1, 1, 1] } }", "grade.slope hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"offset\": [0, 0, 1.5] } }", "grade.offset hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"offset\": [-1.5, 0, 0] } }", "grade.offset hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"power\": [1, 0, 1] } }", "grade.power hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"power\": [1, 1, 5] } }", "grade.power hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"saturation\": 2.5 } }", "grade.saturation hors de");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"grade\": { \"saturation\": -1 } }", "grade.saturation hors de");
        LookProfile full = parse("{ \"dimension\": \"m:d\", \"id\": 2, \"grain\": 0.2, \"grade\": { \"saturation\": 0.5 } }");
        assertSame(full.grade(), full.comfort(true, true).grade(), "Les options de confort gardent l'etalonnage.");
    }

    @Test
    @DisplayName("La section de lumiere cuite se lit avec ses defauts, demande des neons et refuse ses valeurs impossibles")
    void bakedLightSection() {
        assertFalse(parse("{ \"dimension\": \"m:d\", \"id\": 1 }").bakedLight().any(), "Sans section, pas de lumiere cuite.");
        assertSame(LookProfile.BakedLight.NONE, LookProfile.VANILLA.bakedLight(), "Vanilla non plus.");
        String neons = "\"neons\": { \"block\": \"m:tube\" }";
        LookProfile.BakedLight minimal = parse("{ \"dimension\": \"m:d\", \"id\": 1, " + neons + ", \"baked_light\": {} }").bakedLight();
        assertTrue(minimal.any(), "Une section vide suffit.");
        assertEquals(8.0f, minimal.emission(), "Emission par defaut.");
        assertEquals(Vec3d.ZERO, minimal.ambient(), "Pas de fond par defaut.");
        assertEquals(12.0f, minimal.reach(), "Portee par defaut.");
        assertEquals(1.0f, minimal.size(), "Taille par defaut.");
        assertEquals(90.0f, minimal.angle(), "Tout le demi-espace devant la dalle par defaut.");
        assertEquals(1.0f, minimal.shoulder(), "Pas d'epaule par defaut.");
        assertEquals(new Vec3d(1.0, 1.0, 1.0), minimal.bounceColor(), "Les rebonds n'ajoutent pas de couleur par defaut.");
        assertEquals(0.0f, minimal.lightLimit(), "La lumiere d'une surface n'a pas de limite par defaut.");
        LookProfile.BakedLight full = parse("{ \"dimension\": \"m:d\", \"id\": 1, " + neons + ", \"baked_light\": { \"emission\": 3,"
                + " \"ambient\": [0.5, 0.25, 0.125], \"reach\": 10, \"size\": 2, \"angle\": 70, \"occlusion\": 0.75, \"relief\": 1.5,"
                + " \"haze\": 0.25, \"sheen\": 1.25, \"shoulder\": 0.5, \"shadow_size\": 0.5, \"toe\": 0.125, \"pixel_light\": 0.25, \"dark_pixels\": 0.75,"
                + " \"bounce\": 0.5, \"bounce_color\": [1.5, 1.0, 0.5], \"light_limit\": 1.25, \"detail\": 0.5, \"exposure\": 0.75, \"direct\": 1.5 } }")
                .bakedLight();
        assertEquals(new LookProfile.BakedLight(3.0f, new Vec3d(0.5, 0.25, 0.125), 10.0f, 2.0f, 70.0f, 0.5f, 0.75f, 1.5f, 0.25f, 1.25f, 0.5f, 0.125f, 0.25f, 0.75f, 0.5f,
                new Vec3d(1.5, 1.0, 0.5), 1.25f, 0.5f, 0.75f, 1.5f, new LookProfile.Traced(0.5f, 1.5f, 1.25f, 0.5f, 0.125f, 0.5f, 0.75f, 1.5f, 0.5f)), full,
                "Tout lu ; sans section tracee, la lumiere tracee garde textures, exposition et direct des autres paliers.");
        assertEquals(1.0f, minimal.detail(), "Textures entieres par defaut.");
        assertEquals(1.0f, minimal.exposure(), "Exposition neutre par defaut.");
        assertEquals(1.0f, minimal.direct(), "Lumiere directe entiere par defaut.");
        assertEquals(1.0f, minimal.shadowSize(), "Rayons des ombres par defaut.");
        assertEquals(0.0f, minimal.toe(), "Pas de pied de courbe par defaut.");
        assertEquals(0.0f, minimal.pixelLight(), "Degrade lisse par defaut.");
        assertEquals(0.0f, minimal.darkPixels(), "Pas de grain de pixels par defaut.");
        assertEquals(1.0f, minimal.bounce(), "Rebonds entiers par defaut.");
        assertRefused("{ \"dimension\": \"m:d\", \"id\": 1, \"baked_light\": {} }", "demande une section neons");
        String prefix = "{ \"dimension\": \"m:d\", \"id\": 1, " + neons + ", \"baked_light\": { ";
        assertRefused(prefix + "\"emission\": 0 } }", "baked_light.emission hors de");
        assertRefused(prefix + "\"ambient\": [0, 2, 0] } }", "baked_light.ambient hors de");
        assertRefused(prefix + "\"reach\": 16 } }", "baked_light.reach hors de");
        assertRefused(prefix + "\"size\": 9 } }", "baked_light.size hors de");
        assertRefused(prefix + "\"shoulder\": 0 } }", "baked_light.shoulder hors de");
        assertRefused(prefix + "\"occlusion\": 2 } }", "baked_light.occlusion hors de");
        assertRefused(prefix + "\"relief\": 5 } }", "baked_light.relief hors de");
        assertRefused(prefix + "\"haze\": 2 } }", "baked_light.haze hors de");
        assertRefused(prefix + "\"sheen\": 3 } }", "baked_light.sheen hors de");
        assertRefused(prefix + "\"shadow_size\": 9 } }", "baked_light.shadow_size hors de");
        assertRefused(prefix + "\"toe\": 2 } }", "baked_light.toe hors de");
        assertRefused(prefix + "\"pixel_light\": 2 } }", "baked_light.pixel_light hors de");
        assertRefused(prefix + "\"dark_pixels\": 3 } }", "baked_light.dark_pixels hors de");
        assertRefused(prefix + "\"bounce\": 3 } }", "baked_light.bounce hors de");
        assertRefused(prefix + "\"bounce_color\": [1, 3, 1] } }", "baked_light.bounce_color hors de");
        assertRefused(prefix + "\"light_limit\": 5 } }", "baked_light.light_limit hors de");
        assertRefused(prefix + "\"detail\": 2 } }", "baked_light.detail hors de");
        assertRefused(prefix + "\"exposure\": 0 } }", "baked_light.exposure hors de");
        assertRefused(prefix + "\"direct\": 5 } }", "baked_light.direct hors de");
        assertEquals(0.0f, minimal.sheen(), "Pas de reflet par defaut.");
        assertEquals(0.0f, minimal.relief(), "Pas de relief par defaut.");
        assertEquals(0.0f, minimal.haze(), "Pas de brume par defaut.");
        LookProfile comfort = parse("{ \"dimension\": \"m:d\", \"id\": 1, \"grain\": 0.2, " + neons + ", \"baked_light\": { \"emission\": 3 } }");
        assertSame(comfort.bakedLight(), comfort.comfort(true, true).bakedLight(), "Les options de confort gardent la lumiere cuite.");
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
