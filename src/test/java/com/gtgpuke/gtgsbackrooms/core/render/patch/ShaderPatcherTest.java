package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Patch des shaders par enveloppe de main")
class ShaderPatcherTest {

    private static final String INVENTORY = "shader_inventory_1_21_1.json";
    private static final String LOOK_INCLUDE = "assets/gtgs_backrooms/shaders/include/look.glsl";
    private static final List<ShaderPatch.Injection> OUTPUT = List.of(new ShaderPatch.Injection(InjectionPoint.OUTPUT, "gtgs_applyLook"));
    private static final Pattern MOJ_IMPORT = Pattern.compile("#moj_import\\s*<([^>]+)>");

    private static final String FRAGMENT = """
            #version 150

            uniform sampler2D Sampler0;

            in vec2 texCoord0;

            out vec4 fragColor;

            void main() {
                vec4 color = texture(Sampler0, texCoord0);
                if (color.a < 0.1) {
                    discard;
                }
                fragColor = color;
            }
            """;

    @Test
    @DisplayName("L'inventaire versionne des shaders de 1.21.1 est celui que l'analyse produit sur le jar du jeu")
    void inventoryMatchesTheJar() throws IOException {
        Map<String, String> shaders = VanillaShaders.core();
        Map<String, String> includes = VanillaShaders.includes();
        assertTrue(shaders.size() > 100, "Les shaders de base de vanilla sont la : " + shaders.size() + ".");
        JsonArray computed = new JsonArray();
        for (Map.Entry<String, String> entry : shaders.entrySet()) {
            String name = entry.getKey();
            ShaderKind kind = name.endsWith(".fsh") ? ShaderKind.FRAGMENT : ShaderKind.VERTEX;
            ShaderPatcher.Analysis analysis = ShaderPatcher.analyze(VanillaShaders.compiled(entry.getValue(), includes));
            JsonObject row = new JsonObject();
            row.addProperty("name", name);
            row.addProperty("stage", kind.key());
            row.addProperty("version", analysis.version());
            row.addProperty("mains", analysis.mains());
            row.add("outputs", array(kind == ShaderKind.FRAGMENT ? analysis.outputs() : List.of()));
            row.addProperty("discard", analysis.discard());
            row.add("directives", array(new ArrayList<>(new LinkedHashSet<>(analysis.directives())).stream().sorted().toList()));
            row.add("macros", array(analysis.macros()));
            row.add("imports", array(imports(entry.getValue())));
            String refusal = ShaderPatcher.refusal(kind, analysis);
            row.addProperty("verdict", refusal == null ? "patchable" : "refuse:" + refusal);
            computed.add(row);
        }
        JsonObject expected = readInventory();
        assertEquals("1.21.1", expected.get("minecraft").getAsString(), "L'inventaire est celui de la version du jeu.");
        JsonArray expectedRows = expected.getAsJsonArray("shaders");
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(computed);
        assertEquals(expectedRows.size(), computed.size(), "Autant de shaders qu'a l'inventaire. Inventaire calcule :\n" + pretty + ".");
        for (int i = 0; i < computed.size(); i++) {
            assertEquals(expectedRows.get(i), computed.get(i), "Ligne " + i + " de l'inventaire. Inventaire calcule :\n" + pretty + ".");
        }
    }

