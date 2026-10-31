package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;

/**
 * Continuite d'une creature qui traverse un cadre vers une autre dimension, telle que le joueur la voit : a vitesse
 * constante, sa copie affichee ne s'arrete pas et ne saute pas au passage. Chaque image, la position affichee est relevee
 * dans le monde ou est la copie, ramenee aux coordonnees de l'arrivee par la transformation du portail si elle est encore
 * de ce cote : le passage d'un monde a l'autre se compare comme n'importe quel pas. Le serveur maintient la vitesse du
 * corps a chaque tick, d'un cote comme de l'autre.
 */
final class EntityContinuityClientTests {

    /** Vitesse du corps, en blocs par tick : celle d'un mob qui marche. */
    private static final double SPEED = 0.1;
    /** Ticks laisses au corps pour traverser et s'eloigner de l'arrivee. */
    private static final int TRAVEL_TICKS = 70;
    /** Plus long pas tolere, en pas medians : au-dela, la copie saute. */
    private static final double MAX_STEP_RATIO = 4.0;
    /** Pas sous cette part du pas median : la copie semble arretee. */
    private static final double STILL_RATIO = 0.25;
    /** Plus longue suite d'images ou la copie semble arretee (deux ticks a 120 images par seconde). */
    private static final int MAX_STILL_FRAMES = 8;
    private static final int MAX_FRAMES = 4000;

    private static final double[] STEPS = new double[MAX_FRAMES];
    private static int stepCount;
    private static int missingFrames;
    private static int worldSwitches;
    private static @Nullable Vec3d last;
    private static @Nullable ClientWorld lastWorld;
    private static @Nullable UUID body;
    private static @Nullable BackroomsPortalEntity frame;
    private static boolean tracking;
    private static boolean installed;

    private EntityContinuityClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(crossesWithoutStopping("armor_stand_crosses_without_stopping", EntityType.ARMOR_STAND),
                crossesWithoutStopping("pig_crosses_without_stopping", EntityType.PIG));
    }

    private static void install() {
        if (installed) {
            return;
        }
        installed = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!tracking || body == null) {
                return;
            }
            for (ServerWorld world : server.getWorlds()) {
                Entity entity = world.getEntity(body);
                if (entity != null) {
                    Vec3d velocity = entity.getVelocity();
                    double horizontal = Math.hypot(velocity.x, velocity.z);
                    if (horizontal > 1.0e-3) {
                        entity.setVelocity(velocity.x / horizontal * SPEED, velocity.y, velocity.z / horizontal * SPEED);
                    }
                }
            }
        });
        WorldRenderEvents.END.register(context -> {
            if (tracking && !PortalRenderState.isRendering()) {
                record(context.tickCounter().getTickDelta(false));
            }
        });
    }

    /** Releve de l'image : position affichee de la copie, aux coordonnees de l'arrivee. */
    private static void record(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld levelZero = RemoteWorldRegistry.worldOf(BackroomsConstants.LEVEL_ZERO_WORLD);
        Entity arrived = levelZero == null ? null : find(levelZero, body);
        Entity leaving = find(client.world, body);
        Entity shown = arrived != null ? arrived : leaving;
        if (shown == null) {
            if (last != null) {
                missingFrames++;
            }
            return;
        }
        Vec3d at = new Vec3d(MathHelper.lerp(tickDelta, shown.lastRenderX, shown.getX()), MathHelper.lerp(tickDelta, shown.lastRenderY, shown.getY()),
                MathHelper.lerp(tickDelta, shown.lastRenderZ, shown.getZ()));
        if (shown == leaving) {
            at = frame.toDestination(at);
        }
        ClientWorld world = (ClientWorld) shown.getWorld();
        if (lastWorld != null && world != lastWorld) {
            worldSwitches++;
        }
        lastWorld = world;
        if (last != null && stepCount < MAX_FRAMES) {
            STEPS[stepCount++] = at.distanceTo(last);
        }
        last = at;
    }

    private static @Nullable Entity find(ClientWorld world, @Nullable UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    /** Releve fini : la copie a change de monde une fois, sans image ou elle manque, sans arret ni saut une fois en marche. */
    private static void requireContinuous(String what) {
        tracking = false;
        // La copie ne bouge chez le client qu'a la premiere position recue du serveur : le releve commence au premier pas.
        int first = 0;
        while (first < stepCount && STEPS[first] == 0.0) {
            first++;
        }
        double[] moving = Arrays.copyOfRange(STEPS, first, stepCount);
        double[] sorted = moving.clone();
        Arrays.sort(sorted);
        ClientTestContext.require(sorted.length > 0, what + " : la copie ne s'est jamais deplacee.");
        double median = sorted[sorted.length / 2];
        double longest = sorted[sorted.length - 1];
        int still = 0;
        int longestStill = 0;
        for (double step : moving) {
            still = step < STILL_RATIO * median ? still + 1 : 0;
            longestStill = Math.max(longestStill, still);
        }
        ModConstants.LOGGER.info("[Test client] {} : {} images, pas median {}, plus grand {}, arret le plus long {} image(s), {} changement(s) de monde",
                what, moving.length, String.format(Locale.ROOT, "%.4f", median), String.format(Locale.ROOT, "%.4f", longest), longestStill, worldSwitches);
        ClientTestContext.require(worldSwitches == 1, what + " : la copie a change " + worldSwitches + " fois de monde au lieu d'une.");
        ClientTestContext.require(missingFrames == 0, what + " : la copie a manque " + missingFrames + " image(s).");
        ClientTestContext.require(longest <= MAX_STEP_RATIO * median, String.format(Locale.ROOT,
                "%s : la copie saute de %.3f bloc en une image (pas median %.4f).", what, longest, median));
        ClientTestContext.require(longestStill <= MAX_STILL_FRAMES, what + " : la copie reste arretee " + longestStill + " images de suite.");
    }

    private static ClientScenario crossesWithoutStopping(String name, EntityType<?> type) {
        return ClientScenario.named(name)
                .run("portail et joueur de cote", context -> {
                    install();
                    ClientTestPortals.place(context, new PortalSettings(3, 3, true));
                    ClientTestPortals.standAt(context, 3.5, ClientTestPortals.PLANE_Z - 3.0, 45.0f, 10.0f);
                })
                .await("portail recu", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 prete", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .frames(30)
                .run("corps lance vers le cadre", context -> {
                    frame = ClientTestPortals.clientPortal(context);
                    stepCount = 0;
                    missingFrames = 0;
                    worldSwitches = 0;
                    last = null;
                    lastWorld = null;
                    context.onServer(server -> {
                        ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
                        Entity entity = type.create(overworld);
                        entity.refreshPositionAndAngles(0.5, ClientTestPortals.FLOOR_Y, ClientTestPortals.PLANE_Z - 3.0, 0.0f, 0.0f);
                        entity.setVelocity(0.0, 0.0, SPEED);
                        overworld.spawnEntity(entity);
                        body = entity.getUuid();
                    });
                    tracking = true;
                })
                .ticks(TRAVEL_TICKS)
                .check("la copie passe sans s'arreter ni sauter", context -> requireContinuous(name))
                .cleanup(context -> {
                    tracking = false;
                    context.onServer(server -> {
                        for (ServerWorld world : server.getWorlds()) {
                            Entity entity = world.getEntity(body);
                            if (entity != null) {
                                entity.discard();
                            }
                        }
                    });
                })
                .cleanup(ClientTestPortals::remove);
    }
}
