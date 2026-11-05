package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalMark;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;

/**
 * Un corps passe un cadre qui le tourne d'un quart de tour, dans le meme monde, tel que le joueur le voit : le joueur a la
 * troisieme personne, puis un cochon. Le corps tourne avec la tete, sans tour sur lui-meme : chaque image, l'ecart entre le
 * corps et la tete affiches reste celui d'une marche droite, avant le cadre comme apres.
 */
final class CrossingTurnClientTests {

    /** Pieds a la sortie, plus loin dans l'Overworld, face a l'est : ce qui entre vers le sud ressort vers l'est. */
    private static final BlockPos EXIT = new BlockPos(0, -60, 20);
    /** Regard vers l'est, en degres. */
    private static final float EAST = -90.0f;
    /** Plus grand ecart tolere entre le corps et la tete, en degres : en marche droite, le corps suit la tete de pres. */
    private static final float MAX_BODY_GAP = 20.0f;
    /** Plus grand ecart tolere entre la tete et l'est apres le passage, en degres : le portail tourne le regard d'un quart de tour. */
    private static final float MAX_HEAD_GAP = 20.0f;
    /** Saut de la position affichee d'une image a l'autre, en blocs, au-dela duquel le corps a passe le cadre. */
    private static final double CROSSING_JUMP = 5.0;
    /** Vitesse du cochon, en blocs par tick : celle d'un mob qui marche. */
    private static final double SPEED = 0.1;
    /** Ticks laisses au cochon pour passer le cadre, a trois blocs, et marcher de l'autre cote. */
    private static final int PIG_TICKS = 60;

    private static volatile @Nullable UUID tracked;
    private static volatile boolean recording;
    private static boolean installed;
    private static float worstGap;
    private static int worstAt;
    private static int frames;
    private static int crossings;
    private static float lastHead;
    private static @Nullable Vec3d last;