    @Test
    @DisplayName("Chaque fragment shader vanilla recoit le look par enveloppe de main, une fois, avec la bibliotheque en tete")
    void vanillaFragmentShadersArePatched() throws IOException {
        Map<String, String> includes = VanillaShaders.includes();
        String look = lookInclude();
        int patched = 0;
        for (Map.Entry<String, String> entry : VanillaShaders.core().entrySet()) {
            String name = entry.getKey();
            String compiled = VanillaShaders.compiled(entry.getValue(), includes);
            ShaderKind kind = name.endsWith(".fsh") ? ShaderKind.FRAGMENT : ShaderKind.VERTEX;
            ShaderPatcher.Result result = ShaderPatcher.apply(kind, compiled, List.of(look), kind == ShaderKind.FRAGMENT ? OUTPUT : List.of());
            assertNull(result.refusal(), name + " : patchable.");
            String source = result.source();
            assertTrue(source.startsWith("#version 150\n"), name + " : la directive de version reste en tete.");
            assertEquals(1, count(source, "void " + ShaderPatcher.VANILLA_MAIN + "() {"), name + " : la main vanilla est renommee une fois.");
            assertEquals(1, count(source, "\nvoid main() {\n"), name + " : une seule nouvelle main.");
            assertEquals(1, count(source, "uniform int gtgs_LookId;"), name + " : la bibliotheque est inseree une fois.");
            assertTrue(source.indexOf("uniform int gtgs_LookId;") < source.indexOf("void " + ShaderPatcher.VANILLA_MAIN), name
                    + " : la bibliotheque precede la main vanilla.");
            if (kind == ShaderKind.FRAGMENT) {
                assertTrue(source.endsWith("    " + ShaderPatcher.VANILLA_MAIN + "();\n    fragColor = gtgs_applyLook(fragColor);\n}\n"), name
                        + " : la sortie passe par le look apres la main vanilla.");
                patched++;
            } else {
                assertTrue(source.endsWith("    " + ShaderPatcher.VANILLA_MAIN + "();\n}\n"), name + " : la nouvelle main appelle la vanilla.");
            }
            assertSame(source, ShaderPatcher.apply(kind, source, List.of(look), OUTPUT).source(), name + " : deja patche, rien de plus.");
            assertEquals(compiled.contains("discard"), source.contains("discard"), name + " : le discard vanilla est garde.");
        }
        assertEquals(55, patched, "Les cinquante-cinq fragment shaders de base.");
    }

    @Test
    @DisplayName("Les points head, main_before, main_after et output s'inserent dans cet ordre")
    void injectionPointsAreOrdered() {
        List<ShaderPatch.Injection> injections = List.of(
                new ShaderPatch.Injection(InjectionPoint.MAIN_AFTER, "after();"),
                new ShaderPatch.Injection(InjectionPoint.OUTPUT, "look"),
                new ShaderPatch.Injection(InjectionPoint.HEAD, "uniform float gtgs_Head;"),
                new ShaderPatch.Injection(InjectionPoint.MAIN_BEFORE, "before();\n"));
        ShaderPatcher.Result result = ShaderPatcher.apply(ShaderKind.FRAGMENT, FRAGMENT, List.of("// lib\n"), injections);
        assertNull(result.refusal(), "Patchable.");
        String expectedHead = "#version 150\n// lib\nuniform float gtgs_Head;\n\nuniform sampler2D Sampler0;";
        assertTrue(result.source().startsWith(expectedHead), "Bibliotheque puis head juste apres la version : " + result.source() + ".");
        String expectedTail = "void main() {\n    before();\n    gtgs_vanilla_main();\n    after();\n    fragColor = look(fragColor);\n}\n";
        assertTrue(result.source().endsWith(expectedTail), "Nouvelle main dans l'ordre : " + result.source() + ".");
        assertTrue(result.source().contains("void gtgs_vanilla_main() {\n    vec4 color = texture(Sampler0, texCoord0);"),
                "Corps vanilla inchange derriere son nouveau nom.");
    }

