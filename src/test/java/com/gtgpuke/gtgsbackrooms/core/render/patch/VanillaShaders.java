package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import net.minecraft.client.gl.GlImportProcessor;

/**
 * Les shaders de base de vanilla et leurs bibliotheques, lus dans le jar du jeu (ou son dossier extrait), et la source
 * compilee de chacun : celle que le jeu donne au pilote, les {@code #moj_import} resolus par le processeur vanilla.
 */
final class VanillaShaders {

    static final String CORE = "assets/minecraft/shaders/core/";
    static final String INCLUDE = "assets/minecraft/shaders/include/";

    private VanillaShaders() {
    }

    /** Sources brutes des shaders de base, par nom de fichier. */
    static Map<String, String> core() throws IOException {
        return read(CORE, "position.vsh");
    }

    /** Bibliotheques GLSL, par nom de fichier. */
    static Map<String, String> includes() throws IOException {
        return read(INCLUDE, "fog.glsl");
    }

    /** Source compilee d'un shader : ses {@code #moj_import} resolus comme le fait le jeu. */
    static String compiled(String raw, Map<String, String> includes) {
        GlImportProcessor processor = new GlImportProcessor() {
            private final Set<String> visited = new HashSet<>();

            @Override
            public String loadImport(boolean inline, String name) {
                if (!this.visited.add(name)) {
                    return null;
                }
                return includes.get(name);
            }
        };
        return String.join("", processor.readSource(raw));
    }

    private static Map<String, String> read(String directory, String witness) throws IOException {
        URL url = VanillaShaders.class.getClassLoader().getResource(directory + witness);
        assertNotNull(url, "Les shaders vanilla sont sur le classpath de test.");
        Map<String, String> files = new TreeMap<>();
        if ("jar".equals(url.getProtocol())) {
            // Adresse de la forme jar:file:/C:/.../minecraft-merged.jar!/assets/... : le jar est la partie avant le separateur.
            String path = url.getPath();
            Path jarPath = Path.of(URI.create(path.substring(0, path.indexOf("!/"))));
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(directory) && isShader(name)) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            files.put(name.substring(directory.length()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        } else {
            Path folder = Path.of(URI.create(url.toString())).getParent();
            try (Stream<Path> paths = Files.list(folder)) {
                for (Path file : (Iterable<Path>) paths::iterator) {
                    String name = file.getFileName().toString();
                    if (isShader(name)) {
                        files.put(name, Files.readString(file));
                    }
                }
            }
        }
        return files;
    }

    private static boolean isShader(String name) {
        return name.endsWith(".vsh") || name.endsWith(".fsh") || name.endsWith(".glsl");
    }
}
