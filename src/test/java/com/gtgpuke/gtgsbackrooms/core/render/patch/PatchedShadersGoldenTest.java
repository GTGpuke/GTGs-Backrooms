package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderClipInjector;

/**
 * Empreintes des sources que le pilote recoit (specification des shaders, section 11, "Golden") : chaque shader de base
 * de 1.21.1 compile comme le jeu le compile, passe par le plan de decoupe des portails puis par les patchs du mod, comme
 * dans {@code MixinShaderStage}. Un changement de patch, de bibliotheque ou de l'injecteur change des empreintes : il est
 * explicite. Fins de ligne ramenees a {@code \n} avant le calcul, pour la meme empreinte sous Windows et en CI.
 */
@DisplayName("Empreintes des shaders patches")
class PatchedShadersGoldenTest {

    private static final String GOLDEN = "patched_shaders_1_21_1.json";
    private static final String PATCHES = "assets/gtgs_backrooms/shader_patches/";
    private static final int HEX_DIGITS = 16;
    /** Trente-six programmes du monde, deux etages chacun. */
    private static final int LOOK_STAGES = 72;

    @Test
    @DisplayName("Chaque source patchee a l'empreinte versionnee ; un changement des patchs se voit ici")
    void patchedSourcesMatchTheGolden() throws IOException {
        ShaderPatchRegistry registry = ShaderPatchRegistry.of(patchFiles(), PatchedShadersGoldenTest::include);
        Map<String, String> includes = VanillaShaders.includes();
        Map<String, String> computed = new TreeMap<>();
        for (Map.Entry<String, String> entry : VanillaShaders.core().entrySet()) {
            String file = entry.getKey();
            ShaderKind kind = file.endsWith(".fsh") ? ShaderKind.FRAGMENT : ShaderKind.VERTEX;
            String name = file.substring(0, file.length() - ".fsh".length());
            String source = VanillaShaders.compiled(entry.getValue(), includes);
            String patched = registry.patch(kind, name, ShaderClipInjector.inject(source));
            if (!patched.equals(source)) {
                computed.put(file, fingerprint(patched));
            }
        }
        JsonObject expected = readGolden();
        assertEquals("1.21.1", expected.get("minecraft").getAsString(), "Les empreintes sont celles de la version du jeu.");
        JsonObject table = new JsonObject();
        computed.forEach(table::addProperty);
        String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(table);
        assertTrue(computed.size() >= LOOK_STAGES, "Au moins les etages du look, plus les vertex shaders coupes au plan des portails : "
                + computed.size() + ".");
        assertEquals(expected.getAsJsonObject("shaders"), table,
                "Empreintes des sources patchees. Un changement voulu se recopie dans " + GOLDEN + " :\n" + pretty + ".");
    }

    private static Map<String, String> patchFiles() throws IOException {
        URL url = PatchedShadersGoldenTest.class.getClassLoader().getResource(PATCHES + "world_fragment.json");
        assertNotNull(url, "Les patchs du mod sont dans les ressources.");
        Map<String, String> files = new TreeMap<>();
        Path folder = Path.of(URI.create(url.toString())).getParent();
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                files.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        return files;
    }

    private static String include(Identifier id) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream in = PatchedShadersGoldenTest.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String fingerprint(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HEX_DIGITS);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JsonObject readGolden() throws IOException {
        try (InputStream in = PatchedShadersGoldenTest.class.getClassLoader().getResourceAsStream(GOLDEN)) {
            assertNotNull(in, "Les empreintes sont versionnees avec les tests.");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
