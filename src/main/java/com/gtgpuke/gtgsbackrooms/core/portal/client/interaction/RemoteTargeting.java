package com.gtgpuke.gtgsbackrooms.core.portal.client.interaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;

/**
 * Visee a travers un portail : ce que le regard du joueur touche de l'autre cote.
 *
 * <p>Apres le raycast vanilla de chaque image, si le regard traverse le cadre d'un portail avant sa cible (ou si sa
 * cible est un bloc que le portail cache derriere son plan, quand le cadre est pose contre un mur), le rayon est
 * transpose dans la dimension d'arrivee et poursuivi sur la portee restante, contre les blocs
 * puis les entities de la vue. Vanilla recoit alors une cible manquee : ce qui est derriere le cadre dans
 * le monde du joueur ne se vise plus. Les actions et le contour du bloc vise lisent l'etat garde ici.
 */
public final class RemoteTargeting {

    private static final TypeFilter<Entity, Entity> ENTITY_FILTER = TypeFilter.instanceOf(Entity.class);
    /**
     * Les portails ne se visent pas ({@code canHit} faux) : ils se traversent. Comme le rayon vanilla, celui qui regarde
     * ne se vise pas lui-meme, son corps vu dans un portail de son propre monde.
     */
    private static final Predicate<Entity> TARGETABLE = entity -> !entity.isSpectator() && entity.canHit()
            && entity != MinecraftClient.getInstance().getCameraEntity();
    private static final List<Entity> ENTITY_SCRATCH = new ArrayList<>();
    /** Depart du rayon distant un peu devant le plan d'arrivee, pour ne pas toucher ce qui l'affleure. */
    private static final double RAY_OFFSET = 1.0e-3;
    private static final double PARALLEL_EPSILON = 1.0e-6;

    private static @Nullable BackroomsPortalEntity portal;
    private static @Nullable RemoteWorld view;
    private static @Nullable HitResult hit;

    private RemoteTargeting() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Portail que le regard traverse, ou {@code null} si le joueur vise dans son propre monde. */
    public static @Nullable BackroomsPortalEntity portal() {
        return portal;
    }

    /** Vue dans laquelle le regard ressort, ou {@code null}. */
    public static @Nullable RemoteWorld view() {
        return view;
    }

    /** Cible de l'autre cote, en coordonnees de la dimension d'arrivee, ou {@code null} si le regard n'y touche rien. */
    public static @Nullable HitResult hit() {
        return hit;
    }

    /** Bloc vise de l'autre cote d'un portail, ou {@code null}. */
    public static @Nullable BlockHitResult blockHit() {
        return hit instanceof BlockHitResult blockHit ? blockHit : null;
    }

    /** Entity visee de l'autre cote d'un portail, ou {@code null}. */
    public static @Nullable EntityHitResult entityHit() {
        return hit instanceof EntityHitResult entityHit ? entityHit : null;
    }

    /** Oublie la cible : debut de chaque mise a jour, et deconnexion. */
    public static void clear() {
        portal = null;
        view = null;
        hit = null;
    }

