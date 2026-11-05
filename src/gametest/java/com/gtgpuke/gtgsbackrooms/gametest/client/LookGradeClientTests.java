package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntConsumer;

import com.mojang.blaze3d.platform.GlStateManager;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookLuts;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookPrograms;
import com.gtgpuke.gtgsbackrooms.core.render.client.ShaderCompileCheck;
import com.gtgpuke.gtgsbackrooms.core.render.light.LookLut;
import com.gtgpuke.gtgsbackrooms.core.render.light.TexelGrid;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * L'etalonnage et la lumiere au texel tels que le processeur graphique les calcule : les etages patches compilent tous,
 * la LUT est liee a son unite et les programmes la lisent, la fonction {@code gtgs_grade} des shaders rend la couleur de
 * {@link LookLut} (interpolee entre ses pas comme le fait la texture), et {@code gtgs_lightPosition} rend le centre de
 * texel de {@link TexelGrid}. Les deux fonctions sont prises dans les bibliotheques du mod telles que le jeu les charge,
 * compilees dans un shader de test qui calcule un cas par pixel, relu en flottants.
 */
final class LookGradeClientTests {

    /** Trente-six programmes du monde, deux etages chacun : au moins ceux-la sont patches. */
    private static final int LOOK_STAGES = 72;
    private static final Identifier GRADE_LIBRARY = Identifier.of(ModConstants.MOD_ID, "shaders/include/look_grade.glsl");
    private static final Identifier LOOK_LIBRARY = Identifier.of(ModConstants.MOD_ID, "shaders/include/look.glsl");
    private static final String LIGHT_START = "vec3 gtgs_texelOf(vec3 p) {";
    private static final String LIGHT_LAST = "vec3 gtgs_lightPosition() {";
    private static final int BATCH = 64;
    private static final int CHANNELS = 4;
    /** Demi-flottants de la LUT (un millieme pres de 1) et poids de filtrage du pilote. */
    private static final float GRADE_TOLERANCE = 2.0e-3f;
    private static final float LIGHT_TOLERANCE = 1.0e-4f;
    /** Marge, en fraction de texel, qui ecarte les cas poses sur un bord de texel (flottants du GPU contre doubles). */
    private static final double EDGE_MARGIN = 1.0e-3;
    /** Un identifiant de look sans profil : son etage est l'identite. */
    private static final int UNUSED_LOOK = 6;
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String GRADE_MAIN = """
            uniform vec4 gtgs_TestColor[64];
            uniform int gtgs_TestCell;
            out vec4 fragColor;
            void main() {
                vec4 t = gtgs_TestColor[int(gl_FragCoord.x) / gtgs_TestCell];
                fragColor = vec4(gtgs_grade(t.rgb, int(t.w + 0.5)), 1.0);
            }
            """;
    private static final String LIGHT_HEAD = """
            #version 150
            uniform vec4 gtgs_LookParams;
            uniform mat4 ModelViewMat;
            uniform vec3 gtgs_CameraFract;
            vec3 gtgs_Shift = vec3(0.0);
            vec3 gtgs_ViewPos;
            vec3 gtgs_WorldRel;
            vec3 gtgs_Normal;
            float gtgs_Terrain;
            """;
    private static final String LIGHT_MAIN = """
            uniform vec4 gtgs_TestRel[64];
            uniform vec4 gtgs_TestNormal[64];
            out vec4 fragColor;
            void main() {
                int i = int(gl_FragCoord.x);
                gtgs_WorldRel = gtgs_TestRel[i].xyz;
                gtgs_Terrain = gtgs_TestRel[i].w;
                gtgs_Normal = gtgs_TestNormal[i].xyz;
                gtgs_ViewPos = vec3(-7.0, 3.0, 11.0);
                fragColor = vec4(gtgs_lightPosition(), 1.0);
            }
            """;
    private static final Vector3f VIEW_POS = new Vector3f(-7.0f, 3.0f, 11.0f);
    /** Reglages de lecture des pixels laisses dans un etat quelconque avant de refaire la LUT. */
    private static final int[] DIRTY_UNPACK = {GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_ALIGNMENT,
        GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES};
    private static final int[] DIRTY_VALUES = {4096, 7, 5, 1, 99, 3};

