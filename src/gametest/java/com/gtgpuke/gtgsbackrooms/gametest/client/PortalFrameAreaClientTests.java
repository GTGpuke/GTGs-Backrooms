package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Zone de vue de chaque orientation de cadre : debout, au sol, au plafond, plan au milieu ou au bord du bloc, vers chaque
 * sortie (droite, au sol, au plafond), vue de face, de biais, de pres et de dos, puis le cadre d'arrivee vu depuis le
 * Level 0. A chaque point de vue, les pixels de l'image qui portent le look de l'autre monde (bits du stencil) doivent
 * couvrir exactement l'ouverture du cadre projetee a l'ecran, calculee ici depuis la camera de l'image et la geometrie du
 * cadre : un cadre dont une partie montre le bloc du dessous, le mur derriere lui ou rien, ou une vue qui deborde du
 * cadre, fait echouer le test.
 */
final class PortalFrameAreaClientTests {

    /** Portail debout, au sol : le bloc a l'emplacement des autres tests du client. */
    private static final BlockPos STANDING_OR_FLOOR = new BlockPos(0, -60, 4);
    /** Portail au plafond, en l'air : le point de vue le plus lointain, quatre blocs dessous, reste au-dessus du sol. */
    private static final BlockPos CEILING = new BlockPos(0, -54, 4);
    /** Largeur et hauteur des cadres : celles du cadre de l'auteur ou le bug du sol a ete signale. */
    private static final int WIDTH = 3;
    private static final int HEIGHT = 4;
    /** Images laissees au client apres chaque deplacement : vue construite, image stable. */
    private static final int SETTLE_FRAMES = 20;
    /** Ecart tolere : un demi-pixel sur tout le contour (pixels de bord), plus un pour cent de l'aire. */
    private static final double EDGE_SHARE = 0.5;
    private static final double AREA_SHARE = 0.01;
    /** Chunks de l'Overworld recus avant de tenir pour prete la vue du cadre d'arrivee. */
    private static final int MIN_VIEW_CHUNKS = 9;
    private static final double EYE_HEIGHT = 1.62;
    /** Point de passage vers la vue de dos, loin du cadre : le serveur ne prend pas ce saut pour une traversee. */
    private static final Vec3d DETOUR = new Vec3d(20.0, 10.0, 0.0);
    private static final int DETOUR_TICKS = 3;

    /** Points de vue d'un cadre source : distance au plan, decalages en largeur et en hauteur (blocs). */
    private static final double[][] SOURCE_VIEWS = {{4.0, 0.0, 0.0}, {2.5, 1.8, 0.6}, {1.2, 0.0, 0.0}};
    /** De loin : de face pour un cadre debout, en rasant pour un cadre couche. */
    private static final double[] FAR_STANDING_VIEW = {12.0, 0.0, 0.0};
    private static final double[] FAR_LYING_VIEW = {3.0, 0.0, -12.0};
    /**
     * Cadre au sol vu d'en haut, de loin, le regard perpendiculaire au plan : l'avance de la zone vers la camera ne
     * l'emporte plus sur la precision de la profondeur (au-dela d'environ neuf blocs), et seul le decalage de polygone la
     * garde devant la face du sol dans son plan (cadre a ras). En rasant, l'avance compte plusieurs fois le long du regard.
     */
    private static final double[] FAR_ABOVE_VIEW = {15.0, 0.0, 0.0};
    /**
     * Point de vue de dos (debout ou au plafond : sous un cadre au sol, il y a le sol). Un cadre a deux faces n'y montre
     * l'autre monde que si ni son entree ni son arrivee n'est couchee ; sinon, il n'a que sa face avant.
     */
    private static final double[] BACK_VIEW = {-3.0, 0.0, 0.0};
    /** Points de vue du cadre d'arrivee, dans le Level 0 : l'espace devant la sortie est libre. */
    private static final double[][] ARRIVAL_VIEWS = {{2.2, 0.0, 0.0}, {2.2, 0.5, 0.4}};
    /**
     * Cadre d'arrivee au sol vu par qui vient d'en sortir : debout a cote, sur le sol, le regard vers son centre (le cas ou
     * l'auteur a signale un cadre au sol montrant le bloc du dessous).
     */
    private static final double[] GROUND_VIEW = {EYE_HEIGHT, 0.0, -(HEIGHT / 2.0 + 1.2)};

