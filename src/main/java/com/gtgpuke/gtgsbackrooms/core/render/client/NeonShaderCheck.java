package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;

/**
 * Verification, dans le client de developpement, que le jumeau GLSL du calendrier des neons
 * ({@code neon_schedule.glsl}) donne les memes resultats que les vecteurs communs (ceux que Java suit, dans
 * {@code src/test/resources/neon_schedule_vectors.json}) : un shader de test, compile avec le meme fichier que les
 * shaders du monde, calcule chaque vecteur dans un pixel ; l'image est relue et comparee (caractere, intensite, et les
 * huit bits de poids fort du hachage, compares a Java). Un ecart coupe le jumeau pour la session ({@link NeonFaces}) :
 * la face des neons suit alors l'etat envoye dans le bloc des neons. Lancee une fois, au premier rechargement, si
 * l'option {@code gtgsbackrooms.look.verifyNeonShader} donne le chemin du fichier des vecteurs. L'etat GL (programme,
 * framebuffer, texture, tableau de sommets, fenetre de rendu) est remis tel quel : les caches de vanilla le supposent.
 */
public final class NeonShaderCheck {

    private static final int BATCH = 64;
    private static final Identifier SCHEDULE = Identifier.of(ModConstants.MOD_ID, "shaders/include/neon_schedule.glsl");
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String MAIN = """
            uniform ivec4 gtgs_TestPos[64];
            uniform int gtgs_TestSeed[64];
            uniform vec2 gtgs_TestShares[64];
            out vec4 fragColor;
            void main() {
                int i = int(gl_FragCoord.x);
                ivec4 p = gtgs_TestPos[i];
                uint character = gtgs_neonCharacter(p.xyz, gtgs_TestSeed[i]);
                int kind = gtgs_neonKind(character, gtgs_TestShares[i].x, gtgs_TestShares[i].y);
                float intensity = gtgs_neonIntensity(p.xyz, gtgs_TestSeed[i], p.w, gtgs_TestShares[i].x, gtgs_TestShares[i].y);
                fragColor = vec4(float(kind) / 2.0, intensity, float(character >> 24u) / 255.0, 1.0);
            }
            """;
    private static final int KIND_SCALE = 2;
    private static final int CHANNELS = 4;
    private static final int BYTE_MASK = 0xFF;
    private static final int TOP_BYTE = 24;
    private static final float INTENSITY_TOLERANCE = 1.5f;
    private static boolean done;
    private static int identical;

    private NeonShaderCheck() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Thread de rendu, apres le rechargement des modeles : la verification, une fois, si l'option la demande. */
    public static void runOnce(ResourceManager manager) {
        if (done || ModConfig.LOOK_VERIFY_NEON_SHADER.isEmpty()) {
            return;
        }
        done = true;
        try {
            JsonArray vectors = JsonParser.parseString(Files.readString(Path.of(ModConfig.LOOK_VERIFY_NEON_SHADER), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("vectors");
            Resource resource = manager.getResource(SCHEDULE).orElseThrow(() -> new IOException("Bibliotheque absente : " + SCHEDULE + "."));
            String schedule;
            try (Reader reader = resource.getReader()) {
                schedule = IOUtils.toString(reader);
            }
            int mismatches = verify(vectors, "#version 150\n" + schedule + "\n" + MAIN);
            if (mismatches == 0) {
                identical = vectors.size();
                ModConstants.LOGGER.info("[Look] Jumeau GLSL du calendrier des neons : {} vecteur(s) identiques au fichier commun et a Java.", vectors.size());
            } else {
                NeonFaces.disable();
                ModConstants.LOGGER.error("[Look] Jumeau GLSL du calendrier des neons : {} ecart(s) sur {} vecteur(s) : jumeau coupe pour la session.",
                        mismatches, vectors.size());
            }
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            ModConstants.LOGGER.error("[Look] Verification du jumeau GLSL impossible ({}).", exception.getMessage());
        }
    }

    /** Vecteurs que le processeur graphique a rendus identiques au fichier commun ; 0 sans verification reussie. */
    public static int identicalVectors() {
        return identical;
    }

    private static int verify(JsonArray vectors, String fragmentSource) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int[] viewport = new int[CHANNELS];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int program = link(fragmentSource);
        int texture = GL11.glGenTextures();
        int framebuffer = GL30.glGenFramebuffers();
        int array = GL30.glGenVertexArrays();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, BATCH, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL11.glViewport(0, 0, BATCH, 1);
            ByteBuffer pixels = BufferUtils.createByteBuffer(BATCH * CHANNELS);
            int mismatches = 0;
            for (int start = 0; start < vectors.size(); start += BATCH) {
                int count = Math.min(BATCH, vectors.size() - start);
                for (int i = 0; i < count; i++) {
                    JsonObject vector = vectors.get(start + i).getAsJsonObject();
                    GL20.glUniform4i(GL20.glGetUniformLocation(program, "gtgs_TestPos[" + i + "]"), vector.get("x").getAsInt(), vector.get("y").getAsInt(),
                            vector.get("z").getAsInt(), vector.get("tick").getAsInt());
                    GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_TestSeed[" + i + "]"), vector.get("seed").getAsInt());
                    GL20.glUniform2f(GL20.glGetUniformLocation(program, "gtgs_TestShares[" + i + "]"), vector.get("flickering").getAsFloat(),
                            vector.get("dead").getAsFloat());
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                pixels.clear();
                GL11.glReadPixels(0, 0, BATCH, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                for (int i = 0; i < count; i++) {
                    if (!matches(vectors.get(start + i).getAsJsonObject(), pixels, i)) {
                        mismatches++;
                    }
                }
            }
            return mismatches;
        } finally {
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousArray);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL30.glDeleteVertexArrays(array);
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glDeleteTextures(texture);
            GL20.glDeleteProgram(program);
        }
    }

    /** Un pixel contre son vecteur : caractere, intensite, et huit bits du hachage compares a Java ; un ecart est journalise. */
    private static boolean matches(JsonObject vector, ByteBuffer pixels, int i) {
        int kind = Math.round((pixels.get(i * CHANNELS) & BYTE_MASK) * KIND_SCALE / (float) BYTE_MASK);
        float intensity = (pixels.get(i * CHANNELS + 1) & BYTE_MASK) / (float) BYTE_MASK;
        int top = pixels.get(i * CHANNELS + 2) & BYTE_MASK;
        int expectedKind = switch (vector.get("kind").getAsString()) {
            case "flickering" -> NeonSchedule.FLICKERING;
            case "dead" -> NeonSchedule.DEAD;
            default -> NeonSchedule.STEADY;
        };
        int expectedTop = NeonSchedule.character(vector.get("x").getAsInt(), vector.get("y").getAsInt(), vector.get("z").getAsInt(),
                vector.get("seed").getAsInt()) >>> TOP_BYTE;
        boolean same = kind == expectedKind && top == expectedTop
                && Math.abs(intensity - vector.get("intensity").getAsFloat()) * BYTE_MASK <= INTENSITY_TOLERANCE;
        if (!same) {
            ModConstants.LOGGER.error("[Look] Ecart du jumeau GLSL : {} donne caractere {}, intensite {}, octet {} (attendu {}, {}).", vector, kind,
                    intensity, top, expectedKind, expectedTop);
        }
        return same;
    }

    private static int link(String fragmentSource) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Programme de verification refuse (" + log.trim() + ").");
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Shader de verification refuse (" + log.trim() + ").");
        }
        return shader;
    }
}
