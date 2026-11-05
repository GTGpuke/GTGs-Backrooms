package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.BodyShadowTiles;
import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Le cache des ombres des corps : a chaque image, pour chaque vue, ce que les corps retirent de la lumiere des dalles se calcule une fois
 * par texel des tuiles du cache de surfaces proches d'un corps et vues par la camera ({@link BodyShadowTiles}), dans une passe a part
 * ({@code shaders/bake/body_shadow.fsh}, le calcul de {@code body_shadow.glsl}), au lieu de se refaire dans le shader du terrain a
 * chaque fragment, ou une tuile a trois blocs couvre une centaine de pixels. Le terrain lit le resultat par deux textures : la table des
 * emplacements, une par tuile du cache de surfaces (0 : rien a retirer), et l'atlas des emplacements, une tuile et un texel de bordure de
 * chaque cote, en flottants, par emplacement. Un niveau de vue (monde du joueur, vue, vue imbriquee) a les siennes, comme son bloc des
 * neons : une vue ne defait pas celles du monde du joueur, dont le terrain translucide se dessine apres elle. Colle GL ; la recherche
 * des tuiles est dans {@link BodyShadowTiles}.
 */
public final class BodyShadows {

    /** Unite de texture de l'atlas des ombres. */
    public static final int UNIT_SHADOWS = 18;
    /** Unite de texture de la table des emplacements. */
    public static final int UNIT_SLOTS = 19;
    /** Echantillonneur de l'atlas des ombres dans les shaders. */
    public static final String SAMPLER_SHADOWS = "gtgs_BodyShadows";
    /** Echantillonneur de la table des emplacements dans les shaders. */
    public static final String SAMPLER_SLOTS = "gtgs_BodySlots";
    /** Tuiles par dessin : la taille du tableau d'uniformes du vertex. */
    private static final int BATCH = 256;
    private static final int FACE_SHIFT = 16;
    private static final Identifier SHADER = Identifier.of(ModConstants.MOD_ID, "shaders/bake/body_shadow.fsh");
    private static final Identifier[] INCLUDES = {
        Identifier.of(ModConstants.MOD_ID, "shaders/include/area_light.glsl"),
        Identifier.of(ModConstants.MOD_ID, "shaders/include/block_march.glsl"),
        Identifier.of(ModConstants.MOD_ID, "shaders/include/surface_light.glsl"),
        Identifier.of(ModConstants.MOD_ID, "shaders/include/body_box.glsl"),
        Identifier.of(ModConstants.MOD_ID, "shaders/include/neon_block.glsl"),
        Identifier.of(ModConstants.MOD_ID, "shaders/include/body_shadow.glsl"),
    };
    /** Les coins d'une tuile de la passe : deux triangles par tuile, sans tampon de sommets, la tuile lue dans le tableau. */
    private static final String VERTEX = """
            #version 150
            uniform ivec4 gtgs_Tiles[256];
            flat out ivec4 gtgs_Tile;
            const int SLOT = 34;
            const int SLOTS_ACROSS = 64;
            const vec2 ATLAS = vec2(2176.0, 1088.0);
            void main() {
                int tile = gl_VertexID / 6;
                int corner = gl_VertexID - tile * 6;
                int k = corner < 3 ? corner : (corner == 3 ? 2 : (corner == 4 ? 1 : 3));
                gtgs_Tile = gtgs_Tiles[tile];
                int place = (gtgs_Tile.x & 0xFFFF) - 1;
                vec2 origin = vec2(place % SLOTS_ACROSS, place / SLOTS_ACROSS) * float(SLOT);
                vec2 pixel = origin + vec2(k & 1, k >> 1) * float(SLOT);
                gl_Position = vec4(pixel / ATLAS * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final BodyShadowTiles TILES = new BodyShadowTiles();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final IntBuffer BATCH_VALUES = MemoryUtil.memAllocInt(BATCH * 4);
    private static final ShortBuffer SLOT_VALUES = MemoryUtil.memAllocShort(SurfaceAtlas.CAPACITY);
    private static final Level[] LEVELS = new Level[NeonLights.DEPTHS];
    private static int program;
    private static int array;
    private static int locTiles;
    private static int locCameraBlock;
    private static int locNeonCount;
    private static int locLightBase;
    private static boolean programCreated;
    private static boolean programFailed;
    private static boolean warnedDropped;

    /** Le cache d'un niveau de vue : l'atlas des ombres, la table des emplacements, et le framebuffer qui dessine dans l'atlas. */
    private static final class Level {
        private int shadows;
        private int slots;
        private int framebuffer;
        /** {@code true} si la table pointe des emplacements : elle est a vider si plus rien ne se calcule. */
        private boolean filled;
    }

    private BodyShadows() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * La passe d'un niveau de vue, apres ses neons et ses corps ({@link NeonLights}) et avant son terrain : les tuiles proches d'un
     * corps et vues par la camera recoivent leur ombre, et les textures du niveau sont liees. Rien sans cache de surfaces lie.
     *
     * @param depth niveau de la vue, celui de {@link NeonLights#depth()}
     * @param positionMatrix matrice de vue de sa camera, celle du dessin
     */
    static void render(int depth, Matrix4f positionMatrix) {
        if (!LightVolume.surfacesBound()) {
            return;
        }
        Level level = LEVELS[depth];
        if (level == null) {
            level = create();
            LEVELS[depth] = level;
        }
        ShadowCasters casters = NeonLights.casters();
        LightSurfaces surfaces = LightVolume.boundSurfaces();
        int count = 0;
        if (casters.bodies() > 0 && LookResources.quality().casterRays() > 0 && ensureProgram()) {
            VIEW_PROJECTION.set(RenderSystem.getProjectionMatrix()).mul(positionMatrix);
            FRUSTUM.set(VIEW_PROJECTION);
            count = TILES.collect(surfaces.atlas(), FRUSTUM, casters.values(), casters.bodies(), LookState.cameraBlockX(), LookState.cameraBlockY(),
                    LookState.cameraBlockZ(), LookState.cameraFractX(), LookState.cameraFractY(), LookState.cameraFractZ());
            if (TILES.dropped() > 0 && !warnedDropped) {
                warnedDropped = true;
                ModConstants.LOGGER.warn("[Look] Cache des ombres des corps plein : {} tuile(s) sans ombre a cette image.", TILES.dropped());
            }
        }
        if (count > 0 || level.filled) {
            uploadSlots(level, count);
            level.filled = count > 0;
        }
        if (count > 0) {
            draw(level, count);
        }
        bind(depth);
    }

    /** Lie les textures d'un niveau de vue a leurs unites, s'il en a : au retour d'une vue, celles de la vue qui la contient. */
    static void bind(int depth) {
        Level level = LEVELS[depth];
        if (level == null) {
            return;
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SHADOWS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level.shadows);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SLOTS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level.slots);
        GlStateManager._activeTexture(previousUnit);
    }

    /** La table des emplacements d'un niveau : un emplacement par tuile trouvee, dans leur ordre, 0 partout ailleurs. */
    private static void uploadSlots(Level level, int count) {
        MemoryUtil.memSet(MemoryUtil.memAddress(SLOT_VALUES), 0, (long) SurfaceAtlas.CAPACITY * Short.BYTES);
        int[] tiles = TILES.tiles();
        for (int i = 0; i < count; i++) {
            SLOT_VALUES.put(tiles[i], (short) (i + 1));
        }
        SLOT_VALUES.position(0).limit(SurfaceAtlas.CAPACITY);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SLOTS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level.slots);
        PixelUnpack.begin();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SurfaceAtlas.ACROSS, SurfaceAtlas.ROWS, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, SLOT_VALUES);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /** Dessine les tuiles trouvees dans l'atlas du niveau, par lots de {@value #BATCH}. */
    private static void draw(Level level, int count) {
        LightSurfaces.enterPass(level.framebuffer);
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL11.glViewport(0, 0, BodyShadowTiles.ATLAS_WIDTH, BodyShadowTiles.ATLAS_HEIGHT);
            GL20.glUniform3i(locCameraBlock, LookState.cameraBlockX(), LookState.cameraBlockY(), LookState.cameraBlockZ());
            GL20.glUniform1i(locNeonCount, LookState.neonCount());
            GL20.glUniform1i(locLightBase, LightVolume.baseY());
            int[] cells = TILES.cells();
            int[] faces = TILES.faces();
            for (int first = 0; first < count; first += BATCH) {
                int batch = Math.min(BATCH, count - first);
                BATCH_VALUES.clear();
                for (int i = 0; i < batch; i++) {
                    int at = first + i;
                    BATCH_VALUES.put(at + 1 | faces[at] << FACE_SHIFT).put(cells[at * 3]).put(cells[at * 3 + 1]).put(cells[at * 3 + 2]);
                }
                BATCH_VALUES.flip();
                GL20.glUniform4iv(locTiles, BATCH_VALUES);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, batch * 6);
            }
        } finally {
            LightSurfaces.leavePass();
        }
    }

    private static Level create() {
        Level level = new Level();
        int previousUnit = GlStateManager._getActiveTexture();
        PixelUnpack.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SHADOWS);
        level.shadows = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level.shadows);
        nearest();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, BodyShadowTiles.ATLAS_WIDTH, BodyShadowTiles.ATLAS_HEIGHT, 0, GL11.GL_RED, GL11.GL_FLOAT,
                (ByteBuffer) null);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SLOTS);
        level.slots = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level.slots);
        nearest();
        MemoryUtil.memSet(MemoryUtil.memAddress(SLOT_VALUES), 0, (long) SurfaceAtlas.CAPACITY * Short.BYTES);
        SLOT_VALUES.position(0).limit(SurfaceAtlas.CAPACITY);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16UI, SurfaceAtlas.ACROSS, SurfaceAtlas.ROWS, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, SLOT_VALUES);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        level.framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, level.framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, level.shadows, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        return level;
    }

    private static void nearest() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    private static boolean ensureProgram() {
        if (programCreated) {
            return !programFailed;
        }
        programCreated = true;
        try {
            StringBuilder includes = new StringBuilder();
            for (Identifier include : INCLUDES) {
                includes.append(LightSurfaces.read(include));
            }
            program = LightSurfaces.link(VERTEX, LightSurfaces.read(SHADER).replace(LightSurfaces.INCLUDES, includes.toString()));
        } catch (IOException | IllegalStateException exception) {
            programFailed = true;
            ModConstants.LOGGER.error("[Look] Ombres des corps coupees pour la session : {}", exception.getMessage());
            return false;
        }
        locTiles = GL20.glGetUniformLocation(program, "gtgs_Tiles");
        locCameraBlock = GL20.glGetUniformLocation(program, "gtgs_CameraBlock");
        locNeonCount = GL20.glGetUniformLocation(program, "gtgs_NeonCount");
        locLightBase = GL20.glGetUniformLocation(program, "gtgs_LightBase");
        int block = GL31.glGetUniformBlockIndex(program, "gtgs_NeonBlock");
        if (block != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, block, NeonLights.BINDING);
        }
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_BLOCKS), LightSurfaces.UNIT_BLOCKS);
        GL20.glUseProgram(previousProgram);
        array = GL30.glGenVertexArrays();
        return true;
    }

    /** Fin de partie : textures, framebuffers et programme sont rendus. */
    public static void close() {
        for (int i = 0; i < LEVELS.length; i++) {
            @Nullable Level level = LEVELS[i];
            if (level != null) {
                GL30.glDeleteFramebuffers(level.framebuffer);
                GL11.glDeleteTextures(level.shadows);
                GL11.glDeleteTextures(level.slots);
                LEVELS[i] = null;
            }
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        if (array != 0) {
            GL30.glDeleteVertexArrays(array);
            array = 0;
        }
        programCreated = false;
        programFailed = false;
    }
}
