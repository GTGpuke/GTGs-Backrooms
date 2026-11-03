package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.client.FarVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightBounces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightCell;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * La lumiere qui ne saute pas, dans un vrai client : une piece eclairee l'est autant vue de loin que de pres, en High comme en Ultra, et
 * une piece quittee revient telle quelle quand le joueur y retourne. Vus de loin, les neons ne s'allument pas. Rien ne suit la grille des
 * sections : un cache de surfaces trop petit sert d'abord ce qui est proche, une autre hauteur ne relit rien de l'ancienne, et le
 * contraste d'une texture ne depend pas de ce que la grille des blocs garde au-dela de la zone.
 */
final class LightContinuityClientTests {

    /** Longueur de la galerie, en blocs : son fond se voit au-dela de ce que la zone couvre. */
    private static final int LENGTH = 66;
    private static final int HALF_WIDTH = 2;
    private static final int HEIGHT = 4;
    private static final int NEON_EVERY = 5;
    /** Ce que le fond de la galerie peut perdre ou gagner au plus, vu de loin. */
    private static final double MIN_FAR = 0.6;
    private static final double MAX_FAR = 1.4;
    /** Lumiere minimale d'une galerie eclairee. */
    private static final double MIN_LIT = 40.0;
    /** Cellules du loin cuites qu'il faut au moins hors de la fenetre de la zone : une par section qu'il couvre en plus d'elle. */
    private static final int MIN_FAR_CELLS = ((2 * LightZone.FAR_RADIUS + 1) * (2 * LightZone.FAR_RADIUS + 1) - (2 * LightZone.RADIUS + 1)
            * (2 * LightZone.RADIUS + 1)) * LightZone.HEIGHT;
    /** Ecart moyen au plus entre deux images d'une meme vue, au retour : le grain du film seul. */
    private static final double MAX_RETURN = 2.5;
    /** Assez loin pour que la piece quitte la fenetre de la zone, assez pres pour que la zone garde encore ses sections cuites. */
    private static final int AWAY = 72;
    /** Une section de cote : une rangee neuve de surfaces entre dans le cache, a cote de celles qui y sont. */
    private static final int ONE_SECTION = 20;
    /** Ce qu'une surface neuve vaut au moins et au plus de son etat final. */
    private static final double MIN_FRESH = 0.88;
    private static final double MAX_FRESH = 1.25;
    /** Surfaces neuves qu'il faut au moins pour juger. */
    private static final int MIN_FRESH_SURFACES = 100;
    /** Ce que vaut la meme surface dans la zone, en parts de ce qu'elle vaut en tuiles : au-dela, un anneau se voit autour de la camera. */
    private static final double MIN_ZONE = 0.8;
    private static final double MAX_ZONE = 1.2;
    /** Le plus petit cache : presque tout est lu dans la zone. */
    private static final int NO_TILES = 1;
    /** Y de la dalle du puits : sa section est hors de ce que voit la derniere section de la zone. */
    private static final int SHAFT_TOP = 34;
    /** Anneau de l'image ou se voient les murs du haut du puits, en pixels autour de son centre. */
    private static final int RING_MIN = 16;
    private static final int RING_MAX = 28;
    /** Lumiere minimale du haut du puits. */
    private static final double MIN_SHAFT_LIGHT = 35.0;
    /** Tuiles d'un cache trop petit pour le rayon qu'elles couvrent, assez grand pour tout ce qui est proche. */
    private static final int SHORT_CACHE = 7000;
    /** Distance a laquelle une surface doit avoir sa tuile, en blocs : les sections autour de la camera. */
    private static final double SERVED_REACH = 16.0;
    /** Assez haut pour que la bande des tuiles bouge et prenne des surfaces de la couche du dessus, assez bas pour garder la fenetre de la zone. */
    private static final int HIGHER = 4;
    /** Distance a laquelle une surface doit avoir sa tuile quand la bande en demande plus que le cache n'en a pour tout ce qui est proche. */
    private static final double SERVED_HIGHER = 8.0;
    /** Hauteur d'un saut qui change la fenetre de la zone, en blocs. */
    private static final int OTHER_HEIGHT = 40;
    /** Part de ses cellules valides qu'une texture videe garde au plus, sections deja arrivees comprises. */
    private static final double MAX_LEFT_AFTER_WIPE = 0.25;
    /** Ce qui reste des cellules d'une texture ou la lumiere est arrivee : au moins ce nombre. */
    private static final int MIN_VALID_CELLS = 1000;
    private static final Identifier MARCH_LIBRARY = Identifier.of(ModConstants.MOD_ID, "shaders/include/block_march.glsl");
    private static final Identifier LOOK_LIBRARY = Identifier.of(ModConstants.MOD_ID, "shaders/include/look.glsl");
    private static final Identifier OTHER_TEXTURE = Identifier.of(ModConstants.MOD_ID, "block/level_zero_ceiling_tile");
    private static final String CALM_START = "// Le niveau de la texture d'un bloc";
    private static final String CALM_LAST = "vec3 gtgs_calmed(vec3 color) {";
    private static final String CALM_HEAD = """
            #version 150
            uniform ivec3 gtgs_CameraBlock;
            uniform vec3 gtgs_CameraFract;
            uniform sampler2D gtgs_Materials;
            uniform sampler2D gtgs_BlockAtlas;
            uniform sampler2D gtgs_BlockMaterial;
            uniform vec3 gtgs_LightLook;
            uniform float gtgs_Traced;
            vec3 gtgs_WorldRel;
            vec3 gtgs_Normal = vec3(0.0, 1.0, 0.0);
            vec2 gtgs_Uv;
            const float GTGS_ZONE_FADE = 40.0;
            const float GTGS_ZONE_END = 48.0;
            """;
    /** Trois pixels par cas : la couleur calmee, la couleur moyenne de la texture, et celle de la matiere du bloc, a travers la grille. */
    private static final String CALM_MAIN = """
            uniform vec4 gtgs_TestRel[64];
            uniform vec2 gtgs_TestUv;
            out vec4 fragColor;
            void main() {
                int pixel = int(gl_FragCoord.x);
                gtgs_WorldRel = gtgs_TestRel[pixel / 3].xyz;
                gtgs_Uv = gtgs_TestUv;
                ivec3 block = gtgs_CameraBlock + ivec3(floor(gtgs_CameraFract + gtgs_WorldRel - gtgs_Normal * 0.01));
                vec3[3] shown = vec3[3](gtgs_calmed(vec3(1.0)), textureLod(gtgs_BlockAtlas, gtgs_Uv, 16.0).rgb, texelFetch(gtgs_Materials, ivec2(gtgs_material(block), 0), 0).rgb);
                fragColor = vec4(shown[pixel % 3], 1.0);
            }
            """;
    /** Part du contraste gardee dans le test du calme, et ecart permis sur une couleur relue. */
    private static final float DETAIL = 0.25f;
    private static final float COLOR_TOLERANCE = 1.0e-3f;
    /** Couleur d'un bloc inconnu, opaque : celle de ce que la grille tient pour plein sans le connaitre. */
    private static final float UNKNOWN_ALBEDO = 0.5f;
    /** Ecart qu'il faut entre la matiere du bloc et la couleur de l'autre texture : le test distingue les deux lectures. */
    private static final float MIN_COLOR_GAP = 0.05f;
    private static final int QUIET_FRAMES = 40;
    private static final int SETTLE_TICKS = 2400;
    private static final Map<BlockPos, BlockState> REPLACED = new HashMap<>();
    private static double ceilingTiles;
    private static double floorTiles;
    private static int quiet;
    private static Vec3d origin;
    private static double far;
    private static int[] before;
    private static int validBefore;
    private static int baseBefore;
    private static Long2DoubleOpenHashMap known;
    private static Long2DoubleOpenHashMap fresh;