    /**
     * Appele apres {@code GameRenderer.updateCrosshairTarget} : remplace la cible vanilla si le regard traverse un portail avant elle.
     *
     * @param client client du joueur
     * @param tickDelta fraction de tick de l'image
     */
    public static void update(MinecraftClient client, float tickDelta) {
        clear();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (!ModConfig.ENABLE_PORTAL_FEATURE || player == null || world == null
                || !PortalRenderState.remoteRenderingAvailable() || client.getCameraEntity() != player) {
            return;
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        if (portals.isEmpty()) {
            return;
        }
        Vec3d eye = player.getCameraPosVec(tickDelta);
        Vec3d direction = player.getRotationVec(tickDelta);
        double blockReach = player.getBlockInteractionRange();
        double entityReach = player.getEntityInteractionRange();
        double reach = Math.max(blockReach, entityReach);
        HitResult vanilla = client.crosshairTarget;
        double vanillaDistance = vanilla != null && vanilla.getType() != HitResult.Type.MISS ? vanilla.getPos().distanceTo(eye) : reach;
        BlockPos vanillaBlock = vanilla instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        BackroomsPortalEntity crossed = null;
        double crossedAt = reach;
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity candidate = portals.get(i);
            if (!candidate.visible()) {
                continue;
            }
            double t = frameHit(candidate, eye, direction);
            // Un bloc que le portail cache derriere son plan (cadre pose contre un mur) n'arrete pas le regard : vanilla le
            // touche au plan meme, le regard passe par le cadre.
            boolean hidden = vanillaBlock != null && PortalCollision.hides(candidate, vanillaBlock.getX(), vanillaBlock.getY(), vanillaBlock.getZ());
            if (t >= 0.0 && t < crossedAt && (hidden || t < vanillaDistance)) {
                crossed = candidate;
                crossedAt = t;
            }
        }
        if (crossed == null) {
            return;
        }
        if (!crossed.teleportable()) {
            // Portail ferme : on voit a travers le cadre, rien ne s'y atteint ; le regard s'arrete au plan.
            Vec3d stop = eye.add(direction.multiply(crossedAt));
            client.crosshairTarget = BlockHitResult.createMissed(stop, Direction.getFacing(direction), BlockPos.ofFloored(stop));
            client.targetedEntity = null;
            return;
        }
        RemoteWorld target = RemoteWorldRegistry.viewOf(crossed);
        if (target == null) {
            return;
        }
        PortalFacing source = crossed.facingSource();
        PortalFacing exit = crossed.facingDestination();
        Vec3d crossing = eye.add(direction.multiply(crossedAt));
        Vec3d origin = crossed.toDestination(crossing).add(exit.normal.multiply(RAY_OFFSET));
        Vec3d remoteDirection = PortalFacing.transformVector(source, exit, direction);
        ClientWorld remoteWorld = target.world();

        double blockRemaining = blockReach - crossedAt;
        BlockHitResult blockHit = null;
        double blockDistance = Double.MAX_VALUE;
        if (blockRemaining > 0.0) {
            blockHit = remoteWorld.raycast(new RaycastContext(origin, origin.add(remoteDirection.multiply(blockRemaining)),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                blockDistance = blockHit.getPos().distanceTo(origin);
            } else {
                blockHit = null;
            }
        }
        EntityHitResult entityHit = raycastEntities(remoteWorld, origin, remoteDirection, Math.min(entityReach - crossedAt, blockDistance));

        portal = crossed;
        view = target;
        hit = entityHit != null ? entityHit : blockHit;
        // Vanilla ne doit plus viser ce qui est derriere le cadre dans le monde du joueur.
        client.crosshairTarget = BlockHitResult.createMissed(crossing, Direction.getFacing(direction), BlockPos.ofFloored(crossing));
        client.targetedEntity = null;
    }

    /** Distance le long du rayon jusqu'au cadre du portail, ou -1 si le rayon ne le traverse pas depuis le cote d'entree. */
    private static double frameHit(BackroomsPortalEntity candidate, Vec3d eye, Vec3d direction) {
        double along = direction.dotProduct(candidate.facingSource().normal);
        double distance = candidate.signedDistance(eye);
        if (distance <= 0.0 || along >= -PARALLEL_EPSILON) {
            return -1.0;
        }
        double t = -distance / along;
        return candidate.frameContains(eye.x + direction.x * t, eye.y + direction.y * t, eye.z + direction.z * t) ? t : -1.0;
    }

    /** Entity la plus proche touchee par le rayon dans la vue ; {@code ProjectileUtil} travaillerait dans le monde du joueur. */
    private static @Nullable EntityHitResult raycastEntities(ClientWorld world, Vec3d origin, Vec3d direction, double maxDistance) {
        if (maxDistance <= 0.0) {
            return null;
        }
        Vec3d end = origin.add(direction.multiply(maxDistance));
        ENTITY_SCRATCH.clear();
        world.collectEntitiesByType(ENTITY_FILTER, new Box(origin, end).expand(1.0), TARGETABLE, ENTITY_SCRATCH);
        Entity nearest = null;
        Vec3d nearestPos = null;
        double nearestDistance = maxDistance * maxDistance;
        for (int i = 0; i < ENTITY_SCRATCH.size(); i++) {
            Entity entity = ENTITY_SCRATCH.get(i);
            Optional<Vec3d> point = entity.getBoundingBox().expand(entity.getTargetingMargin()).raycast(origin, end);
            if (point.isPresent()) {
                double distance = origin.squaredDistanceTo(point.get());
                if (distance < nearestDistance) {
                    nearest = entity;
                    nearestPos = point.get();
                    nearestDistance = distance;
                }
            }
        }
        ENTITY_SCRATCH.clear();
        return nearest == null ? null : new EntityHitResult(nearest, nearestPos);
    }
}
