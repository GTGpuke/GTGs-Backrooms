package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.EntityHandover;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalClipping;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackPortalRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackState;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Entities a cheval sur le plan d'un portail, dessinees en deux moities : chacune coupee au plan, dans le monde ou elle
 * se trouve. Celui qui traverse est ainsi a la fois d'un cote et de l'autre, quel que soit l'angle de vue.
 *
 * <p>Une entity coupee par une face de portail de son monde (a cheval sur le cadre, le centre devant la face et en face
 * de l'ouverture) : vanilla ne la dessine pas ; sa partie devant la face se dessine dans son monde, coupee au plan, sa
 * partie passee dans la vue du portail, transposee (la decoupe de la vue ne garde que ce qui est au-dela du plan
 * d'arrivee). Une entity qui vient d'arriver par un portail ({@link EntityHandover#arrivalPortal}) : le client la montre
 * avec un peu de retard, souvent encore derriere le plan d'arrivee ; sa partie sortie se dessine dans son monde, coupee
 * au plan d'arrivee, sa partie pas encore sortie devant le cadre d'entree, dans le monde d'ou elle vient, transposee et
 * coupee a son plan. Une entity d'un autre monde a cheval sur le plan d'arrivee d'un portail, le centre deja du cote de
 * la sortie, se dessine de meme devant son cadre d'entree.
 *
 * <p>Chaque decision porte sur la position affichee a l'image, interpolee entre deux ticks, et non sur celle du tick.
 *
 * <p>Une vue de portail n'a qu'un plan de decoupe : une entity coupee par un autre portail que celui de la vue s'y dessine
 * entiere.
 *
 * <p>Sous un pack de shaders, qui ignore la distance de decoupe, chaque moitie est rognee au plan sur le processeur
 * ({@link ClippedVertexConsumers}) ; dans le rendu complet d'une vue a travers un cadre, la projection a plan proche
 * oblique de la vue coupe deja au plan d'arrivee.
 */
public final class StraddlingEntityRenderer {

    /**
     * Marge du modele d'une entity au-dela de sa boite de collision, de tous cotes : vanilla tient la meme pour decider si
     * une entity est a l'ecran. Le modele touche le plan avant la boite : coupe des ce moment, il passe de l'autre cote au
     * lieu de rester entier dans son monde. Trop large, elle ne change rien a l'image : coupee sans depasser le plan,
     * l'entity reste entiere de son cote.
     */
    private static final double MODEL_MARGIN = 0.5;
    /**
     * Marge d'une entity vivante : bras ecartes et membres qui balancent, epee levee au bout du bras, tete d'un cheval,
     * nage et vol plane (modele allonge dans une boite reduite a un cube).
     */
    private static final double LIVING_MODEL_MARGIN = 1.0;
    /**
     * Au-dela de la distance de rendu des portails, plus cette marge (le plus grand cadre et le modele d'une entity), un
     * portail n'est pas dessine et rien de ce qu'il coupe ne se voit : il ne compte pas, ni ici ni pour vanilla.
     */
    private static final double REACH_MARGIN = 16.0;
    /** Rotation du passage de chaque paire d'orientations (source, arrivee), calculee une fois. */
    private static final Quaternionf[][] ROTATIONS = rotations();
    private static final Quaternionf IDENTITY = new Quaternionf();
    private static final TypeFilter<Entity, Entity> ANY_ENTITY = TypeFilter.instanceOf(Entity.class);
    private static final Predicate<Entity> NOT_A_PORTAL = entity -> !(entity instanceof BackroomsPortalEntity);
    private static final List<Entity> FOUND = new ArrayList<>();
    private static final MatrixStack MATRICES = new MatrixStack();
    private static final VertexConsumerProvider.Immediate BUFFERS = VertexConsumerProvider.immediate(new BufferAllocator(262_144));
    /** Les memes tampons, rognes au plan sur le processeur : pour les shaders d'un pack, qui ignorent la distance de decoupe. */
    private static final ClippedVertexConsumers CLIPPED = new ClippedVertexConsumers(BUFFERS);
    /** Section du moniteur de performance : les moities dessinees dans le monde du joueur, a chaque image. */
    private static final String PERF_PARTS = "client.portal.straddling";
    /** Pendant les dessins de ce rendu : vanilla ne doit pas refuser les entities qu'il dessine lui-meme. */
    private static boolean drawingParts;
    /** Position affichee de la derniere entity examinee ({@link #show}), et son ecart a celle du tick. */
    private static double shownX;
    private static double shownY;
    private static double shownZ;
    private static double shiftX;
    private static double shiftY;
    private static double shiftZ;

    private StraddlingEntityRenderer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    private static Quaternionf[][] rotations() {
        PortalFacing[] facings = PortalFacing.values();
        Quaternionf[][] table = new Quaternionf[facings.length][facings.length];
        for (PortalFacing source : facings) {
            for (PortalFacing destination : facings) {
                Vec3d x = PortalFacing.transformVector(source, destination, new Vec3d(1.0, 0.0, 0.0));
                Vec3d y = PortalFacing.transformVector(source, destination, new Vec3d(0.0, 1.0, 0.0));
                Vec3d z = PortalFacing.transformVector(source, destination, new Vec3d(0.0, 0.0, 1.0));
                Matrix3f rotation = new Matrix3f(
                        (float) x.x, (float) x.y, (float) x.z,
                        (float) y.x, (float) y.y, (float) y.z,
                        (float) z.x, (float) z.y, (float) z.z);
                table[source.ordinal()][destination.ordinal()] = new Quaternionf().setFromNormalized(rotation);
            }
        }
        return table;
    }

    /**
     * {@code true} si vanilla ne doit pas dessiner cette entity : ce rendu la dessine coupee. Dans le monde du joueur
     * quand les portails se dessinent, et dans le rendu complet d'une vue qui couvre l'ecran sous un pack de shaders ;
     * jamais pendant les dessins de ce rendu.
     */
    public static boolean skipsVanillaDraw(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        return !drawingParts && ModConfig.ENABLE_PORTAL_FEATURE
                && (PortalRenderState.canRenderPortals() || ShaderPackPortalRenderer.coversScreen())
                && drawnInParts(entity, cameraX, cameraY, cameraZ, tickDelta);
    }

    /**
     * {@code true} si ce rendu dessine l'entity en deux moities dans son monde : arrivant, ou coupee par une face a portee
     * de la camera qui dessine ce monde.
     */
    static boolean drawnInParts(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        return EntityHandover.arrivalPortal(entity) != null || faceCutting(entity, cameraX, cameraY, cameraZ, tickDelta) != null;
    }

    /**
     * Face de portail ouverte et visible de ce monde qui coupe l'entity : a cheval sur son cadre, son point de traversee
     * (les pieds, les yeux pour un cadre couche) devant elle et en face de l'ouverture. A cote, comme pour la traversee,
     * elle ne fait que longer le portail : rien d'elle n'est passe. Une entity qui vient d'arriver par un portail n'en a
     * pas : c'est son plan d'arrivee qui la coupe.
     *
     * <p>Le test porte sur la position affichee a cette image, pas sur celle du tick, qui a jusqu'a un tick d'avance :
     * le joueur bascule de monde quand ses pieds affiches passent le plan ; teste au tick, son centre serait deja derriere
     * la face pendant les dernieres images, dessine entier par vanilla, cache derriere la vue, et absent de la vue.
     */
    static @Nullable BackroomsPortalEntity cuttingFace(Entity entity, Vec3d cameraPos, float tickDelta) {
        return EntityHandover.arrivalPortal(entity) != null ? null : faceCutting(entity, cameraPos.x, cameraPos.y, cameraPos.z, tickDelta);
    }

    /**
     * Face a portee de la camera qui dessine le monde de l'entity, et qui la coupe a sa position affichee, qu'elle vienne
     * d'arriver ou non (voir {@link #cuttingFace}).
     */
    private static @Nullable BackroomsPortalEntity faceCutting(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(entity.getWorld());
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (withinReach(portal, cameraX, cameraY, cameraZ) && cutBy(portal, entity, tickDelta)) {
                return portal;
            }
        }
        return null;
    }

    /**
     * {@code true} si cette face, ouverte et visible, coupe l'entity a sa position affichee. Le point de traversee decide,
     * comme pour la traversee : sur un cadre au sol, une entity dont les pieds sont passes et pas encore les yeux reste
     * coupee, sa partie passee dessinee de l'autre cote.
     */
    private static boolean cutBy(BackroomsPortalEntity portal, Entity entity, float tickDelta) {
        if (!portal.teleportable() || !portal.visible()) {
            return false;
        }
        show(entity, tickDelta);
        return portal.crossingPointFaces(entity, shownX, shownY, shownZ)
                && portal.sourceFrameStraddles(entity.getBoundingBox(), modelMargin(entity), shiftX, shiftY, shiftZ);
    }

    /** {@code true} si le portail est assez pres de la camera pour etre dessine, ou presque (voir {@link #REACH_MARGIN}). */
    private static boolean withinReach(BackroomsPortalEntity portal, double cameraX, double cameraY, double cameraZ) {
        double reach = ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS + REACH_MARGIN;
        return portal.squaredDistanceTo(cameraX, cameraY, cameraZ) <= reach * reach;
    }

    /** {@code true} si un portail ouvert et visible de ce monde est a portee de la camera, ou si une entity vient d'arriver. */
    private static boolean anythingToDraw(World world, Vec3d cameraPos) {
        if (EntityHandover.arrivalCount() > 0) {
            return true;
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.teleportable() && portal.visible() && withinReach(portal, cameraPos.x, cameraPos.y, cameraPos.z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code true} si l'entity, a sa position affichee, sort de l'arrivee du portail sans y etre arrivee par lui : a
     * cheval sur son plan, son point de traversee (les yeux si l'arrivee est couchee) en face de l'ouverture et du cote de
     * la sortie, comme pour la traversee du retour qu'elle prend. Un bloc portail a deux faces dans le meme plan : celle
     * dont ce point est encore devant y entre, et c'est l'arrivee de l'autre face, dont elle ne sort pas, qui la verrait a
     * cheval ; sans ce cote, ses deux moities passeraient de l'autre cote.
     */
    private static boolean leavesArrival(BackroomsPortalEntity portal, Entity entity, float tickDelta) {
        return straddlesArrival(portal, entity, tickDelta) && portal.crossingPointFacesArrival(entity, shownX, shownY, shownZ);
    }

    /** {@code true} si l'entity, a sa position affichee, est a cheval sur le plan d'arrivee du portail, dans son cadre. */
    static boolean straddlesArrival(BackroomsPortalEntity portal, Entity entity, float tickDelta) {
        show(entity, tickDelta);
        return portal.arrivalFrameStraddles(entity.getBoundingBox(), modelMargin(entity), shiftX, shiftY, shiftZ);
    }

    /** Marge du modele de l'entity au-dela de sa boite de collision (voir {@link #MODEL_MARGIN}). */
    public static double modelMargin(Entity entity) {
        return entity instanceof LivingEntity ? LIVING_MODEL_MARGIN : MODEL_MARGIN;
    }

    /** Pose la position affichee de l'entity a cette image, comme vanilla l'interpole, et son ecart a celle du tick. */
    private static void show(Entity entity, float tickDelta) {
        shownX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        shownY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        shownZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        shiftX = shownX - entity.getX();
        shiftY = shownY - entity.getY();
        shiftZ = shownZ - entity.getZ();
    }

    /**
     * Dans le monde du joueur, apres les vues : les entities qu'un de ses portails coupe ou qui sortent d'une arrivee,
     * chacune la partie qui est de ce cote, et la partie encore derriere l'arrivee de celles qui sortent d'un portail de
     * ce monde.
     *
     * @param client client
     * @param camera camera du joueur
     * @param positionMatrix matrice de vue du joueur
     * @param projection projection du joueur
     * @param tickDelta fraction de tick de l'image
     */
    @SuppressWarnings("try")
    public static void renderMainWorldParts(MinecraftClient client, Camera camera, Matrix4f positionMatrix, Matrix4f projection, float tickDelta) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_PARTS)) {
            renderMainWorldPartsMeasured(client, camera, positionMatrix, projection, tickDelta);
        }
    }

    private static void renderMainWorldPartsMeasured(
            MinecraftClient client, Camera camera, Matrix4f positionMatrix, Matrix4f projection, float tickDelta) {
        // Sans portail a portee ni arrivee en cours, rien a dessiner : l'etat de rendu n'est pas touche.
        if (!anythingToDraw(client.world, camera.getPos())) {
            return;
        }
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(positionMatrix);
        RenderSystem.applyModelViewMatrix();
        try {
            renderWorldParts(client, client.world, camera.getPos(), positionMatrix, tickDelta);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, sorter);
        }
    }

    /**
     * Les entities d'un monde coupees par un de ses portails ou qui en sortent, et celles qui sortent de l'arrivee de ses
     * portails : dans le monde du joueur, ou dans une vue qui couvre l'ecran (troisieme personne, la camera passee de
     * l'autre cote). La matrice de vue est deja posee : chaque partie se dessine sous son propre plan de decoupe.
     */
    static void renderWorldParts(MinecraftClient client, World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta) {
        WorldRendererAccessor accessor = (WorldRendererAccessor) client.worldRenderer;
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        Entity hidden = client.gameRenderer.getCamera().isThirdPerson() ? null : client.getCameraEntity();
        drawingParts = true;
        dispatcher.setRenderShadows(false);
        try {
            List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
            for (int i = 0; i < portals.size(); i++) {
                BackroomsPortalEntity portal = portals.get(i);
                if (!portal.teleportable() || !portal.visible() || !withinReach(portal, cameraPos.x, cameraPos.y, cameraPos.z)) {
                    continue;
                }
                // Coupees par cette face : la partie devant elle.
                collect(world, portal.crossingBox());
                for (int j = 0; j < FOUND.size(); j++) {
                    Entity entity = FOUND.get(j);
                    if (entity != hidden && cuttingFace(entity, cameraPos, tickDelta) == portal) {
                        drawClipped(accessor, entity, cameraPos, IDENTITY, portal.facingSource().normal, portal.getPos(),
                                cameraPos, positionMatrix, tickDelta);
                    }
                }
                renderLeavingParts(client, accessor, world, portal, cameraPos, positionMatrix, tickDelta, hidden);
            }
            renderArrivedParts(accessor, world, cameraPos, positionMatrix, tickDelta, hidden);
        } finally {
            dispatcher.setRenderShadows(true);
            drawingParts = false;
            FOUND.clear();
            // Etat vanilla par defaut, que les couches suivantes supposent sans le poser : faces arriere eliminees.
            RenderSystem.enableCull();
        }
    }

    /**
     * Ce qui sort de l'arrivee d'un portail de ce monde, dans le monde d'arrivee : la partie encore derriere le plan
     * d'arrivee, ramenee devant le cadre et coupee a son plan.
     */
    private static void renderLeavingParts(
            MinecraftClient client,
            WorldRendererAccessor accessor,
            World world,
            BackroomsPortalEntity portal,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            float tickDelta,
            @Nullable Entity hidden) {
        World destination = RemoteWorldRegistry.worldOf(portal.destinationDim());
        if (destination == null) {
            return;
        }
        collect(destination, portal.arrivalCrossingBox());
        Vec3d imageCamera = null;
        Quaternionf rotation = ROTATIONS[portal.facingDestination().ordinal()][portal.facingSource().ordinal()];
        for (int i = 0; i < FOUND.size(); i++) {
            Entity entity = FOUND.get(i);
            BackroomsPortalEntity through = EntityHandover.arrivalPortal(entity);
            boolean leaving = through == null ? leavesArrival(portal, entity, tickDelta) : through == portal;
            if (entity == hidden || !leaving) {
                continue;
            }
            if (imageCamera == null) {
                imageCamera = portal.toDestination(cameraPos);
            }
            drawClipped(accessor, entity, imageCamera, rotation, portal.facingSource().normal, portal.getPos(), cameraPos, positionMatrix, tickDelta);
        }
    }

    /** Entities de ce monde arrivees par un portail : la partie sortie, coupee au plan d'arrivee. */
    private static void renderArrivedParts(
            WorldRendererAccessor accessor,
            World world,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            float tickDelta,
            @Nullable Entity hidden) {
        for (int i = 0; i < EntityHandover.arrivalCount(); i++) {
            Entity entity = EntityHandover.arrivedEntity(i);
            BackroomsPortalEntity portal = EntityHandover.arrivalPortal(entity);
            if (entity != hidden && entity.getWorld() == world && portal != null) {
                drawClipped(accessor, entity, cameraPos, IDENTITY, portal.facingDestination().normal, portal.destinationPos(),
                        cameraPos, positionMatrix, tickDelta);
            }
        }
    }

    /**
     * Sous un pack de shaders, pendant le rendu complet d'une vue : a travers un cadre, la partie passee de ce qui entre
     * par ce cadre (la projection a plan proche oblique de la vue coupe au plan d'arrivee) ; dans une vue qui couvre
     * l'ecran, les entities coupees de son monde, rognees a leur plan.
     *
     * @param client client, son renderer etant celui de la vue
     * @param portal portail dont c'est la vue, {@code null} si elle couvre l'ecran
     * @param frameCameraPos camera qui regarde le cadre, dans le monde du portail ; {@code null} si la vue couvre l'ecran
     * @param hidden entity de la camera du joueur a la premiere personne, jamais dessinee
     * @param world monde de la vue
     * @param cameraPos camera de la vue
     * @param positionMatrix matrice de vue de la camera de la vue
     * @param tickDelta fraction de tick de l'image
     */
    public static void renderPackViewParts(
            MinecraftClient client,
            @Nullable BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            @Nullable Entity hidden,
            World world,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            float tickDelta) {
        if (portal != null && frameCameraPos != null) {
            renderEnteringParts(client, (WorldRendererAccessor) client.worldRenderer, portal, frameCameraPos, hidden, tickDelta, BUFFERS);
            BUFFERS.draw();
        } else {
            renderWorldParts(client, world, cameraPos, positionMatrix, tickDelta);
        }
    }

    /**
     * Dans la vue d'un portail : la partie passee de ce qui est coupe par son cadre d'entree, transposee ; et ce qui est
     * arrive par le portail dont celui-ci est le retour, la partie pas encore sortie. La decoupe de la vue est active.
     *
     * @param client client
     * @param accessor renderer de la vue, pour dessiner une entity comme vanilla
     * @param portal portail dont c'est la vue
     * @param frameCameraPos camera qui regarde le cadre, dans le monde du portail
     * @param hidden entity de la camera du joueur a la premiere personne, jamais dessinee
     * @param tickDelta fraction de tick de l'image
     * @param consumers tampons de la vue
     */
    static void renderEnteringParts(
            MinecraftClient client,
            WorldRendererAccessor accessor,
            BackroomsPortalEntity portal,
            Vec3d frameCameraPos,
            @Nullable Entity hidden,
            float tickDelta,
            VertexConsumerProvider consumers) {
        World world = portal.getWorld();
        Quaternionf rotation = ROTATIONS[portal.facingSource().ordinal()][portal.facingDestination().ordinal()];
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);
        try {
            collect(world, portal.crossingBox());
            for (int i = 0; i < FOUND.size(); i++) {
                Entity entity = FOUND.get(i);
                boolean cut = EntityHandover.arrivalPortal(entity) == null && cutBy(portal, entity, tickDelta);
                if (entity != hidden && (cut || arrivedThroughReturnOf(entity, portal))) {
                    renderTransposed(accessor, entity, frameCameraPos, rotation, tickDelta, consumers);
                }
            }
        } finally {
            dispatcher.setRenderShadows(true);
            FOUND.clear();
        }
    }

    /** {@code true} si l'entity est arrivee par le portail dont celui-ci est le retour : meme plan, sens inverse. */
    private static boolean arrivedThroughReturnOf(Entity entity, BackroomsPortalEntity portal) {
        BackroomsPortalEntity through = EntityHandover.arrivalPortal(entity);
        return through != null && through.facingDestination() == portal.facingSource() && portal.centeredAt(through.destinationPos());
    }

    /** Entities d'un monde dans une boite, hors portails, dans {@link #FOUND}. */
    private static void collect(World world, Box box) {
        FOUND.clear();
        world.collectEntitiesByType(ANY_ENTITY, box, NOT_A_PORTAL, FOUND);
    }

    /**
     * Dessine une entity coupee a un plan, du cote ou pointe sa normale, sous ses propres tampons : la decoupe ne vaut que
     * pour elle.
     */
    private static void drawClipped(
            WorldRendererAccessor accessor,
            Entity entity,
            Vec3d imageCamera,
            Quaternionf rotation,
            Vec3d planeNormal,
            Vec3d planePoint,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            float tickDelta) {
        if (ShaderPackState.inUse()) {
            // Les shaders du pack ignorent la distance de decoupe : les sommets sont rognes au plan avant d'etre ecrits.
            CLIPPED.setPlane(planeNormal, planePoint, cameraPos);
            renderTransposed(accessor, entity, imageCamera, rotation, tickDelta, CLIPPED);
            BUFFERS.draw();
            return;
        }
        PortalClipping.push(planeNormal, planePoint, cameraPos, positionMatrix);
        try {
            renderTransposed(accessor, entity, imageCamera, rotation, tickDelta, BUFFERS);
            BUFFERS.draw();
        } finally {
            PortalClipping.pop();
        }
    }

    /**
     * Dessine une entity depuis l'image de la camera de l'autre cote, tournee comme le passage : ses sommets arrivent la
     * ou le portail les transpose. Sans rotation ni image, elle se dessine a sa place.
     */
    private static void renderTransposed(
            WorldRendererAccessor accessor,
            Entity entity,
            Vec3d imageCamera,
            Quaternionf rotation,
            float tickDelta,
            VertexConsumerProvider consumers) {
        MATRICES.push();
        try {
            MATRICES.multiply(rotation);
            accessor.backrooms$renderEntity(entity, imageCamera.x, imageCamera.y, imageCamera.z, tickDelta, MATRICES, consumers);
        } finally {
            MATRICES.pop();
        }
    }
}