    @Test
    @DisplayName("Une sortie avec qualificatifs et un autre nom est reconnue, et les commentaires n'y changent rien")
    void qualifiedOutputAndCommentsAreHandled() {
        String source = """
                #version 150
                /* void main() { dans un commentaire */
                // out vec4 fausse;
                layout(location = 0) out vec4 color; // la vraie
                void main() { color = vec4(1.0); }
                """;
        ShaderPatcher.Analysis analysis = ShaderPatcher.analyze(source);
        assertEquals(List.of("color"), analysis.outputs(), "La seule sortie declaree.");
        assertEquals(1, analysis.mains(), "La main des commentaires ne compte pas.");
        assertFalse(analysis.discard(), "Aucun discard.");
        ShaderPatcher.Result result = ShaderPatcher.apply(ShaderKind.FRAGMENT, source, List.of(), OUTPUT);
        assertNull(result.refusal(), "Patchable.");
        assertTrue(result.source().contains("void gtgs_vanilla_main() { color = vec4(1.0); }"), "Main renommee sur la vraie.");
        assertTrue(result.source().contains("/* void main() { dans un commentaire */"), "Commentaire intact.");
        assertTrue(result.source().endsWith("    color = gtgs_applyLook(color);\n}\n"), "Sortie reconnue sous son nom.");
    }

    @Test
    @DisplayName("Les sources hors signature sont refusees, chacune pour sa raison, et rendues telles quelles")
    void unexpectedSourcesAreRefused() {
        assertRefused(ShaderPatcher.REFUSAL_VERSION, FRAGMENT.replace("#version 150", ""), "Sans version.");
        assertRefused(ShaderPatcher.REFUSAL_VERSION, FRAGMENT.replace("#version 150", "#version 330"), "Autre version.");
        assertRefused(ShaderPatcher.REFUSAL_VERSION, FRAGMENT.replace("#version 150", "#version 150\n#version 150"), "Version declaree deux fois.");
        assertRefused(ShaderPatcher.REFUSAL_DIRECTIVES, FRAGMENT.replace("out vec4 fragColor;", "#ifdef ALPHA\nout vec4 fragColor;\n#endif"),
                "Directive conditionnelle.");
        assertRefused(ShaderPatcher.REFUSAL_DIRECTIVES, FRAGMENT.replace("out vec4 fragColor;", "#define OUT fragColor\nout vec4 OUT;"),
                "Macro dans la declaration de la sortie.");
        assertRefused(ShaderPatcher.REFUSAL_DIRECTIVES, FRAGMENT.replace("void main() {", "#define BODY\nvoid main() { BODY"),
                "Macro sur la ligne de la signature de main.");
        assertRefused(ShaderPatcher.REFUSAL_DIRECTIVES, FRAGMENT.replace("void main() {", "#define BODY\n#define TAIL\nvoid main() { BODY"),
                "Premiere macro utilisee : la seconde n'est pas examinee.");
        assertRefused(ShaderPatcher.REFUSAL_MAIN, FRAGMENT.replace("void main() {", "#define MAIN main\nvoid MAIN() {"),
                "Main cachee derriere une macro : aucune main.");
        assertRefused(ShaderPatcher.REFUSAL_DIRECTIVES, FRAGMENT.replace("#version 150", "#version 150\n#pragma optimize(on)"), "Directive inconnue.");
        assertRefused(ShaderPatcher.REFUSAL_MAIN, FRAGMENT.replace("void main() {", "void helper() {"), "Sans main.");
        assertRefused(ShaderPatcher.REFUSAL_MAIN, FRAGMENT + "\nvoid main() {}\n", "Deux main.");
        assertRefused(ShaderPatcher.REFUSAL_OUTPUT, FRAGMENT.replace("out vec4 fragColor;", "out vec3 fragColor;"), "Sortie d'un autre type.");
        assertRefused(ShaderPatcher.REFUSAL_OUTPUT, FRAGMENT.replace("out vec4 fragColor;", "out vec4 fragColor;\nout vec4 normal;"),
                "Deux sorties.");
    }