    private LightContinuityClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(sectionsComeBack(), freshSurfacesAreBright(), farRoomStaysLit(), zoneMeetsTheTiles(), shortCacheServesTheNearest(),
                otherHeightStartsClean(), texturesStayCalmBeyondTheGrid(), reloadedColumnIsCopiedAgain(), shaftAboveTheZone());
    }

    private static boolean settled(ClientTestContext context) {
        quiet = LightSurfaces.pending() == null && LightBounces.settled() && LightCopy.idle(context.world()) ? quiet + 1 : 0;
        if (quiet >= QUIET_FRAMES) {
            quiet = 0;
            return true;
        }
        return false;
    }

    private static void tier(ClientTestContext context, LookQuality quality) {
        LookResources.applySettings(LookSettings.current().withQuality(quality.key()), context.client());
    }

    private static void look(ClientTestContext context, float yaw, float pitch) {
        context.player().setYaw(yaw);
        context.player().setPitch(pitch);
    }

    private static void teleport(ClientTestContext context, double x, double y, double z) {
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            ClientTestContext.serverPlayer(server).teleport(world, x, y, z, 0.0f, 0.0f);
        });
    }

    private static int[] pixels(NativeImage image) {
        int[] copy = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                copy[y * image.getWidth() + x] = image.getColor(x, y);
            }
        }
        return copy;
    }

    private static double luminance(int abgr) {
        return 0.299 * (abgr & 0xFF) + 0.587 * (abgr >> 8 & 0xFF) + 0.114 * (abgr >> 16 & 0xFF);
    }

    private static ClientScenario arrival(String name, LookQuality quality) {
        return ClientScenario.named(name)
                .run("palier", context -> tier(context, quality))
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .ticks(40)
                .await("lumiere au repos", LightContinuityClientTests::settled, SETTLE_TICKS);
    }

    /** Une section que la fenetre de la zone a quittee, puis retrouvee, repart vers la texture : sans cela, la piece reste noire. */
    private static ClientScenario sectionsComeBack() {
        return arrival("sections_come_back", LookQuality.ULTRA)
                .run("regard", context -> {
                    origin = context.player().getPos();
                    look(context, 200.0f, 0.0f);
                })
                .frames(8)
                .capture("la piece avant de partir", image -> before = pixels(image))
                .run("loin de la piece", context -> teleport(context, origin.x + AWAY, origin.y, origin.z))
                .ticks(60)
                .await("repos au loin", LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("retour", context -> teleport(context, origin.x, origin.y, origin.z))
                .ticks(60)
                .await("repos au retour", LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("meme regard", context -> look(context, 200.0f, 0.0f))
                .frames(8)
                .capture("la piece revient telle quelle", image -> {
                    double sum = 0.0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            sum += Math.abs(luminance(image.getColor(x, y)) - luminance(before[y * image.getWidth() + x]));
                        }
                    }
                    double mean = sum / (image.getWidth() * image.getHeight());
                    ModConstants.LOGGER.info("[Test client] Retour dans une piece : ecart moyen {}.", String.format(Locale.ROOT, "%.2f", mean));
                    ClientTestContext.require(mean <= MAX_RETURN, String.format(Locale.ROOT, "La piece ne revient pas telle quelle : ecart moyen %.2f.", mean));
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * En marchant, une rangee neuve de surfaces arrive a cote de surfaces deja la. Son premier rebond doit deja valoir presque son etat
     * final : sinon la lumiere fait des rectangles, jusqu'au balayage suivant.
     */
    private static ClientScenario freshSurfacesAreBright() {
        return arrival("fresh_surfaces_are_bright", LookQuality.ULTRA)
                .run("releve des surfaces connues", context -> {
                    origin = context.player().getPos();
                    known = LightSurfaces.bounceMeans();
                })
                .run("une section plus loin", context -> teleport(context, origin.x + ONE_SECTION, origin.y, origin.z))
                .await("le balayage commence", context -> LightBounces.inFirstRound(), SETTLE_TICKS)
                .run("releve des surfaces neuves", context -> fresh = LightSurfaces.bounceMeans())
                .ticks(4)
                .await("repos", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("une surface neuve vaut deja presque son etat final", context -> {
                    Long2DoubleOpenHashMap last = LightSurfaces.bounceMeans();
                    double before = 0.0;
                    double after = 0.0;
                    int count = 0;
                    for (Long2DoubleOpenHashMap.Entry entry : fresh.long2DoubleEntrySet()) {
                        if (!known.containsKey(entry.getLongKey()) && last.containsKey(entry.getLongKey())) {
                            before += entry.getDoubleValue();
                            after += last.get(entry.getLongKey());
                            count++;
                        }
                    }
                    ModConstants.LOGGER.info("[Test client] {} surfaces neuves : {} puis {} au repos.", count, String.format(Locale.ROOT, "%.2f", before / count),
                            String.format(Locale.ROOT, "%.2f", after / count));
                    ClientTestContext.require(count >= MIN_FRESH_SURFACES, "Seulement " + count + " surfaces neuves.");
                    ClientTestContext.require(before >= after * MIN_FRESH && before <= after * MAX_FRESH,
                            String.format(Locale.ROOT, "Une surface neuve n'est pas a son niveau : %.2f pour %.2f.", before / count, after / count));
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * La zone prend le relais des tuiles a 24 blocs : elle doit valoir la meme chose, surtout pour ce qui ne vit que de rebonds. Le plafond et le
     * sol, vus avec leurs tuiles, puis presque sans : la zone seule montre ce qu'on verrait au loin dans une grande salle.
     */
    private static ClientScenario zoneMeetsTheTiles() {
        return arrival("zone_meets_the_tiles", LookQuality.HIGH)
                .run("palier Ultra, en jeu", context -> tier(context, LookQuality.ULTRA))
                .ticks(40)
                .await("repos en Ultra", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("la zone suit le palier", context -> {
                    LightBakeSettings wanted = LightBakeSettings.of(LookProfiles.of(context.world().getRegistryKey()), context.world().getRegistryKey().getValue());
                    LightBakeSettings baked = LightCopy.settings(context.world());
                    ClientTestContext.require(wanted.equals(baked), "La zone garde les reglages de l'ancien palier : " + baked + " contre " + wanted + ".");
                })
                .run("regard au plafond, tuiles", context -> look(context, 200.0f, -70.0f))
                .frames(8)
                .capture("le plafond en tuiles", image -> ceilingTiles = Images.luminance(Images.mean(image, 0.2, 0.2, 0.8, 0.8)))
                .run("regard au sol, tuiles", context -> look(context, 200.0f, 50.0f))
                .frames(8)
                .capture("le sol en tuiles", image -> floorTiles = Images.luminance(Images.mean(image, 0.2, 0.2, 0.8, 0.8)))
                .run("presque plus de tuiles", context -> {
                    LightSurfaces.limitTiles(NO_TILES);
                    LightVolume.rebake();
                })
                .ticks(40)
                .await("repos sans tuiles", LightContinuityClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("le sol dans la zone", image -> requireZone("sol", floorTiles, Images.luminance(Images.mean(image, 0.2, 0.2, 0.8, 0.8))))
                .run("regard au plafond, zone", context -> look(context, 200.0f, -70.0f))
                .frames(8)
                .capture("le plafond dans la zone", image -> requireZone("plafond", ceilingTiles, Images.luminance(Images.mean(image, 0.2, 0.2, 0.8, 0.8))))
                .cleanup(context -> {
                    LightSurfaces.limitTiles(SurfaceAtlas.CAPACITY);
                    LightVolume.rebake();
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static void requireZone(String surface, double tiles, double zone) {
        ModConstants.LOGGER.info("[Test client] Zone contre tuiles, {} : {} contre {}.", surface, String.format(Locale.ROOT, "%.1f", zone),
                String.format(Locale.ROOT, "%.1f", tiles));
        ClientTestContext.require(zone >= tiles * MIN_ZONE && zone <= tiles * MAX_ZONE,
                String.format(Locale.ROOT, "La zone ne vaut pas les tuiles au %s : %.1f contre %.1f.", surface, zone, tiles));
    }

    /**
     * Une grande salle ouverte demande plus de tuiles que le cache n'en a : les sections proches sont servies d'abord, celles qui sortent
     * du rayon rendent les leurs avant que les autres n'en prennent, et une section servie a moitie finit servie. Sinon elle garde en
     * carre, de pres, la lumiere de la zone.
     */
    private static ClientScenario shortCacheServesTheNearest() {
        return arrival("short_cache_serves_the_nearest", LookQuality.ULTRA)
                .run("cache trop petit", context -> {
                    origin = context.player().getPos();
                    LightSurfaces.limitTiles(SHORT_CACHE);
                    LightVolume.rebake();
                })
                .ticks(40)
                .await("repos, cache plein", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("il manque des tuiles", context -> ClientTestContext.require(LightSurfaces.starving(), "Le cache suffit : le test ne prouve rien."))
                .run("une section plus loin", context -> teleport(context, origin.x + ONE_SECTION, origin.y, origin.z))
                .ticks(40)
                .await("repos une section plus loin", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("tout ce qui est proche a sa tuile", LightContinuityClientTests::requireNearestServed)
                .run("en diagonale", context -> teleport(context, origin.x + ONE_SECTION * 2, origin.y, origin.z + ONE_SECTION))
                .ticks(40)
                .await("repos en diagonale", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("tout ce qui est proche a toujours sa tuile", LightContinuityClientTests::requireNearestServed)
                .run("un peu plus haut", context -> teleport(context, origin.x + ONE_SECTION * 2, origin.y + HIGHER, origin.z + ONE_SECTION))
                .ticks(40)
                .await("repos un peu plus haut", LightContinuityClientTests::settled, SETTLE_TICKS)
                .check("la bande suit la camera, le proche d'abord", context -> requireServed(context, SERVED_HIGHER))
                .cleanup(context -> {
                    LightSurfaces.limitTiles(SurfaceAtlas.CAPACITY);
                    LightVolume.rebake();
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static void requireNearestServed(ClientTestContext context) {
        requireServed(context, SERVED_REACH);
    }

    private static void requireServed(ClientTestContext context, double reach) {
        int missing = LightSurfaces.surfacesWithoutTile(context.player().getX(), context.player().getBlockY() + 1, context.player().getZ(), reach);
        int beyond = LightSurfaces.sectionsBeyondRadius();
        ModConstants.LOGGER.info("[Test client] Surfaces proches sans tuile, cache trop petit : {}, et {} sections hors du rayon en gardent.", missing, beyond);
        ClientTestContext.require(missing == 0, missing + " surfaces a moins de " + reach + " blocs n'ont pas de tuile.");
        ClientTestContext.require(beyond == 0, beyond + " sections gardent leurs tuiles hors du rayon.");
    }

    /** Les cellules valides de la texture de zone, relue sur le processeur. */
    private static int validCells() {
        int size = LightVolume.WIDTH * LightVolume.ROWS * LightCell.FACES * 2 * LightVolume.WIDTH * Integer.BYTES;
        ByteBuffer cells = MemoryUtil.memAlloc(size);
        try {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, cells);
            GlStateManager._activeTexture(previousUnit);
            int valid = 0;
            for (int at = 0; at < size; at += Integer.BYTES) {
                valid += cells.getInt(at) >>> 30 != 0 ? 1 : 0;
            }
            return valid;
        } finally {
            MemoryUtil.memFree(cells);
        }
    }

    /**
     * Une autre hauteur est une autre fenetre : ce que la texture de zone tenait se relirait a la mauvaise hauteur, par carres de
     * sections. La texture est donc videe d'un coup, avant la premiere image a cette hauteur.
     */
    private static ClientScenario otherHeightStartsClean() {
        return arrival("other_height_starts_clean", LookQuality.ULTRA)
                .run("releve de la zone", context -> {
                    origin = context.player().getPos();
                    validBefore = validCells();
                    baseBefore = LightVolume.baseY();
                    ClientTestContext.require(validBefore >= MIN_VALID_CELLS, "Zone vide : seulement " + validBefore + " cellules.");
                })
                .run("une autre hauteur", context -> teleport(context, origin.x, origin.y + OTHER_HEIGHT, origin.z))
                .await("la fenetre suit la camera", context -> LightVolume.baseY() != baseBefore, 400)
                .check("rien de l'ancienne hauteur ne reste", context -> {
                    int left = validCells();
                    ModConstants.LOGGER.info("[Test client] Zone a l'autre hauteur : {} cellules valides sur {} avant.", left, validBefore);
                    ClientTestContext.require(left <= validBefore * MAX_LEFT_AFTER_WIPE, "La zone garde " + left + " cellules de l'ancienne hauteur, sur " + validBefore + ".");
                })
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * La grille des blocs ne couvre que la zone et se repete au-dela : le contraste d'une texture se calme d'apres elle de pres, et d'apres
     * la texture elle-meme au-dela, en largeur comme en hauteur. La fonction est celle des shaders, sur la vraie grille.
     */
    private static ClientScenario texturesStayCalmBeyondTheGrid() {
        return arrival("textures_stay_calm_beyond_the_grid", LookQuality.ULTRA)
                .check("le calme ne depend pas de ce que la grille repete", LightContinuityClientTests::requireCalmBeyondTheGrid)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static void requireCalmBeyondTheGrid(ClientTestContext context) {
        String look = LookGradeClientTests.library(context.client(), LOOK_LIBRARY);
        int start = look.indexOf(CALM_START);
        int end = look.indexOf("\n}\n", look.indexOf(CALM_LAST, start));
        ClientTestContext.require(start >= 0 && end > start, "gtgs_calmed introuvable dans " + LOOK_LIBRARY + ".");
        String source = CALM_HEAD + LookGradeClientTests.library(context.client(), MARCH_LIBRARY) + look.substring(start, end + 2) + "\n" + CALM_MAIN;
        Sprite sprite = context.client().getBakedModelManager().getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getSprite(OTHER_TEXTURE);
        BlockPos floor = context.player().getBlockPos().down();
        // Le sol sous le joueur, dans la grille ; a un tour de la grille en largeur, ou elle redonne le meme bloc ; bien plus bas que la zone ;
        // le bloc sous le sol, sous le monde : la fenetre de la premiere couche en contient une section, que la grille tient pour opaque.
        float[][] cases = {{0.0f, -0.5f, 0.0f}, {128.0f, -0.5f, 0.0f}, {0.0f, -0.5f, 128.0f}, {0.0f, -64.5f, 0.0f}, {0.0f, -1.5f, 0.0f}};
        boolean[] beyond = {false, true, true, true, false};
        int below = cases.length - 1;
        float[] pixels = calmPixels(context, source, sprite, floor, cases, 1.0f);
        // Sans lumiere tracee, la table des matieres n'existe pas : tout se lit dans la texture, meme dans la grille.
        float[] plain = calmPixels(context, source, sprite, floor, cases, 0.0f);
        for (int i = 0; i < cases.length; i++) {
            for (int channel = 0; channel < 3; channel++) {
                float expected = plain[(i * 3 + 1) * 4 + channel] * (1.0f - DETAIL) + DETAIL;
                ClientTestContext.require(Math.abs(plain[i * 12 + channel] - expected) <= COLOR_TOLERANCE, String.format(Locale.ROOT,
                        "Sans lumiere tracee, cas %d, composante %d : %.4f au lieu de %.4f.", i, channel, plain[i * 12 + channel], expected));
            }
        }
        checkCalmCases(pixels, cases, beyond, below, floor);
    }

    private static float[] calmPixels(ClientTestContext context, String source, Sprite sprite, BlockPos floor, float[][] cases, float traced) {
        return LookGradeClientTests.render(source, 1, program -> {
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "gtgs_Traced"), traced);
            GL20.glUniform3i(GL20.glGetUniformLocation(program, "gtgs_CameraBlock"), floor.getX(), floor.getY() + 1, floor.getZ());
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "gtgs_CameraFract"), 0.5f, 0.5f, 0.5f);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_LightBase"), LightVolume.baseY());
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "gtgs_LightLook"), DETAIL, 0.0f, 0.0f);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "gtgs_TestUv"), (sprite.getMinU() + sprite.getMaxU()) * 0.5f, (sprite.getMinV() + sprite.getMaxV()) * 0.5f);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, LightSurfaces.SAMPLER_BLOCKS), LightSurfaces.UNIT_BLOCKS);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, LightBounces.SAMPLER_MATERIALS), LightBounces.UNIT_MATERIALS);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "gtgs_BlockAtlas"), LightSurfaces.UNIT_SPRITES);
            for (int i = 0; i < cases.length; i++) {
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestRel[" + i + "]"), cases[i][0], cases[i][1], cases[i][2], 0.0f);
            }
        });
    }

    private static void checkCalmCases(float[] pixels, float[][] cases, boolean[] beyond, int below, BlockPos floor) {
        for (int i = 0; i < cases.length; i++) {
            for (int channel = 0; channel < 3; channel++) {
                float calmed = pixels[i * 12 + channel];
                float whole = pixels[(i * 3 + 1) * 4 + channel];
                float table = pixels[(i * 3 + 2) * 4 + channel];
                // Un bloc que la grille ne connait pas garde sa texture.
                float expected = i == below ? 1.0f : (beyond[i] ? whole : table) * (1.0f - DETAIL) + DETAIL;
                ClientTestContext.require(Math.abs(calmed - expected) <= COLOR_TOLERANCE, String.format(Locale.ROOT,
                        "Cas %d, composante %d : %.4f au lieu de %.4f (matiere %.4f, texture %.4f).", i, channel, calmed, expected, table, whole));
            }
        }
        float gap = Math.abs(pixels[1 * 4] - pixels[2 * 4]) + Math.abs(pixels[1 * 4 + 2] - pixels[2 * 4 + 2]);
        ClientTestContext.require(gap >= MIN_COLOR_GAP, "La texture et la matiere se ressemblent trop pour distinguer les deux lectures : " + gap + ".");
        ClientTestContext.require(floor.getY() == 0 && LightVolume.baseY() < 0, "Le joueur n'est pas au fond du monde : le dessous n'est pas lu.");
        float underWorld = pixels[(below * 3 + 2) * 4];
        ClientTestContext.require(Math.abs(underWorld - UNKNOWN_ALBEDO) <= COLOR_TOLERANCE, "Le dessous du monde n'est pas opaque dans la grille : " + underWorld + ".");
    }

    /** Une colonne qui revient est a recopier, meme si la camera n'a pas bouge : sa copie est perimee, et la cuisson attend ses sections. */
    private static ClientScenario reloadedColumnIsCopiedAgain() {
        return arrival("reloaded_column_is_copied_again", LookQuality.HIGH)
                .run("la colonne du joueur revient", context -> {
                    BlockPos feet = context.player().getBlockPos();
                    ClientTestContext.require(LightCopy.copiedBlock(context.world(), feet) == LightBlocks.AIR, "L'air du joueur n'est pas copie.");
                    WorldChunk chunk = context.world().getChunkManager().getWorldChunk(feet.getX() >> 4, feet.getZ() >> 4);
                    ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(context.world(), chunk);
                    ClientTestContext.require(LightCopy.copiedBlock(context.world(), feet) == LightBlocks.UNKNOWN, "La copie perimee reste.");
                })
                .await("la colonne est recopiee", context -> LightCopy.copiedBlock(context.world(), context.player().getBlockPos()) == LightBlocks.AIR, 200)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Un puits ferme, du joueur jusqu'a une dalle bien au-dessus de la zone : ni la zone ni sa cuisson ne voient cette dalle. */
    private static void buildShaft(ClientTestContext context) {
        BlockPos start = context.player().getBlockPos();
        origin = Vec3d.ofBottomCenter(start);
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            BlockState wall = ModBlocks.LEVEL_ZERO_WALLPAPER.getDefaultState();
            BlockState neon = ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState();
            for (int y = start.getY(); y <= SHAFT_TOP; y++) {
                for (int z = -2; z <= 2; z++) {
                    for (int x = -2; x <= 2; x++) {
                        boolean side = Math.max(Math.abs(x), Math.abs(z)) == 2;
                        boolean top = y == SHAFT_TOP;
                        BlockPos pos = new BlockPos(start.getX() + x, y, start.getZ() + z);
                        set(world, pos, top && x == 0 && z == 0 ? neon : side || top ? wall : Blocks.AIR.getDefaultState());
                    }
                }
            }
        });
    }

    /** La luminance moyenne d'un anneau autour du centre de l'image. */
    private static double ring(NativeImage image) {
        double sum = 0.0;
        int count = 0;
        int middleX = image.getWidth() / 2;
        int middleY = image.getHeight() / 2;
        for (int y = middleY - RING_MAX; y <= middleY + RING_MAX; y++) {
            for (int x = middleX - RING_MAX; x <= middleX + RING_MAX; x++) {
                int squared = (x - middleX) * (x - middleX) + (y - middleY) * (y - middleY);
                if (squared >= RING_MIN * RING_MIN && squared <= RING_MAX * RING_MAX) {
                    sum += luminance(image.getColor(x, y));
                    count++;
                }
            }
        }
        return sum / count;
    }

    /**
     * Le haut d'un puits sort de la hauteur de la zone, et sa lumiere vient de la lightmap : lire la zone s'arreterait a sa derniere
     * rangee, sous une dalle qu'elle ne voit pas, et un mur eclaire resterait noir au-dela d'une limite de section.
     */
    private static ClientScenario shaftAboveTheZone() {
        return arrival("shaft_above_the_zone", LookQuality.ULTRA)
                .run("un puits jusqu'a une dalle", LightContinuityClientTests::buildShaft)
                .ticks(10)
                .run("au fond du puits", context -> teleport(context, origin.x, origin.y, origin.z))
                .ticks(60)
                .await("repos au fond du puits", LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("regard vers le haut", context -> look(context, 0.0f, -90.0f))
                .frames(8)
                .capture("le haut du puits, au-dessus de la zone, est eclaire", image -> {
                    double top = ring(image);
                    ModConstants.LOGGER.info("[Test client] Haut du puits : {}.", String.format(Locale.ROOT, "%.1f", top));
                    ClientTestContext.require(top >= MIN_SHAFT_LIGHT, String.format(Locale.ROOT, "Le haut du puits est noir : %.1f.", top));
                })
                .cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        REPLACED.putIfAbsent(pos.toImmutable(), world.getBlockState(pos));
        world.setBlockState(pos, state);
    }

    /** Une galerie droite, eclairee sur toute sa longueur, posee a travers le Level 0 : son fond se voit de tres loin. */
    private static void build(ClientTestContext context) {
        origin = context.player().getPos();
        BlockPos start = context.player().getBlockPos();
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            BlockState wall = ModBlocks.LEVEL_ZERO_WALLPAPER.getDefaultState();
            BlockState floor = ModBlocks.LEVEL_ZERO_DAMP_CARPET.getDefaultState();
            BlockState ceiling = ModBlocks.LEVEL_ZERO_CEILING_TILE.getDefaultState();
            BlockState neon = ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState();
            for (int z = -1; z <= LENGTH + 1; z++) {
                for (int y = -1; y <= HEIGHT; y++) {
                    for (int x = -HALF_WIDTH - 1; x <= HALF_WIDTH + 1; x++) {
                        boolean side = Math.abs(x) > HALF_WIDTH || z < 0 || z > LENGTH;
                        boolean lamp = y == HEIGHT && x == 0 && z % NEON_EVERY == 2;
                        set(world, start.add(x, y, z), y < 0 ? floor : lamp ? neon : y == HEIGHT ? ceiling : side ? wall : Blocks.AIR.getDefaultState());
                    }
                }
            }
        });
    }

    private static void restore(ClientTestContext context) {
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            REPLACED.forEach(world::setBlockState);
            REPLACED.clear();
        });
    }

    /** La luminance du centre de l'image : le fond de la galerie. */
    private static double center(NativeImage image) {
        double sum = 0.0;
        int count = 0;
        int middleX = image.getWidth() / 2;
        int middleY = image.getHeight() / 2;
        for (int y = middleY - 6; y < middleY + 6; y++) {
            for (int x = middleX - 6; x < middleX + 6; x++) {
                sum += luminance(image.getColor(x, y));
                count++;
            }
        }
        return sum / count;
    }

    private static ClientScenario gallery(ClientScenario scenario, String tier) {
        return scenario
                .run("au debut de la galerie, " + tier, context -> teleport(context, origin.x, origin.y, origin.z))
                .ticks(40)
                .await("repos au debut, " + tier, LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("regard vers le fond, " + tier, context -> look(context, 0.0f, 0.0f))
                .frames(8)
                .capture("le fond, vu de loin, " + tier, image -> far = center(image))
                .run("pres du fond, " + tier, context -> teleport(context, origin.x, origin.y, origin.z + LENGTH - 16))
                .ticks(40)
                .await("repos pres du fond, " + tier, LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("regard vers le fond de pres, " + tier, context -> look(context, 0.0f, 0.0f))
                .frames(8)
                .capture("le fond, vu de pres, " + tier, image -> {
                    double near = center(image);
                    ModConstants.LOGGER.info("[Test client] Fond de la galerie en {} : {} de loin, {} de pres.", tier, String.format(Locale.ROOT, "%.0f", far),
                            String.format(Locale.ROOT, "%.0f", near));
                    ClientTestContext.require(near > MIN_LIT, String.format(Locale.ROOT, "La galerie n'est pas eclairee en %s : %.0f.", tier, near));
                    ClientTestContext.require(far >= near * MIN_FAR && far <= near * MAX_FAR,
                            String.format(Locale.ROOT, "Le fond change de lumiere en s'approchant, en %s : %.0f de loin, %.0f de pres.", tier, far, near));
                });
    }

    /**
     * Une surface au-dela de la zone prend la lumiere du loin : elle doit deja valoir ce qu'elle vaudra de pres. Le loin est cuit autour
     * de la zone, dans sa texture, et le terrain la lit.
     */
    private static ClientScenario farRoomStaysLit() {
        ClientScenario scenario = arrival("far_room_stays_lit", LookQuality.HIGH)
                .run("une longue galerie", LightContinuityClientTests::build)
                .ticks(40);
        scenario = gallery(scenario, "High");
        scenario = scenario.check("le loin est cuit au-dela de la zone, et le terrain le lit", context -> {
            int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
            int location = GL20.glGetUniformLocation(program, FarVolume.SAMPLER);
            ClientTestContext.require(location >= 0 && GL20.glGetUniformi(program, location) == FarVolume.UNIT, "Le terrain ne lit pas la texture du loin.");
            int[] cells = farCellsBeyondZone(context);
            ModConstants.LOGGER.info("[Test client] Loin : {} cellules cuites hors de la zone, {} de part indirecte.", cells[0], cells[1]);
            ClientTestContext.require(cells[0] >= MIN_FAR_CELLS && cells[1] >= MIN_FAR_CELLS, "Loin presque vide hors de la zone : " + cells[0] + " cellules, "
                    + cells[1] + " de part indirecte.");
        });
        scenario = scenario.run("palier Ultra", context -> tier(context, LookQuality.ULTRA)).ticks(4);
        scenario = gallery(scenario, "Ultra");
        return scenario
                .cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * Cellules valides de la texture du loin dans les sections hors de la fenetre de la zone, autour de la camera : dans les tranches de
     * l'eclairement complet, puis dans celles de sa part indirecte.
     */
    private static int[] farCellsBeyondZone(ClientTestContext context) {
        int texture = FarVolume.texture();
        ClientTestContext.require(texture != 0, "Pas de texture du loin.");
        int width = FarVolume.WIDTH;
        int rows = FarVolume.HEIGHT;
        int slots = width / LightZone.FAR_CELLS;
        Vec3d camera = context.client().gameRenderer.getCamera().getPos();
        int centerX = Math.floorMod(ChunkSectionPos.getSectionCoord(MathHelper.floor(camera.x)), slots);
        int centerZ = Math.floorMod(ChunkSectionPos.getSectionCoord(MathHelper.floor(camera.z)), slots);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * rows * width * Integer.BYTES);
        int previousUnit = GlStateManager._getActiveTexture();
        try {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + FarVolume.UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, pixels);
            int[] count = new int[2];
            for (int z = 0; z < width; z++) {
                // Ecart signe a la section de la camera, la texture se repetant tous les slots sections.
                int dz = Math.floorMod(z / LightZone.FAR_CELLS - centerZ + slots / 2, slots) - slots / 2;
                for (int y = 0; y < rows; y++) {
                    for (int x = 0; x < width; x++) {
                        int dx = Math.floorMod(x / LightZone.FAR_CELLS - centerX + slots / 2, slots) - slots / 2;
                        boolean beyond = Math.abs(dx) > LightZone.RADIUS || Math.abs(dz) > LightZone.RADIUS;
                        if (beyond && pixels.getInt(((z * rows + y) * width + x) * Integer.BYTES) >>> 30 != 0) {
                            count[y < rows / 2 ? 0 : 1]++;
                        }
                    }
                }
            }
            return count;
        } finally {
            GlStateManager._activeTexture(previousUnit);
            MemoryUtil.memFree(pixels);
        }
    }
}
