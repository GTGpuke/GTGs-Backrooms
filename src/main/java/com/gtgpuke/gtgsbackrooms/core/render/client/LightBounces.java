package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Les rebonds traces du palier Ultra : une petite tuile par surface, calculee par rayons dans la grille des blocs (shader
 * {@code bake/bounce.fsh}), a cote du cache de surfaces dont elle relit la lumiere. Une surface qui prend sa tuile calcule tout de
 * suite un premier rebond. Une fois le cache de surfaces au repos, un balayage repasse sur toutes les tuiles, en deux tours : le
 * premier ecrit a part, sur la seule lumiere directe, le second le relit et ecrit le cache lu par le terrain, ce qui fait deux
 * rebonds. Le resultat ne depend donc jamais de ce qui etait la avant, et l'image ne voit jamais d'etat intermediaire : poser un
 * bloc ne fait pas retomber la lumiere autour. Un shader refuse par le pilote coupe les rebonds pour la session : le terrain garde
 * ceux de la zone.
 */
public final class LightBounces {

    /** Unite de texture des rebonds, lue par le terrain. */
    public static final int UNIT_BOUNCES = 13;
    /** Unite de texture des matieres. */
    public static final int UNIT_MATERIALS = 14;
    /** Echantillonneur des rebonds dans les shaders patches. */
    public static final String SAMPLER_BOUNCES = "gtgs_BounceAtlas";
    /** Echantillonneur des matieres dans les shaders patches. */
    public static final String SAMPLER_MATERIALS = "gtgs_Materials";
    private static final int UNIT_PREVIOUS = 15;
    private static final Identifier SHADER = Identifier.of(ModConstants.MOD_ID, "shaders/bake/bounce.fsh");
    private static final int TILE = 8;
    private static final int ATLAS_WIDTH = SurfaceAtlas.ACROSS * TILE;
    private static final int ATLAS_HEIGHT = SurfaceAtlas.ROWS * TILE;
    /** Tuiles calculees par image pendant un balayage. */
    private static final int PER_FRAME = 96;
    /** Ce que valent tous les rebonds, en parts du premier : une surface neuve ne doit pas attendre le balayage pour etre claire. */
    private static final float FRESH_GAIN = 2.2f;
    /** Tours d'un balayage : deux rebonds. */
    private static final int ROUNDS = 2;
    private static final int LAST_ROUND = 1;
    private static final long NONE = Long.MIN_VALUE;
    private static final int MATERIALS = 256;
    private static final int MATERIAL_CHANNELS = 4;
    private static final long[] TILE_CELL = new long[SurfaceAtlas.CAPACITY];
    private static final byte[] TILE_FACE = new byte[SurfaceAtlas.CAPACITY];
    private static final IntArrayList FRESH = new IntArrayList();
    private static final FloatBuffer MATERIAL_VALUES = MemoryUtil.memAllocFloat(MATERIALS * MATERIAL_CHANNELS);
    private static int atlas;
    private static int previous;
    private static int materials;
    private static int framebuffer;
    private static int firstRound;
    private static int program;
    private static int array;
    private static int locOrigin;
    private static int locCell;
    private static int locFace;
    private static int locBase;
    private static int locRays;
    private static int locBounce;
    private static int locTint;
    private static int locPrevious;
    private static int locGain;
    private static int cursor;
    private static int roundsLeft;
    private static int materialCount;
    private static boolean changed;
    private static boolean failed;
    private static boolean created;

    static {
        Arrays.fill(TILE_CELL, NONE);
    }

    private LightBounces() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si les rebonds se tracent : palier Ultra, et aucune erreur. */
    public static boolean active() {
        return !failed && LookResources.quality().traced();
    }

