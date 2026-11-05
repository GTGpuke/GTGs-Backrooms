package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.TileBudget;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightCell;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Le cache de surfaces d'une zone de lumiere : une lightmap d'un pixel par pixel de texture, calculee par le processeur graphique,
 * quelques tuiles par image (shader {@code bake/surface.fsh}) avec la grille des blocs de la zone en texture 3D. La tuile d'une surface
 * se retrouve par une texture d'indirection, de la forme de la texture de zone : les shaders du terrain y lisent en un acces la lumiere
 * qui arrive a chaque pixel, la part de ses dalles qu'il voit, ce que les blocs proches lui cachent et son relief. Une tuile se calcule
 * d'un seul dessin, et seulement quand ce qu'elle voit change. Elle ne se calcule que si la grille des blocs est a jour autour d'elle :
 * ses ombres ne s'ecrivent jamais d'apres d'autres blocs. Un shader refuse par le pilote coupe les caches pour la session : le terrain
 * garde la texture de zone.
 *
 * <p>Deux caches, chacun avec ses tuiles, sa grille et ses rebonds ({@link LightBounces}) : celui de la zone du joueur ({@link #PLAYER})
 * et celui de la zone d'une vue de portail ({@link #VIEW}), pour qu'une vue ait la qualite choisie. Ils partagent le programme et les
 * unites de texture (chaque vue lie les siens en entrant) ; chacun a son temps permis par image, plus court pour la vue, qui ne calcule
 * que lorsque celui du joueur n'a rien en attente.
 */
public final class LightSurfaces {

    /** Unite de texture du cache. */
    public static final int UNIT_ATLAS = 9;
    /** Unite de texture de l'indirection. */
    public static final int UNIT_TILES = 10;
    /** Echantillonneur du cache dans les shaders patches. */
    public static final String SAMPLER_ATLAS = "gtgs_SurfaceAtlas";
    /** Echantillonneur de l'indirection dans les shaders patches. */
    public static final String SAMPLER_TILES = "gtgs_SurfaceTiles";
    /** Unite de texture de la grille des blocs. */
    public static final int UNIT_BLOCKS = 11;
    /** Echantillonneur de la grille des blocs dans les shaders patches. */
    public static final String SAMPLER_BLOCKS = "gtgs_Blocks";
    /** Unite de texture de l'atlas des blocs. */
    public static final int UNIT_SPRITES = 12;
    /**
     * Temps permis par image au calcul des tuiles, en millisecondes, processeur et carte graphique, pour le cache du joueur : le coeur du
     * cout du cache, qui ne dure que le temps de remplir les surfaces proches ou de suivre un bloc change. Un nombre fixe de tuiles
     * prenait une demi-seconde a suivre un bloc pose sous une dalle, sans que la carte s'en apercoive.
     */
    private static final float TILE_MILLISECONDS = 1.5f;
    /**
     * Celui du cache d'une vue, et de ses rebonds, qui ne prennent que les images ou ceux du joueur n'ont rien a faire : la vue se
     * remplit moins vite, et une image ou le joueur est au repos ne coute pas plus d'une demi-milliseconde de plus.
     */
    private static final float VIEW_MILLISECONDS = 0.5f;
    /** Rayon des sections qui ont des tuiles, autour de l'ancrage : celui ou les shaders font fondre les tuiles dans la zone. */
    private static final int TILE_RADIUS = 2;
    /** Le cache de la zone du joueur. */
    static final LightSurfaces PLAYER = new LightSurfaces(TILE_MILLISECONDS);
    /** Le cache de la zone d'une vue de portail. */
    static final LightSurfaces VIEW = new LightSurfaces(VIEW_MILLISECONDS);
    private static final Identifier SHADER = Identifier.of(ModConstants.MOD_ID, "shaders/bake/surface.fsh");
    private static final Identifier AREA_LIGHT = Identifier.of(ModConstants.MOD_ID, "shaders/include/area_light.glsl");
    private static final Identifier BLOCK_MARCH = Identifier.of(ModConstants.MOD_ID, "shaders/include/block_march.glsl");
    private static final Identifier SURFACE_LIGHT = Identifier.of(ModConstants.MOD_ID, "shaders/include/surface_light.glsl");
    /** La ligne d'un shader de calcul que remplacent ses bibliotheques : hors des shaders vanilla, rien ne resout {@code #moj_import}. */
    static final String INCLUDES = "#pragma gtgs_includes";
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final int ATLAS_WIDTH = SurfaceAtlas.ACROSS * SurfaceAtlas.TILE;
    private static final int ATLAS_HEIGHT = SurfaceAtlas.ROWS * SurfaceAtlas.TILE;
    private static final int SECTION = 16;
    private static final int SLOTS_ACROSS = LightVolume.WIDTH / SECTION;
    private static final int GRID_SLOTS = SLOTS_ACROSS * SLOTS_ACROSS * LightZone.HEIGHT;
    /** Sections de la grille des blocs envoyees par image. */
    private static final int GRID_PER_FRAME = 8;
    /** Hauteur retenue de part et d'autre de la camera, en blocs : un layer et ce qui le borde. */
    private static final int TILE_BAND = 7;
    /** Neons d'une tuile, au plus : ce que declare son shader. */
    private static final int TILE_LIGHTS = 24;
    /** Blocs changes traites par image un par un ; au-dela, leurs sections repartent entieres. */
    private static final int BLOCKS_PER_FRAME = 32;
    /** Moitie de la diagonale d'une face et du cote d'une dalle : ce qui peut encore eclairer une tuile hors de portee de son centre. */
    private static final float TILE_MARGIN = 1.5f;
    private static final float CELL_CENTER = 0.5f;
    private static final int LIGHT_INTS = 4;
    private static final int ORDER_SHIFT = 32;
    private static final long NONE = Long.MIN_VALUE;
    private static final int[] NEAREST_FIRST = nearestFirst();
    private static final ByteBuffer GRID_VALUES = MemoryUtil.memAlloc(LightBlocks.SECTION_BLOCKS);
    private static final ShortBuffer TILE_VALUES = MemoryUtil.memAllocShort(LightCell.VALUES);
    private static final ByteBuffer GRID_BLOCK = MemoryUtil.memAlloc(1);
    private static final IntBuffer LIGHTS = MemoryUtil.memAllocInt(TILE_LIGHTS * LIGHT_INTS);
    /** Cadres des portails et neons vus par eux, pour le calcul des tuiles ({@link PortalLightUniforms}). */
    private static final float[] FRAME_VALUES = new float[PortalLightUniforms.FRAMES * PortalLightUniforms.FRAME_FLOATS];
    private static final float[] SEEN_VALUES = new float[PortalLightUniforms.SEEN * PortalLightUniforms.SEEN_FLOATS];
    private static final FloatBuffer FRAMES = MemoryUtil.memAllocFloat(FRAME_VALUES.length);
    private static final FloatBuffer SEEN = MemoryUtil.memAllocFloat(SEEN_VALUES.length);
    /** Au-dela de la fenetre des tuiles, la distance ou un cadre compte encore : la portee d'une dalle et un peu plus. */
    static final double FRAME_RANGE = (LightZone.RADIUS + 1) * 16.0 + 24.0;
    /** Ecart sous lequel deux mesures d'un passage sont la meme : les rebonds ne repartent pas pour un dernier bit. */
    private static final float PASSAGE_TOLERANCE = 1.0e-3f;
    private static final IntBuffer SAVED_VIEWPORT = MemoryUtil.memAllocInt(16);
    private static final long[] LIGHT_ORDER = new long[256];
    private static final short[] TILE_ARRAY = new short[LightCell.VALUES];
    private static long bakedTiles;
    private static int program;
    private static int array;
    private static int locOrigin;
    private static int locCell;
    private static int locFace;
    private static int locBase;
    private static int locSprite;
    private static int locCount;
    private static int locLights;
    private static int locEmission;
    private static int locCone;
    private static int locRelief;
    private static int locRays;
    private static int locPortalOrigin;
    private static int locPortalCount;
    private static int locFrames;
    private static int locSeen;
    private static boolean programFailed;
    private static boolean programCreated;
    private static int savedProgram;
    private static int savedFramebuffer;
    private static int savedArray;
    private static int savedUnit;
    private static boolean savedBlend;
    private static boolean savedDepth;
    private static boolean savedStencil;
    private static boolean savedCull;
    private static boolean savedScissor;

    /** Les rebonds traces de ce cache. */
    final LightBounces bounces;
    /** Temps permis par image au calcul des tuiles de ce cache. */
    private final TileBudget tileBudget;
    private final TileClock tileClock;
    private final SurfaceAtlas jobs = new SurfaceAtlas();
    private final long[] gridOccupant = new long[GRID_SLOTS];
    /** Les sections cuites de la zone : celles qui entrent dans le rayon des tuiles en marchant y puisent leurs surfaces. */
    private final Long2ObjectOpenHashMap<LightZone.Baked> kept = new Long2ObjectOpenHashMap<>();
    /** Blocs changes depuis la derniere image, et s'ils emettent ou emettaient. */
    private final LongArrayList changed = new LongArrayList();
    private final BooleanArrayList changedLight = new BooleanArrayList();
    /** Sections copiees entieres depuis la derniere image. */
    private final LongArrayList sectionsCopied = new LongArrayList();
    /** Numero du dernier changement de chaque section dont les surfaces ont suivi un bloc sans attendre la cuisson. */
    private final Long2LongOpenHashMap touched = new Long2LongOpenHashMap();
    private final int[] portalOrigin = new int[3];
    /** Les portails dont les tuiles tiennent compte : leur forme change, les tuiles autour se recalculent. */
    private PortalOpenings portals = PortalOpenings.NONE;
    /** La derniere mesure de ce qui sort des passages que les rebonds ont vue. */
    private LightZone.@Nullable PassageLight passageLight;
    private int lastBase;
    private int lastAnchorX = Integer.MIN_VALUE;
    private int lastAnchorZ = Integer.MIN_VALUE;
    private int atlas;
    private int tiles;
    private int blocks;
    private int framebuffer;
    private boolean failed;
    private boolean created;
    /** {@code true} si ce cache a calcule des tuiles a l'image en cours. */
    private boolean worked;
    /** {@code true} si une image a servi ce cache depuis la derniere revue ({@link #releaseIdle}). */
    private boolean used;
    /** Derniere revue ou ce cache servait, en millisecondes. */
    private long usedAt;

    private LightSurfaces(float milliseconds) {
        this.tileBudget = new TileBudget(milliseconds);
        this.tileClock = new TileClock(this.tileBudget);
        this.bounces = new LightBounces(milliseconds);
        Arrays.fill(this.gridOccupant, NONE);
    }

    /** La place des surfaces de ce cache, pour la passe des ombres des corps. */
    SurfaceAtlas atlas() {
        return this.jobs;
    }

    /** {@code true} si le cache du joueur porte la lumiere directe du terrain : qualite complete, et aucune erreur. */
    public static boolean active() {
        return PLAYER.ready();
    }

    /** {@code true} si ce cache porte la lumiere directe du terrain : qualite complete, et aucune erreur. */
    boolean ready() {
        return this.created && !this.failed && LookResources.surfaceLight();
    }

    /** {@code true} si ce cache tient ses textures sur la carte graphique. */
    boolean allocated() {
        return this.created && !this.failed;
    }

    /** {@code true} si ses rebonds traces tiennent leurs textures sur la carte graphique. */
    boolean bouncesAllocated() {
        return this.bounces.allocated();
    }

    /**
     * Une fois par image, sa zone montree : un cache qu'aucune image n'a servi depuis {@link LightVolume#RELEASE_MS} (un palier sans
     * cache de surfaces) rend ses textures, et se recree a la premiere image qui en a besoin ; un cache servi laisse ses rebonds
     * juger des leurs.
     *
     * @param now instant de l'image, en millisecondes
     */
    void releaseIdle(long now) {
        if (this.used) {
            this.used = false;
            this.usedAt = now;
            this.bounces.releaseIdle(now);
        } else if (allocated() && now - this.usedAt > LightVolume.RELEASE_MS) {
            freeTextures();
        }
    }

    /** Le programme des tuiles, une fois pour les deux caches ; {@code false} s'il est refuse. */
    private static boolean ensureProgram() {
        if (programCreated) {
            return !programFailed;
        }
        programCreated = true;
        if (GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) < ATLAS_HEIGHT) {
            programFailed = true;
            ModConstants.LOGGER.error("[Look] Cache de surfaces coupe pour la session : texture trop grande pour le pilote.");
            return false;
        }
        try {
            program = linkBake(SHADER);
        } catch (IOException | IllegalStateException exception) {
            programFailed = true;
            ModConstants.LOGGER.error("[Look] Cache de surfaces coupe pour la session : {}", exception.getMessage());
            return false;
        }
        locOrigin = GL20.glGetUniformLocation(program, "gtgs_TileOrigin");
        locCell = GL20.glGetUniformLocation(program, "gtgs_Cell");
        locFace = GL20.glGetUniformLocation(program, "gtgs_Face");
        locBase = GL20.glGetUniformLocation(program, "gtgs_LightBase");
        locSprite = GL20.glGetUniformLocation(program, "gtgs_Sprite");
        locCount = GL20.glGetUniformLocation(program, "gtgs_LightCount");
        locLights = GL20.glGetUniformLocation(program, "gtgs_Lights");
        locEmission = GL20.glGetUniformLocation(program, "gtgs_Emission");
        locCone = GL20.glGetUniformLocation(program, "gtgs_Cone");
        locRelief = GL20.glGetUniformLocation(program, "gtgs_Relief");
        locRays = GL20.glGetUniformLocation(program, "gtgs_Rays");
        locPortalOrigin = GL20.glGetUniformLocation(program, "gtgs_PortalOrigin");
        locPortalCount = GL20.glGetUniformLocation(program, "gtgs_PortalCount");
        locFrames = GL20.glGetUniformLocation(program, "gtgs_Frames");
        locSeen = GL20.glGetUniformLocation(program, "gtgs_Seen");
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, SAMPLER_BLOCKS), UNIT_BLOCKS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_BlockAtlas"), UNIT_SPRITES);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, BlockMaterials.SAMPLER), BlockMaterials.UNIT);
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
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        this.atlas = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.atlas);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        this.tiles = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.tiles);
        filterNearest();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R16UI, LightVolume.WIDTH, LightVolume.ROWS * LightCell.FACES, LightVolume.WIDTH, 0, GL30.GL_RED_INTEGER,
                GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
        clearTiles();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        this.blocks = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.blocks);
        filterNearest();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8UI, LightVolume.WIDTH, LightVolume.ROWS, LightVolume.WIDTH, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GlStateManager._activeTexture(previousUnit);
        PixelUnpack.end();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        this.framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.atlas, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
    }

    private static void filterNearest() {
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
    }

    private static void clearTiles() {
        Arrays.fill(TILE_ARRAY, (short) 0);
        for (int z = 0; z < LightVolume.WIDTH; z += SECTION) {
            for (int y = 0; y < LightVolume.ROWS * LightCell.FACES; y += SECTION) {
                for (int x = 0; x < LightVolume.WIDTH; x += SECTION) {
                    TILE_VALUES.clear();
                    TILE_VALUES.put(TILE_ARRAY, 0, LightCell.SECTION_CELLS).flip();
                    GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, x, y, z, SECTION, SECTION, SECTION, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, TILE_VALUES);
                }
            }
        }
    }

    /** Le texte d'un shader ou d'une bibliotheque du mod. */
    static String read(Identifier file) throws IOException {
        Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(file).orElseThrow(() -> new IOException("Shader absent : " + file + "."));
        try (Reader reader = resource.getReader()) {
            return IOUtils.toString(reader);
        }
    }

    /** Le programme d'un calcul sur les tuiles, ses bibliotheques inserees. */
    static int linkBake(Identifier shader) throws IOException {
        return link(read(shader).replace(INCLUDES, read(AREA_LIGHT) + read(BLOCK_MARCH) + read(SURFACE_LIGHT)));
    }

    private static int link(String fragmentSource) {
        return link(VERTEX, fragmentSource);
    }

    /** Un programme de ces deux etages, lie ; une erreur de compilation ou de liaison leve. */
    static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            throw new IllegalStateException("Programme refuse (" + log.trim() + ").");
        }
        return linked;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Shader refuse (" + log.trim() + ").");
        }
        return shader;
    }

    /** Debut d'une image : aucun cache n'a encore calcule, meme celui dont la zone ne se montre plus. */
    static void beginFrame() {
        PLAYER.worked = false;
        VIEW.worked = false;
        PLAYER.bounces.rest();
        VIEW.bounces.rest();
    }

    /** Les deux caches oublient tout : les reglages ont change. */
    static void resetAll() {
        PLAYER.reset();
        VIEW.reset();
    }

    /** Oublie tout ce qui est range : autre zone, autre hauteur, ou fin de partie. */
    void reset() {
        clearState();
        this.bounces.reset();
        if (this.created && !this.failed) {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.tiles);
            PixelUnpack.begin();
            clearTiles();
            PixelUnpack.end();
            GlStateManager._activeTexture(previousUnit);
        }
    }

    /**
     * Rend ses textures, son framebuffer et ceux de ses rebonds a la carte graphique, apres un moment sans usage
     * ({@link #releaseIdle}). Tout se recree a la prochaine image qui en a besoin. Un shader refuse reste coupe pour la session.
     */
    void freeTextures() {
        clearState();
        this.bounces.freeTextures();
        if (!this.created || this.failed) {
            return;
        }
        GL30.glDeleteFramebuffers(this.framebuffer);
        GL11.glDeleteTextures(this.atlas);
        GL11.glDeleteTextures(this.tiles);
        GL11.glDeleteTextures(this.blocks);
        this.framebuffer = 0;
        this.atlas = 0;
        this.tiles = 0;
        this.blocks = 0;
        this.created = false;
    }

    /** Oublie ce que le cache tenait, sans toucher a ses textures. */
    private void clearState() {
        this.jobs.reset();
        this.passageLight = null;
        // Les ouvertures tiennent les zones de l'autre cote : gardees, elles survivraient a la partie.
        this.portals = PortalOpenings.NONE;
        this.kept.clear();
        this.touched.clear();
        this.sectionsCopied.clear();
        this.changed.clear();
        this.changedLight.clear();
        this.lastAnchorX = Integer.MIN_VALUE;
        Arrays.fill(this.gridOccupant, NONE);
    }

    /** Une section quitte la zone : ses tuiles se liberent, et sa place dans l'indirection est deja effacee par {@link #clearSlot}. */
    void release(long key) {
        this.jobs.release(key);
        this.kept.remove(key);
        this.touched.remove(key);
    }

    /** Efface la place d'une section dans l'indirection. */
    void clearSlot(int x, int row, int z) {
        if (!this.created || this.failed) {
            return;
        }
        Arrays.fill(TILE_ARRAY, (short) 0);
        uploadTiles(x, row, z);
    }

    /** Une section cuite arrive : si elle est assez proche de la camera, ses surfaces prennent des tuiles. */
    void accept(LightZone.Baked baked, int anchorX, int anchorZ, int base, int cameraY) {
        // Sans cache de surfaces, rien ne se cree : l'atlas pese cent vingt-huit mebioctets, et un changement de palier republie tout.
        if (!ModConfig.ENABLE_LOOK || !LookResources.surfaceLight()) {
            return;
        }
        create();
        if (this.failed) {
            return;
        }
        // Cuite avant le dernier bloc de cette section : ses surfaces l'ont deja suivi, et la suivante arrive.
        if (baked.stamp() < this.touched.get(baked.key())) {
            return;
        }
        this.kept.put(baked.key(), baked);
        place(baked, anchorX, anchorZ, base, cameraY);
        this.bounces.touch();
    }

    /** Range une section dans le rayon, ce qui ne change pas gardant sa tuile ; une section au-dela rend les siennes. */
    private void place(LightZone.Baked baked, int anchorX, int anchorZ, int base, int cameraY) {
        long key = baked.key();
        if (inRadius(key, anchorX, anchorZ)) {
            this.jobs.accept(key, ChunkSectionPos.unpackY(key) << 4, baked.surfaces(), baked.lights(), cameraY - TILE_BAND, cameraY + TILE_BAND);
        } else if (this.jobs.holds(key)) {
            this.jobs.release(key);
            clearSlot(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key) - base, ChunkSectionPos.unpackZ(key));
        }
    }

    /** La camera a change de hauteur : les tuiles suivent sa bande, ce qui y reste garde sa valeur, et ce qui proche en manque est servi d'abord. */
    void rebanded(int anchorX, int anchorZ, int cameraY) {
        for (LightZone.Baked baked : this.kept.values()) {
            place(baked, anchorX, anchorZ, this.lastBase, cameraY);
        }
        serve(anchorX, anchorZ, this.lastBase, cameraY);
    }

    /** {@code true} si cette section est dans le rayon des tuiles. */
    private static boolean inRadius(long key, int anchorX, int anchorZ) {
        return Math.abs(ChunkSectionPos.unpackX(key) - anchorX) <= TILE_RADIUS && Math.abs(ChunkSectionPos.unpackZ(key) - anchorZ) <= TILE_RADIUS;
    }

    /** L'ancrage a change de section : les sections deja cuites sortent du rayon des tuiles, et leurs tuiles servent a celles qui y entrent. */
    private void followAnchor(int anchorX, int anchorZ, int base, int cameraY) {
        if (anchorX == this.lastAnchorX && anchorZ == this.lastAnchorZ) {
            return;
        }
        this.lastAnchorX = anchorX;
        this.lastAnchorZ = anchorZ;
        for (LightZone.Baked baked : this.kept.values()) {
            if (!inRadius(baked.key(), anchorX, anchorZ) && this.jobs.holds(baked.key())) {
                place(baked, anchorX, anchorZ, base, cameraY);
            }
        }
        serve(anchorX, anchorZ, base, cameraY);
    }

    /** Les colonnes du rayon des tuiles autour de l'ancrage, deux entiers chacune, les plus proches d'abord. */
    private static int[] nearestFirst() {
        int[] order = new int[(TILE_RADIUS * 2 + 1) * (TILE_RADIUS * 2 + 1) * 2];
        int at = 0;
        for (int ring = 0; ring <= TILE_RADIUS; ring++) {
            for (int z = -ring; z <= ring; z++) {
                for (int x = -ring; x <= ring; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == ring) {
                        order[at++] = x;
                        order[at++] = z;
                    }
                }
            }
        }
        return order;
    }

    /**
     * Sert les sections du rayon des tuiles qui en manquent, de la plus proche de l'ancrage a la plus loin. S'il n'y en a pas assez pour
     * toutes, une section reprend les siennes aux plus loin : seules celles-la gardent la lumiere de la zone, ou elle ne se voit pas.
     */
    private void serve(int anchorX, int anchorZ, int base, int cameraY) {
        for (int column = 0; column < NEAREST_FIRST.length; column += 2) {
            for (int row = 0; row < LightZone.HEIGHT; row++) {
                long key = ChunkSectionPos.asLong(anchorX + NEAREST_FIRST[column], base + row, anchorZ + NEAREST_FIRST[column + 1]);
                LightZone.Baked baked = this.kept.get(key);
                if (baked != null && (!this.jobs.holds(key) || this.jobs.starved(key))) {
                    place(baked, anchorX, anchorZ, base, cameraY);
                    for (int farther = NEAREST_FIRST.length - 2; farther > column && this.jobs.starved(key); farther -= 2) {
                        takeTiles(anchorX + NEAREST_FIRST[farther], anchorZ + NEAREST_FIRST[farther + 1], baked, anchorX, anchorZ, base, cameraY);
                    }
                }
            }
        }
    }

    /** Rend les tuiles d'une colonne plus loin, section par section tant que celle qui en manque n'est pas servie. */
    private void takeTiles(int x, int z, LightZone.Baked starved, int anchorX, int anchorZ, int base, int cameraY) {
        for (int row = 0; row < LightZone.HEIGHT && this.jobs.starved(starved.key()); row++) {
            long key = ChunkSectionPos.asLong(x, base + row, z);
            if (this.jobs.holds(key)) {
                this.jobs.release(key);
                clearSlot(x, row, z);
                place(starved, anchorX, anchorZ, base, cameraY);
            }
        }
    }

    /**
     * Debut du rendu du monde : la grille des blocs suit la zone et les blocs changes, puis quelques tuiles se calculent, si
     * la grille est complete : une tuile calculee sur des blocs absents garderait ses ombres fausses. Le cache d'une vue ne calcule
     * que si celui du joueur n'a rien en attente.
     *
     * @param zone zone montree
     * @param look profil de sa dimension
     * @param anchorX section d'ancrage
     * @param base premiere section de la zone
     * @param anchorZ section d'ancrage
     * @param cameraY Y de l'ancrage
     */
    void frame(LightZone zone, LookProfile look, int anchorX, int base, int anchorZ, int cameraY) {
        if (!ModConfig.ENABLE_LOOK || this.failed || !LookResources.surfaceLight()) {
            this.sectionsCopied.clear();
            this.changed.clear();
            this.changedLight.clear();
            return;
        }
        create();
        if (this.failed) {
            return;
        }
        this.used = true;
        this.lastBase = base;
        LightBakeSettings settings = zone.settings();
        for (int i = 0; i < this.sectionsCopied.size(); i++) {
            this.jobs.sectionChanged(this.sectionsCopied.getLong(i), settings.reach());
        }
        this.sectionsCopied.clear();
        followPortals(zone.openings(), settings.reach());
        followPassages(zone.passageLight());
        this.portalOrigin[0] = anchorX << 4;
        this.portalOrigin[1] = base << 4;
        this.portalOrigin[2] = anchorZ << 4;
        applyChanged(zone.blocks(), settings, look, base, cameraY);
        boolean ready = uploadGrid(zone, anchorX, base, anchorZ);
        followAnchor(anchorX, anchorZ, base, cameraY);
        bindForShaders();
        // Le joueur passe d'abord : celui d'une vue ne calcule qu'a une image ou le joueur n'a rien calcule ni rien en attente.
        boolean player = this == PLAYER;
        boolean turn = player || !PLAYER.worked && PLAYER.jobs.current() == null;
        SurfaceAtlas.Job job = this.jobs.current();
        this.worked = job != null && ready && turn;
        if (this.worked) {
            bake(job, settings, look, base);
        }
        List<SurfaceAtlas.Job> moved = this.jobs.takeMoved();
        for (int i = 0; i < moved.size(); i++) {
            publish(moved.get(i), base);
        }
        boolean sweep = player || !PLAYER.bounces.worked() && PLAYER.bounces.idle();
        BlockPos camera = MinecraftClient.getInstance().gameRenderer.getCamera().getBlockPos();
        this.bounces.frame(zone, look, base, this.portalOrigin, ready && this.jobs.current() == null, sweep, player ? camera.getX() : (anchorX << 4) + 8,
                player ? camera.getY() : cameraY, player ? camera.getZ() : (anchorZ << 4) + 8);
    }

    /**
     * Les portails de la zone ont change de forme (un cadre pose ou retire, un neon vu par lui qui arrive ou part) : les tuiles autour
     * de chaque cadre, avant et apres, se recalculent. Une couleur qui change ne compte pas : les tuiles ne gardent que la lumiere
     * directe des neons.
     */
    private void followPortals(PortalOpenings next, float reach) {
        if (next.sameShapeAs(this.portals)) {
            return;
        }
        staleAround(this.portals, reach);
        staleAround(next, reach);
        this.portals = next;
    }

    /**
     * Ce qui sort des passages a ete mesure de nouveau (l'autre cote cuit, ou change) : les rebonds traces, qui le lisent quand un rayon
     * entre dans un cadre, repartent en balayage. Une mesure identique a la precedente ne recommence rien : la cuisson en refait une a
     * chaque section cuite, et la plupart ne changent rien aux cadres.
     */
    private void followPassages(LightZone.@Nullable PassageLight measured) {
        if (measured == this.passageLight || measured != null && this.passageLight != null && measured.openings() == this.passageLight.openings()
                && sameValues(measured.values(), this.passageLight.values())) {
            return;
        }
        this.passageLight = measured;
        this.bounces.touch();
    }

    private static boolean sameValues(float[] a, float[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > PASSAGE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private void staleAround(PortalOpenings openings, float reach) {
        for (int i = 0; i < openings.count(); i++) {
            if (openings.isFrame(i)) {
                this.jobs.sectionChanged(ChunkSectionPos.asLong(ChunkSectionPos.getSectionCoord(openings.center(i, 0)),
                        ChunkSectionPos.getSectionCoord(openings.center(i, 1)), ChunkSectionPos.getSectionCoord(openings.center(i, 2))), reach);
            }
        }
    }

    /** Les blocs changes depuis la derniere image, sur la grille et dans le cache. */
    private void applyChanged(LightBlocks source, LightBakeSettings settings, LookProfile look, int base, int cameraY) {
        int count = this.changed.size();
        if (count == 0) {
            return;
        }
        boolean coarse = count > BLOCKS_PER_FRAME;
        for (int i = 0; i < count; i++) {
            long pos = this.changed.getLong(i);
            int x = BlockPos.unpackLongX(pos);
            int y = BlockPos.unpackLongY(pos);
            int z = BlockPos.unpackLongZ(pos);
            int row = (y >> 4) - base;
            if (coarse) {
                if (row >= 0 && row < LightZone.HEIGHT) {
                    this.gridOccupant[gridSlot(x >> 4, row, z >> 4)] = NONE;
                }
                this.jobs.sectionChanged(ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4), settings.reach());
            } else {
                gridBlock(source, x, y, z, row);
                this.jobs.blockChanged(source, x, y, z, this.changedLight.getBoolean(i), settings.reach(), rayRadius(look), cameraY - TILE_BAND,
                        cameraY + TILE_BAND);
            }
        }
        this.changed.clear();
        this.changedLight.clear();
    }

    /** L'ecart des rayons autour du centre d'une dalle, celui du profil sous ce palier : petit pour des ombres nettes, grand pour des ombres larges. */
    private static float rayRadius(LookProfile look) {
        return look.bakedLight().shadowSize() * CELL_CENTER;
    }

    /** Pose un bloc sur la grille, si sa section y est deja. */
    private void gridBlock(LightBlocks source, int x, int y, int z, int row) {
        if (row < 0 || row >= LightZone.HEIGHT || this.gridOccupant[gridSlot(x >> 4, row, z >> 4)] != ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4)) {
            return;
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.blocks);
        PixelUnpack.begin();
        GRID_BLOCK.clear();
        GRID_BLOCK.put(source.block(x, y, z)).flip();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, x & LightVolume.WIDTH - 1, row * SECTION + (y & SECTION - 1), z & LightVolume.WIDTH - 1, 1, 1, 1,
                GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, GRID_BLOCK);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /** Lie cache, indirection, grille des blocs et rebonds a leurs unites : ceux de la zone que lit la vue qui suit. */
    void bind() {
        if (!this.created || this.failed) {
            return;
        }
        bindForShaders();
        this.bounces.bind();
    }

    /** Lie cache, indirection et grille des blocs a leurs unites : un rechargement a pu les changer. */
    private void bindForShaders() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.atlas);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.tiles);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.blocks);
        GlStateManager._activeTexture(previousUnit);
    }

    /**
     * Lie l'atlas des blocs et la matiere de leurs textures a leurs unites, a tous les paliers a lumiere cuite : le terrain y lit la
     * couleur moyenne d'une texture (textures calmees) et la hauteur de ses pixels. Un rechargement a pu les changer.
     */
    static void bindSprites() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_SPRITES);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, MinecraftClient.getInstance().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getGlId());
        GlStateManager._activeTexture(previousUnit);
        BlockMaterials.bindForShaders();
    }

    private static int gridSlot(int x, int row, int z) {
        return (row * SLOTS_ACROSS + (z & SLOTS_ACROSS - 1)) * SLOTS_ACROSS + (x & SLOTS_ACROSS - 1);
    }

    /** Envoie ce qui manque a la grille ; rend {@code true} si elle est complete autour de l'ancrage. */
    private boolean uploadGrid(LightZone zone, int anchorX, int base, int anchorZ) {
        LightBlocks source = zone.blocks();
        boolean complete = true;
        int budget = GRID_PER_FRAME;
        int reach = SLOTS_ACROSS / 2 - 1;
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.blocks);
        boolean open = false;
        for (int row = 0; row < LightZone.HEIGHT; row++) {
            for (int z = anchorZ - reach; z <= anchorZ + reach; z++) {
                for (int x = anchorX - reach; x <= anchorX + reach; x++) {
                    long key = ChunkSectionPos.asLong(x, base + row, z);
                    int slot = gridSlot(x, row, z);
                    if (this.gridOccupant[slot] == key) {
                        continue;
                    }
                    byte[] section = source.section(key);
                    if (section == null && !zone.inWorld(base + row)) {
                        // Une section au-dessus ou au-dessous du monde n'arrivera jamais : la grille la tient pour opaque.
                        section = LightBlocks.UNLOADED;
                    }
                    if (section == null) {
                        complete = false;
                    } else if (budget == 0) {
                        complete = false;
                    } else {
                        if (!open) {
                            PixelUnpack.begin();
                            open = true;
                        }
                        GRID_VALUES.clear();
                        GRID_VALUES.put(section).flip();
                        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, (x & SLOTS_ACROSS - 1) * SECTION, row * SECTION, (z & SLOTS_ACROSS - 1) * SECTION, SECTION, SECTION, SECTION,
                                GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, GRID_VALUES);
                        this.gridOccupant[slot] = key;
                        budget--;
                    }
                }
            }
        }
        if (open) {
            PixelUnpack.end();
        }
        GlStateManager._activeTexture(previousUnit);
        return complete;
    }

    /** Une section vient d'etre copiee entiere : elle repart vers la grille, et ce que ses blocs atteignent repart a calculer. */
    void onSectionCopied(long key, int base) {
        int row = ChunkSectionPos.unpackY(key) - base;
        if (row >= 0 && row < LightZone.HEIGHT) {
            this.gridOccupant[gridSlot(ChunkSectionPos.unpackX(key), row, ChunkSectionPos.unpackZ(key))] = NONE;
        }
        this.sectionsCopied.add(key);
    }

    /**
     * Un bloc a change : l'image suivante le pose sur la grille et remet a calculer ce qu'il change.
     *
     * @param pos position du bloc
     * @param light {@code true} s'il emet ou emettait
     * @param stamp numero du changement : une section cuite avant lui n'apporte plus rien aux tuiles
     */
    void onBlockChanged(BlockPos pos, boolean light, long stamp) {
        this.changed.add(pos.asLong());
        this.changedLight.add(light);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    this.touched.put(ChunkSectionPos.asLong((pos.getX() + dx) >> 4, (pos.getY() + dy) >> 4, (pos.getZ() + dz) >> 4), stamp);
                }
            }
        }
    }

    /** Quitte le rendu pour un calcul sur une tuile : rien de l'etat du jeu ne doit s'y appliquer, et {@link #leavePass} le rend. */
    static void enterPass(int target) {
        savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        savedFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        savedArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        savedUnit = GlStateManager._getActiveTexture();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, SAVED_VIEWPORT);
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        savedDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        savedStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        savedScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
    }

    /** Rend l'etat trouve par {@link #enterPass}. */
    static void leavePass() {
        GlStateManager._activeTexture(savedUnit);
        GL20.glUseProgram(savedProgram);
        GL30.glBindVertexArray(savedArray);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFramebuffer);
        GL11.glViewport(SAVED_VIEWPORT.get(0), SAVED_VIEWPORT.get(1), SAVED_VIEWPORT.get(2), SAVED_VIEWPORT.get(3));
        if (savedBlend) {
            RenderSystem.enableBlend();
        }
        if (savedDepth) {
            RenderSystem.enableDepthTest();
        }
        if (savedStencil) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        }
        if (savedCull) {
            RenderSystem.enableCull();
        }
        if (savedScissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void bake(SurfaceAtlas.Job first, LightBakeSettings settings, LookProfile look, int base) {
        enterPass(this.framebuffer);
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL20.glUniform1i(locBase, base << 4);
            GL20.glUniform4f(locEmission, Math.max(settings.red(), Math.max(settings.green(), settings.blue())), settings.size(), rayRadius(look), settings.reach());
            GL20.glUniform1f(locCone, settings.cone());
            GL20.glUniform1f(locRelief, look.bakedLight().relief());
            GL20.glUniform2i(locRays, LookResources.quality().surfaceRays(), LookResources.quality().surfaceOcclusionRays());
            PortalLightUniforms.pack(this.portals, this.portalOrigin, this.portalOrigin[0] + 8.0, this.portalOrigin[1] + 16.0, this.portalOrigin[2] + 8.0,
                    FRAME_RANGE, FRAME_VALUES, SEEN_VALUES);
            GL20.glUniform3i(locPortalOrigin, this.portalOrigin[0], this.portalOrigin[1], this.portalOrigin[2]);
            GL20.glUniform2i(locPortalCount, PortalLightUniforms.frames(), PortalLightUniforms.seen());
            FRAMES.clear().put(FRAME_VALUES).flip();
            SEEN.clear().put(SEEN_VALUES).flip();
            GL20.glUniform4fv(locFrames, FRAMES);
            GL20.glUniform4fv(locSeen, SEEN);
            int given = this.tileBudget.tiles();
            int budget = given;
            this.tileClock.begin();
            SurfaceAtlas.Job job = first;
            while (job != null && budget > 0) {
                budget = bakeTiles(job, budget, settings.reach());
                if (job.done()) {
                    this.jobs.finish();
                    job = this.jobs.current();
                }
            }
            this.tileClock.end(given - budget);
        } finally {
            leavePass();
        }
    }

    /** Calcule des tuiles d'une section, tant que le budget de l'image tient ; rend ce qu'il en reste. */
    private int bakeTiles(SurfaceAtlas.Job job, int budget, float reach) {
        int left = budget;
        long key = job.key();
        int baseX = ChunkSectionPos.unpackX(key) << 4;
        int baseY = ChunkSectionPos.unpackY(key) << 4;
        int baseZ = ChunkSectionPos.unpackZ(key) << 4;
        int[] surfaces = job.surfaces();
        int[] held = job.tiles();
        boolean traced = LightBounces.active();
        while (!job.done() && left > 0) {
            int i = job.next();
            int surface = surfaces[i];
            int cell = SurfaceAtlas.cell(surface);
            int tile = held[i];
            int originX = (tile % SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
            int originY = (tile / SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
            GL20.glUniform1i(locCount, tileLights(job.lights(), baseX + (cell & 15), baseY + (cell >> 4 & 15), baseZ + (cell >> 8), SurfaceAtlas.face(surface), reach));
            GL20.glUniform4iv(locLights, LIGHTS);
            GL11.glViewport(originX, originY, SurfaceAtlas.TILE, SurfaceAtlas.TILE);
            GL20.glUniform2i(locOrigin, originX, originY);
            GL20.glUniform3i(locCell, baseX + (cell & 15), baseY + (cell >> 4 & 15), baseZ + (cell >> 8));
            GL20.glUniform1i(locFace, SurfaceAtlas.face(surface));
            float[] sprite = LightCopy.sprite(this == VIEW, SurfaceAtlas.material(surface));
            GL20.glUniform4f(locSprite, sprite[0], sprite[1], sprite[2], sprite[3]);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            if (traced) {
                this.bounces.tileBaked(tile, baseX + (cell & 15), baseY + (cell >> 4 & 15), baseZ + (cell >> 8), SurfaceAtlas.face(surface), job.fresh(i));
            }
            job.advance();
            bakedTiles++;
            left--;
        }
        return left;
    }

    /** Les neons qui comptent pour une surface, les plus proches d'abord, poses dans {@link #LIGHTS} ; rend leur nombre. */
    private static int tileLights(int[] lights, int cellX, int cellY, int cellZ, int face, float reach) {
        float px = cellX + CELL_CENTER - LightCell.NORMAL_X[face] * CELL_CENTER;
        float py = cellY + CELL_CENTER - LightCell.NORMAL_Y[face] * CELL_CENTER;
        float pz = cellZ + CELL_CENTER - LightCell.NORMAL_Z[face] * CELL_CENTER;
        float limit = (reach + TILE_MARGIN) * (reach + TILE_MARGIN);
        int found = 0;
        for (int i = 0; i < lights.length; i += LIGHT_INTS) {
            int side = lights[i + 3];
            float dx = lights[i] + CELL_CENTER + LightCell.NORMAL_X[side] * CELL_CENTER - px;
            float dy = lights[i + 1] + CELL_CENTER + LightCell.NORMAL_Y[side] * CELL_CENTER - py;
            float dz = lights[i + 2] + CELL_CENTER + LightCell.NORMAL_Z[side] * CELL_CENTER - pz;
            float distance2 = dx * dx + dy * dy + dz * dz;
            boolean faces = dx * LightCell.NORMAL_X[face] + dy * LightCell.NORMAL_Y[face] + dz * LightCell.NORMAL_Z[face] > -TILE_MARGIN;
            boolean lit = dx * LightCell.NORMAL_X[side] + dy * LightCell.NORMAL_Y[side] + dz * LightCell.NORMAL_Z[side] < TILE_MARGIN;
            if (distance2 <= limit && faces && lit && found < LIGHT_ORDER.length) {
                LIGHT_ORDER[found++] = (long) (distance2 * 16.0f) << ORDER_SHIFT | i;
            }
        }
        Arrays.sort(LIGHT_ORDER, 0, found);
        int keptLights = Math.min(found, TILE_LIGHTS);
        LIGHTS.clear();
        for (int k = 0; k < keptLights; k++) {
            LIGHTS.put(lights, (int) LIGHT_ORDER[k], LIGHT_INTS);
        }
        LIGHTS.flip();
        return keptLights;
    }

    /** L'indirection d'une section suit ses surfaces ; celle qui n'a pas encore de valeur reste a la lumiere de la zone. */
    private void publish(SurfaceAtlas.Job job, int base) {
        Arrays.fill(TILE_ARRAY, (short) 0);
        int[] surfaces = job.surfaces();
        int[] held = job.tiles();
        for (int i = 0; i < surfaces.length; i++) {
            if (job.shown(i)) {
                TILE_ARRAY[SurfaceAtlas.face(surfaces[i]) * LightCell.SECTION_CELLS + SurfaceAtlas.cell(surfaces[i])] = (short) held[i];
            }
        }
        long key = job.key();
        uploadTiles(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key) - base, ChunkSectionPos.unpackZ(key));
    }

    private void uploadTiles(int x, int row, int z) {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.tiles);
        PixelUnpack.begin();
        for (int face = 0; face < LightCell.FACES; face++) {
            TILE_VALUES.clear();
            TILE_VALUES.put(TILE_ARRAY, face * LightCell.SECTION_CELLS, LightCell.SECTION_CELLS).flip();
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, (x & SLOTS_ACROSS - 1) * SECTION, face * LightVolume.ROWS + row * SECTION, (z & SLOTS_ACROSS - 1) * SECTION,
                    SECTION, SECTION, SECTION, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, TILE_VALUES);
        }
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /**
     * L'empreinte de chaque tuile tenue par le cache du joueur, par surface : deux caches identiques au bit pres ont les memes (tests du
     * client).
     *
     * @return une empreinte par surface tenue et montree
     */
    public static Long2LongOpenHashMap fingerprints() {
        Long2LongOpenHashMap prints = new Long2LongOpenHashMap();
        ByteBuffer pixels = PLAYER.readAtlas();
        try {
            for (LightZone.Baked baked : PLAYER.kept.values()) {
                SurfaceAtlas.Job job = PLAYER.jobs.job(baked.key());
                if (job != null) {
                    for (int i = 0; i < job.surfaces().length; i++) {
                        if (job.shown(i)) {
                            prints.put(job.key() * 31L + job.surfaces()[i], tilePrint(pixels, job.tiles()[i]));
                        }
                    }
                }
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }
        return prints;
    }

    /** Le cache relu sur le processeur, a rendre par {@link MemoryUtil#memFree}. */
    private ByteBuffer readAtlas() {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.atlas);
        PixelUnpack.begin();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        return pixels;
    }

    /**
     * Les pixels de la tuile d'une surface du cache du joueur, de bas en haut de sa face (tests du client).
     *
     * @param x cellule d'air devant la surface
     * @param y cellule d'air devant la surface
     * @param z cellule d'air devant la surface
     * @param face face de la surface
     * @return quatre octets par pixel, ou {@code null} si elle n'a pas de tuile montree
     */
    public static byte @Nullable [] surfacePixels(int x, int y, int z, int face) {
        SurfaceAtlas.Job job = PLAYER.jobs.job(ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (job == null) {
            return null;
        }
        int cell = (z & SECTION - 1) << 8 | (y & SECTION - 1) << 4 | x & SECTION - 1;
        for (int i = 0; i < job.surfaces().length; i++) {
            int surface = job.surfaces()[i];
            if (job.shown(i) && SurfaceAtlas.cell(surface) == cell && SurfaceAtlas.face(surface) == face) {
                return PLAYER.tilePixels(job.tiles()[i]);
            }
        }
        return null;
    }

    private byte[] tilePixels(int tile) {
        ByteBuffer pixels = readAtlas();
        try {
            byte[] copy = new byte[SurfaceAtlas.TILE * SurfaceAtlas.TILE * 4];
            int originX = (tile % SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
            int originY = (tile / SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
            for (int y = 0; y < SurfaceAtlas.TILE; y++) {
                pixels.position(((originY + y) * ATLAS_WIDTH + originX) * 4);
                pixels.get(copy, y * SurfaceAtlas.TILE * 4, SurfaceAtlas.TILE * 4);
            }
            return copy;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /**
     * L'empreinte des rebonds de chaque surface tenue par le cache du joueur : deux calculs identiques au bit pres ont les memes (tests du
     * client).
     *
     * @return une empreinte par surface tenue et montree
     */
    public static Long2LongOpenHashMap bounceFingerprints() {
        Long2LongOpenHashMap prints = new Long2LongOpenHashMap();
        byte[] pixels = PLAYER.bounces.readTraced();
        for (LightZone.Baked baked : PLAYER.kept.values()) {
            SurfaceAtlas.Job job = PLAYER.jobs.job(baked.key());
            if (job != null) {
                for (int i = 0; i < job.surfaces().length; i++) {
                    if (job.shown(i)) {
                        prints.put(job.key() * 31L + job.surfaces()[i], LightBounces.tilePrint(pixels, job.tiles()[i]));
                    }
                }
            }
        }
        return prints;
    }

    /**
     * La moyenne des rebonds de chaque surface tenue par le cache du joueur (tests du client).
     *
     * @return une moyenne par surface tenue et montree
     */
    public static Long2DoubleOpenHashMap bounceMeans() {
        Long2DoubleOpenHashMap means = new Long2DoubleOpenHashMap();
        byte[] pixels = PLAYER.bounces.readTraced();
        for (LightZone.Baked baked : PLAYER.kept.values()) {
            SurfaceAtlas.Job job = PLAYER.jobs.job(baked.key());
            if (job != null) {
                for (int i = 0; i < job.surfaces().length; i++) {
                    if (job.shown(i)) {
                        means.put(job.key() * 31L + job.surfaces()[i], LightBounces.tileMean(pixels, job.tiles()[i]));
                    }
                }
            }
        }
        return means;
    }

    private static long tilePrint(ByteBuffer pixels, int tile) {
        int originX = (tile % SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
        int originY = (tile / SurfaceAtlas.ACROSS) * SurfaceAtlas.TILE;
        long print = 1125899906842597L;
        for (int y = 0; y < SurfaceAtlas.TILE; y++) {
            for (int x = 0; x < SurfaceAtlas.TILE; x++) {
                print = print * 31L + pixels.getInt(((originY + y) * ATLAS_WIDTH + originX + x) * 4);
            }
        }
        return print;
    }

    /** Tuiles calculees depuis le lancement, par les deux caches (tests du client). */
    public static long bakedTiles() {
        return bakedTiles;
    }

    /** Tuiles libres du cache du joueur (tests du client). */
    public static int freeTiles() {
        return PLAYER.jobs.freeTiles();
    }

    /**
     * Ramene le cache du joueur a ce nombre de tuiles : il se remplit comme dans une salle plus grande (tests du client).
     *
     * @param count tuiles du cache
     */
    public static void limitTiles(int count) {
        PLAYER.jobs.limit(count);
    }

    /** {@code true} si une section du cache du joueur attend des tuiles (tests du client). */
    public static boolean starving() {
        return PLAYER.jobs.starving();
    }

    /** Sections du cache du joueur qui gardent des tuiles hors du rayon de l'ancrage (tests du client). */
    public static int sectionsBeyondRadius() {
        int count = 0;
        for (LightZone.Baked baked : PLAYER.kept.values()) {
            count += PLAYER.jobs.holds(baked.key()) && !inRadius(baked.key(), PLAYER.lastAnchorX, PLAYER.lastAnchorZ) ? 1 : 0;
        }
        return count;
    }

    /**
     * Les surfaces cuites de la zone du joueur assez proches d'un point qui n'ont pas de tuile montree (tests du client).
     *
     * @param x point
     * @param y point
     * @param z point
     * @param reach distance au point, en blocs, sur X et Z
     * @return leur nombre, hors de la bande de hauteur des tuiles
     */
    public static int surfacesWithoutTile(double x, int y, double z, double reach) {
        int count = 0;
        for (LightZone.Baked baked : PLAYER.kept.values()) {
            long key = baked.key();
            int originX = ChunkSectionPos.unpackX(key) << 4;
            int originY = ChunkSectionPos.unpackY(key) << 4;
            int originZ = ChunkSectionPos.unpackZ(key) << 4;
            SurfaceAtlas.Job job = PLAYER.jobs.job(key);
            IntOpenHashSet shown = new IntOpenHashSet();
            for (int i = 0; job != null && i < job.surfaces().length; i++) {
                if (job.shown(i)) {
                    shown.add(job.surfaces()[i]);
                }
            }
            for (int surface : baked.surfaces()) {
                int cell = SurfaceAtlas.cell(surface);
                int surfaceY = originY + (cell >> 4 & SECTION - 1);
                boolean near = Math.max(Math.abs(originX + (cell & SECTION - 1) + CELL_CENTER - x), Math.abs(originZ + (cell >> 8) + CELL_CENTER - z)) < reach;
                if (near && Math.abs(surfaceY - y) <= TILE_BAND && !shown.contains(surface)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** La section courante en attente dans le cache du joueur, ou {@code null} (tests du client). */
    public static SurfaceAtlas.@Nullable Job pending() {
        return PLAYER.jobs.current();
    }

    /** La section courante en attente dans le cache de la vue, ou {@code null} (tests du client). */
    public static SurfaceAtlas.@Nullable Job viewPending() {
        return VIEW.jobs.current();
    }

    /** {@code true} si le cache de la vue tient des tuiles (tests du client). */
    public static boolean viewHoldsTiles() {
        return VIEW.created && !VIEW.failed && VIEW.jobs.freeTiles() < SurfaceAtlas.CAPACITY - 1;
    }
}