    private LookGradeClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(gradeAndTexelLightOnGpu());
    }

    private static ClientScenario gradeAndTexelLightOnGpu() {
        return ClientScenario.named("look_grade_and_texel_light_on_gpu")
                .run("palier avec le post", LevelZeroClientTests::highTier)
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .frames(10)
                .check("etages patches tous compiles par le pilote", context -> {
                    ClientTestContext.require(ShaderCompileCheck.refused() == 0, ShaderCompileCheck.refused() + " etage(s) patche(s) refuse(s) par le pilote.");
                    ClientTestContext.require(ShaderCompileCheck.accepted() >= LOOK_STAGES, "Seulement " + ShaderCompileCheck.accepted()
                            + " etage(s) patche(s) compile(s), au moins " + LOOK_STAGES + " attendus.");
                })
                .check("LUT liee a son unite, lue par le terrain et par le post", LookGradeClientTests::requireLutBound)
                .run("autre texture 3D liee a l'unite de la LUT", context -> {
                    int previousUnit = GlStateManager._getActiveTexture();
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LookLuts.UNIT);
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
                    GlStateManager._activeTexture(previousUnit);
                })
                .frames(2)
                .check("LUT reliee au debut du rendu du monde", LookGradeClientTests::requireLutBound)
                .check("pas de la grille des texels envoye au terrain", context -> {
                    int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
                    int location = GL20.glGetUniformLocation(program, "gtgs_LookParams");
                    ClientTestContext.require(location >= 0, "Terrain sans gtgs_LookParams.");
                    float[] params = new float[CHANNELS];
                    GL20.glGetUniformfv(program, location, params);
                    int steps = TexelGrid.steps(ModConfig.LOOK_PIXEL_SHADOWS);
                    ClientTestContext.require(params[0] == steps, "Pas de la grille " + params[0] + " dans le terrain au lieu de " + steps + ".");
                })
                .check("LUT refaite en pleine partie, etat de lecture des pixels quelconque", LookGradeClientTests::requireRebuildKeepsUnpackState)
                .await("lumiere cuite autour du joueur", LookGradeClientTests::zoneBaked, 1200)
                .check("le terrain lit la zone de lumiere cuite, a son unite", LookGradeClientTests::requireZoneRead)
                .await("tuiles du cache de surfaces calculees", context -> LightSurfaces.freeTiles() < SurfaceAtlas.CAPACITY - 1 && LightSurfaces.pending() == null, 2400)
                .frames(2)
                .check("le terrain lit le cache de surfaces, une image apres l'autre sans erreur", LookGradeClientTests::requireSurfaceRead)
                .check("gtgs_grade du GPU egale LookLut", LookGradeClientTests::requireGradeMatches)
                .check("gtgs_lightPosition du GPU egale TexelGrid", LookGradeClientTests::requireLightPositionMatches)
                .run("retour dans l'Overworld", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .frames(4)
                .check("hors des Backrooms, le terrain ignore la zone et les faces gardent l'ombrage vanilla", context -> {
                    requireBaked(0.0f);
                    ClientTestContext.require(!LightCopy.flatFaces(context.world()), "L'Overworld perd son ombrage par face.");
                    ClientTestContext.require(context.world().getBrightness(Direction.DOWN, true) < 1.0f, "Ombrage vanilla du dessous des blocs.");
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static void requireLutBound(ClientTestContext context) {
        ClientTestContext.require(LookLuts.texture() != 0, "Aucune texture de LUT.");
        int previousUnit = GlStateManager._getActiveTexture();
        int bound;
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LookLuts.UNIT);
            bound = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        } finally {
            GlStateManager._activeTexture(previousUnit);
        }
        ClientTestContext.require(bound == LookLuts.texture(), "Unite " + LookLuts.UNIT + " : texture 3D " + bound + " au lieu de la LUT "
                + LookLuts.texture() + ".");
        requireSamplerUnit("terrain plein", GameRenderer.getRenderTypeSolidProgram().getGlRef());
        ClientTestContext.require(LookPrograms.post() != null, "Programme du post non charge.");
        requireSamplerUnit("post", LookPrograms.post().getGlRef());
    }

    /**
     * Vanilla laisse le pas des lignes de sa derniere image envoyee (textures animees, a chaque tick) : la LUT refaite par
     * un rechargement en pleine partie doit se lire quand meme, sans lire au-dela de sa table, et rendre l'etat trouve. Un
     * tampon de pixels lie (un autre mod) ne doit pas changer ce qu'elle envoie.
     */
    private static void requireRebuildKeepsUnpackState(ClientTestContext context) {
        int[] previous = new int[DIRTY_UNPACK.length];
        for (int i = 0; i < DIRTY_UNPACK.length; i++) {
            previous[i] = GL11.glGetInteger(DIRTY_UNPACK[i]);
        }
        int buffer = GL15.glGenBuffers();
        int[] after = new int[DIRTY_UNPACK.length];
        int boundAfter;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, 16L, GL15.GL_STREAM_DRAW);
            for (int i = 0; i < DIRTY_UNPACK.length; i++) {
                GL11.glPixelStorei(DIRTY_UNPACK[i], DIRTY_VALUES[i]);
            }
            LookLuts.rebuild(List.of(LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD)));
            for (int i = 0; i < DIRTY_UNPACK.length; i++) {
                after[i] = GL11.glGetInteger(DIRTY_UNPACK[i]);
            }
            boundAfter = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        } finally {
            for (int i = 0; i < DIRTY_UNPACK.length; i++) {
                GL11.glPixelStorei(DIRTY_UNPACK[i], previous[i]);
            }
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glDeleteBuffers(buffer);
        }
        for (int i = 0; i < DIRTY_UNPACK.length; i++) {
            ClientTestContext.require(after[i] == DIRTY_VALUES[i], "Reglage de lecture des pixels " + DIRTY_UNPACK[i] + " a " + after[i]
                    + " apres la LUT, au lieu de " + DIRTY_VALUES[i] + ".");
        }
        ClientTestContext.require(boundAfter == buffer, "Tampon de pixels " + boundAfter + " apres la LUT, au lieu de " + buffer + ".");
    }

    /** La lumiere cuite est arrivee dans la texture : une valeur valide sous le joueur, d'apres le terrain lui-meme. */
    private static boolean zoneBaked(ClientTestContext context) {
        return LightVolume.texture() != 0 && LightCopy.copiedSections(context.world()) > 0
                && LightVolume.covers(LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD), context.player().getBlockX(), context.player().getBlockY(),
                        context.player().getBlockZ());
    }

    private static void requireZoneRead(ClientTestContext context) {
        requireBaked(1.0f);
        ClientTestContext.require(LightCopy.flatFaces(context.world()), "Le Level 0 garde l'ombrage vanilla par face.");
        ClientTestContext.require(context.world().getBrightness(Direction.DOWN, true) == 1.0f, "Le dessous des blocs reste assombri.");
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        int location = GL20.glGetUniformLocation(program, LightVolume.SAMPLER);
        ClientTestContext.require(location >= 0, "Terrain sans zone de lumiere.");
        ClientTestContext.require(GL20.glGetUniformi(program, location) == LightVolume.UNIT, "Zone lue sur une autre unite.");
        int previousUnit = GlStateManager._getActiveTexture();
        int bound;
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
            bound = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
        } finally {
            GlStateManager._activeTexture(previousUnit);
        }
        ClientTestContext.require(bound == LightVolume.texture(), "Texture " + bound + " a l'unite de la zone au lieu de " + LightVolume.texture() + ".");
    }

    private static void requireSurfaceRead(ClientTestContext context) {
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        ClientTestContext.require(LightSurfaces.active(), "Cache de surfaces coupe.");
        requireFloat(program, "gtgs_SurfaceLight", 1.0f);
        int atlas = GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_ATLAS);
        int tiles = GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_TILES);
        ClientTestContext.require(GL20.glGetUniformi(program, atlas) == LightSurfaces.UNIT_ATLAS, "Cache lu sur une autre unite.");
        ClientTestContext.require(GL20.glGetUniformi(program, tiles) == LightSurfaces.UNIT_TILES, "Indirection lue sur une autre unite.");
    }

    private static void requireFloat(int program, String name, float expected) {
        int location = GL20.glGetUniformLocation(program, name);
        ClientTestContext.require(location >= 0, "Terrain sans " + name + ".");
        float[] value = new float[1];
        GL20.glGetUniformfv(program, location, value);
        ClientTestContext.require(value[0] == expected, name + " a " + value[0] + " au lieu de " + expected + ".");
    }

    private static void requireBaked(float expected) {
        requireFloat(GameRenderer.getRenderTypeSolidProgram().getGlRef(), "gtgs_BakedLight", expected);
    }

    private static void requireSamplerUnit(String name, int program) {
        int location = GL20.glGetUniformLocation(program, LookLuts.SAMPLER);
        ClientTestContext.require(location >= 0, "Programme " + name + " sans echantillonneur " + LookLuts.SAMPLER + ".");
        int unit = GL20.glGetUniformi(program, location);
        ClientTestContext.require(unit == LookLuts.UNIT, "Programme " + name + " : LUT lue sur l'unite " + unit + " au lieu de " + LookLuts.UNIT + ".");
    }

    private static void requireGradeMatches(ClientTestContext context) {
        LookProfile levelZero = LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD);
        float[] table = new float[LookLut.SIZE * LookLut.SIZE * LookLut.SIZE * LookLut.CHANNELS];
        LookLut.fill(levelZero, table, 0);
        Random random = new Random(1L);
        List<float[]> cases = new ArrayList<>();
        int[] looks = {0, levelZero.id(), UNUSED_LOOK};
        for (int look : looks) {
            cases.add(new float[] {0.0f, 0.0f, 0.0f, look});
            cases.add(new float[] {1.0f, 1.0f, 1.0f, look});
            cases.add(new float[] {1.3f, -0.2f, 0.5f, look});
            for (int i = 0; i < 12; i++) {
                cases.add(new float[] {random.nextInt(LookLut.SIZE) / (LookLut.SIZE - 1.0f), random.nextInt(LookLut.SIZE) / (LookLut.SIZE - 1.0f),
                        random.nextInt(LookLut.SIZE) / (LookLut.SIZE - 1.0f), look});
            }
            for (int i = 0; i < 6; i++) {
                cases.add(new float[] {random.nextFloat(), random.nextFloat(), random.nextFloat(), look});
            }
        }
        String source = "#version 150\n" + library(context.client(), GRADE_LIBRARY) + "\n" + GRADE_MAIN;
        // Un cas par carre de deux pixels : couleur constante dans le carre, le pilote agrandit la texture (filtre
        // d'agrandissement, le cas d'une surface en jeu). Un cas par pixel : voisins tres differents, il la reduit.
        for (int cell = 1; cell <= 2; cell++) {
            int size = cell;
            float[] pixels = render(source, cell, program -> {
                GL20.glUniform1i(GL20.glGetUniformLocation(program, LookLuts.SAMPLER), LookLuts.UNIT);
                GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_TestCell"), size);
                for (int i = 0; i < cases.size(); i++) {
                    float[] c = cases.get(i);
                    GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestColor[" + i + "]"), c[0], c[1], c[2], c[3]);
                }
            });
            requireGradedCases(cases, pixels, levelZero, table, cell == 1 ? "reduite" : "agrandie");
        }
    }

    private static void requireGradedCases(List<float[]> cases, float[] pixels, LookProfile levelZero, float[] table, String filter) {
        float[] expected = new float[3];
        for (int i = 0; i < cases.size(); i++) {
            float[] c = cases.get(i);
            if ((int) c[3] == levelZero.id()) {
                trilinear(table, c, expected);
            } else {
                for (int k = 0; k < 3; k++) {
                    expected[k] = Math.clamp(c[k], 0.0f, 1.0f);
                }
            }
            for (int k = 0; k < 3; k++) {
                float actual = pixels[i * CHANNELS + k];
                ClientTestContext.require(Math.abs(actual - expected[k]) <= GRADE_TOLERANCE, String.format(Locale.ROOT,
                        "LUT %s, look %d, couleur (%.4f, %.4f, %.4f), composante %d : %.5f au lieu de %.5f.", filter, (int) c[3], c[0], c[1], c[2], k,
                        actual, expected[k]));
            }
        }
    }

    /** Ce que rend la texture filtree : interpolation des huit pas voisins de la table. */
    private static void trilinear(float[] table, float[] color, float[] out) {
        int last = LookLut.SIZE - 1;
        int[] low = new int[3];
        int[] high = new int[3];
        float[] weight = new float[3];
        for (int k = 0; k < 3; k++) {
            float x = Math.clamp(color[k], 0.0f, 1.0f) * last;
            low[k] = Math.min((int) Math.floor(x), last);
            high[k] = Math.min(low[k] + 1, last);
            weight[k] = x - low[k];
        }
        for (int channel = 0; channel < 3; channel++) {
            float sum = 0.0f;
            for (int corner = 0; corner < 8; corner++) {
                int r = (corner & 1) == 0 ? low[0] : high[0];
                int g = (corner & 2) == 0 ? low[1] : high[1];
                int b = (corner & 4) == 0 ? low[2] : high[2];
                float w = ((corner & 1) == 0 ? 1.0f - weight[0] : weight[0]) * ((corner & 2) == 0 ? 1.0f - weight[1] : weight[1])
                        * ((corner & 4) == 0 ? 1.0f - weight[2] : weight[2]);
                sum += w * table[((b * LookLut.SIZE + g) * LookLut.SIZE + r) * LookLut.CHANNELS + channel];
            }
            out[channel] = sum;
        }
    }

    private static void requireLightPositionMatches(ClientTestContext context) {
        String look = library(context.client(), LOOK_LIBRARY);
        int start = look.indexOf(LIGHT_START);
        int end = look.indexOf("\n}\n", look.indexOf(LIGHT_LAST, start));
        ClientTestContext.require(start >= 0 && end > start, "gtgs_lightPosition introuvable dans " + LOOK_LIBRARY + ".");
        String source = LIGHT_HEAD + look.substring(start, end + 2) + "\n" + LIGHT_MAIN;
        Matrix4f view = new Matrix4f().rotateX(0.35f).rotateY(-0.8f).translate(0.25f, -0.5f, 0.125f);
        float[] matrix = view.get(new float[16]);
        Vector3f fract = new Vector3f(0.3125f, 0.71f, 0.05f);
        Random random = new Random(2L);
        // Les six faces, des normales en biais, et les egalites x et y, y et z, des trois.
        float[][] normals = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {0.6f, 0.8f, 0}, {0, 0.6f, -0.8f},
            {0.7f, 0.7f, 0}, {0, 0.7f, -0.7f}, {-0.6f, 0.6f, 0.6f}};
        for (int steps : new int[] {TexelGrid.DEFAULT_STEPS, 16, 0}) {
            List<float[]> cases = new ArrayList<>();
            while (cases.size() < BATCH) {
                float[] normal = normals[cases.size() % normals.length];
                float terrain = cases.size() % 11 == 10 ? 0.0f : 1.0f;
                float[] rel = {random.nextFloat() * 40.0f - 20.0f, random.nextFloat() * 40.0f - 20.0f, random.nextFloat() * 40.0f - 20.0f};
                if (nearTexelEdge(rel, fract, steps)) {
                    continue;
                }
                cases.add(new float[] {rel[0], rel[1], rel[2], terrain, normal[0], normal[1], normal[2]});
            }
            float[] pixels = render(source, 1, program -> {
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_LookParams"), steps, 0.0f, 0.0f, 0.0f);
                GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ModelViewMat"), false, matrix);
                GL20.glUniform3f(GL20.glGetUniformLocation(program, "gtgs_CameraFract"), fract.x, fract.y, fract.z);
                for (int i = 0; i < cases.size(); i++) {
                    float[] c = cases.get(i);
                    GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestRel[" + i + "]"), c[0], c[1], c[2], c[3]);
                    GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestNormal[" + i + "]"), c[4], c[5], c[6], 0.0f);
                }
            });
            double[] snapped = new double[3];
            for (int i = 0; i < cases.size(); i++) {
                float[] c = cases.get(i);
                Vector3f expected;
                if (steps == 0 || c[3] < 0.5f) {
                    expected = new Vector3f(VIEW_POS);
                } else {
                    TexelGrid.snap(fract.x + (double) c[0], fract.y + (double) c[1], fract.z + (double) c[2], c[4], c[5], c[6], steps, snapped);
                    expected = view.transformPosition(new Vector3f((float) (snapped[0] - fract.x), (float) (snapped[1] - fract.y),
                            (float) (snapped[2] - fract.z)));
                }
                float dx = pixels[i * CHANNELS] - expected.x;
                float dy = pixels[i * CHANNELS + 1] - expected.y;
                float dz = pixels[i * CHANNELS + 2] - expected.z;
                ClientTestContext.require(Math.abs(dx) <= LIGHT_TOLERANCE && Math.abs(dy) <= LIGHT_TOLERANCE && Math.abs(dz) <= LIGHT_TOLERANCE,
                        String.format(Locale.ROOT, "Pas %d, point (%.4f, %.4f, %.4f), normale (%.1f, %.1f, %.1f), terrain %.0f : (%.5f, %.5f, %.5f) au lieu de %s.",
                                steps, c[0], c[1], c[2], c[4], c[5], c[6], c[3], pixels[i * CHANNELS], pixels[i * CHANNELS + 1], pixels[i * CHANNELS + 2],
                                expected));
            }
        }
    }

    /** Un point a moins de la marge d'un bord de texel, sur un axe : les flottants du GPU peuvent l'arrondir de l'autre cote. */
    private static boolean nearTexelEdge(float[] rel, Vector3f fract, int steps) {
        if (steps == 0) {
            return false;
        }
        double[] p = {fract.x + (double) rel[0], fract.y + (double) rel[1], fract.z + (double) rel[2]};
        for (double value : p) {
            double texels = value * steps;
            double distance = Math.abs(texels - Math.rint(texels));
            if (distance < EDGE_MARGIN * steps) {
                return true;
            }
        }
        return false;
    }

    static String library(MinecraftClient client, Identifier id) {
        Resource resource = client.getResourceManager().getResource(id).orElse(null);
        ClientTestContext.require(resource != null, "Bibliotheque absente : " + id + ".");
        try (Reader reader = resource.getReader()) {
            return IOUtils.toString(reader).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Lecture impossible : " + id + ".", exception);
        }
    }

    /**
     * Dessine le shader de test, un cas par carre de {@code cell} pixels de cote, {@value #BATCH} cas sur une ligne, en
     * flottants, et rend le premier pixel de chaque cas. L'etat GL (programme, framebuffer, texture, tableau de sommets,
     * fenetre de rendu) est remis tel quel : les caches de vanilla le supposent.
     */
    static float[] render(String fragmentSource, int cell, IntConsumer uniforms) {
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
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, BATCH * cell, cell, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL20.glUseProgram(program);
            uniforms.accept(program);
            GL30.glBindVertexArray(array);
            GL11.glViewport(0, 0, BATCH * cell, cell);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            FloatBuffer pixels = BufferUtils.createFloatBuffer(BATCH * cell * CHANNELS);
            GL11.glReadPixels(0, 0, BATCH * cell, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
            float[] values = new float[BATCH * CHANNELS];
            for (int i = 0; i < BATCH; i++) {
                for (int k = 0; k < CHANNELS; k++) {
                    values[i * CHANNELS + k] = pixels.get(i * cell * CHANNELS + k);
                }
            }
            return values;
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
            throw new IllegalStateException("Programme de test refuse (" + log.trim() + ").");
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
            throw new IllegalStateException("Shader de test refuse (" + log.trim() + ") :\n" + source);
        }
        return shader;
    }
}