    private static void create() {
        if (created || failed) {
            return;
        }
        created = true;
        try {
            program = LightSurfaces.linkBake(SHADER);
        } catch (IOException | IllegalStateException exception) {
            failed = true;
            ModConstants.LOGGER.error("[Look] Rebonds traces coupes pour la session : {}", exception.getMessage());
            return;
        }
        locOrigin = GL20.glGetUniformLocation(program, "gtgs_TileOrigin");
        locCell = GL20.glGetUniformLocation(program, "gtgs_Cell");
        locFace = GL20.glGetUniformLocation(program, "gtgs_Face");
        locBase = GL20.glGetUniformLocation(program, "gtgs_LightBase");
        locRays = GL20.glGetUniformLocation(program, "gtgs_Rays");
        locBounce = GL20.glGetUniformLocation(program, "gtgs_Bounce");
        locTint = GL20.glGetUniformLocation(program, "gtgs_NeonTint");
        locPrevious = GL20.glGetUniformLocation(program, "gtgs_PreviousShare");
        locGain = GL20.glGetUniformLocation(program, "gtgs_Gain");
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightVolume.SAMPLER), LightVolume.UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_ATLAS), LightSurfaces.UNIT_ATLAS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_TILES), LightSurfaces.UNIT_TILES);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_BLOCKS), LightSurfaces.UNIT_BLOCKS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, SAMPLER_MATERIALS), UNIT_MATERIALS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_BouncePrevious"), UNIT_PREVIOUS);
        GL20.glUseProgram(previousProgram);
        int previousUnit = GlStateManager._getActiveTexture();
        PixelUnpack.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BOUNCES);
        atlas = texture(GL11.GL_LINEAR, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, GL11.GL_UNSIGNED_BYTE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        previous = texture(GL11.GL_LINEAR, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, GL11.GL_UNSIGNED_BYTE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        materials = texture(GL11.GL_NEAREST, GL30.GL_RGBA32F, MATERIALS, 1, GL11.GL_FLOAT);
        GlStateManager._activeTexture(previousUnit);
        PixelUnpack.end();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, atlas, 0);
        clearAtlas();
        firstRound = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, firstRound);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, previous, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        array = GL30.glGenVertexArrays();
    }

    private static int texture(int filter, int format, int width, int height, int type) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        return texture;
    }

    /** Le cache des rebonds vide : le terrain garde ceux de la zone, sur toutes ses surfaces. */
    private static void clearAtlas() {
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    /** Oublie tout : autre zone, autre hauteur, ou fin de partie. */
    static void reset() {
        Arrays.fill(TILE_CELL, NONE);
        FRESH.clear();
        cursor = 0;
        roundsLeft = 0;
        changed = false;
        materialCount = 0;
        if (created && !failed) {
            int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            clearAtlas();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    /**
     * Une tuile du cache de surfaces vient d'etre calculee : ce qu'elle renvoie a change.
     *
     * @param tile tuile
     * @param x cellule d'air devant la surface
     * @param y cellule d'air devant la surface
     * @param z cellule d'air devant la surface
     * @param face face de la surface
     * @param fresh {@code true} si la surface n'avait pas encore de valeur : son rebond se calcule dans la meme image
     */
    static void tileBaked(int tile, int x, int y, int z, int face, boolean fresh) {
        TILE_CELL[tile] = BlockPos.asLong(x, y, z);
        TILE_FACE[tile] = (byte) face;
        changed = true;
        if (fresh) {
            FRESH.add(tile);
        }
    }

    /** Une section cuite arrive : les rayons qui sortent des tuiles lisent la texture de zone, et ce qu'ils y trouvent a change. */
    static void touch() {
        changed = true;
    }

    /**
     * Fin du travail d'une image sur le cache de surfaces : les rebonds qui doivent suivre.
     *
     * @param settings reglages de la zone
     * @param look profil de sa dimension
     * @param blocks copie du monde
     * @param base premiere section de la zone
     * @param idle {@code true} si plus rien n'attend dans le cache de surfaces
     */
    static void frame(LightBakeSettings settings, LookProfile look, LightBlocks blocks, int base, boolean idle) {
        if (!active()) {
            FRESH.clear();
            changed = false;
            return;
        }
        create();
        if (failed) {
            return;
        }
        uploadMaterials(blocks);
        bindForShaders();
        if (idle && changed && roundsLeft == 0) {
            changed = false;
            cursor = 1;
            roundsLeft = ROUNDS;
        }
        if (FRESH.isEmpty() && roundsLeft == 0) {
            return;
        }
        LightSurfaces.enterPass(framebuffer);
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL20.glUniform1i(locBase, base << 4);
            GL20.glUniform1i(locRays, LookResources.quality().tracedRays());
            GL20.glUniform1f(locBounce, look.bakedLight().bounce());
            LookProfile.Neons neons = look.neons();
            float strongest = Math.max(Math.max(neons.red(), Math.max(neons.green(), neons.blue())), 1.0e-3f);
            GL20.glUniform3f(locTint, neons.red() / strongest, neons.green() / strongest, neons.blue() / strongest);
            aim(framebuffer, previous, 0.0f);
            GL20.glUniform1f(locGain, FRESH_GAIN);
            for (int i = 0; i < FRESH.size(); i++) {
                gather(FRESH.getInt(i));
            }
            FRESH.clear();
            GL20.glUniform1f(locGain, 1.0f);
            sweep();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        } finally {
            LightSurfaces.leavePass();
        }
    }

    /** Ou ecrit ce qui suit, et ce qu'il relit : jamais la texture qu'il ecrit. */
    private static void aim(int target, int read, float previousShare) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, read);
        GL20.glUniform1f(locPrevious, previousShare);
    }

    private static void aimRound() {
        if (roundsLeft == LAST_ROUND) {
            aim(framebuffer, previous, 1.0f);
        } else {
            aim(firstRound, atlas, 0.0f);
        }
    }

    /** Quelques tuiles du balayage en cours : le premier tour ecrit a part, le dernier ecrit ce que le terrain lit. */
    private static void sweep() {
        int budget = PER_FRAME;
        if (roundsLeft > 0) {
            aimRound();
        }
        while (roundsLeft > 0 && budget > 0) {
            while (cursor < SurfaceAtlas.CAPACITY && budget > 0) {
                if (TILE_CELL[cursor] != NONE) {
                    gather(cursor);
                    budget--;
                }
                cursor++;
            }
            if (cursor == SurfaceAtlas.CAPACITY) {
                cursor = 1;
                roundsLeft--;
                if (roundsLeft > 0) {
                    aimRound();
                }
            }
        }
    }

    private static void gather(int tile) {
        long cell = TILE_CELL[tile];
        int originX = (tile % SurfaceAtlas.ACROSS) * TILE;
        int originY = (tile / SurfaceAtlas.ACROSS) * TILE;
        GL11.glViewport(originX, originY, TILE, TILE);
        GL20.glUniform2i(locOrigin, originX, originY);
        GL20.glUniform3i(locCell, BlockPos.unpackLongX(cell), BlockPos.unpackLongY(cell), BlockPos.unpackLongZ(cell));
        GL20.glUniform1i(locFace, TILE_FACE[tile]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** Les matieres connues, a chaque nouvelle : couleur moyenne et 1 si elle emet. */
    private static void uploadMaterials(LightBlocks blocks) {
        boolean[] emissive = blocks.emissive();
        if (emissive.length == materialCount) {
            return;
        }
        materialCount = emissive.length;
        float[] albedos = blocks.albedos();
        MATERIAL_VALUES.clear();
        for (int row = 0; row < MATERIALS; row++) {
            boolean known = row < materialCount;
            MATERIAL_VALUES.put(known ? albedos[row * 3] : 0.0f).put(known ? albedos[row * 3 + 1] : 0.0f).put(known ? albedos[row * 3 + 2] : 0.0f)
                    .put(known && emissive[row] ? 1.0f : 0.0f);
        }
        MATERIAL_VALUES.flip();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, materials);
        PixelUnpack.begin();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, MATERIALS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, MATERIAL_VALUES);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /** Rebonds et matieres a leurs unites : un rechargement a pu les changer. */
    private static void bindForShaders() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BOUNCES);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, materials);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        GlStateManager._activeTexture(previousUnit);
    }

    /** {@code true} pendant le premier tour d'un balayage, qui n'ecrit rien de visible (tests du client). */
    public static boolean inFirstRound() {
        return roundsLeft == ROUNDS;
    }

    /** {@code true} si rien n'attend : tuiles neuves, balayage (tests du client). */
    public static boolean settled() {
        return FRESH.isEmpty() && !changed && roundsLeft == 0;
    }

    /** L'empreinte d'une tuile dans le cache relu. */
    static long tilePrint(byte[] pixels, int tile) {
        int originX = (tile % SurfaceAtlas.ACROSS) * TILE;
        int originY = (tile / SurfaceAtlas.ACROSS) * TILE;
        long print = 1125899906842597L;
        for (int y = 0; y < TILE; y++) {
            int from = ((originY + y) * ATLAS_WIDTH + originX) * 4;
            for (int i = 0; i < TILE * 4; i++) {
                print = print * 31L + pixels[from + i];
            }
        }
        return print;
    }

    /** La moyenne du vert d'une tuile dans le cache relu. */
    static double tileMean(byte[] pixels, int tile) {
        int originX = (tile % SurfaceAtlas.ACROSS) * TILE;
        int originY = (tile / SurfaceAtlas.ACROSS) * TILE;
        long sum = 0L;
        for (int y = 0; y < TILE; y++) {
            int from = ((originY + y) * ATLAS_WIDTH + originX) * 4;
            for (int x = 0; x < TILE; x++) {
                sum += pixels[from + x * 4 + 1] & 0xFF;
            }
        }
        return sum / (double) (TILE * TILE);
    }

    /**
     * Le cache des rebonds, relu sur le processeur (tests du client).
     *
     * @return quatre octets par pixel
     */
    public static byte[] read() {
        return read(atlas);
    }

    /**
     * Le premier tour du dernier calcul, relu sur le processeur (tests du client).
     *
     * @return quatre octets par pixel
     */
    public static byte[] readFirstRound() {
        return read(previous);
    }

    private static byte[] read(int texture) {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        try {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            PixelUnpack.begin();
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            PixelUnpack.end();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GlStateManager._activeTexture(previousUnit);
            byte[] copy = new byte[pixels.remaining()];
            pixels.get(copy);
            return copy;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
