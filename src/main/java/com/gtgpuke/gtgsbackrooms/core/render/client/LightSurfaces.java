package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
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
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightCell;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Le cache de surfaces : une lightmap d'un pixel par pixel de texture, calculee par le processeur graphique, quelques tuiles
 * par image (shader {@code bake/surface.fsh}) avec la grille des blocs de la zone en texture 3D. La tuile d'une surface se
 * retrouve par une texture d'indirection, de la forme de la texture de zone : les shaders du terrain y lisent en un acces la
 * lumiere qui arrive a chaque pixel, la part de ses dalles qu'il voit, ce que les blocs proches lui cachent et son relief.
 * Une tuile se calcule d'un seul dessin, et seulement quand ce qu'elle voit change. Elle ne se calcule que si la grille des
 * blocs est a jour autour d'elle : ses ombres ne s'ecrivent jamais d'apres d'autres blocs. Un shader refuse par le pilote
 * coupe le cache pour la session : le terrain garde la texture de zone.
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
    private static final Identifier SHADER = Identifier.of(ModConstants.MOD_ID, "shaders/bake/surface.fsh");
    private static final Identifier AREA_LIGHT = Identifier.of(ModConstants.MOD_ID, "shaders/include/area_light.glsl");
    private static final Identifier BLOCK_MARCH = Identifier.of(ModConstants.MOD_ID, "shaders/include/block_march.glsl");
    private static final Identifier SURFACE_LIGHT = Identifier.of(ModConstants.MOD_ID, "shaders/include/surface_light.glsl");
    /** La ligne d'un shader de calcul que remplacent ses bibliotheques : hors des shaders vanilla, rien ne resout {@code #moj_import}. */
    private static final String INCLUDES = "#pragma gtgs_includes";
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
    /** Tuiles calculees par image : le coeur du cout du cache, et il ne dure que le temps de remplir les surfaces proches. */
    private static final int TILES_PER_FRAME = 96;
    /** Sections de la grille des blocs envoyees par image. */
    private static final int GRID_PER_FRAME = 8;
    /** Rayon des sections qui ont des tuiles, autour de l'ancrage. */
    private static final int TILE_RADIUS = 2;
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
    private static final SurfaceAtlas ATLAS = new SurfaceAtlas();
    private static final long[] GRID_OCCUPANT = new long[GRID_SLOTS];
    private static final ByteBuffer GRID_VALUES = MemoryUtil.memAlloc(LightBlocks.SECTION_BLOCKS);
    private static final ShortBuffer TILE_VALUES = MemoryUtil.memAllocShort(LightCell.VALUES);
    private static final ByteBuffer GRID_BLOCK = MemoryUtil.memAlloc(1);
    private static final IntBuffer LIGHTS = MemoryUtil.memAllocInt(TILE_LIGHTS * LIGHT_INTS);
    private static final IntBuffer SAVED_VIEWPORT = MemoryUtil.memAllocInt(16);
    private static final long[] LIGHT_ORDER = new long[256];
    private static final short[] TILE_ARRAY = new short[LightCell.VALUES];
    /** Les sections cuites de la zone : celles qui entrent dans le rayon des tuiles en marchant y puisent leurs surfaces. */
    private static final Long2ObjectOpenHashMap<LightZone.Baked> KEPT = new Long2ObjectOpenHashMap<>();
    /** Blocs changes depuis la derniere image, et s'ils emettent ou emettaient. */
    private static final LongArrayList CHANGED = new LongArrayList();
    /** Sections copiees entieres depuis la derniere image. */
    private static final LongArrayList SECTIONS_COPIED = new LongArrayList();
    private static final BooleanArrayList CHANGED_LIGHT = new BooleanArrayList();
    /** Numero du dernier changement de chaque section dont les surfaces ont suivi un bloc sans attendre la cuisson. */
    private static final Long2LongOpenHashMap TOUCHED = new Long2LongOpenHashMap();
    private static int lastBase;
    private static long bakedTiles;
    private static int lastAnchorX = Integer.MIN_VALUE;
    private static int lastAnchorZ = Integer.MIN_VALUE;
    private static int atlas;
    private static int tiles;
    private static int blocks;
    private static int framebuffer;
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
    private static boolean failed;
    private static boolean created;
    private static int savedProgram;
    private static int savedFramebuffer;
    private static int savedArray;
    private static int savedUnit;
    private static boolean savedBlend;
    private static boolean savedDepth;
    private static boolean savedStencil;
    private static boolean savedCull;
    private static boolean savedScissor;

    static {
        Arrays.fill(GRID_OCCUPANT, NONE);
    }

    private LightSurfaces() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si le cache porte la lumiere directe du terrain : qualite complete, et aucune erreur. */
    public static boolean active() {
        return created && !failed && LookResources.surfaceLight();
    }

    private static void create() {
        if (created || failed) {
            return;
        }
        created = true;
        if (GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) < ATLAS_HEIGHT) {
            failed = true;
            ModConstants.LOGGER.error("[Look] Cache de surfaces coupe pour la session : texture trop grande pour le pilote.");
            return;
        }
        try {
            program = linkBake(SHADER);
        } catch (IOException | IllegalStateException exception) {
            failed = true;
            ModConstants.LOGGER.error("[Look] Cache de surfaces coupe pour la session : {}", exception.getMessage());
            return;
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
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, SAMPLER_BLOCKS), UNIT_BLOCKS);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_BlockAtlas"), UNIT_SPRITES);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, BlockMaterials.SAMPLER), BlockMaterials.UNIT);
        GL20.glUseProgram(previousProgram);
        int previousUnit = GlStateManager._getActiveTexture();
        PixelUnpack.begin();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        atlas = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        tiles = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tiles);
        filterNearest();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R16UI, LightVolume.WIDTH, LightVolume.ROWS * LightCell.FACES, LightVolume.WIDTH, 0, GL30.GL_RED_INTEGER,
                GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
        clearTiles();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        blocks = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, blocks);
        filterNearest();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8UI, LightVolume.WIDTH, LightVolume.ROWS, LightVolume.WIDTH, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GlStateManager._activeTexture(previousUnit);
        PixelUnpack.end();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, atlas, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        array = GL30.glGenVertexArrays();
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

    private static String read(Identifier file) throws IOException {
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
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
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

    /** Oublie tout ce qui est range : autre zone, autre hauteur, ou fin de partie. */
    static void reset() {
        ATLAS.reset();
        LightBounces.reset();
        KEPT.clear();
        TOUCHED.clear();
        SECTIONS_COPIED.clear();
        CHANGED.clear();
        CHANGED_LIGHT.clear();
        lastAnchorX = Integer.MIN_VALUE;
        Arrays.fill(GRID_OCCUPANT, NONE);
        if (created && !failed) {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, tiles);
            PixelUnpack.begin();
            clearTiles();
            PixelUnpack.end();
            GlStateManager._activeTexture(previousUnit);
        }
    }

    /** Une section quitte la zone : ses tuiles se liberent, et sa place dans l'indirection est deja effacee par {@link #clearSlot}. */
    static void release(long key) {
        ATLAS.release(key);
        KEPT.remove(key);
        TOUCHED.remove(key);
    }

    /** Efface la place d'une section dans l'indirection. */
    static void clearSlot(int x, int row, int z) {
        if (!created || failed) {
            return;
        }
        Arrays.fill(TILE_ARRAY, (short) 0);
        uploadTiles(x, row, z);
    }

    /** Une section cuite arrive : si elle est assez proche de la camera, ses surfaces prennent des tuiles. */
    static void accept(LightZone.Baked baked, int anchorX, int anchorZ, int base, int cameraY) {
        // Sans cache de surfaces, rien ne se cree : l'atlas pese cent vingt-huit mebioctets, et un changement de palier republie tout.
        if (!ModConfig.ENABLE_LOOK || !LookResources.surfaceLight()) {
            return;
        }
        create();
        if (failed) {
            return;
        }
        // Cuite avant le dernier bloc de cette section : ses surfaces l'ont deja suivi, et la suivante arrive.
        if (baked.stamp() < TOUCHED.get(baked.key())) {
            return;
        }
        KEPT.put(baked.key(), baked);
        place(baked, anchorX, anchorZ, base, cameraY);
        LightBounces.touch();
    }

    /** Range une section dans le rayon, ce qui ne change pas gardant sa tuile ; une section au-dela rend les siennes. */
    private static void place(LightZone.Baked baked, int anchorX, int anchorZ, int base, int cameraY) {
        long key = baked.key();
        if (inRadius(key, anchorX, anchorZ)) {
            ATLAS.accept(key, ChunkSectionPos.unpackY(key) << 4, baked.surfaces(), baked.lights(), cameraY - TILE_BAND, cameraY + TILE_BAND);
        } else if (ATLAS.holds(key)) {
            ATLAS.release(key);
            clearSlot(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key) - base, ChunkSectionPos.unpackZ(key));
        }
    }

    /** La camera a change de hauteur : les tuiles suivent sa bande, ce qui y reste garde sa valeur, et ce qui proche en manque est servi d'abord. */
    static void rebanded(int anchorX, int anchorZ, int cameraY) {
        for (LightZone.Baked baked : KEPT.values()) {
            place(baked, anchorX, anchorZ, lastBase, cameraY);
        }
        serve(anchorX, anchorZ, lastBase, cameraY);
    }

    /** {@code true} si cette section est dans le rayon des tuiles. */
    private static boolean inRadius(long key, int anchorX, int anchorZ) {
        return Math.abs(ChunkSectionPos.unpackX(key) - anchorX) <= TILE_RADIUS && Math.abs(ChunkSectionPos.unpackZ(key) - anchorZ) <= TILE_RADIUS;
    }

    /** L'ancrage a change de section : les sections deja cuites sortent du rayon des tuiles, et leurs tuiles servent a celles qui y entrent. */
    private static void followAnchor(int anchorX, int anchorZ, int base, int cameraY) {
        if (anchorX == lastAnchorX && anchorZ == lastAnchorZ) {
            return;
        }
        lastAnchorX = anchorX;
        lastAnchorZ = anchorZ;
        for (LightZone.Baked baked : KEPT.values()) {
            if (!inRadius(baked.key(), anchorX, anchorZ) && ATLAS.holds(baked.key())) {
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
    private static void serve(int anchorX, int anchorZ, int base, int cameraY) {
        for (int column = 0; column < NEAREST_FIRST.length; column += 2) {
            for (int row = 0; row < LightZone.HEIGHT; row++) {
                long key = ChunkSectionPos.asLong(anchorX + NEAREST_FIRST[column], base + row, anchorZ + NEAREST_FIRST[column + 1]);
                LightZone.Baked baked = KEPT.get(key);
                if (baked != null && (!ATLAS.holds(key) || ATLAS.starved(key))) {
                    place(baked, anchorX, anchorZ, base, cameraY);
                    for (int farther = NEAREST_FIRST.length - 2; farther > column && ATLAS.starved(key); farther -= 2) {
                        takeTiles(anchorX + NEAREST_FIRST[farther], anchorZ + NEAREST_FIRST[farther + 1], baked, anchorX, anchorZ, base, cameraY);
                    }
                }
            }
        }
    }

    /** Rend les tuiles d'une colonne plus loin, section par section tant que celle qui en manque n'est pas servie. */
    private static void takeTiles(int x, int z, LightZone.Baked starved, int anchorX, int anchorZ, int base, int cameraY) {
        for (int row = 0; row < LightZone.HEIGHT && ATLAS.starved(starved.key()); row++) {
            long key = ChunkSectionPos.asLong(x, base + row, z);
            if (ATLAS.holds(key)) {
                ATLAS.release(key);
                clearSlot(x, row, z);
                place(starved, anchorX, anchorZ, base, cameraY);
            }
        }
    }

    /**
     * Debut du rendu du monde : la grille des blocs suit la zone et les blocs changes, puis quelques tuiles se calculent, si
     * la grille est complete : une tuile calculee sur des blocs absents garderait ses ombres fausses.
     *
     * @param zone zone montree
     * @param look profil de sa dimension
     * @param anchorX section d'ancrage
     * @param base premiere section de la zone
     * @param anchorZ section d'ancrage
     * @param cameraY Y de l'ancrage
     */
    static void frame(LightZone zone, LookProfile look, int anchorX, int base, int anchorZ, int cameraY) {
        if (!ModConfig.ENABLE_LOOK || failed || !LookResources.surfaceLight()) {
            SECTIONS_COPIED.clear();
            CHANGED.clear();
            CHANGED_LIGHT.clear();
            return;
        }
        create();
        if (failed) {
            return;
        }
        lastBase = base;
        LightBakeSettings settings = zone.settings();
        for (int i = 0; i < SECTIONS_COPIED.size(); i++) {
            ATLAS.sectionChanged(SECTIONS_COPIED.getLong(i), settings.reach());
        }
        SECTIONS_COPIED.clear();
        applyChanged(zone.blocks(), settings, look, base, cameraY);
        boolean ready = uploadGrid(zone, anchorX, base, anchorZ);
        followAnchor(anchorX, anchorZ, base, cameraY);
        bindForShaders();
        SurfaceAtlas.Job job = ATLAS.current();
        if (job != null && ready) {
            bake(job, settings, look, base);
        }
        List<SurfaceAtlas.Job> moved = ATLAS.takeMoved();
        for (int i = 0; i < moved.size(); i++) {
            publish(moved.get(i), base);
        }
        LightBounces.frame(settings, look, zone.blocks(), base, ready && ATLAS.current() == null);
    }

    /** Les blocs changes depuis la derniere image, sur la grille et dans le cache. */
    private static void applyChanged(LightBlocks source, LightBakeSettings settings, LookProfile look, int base, int cameraY) {
        int count = CHANGED.size();
        if (count == 0) {
            return;
        }
        boolean coarse = count > BLOCKS_PER_FRAME;
        for (int i = 0; i < count; i++) {
            long pos = CHANGED.getLong(i);
            int x = BlockPos.unpackLongX(pos);
            int y = BlockPos.unpackLongY(pos);
            int z = BlockPos.unpackLongZ(pos);
            int row = (y >> 4) - base;
            if (coarse) {
                if (row >= 0 && row < LightZone.HEIGHT) {
                    GRID_OCCUPANT[gridSlot(x >> 4, row, z >> 4)] = NONE;
                }
                ATLAS.sectionChanged(ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4), settings.reach());
            } else {
                gridBlock(source, x, y, z, row);
                ATLAS.blockChanged(source, x, y, z, CHANGED_LIGHT.getBoolean(i), settings.reach(), rayRadius(look), cameraY - TILE_BAND, cameraY + TILE_BAND);
            }
        }
        CHANGED.clear();
        CHANGED_LIGHT.clear();
    }

    /** L'ecart des rayons autour du centre d'une dalle, celui du profil sous ce palier : petit pour des ombres nettes, grand pour des ombres larges. */
    private static float rayRadius(LookProfile look) {
        return look.bakedLight().shadowSize() * CELL_CENTER;
    }

    /** Pose un bloc sur la grille, si sa section y est deja. */
    private static void gridBlock(LightBlocks source, int x, int y, int z, int row) {
        if (row < 0 || row >= LightZone.HEIGHT || GRID_OCCUPANT[gridSlot(x >> 4, row, z >> 4)] != ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4)) {
            return;
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, blocks);
        PixelUnpack.begin();
        GRID_BLOCK.clear();
        GRID_BLOCK.put(source.block(x, y, z)).flip();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, x & LightVolume.WIDTH - 1, row * SECTION + (y & SECTION - 1), z & LightVolume.WIDTH - 1, 1, 1, 1,
                GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, GRID_BLOCK);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    /** Lie cache, indirection et grille des blocs a leurs unites : un rechargement a pu les changer. */
    private static void bindForShaders() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tiles);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, blocks);
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
    private static boolean uploadGrid(LightZone zone, int anchorX, int base, int anchorZ) {
        LightBlocks source = zone.blocks();
        boolean complete = true;
        int budget = GRID_PER_FRAME;
        int reach = SLOTS_ACROSS / 2 - 1;
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_BLOCKS);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, blocks);
        boolean open = false;
        for (int row = 0; row < LightZone.HEIGHT; row++) {
            for (int z = anchorZ - reach; z <= anchorZ + reach; z++) {
                for (int x = anchorX - reach; x <= anchorX + reach; x++) {
                    long key = ChunkSectionPos.asLong(x, base + row, z);
                    int slot = gridSlot(x, row, z);
                    if (GRID_OCCUPANT[slot] == key) {
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
                        GRID_OCCUPANT[slot] = key;
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
    static void onSectionCopied(long key, int base) {
        int row = ChunkSectionPos.unpackY(key) - base;
        if (row >= 0 && row < LightZone.HEIGHT) {
            GRID_OCCUPANT[gridSlot(ChunkSectionPos.unpackX(key), row, ChunkSectionPos.unpackZ(key))] = NONE;
        }
        SECTIONS_COPIED.add(key);
    }

    /**
     * Un bloc a change : l'image suivante le pose sur la grille et remet a calculer ce qu'il change.
     *
     * @param pos position du bloc
     * @param light {@code true} s'il emet ou emettait
     * @param stamp numero du changement : une section cuite avant lui n'apporte plus rien aux tuiles
     */
    static void onBlockChanged(BlockPos pos, boolean light, long stamp) {
        CHANGED.add(pos.asLong());
        CHANGED_LIGHT.add(light);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    TOUCHED.put(ChunkSectionPos.asLong((pos.getX() + dx) >> 4, (pos.getY() + dy) >> 4, (pos.getZ() + dz) >> 4), stamp);
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

    private static void bake(SurfaceAtlas.Job first, LightBakeSettings settings, LookProfile look, int base) {
        enterPass(framebuffer);
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(array);
            GL20.glUniform1i(locBase, base << 4);
            GL20.glUniform4f(locEmission, Math.max(settings.red(), Math.max(settings.green(), settings.blue())), settings.size(), rayRadius(look), settings.reach());
            GL20.glUniform1f(locCone, settings.cone());
            GL20.glUniform1f(locRelief, look.bakedLight().relief());
            GL20.glUniform2i(locRays, LookResources.quality().surfaceRays(), LookResources.quality().surfaceOcclusionRays());
            int budget = TILES_PER_FRAME;
            SurfaceAtlas.Job job = first;
            while (job != null && budget > 0) {
                budget = bakeTiles(job, budget, settings.reach());
                if (job.done()) {
                    ATLAS.finish();
                    job = ATLAS.current();
                }
            }
        } finally {
            leavePass();
        }
    }

    /** Calcule des tuiles d'une section, tant que le budget de l'image tient ; rend ce qu'il en reste. */
    private static int bakeTiles(SurfaceAtlas.Job job, int budget, float reach) {
        int left = budget;
        long key = job.key();
        int baseX = ChunkSectionPos.unpackX(key) << 4;
        int baseY = ChunkSectionPos.unpackY(key) << 4;
        int baseZ = ChunkSectionPos.unpackZ(key) << 4;
        int[] surfaces = job.surfaces();
        int[] held = job.tiles();
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
            float[] sprite = LightCopy.sprite(SurfaceAtlas.material(surface));
            GL20.glUniform4f(locSprite, sprite[0], sprite[1], sprite[2], sprite[3]);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            if (LightBounces.active()) {
                LightBounces.tileBaked(tile, baseX + (cell & 15), baseY + (cell >> 4 & 15), baseZ + (cell >> 8), SurfaceAtlas.face(surface), job.fresh(i));
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
        int kept = Math.min(found, TILE_LIGHTS);
        LIGHTS.clear();
        for (int k = 0; k < kept; k++) {
            LIGHTS.put(lights, (int) LIGHT_ORDER[k], LIGHT_INTS);
        }
        LIGHTS.flip();
        return kept;
    }

    /** L'indirection d'une section suit ses surfaces ; celle qui n'a pas encore de valeur reste a la lumiere de la zone. */
    private static void publish(SurfaceAtlas.Job job, int base) {
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

    private static void uploadTiles(int x, int row, int z) {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_TILES);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tiles);
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
     * L'empreinte de chaque tuile tenue, par surface : deux caches identiques au bit pres ont les memes (tests du client).
     *
     * @return une empreinte par surface tenue et montree
     */
    public static Long2LongOpenHashMap fingerprints() {
        Long2LongOpenHashMap prints = new Long2LongOpenHashMap();
        ByteBuffer pixels = readAtlas();
        try {
            for (LightZone.Baked baked : KEPT.values()) {
                SurfaceAtlas.Job job = ATLAS.job(baked.key());
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
    private static ByteBuffer readAtlas() {
        ByteBuffer pixels = MemoryUtil.memAlloc(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT_ATLAS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
        PixelUnpack.begin();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        return pixels;
    }

    /**
     * Les pixels de la tuile d'une surface, de bas en haut de sa face (tests du client).
     *
     * @param x cellule d'air devant la surface
     * @param y cellule d'air devant la surface
     * @param z cellule d'air devant la surface
     * @param face face de la surface
     * @return quatre octets par pixel, ou {@code null} si elle n'a pas de tuile montree
     */
    public static byte @Nullable [] surfacePixels(int x, int y, int z, int face) {
        SurfaceAtlas.Job job = ATLAS.job(ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (job == null) {
            return null;
        }
        int cell = (z & SECTION - 1) << 8 | (y & SECTION - 1) << 4 | x & SECTION - 1;
        for (int i = 0; i < job.surfaces().length; i++) {
            int surface = job.surfaces()[i];
            if (job.shown(i) && SurfaceAtlas.cell(surface) == cell && SurfaceAtlas.face(surface) == face) {
                return tilePixels(job.tiles()[i]);
            }
        }
        return null;
    }

    private static byte[] tilePixels(int tile) {
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
     * L'empreinte des rebonds de chaque surface tenue : deux calculs identiques au bit pres ont les memes (tests du client).
     *
     * @return une empreinte par surface tenue et montree
     */
    public static Long2LongOpenHashMap bounceFingerprints() {
        Long2LongOpenHashMap prints = new Long2LongOpenHashMap();
        byte[] pixels = LightBounces.read();
        for (LightZone.Baked baked : KEPT.values()) {
            SurfaceAtlas.Job job = ATLAS.job(baked.key());
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
     * La moyenne des rebonds de chaque surface tenue (tests du client).
     *
     * @return une moyenne par surface tenue et montree
     */
    public static Long2DoubleOpenHashMap bounceMeans() {
        Long2DoubleOpenHashMap means = new Long2DoubleOpenHashMap();
        byte[] pixels = LightBounces.read();
        for (LightZone.Baked baked : KEPT.values()) {
            SurfaceAtlas.Job job = ATLAS.job(baked.key());
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

    /** Tuiles calculees depuis le lancement (tests du client). */
    public static long bakedTiles() {
        return bakedTiles;
    }

    /** Tuiles libres (tests du client). */
    public static int freeTiles() {
        return ATLAS.freeTiles();
    }

    /**
     * Ramene le cache a ce nombre de tuiles : il se remplit comme dans une salle plus grande (tests du client).
     *
     * @param tiles tuiles du cache
     */
    public static void limitTiles(int tiles) {
        ATLAS.limit(tiles);
    }

    /** {@code true} si une section attend des tuiles (tests du client). */
    public static boolean starving() {
        return ATLAS.starving();
    }

    /** Sections qui gardent des tuiles hors du rayon de l'ancrage (tests du client). */
    public static int sectionsBeyondRadius() {
        int count = 0;
        for (LightZone.Baked baked : KEPT.values()) {
            count += ATLAS.holds(baked.key()) && !inRadius(baked.key(), lastAnchorX, lastAnchorZ) ? 1 : 0;
        }
        return count;
    }

    /**
     * Les surfaces cuites assez proches d'un point qui n'ont pas de tuile montree (tests du client).
     *
     * @param x point
     * @param y point
     * @param z point
     * @param reach distance au point, en blocs, sur X et Z
     * @return leur nombre, hors de la bande de hauteur des tuiles
     */
    public static int surfacesWithoutTile(double x, int y, double z, double reach) {
        int count = 0;
        for (LightZone.Baked baked : KEPT.values()) {
            long key = baked.key();
            int originX = ChunkSectionPos.unpackX(key) << 4;
            int originY = ChunkSectionPos.unpackY(key) << 4;
            int originZ = ChunkSectionPos.unpackZ(key) << 4;
            SurfaceAtlas.Job job = ATLAS.job(key);
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

    /** La section courante en attente, ou {@code null} (tests du client). */
    public static SurfaceAtlas.@Nullable Job pending() {
        return ATLAS.current();
    }
}