    private PortalFrameAreaClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(matrix("frame_area_standing", Direction.NORTH, STANDING_OR_FLOOR, true),
                matrix("frame_area_floor", Direction.UP, STANDING_OR_FLOOR, false),
                matrix("frame_area_ceiling", Direction.DOWN, CEILING, true));
    }

    /** Une orientation de cadre, plan au milieu puis au bord du bloc, vers chaque sortie. */
    private static ClientScenario matrix(String name, Direction facing, BlockPos block, boolean viewFromBack) {
        List<String> failures = new ArrayList<>();
        BackroomsPortalEntity[] source = new BackroomsPortalEntity[1];
        double[] fovEffect = new double[1];
        ClientScenario scenario = ClientScenario.named(name)
                .run("effet du vol sur le champ de vision coupe", context -> {
                    fovEffect[0] = context.client().options.getFovEffectScale().getValue();
                    context.client().options.getFovEffectScale().setValue(0.0);
                });
        PortalExit[] exits = {PortalExit.STRAIGHT, PortalExit.FLOOR, PortalExit.CEILING};
        for (boolean flush : new boolean[] {false, true}) {
            for (PortalExit exit : exits) {
                String config = String.format(Locale.ROOT, "%s %s, sortie %s", facing.asString(), flush ? "a ras" : "au milieu", exit.asString());
                PortalSettings settings = new PortalSettings(WIDTH, HEIGHT, true, true, false, false, exit, 0, flush);
                scenario = scenario
                        .run(config + " : pose", context -> {
                            place(context, block, facing, settings);
                            fly(context, World.OVERWORLD, Vec3d.ofCenter(block).add(Vec3d.of(facing.getVector()).multiply(4.0)), 0.0f, 0.0f);
                        })
                        .await(config + " : cadre recu", context -> (source[0] = sourceFrame(context, facing, block, exit)) != null,
                                ClientTestPortals.PORTAL_TICKS)
                        .run(config + " : face au cadre", context -> lookFrom(context, World.OVERWORLD, source[0], SOURCE_VIEWS[0]))
                        .await(config + " : vue du Level 0 prete", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS);
                List<double[]> views = new ArrayList<>(List.of(SOURCE_VIEWS));
                views.add(facing.getAxis().isHorizontal() ? FAR_STANDING_VIEW : FAR_LYING_VIEW);
                if (facing == Direction.UP) {
                    views.add(FAR_ABOVE_VIEW);
                }
                if (viewFromBack) {
                    views.add(BACK_VIEW);
                }
                boolean backFace = facing.getAxis().isHorizontal() && exit.arrivalFacing(PortalFacing.fromDirection(facing)).direction.getAxis().isHorizontal();
                for (double[] view : views) {
                    String where = config + String.format(Locale.ROOT, " : vue (%.1f, %.1f, %.1f)", view[0], view[1], view[2]);
                    boolean shown = view[0] > 0.0 || backFace;
                    if (view[0] < 0.0) {
                        // Un saut de moins de huit blocs a travers l'ouverture serait une traversee : detour par un point eloigne.
                        scenario = scenario
                                .run(where + " : detour", context -> fly(context, World.OVERWORLD, source[0].getPos().add(DETOUR), 0.0f, 0.0f))
                                .ticks(DETOUR_TICKS);
                    }
                    scenario = scenario
                            .run(where, context -> lookFrom(context, World.OVERWORLD, source[0], view))
                            .frames(SETTLE_FRAMES)
                            .run(where + " : mesure", context -> measure(context, where, source[0],
                                    LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).id(), shown, failures));
                }
                BackroomsPortalEntity[] arrival = new BackroomsPortalEntity[1];
                scenario = scenario
                        .run(config + " : vers l'arrivee", context -> {
                            Vec3d out = Vec3d.of(source[0].facingDestination().direction.getVector());
                            fly(context, BackroomsConstants.LEVEL_ZERO_WORLD, source[0].destinationPos().add(out.multiply(ARRIVAL_VIEWS[0][0])), 0.0f, 0.0f);
                        })
                        .await(config + " : dans le Level 0", LevelZeroClientTests::inLevelZero, ClientTestPortals.PORTAL_TICKS)
                        .await(config + " : cadre d'arrivee recu", context -> (arrival[0] = arrivalFrame(context, source[0])) != null,
                                ClientTestPortals.PORTAL_TICKS)
                        .await(config + " : vue de l'Overworld prete", PortalFrameAreaClientTests::overworldViewReady, ClientTestPortals.PORTAL_TICKS);
                List<double[]> arrivalViews = new ArrayList<>(List.of(ARRIVAL_VIEWS));
                if (exit == PortalExit.FLOOR) {
                    arrivalViews.add(GROUND_VIEW);
                }
                for (double[] view : arrivalViews) {
                    String where = config + String.format(Locale.ROOT, " : arrivee (%.1f, %.1f, %.1f)", view[0], view[1], view[2]);
                    scenario = scenario
                            .run(where, context -> lookFrom(context, BackroomsConstants.LEVEL_ZERO_WORLD, arrival[0], view))
                            .frames(SETTLE_FRAMES)
                            .run(where + " : mesure", context -> measure(context, where, arrival[0], LookProfiles.of(World.OVERWORLD).id(), true, failures));
                }
                scenario = scenario
                        .run(config + " : retour et retrait", context -> {
                            remove(context, block);
                            fly(context, World.OVERWORLD, new Vec3d(0.5, ClientTestPortals.FLOOR_Y + EYE_HEIGHT, 0.5), 0.0f, 0.0f);
                        })
                        .await(config + " : dans l'Overworld", context -> context.client().world != null
                                && context.client().world.getRegistryKey() == World.OVERWORLD && ClientPortalIndex.portals(context.world()).isEmpty(),
                                ClientTestPortals.PORTAL_TICKS);
            }
        }
        return scenario
                .check("chaque cadre couvre exactement son ouverture", context ->
                        ClientTestContext.require(failures.isEmpty(), failures.size() + " zone(s) fausse(s) :\n" + String.join("\n", failures)))
                .cleanup(context -> {
                    context.client().options.getFovEffectScale().setValue(fovEffect[0]);
                    remove(context, block);
                    context.onServer(server -> {
                        ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
                        player.getAbilities().flying = false;
                        player.sendAbilitiesUpdate();
                    });
                })
                .cleanup(LevelZeroClientTests::backToOverworld);
    }

    /** Pose le bloc portail dans cette orientation, aux reglages donnes, cote serveur. */
    private static void place(ClientTestContext context, BlockPos block, Direction facing, PortalSettings settings) {
        context.onServer(server -> {
            ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
            overworld.setBlockState(block, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, facing));
            if (overworld.getBlockEntity(block) instanceof BackroomsPortalBlockEntity portal) {
                portal.readComponents(ClientTestPortals.wand(settings));
            }
        });
    }

    private static void remove(ClientTestContext context, BlockPos block) {
        context.onServer(server -> ClientTestContext.serverWorld(server, World.OVERWORLD).setBlockState(block, Blocks.AIR.getDefaultState()));
    }

    /** Le joueur en vol, les yeux a ce point, avec ce regard. */
    private static void fly(ClientTestContext context, RegistryKey<World> dimension, Vec3d eye, float yaw, float pitch) {
        context.onServer(server -> {
            ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            player.teleport(ClientTestContext.serverWorld(server, dimension), eye.x, eye.y - EYE_HEIGHT, eye.z, yaw, pitch);
        });
    }

    /** Les yeux du joueur a ce point de vue du cadre (distance au plan, decalages en largeur et en hauteur), regard vers son centre. */
    private static void lookFrom(ClientTestContext context, RegistryKey<World> dimension, BackroomsPortalEntity portal, double[] view) {
        PortalFacing facing = portal.facingSource();
        Vec3d center = portal.getPos();
        Vec3d eye = center.add(facing.normal.multiply(view[0])).add(facing.axisW.multiply(view[1])).add(facing.axisH.multiply(view[2]));
        Vec3d to = center.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
        fly(context, dimension, eye, yaw, pitch);
    }

    /** Face du cadre pose dans l'Overworld, dans l'orientation du bloc, vers la sortie reglee, ou {@code null}. */
    private static @Nullable BackroomsPortalEntity sourceFrame(ClientTestContext context, Direction facing, BlockPos block, PortalExit exit) {
        if (context.client().world == null || context.client().world.getRegistryKey() != World.OVERWORLD) {
            return null;
        }
        for (BackroomsPortalEntity portal : ClientPortalIndex.portals(context.world())) {
            if (portal.facingSource().direction == facing && portal.getPos().isInRange(Vec3d.ofCenter(block), Math.max(WIDTH, HEIGHT))
                    && portal.facingDestination() == exit.arrivalFacing(portal.facingSource())) {
                return portal;
            }
        }
        return null;
    }

    /** Cadre d'arrivee de ce portail, cote client, dans le Level 0 : celui dont la face regarde vers la sortie, ou {@code null}. */
    private static @Nullable BackroomsPortalEntity arrivalFrame(ClientTestContext context, BackroomsPortalEntity source) {
        if (context.client().world == null || context.client().world.getRegistryKey() != BackroomsConstants.LEVEL_ZERO_WORLD) {
            return null;
        }
        for (BackroomsPortalEntity portal : ClientPortalIndex.portals(context.world())) {
            if (portal.facingSource() == source.facingDestination() && portal.getPos().isInRange(source.destinationPos(), 1.0)) {
                return portal;
            }
        }
        return null;
    }

    private static boolean overworldViewReady(ClientTestContext context) {
        RemoteWorld remote = RemoteWorldRegistry.get(World.OVERWORLD);
        return remote != null && remote.world().getChunkManager().getLoadedChunkCount() >= MIN_VIEW_CHUNKS;
    }

    /**
     * Compare les pixels au look de l'autre monde a l'ouverture du cadre projetee depuis la camera de la derniere image, ou a
     * zero si ce point de vue ne doit rien en montrer ({@code shown}) ; un ecart au-dela de la tolerance s'ajoute aux echecs.
     */
    private static void measure(ClientTestContext context, String where, BackroomsPortalEntity portal, int look, boolean shown, List<String> failures) {
        MinecraftClient client = context.client();
        double[] expected = projectedOpening(client, portal);
        int actual = ClientTestStencil.pixelsWithLook(client, look);
        if (expected == null) {
            failures.add(where + " : un coin du cadre est derriere la camera, point de vue a revoir.");
            return;
        }
        double tolerance = EDGE_SHARE * expected[1] + AREA_SHARE * expected[0];
        double wanted = shown ? expected[0] : 0.0;
        String line = String.format(Locale.ROOT, "%s : %d pixel(s) au look de l'autre monde, %.0f attendu(s) (tolerance %.0f).",
                where, actual, wanted, tolerance);
        ModConstants.LOGGER.info("[Test client] {}", line);
        if (expected[0] < 1000.0) {
            failures.add(line + " Cadre trop petit a l'ecran : point de vue a revoir.");
        } else if (Math.abs(actual - wanted) > tolerance) {
            failures.add(line);
        }
    }

    /**
     * Aire et contour, en pixels, de l'ouverture du cadre projetee par la camera de la derniere image avec la projection de
     * vanilla, decoupee aux bords de l'ecran ; {@code null} si un coin est derriere la camera.
     */
    private static double @Nullable [] projectedOpening(MinecraftClient client, BackroomsPortalEntity portal) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d eye = camera.getPos();
        Quaternionf view = camera.getRotation().conjugate(new Quaternionf());
        Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue());
        int width = client.getFramebuffer().textureWidth;
        int height = client.getFramebuffer().textureHeight;
        PortalFacing facing = portal.facingSource();
        double halfWidth = portal.width() / 2.0;
        double halfHeight = portal.height() / 2.0;
        double[] polygon = new double[4 * 2];
        double[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < corners.length; i++) {
            Vec3d corner = portal.getPos().add(facing.axisW.multiply(corners[i][0] * halfWidth)).add(facing.axisH.multiply(corners[i][1] * halfHeight));
            Vector3f relative = new Vector3f((float) (corner.x - eye.x), (float) (corner.y - eye.y), (float) (corner.z - eye.z));
            view.transform(relative);
            Vector4f clip = projection.transform(new Vector4f(relative, 1.0f));
            if (clip.w <= 0.0f) {
                return null;
            }
            polygon[i * 2] = (clip.x / clip.w + 1.0) / 2.0 * width;
            polygon[i * 2 + 1] = (clip.y / clip.w + 1.0) / 2.0 * height;
        }
        double[] clipped = clipToRectangle(polygon, width, height);
        double area = 0.0;
        double perimeter = 0.0;
        int count = clipped.length / 2;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            area += clipped[i * 2] * clipped[j * 2 + 1] - clipped[j * 2] * clipped[i * 2 + 1];
            perimeter += Math.hypot(clipped[j * 2] - clipped[i * 2], clipped[j * 2 + 1] - clipped[i * 2 + 1]);
        }
        return new double[] {Math.abs(area) / 2.0, perimeter};
    }

    /** Polygone (x, y a la suite) decoupe au rectangle [0, largeur] x [0, hauteur] (Sutherland-Hodgman). */
    private static double[] clipToRectangle(double[] polygon, double width, double height) {
        double[] result = polygon;
        double[][] edges = {{1, 0, 0}, {-1, 0, width}, {0, 1, 0}, {0, -1, height}};
        for (double[] edge : edges) {
            int count = result.length / 2;
            List<Double> kept = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double ax = result[i * 2];
                double ay = result[i * 2 + 1];
                double bx = result[((i + 1) % count) * 2];
                double by = result[((i + 1) % count) * 2 + 1];
                double da = inside(edge, ax, ay);
                double db = inside(edge, bx, by);
                if (da >= 0) {
                    kept.add(ax);
                    kept.add(ay);
                }
                if (da >= 0 != db >= 0) {
                    double t = da / (da - db);
                    kept.add(ax + (bx - ax) * t);
                    kept.add(ay + (by - ay) * t);
                }
            }
            result = new double[kept.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = kept.get(i);
            }
            if (result.length == 0) {
                return result;
            }
        }
        return result;
    }

    /** Distance signee d'un point au bord du rectangle : positive du cote de l'ecran. */
    private static double inside(double[] edge, double x, double y) {
        return edge[0] * x + edge[1] * y + edge[2];
    }
}
