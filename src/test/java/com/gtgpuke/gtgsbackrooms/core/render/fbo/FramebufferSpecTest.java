package com.gtgpuke.gtgsbackrooms.core.render.fbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

@DisplayName("Framebuffer declare en donnees")
class FramebufferSpecTest {

    @Test
    @DisplayName("La copie d'ecran du mod se lit : plein ecran, sans profondeur, au plus proche")
    void screenCopyIsRead() throws IOException {
        FramebufferSpec spec = FramebufferSpec.fromJson(JsonParser.parseString(resource("assets/gtgs_backrooms/framebuffers/screen_copy.json"))
                .getAsJsonObject());
        assertEquals(Identifier.of("gtgs_backrooms", "screen_copy"), spec.name(), "Nom.");
        assertEquals(1.0f, spec.scale(), "Plein ecran.");
        assertFalse(spec.depth(), "Sans profondeur.");
        assertFalse(spec.linear(), "Au plus proche.");
        assertEquals(1280, spec.width(1280), "Largeur de l'ecran.");
        assertEquals(720, spec.height(720), "Hauteur de l'ecran.");
    }

    @Test
    @DisplayName("Une declaration minimale a des defauts surs, une demi-echelle arrondit et garde un pixel au moins")
    void defaultsAndScale() {
        FramebufferSpec minimal = parse("{ \"name\": \"m:f\" }");
        assertEquals(1.0f, minimal.scale(), "Plein ecran par defaut.");
        assertFalse(minimal.depth(), "Sans profondeur par defaut.");
        assertFalse(minimal.linear(), "Au plus proche par defaut.");
        FramebufferSpec half = parse("{ \"name\": \"m:f\", \"scale\": 0.5, \"depth\": true, \"filter\": \"linear\" }");
        assertTrue(half.depth() && half.linear(), "Profondeur et filtrage lineaire lus.");
        assertEquals(427, half.width(853), "Arrondi au plus pres.");
        assertEquals(1, half.height(1), "Un pixel au moins.");
    }

    @Test
    @DisplayName("Une declaration invalide est refusee avec sa raison")
    void invalidDeclarationsAreRefused() {
        assertRefused("{ }", "Nom de framebuffer absent");
        assertRefused("{ \"name\": { } }", "Nom de framebuffer absent");
        assertRefused("{ \"name\": \"m f\" }", "Nom de framebuffer absent");
        assertRefused("{ \"name\": \"m:f\", \"scale\": 0 }", "Echelle hors de");
        assertRefused("{ \"name\": \"m:f\", \"scale\": 1.5 }", "Echelle hors de");
        assertRefused("{ \"name\": \"m:f\", \"format\": \"RGBA16F\" }", "Format inconnu");
        assertRefused("{ \"name\": \"m:f\", \"filter\": \"cubic\" }", "Filtre inconnu");
    }

    private static void assertRefused(String json, String reason) {
        JsonParseException failure = assertThrows(JsonParseException.class, () -> parse(json), reason);
        assertTrue(failure.getMessage().contains(reason), "Raison : " + failure.getMessage() + ".");
    }

    private static FramebufferSpec parse(String json) {
        return FramebufferSpec.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = FramebufferSpecTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " est dans les ressources du mod.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