    @Test
    @DisplayName("Une macro definie ailleurs, une directive #line et un vertex shader sans sortie restent patchables")
    void harmlessDirectivesArePatchable() {
        String vertex = """
                #version 150
                #line 0 1
                #define LIGHT_POWER 0.6
                in vec3 Position;
                uniform mat4 ModelViewMat;
                out float brightness;
                void main() {
                    brightness = LIGHT_POWER;
                    gl_Position = ModelViewMat * vec4(Position, 1.0);
                }
                """;
        ShaderPatcher.Analysis analysis = ShaderPatcher.analyze(vertex);
        assertEquals(List.of("define"), analysis.directives(), "Seule la macro est relevee, pas #line.");
        assertEquals(List.of("LIGHT_POWER"), analysis.macros(), "La macro par son nom.");
        assertFalse(analysis.macroUsed(), "Pas dans une signature.");
        assertNull(ShaderPatcher.refusal(ShaderKind.VERTEX, analysis), "Patchable.");
        assertEquals(ShaderPatcher.REFUSAL_OUTPUT, ShaderPatcher.refusal(ShaderKind.FRAGMENT, analysis), "Un fragment exigerait une sortie.");
        ShaderPatcher.Result result = ShaderPatcher.apply(ShaderKind.VERTEX, vertex, List.of(),
                List.of(new ShaderPatch.Injection(InjectionPoint.HEAD, "out vec3 gtgs_World;")));
        assertNull(result.refusal(), "Patchable.");
        assertTrue(result.source().startsWith("#version 150\nout vec3 gtgs_World;\n#line 0 1\n"), "Head apres la version, avant #line.");
    }

    @Test
    @DisplayName("Le jeton de position devient Position + ChunkOffset dans un shader de terrain, Position ailleurs")
    void positionTokenFollowsTheShader() throws IOException {
        Map<String, String> includes = VanillaShaders.includes();
        Map<String, String> core = VanillaShaders.core();
        List<ShaderPatch.Injection> injections = List.of(new ShaderPatch.Injection(InjectionPoint.HEAD, "out vec3 gtgs_ViewPos;"),
                new ShaderPatch.Injection(InjectionPoint.MAIN_AFTER, "gtgs_ViewPos = (ModelViewMat * vec4(${position}, 1.0)).xyz;"));
        String terrain = ShaderPatcher.apply(ShaderKind.VERTEX, VanillaShaders.compiled(core.get("rendertype_solid.vsh"), includes), List.of(), injections)
                .source();
        assertTrue(terrain.contains("    gtgs_ViewPos = (ModelViewMat * vec4(Position + ChunkOffset, 1.0)).xyz;\n"), "Terrain : decalage de section.");
        String entity = ShaderPatcher.apply(ShaderKind.VERTEX, VanillaShaders.compiled(core.get("rendertype_entity_solid.vsh"), includes), List.of(),
                injections).source();
        assertTrue(entity.contains("    gtgs_ViewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;\n"), "Entite : position seule.");
        assertTrue(entity.contains("#version 150\nout vec3 gtgs_ViewPos;\n"), "Head sans jeton, inchange.");
    }

    @Test
    @DisplayName("Les jetons de texture, de normale et de terrain suivent ce que le shader declare")
    void attributeTokensFollowTheShader() throws IOException {
        Map<String, String> includes = VanillaShaders.includes();
        Map<String, String> core = VanillaShaders.core();
        List<ShaderPatch.Injection> injections = List.of(new ShaderPatch.Injection(InjectionPoint.MAIN_AFTER,
                "gtgs_Uv = ${uv}; gtgs_Normal = ${normal}; gtgs_Terrain = ${terrain};"));
        String terrain = ShaderPatcher.apply(ShaderKind.VERTEX, VanillaShaders.compiled(core.get("rendertype_solid.vsh"), includes), List.of(), injections)
                .source();
        assertTrue(terrain.contains("    gtgs_Uv = UV0; gtgs_Normal = Normal; gtgs_Terrain = 1.0;\n"), "Terrain : texture, normale, terrain.");
        String entity = ShaderPatcher.apply(ShaderKind.VERTEX, VanillaShaders.compiled(core.get("rendertype_entity_solid.vsh"), includes), List.of(),
                injections).source();
        assertTrue(entity.contains("    gtgs_Uv = UV0; gtgs_Normal = Normal; gtgs_Terrain = 0.0;\n"), "Entite : pas du terrain.");
        String lightning = ShaderPatcher.apply(ShaderKind.VERTEX, VanillaShaders.compiled(core.get("rendertype_lightning.vsh"), includes), List.of(),
                injections).source();
        assertTrue(lightning.contains("    gtgs_Uv = vec2(-1.0); gtgs_Normal = vec3(0.0); gtgs_Terrain = 0.0;\n"), "Sans texture ni normale : neutre.");
    }

