package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Patch declaratif : cibles, exclusions, bibliotheques et injections")
class ShaderPatchTest {

    private static final String VALID = """
            {
              "stage": "fragment",
              "targets": ["rendertype_*", "particle"],
              "excludes": ["rendertype_text*"],
              "imports": ["gtgs_backrooms:look.glsl"],
              "inject": [
                { "point": "head", "code": "uniform float gtgs_Extra;" },
                { "point": "output", "function": "gtgs_applyLook" }
              ]
            }
            """;

    @Test
    @DisplayName("Un patch valide se lit : cibles par motif, exclusions prioritaires, bibliotheque resolue, injections dans l'ordre")
    void validPatchIsRead() {
        ShaderPatch patch = parse("world_fragment.json", VALID);
        assertEquals("world_fragment.json", patch.id(), "Nom du fichier.");
        assertSame(ShaderKind.FRAGMENT, patch.kind(), "Etage.");
        assertTrue(patch.appliesTo("rendertype_solid"), "Cible par motif.");
        assertTrue(patch.appliesTo("particle"), "Cible exacte.");
        assertFalse(patch.appliesTo("rendertype_text_intensity"), "Exclu par motif.");
        assertFalse(patch.appliesTo("blit_screen"), "Hors cibles.");
        assertFalse(patch.appliesTo("my_rendertype_solid"), "Le motif couvre tout le nom, pas un morceau.");
        assertEquals(List.of(Identifier.of("gtgs_backrooms", "shaders/include/look.glsl")), patch.imports(), "Bibliotheque sous son chemin complet.");
        assertEquals(2, patch.injections().size(), "Deux injections.");
        assertEquals(new ShaderPatch.Injection(InjectionPoint.HEAD, "uniform float gtgs_Extra;"), patch.injections().get(0), "Head, code tel quel.");
        assertEquals(new ShaderPatch.Injection(InjectionPoint.OUTPUT, "gtgs_applyLook"), patch.injections().get(1), "Sortie, nom de fonction.");
    }

    @Test
    @DisplayName("Sans exclusions ni bibliotheques, le patch se lit aussi")
    void optionalFieldsMayBeAbsent() {
        ShaderPatch patch = parse("p.json", """
                { "stage": "vertex", "targets": ["*"], "inject": [ { "point": "main_before", "code": "x();" } ] }
                """);
        assertSame(ShaderKind.VERTEX, patch.kind(), "Etage vertex.");
        assertTrue(patch.excludes().isEmpty() && patch.imports().isEmpty(), "Rien d'optionnel.");
        assertTrue(patch.appliesTo("anything"), "Motif universel.");
    }

    @Test
    @DisplayName("Un patch invalide est refuse avec sa raison")
    void invalidPatchesAreRefused() {
        assertRefused(VALID.replace("\"fragment\"", "\"geometry\""), "Etage inconnu");
        assertRefused(VALID.replace("\"stage\": \"fragment\",", ""), "Champ manquant : stage");
        assertRefused(VALID.replace("\"stage\": \"fragment\",", "\"stage\": {},"), "Champ manquant : stage");
        assertRefused(VALID.replace("[\"rendertype_*\", \"particle\"]", "[]"), "Aucun shader cible");
        assertRefused(VALID.replace("\"particle\"", "\"\""), "Motif vide");
        assertRefused(VALID.replace("gtgs_backrooms:look.glsl", "gtgs backrooms:look.glsl"), "Bibliotheque invalide");
        assertRefused(VALID.replace("\"head\"", "\"tail\""), "Point d'injection inconnu");
        assertRefused(VALID.replace("\"gtgs_applyLook\"", "\"gtgs_applyLook()\""), "Nom de fonction invalide");
        assertRefused(VALID.replace("\"stage\": \"fragment\"", "\"stage\": \"vertex\""), "La sortie ne se patche que dans un fragment shader");
        assertRefused(VALID.replace("\"uniform float gtgs_Extra;\"", "\" \""), "Code vide");
        assertRefused(VALID.replace("\"code\": \"uniform float gtgs_Extra;\"", "\"function\": \"f\""), "Champ manquant : code");
        assertRefused(VALID.replaceAll("\"inject\": \\[[^\\]]*\\]", "\"inject\": []"), "Aucune injection");
        assertRefused("{ \"stage\": \"fragment\", \"targets\": [\"*\"] }", "Aucune injection");
    }

    @Test
    @DisplayName("Les noms d'etage et de point se resolvent sans tenir compte de la casse, et un nom inconnu rend null")
    void keysAreResolved() {
        assertSame(ShaderKind.FRAGMENT, ShaderKind.byKey("Fragment"), "Etage.");
        assertNull(ShaderKind.byKey("compute"), "Etage inconnu.");
        assertEquals("vertex", ShaderKind.VERTEX.key(), "Cle de l'etage.");
        assertSame(InjectionPoint.MAIN_AFTER, InjectionPoint.byKey("MAIN_AFTER"), "Point.");
        assertNull(InjectionPoint.byKey("end"), "Point inconnu.");
        assertEquals("output", InjectionPoint.OUTPUT.key(), "Cle du point.");
    }

    private static void assertRefused(String json, String reason) {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> parse("p.json", json), reason);
        assertTrue(failure.getMessage().contains(reason), "Raison : " + failure.getMessage() + ".");
    }

    private static ShaderPatch parse(String id, String json) {
        return ShaderPatch.fromJson(id, JsonParser.parseString(json).getAsJsonObject());
    }
}