    private CrossingTurnClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(playerBodyTurnsWithTheFrame(), pigBodyTurnsWithTheFrame());
    }

    private static void install() {
        if (installed) {
            return;
        }
        installed = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!recording || tracked == null) {
                return;
            }
            for (ServerWorld world : server.getWorlds()) {
                if (world.getEntity(tracked) instanceof MobEntity mob) {
                    // Une creature qui erre manque le cadre : ses buts tombent a chaque tick, et sa vitesse reste celle d'une marche.
                    mob.clearGoals(goal -> true);
                    mob.getNavigation().stop();
                    Vec3d velocity = mob.getVelocity();
                    double horizontal = Math.hypot(velocity.x, velocity.z);
                    if (horizontal > 1.0e-3) {
                        mob.setVelocity(velocity.x / horizontal * SPEED, velocity.y, velocity.z / horizontal * SPEED);
                    }
                }
            }
        });
        WorldRenderEvents.END.register(context -> {
            if (recording && !PortalRenderState.isRendering()) {
                record(context.tickCounter().getTickDelta(false));
            }
        });
    }

    /** Releve de l'image : ecart entre le corps et la tete affiches, et passage du cadre (un saut de la position affichee). */
    private static void record(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        LivingEntity body = find(client);
        if (body == null) {
            return;
        }
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, body.prevBodyYaw, body.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, body.prevHeadYaw, body.headYaw);
        float gap = Math.abs(MathHelper.wrapDegrees(bodyYaw - headYaw));
        if (gap > worstGap) {
            worstGap = gap;
            worstAt = frames;
        }
        Vec3d at = new Vec3d(MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()), MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()),
                MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()));
        if (last != null && at.distanceTo(last) > CROSSING_JUMP) {
            crossings++;
        }
        last = at;
        lastHead = headYaw;
        frames++;
    }

    private static @Nullable LivingEntity find(MinecraftClient client) {
        if (client.world == null || tracked == null) {
            return null;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity.getUuid().equals(tracked) && entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void start(@Nullable UUID body) {
        tracked = body;
        worstGap = 0.0f;
        worstAt = -1;
        frames = 0;
        crossings = 0;
        last = null;
        recording = true;
    }

    /** Releve fini : le corps a passe le cadre une fois et regarde vers l'est, sans s'ecarter de sa tete. */
    private static void requireTurned(String what) {
        recording = false;
        ModConstants.LOGGER.info("[Test client] {} : {} images, {} passage(s), ecart du corps a la tete au plus {} degres (image {}).", what, frames,
                crossings, String.format(Locale.ROOT, "%.1f", worstGap), worstAt);
        ClientTestContext.require(crossings == 1, what + " : " + crossings + " passage(s) du cadre au lieu d'un.");
        ClientTestContext.require(Math.abs(MathHelper.wrapDegrees(lastHead - EAST)) < MAX_HEAD_GAP, String.format(Locale.ROOT,
                "%s : la tete regarde a %.1f degres au lieu de l'est.", what, lastHead));
        ClientTestContext.require(worstGap <= MAX_BODY_GAP, String.format(Locale.ROOT,
                "%s : le corps s'ecarte de %.1f degres de la tete (image %d sur %d).", what, worstGap, worstAt, frames));
    }

    /** Portail vers une sortie plus loin dans l'Overworld, qui tourne d'un quart de tour ; joueur pose devant, vers le cadre. */
    private static ClientScenario withTurningPortal(String name, double playerX, float playerYaw) {
        PortalSettings turning = PortalSettings.DEFAULT.withExit(Optional.of(World.OVERWORLD),
                Optional.of(new PortalMark(World.OVERWORLD, EXIT, Direction.EAST)));
        return ClientScenario.named(name)
                .run("portail qui tourne d'un quart de tour, joueur devant", context -> {
                    install();
                    ClientTestPortals.place(context, turning);
                    ClientTestPortals.standAt(context, playerX, ClientTestPortals.PLANE_Z - 3.0, playerYaw, 10.0f);
                })
                .await("portail recu", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .frames(60);
    }

    private static ClientScenario playerBodyTurnsWithTheFrame() {
        return withTurningPortal("player_body_turns_with_the_frame", 0.5, 0.0f)
                .run("troisieme personne, marche vers le cadre", context -> {
                    start(context.player().getUuid());
                    context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    context.client().options.forwardKey.setPressed(true);
                })
                .await("cadre passe", context -> crossings > 0, 200)
                .ticks(20)
                .run("arret", context -> context.client().options.forwardKey.setPressed(false))
                .check("le corps tourne avec la tete", context -> requireTurned("joueur"))
                .cleanup(context -> {
                    recording = false;
                    context.client().options.forwardKey.setPressed(false);
                    context.client().options.setPerspective(Perspective.FIRST_PERSON);
                })
                .cleanup(ClientTestPortals::remove);
    }

    private static ClientScenario pigBodyTurnsWithTheFrame() {
        UUID[] pig = new UUID[1];
        return withTurningPortal("pig_body_turns_with_the_frame", 3.5, 45.0f)
                .run("cochon lance vers le cadre", context -> {
                    context.onServer(server -> {
                        ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
                        Entity entity = EntityType.PIG.create(overworld);
                        entity.refreshPositionAndAngles(0.5, ClientTestPortals.FLOOR_Y, ClientTestPortals.PLANE_Z - 3.0, 0.0f, 0.0f);
                        entity.setVelocity(0.0, 0.0, SPEED);
                        overworld.spawnEntity(entity);
                        pig[0] = entity.getUuid();
                    });
                    start(pig[0]);
                })
                .ticks(PIG_TICKS)
                .check("le corps tourne avec la tete", context -> requireTurned("cochon"))
                .cleanup(context -> {
                    recording = false;
                    context.onServer(server -> {
                        Entity entity = pig[0] == null ? null : ClientTestContext.serverWorld(server, World.OVERWORLD).getEntity(pig[0]);
                        if (entity != null) {
                            entity.discard();
                        }
                    });
                })
                .cleanup(ClientTestPortals::remove);
    }
}