    @Test
    @DisplayName("Les commentaires sont blanchis sans deplacer un caractere")
    void commentsAreBlankedInPlace() {
        String source = "a /* b\nc */ d // e\nf /* g";
        String blank = ShaderPatcher.blankComments(source);
        assertEquals(source.length(), blank.length(), "Meme longueur.");
        assertEquals("a     \n     d     \nf     ", blank, "Commentaires blancs, sauts de ligne gardes, commentaire ouvert blanchi jusqu'au bout.");
        assertEquals("x     ", ShaderPatcher.blankComments("x // y"), "Commentaire de ligne jusqu'a la fin du texte.");
        assertEquals("x     ", ShaderPatcher.blankComments("x /*y*"), "Commentaire ouvert fini par une etoile.");
        assertEquals("x/", ShaderPatcher.blankComments("x/"), "Une barre seule n'est pas un commentaire.");
        assertEquals("x           y", ShaderPatcher.blankComments("x /* a*b */ y"), "Une etoile sans barre ne ferme pas le commentaire.");
    }

    @Test
    @DisplayName("Une version sans saut de ligne et une sortie en derniere ligne sont lues jusqu'au bout du texte")
    void edgesOfTheTextAreHandled() {
        String lastLineOutput = "#version 150\nvoid main() { c = vec4(1.0); }\nout vec4 c;";
        ShaderPatcher.Analysis analysis = ShaderPatcher.analyze(lastLineOutput);
        assertEquals(List.of("c"), analysis.outputs(), "Sortie en derniere ligne, sans saut de ligne final.");
        assertNull(ShaderPatcher.refusal(ShaderKind.FRAGMENT, analysis), "Patchable.");
        String versionLast = "out vec4 c;\nvoid main() { c = vec4(1.0); }\n#version 150";
        assertRefused(ShaderPatcher.REFUSAL_VERSION, versionLast, "Version apres main, sans saut de ligne : pas un shader valide.");
        String versionAfterMain = "out vec4 c;\nvoid main() { c = vec4(1.0); }\n#version 150\n";
        assertRefused(ShaderPatcher.REFUSAL_VERSION, versionAfterMain, "Version apres main : pas un shader valide.");
    }

    private static void assertRefused(String reason, String source, String message) {
        ShaderPatcher.Result result = ShaderPatcher.apply(ShaderKind.FRAGMENT, source, List.of(), OUTPUT);
        assertEquals(reason, result.refusal(), message);
        assertSame(source, result.source(), message + " La source repart telle quelle.");
    }

    private static JsonObject readInventory() throws IOException {
        try (InputStream in = ShaderPatcherTest.class.getClassLoader().getResourceAsStream(INVENTORY)) {
            assertNotNull(in, "L'inventaire est versionne avec les tests.");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static String lookInclude() throws IOException {
        try (InputStream in = ShaderPatcherTest.class.getClassLoader().getResourceAsStream(LOOK_INCLUDE)) {
            assertNotNull(in, "La bibliotheque du look est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> imports(String raw) {
        List<String> names = new ArrayList<>();
        Matcher matcher = MOJ_IMPORT.matcher(raw);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static JsonElement array(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
