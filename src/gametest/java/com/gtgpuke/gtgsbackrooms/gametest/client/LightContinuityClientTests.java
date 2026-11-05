package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.jetbrains.annotations.Nullable;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroArrival;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalMark;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalPlacement;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteKeys;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.client.FarVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightBounces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonLights;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightCell;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
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

    /**
     * Le portail de la vue, a ce nombre de blocs devant le joueur, et son arrivee, a peu pres a ce nombre de blocs a l'est : hors de
     * la zone du joueur mais dans ce que vanilla lui envoie, ou bien plus loin, dans l'ailleurs du Level 0.
     */
    private static final int VIEW_PORTAL_STEP = 3;
    private static final int VIEW_NEAR = 60;
    private static final int VIEW_FAR = 300;
    /** Face du sol d'une cellule d'air, et ce que le sol devant un portail eclaire gagne au moins. */
    private static final int UP_FACE = 2;
    private static final float PORTAL_LIGHT_GAIN = 0.02f;

    static List<ClientScenario> scenarios() {
        return List.of(sectionsComeBack(), lightIsReleasedWhenUnused(), freshSurfacesAreBright(), farRoomStaysLit(), zoneMeetsTheTiles(), shortCacheServesTheNearest(),
                otherHeightStartsClean(), texturesStayCalmBeyondTheGrid(), reloadedColumnIsCopiedAgain(), shaftAboveTheZone(),
                viewReadsItsZone("view_reads_its_zone", VIEW_NEAR, false),
                viewReadsItsZone("far_view_reads_its_zone", VIEW_FAR, true), portalLightReachesTheZone(), bodyBeyondShadesThroughTheFrame(),
                frameBlockingACorridor(), twoWayViewMatchesItsRebake(), oneWayViewKeepsTheLight(), seenDallesReflectThroughTheFrame(),
                portalMatchesAContinuousCorridor());
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

    /** Le portail d'une vue dans le Level 0, et la direction ou le joueur le regarde. */
    private static BlockPos viewPortal;
    private static Direction viewAhead = Direction.NORTH;

    /** Une direction ou le Level 0 est libre devant le joueur, sur la longueur ou se pose le portail. */
    private static Direction freeAhead(ClientTestContext context) {
        BlockPos feet = context.player().getBlockPos();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            boolean free = true;
            for (int step = 1; step <= VIEW_PORTAL_STEP && free; step++) {
                BlockPos ahead = feet.offset(direction, step);
                free = context.world().getBlockState(ahead).isAir() && context.world().getBlockState(ahead.up()).isAir();
            }
            if (free) {
                return direction;
            }
        }
        throw new AssertionError("Aucune direction libre devant le joueur dans le Level 0.");
    }

    /** Le portail pose pour la vue, cote client, ou {@code null} s'il n'y est pas encore. */
    private static @Nullable BackroomsPortalEntity viewPortalEntity(ClientTestContext context) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(context.world());
        for (int i = 0; i < portals.size(); i++) {
            if (viewPortal != null && portals.get(i).getPos().isInRange(Vec3d.ofCenter(viewPortal), 1.5)) {
                return portals.get(i);
            }
        }
        return null;
    }

    /**
     * La vue d'un portail vers un autre endroit du Level 0 : hors de la zone du joueur, elle a sa propre zone, cuite autour de la
     * camera de la vue, et la lit au lieu de la lightmap. Pres, la vue est le monde du joueur ; loin, au-dela de ce que vanilla lui
     * envoie, l'ailleurs du Level 0, que le serveur synchronise comme une autre dimension.
     *
     * @param distance distance de l'arrivee, a peu pres, en blocs vers l'est
     * @param elsewhere {@code true} si la vue doit etre l'ailleurs
     */
    private static ClientScenario viewReadsItsZone(String name, int distance, boolean elsewhere) {
        return arrival(name, LookQuality.HIGH)
                .run("un portail vers un autre endroit du Level 0", context -> {
                    viewAhead = freeAhead(context);
                    BlockPos feet = context.player().getBlockPos();
                    viewPortal = feet.offset(viewAhead, VIEW_PORTAL_STEP);
                    look(context, viewAhead.asRotation(), 0.0f);
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        PortalTargets.Target target = LevelZeroArrival.find(level, feet.getX() + distance, feet.getZ(), PortalFacing.NORTH, PortalSettings.DEFAULT);
                        ClientTestContext.require(target != null, "Aucune arrivee dans le Level 0 pour la vue.");
                        PortalMark mark = new PortalMark(level.getRegistryKey(),
                                BlockPos.ofFloored(target.position().subtract(0.0, PortalSettings.DEFAULT.height() / 2.0, 0.0)), Direction.NORTH);
                        PortalSettings settings = new PortalSettings(1, 2, true, true, false, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(mark));
                        level.setBlockState(viewPortal, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, viewAhead.getOpposite()));
                        if (level.getBlockEntity(viewPortal) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                    });
                })
                .await("vue du portail", context -> {
                    look(context, viewAhead.asRotation(), 0.0f);
                    BackroomsPortalEntity portal = viewPortalEntity(context);
                    RemoteWorld view = portal == null ? null : RemoteWorldRegistry.viewOf(portal);
                    return view != null && (view == RemoteWorldRegistry.get(RemoteKeys.elsewhere(BackroomsConstants.LEVEL_ZERO_WORLD))) == elsewhere;
                }, SETTLE_TICKS)
                .ticks(40)
                .await("zone de la vue cuite", context -> {
                    look(context, viewAhead.asRotation(), 0.0f);
                    return LightCopy.viewIdle();
                }, SETTLE_TICKS)
                .await("tuiles de la vue calculees", context -> {
                    look(context, viewAhead.asRotation(), 0.0f);
                    return LightSurfaces.viewHoldsTiles() && LightSurfaces.viewPending() == null;
                }, SETTLE_TICKS)
                .frames(8)
                .check("la vue lit sa zone", context -> {
                    BackroomsPortalEntity portal = viewPortalEntity(context);
                    ClientTestContext.require(portal != null, "Le portail de la vue a disparu.");
                    RemoteWorld view = RemoteWorldRegistry.viewOf(portal);
                    ClientTestContext.require(elsewhere ? view == RemoteWorldRegistry.get(RemoteKeys.elsewhere(BackroomsConstants.LEVEL_ZERO_WORLD))
                            : view != null && view.world() == context.world(), "La vue n'est pas montree par le monde attendu.");
                    ClientTestContext.require(LightVolume.viewTexture() != 0, "La zone de la vue n'a pas de texture.");
                    ClientTestContext.require(LightVolume.lastViewEntries() > 0, "La vue du portail ne lit pas la zone de sa camera.");
                    ClientTestContext.require(LightVolume.lastViewSurfaceEntries() > 0, "La vue du portail n'a pas le cache de surfaces du palier choisi.");
                })
                .cleanup(context -> context.onServer(server -> {
                    if (viewPortal != null) {
                        ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(viewPortal, Blocks.AIR.getDefaultState());
                    }
                }))
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Lumiere de la cellule du sol devant le portail, avant lui, et le neon pose devant son arrivee, avec le bloc qu'il remplace. */
    private static float floorBefore;
    private static BlockPos lamp;
    private static @Nullable BlockState lampBefore;
    /** Pied de l'arrivee du portail vers le neon. */
    private static BlockPos arrivalBottom;
    /** Blocs au-dessus du sol de l'arrivee ou chercher son plafond. */
    private static final int CEILING_SEARCH = 8;

    /** L'ouverture dont le centre est dans ce bloc, {@code -1} s'il n'y en a pas : celle d'un portail, pas celle de son retour. */
    private static int openingAt(PortalOpenings openings, BlockPos block) {
        for (int i = 0; i < openings.count(); i++) {
            if (Vec3d.ofCenter(block).isInRange(new Vec3d(openings.center(i, 0), openings.center(i, 1), openings.center(i, 2)), 1.0)) {
                return i;
            }
        }
        return -1;
    }

    /** L'eclairement complet d'une face de cellule d'air dans la texture de zone, relue sur le processeur. */
    private static float zoneRed(BlockPos cell, int face) {
        int height = LightVolume.ROWS * LightCell.FACES * 2;
        int size = LightVolume.WIDTH * height * LightVolume.WIDTH * Integer.BYTES;
        ByteBuffer cells = MemoryUtil.memAlloc(size);
        try {
            int previousUnit = GlStateManager._getActiveTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, cells);
            GlStateManager._activeTexture(previousUnit);
            int row = cell.getY() - LightVolume.baseY() + face * LightVolume.ROWS;
            int value = cells.getInt((((cell.getZ() & 127) * height + row) * LightVolume.WIDTH + (cell.getX() & 127)) * Integer.BYTES);
            return LightCell.valid(value) ? LightCell.red(value) : -1.0f;
        } finally {
            MemoryUtil.memFree(cells);
        }
    }

    /**
     * Un portail qui laisse passer la lumiere, vers un autre endroit du Level 0, la fait passer comme si les deux endroits se touchaient :
     * sa zone le compte comme un passage, avec la zone qui cuit la lumiere de l'autre cote, le sol devant lui recoit la lumiere d'un neon
     * pose la-bas, juste devant l'arrivee, et les shaders recoivent ce qui sort du passage pour les corps. Le portail parti, tout revient.
     */
    private static ClientScenario portalLightReachesTheZone() {
        return arrival("portal_light_reaches_the_zone", LookQuality.HIGH)
                .run("releve du sol, puis un portail vers un neon allume", context -> {
                    floorBefore = zoneRed(context.player().getBlockPos().offset(freeAhead(context), VIEW_PORTAL_STEP - 1), UP_FACE);
                    ClientTestContext.require(floorBefore >= 0.0f, "Le sol devant le portail n'est pas dans la zone.");
                    lampPortal(context);
                })
                .await("passage compte par la zone, avec la zone de l'autre cote", LightContinuityClientTests::passageCounted, SETTLE_TICKS)
                .await("lumiere au repos", LightContinuityClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .check("le sol devant le portail s'eclaircit", context -> {
                    float floor = zoneRed(context.player().getBlockPos().offset(viewAhead, VIEW_PORTAL_STEP - 1), UP_FACE);
                    ModConstants.LOGGER.info("[Test client] Sol devant le portail : {} avant, {} avec la lumiere qui passe.", floorBefore, floor);
                    ClientTestContext.require(floor > floorBefore + PORTAL_LIGHT_GAIN, "Le sol devant le portail ne s'eclaircit pas : " + floor + " contre " + floorBefore + ".");
                    ClientTestContext.require(NeonLights.playerOpenings() == 1, "Les shaders ne recoivent pas le passage : " + NeonLights.playerOpenings() + ".");
                })
                .run("le portail part", context -> context.onServer(server -> {
                    ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(viewPortal, Blocks.AIR.getDefaultState());
                }))
                .await("plus de passage", context -> openingAt(LightCopy.openings(context.world()), viewPortal) < 0, SETTLE_TICKS)
                .await("lumiere au repos", LightContinuityClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .check("le sol revient", context -> {
                    float floor = zoneRed(context.player().getBlockPos().offset(viewAhead, VIEW_PORTAL_STEP - 1), UP_FACE);
                    ClientTestContext.require(Math.abs(floor - floorBefore) < PORTAL_LIGHT_GAIN, "Le sol garde la lumiere du portail parti : " + floor + " contre "
                            + floorBefore + ".");
                    ClientTestContext.require(NeonLights.playerOpenings() == 0, "Les shaders gardent un passage.");
                })
                .cleanup(LightContinuityClientTests::removeLampPortal)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * Un portail devant le joueur, qui laisse passer la lumiere, vers un autre endroit du Level 0 a l'est, dans ce que vanilla envoie,
     * et un neon au plafond juste devant son arrivee : la lumiere cuite de l'autre cote ne doit rien au hasard.
     */
    private static void lampPortal(ClientTestContext context) {
        viewAhead = freeAhead(context);
        BlockPos feet = context.player().getBlockPos();
        viewPortal = feet.offset(viewAhead, VIEW_PORTAL_STEP);
        look(context, viewAhead.asRotation(), 0.0f);
        context.onServer(server -> {
            ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            PortalTargets.Target target = LevelZeroArrival.find(level, feet.getX() + VIEW_NEAR, feet.getZ(), PortalFacing.NORTH, PortalSettings.DEFAULT);
            ClientTestContext.require(target != null, "Aucune arrivee dans le Level 0 pour le portail.");
            arrivalBottom = BlockPos.ofFloored(target.position().subtract(0.0, PortalSettings.DEFAULT.height() / 2.0, 0.0));
            BlockPos above = arrivalBottom.north();
            while (level.getBlockState(above).isAir() && above.getY() < arrivalBottom.getY() + CEILING_SEARCH) {
                above = above.up();
            }
            lamp = above;
            lampBefore = level.getBlockState(above);
            level.setBlockState(lamp, ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
            PortalSettings settings = new PortalSettings(1, 2, true, false, false, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                    Optional.empty(), Optional.of(new PortalMark(level.getRegistryKey(), arrivalBottom, Direction.NORTH)));
            level.setBlockState(viewPortal, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, viewAhead.getOpposite()));
            if (level.getBlockEntity(viewPortal) instanceof BackroomsPortalBlockEntity block) {
                block.readComponents(ClientTestPortals.wand(settings));
            }
        });
    }

    /** La zone du joueur compte le portail comme un passage, face au joueur, avec la zone qui cuit la lumiere de l'autre cote. */
    private static boolean passageCounted(ClientTestContext context) {
        look(context, viewAhead.asRotation(), 0.0f);
        PortalOpenings openings = LightCopy.openings(context.world());
        int index = openingAt(openings, viewPortal);
        return index >= 0 && openings.kind(index) == PortalOpenings.PASSAGE && openings.zone(index) != null
                && openings.face(index) == PortalOpenings.face(viewAhead.getOpposite().getOffsetX(), 0.0, viewAhead.getOpposite().getOffsetZ());
    }

    private static void removeLampPortal(ClientTestContext context) {
        context.onServer(server -> {
            ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            if (viewPortal != null) {
                level.setBlockState(viewPortal, Blocks.AIR.getDefaultState());
            }
            if (lamp != null && lampBefore != null) {
                level.setBlockState(lamp, lampBefore);
            }
        });
    }

    /** Regard vers le sol juste devant le portail, en degres sous l'horizon. */
    private static final float FLOOR_PITCH = 40.0f;
    /** Bande de l'image qui montre ce sol, sous le cadre : gauche, haut, droite, bas, en parts de l'image. */
    private static final double[] FLOOR_BAND = {0.42, 0.38, 0.58, 0.5};
    /**
     * Ce que le sol devant le portail perd au moins quand un corps de l'autre cote cache le neon, en part de sa luminance. Mesure : 17 %,
     * dont 2,5 % pour sa seule ombre de contact, qui passe elle aussi le cadre ; au-dessus d'elle, le seuil exige l'ombre portee.
     */
    private static final double MIN_BEYOND_SHADE = 0.08;
    /** Ecart du villageois au plan d'arrivee : contre lui, de ce cote-la. */
    private static final double AGAINST_ARRIVAL = 0.32;
    private static @Nullable UUID beyondBody;
    private static double floorLit;

    private static double floorBand(NativeImage image) {
        return Images.luminance(Images.mean(image, FLOOR_BAND[0], FLOOR_BAND[1], FLOOR_BAND[2], FLOOR_BAND[3]));
    }

    private static @Nullable Entity find(ClientWorld world, @Nullable UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Les ombres passent le cadre comme la lumiere : un corps de l'autre cote, contre l'arrivee et sous le neon qui eclaire par elle,
     * en cache une partie, et le sol devant l'entree s'assombrit.
     */
    private static ClientScenario bodyBeyondShadesThroughTheFrame() {
        return arrival("body_beyond_shades_through_the_frame", LookQuality.HIGH)
                .run("un portail vers un neon allume", LightContinuityClientTests::lampPortal)
                .await("passage compte par la zone, avec la zone de l'autre cote", LightContinuityClientTests::passageCounted, SETTLE_TICKS)
                .await("lumiere au repos", LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("regard vers le sol devant le portail", context -> look(context, viewAhead.asRotation(), FLOOR_PITCH))
                .frames(20)
                .capture("sol sans corps de l'autre cote", image -> floorLit = floorBand(image))
                .run("un villageois contre l'arrivee", context -> {
                    BackroomsPortalEntity portal = viewPortalEntity(context);
                    ClientTestContext.require(portal != null, "Le portail a disparu.");
                    Vec3d arrival = portal.destinationPos();
                    Vec3d normal = portal.facingDestination().normal;
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        VillagerEntity villager = EntityType.VILLAGER.create(level);
                        villager.refreshPositionAndAngles(arrival.x + normal.x * AGAINST_ARRIVAL, arrivalBottom.getY(), arrival.z + normal.z * AGAINST_ARRIVAL, 0.0f, 0.0f);
                        villager.setAiDisabled(true);
                        villager.setInvulnerable(true);
                        level.spawnEntity(villager);
                        beyondBody = villager.getUuid();
                    });
                })
                .await("villageois chez le client", context -> find(context.world(), beyondBody) != null, SETTLE_TICKS)
                .run("regard vers le sol devant le portail", context -> look(context, viewAhead.asRotation(), FLOOR_PITCH))
                .frames(20)
                .capture("sol avec le corps de l'autre cote", image -> {
                    double shaded = floorBand(image);
                    ModConstants.LOGGER.info("[Test client] Sol devant le portail : {} sans corps de l'autre cote, {} avec.", String.format(Locale.ROOT, "%.2f", floorLit),
                            String.format(Locale.ROOT, "%.2f", shaded));
                    ClientTestContext.require(shaded < floorLit * (1.0 - MIN_BEYOND_SHADE), String.format(Locale.ROOT,
                            "Le corps de l'autre cote ne fait pas d'ombre par le cadre : %.2f contre %.2f.", shaded, floorLit));
                })
                .cleanup(context -> context.onServer(server -> {
                    Entity body = beyondBody == null ? null : ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).getEntity(beyondBody);
                    if (body != null) {
                        body.discard();
                    }
                }))
                .cleanup(LightContinuityClientTests::removeLampPortal)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Couloir ferme pose autour du joueur, et son villageois. */
    private static BlockPos corridor;
    private static @Nullable UUID corridorBody;
    private static double floorWithFrame;
    private static double edgeWithFrame;
    /** Bande de l'image qui montre le sol entre le joueur et le villageois, la ou un neon derriere le cadre jetterait son ombre. */
    private static final double[] FRONT_FLOOR = {0.39, 0.6, 0.62, 0.83};
    /** Ecart de luminance tolere entre le cadre et un vrai mur sur cette bande : sans le cadre comme mur, l'ombre fantome en retirait 15. */
    private static final double MAX_FRAME_GAP = 6.0;
    /** Le mur droit du couloir tout contre le bord du cadre, a hauteur de mur : gauche, haut, droite, bas, en parts de l'image. */
    private static final double[] FRAME_EDGE = {0.592, 0.396, 0.607, 0.521};
    /** Rugosite toleree du mur au bord du cadre, en parts de celle du meme mur au bord d'un vrai mur : le mouchetis la doublait et plus. */
    private static final double MAX_EDGE_ROUGHNESS = 1.4;

    /**
     * La rugosite la plus forte d'une colonne de deux pixels dans la bande du bord du cadre : l'ecart moyen de luminance d'un pixel a celui
     * du dessous. Un mur lisse donne celle de sa texture ; un bord mouchete, bien plus.
     */
    private static double edgeRoughness(NativeImage image) {
        int left = (int) (FRAME_EDGE[0] * image.getWidth());
        int top = (int) (FRAME_EDGE[1] * image.getHeight());
        int right = (int) (FRAME_EDGE[2] * image.getWidth());
        int bottom = (int) (FRAME_EDGE[3] * image.getHeight());
        double worst = 0.0;
        for (int x = left; x + 1 < right; x += 2) {
            double sum = 0.0;
            int count = 0;
            for (int y = top; y + 1 < bottom; y++) {
                for (int dx = 0; dx < 2; dx++) {
                    sum += Math.abs(luminance(image.getColor(x + dx, y + 1)) - luminance(image.getColor(x + dx, y)));
                    count++;
                }
            }
            worst = Math.max(worst, sum / count);
        }
        return worst;
    }

    /**
     * Un couloir ferme, bouche par un cadre qui ne laisse pas passer la lumiere, un neon juste derriere lui et un villageois devant, compare
     * au meme couloir bouche par un vrai mur au meme plan. Le neon de derriere n'eclaire pas ce cote, et ne fait donc pas l'ombre du
     * villageois vers le joueur (en High, ou les corps ont des ombres portees) : le sol entre eux vaut celui du couloir mure. Et le mur du
     * couloir, tout contre le bord du cadre, est aussi lisse qu'au bord du mur (en Low, ou la lumiere vient de la zone seule) : il ne lit pas
     * tantot sa cellule, tantot celle que le plan coupe, cuite avec la lumiere de derriere.
     */
    private static ClientScenario frameBlockingACorridor() {
        return arrival("frame_blocking_a_corridor", LookQuality.HIGH)
                .run("couloir ferme, cadre, neon derriere, villageois devant", context -> {
                    corridor = context.player().getBlockPos();
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        corridor(level, corridor, -3, 14);
                        set(level, corridor.add(0, 4, 2), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        set(level, corridor.add(0, 4, 8), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        // Vers un autre endroit du Level 0 : un portail vers l'Overworld ne compte pas pour la lumiere. Le plan au bord du bloc, sur
                        // sa face arriere : celui d'un mur pose dans les blocs d'apres.
                        PortalTargets.Target target = LevelZeroArrival.find(level, corridor.getX() + VIEW_NEAR, corridor.getZ(), PortalFacing.SOUTH,
                                new PortalSettings(3, 4, true));
                        ClientTestContext.require(target != null, "Aucune arrivee dans le Level 0 pour le portail.");
                        PortalMark mark = new PortalMark(level.getRegistryKey(), BlockPos.ofFloored(target.position().subtract(0.0, 2.0, 0.0)), Direction.SOUTH);
                        PortalSettings settings = new PortalSettings(3, 4, false, true, false, false, PortalExit.STRAIGHT, 0, true, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(mark));
                        set(level, corridor.add(0, 0, 6), ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
                        if (level.getBlockEntity(corridor.add(0, 0, 6)) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                        VillagerEntity villager = EntityType.VILLAGER.create(level);
                        villager.refreshPositionAndAngles(corridor.getX() + 0.5, corridor.getY(), corridor.getZ() + 5.0, 180.0f, 0.0f);
                        villager.setAiDisabled(true);
                        villager.setInvulnerable(true);
                        level.spawnEntity(villager);
                        corridorBody = villager.getUuid();
                        ClientTestContext.serverPlayer(server).teleport(level, corridor.getX() + 0.5, corridor.getY(), corridor.getZ() + 0.5, 0.0f, 12.0f);
                    });
                })
                .await("cadre et villageois chez le client", context -> find(context.world(), corridorBody) != null
                        && !ClientPortalIndex.portals(context.world()).isEmpty(), SETTLE_TICKS)
                .await("lumiere au repos avec le cadre", context -> {
                    look(context, 0.0f, 12.0f);
                    return settled(context);
                }, SETTLE_TICKS)
                .run("regard le long du couloir", context -> look(context, 0.0f, 12.0f))
                .frames(20)
                .capture("sol devant le villageois, cadre", image -> floorWithFrame = Images.luminance(Images.mean(image, FRONT_FLOOR[0], FRONT_FLOOR[1],
                        FRONT_FLOOR[2], FRONT_FLOOR[3])))
                .run("palier Low", context -> tier(context, LookQuality.LOW))
                .await("lumiere au repos en Low avec le cadre", context -> {
                    look(context, 0.0f, 12.0f);
                    return settled(context);
                }, SETTLE_TICKS)
                .run("regard le long du couloir", context -> look(context, 0.0f, 12.0f))
                .frames(20)
                .capture("bord du cadre", image -> edgeWithFrame = edgeRoughness(image))
                .run("un vrai mur au plan du cadre", context -> context.onServer(server -> {
                    ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                    level.setBlockState(corridor.add(0, 0, 6), Blocks.AIR.getDefaultState());
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 3; y++) {
                            set(level, corridor.add(x, y, 7), ModBlocks.LEVEL_ZERO_WALLPAPER.getDefaultState());
                        }
                    }
                }))
                .await("plus de cadre chez le client", context -> ClientPortalIndex.portals(context.world()).isEmpty(), SETTLE_TICKS)
                .await("lumiere au repos en Low avec le mur", context -> {
                    look(context, 0.0f, 12.0f);
                    return settled(context);
                }, SETTLE_TICKS)
                .run("regard le long du couloir", context -> look(context, 0.0f, 12.0f))
                .frames(20)
                .capture("bord du mur", image -> {
                    double wall = edgeRoughness(image);
                    ModConstants.LOGGER.info("[Test client] Rugosite du mur au bord : {} avec le cadre, {} avec un mur.", String.format(Locale.ROOT, "%.2f", edgeWithFrame),
                            String.format(Locale.ROOT, "%.2f", wall));
                    ClientTestContext.require(edgeWithFrame < wall * MAX_EDGE_ROUGHNESS, String.format(Locale.ROOT,
                            "Le mur est mouchete au bord du cadre : rugosite %.2f contre %.2f au bord d'un mur.", edgeWithFrame, wall));
                })
                .run("palier High", context -> tier(context, LookQuality.HIGH))
                .await("lumiere au repos en High avec le mur", context -> {
                    look(context, 0.0f, 12.0f);
                    return settled(context);
                }, SETTLE_TICKS)
                .run("regard le long du couloir", context -> look(context, 0.0f, 12.0f))
                .frames(20)
                .capture("sol devant le villageois, mur", image -> {
                    double wall = Images.luminance(Images.mean(image, FRONT_FLOOR[0], FRONT_FLOOR[1], FRONT_FLOOR[2], FRONT_FLOOR[3]));
                    ModConstants.LOGGER.info("[Test client] Sol devant le villageois : {} avec le cadre, {} avec un mur.", String.format(Locale.ROOT, "%.2f", floorWithFrame),
                            String.format(Locale.ROOT, "%.2f", wall));
                    ClientTestContext.require(Math.abs(floorWithFrame - wall) < MAX_FRAME_GAP, String.format(Locale.ROOT,
                            "Le neon derriere le cadre fait encore l'ombre du villageois : %.2f avec le cadre, %.2f avec un mur.", floorWithFrame, wall));
                })
                .cleanup(context -> context.onServer(server -> {
                    Entity body = corridorBody == null ? null : ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).getEntity(corridorBody);
                    if (body != null) {
                        body.discard();
                    }
                }))
                .cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Le mur du fond vu par le cadre, le joueur tout contre lui : gauche, haut, droite, bas, en parts de l'image. */
    private static final double[] VIEW_WALL = {0.35, 0.2, 0.65, 0.83};
    /** Ecart de luminance tolere sur ce mur entre la vue telle qu'elle arrive et la meme vue recalculee entierement. */
    private static final double MAX_REBAKE_GAP = 3.0;
    /** Distance du joueur au plan du portail, en blocs, pour que le cadre de trois couvre l'image. */
    private static final double AGAINST_THE_FRAME = 1.7;
    private static double rebakedWall;
    private static double arrivedWall;

    /** Un portail de trois sur trois a {@link #VIEW_PORTAL_STEP} blocs devant le joueur, vers {@link #VIEW_NEAR} blocs a l'est, aller-retour ou aller simple. */
    private static void sizedPortal(ClientTestContext context, boolean oneWay) {
        BlockPos feet = BlockPos.ofFloored(origin);
        PortalSettings size = new PortalSettings(3, 3, true);
        context.onServer(server -> {
            ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            PortalTargets.Target target = LevelZeroArrival.find(level, feet.getX() + VIEW_NEAR, feet.getZ(), PortalFacing.fromDirection(viewAhead), size);
            ClientTestContext.require(target != null, "Aucune arrivee dans le Level 0 pour le portail.");
            BlockPos bottom = BlockPos.ofFloored(target.position().subtract(0.0, size.height() / 2.0, 0.0));
            PortalSettings settings = new PortalSettings(3, 3, true, !oneWay, oneWay, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                    Optional.empty(), Optional.of(new PortalMark(level.getRegistryKey(), bottom, viewAhead)));
            level.setBlockState(viewPortal, Blocks.AIR.getDefaultState());
            level.setBlockState(viewPortal, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, viewAhead.getOpposite()));
            if (level.getBlockEntity(viewPortal) instanceof BackroomsPortalBlockEntity block) {
                block.readComponents(ClientTestPortals.wand(settings));
            }
        });
    }

    /** La vue du portail est dessinee avec sa zone, et toute la lumiere, du joueur comme de la vue, est au repos. */
    private static boolean viewSettled(ClientTestContext context) {
        look(context, viewAhead.asRotation(), 0.0f);
        BackroomsPortalEntity portal = viewPortalEntity(context);
        return portal != null && RemoteWorldRegistry.viewOf(portal) != null && LightVolume.viewTexture() != 0 && settled(context) && LightCopy.viewIdle()
                && LightSurfaces.viewPending() == null && LightBounces.viewSettled();
    }

    private static double viewWall(NativeImage image) {
        return Images.luminance(Images.mean(image, VIEW_WALL[0], VIEW_WALL[1], VIEW_WALL[2], VIEW_WALL[3]));
    }

    /**
     * En Ultra, la vue d'un portail aller-retour pose d'un coup vaut la meme vue recalculee entierement : le cadre de retour, a l'arrivee,
     * est un passage qui apporte la lumiere de ce cote, et les rebonds traces de la vue lisaient ce qui en sort avant que ce ne soit
     * mesure, puis gardaient ce rien tant que rien d'autre ne les faisait recalculer : le mur du fond perdait un quart de sa lumiere.
     * Le juge d'un cache incremental est le recalcul complet. Chaque zone y compte le passage du cadre de retour une seule fois : l'arrivee
     * d'un portail qui a son cadre de retour n'est pas en plus un passage d'aller simple. Le portail retire, la zone de la vue rend sa
     * memoire graphique apres le delai des tests ; reposee, la vue recreee vaut la premiere.
     */
    private static ClientScenario twoWayViewMatchesItsRebake() {
        return arrival("two_way_view_matches_its_rebake", LookQuality.ULTRA)
                .run("portail aller-retour, le joueur tout contre", context -> {
                    viewAhead = freeAhead(context);
                    origin = context.player().getPos();
                    // Au dernier bloc que freeAhead dit libre : le portail ne remplace pas un mur que le nettoyage laisserait troue.
                    viewPortal = BlockPos.ofFloored(origin).offset(viewAhead, VIEW_PORTAL_STEP);
                    sizedPortal(context, false);
                    teleport(context, origin.x + viewAhead.getOffsetX() * (VIEW_PORTAL_STEP - AGAINST_THE_FRAME), origin.y,
                            origin.z + viewAhead.getOffsetZ() * (VIEW_PORTAL_STEP - AGAINST_THE_FRAME));
                })
                .await("retour chez le client", context -> ClientPortalIndex.allPortals(context.world()).size() >= 4, SETTLE_TICKS)
                .await("vue au repos, telle qu'elle arrive", LightContinuityClientTests::viewSettled, SETTLE_TICKS)
                .run("un passage par cadre", context -> {
                    BackroomsPortalEntity portal = viewPortalEntity(context);
                    ClientWorld view = portal == null ? null : RemoteWorldRegistry.worldOf(portal);
                    ClientTestContext.require(view != null, "La vue n'a pas de monde.");
                    for (ClientWorld world : new ClientWorld[] {context.world(), view}) {
                        PortalOpenings openings = LightCopy.openings(world);
                        ClientTestContext.require(passages(openings) > 0, "Une zone ne compte aucun passage par le portail aller-retour.");
                        ClientTestContext.require(!doublePassage(openings),
                                "Un passage compte deux fois : le cadre de retour, et l'arrivee prise pour celle d'un aller simple.");
                    }
                })
                .frames(20)
                .capture("mur du fond, telle qu'elle arrive", image -> arrivedWall = viewWall(image))
                .run("tout recalculer", context -> LightVolume.rebake())
                .ticks(20)
                .await("vue au repos, recalculee", LightContinuityClientTests::viewSettled, SETTLE_TICKS)
                .frames(20)
                .capture("mur du fond, recalculee", image -> {
                    rebakedWall = viewWall(image);
                    ModConstants.LOGGER.info("[Test client] Mur du fond vu par le cadre : {} telle que la vue arrive, {} recalculee.",
                            String.format(Locale.ROOT, "%.2f", arrivedWall), String.format(Locale.ROOT, "%.2f", rebakedWall));
                    ClientTestContext.require(Math.abs(arrivedWall - rebakedWall) <= MAX_REBAKE_GAP, String.format(Locale.ROOT,
                            "La vue telle qu'elle arrive ne vaut pas son recalcul : %.2f contre %.2f.", arrivedWall, rebakedWall));
                })
                .run("portail retire : plus aucune vue", context -> context.onServer(server ->
                        ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(viewPortal, Blocks.AIR.getDefaultState())))
                .await("memoire graphique de la vue rendue", context -> !LightVolume.viewAllocated(), RELEASE_TICKS)
                .run("portail repose", context -> sizedPortal(context, false))
                .await("retour chez le client, de nouveau", context -> ClientPortalIndex.allPortals(context.world()).size() >= 4, SETTLE_TICKS)
                .await("vue au repos, recreee", LightContinuityClientTests::viewSettled, SETTLE_TICKS)
                .frames(20)
                .capture("mur du fond, vue recreee", image -> {
                    double recreated = viewWall(image);
                    ModConstants.LOGGER.info("[Test client] Mur du fond vu par le cadre, vue recreee apres sa liberation : {}.",
                            String.format(Locale.ROOT, "%.2f", recreated));
                    ClientTestContext.require(Math.abs(recreated - rebakedWall) <= MAX_REBAKE_GAP, String.format(Locale.ROOT,
                            "La vue recreee apres sa liberation ne vaut pas la premiere : %.2f contre %.2f.", recreated, rebakedWall));
                })
                .cleanup(context -> context.onServer(server -> {
                    if (viewPortal != null) {
                        ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(viewPortal, Blocks.AIR.getDefaultState());
                    }
                }))
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Ticks laisses a la liberation d'un cache de lumiere sans usage : le delai des tests (cinq secondes), et de la marge. */
    private static final int RELEASE_TICKS = 600;
    /** Ecart en dessous duquel deux centres de passages sont le meme, en blocs. */
    private static final float SAME_CENTER = 0.01f;

    /** Les passages d'une zone : les cadres qui ont leur zone de l'autre cote, et les arrivees d'aller simple. */
    private static int passages(PortalOpenings openings) {
        int count = 0;
        for (int i = 0; i < openings.count(); i++) {
            count += openings.kind(i) == PortalOpenings.PASSAGE ? 1 : 0;
        }
        return count;
    }

    /** Deux passages au meme centre et sur la meme face : la lumiere de l'autre cote y entrerait deux fois. */
    private static boolean doublePassage(PortalOpenings openings) {
        for (int i = 0; i < openings.count(); i++) {
            for (int j = i + 1; j < openings.count(); j++) {
                if (openings.kind(i) == PortalOpenings.PASSAGE && openings.kind(j) == PortalOpenings.PASSAGE && openings.face(i) == openings.face(j)
                        && Math.abs(openings.center(i, 0) - openings.center(j, 0)) < SAME_CENTER && Math.abs(openings.center(i, 1) - openings.center(j, 1)) < SAME_CENTER
                        && Math.abs(openings.center(i, 2) - openings.center(j, 2)) < SAME_CENTER) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Sol de ce cote, pres du plan (haut, bas), et sol de l'autre cote, juste derriere le plan, en fractions de l'image. */
    private static final double[] FLOOR_BEFORE_PLANE = {0.4, 0.54, 0.6, 0.69};
    private static final double[] FLOOR_BEYOND_PLANE = {0.4, 0.31, 0.6, 0.42};
    /** Part de la lumiere du sol de ce cote que le sol juste derriere le plan garde au moins : la continuite. */
    private static final double ONE_WAY_CONTINUITY = 0.6;
    private static final double LIT_FLOOR = 40.0;
    private static BlockPos oneWayCorridor;

    private static void oneWayLook(ClientTestContext context) {
        context.player().setPosition(oneWayCorridor.getX() + 0.5, oneWayCorridor.getY(), oneWayCorridor.getZ() + 4.0);
        context.player().setVelocity(0.0, 0.0, 0.0);
        context.player().setYaw(0.0f);
        context.player().setPitch(38.0f);
    }

    /**
     * Un couloir eclaire par deux dalles, bouche par un portail aller simple (sans cadre de retour) vers un couloir identique dont la seule
     * dalle est loin : vu par l'entree, le sol juste derriere le plan garde la lumiere de ce cote, comme par un aller-retour. Sans le
     * passage de l'arrivee, il etait noir des le plan (4 niveaux contre 93).
     */
    private static ClientScenario oneWayViewKeepsTheLight() {
        return arrival("one_way_view_keeps_the_light", LookQuality.ULTRA)
                .run("sans grain, sans bloom, sans vignette", context -> LookResources.applySettings(LookSettings.current()
                        .with(LookOption.REDUCE_NOISE, true).with(LookOption.BLOOM, false).with(LookOption.VIGNETTE, false), context.client()))
                .run("couloir eclaire, aller simple, couloir sombre derriere", context -> {
                    oneWayCorridor = context.player().getBlockPos();
                    BlockPos o = oneWayCorridor;
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        corridor(level, o, -3, 14);
                        set(level, o.add(0, 4, 1), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        set(level, o.add(0, 4, 4), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        PortalTargets.Target target = LevelZeroArrival.find(level, o.getX() + VIEW_NEAR, o.getZ(), PortalFacing.SOUTH, new PortalSettings(3, 4, true));
                        ClientTestContext.require(target != null, "Aucune arrivee.");
                        BlockPos bottom = BlockPos.ofFloored(target.position().subtract(0.0, 2.0, 0.0));
                        corridor(level, bottom, -9, 22);
                        set(level, bottom.add(0, 4, 18), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        PortalMark mark = new PortalMark(level.getRegistryKey(), bottom, Direction.SOUTH);
                        PortalSettings settings = new PortalSettings(3, 4, true, true, true, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(mark));
                        set(level, o.add(0, 0, 6), ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
                        if (level.getBlockEntity(o.add(0, 0, 6)) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                        ClientTestContext.serverPlayer(server).teleport(level, o.getX() + 0.5, o.getY(), o.getZ() + 4.0, 0.0f, 38.0f);
                        ClientTestContext.serverPlayer(server).getAbilities().flying = true;
                        ClientTestContext.serverPlayer(server).sendAbilitiesUpdate();
                    });
                })
                .await("cadre chez le client", context -> ClientPortalIndex.portals(context.world()).size() >= 1, SETTLE_TICKS)
                .ticks(100)
                .await("lumiere au repos, vue comprise", context -> {
                    oneWayLook(context);
                    return settled(context) && LightCopy.viewIdle() && LightSurfaces.viewPending() == null && LightBounces.viewSettled();
                }, SETTLE_TICKS)
                .ticks(200)
                .run("regard baisse vers le plan", LightContinuityClientTests::oneWayLook)
                .frames(30)
                .capture("sol des deux cotes du plan", image -> {
                    double before = Images.luminance(Images.mean(image, FLOOR_BEFORE_PLANE[0], FLOOR_BEFORE_PLANE[1], FLOOR_BEFORE_PLANE[2], FLOOR_BEFORE_PLANE[3]));
                    double beyond = Images.luminance(Images.mean(image, FLOOR_BEYOND_PLANE[0], FLOOR_BEYOND_PLANE[1], FLOOR_BEYOND_PLANE[2], FLOOR_BEYOND_PLANE[3]));
                    ModConstants.LOGGER.info("[Test client] Sol devant le plan d'un aller simple : {} ; juste derriere : {}.", String.format(Locale.ROOT, "%.1f", before),
                            String.format(Locale.ROOT, "%.1f", beyond));
                    ClientTestContext.require(before > LIT_FLOOR, "Le sol de ce cote n'est pas eclaire : " + before + ".");
                    ClientTestContext.require(beyond > before * ONE_WAY_CONTINUITY, String.format(Locale.ROOT,
                            "Le sol derriere le plan d'un aller simple perd la lumiere de ce cote : %.1f contre %.1f.", beyond, before));
                })
                .cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Le haut du mur de gauche de ce cote, pres du plan (gauche, haut, droite, bas), en fractions de l'image, vu de biais vers le haut. */
    private static final double[] WALL_BY_PLANE = {0.375, 0.33, 0.5, 0.67};
    /** Ce que le reflet des dalles de l'autre cote ajoute au moins a ce mur, en niveaux de 0 a 255 (1,8 au banc, 0 sans ce reflet). */
    private static final double MIN_SEEN_SHINE = 1.0;
    private static BlockPos seenGlossCorridor;
    private static double wallShine;

    private static double wallByPlane(NativeImage image) {
        return Images.luminance(Images.mean(image, WALL_BY_PLANE[0], WALL_BY_PLANE[1], WALL_BY_PLANE[2], WALL_BY_PLANE[3]));
    }

    private static void seenGlossLook(ClientTestContext context) {
        context.player().setPosition(seenGlossCorridor.getX() + 0.5, seenGlossCorridor.getY(), seenGlossCorridor.getZ() + 4.0);
        context.player().setVelocity(0.0, 0.0, 0.0);
        context.player().setYaw(-35.0f);
        context.player().setPitch(-25.0f);
    }

    private static boolean seenGlossSettled(ClientTestContext context) {
        seenGlossLook(context);
        return settled(context) && LightCopy.viewIdle() && LightSurfaces.viewPending() == null && LightBounces.viewSettled();
    }

    /**
     * Un couloir sans dalle, bouche par un portail aller-retour vers un couloir eclaire dont une dalle est a un bloc de l'arrivee : vu de
     * biais vers le haut, le mur de ce cote pres du plan reflete cette dalle, par le cadre. Couper les reflets l'assombrit ; sans le reflet
     * des dalles vues par un cadre, couper les reflets n'y changeait rien (0 contre 1,8 au banc).
     */
    private static ClientScenario seenDallesReflectThroughTheFrame() {
        return arrival("seen_dalles_reflect_through_the_frame", LookQuality.ULTRA)
                .run("sans grain, sans bloom, sans vignette", context -> LookResources.applySettings(LookSettings.current()
                        .with(LookOption.REDUCE_NOISE, true).with(LookOption.BLOOM, false).with(LookOption.VIGNETTE, false), context.client()))
                .run("couloir sombre, aller-retour, couloir eclaire derriere", context -> {
                    seenGlossCorridor = context.player().getBlockPos();
                    BlockPos o = seenGlossCorridor;
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        corridor(level, o, -3, 14);
                        PortalTargets.Target target = LevelZeroArrival.find(level, o.getX() + VIEW_NEAR, o.getZ(), PortalFacing.SOUTH, new PortalSettings(3, 4, true));
                        ClientTestContext.require(target != null, "Aucune arrivee.");
                        BlockPos bottom = BlockPos.ofFloored(target.position().subtract(0.0, 2.0, 0.0));
                        corridor(level, bottom, -9, 8);
                        set(level, bottom.add(0, 4, 1), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        set(level, bottom.add(0, 4, 5), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        PortalMark mark = new PortalMark(level.getRegistryKey(), bottom, Direction.SOUTH);
                        PortalSettings settings = new PortalSettings(3, 4, true, true, false, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(mark));
                        set(level, o.add(0, 0, 6), ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
                        if (level.getBlockEntity(o.add(0, 0, 6)) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                        ClientTestContext.serverPlayer(server).teleport(level, o.getX() + 0.5, o.getY(), o.getZ() + 4.0, -35.0f, -25.0f);
                        ClientTestContext.serverPlayer(server).getAbilities().flying = true;
                        ClientTestContext.serverPlayer(server).sendAbilitiesUpdate();
                    });
                })
                .await("cadre chez le client", context -> ClientPortalIndex.portals(context.world()).size() >= 2, SETTLE_TICKS)
                .ticks(100)
                .await("lumiere au repos, vue comprise", LightContinuityClientTests::seenGlossSettled, SETTLE_TICKS)
                .ticks(200)
                .run("regard vers le haut du mur de gauche", LightContinuityClientTests::seenGlossLook)
                .frames(30)
                .capture("mur pres du plan, reflets rendus", image -> wallShine = wallByPlane(image))
                .run("reflets coupes", context -> LookResources.applySettings(LookSettings.current().with(LookOption.SHEEN, false), context.client()))
                // Changer un reglage recuit la lumiere : la capture attend que la zone et la vue soient de nouveau au repos.
                .await("lumiere au repos sans reflets", LightContinuityClientTests::seenGlossSettled, SETTLE_TICKS)
                .ticks(200)
                .run("regard vers le haut du mur, sans reflets", LightContinuityClientTests::seenGlossLook)
                .frames(30)
                .capture("mur pres du plan, reflets coupes", image -> {
                    double matte = wallByPlane(image);
                    ModConstants.LOGGER.info("[Test client] Mur de ce cote pres du plan : {} avec reflets, {} sans.", String.format(Locale.ROOT, "%.1f", wallShine),
                            String.format(Locale.ROOT, "%.1f", matte));
                    ClientTestContext.require(wallShine > matte + MIN_SEEN_SHINE, String.format(Locale.ROOT,
                            "La dalle de l'autre cote ne se reflete pas par le cadre sur le mur de ce cote : %.1f avec reflets, %.1f sans.", wallShine, matte));
                })
                .run("reflets rendus", context -> LookResources.applySettings(LookSettings.current().with(LookOption.SHEEN, true), context.client()))
                .cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Paliers de la couture : tous, de la zone seule (Low) aux rebonds traces (Ultra). */
    private static final LookQuality[] SEAM_TIERS = {LookQuality.LOW, LookQuality.MEDIUM, LookQuality.HIGH, LookQuality.ULTRA};
    /**
     * Ecart moyen tolere entre le portail et le couloir continu, par palier, en niveaux de 0 a 255, sur une bande de lignes : un saut de
     * lumiere au plan decale toute une bande. Mesure, de Low a High : 0,7 au plus (au pixel, la moquette des deux couloirs differe, ses
     * variantes de texture suivent la position) ; colle au cadre, sans l'ombre de contact du joueur de l'autre cote du plan, 13 en Low, et
     * avec une penombre visee au centre des dalles vues par le cadre, 1,8 en High. En Ultra, les rebonds traces lisent ce qui sort du
     * passage, mesure par la cuisson dans ses cellules, plus grossieres que les tuiles : 2,2 au plus (sous les tubes, le fond sombre vu
     * par le cadre), 1,5 pres du cadre, contre 2,9 et 3,3 quand la mesure se faisait en seize directions, la meme sur tout le cadre et fond compris.
     * Une grille de quatre points par cote sur le cadre ramene le pire a 1,7, mais eclaircit de 0,5 le sol colle au plan : gardee a deux.
     */
    private static final double[] MAX_SEAM_BAND = {1.5, 1.5, 1.5, 2.5};
    /** Age du joueur pendant les mesures : sa pose, et donc son ombre, ne changent pas d'une image a l'autre. */
    private static final int SEAM_AGE = 777;
    /**
     * Points de vue, en z du couloir (le plan est en 6,5), tangage et hauteur des bandes de lignes : pres du cadre, regard au sol ; colle au
     * cadre, regard a ses pieds (son ombre de contact passe le plan) ; a vingt blocs, le cadre au loin (son halo), en bandes fines : le cadre
     * n'y fait que quelques dizaines de lignes ; sous les tubes, regard vers le haut du cadre, en bandes fines : les rayons vers le cadre
     * passent sous un tube, dans son halo, que la vue doit reprendre jusqu'au plan (sans lui, 2,6 niveaux en High ; 1,0 au plus avec).
     */
    private static final double[] SEAM_Z = {4.0, 6.0, -16.0, -1.0};
    private static final float[] SEAM_PITCH = {38.0f, 62.0f, 6.0f, -16.0f};
    private static final int[] SEAM_BANDS = {40, 40, 8, 8};
    private static final String[] SEAM_WHERE = {"pres du cadre", "colle au cadre", "de loin", "sous les tubes"};
    private static final int[][] SEAM_PORTAL = new int[SEAM_TIERS.length * SEAM_Z.length][];
    private static BlockPos seamCorridor;
    private static int seamView;
    private static int seamFrozen;
    private static int seamWorstTop;

    private static void seamLook(ClientTestContext context) {
        context.player().age = SEAM_AGE;
        context.player().setPosition(seamCorridor.getX() + 0.5, seamCorridor.getY(), seamCorridor.getZ() + SEAM_Z[seamView]);
        context.player().setVelocity(0.0, 0.0, 0.0);
        context.player().setYaw(0.0f);
        context.player().setPitch(SEAM_PITCH[seamView]);
    }

    private static boolean seamSettled(ClientTestContext context) {
        seamLook(context);
        return settled(context) && (ClientPortalIndex.portals(context.world()).isEmpty() || LightCopy.viewIdle()
                && (!LookResources.surfaceLight() || LightSurfaces.viewPending() == null && LightBounces.viewSettled()));
    }

    /** Un z, de from a to, ou un tube au plafond du couloir, en x, est stable : ni vacillant, ni mort, ni dans un secteur en panne. */
    private static int steadyNeon(ServerWorld level, BlockPos origin, int x, int from, int to) {
        int seed = NeonSchedule.seed(level.getRegistryKey().getValue());
        LookProfile.Neons neons = LookProfiles.of(level.getRegistryKey()).neons();
        for (int z = from; z <= to; z++) {
            BlockPos at = origin.add(x, 4, z);
            if (NeonSchedule.kind(NeonSchedule.character(at.getX(), at.getY(), at.getZ(), seed), neons.flickering(), neons.dead()) == NeonSchedule.STEADY
                    && !NeonSchedule.blackout(at.getX(), at.getZ(), seed, neons.blackout())) {
                return z;
            }
        }
        throw new AssertionError("Aucun tube stable de " + from + " a " + to + ".");
    }

    /** La plus grande moyenne de l'ecart, en valeur absolue, par bande de lignes, sur la colonne du centre (le sol, le plan, l'autre cote). */
    private static double worstSeamBand(int[] portal, NativeImage reference, int band) {
        int width = reference.getWidth();
        double worst = 0.0;
        for (int top = 0; top + band <= reference.getHeight(); top += band) {
            double sum = 0.0;
            int count = 0;
            for (int y = top; y < top + band; y++) {
                for (int x = width / 2 - 60; x < width / 2 + 60; x++) {
                    sum += luminance(portal[y * width + x]) - luminance(reference.getColor(x, y));
                    count++;
                }
            }
            if (Math.abs(sum / count) > worst) {
                worst = Math.abs(sum / count);
                seamWorstTop = top;
            }
        }
        return worst;
    }

    /** Pour chaque palier, puis chaque point de vue : repos, pose figee, et une capture. */
    private static ClientScenario seamCaptures(ClientScenario scenario, String what, boolean reference) {
        ClientScenario s = scenario;
        for (int i = 0; i < SEAM_TIERS.length; i++) {
            LookQuality quality = SEAM_TIERS[i];
            s = s.run("palier " + quality.key() + ", " + what, context -> tier(context, quality)).ticks(60);
            int tierAt = i;
            for (int view = 0; view < SEAM_Z.length; view++) {
                int slot = i * SEAM_Z.length + view;
                int place = view;
                String where = quality.key() + ", " + SEAM_WHERE[view] + ", " + what;
                s = s.run("place " + where, context -> seamView = place)
                        .await("repos " + where, LightContinuityClientTests::seamSettled, SETTLE_TICKS)
                        .ticks(200)
                        .await("repos bis " + where, LightContinuityClientTests::seamSettled, SETTLE_TICKS)
                        .await("pose figee " + where, context -> {
                            seamLook(context);
                            return ++seamFrozen % 10 == 0;
                        }, 100)
                        .frames(2)
                        .capture(where, image -> {
                            if (!reference) {
                                SEAM_PORTAL[slot] = pixels(image);
                                return;
                            }
                            double worst = worstSeamBand(SEAM_PORTAL[slot], image, SEAM_BANDS[place]);
                            ModConstants.LOGGER.info("[Test client] Couture en {} : ecart moyen de la pire bande {} (lignes {} et suivantes).", where,
                                    String.format(Locale.ROOT, "%.2f", worst), seamWorstTop);
                            ClientTestContext.require(worst <= MAX_SEAM_BAND[tierAt], String.format(Locale.ROOT,
                                    "Le portail se voit (%s) : une bande differe de %.2f niveaux du couloir continu.", where, worst));
                        });
            }
        }
        return s;
    }

    /**
     * La couture, le juge exact d'un portail qui ne doit pas se voir : un couloir eclaire de tubes tout du long, bouche par un portail aller
     * simple vers un couloir construit comme sa suite exacte derriere le plan, sans tube ; le joueur, visible, regarde le sol vers le plan,
     * puis ses pieds colle au cadre, puis le cadre de vingt blocs, puis le haut du cadre sous les tubes. Le portail retire, le couloir
     * continue lui-meme. A tous les paliers, les deux images sont egales a quelques niveaux pres, bande par bande : lumiere, ombres du
     * joueur (de contact et portee) et halo passent le plan comme s'il n'y en avait pas. De loin, le halo de la vue jusqu'au plan est celui
     * des dix tubes du joueur : les tubes vus par le cadre lui ajoutaient une tache claire en haut du mur du fond.
     */
    private static ClientScenario portalMatchesAContinuousCorridor() {
        ClientScenario s = arrival("portal_matches_a_continuous_corridor", LookQuality.HIGH)
                .run("sans grain, sans bloom, sans vignette", context -> LookResources.applySettings(LookSettings.current()
                        .with(LookOption.REDUCE_NOISE, true).with(LookOption.BLOOM, false).with(LookOption.VIGNETTE, false), context.client()))
                .run("couloir eclaire, aller simple, sa suite exacte derriere le plan", context -> {
                    seamCorridor = context.player().getBlockPos();
                    seamView = 0;
                    BlockPos o = seamCorridor;
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        corridor(level, o, -23, 14);
                        // Des tubes stables, tous les quatre blocs : un tube qui vacille selon le calendrier eteint une bande d'une image a l'autre.
                        for (int first = 3; first >= -21; first -= 4) {
                            set(level, o.add(0, 4, steadyNeon(level, o, 0, first - 1, first + 1)), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        }
                        // Deux rangees de plus autour du point de vue lointain : les dix tubes les plus proches du joueur sont les siens, et les
                        // tubes du cadre, a vingt blocs, n'en sont pas ; c'est la ou une vue prenait un halo que l'image du joueur n'a pas.
                        for (int side = -1; side <= 1; side += 2) {
                            for (int first = -21; first <= -9; first += 3) {
                                set(level, o.add(side, 4, steadyNeon(level, o, side, first, first + 1)), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                            }
                        }
                        PortalTargets.Target target = LevelZeroArrival.find(level, o.getX() + VIEW_NEAR, o.getZ(), PortalFacing.SOUTH, new PortalSettings(3, 4, true));
                        ClientTestContext.require(target != null, "Aucune arrivee.");
                        BlockPos bottom = BlockPos.ofFloored(target.position().subtract(0.0, 2.0, 0.0));
                        // Le plan de l'entree est au milieu du bloc 6, son mur au bloc 14 : 7,5 blocs ; l'arrivee au milieu de son bloc 0, le mur au bloc 8.
                        corridor(level, bottom, -9, 8);
                        PortalSettings settings = new PortalSettings(3, 4, true, true, true, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(new PortalMark(level.getRegistryKey(), bottom, Direction.SOUTH)));
                        set(level, o.add(0, 0, 6), ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
                        if (level.getBlockEntity(o.add(0, 0, 6)) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                        ClientTestContext.serverPlayer(server).teleport(level, o.getX() + 0.5, o.getY(), o.getZ() + SEAM_Z[0], 0.0f, SEAM_PITCH[0]);
                        ClientTestContext.serverPlayer(server).getAbilities().flying = true;
                        ClientTestContext.serverPlayer(server).sendAbilitiesUpdate();
                    });
                })
                .await("cadre chez le client", context -> !ClientPortalIndex.portals(context.world()).isEmpty(), SETTLE_TICKS)
                .ticks(100);
        s = seamCaptures(s, "portail", false);
        s = s.run("portail retire : le couloir continue", context -> context.onServer(server -> ClientTestContext.serverWorld(server,
                        BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(seamCorridor.add(0, 0, 6), Blocks.AIR.getDefaultState())))
                .await("plus de portail", context -> ClientPortalIndex.portals(context.world()).isEmpty(), SETTLE_TICKS)
                .ticks(100);
        s = seamCaptures(s, "couloir continu", true);
        return s.cleanup(LightContinuityClientTests::restore)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Un couloir de trois blocs de large et quatre de haut, moquette, papier peint et dalles, de fromZ a toZ (murs compris). */
    private static void corridor(ServerWorld level, BlockPos origin, int fromZ, int toZ) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    boolean inside = Math.abs(x) <= 1 && y >= 0 && y <= 3 && z > fromZ && z < toZ;
                    BlockState state = inside ? Blocks.AIR.getDefaultState() : y == -1 ? ModBlocks.LEVEL_ZERO_DAMP_CARPET.getDefaultState()
                            : y == 4 ? ModBlocks.LEVEL_ZERO_CEILING_TILE.getDefaultState() : ModBlocks.LEVEL_ZERO_WALLPAPER.getDefaultState();
                    set(level, origin.add(x, y, z), state);
                }
            }
        }
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
     * Un cache de lumiere sans usage rend sa memoire graphique apres le delai des tests (cinq secondes) : les rebonds traces au palier
     * High, qui ne les lit pas, le cache de surfaces au palier Low, la zone du joueur dans l'Overworld, ce que chacun lit encore restant
     * la. De retour dans le Level 0 en Ultra, tout se recree, et la piece revient telle qu'elle etait.
     */
    private static ClientScenario lightIsReleasedWhenUnused() {
        return arrival("light_is_released_when_unused", LookQuality.ULTRA)
                .run("regard", context -> {
                    origin = context.player().getPos();
                    look(context, 200.0f, 0.0f);
                })
                .frames(8)
                .capture("la piece en Ultra", image -> before = pixels(image))
                .check("tout est la en Ultra", context -> ClientTestContext.require(LightVolume.playerBouncesAllocated()
                        && LightVolume.playerSurfacesAllocated(), "En Ultra, le cache de surfaces et les rebonds traces doivent etre la."))
                .run("palier High, sans rebonds traces", context -> tier(context, LookQuality.HIGH))
                .await("rebonds traces rendus", context -> !LightVolume.playerBouncesAllocated(), RELEASE_TICKS)
                .check("le cache de surfaces, lu en High, reste", context -> ClientTestContext.require(LightVolume.playerSurfacesAllocated(),
                        "Le cache de surfaces, que le High lit, a ete rendu."))
                .run("palier Low, sans cache de surfaces", context -> tier(context, LookQuality.LOW))
                .await("cache de surfaces rendu", context -> !LightVolume.playerSurfacesAllocated(), RELEASE_TICKS)
                .check("la zone, lue en Low, reste", context -> ClientTestContext.require(LightVolume.playerAllocated(),
                        "La zone du joueur, que le Low lit, a ete rendue."))
                .run("dans l'Overworld, sans lumiere cuite", LevelZeroClientTests::backToOverworld)
                .await("zone du joueur rendue", context -> !LightVolume.playerAllocated(), RELEASE_TICKS)
                .run("palier Ultra", context -> tier(context, LookQuality.ULTRA))
                .run("retour dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .run("a la meme place", context -> teleport(context, origin.x, origin.y, origin.z))
                .ticks(60)
                .await("repos au retour", LightContinuityClientTests::settled, SETTLE_TICKS)
                .run("meme regard", context -> look(context, 200.0f, 0.0f))
                .frames(8)
                .capture("la piece, tout recree", image -> {
                    double sum = 0.0;
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            sum += Math.abs(luminance(image.getColor(x, y)) - luminance(before[y * image.getWidth() + x]));
                        }
                    }
                    double mean = sum / (image.getWidth() * image.getHeight());
                    ModConstants.LOGGER.info("[Test client] Cache de lumiere rendu puis recree : ecart moyen {}.", String.format(Locale.ROOT, "%.2f", mean));
                    ClientTestContext.require(mean <= MAX_RETURN, String.format(Locale.ROOT,
                            "La piece ne revient pas telle quelle apres la liberation du cache de lumiere : ecart moyen %.2f.", mean));
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
            // Les scenarios qui font voler le joueur le reposent : le suivant le trouverait en vol.
            ClientTestContext.serverPlayer(server).getAbilities().flying = false;
            ClientTestContext.serverPlayer(server).sendAbilitiesUpdate();
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
        int texture = LightVolume.farTexture();
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
