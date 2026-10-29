package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Registre des patchs de shaders")
class ShaderPatchRegistryTest {

    private static final String WORLD_FRAGMENT = "assets/gtgs_backrooms/shader_patches/world_fragment.json";
    private static final Identifier LOOK = Identifier.of("gtgs_backrooms", "shaders/include/look.glsl");
    private static final Identifier SCHEDULE = Identifier.of("gtgs_backrooms", "shaders/include/neon_schedule.glsl");

    @Test
    @DisplayName("Le patch du mod s'applique aux shaders du monde et a eux seuls, bibliotheque du mod comprise")
    void modPatchAppliesToWorldShaders() throws IOException {
        ShaderPatchRegistry registry = ShaderPatchRegistry.of(Map.of("world_fragment.json", resource(WORLD_FRAGMENT)),
                include -> include.equals(LOOK) ? lookInclude() : include.equals(SCHEDULE) ? scheduleInclude() : null);
        assertEquals(1, registry.size(), "Un patch.");
        Map<String, String> includes = VanillaShaders.includes();
        Map<String, String> core = VanillaShaders.core();
        String solid = VanillaShaders.compiled(core.get("rendertype_solid.fsh"), includes);
        String patched = registry.patch(ShaderKind.FRAGMENT, "rendertype_solid", solid);
        assertTrue(patched.contains("vec4 gtgs_applyLook(vec4 c)"), "Bibliotheque du mod inseree.");
        assertTrue(patched.indexOf("float gtgs_neonIntensity(") < patched.indexOf("vec4 gtgs_applyLook(vec4 c)"),
                "Le jumeau du calendrier des neons est insere avant le look qui l'appelle.");
        assertTrue(patched.endsWith("    fragColor = gtgs_applyLook(fragColor);\n}\n"), "Sortie passee par le look.");
        assertSame(patched, registry.patch(ShaderKind.FRAGMENT, "rendertype_solid", patched), "Deja patche.");
        String particle = VanillaShaders.compiled(core.get("particle.fsh"), includes);
        assertTrue(registry.patch(ShaderKind.FRAGMENT, "particle", particle).contains(ShaderPatcher.VANILLA_MAIN), "Les particules aussi.");
        for (String name : new String[] {"rendertype_text", "rendertype_gui", "rendertype_outline", "rendertype_clouds", "blit_screen", "position"}) {
            String source = VanillaShaders.compiled(core.get(name + ".fsh"), includes);
            assertSame(source, registry.patch(ShaderKind.FRAGMENT, name, source), name + " reste vanilla.");
        }
        String vertex = VanillaShaders.compiled(core.get("rendertype_solid.vsh"), includes);
        assertSame(vertex, registry.patch(ShaderKind.VERTEX, "rendertype_solid", vertex), "Le patch vise les fragment shaders.");
    }

    @Test
    @DisplayName("Un fichier invalide ou une bibliotheque absente ecartent leur patch ; les autres restent")
    void invalidFilesAndMissingIncludesAreSkipped() {
        String valid = "{ \"stage\": \"fragment\", \"targets\": [\"a_*\"], \"inject\": [ { \"point\": \"output\", \"function\": \"f\" } ] }";
        String missingInclude = "{ \"stage\": \"fragment\", \"targets\": [\"*\"], \"imports\": [\"m:x.glsl\"],"
                + " \"inject\": [ { \"point\": \"output\", \"function\": \"g\" } ] }";
        ShaderPatchRegistry registry = ShaderPatchRegistry.of(
                Map.of("c_valid.json", valid, "a_broken.json", "{ \"stage\": 3 }", "b_missing.json", missingInclude, "d_text.json", "pas du json"),
                include -> null);
        assertEquals(1, registry.size(), "Seul le patch valide et complet reste.");
        String source = "#version 150\nout vec4 fragColor;\nvoid main() { fragColor = vec4(1.0); }\n";
        assertTrue(registry.patch(ShaderKind.FRAGMENT, "a_solid", source).endsWith("    fragColor = f(fragColor);\n}\n"), "Le patch valide s'applique.");
        assertSame(source, registry.patch(ShaderKind.FRAGMENT, "b_other", source), "Le patch a la bibliotheque absente ne s'applique pas.");
    }

    @Test
    @DisplayName("Deux patchs qui importent la meme bibliotheque la lisent une fois et s'appliquent dans l'ordre des fichiers")
    void patchesShareIncludesAndApplyInOrder() {
        String first = "{ \"stage\": \"fragment\", \"targets\": [\"*\"], \"imports\": [\"m:lib.glsl\"],"
                + " \"inject\": [ { \"point\": \"output\", \"function\": \"f\" } ] }";
        String second = "{ \"stage\": \"fragment\", \"targets\": [\"*\"], \"imports\": [\"m:lib.glsl\"],"
                + " \"inject\": [ { \"point\": \"main_before\", \"code\": \"g();\" } ] }";
        int[] reads = new int[1];
        ShaderPatchRegistry registry = ShaderPatchRegistry.of(Map.of("a.json", first, "b.json", second), include -> {
            reads[0]++;
            return "// lib";
        });
        assertEquals(2, registry.size(), "Deux patchs.");
        assertEquals(1, reads[0], "La bibliotheque est lue une fois.");
        String source = "#version 150\nout vec4 fragColor;\nvoid main() { fragColor = vec4(1.0); }\n";
        String patched = registry.patch(ShaderKind.FRAGMENT, "x", source);
        assertTrue(patched.endsWith("    fragColor = f(fragColor);\n}\n"), "Le premier patch enveloppe la main : " + patched + ".");
        assertFalse(patched.contains("g();"), "Le second patch trouve la main deja enveloppee : un seul patch par etage.");
        assertSame(patched, registry.patch(ShaderKind.FRAGMENT, "x", patched), "Le second trouve la source deja patchee et ne l'enveloppe pas deux fois.");
    }

    @Test
    @DisplayName("Un shader hors signature n'est patche par aucun patch, et chaque refus n'est journalise qu'une fois")
    void unpatchableShaderStaysVanilla() {
        String patch = "{ \"stage\": \"fragment\", \"targets\": [\"*\"], \"inject\": [ { \"point\": \"output\", \"function\": \"f\" } ] }";
        ShaderPatchRegistry registry = ShaderPatchRegistry.of(Map.of("p.json", patch), include -> null);
        String twoOutputs = "#version 150\nout vec4 a;\nout vec4 b;\nvoid main() { a = b; }\n";
        assertSame(twoOutputs, registry.patch(ShaderKind.FRAGMENT, "x", twoOutputs), "Refuse : deux sorties.");
        assertSame(twoOutputs, registry.patch(ShaderKind.FRAGMENT, "x", twoOutputs), "Refuse encore, journal deja ecrit.");
        assertSame(ShaderPatchRegistry.EMPTY, ShaderPatchRegistry.of(Map.of(), include -> null), "Sans fichier, le registre vide.");
        assertSame(twoOutputs, ShaderPatchRegistry.EMPTY.patch(ShaderKind.FRAGMENT, "x", twoOutputs), "Le registre vide ne touche a rien.");
    }

    static String lookInclude() {
        try {
            return resource("assets/gtgs_backrooms/shaders/include/look.glsl");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String scheduleInclude() {
        try {
            return resource("assets/gtgs_backrooms/shaders/include/neon_schedule.glsl");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ShaderPatchRegistryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
