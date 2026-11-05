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
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.TileBudget;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Les rebonds traces du palier Ultra : une petite tuile par surface, calculee par rayons dans la grille des blocs (shader
 * {@code bake/bounce.fsh}), a cote du cache de surfaces dont elle relit la lumiere. Une surface qui prend sa tuile calcule tout de
 * suite un premier rebond. Une fois le cache de surfaces au repos, un balayage repasse sur toutes les tuiles, de la plus proche de la
 * camera a la plus loin, en deux tours : le premier ecrit a part, sur la seule lumiere directe, le second le relit et ecrit le cache lu
 * par le terrain, ce qui fait deux rebonds. Le resultat ne depend donc jamais de ce qui etait la avant, et l'image ne voit jamais d'etat
 * intermediaire : poser un bloc ne fait pas retomber la lumiere autour. Les rayons portent a 24 blocs et le second tour relit le
 * premier : un changement touche tout le cache, ou presque, et c'est le temps permis par image qui fait la vitesse du balayage. Un shader
 * refuse par le pilote coupe les rebonds pour la session : le terrain garde ceux de la zone.
 *
 * <p>Un jeu de rebonds par cache de surfaces ({@link LightSurfaces#PLAYER}, {@link LightSurfaces#VIEW}) : le programme est commun, chacun a
 * son temps permis par image, et celui d'une vue ne balaie que lorsque celui du joueur est au repos.
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
    /** Unite de texture de ce qui sort des passages, lue par la seule passe des rebonds. */
    private static final int UNIT_PASSAGES = 20;
    /** Texels de la mesure d'un passage par ligne : une direction et un point chacun ; deux lignes par cadre. */
    private static final int PASSAGE_TEXELS = LightZone.PASSAGE_DIRECTIONS * LightZone.PASSAGE_POINTS;
    private static final Identifier SHADER = Identifier.of(ModConstants.MOD_ID, "shaders/bake/bounce.fsh");
    private static final int TILE = 8;
    private static final int ATLAS_WIDTH = SurfaceAtlas.ACROSS * TILE;
    private static final int ATLAS_HEIGHT = SurfaceAtlas.ROWS * TILE;
    /** Distances au centre que distingue l'ordre d'un balayage, en blocs : au-dela, les surfaces passent ensemble, en dernier. */
    private static final int ORDER_DISTANCES = 128;
    /** Tours d'un balayage : deux rebonds. */
    private static final int ROUNDS = 2;
    private static final int LAST_ROUND = 1;
    private static final long NONE = Long.MIN_VALUE;
    private static final int MATERIALS = 256;
    private static final int MATERIAL_CHANNELS = 4;
    private static final FloatBuffer MATERIAL_VALUES = MemoryUtil.memAllocFloat(MATERIALS * MATERIAL_CHANNELS);
    /** Cadres des portails pour les rayons, et ce qui sort de chaque passage, par direction ({@link LightZone#passageLight}). */
    private static final float[] FRAME_VALUES = new float[PortalLightUniforms.FRAMES * PortalLightUniforms.FRAME_FLOATS];
    private static final float[] SEEN_VALUES = new float[PortalLightUniforms.SEEN * PortalLightUniforms.SEEN_FLOATS];
    private static final FloatBuffer FRAMES = MemoryUtil.memAllocFloat(FRAME_VALUES.length);
    /** La mesure d'un passage, ses deux lignes : la lumiere directe, puis elle et un rebond, par direction et par point. */
    private static final FloatBuffer PASSAGE_VALUES = MemoryUtil.memAllocFloat(PASSAGE_TEXELS * 2 * 4);
    /** Le drapeau d'un passage dont la sortie n'est pas encore mesuree, pour {@code bounce.fsh}. */
    private static final float UNMEASURED = 2.0f;
    private static int program;
    private static int array;
    private static int locOrigin;
    private static int locCell;
    private static int locFace;
    private static int locBase;
    private static int locRays;
    private static int locBounce;
    private static int locBounceColor;
    private static int locTint;
    private static int locPrevious;
    private static int locFresh;
    private static int locPortalOrigin;
    private static int locFrameCount;
    private static int locFrames;
    private static boolean programCreated;
    private static boolean programFailed;

    private final long[] tileCell = new long[SurfaceAtlas.CAPACITY];
    private final byte[] tileFace = new byte[SurfaceAtlas.CAPACITY];
    private final IntArrayList fresh = new IntArrayList();
    /** Les tuiles du balayage en cours, de la plus proche du centre a la plus loin. */
    private final int[] order = new int[SurfaceAtlas.CAPACITY];
    /** Debut de chaque distance dans l'ordre, pendant son calcul. */
    private final int[] orderStarts = new int[ORDER_DISTANCES + 1];
    private int orderCount;
    private int atlas;
    private int previous;
    private int materials;
    private int framebuffer;
    private int firstRound;
    private int passages;
    private int cursor;
    private int roundsLeft;
    private int materialCount;
    private boolean changed;
    private boolean failed;
    private boolean created;
    /** {@code true} si une image a servi les rebonds depuis la derniere revue ({@link #releaseIdle}). */
    private boolean used;
    /** Derniere revue ou les rebonds servaient, en millisecondes. */
    private long usedAt;
    /** {@code true} si ce jeu a calcule des tuiles a l'image en cours. */
    private boolean worked;

    /** Temps permis par image a ces rebonds, processeur et carte graphique. */
    private final TileBudget budget;
    private final TileClock clock;

    /** @param milliseconds temps permis par image, en millisecondes */
    LightBounces(float milliseconds) {
        this.budget = new TileBudget(milliseconds);
        this.clock = new TileClock(this.budget);
        Arrays.fill(this.tileCell, NONE);
    }

    /** {@code true} si les rebonds se tracent : palier Ultra, et aucune erreur. */
    public static boolean active() {
        return !programFailed && LookResources.quality().traced();
    }

    /** Le programme des rebonds, une fois pour les deux jeux ; {@code false} s'il est refuse. */
    private static boolean ensureProgram() {
        if (programCreated) {
            return !programFailed;
        }
        programCreated = true;
        try {
            program = LightSurfaces.linkBake(SHADER);
        } catch (IOException | IllegalStateException exception) {
            programFailed = true;
            ModConstants.LOGGER.error("[Look] Rebonds traces coupes pour la session : {}", exception.getMessage());
            return false;
        }
        locOrigin = GL20.glGetUniformLocation(program, "gtgs_TileOrigin");
        locCell = GL20.glGetUniformLocation(program, "gtgs_Cell");
        locFace = GL20.glGetUniformLocation(program, "gtgs_Face");
        locBase = GL20.glGetUniformLocation(program, "gtgs_LightBase");
        locRays = GL20.glGetUniformLocation(program, "gtgs_Rays");
        locBounce = GL20.glGetUniformLocation(program, "gtgs_Bounce");
        locBounceColor = GL20.glGetUniformLocation(program, "gtgs_BounceColor");
        locTint = GL20.glGetUniformLocation(program, "gtgs_NeonTint");
        locPrevious = GL20.glGetUniformLocation(program, "gtgs_PreviousShare");
        locFresh = GL20.glGetUniformLocation(program, "gtgs_Fresh");
        locPortalOrigin = GL20.glGetUniformLocation(program, "gtgs_PortalOrigin");
        locFrameCount = GL20.glGetUniformLocation(program, "gtgs_FrameCount");
        locFrames = GL20.glGetUniformLocation(program, "gtgs_Frames");
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightVolume.SAMPLER), LightVolume.UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_ATLAS), LightSurfaces.UNIT_ATLAS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_TILES), LightSurfaces.UNIT_TILES);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_BLOCKS), LightSurfaces.UNIT_BLOCKS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, SAMPLER_MATERIALS), UNIT_MATERIALS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_BouncePrevious"), UNIT_PREVIOUS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_Passages"), UNIT_PASSAGES);
        GL20.glUseProgram(previousProgram);
        array = GL30.glGenVertexArrays();
        return true;
    }

    private void create() {
        if (this.created || this.failed) {
            return;
        }
        this.created = true;
        if (!ensureProgram()) {
            this.failed = true;
            return;
        }
        int previousUnit = GlStateManager._getActiveTexture();
        PixelUnpack.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BOUNCES);
        this.atlas = texture(GL11.GL_LINEAR, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, GL11.GL_UNSIGNED_BYTE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        this.previous = texture(GL11.GL_LINEAR, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, GL11.GL_UNSIGNED_BYTE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        this.materials = texture(GL11.GL_NEAREST, GL30.GL_RGBA32F, MATERIALS, 1, GL11.GL_FLOAT);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PASSAGES);
        this.passages = texture(GL11.GL_NEAREST, GL30.GL_RGBA32F, PASSAGE_TEXELS, PortalLightUniforms.FRAMES * 2, GL11.GL_FLOAT);
        GlStateManager._activeTexture(previousUnit);
        PixelUnpack.end();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        this.framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.atlas, 0);
        clearAtlas();
        this.firstRound = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.firstRound);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.previous, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
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
    void reset() {
        clearState();
        if (this.created && !this.failed) {
            int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            clearAtlas();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private void clearState() {
        Arrays.fill(this.tileCell, NONE);
        this.fresh.clear();
        this.cursor = 0;
        this.roundsLeft = 0;
        this.changed = false;
        this.materialCount = 0;
    }

    /**
     * Rend ses textures et ses framebuffers a la carte graphique, avec son cache de surfaces : tout se recree a la prochaine image qui
     * en a besoin. Un shader refuse reste coupe pour la session.
     */
    void freeTextures() {
        clearState();
        deleteObjects();
    }

    /** {@code true} si ses textures sont sur la carte graphique. */
    boolean allocated() {
        return this.created && !this.failed;
    }

    /**
     * Une fois par image, son cache de surfaces servi : des rebonds qu'aucune image n'a traces depuis {@link LightVolume#RELEASE_MS}
     * (un palier sans rebonds traces) rendent leurs textures. Les surfaces qu'ils connaissent restent : recrees, ils les retracent
     * toutes, par un balayage.
     *
     * @param now instant de l'image, en millisecondes
     */
    void releaseIdle(long now) {
        if (this.used) {
            this.used = false;
            this.usedAt = now;
        } else if (allocated() && now - this.usedAt > LightVolume.RELEASE_MS) {
            deleteObjects();
            this.fresh.clear();
            this.cursor = 0;
            this.roundsLeft = 0;
            this.changed = true;
        }
    }

    private void deleteObjects() {
        if (!this.created || this.failed) {
            return;
        }
        GL30.glDeleteFramebuffers(this.framebuffer);
        GL30.glDeleteFramebuffers(this.firstRound);
        GL11.glDeleteTextures(this.atlas);
        GL11.glDeleteTextures(this.previous);
        GL11.glDeleteTextures(this.materials);
        GL11.glDeleteTextures(this.passages);
        this.passages = 0;
        this.framebuffer = 0;
        this.firstRound = 0;
        this.atlas = 0;
        this.previous = 0;
        this.materials = 0;
        this.materialCount = 0;
        this.created = false;
    }

    /**
     * Une tuile du cache de surfaces vient d'etre calculee : ce qu'elle renvoie a change.
     *
     * @param tile tuile
     * @param x cellule d'air devant la surface
     * @param y cellule d'air devant la surface
     * @param z cellule d'air devant la surface
     * @param face face de la surface
     * @param first {@code true} si la surface n'avait pas encore de valeur : son rebond se calcule dans la meme image
     */
    void tileBaked(int tile, int x, int y, int z, int face, boolean first) {
        this.tileCell[tile] = BlockPos.asLong(x, y, z);
        this.tileFace[tile] = (byte) face;
        this.changed = true;
        if (first) {
            this.fresh.add(tile);
        }
    }

    /** Une section cuite arrive : les rayons qui sortent des tuiles lisent la texture de zone, et ce qu'ils y trouvent a change. */
    void touch() {
        this.changed = true;
    }

    /**
     * Fin du travail d'une image sur le cache de surfaces : les rebonds qui doivent suivre.
     *
     * @param zone zone de ce cache : ses reglages, sa copie du monde, ses portails et ce qui sort de ses passages
     * @param look profil de sa dimension
     * @param base premiere section de la zone
     * @param origin bloc d'origine des positions des cadres : x, y, z
     * @param idle {@code true} si plus rien n'attend dans le cache de surfaces
     * @param turn {@code true} si ce jeu peut balayer a cette image : celui d'une vue attend que celui du joueur soit au repos
     * @param centerX bloc d'ou part l'ordre d'un balayage
     * @param centerY bloc d'ou part l'ordre d'un balayage
     * @param centerZ bloc d'ou part l'ordre d'un balayage
     */
    void frame(LightZone zone, LookProfile look, int base, int[] origin, boolean idle, boolean turn, int centerX, int centerY, int centerZ) {
        LightBlocks blocks = zone.blocks();
        if (!active()) {
            this.fresh.clear();
            this.changed = false;
            return;
        }
        create();
        if (this.failed) {
            return;
        }
        this.used = true;
        uploadMaterials(blocks);
        bindForShaders();
        if (idle && turn && this.changed && this.roundsLeft == 0) {
            this.changed = false;
            this.cursor = 0;
            this.roundsLeft = ROUNDS;
            order(centerX, centerY, centerZ);
        }
        if (this.fresh.isEmpty() && (this.roundsLeft == 0 || !turn)) {
            return;
        }
        this.worked = true;
        LightSurfaces.enterPass(this.framebuffer);
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL20.glUniform1i(locBase, base << 4);
            GL20.glUniform1i(locRays, LookResources.quality().tracedRays());
            GL20.glUniform1f(locBounce, look.bakedLight().bounce());
            Vec3d bounceColor = look.bakedLight().bounceColor();
            GL20.glUniform3f(locBounceColor, (float) bounceColor.x, (float) bounceColor.y, (float) bounceColor.z);
            LookProfile.Neons neons = look.neons();
            float strongest = Math.max(Math.max(neons.red(), Math.max(neons.green(), neons.blue())), 1.0e-3f);
            GL20.glUniform3f(locTint, neons.red() / strongest, neons.green() / strongest, neons.blue() / strongest);
            portals(zone, origin);
            this.clock.begin();
            aim(this.framebuffer, this.previous, 0.0f);
            GL20.glUniform1f(locFresh, 1.0f);
            int first = this.fresh.size();
            for (int i = 0; i < first; i++) {
                gather(this.fresh.getInt(i));
            }
            this.fresh.clear();
            GL20.glUniform1f(locFresh, 0.0f);
            this.clock.end(first + sweep(turn ? this.budget.tiles() : 0));
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.previous);
        } finally {
            LightSurfaces.leavePass();
        }
    }

    /**
     * Les cadres des portails a portee de la zone des tuiles, et ce qui sort de chaque passage, par direction et par point du cadre : la
     * lumiere directe de l'autre cote pour le premier tour, elle et un rebond pour le second. Un passage pas encore
     * mesure laisse passer les rayons. La mesure de chaque passage mesure a portee part vers les deux lignes de sa place a chaque
     * passe (8 Kio), et la passe ne lit que celles-la : rien ne peut y rester d'une mesure, ou d'un rangement des cadres, d'avant.
     */
    private void portals(LightZone zone, int[] origin) {
        PortalOpenings openings = zone.openings();
        LightZone.PassageLight measured = zone.passageLight();
        float[] light = measured != null && measured.openings() == openings ? measured.values() : null;
        PortalLightUniforms.pack(openings, origin, origin[0] + 8.0, origin[1] + 16.0, origin[2] + 8.0, LightSurfaces.FRAME_RANGE, FRAME_VALUES, SEEN_VALUES);
        int frames = PortalLightUniforms.frames();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PASSAGES);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.passages);
        for (int slot = 0; slot < frames; slot++) {
            int flag = slot * PortalLightUniforms.FRAME_FLOATS + 7;
            if (FRAME_VALUES[flag] < 0.5f) {
                continue;
            }
            if (light == null) {
                // Pas encore mesure : le cadre laisse passer les rayons comme s'il n'etait pas la, plutot que de les arreter.
                FRAME_VALUES[flag] = UNMEASURED;
            } else {
                sendPassage(light, PortalLightUniforms.frameOpening(slot), slot);
            }
        }
        GlStateManager._activeTexture(previousUnit);
        FRAMES.clear().put(FRAME_VALUES).flip();
        GL20.glUniform3i(locPortalOrigin, origin[0], origin[1], origin[2]);
        GL20.glUniform1i(locFrameCount, frames);
        GL20.glUniform4fv(locFrames, FRAMES);
    }

    /**
     * La mesure d'un passage dans les deux lignes de la place de son cadre, dans la texture liee : une direction et un point par texel, la
     * lumiere directe, puis elle et un rebond.
     */
    private static void sendPassage(float[] light, int opening, int slot) {
        PASSAGE_VALUES.clear();
        for (int line = 0; line < 2; line++) {
            for (int sample = 0; sample < PASSAGE_TEXELS; sample++) {
                int at = opening * LightZone.PASSAGE_VALUES + sample * LightZone.PASSAGE_SAMPLE + line * 3;
                PASSAGE_VALUES.put(light[at]).put(light[at + 1]).put(light[at + 2]).put(0.0f);
            }
        }
        PASSAGE_VALUES.flip();
        PixelUnpack.begin();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, slot * 2, PASSAGE_TEXELS, 2, GL11.GL_RGBA, GL11.GL_FLOAT, PASSAGE_VALUES);
        PixelUnpack.end();
    }

    /** Ou ecrit ce qui suit, et ce qu'il relit : jamais la texture qu'il ecrit. */
    private static void aim(int target, int read, float previousShare) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, read);
        GL20.glUniform1f(locPrevious, previousShare);
    }

    private void aimRound() {
        if (this.roundsLeft == LAST_ROUND) {
            aim(this.framebuffer, this.previous, 1.0f);
        } else {
            aim(this.firstRound, this.atlas, 0.0f);
        }
    }

    /**
     * Les tuiles tenues, de la plus proche du centre a la plus loin, au bloc pres : le balayage les suit dans cet ordre, a chacun de ses
     * tours. Un tri par distance entiere, dans des tableaux alloues une fois : le tri du JDK peut allouer un tampon.
     */
    private void order(int centerX, int centerY, int centerZ) {
        Arrays.fill(this.orderStarts, 0);
        for (int tile = 1; tile < SurfaceAtlas.CAPACITY; tile++) {
            if (this.tileCell[tile] != NONE) {
                this.orderStarts[distance(this.tileCell[tile], centerX, centerY, centerZ) + 1]++;
            }
        }
        for (int i = 1; i <= ORDER_DISTANCES; i++) {
            this.orderStarts[i] += this.orderStarts[i - 1];
        }
        this.orderCount = this.orderStarts[ORDER_DISTANCES];
        for (int tile = 1; tile < SurfaceAtlas.CAPACITY; tile++) {
            if (this.tileCell[tile] != NONE) {
                this.order[this.orderStarts[distance(this.tileCell[tile], centerX, centerY, centerZ)]++] = tile;
            }
        }
    }

    /** Distance d'une surface au centre, en blocs entiers, les plus loin ensemble. */
    private static int distance(long cell, int centerX, int centerY, int centerZ) {
        double dx = BlockPos.unpackLongX(cell) - centerX;
        double dy = BlockPos.unpackLongY(cell) - centerY;
        double dz = BlockPos.unpackLongZ(cell) - centerZ;
        return Math.min(ORDER_DISTANCES - 1, (int) Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * Des tuiles du balayage en cours, autant que le budget : le premier tour ecrit a part, le dernier ecrit ce que le terrain lit.
     *
     * @return tuiles calculees
     */
    private int sweep(int budget) {
        int done = 0;
        if (this.roundsLeft > 0 && budget > 0) {
            aimRound();
        }
        while (this.roundsLeft > 0 && done < budget) {
            while (this.cursor < this.orderCount && done < budget) {
                int tile = this.order[this.cursor];
                if (this.tileCell[tile] != NONE) {
                    gather(tile);
                    done++;
                }
                this.cursor++;
            }
            if (this.cursor == this.orderCount) {
                this.cursor = 0;
                this.roundsLeft--;
                if (this.roundsLeft > 0) {
                    aimRound();
                }
            }
        }
        return done;
    }

    private void gather(int tile) {
        long cell = this.tileCell[tile];
        int originX = (tile % SurfaceAtlas.ACROSS) * TILE;
        int originY = (tile / SurfaceAtlas.ACROSS) * TILE;
        GL11.glViewport(originX, originY, TILE, TILE);
        GL20.glUniform2i(locOrigin, originX, originY);
        GL20.glUniform3i(locCell, BlockPos.unpackLongX(cell), BlockPos.unpackLongY(cell), BlockPos.unpackLongZ(cell));
        GL20.glUniform1i(locFace, this.tileFace[tile]);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** Les matieres connues, a chaque nouvelle : couleur moyenne et 1 si elle emet. */
    private void uploadMaterials(LightBlocks blocks) {
        boolean[] emissive = blocks.emissive();
        if (emissive.length == this.materialCount) {
            return;
        }
        this.materialCount = emissive.length;
        float[] albedos = blocks.albedos();
        MATERIAL_VALUES.clear();
        for (int row = 0; row < MATERIALS; row++) {
            boolean known = row < this.materialCount;
            MATERIAL_VALUES.put(known ? albedos[row * 3] : 0.0f).put(known ? albedos[row * 3 + 1] : 0.0f).put(known ? albedos[row * 3 + 2] : 0.0f)
                    .put(known && emissive[row] ? 1.0f : 0.0f);
        }
        MATERIAL_VALUES.flip();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.materials);
        PixelUnpack.begin();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, MATERIALS, 1, GL11.GL_RGBA, GL11.GL_FLOAT, MATERIAL_VALUES);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /** Lie rebonds et matieres a leurs unites, pour les dessins qui suivent. */
    void bind() {
        if (this.created && !this.failed) {
            bindForShaders();
        }
    }

    /** Rebonds et matieres a leurs unites : un rechargement a pu les changer. */
    private void bindForShaders() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BOUNCES);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.atlas);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_MATERIALS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.materials);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.previous);
        GlStateManager._activeTexture(previousUnit);
    }

    /** {@code true} si rien n'attend : tuiles neuves, balayage. */
    boolean idle() {
        return this.fresh.isEmpty() && !this.changed && this.roundsLeft == 0;
    }

    /** Debut d'une image : rien n'est encore calcule. */
    void rest() {
        this.worked = false;
    }

    /** {@code true} si ce jeu a calcule des tuiles a l'image en cours. */
    boolean worked() {
        return this.worked;
    }

    /** {@code true} pendant le premier tour d'un balayage des rebonds du joueur, qui n'ecrit rien de visible (tests du client). */
    public static boolean inFirstRound() {
        return LightSurfaces.PLAYER.bounces.roundsLeft == ROUNDS;
    }

    /** {@code true} si rien n'attend dans les rebonds du joueur : tuiles neuves, balayage (tests du client). */
    public static boolean settled() {
        return LightSurfaces.PLAYER.bounces.idle();
    }

    /** {@code true} si rien n'attend dans les rebonds de la vue (tests du client). */
    public static boolean viewSettled() {
        return LightSurfaces.VIEW.bounces.idle();
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
     * Les rebonds du joueur, relus sur le processeur (tests du client).
     *
     * @return quatre octets par pixel
     */
    public static byte[] read() {
        return LightSurfaces.PLAYER.bounces.readTraced();
    }

    /**
     * Le premier tour du dernier calcul des rebonds du joueur, relu sur le processeur (tests du client).
     *
     * @return quatre octets par pixel
     */
    public static byte[] readFirstRound() {
        LightBounces player = LightSurfaces.PLAYER.bounces;
        return player.read(player.previous);
    }

    /** Ce jeu de rebonds, relu sur le processeur. */
    byte[] readTraced() {
        return read(this.atlas);
    }

    private byte[] read(int texture) {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        try {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_PREVIOUS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            PixelUnpack.begin();
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            PixelUnpack.end();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.previous);
            GlStateManager._activeTexture(previousUnit);
            byte[] copy = new byte[pixels.remaining()];
            pixels.get(copy);
            return copy;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
