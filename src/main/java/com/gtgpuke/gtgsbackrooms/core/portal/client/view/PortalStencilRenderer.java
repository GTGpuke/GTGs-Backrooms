package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.EntityHandover;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.FrozenViews;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.PortalSightReporter;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteViewRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.StraddlingEntityRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalRelevance;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookFrame;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookPost;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.mixin.client.BackgroundRendererAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.BuiltChunkStorageAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.CameraAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.FrustumAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.MinecraftClientAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Rend l'autre cote de chaque portail visible, puis un niveau de portails imbriques, au milieu du rendu du monde : apres
 * ses blocs opaques et ses entities, avant ce qui est translucide. L'eau ou le verre du monde du joueur devant un
 * cadre se dessinent ensuite par-dessus la vue, comme par-dessus n'importe quel decor.
 *
 * <p>Les bits bas du stencil ({@code StencilLookMask.LEVEL_MASK}, {@code 0x03}) portent le niveau d'imbrication : 0
 * pour le monde du joueur, 1 dans la vue d'un portail, 2 dans celle d'un portail vu a travers lui ; les bits
 * {@code 0x1C} portent le look de la dimension montree ({@code StencilLookMask.LOOK_MASK}), pour le post masque. Les portails se dessinent un par un, du plus proche
 * au plus lointain. Pour chacun : sa zone de vue (voir {@link FrameAreaRenderer}) passe le stencil du niveau courant au
 * suivant la ou elle n'est cachee par rien, sa profondeur est poussee au plan lointain, le fond prend la couleur de
 * brouillard de la vue, puis la vue (ciel, blocs, entities, block entities, particules, nuages, pluie et neige) est dessinee depuis
 * la camera transposee avec la projection du joueur, limitee a la zone par le stencil et au plan du portail d'arrivee
 * par la decoupe des shaders ({@link PortalClipping}). Enfin la zone reprend sa vraie profondeur et son stencil revient
 * au niveau courant : le portail suivant, s'il est derriere, est cache par celui-ci, et deux portails l'un derriere
 * l'autre ne se melangent pas. Un portail est a sens unique : vu de dos, il n'est pas dessine.
 *
 * <p>A la troisieme personne, quand la camera est passee derriere un portail que le joueur vient de franchir
 * ({@link ThirdPersonPortal}), l'image entiere est la vue de l'autre cote, depuis l'image de la camera : tout l'ecran
 * passe au niveau 1, la vue s'y dessine avec ses portails imbriques, et le reste du rendu du monde du joueur est refuse
 * par le stencil jusqu'a la fin de l'image.
 *
 * <p>Sous un pack de shaders ({@link ShaderPackState}), rien de tout cela au milieu du rendu : les vues se dessinent apres
 * l'image du monde, chacune par le rendu complet de son renderer ({@link ShaderPackPortalRenderer}).
 */
public final class PortalStencilRenderer {

    /** Frustums de culling des portails visibles (depuis le joueur, puis depuis la camera d'une vue), reutilises d'une image a l'autre. */
    private static final Frustum CULLING_FRUSTUM = new Frustum(new Matrix4f(), new Matrix4f());
    private static final Frustum NESTED_FRUSTUM = new Frustum(new Matrix4f(), new Matrix4f());
    /** Volume visible a travers le cadre d'une vue : seuls les portails qui le touchent sont imbriques. */
    private static final ViewCone NESTED_CONE = new ViewCone();

    /** Garde-fou de performance : au-dela, les portails les plus lointains de l'image ne sont pas dessines. */
    private static final int MAX_PORTALS_PER_FRAME = 32;
    /** Niveau d'imbrication du monde du joueur ; une vue est au niveau suivant, une vue imbriquee encore un au-dela. */
    private static final int WORLD_LEVEL = 0;
    private static final int FULL_MASK = 0xFF;
    /** Distance de la camera a la troisieme personne, comme vanilla, avant l'echelle du joueur. */
    private static final float THIRD_PERSON_DISTANCE = 4.0f;
    /** Ecart des rayons qui eloignent la camera des blocs, comme vanilla : huit coins d'un petit cube autour d'elle. */
    private static final double CAMERA_CLEARANCE = 0.1;
    private static final int CAMERA_CORNERS = 8;
    private static final long DEBUG_LOG_INTERVAL_MILLIS = 1000L;
    /** Section du moniteur de performance : tout le rendu des portails d'une image. */
    private static final String PERF_RENDER = "client.portal.render";
    /** Section du moniteur de performance : la preuve qu'un cadre est cache derriere des blocs ({@link FrameOcclusion}). */
    private static final String PERF_OCCLUSION = "client.portal.occlusion";
    private static final String PERF_FROZEN_CAPTURE = "client.portal.frozen_capture";
    private static final String PERF_FROZEN_DRAW = "client.portal.frozen_draw";
    /** Cases opaques du monde d'une vue, reutilisees : thread de rendu seulement. */
    private static final ShownOccluders OCCLUDERS = new ShownOccluders();
    /** Masque de l'origine d'une section de seize blocs. */
    private static final int SECTION_MASK = ~15;

    private static final List<BackroomsPortalEntity> VISIBLE = new ArrayList<>();
    /**
     * Cadres proches hors du champ (dans le dos, sous les pieds, sur le cote) vers une autre dimension : le joueur peut les
     * passer sans les regarder. Leur vue ne se dessine pas, mais son renderer s'entretient et construit d'avance ce que le
     * joueur verra en sortant ({@link RemoteViewRenderer#PREBUILD_DISTANCE}). Rendu vanilla seulement : sous un pack de
     * shaders, la liste est videe aussitot remplie.
     */
    private static final List<BackroomsPortalEntity> NEAR_UNSEEN = new ArrayList<>();
    /** Portails au-dela de la distance de rendu, jusqu'a {@link ViewDetail#FROZEN_DISTANCE} : leur derniere image figee. */
    private static final List<BackroomsPortalEntity> FAR_FROZEN = new ArrayList<>();
    /** Rectangle d'ecran d'un cadre a capturer, en pixels du framebuffer du jeu. */
    private static final int[] CAPTURE_RECT = new int[4];
    /** Une image figee au plus est prise par image. */
    private static boolean capturedThisFrame;
    /**
     * Distance au carre au-dela de laquelle une vue allegee prend son image figee d'avance : huit blocs avant la distance
     * de rendu des vues, au-dela de laquelle seule cette image reste.
     */
    private static final double CAPTURE_EDGE_SQUARED = MathHelper.square(Math.max(0.0, ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS - 8.0));
    /** Pendant une capture, les ciseaux passent des pixels de l'ecran a ceux du framebuffer de capture : decalage, puis echelle. */
    private static int scissorOffsetX;
    private static int scissorOffsetY;
    private static int scissorScale = 1;
    /** Portails imbriques visibles dans la vue en cours, reutilises d'une image a l'autre. */
    private static final List<BackroomsPortalEntity> NESTED = new ArrayList<>();
    private static final Matrix4f REMOTE_POSITION = new Matrix4f();
    private static final Matrix4f NESTED_POSITION = new Matrix4f();
    /** Projection et vue d'un cadre, et un sommet transforme, pour son rectangle d'ecran. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH_CLIP = new Vector4f();
    /** Marge, en pixels, autour du rectangle d'ecran d'un cadre : les pixels de son bord en font partie. */
    private static final int SCISSOR_MARGIN = 2;
    /**
     * Rectangles du ciseau poses, en pixels du framebuffer (x et y du coin bas gauche, puis x et y du coin haut droit) : la
     * vue, puis la vue imbriquee, chacun coupe avec le precedent.
     */
    private static final int[][] SCISSORS = {new int[4], new int[4]};
    /** Profondeur du ciseau pose : zero hors d'une vue, un dans une vue, deux dans une vue imbriquee. */
    private static int scissorDepth;
    /** Brouillard du monde du joueur (couleur, portee, forme), rendu apres les vues, qui posent chacune le leur. */
    private static final float[] MAIN_FOG = new float[4];
    private static float mainFogStart;
    private static float mainFogEnd;
    private static FogShape mainFogShape = FogShape.SPHERE;
    private static long nextDebugLogMillis;
    /** La vue couvre l'ecran : le stencil refuse la suite du rendu du monde du joueur jusqu'a la fin de l'image. */
    private static boolean coveringScreen;
    /** Oeil et camera voulue a la troisieme personne, calcules en cherchant le portail franchi et repris pour le dessiner. */
    private static Vec3d behindEye = Vec3d.ZERO;
    private static Vec3d behindWanted = Vec3d.ZERO;

    private PortalStencilRenderer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Point d'entree appele par le mixin de {@code WorldRenderer.render}, avant la couche translucide. Sous un pack de
     * shaders, seulement les moities d'entities a cheval : les vues se dessinent apres l'image du monde
     * ({@link #renderAfterWorld}).
     *
     * @param camera camera du joueur
     * @param positionMatrix matrice de vue du rendu monde
     * @param projectionMatrix matrice de projection du rendu monde
     * @param lightmap gestionnaire de lightmap du rendu : il porte celle de chaque monde vu le temps de sa vue
     * @param tickDelta fraction de tick
     */
    @SuppressWarnings("try")
    public static void renderAllVisiblePortals(
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        if (ShaderPackState.inUse()) {
            // Les vues se dessinent apres l'image ; les moities d'entities a cheval, elles, ici, dans le pipeline du pack.
            MinecraftClient client = MinecraftClient.getInstance();
            if (ready(client)) {
                StraddlingEntityRenderer.renderMainWorldParts(client, camera, positionMatrix, projectionMatrix, tickDelta);
            }
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_RENDER)) {
            renderPortals(camera, positionMatrix, projectionMatrix, lightmap, tickDelta);
        }
    }

    /**
     * Point d'entree appele par le mixin en fin de {@code WorldRenderer.render} : sous un pack de shaders, les vues se
     * dessinent ici, apres l'image du monde du joueur que le mod de shaders vient de composer
     * ({@link ShaderPackPortalRenderer}). Les masques d'ecriture sont d'abord rendus, a chaque image : la passe finale du
     * pack coupe l'ecriture de la profondeur. Le mixin ne l'appelle pas pendant le rendu complet d'une vue, qui repasse par la.
     */
    @SuppressWarnings("try")
    public static void renderAfterWorld(
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix) {
        if (!ShaderPackState.inUse()) {
            return;
        }
        // La passe finale du pack laisse l'ecriture de la profondeur coupee. Une vue dessinee la rendait ; sans vue, rien :
        // l'effacement de l'image suivante ne remettait plus la profondeur au plan lointain, et le pack prenait des pans
        // entiers du monde pour du ciel (noirs dans une dimension sans ciel).
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        // Dimension a look sous le pack : son look se pose sur l'image du pack, avant les vues, qui montrent d'autres dimensions.
        LookPost.renderUnderShaderPack(MinecraftClient.getInstance());
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_RENDER)) {
            renderPortalsAfterWorld(tickCounter, camera, lightmap, positionMatrix, projectionMatrix);
        } finally {
            HandMask.clear(MinecraftClient.getInstance());
        }
    }

    private static void renderPortals(
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        PortalSightReporter.beginFrame();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ready(client)) {
            return;
        }
        if (ClientPortalIndex.allPortals(client.world).isEmpty()) {
            // Aucun portail dans ce monde, mais une entity arrivee par un portail aller simple s'y dessine encore en
            // parties : vanilla ne la dessine pas.
            if (EntityHandover.arrivalCount() > 0) {
                StraddlingEntityRenderer.renderMainWorldParts(client, camera, positionMatrix, projectionMatrix, tickDelta);
            }
            return;
        }
        BackroomsPortalEntity behind = collect(client, camera, positionMatrix, projectionMatrix, tickDelta);
        if (behind == null && VISIBLE.isEmpty() && FAR_FROZEN.isEmpty() && NEAR_UNSEEN.isEmpty()) {
            // Aucun cadre a l'ecran, mais une entity peut etre coupee par un portail vu de cote : vanilla ne la dessine pas.
            StraddlingEntityRenderer.renderMainWorldParts(client, camera, positionMatrix, projectionMatrix, tickDelta);
            return;
        }
        PortalRenderState.beginFrame();
        // Vanilla a empile sa matrice de vue pour ses entities : les zones et les vues portent chacune la leur.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            if (behind != null) {
                renderFromBehind(client, behind, camera, projectionMatrix, lightmap, tickDelta);
            } else {
                renderThroughStencil(client, camera, positionMatrix, projectionMatrix, lightmap, tickDelta);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            PortalRenderState.endFrame();
            VISIBLE.clear();
            NEAR_UNSEEN.clear();
        }
    }

    /**
     * Sous un pack de shaders : les memes portails, dessines par {@link ShaderPackPortalRenderer} apres l'image du monde.
     * Les moities d'entities a cheval se sont dessinees au milieu du rendu du monde ({@link #renderAllVisiblePortals}).
     */
    private static void renderPortalsAfterWorld(
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix) {
        PortalSightReporter.beginFrame();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ready(client) || ClientPortalIndex.allPortals(client.world).isEmpty()) {
            return;
        }
        BackroomsPortalEntity behind = collect(client, camera, positionMatrix, projectionMatrix, tickCounter.getTickDelta(false));
        // Sous un pack, pas d'image figee : les portails lointains ne se dessinent pas, et ne restent pas retenus. Pas de
        // construction d'avance non plus : le pack dessine chaque vue en entier.
        FAR_FROZEN.clear();
        NEAR_UNSEEN.clear();
        if (behind == null && VISIBLE.isEmpty()) {
            return;
        }
        PortalRenderState.beginFrame();
        saveMainFog();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            if (behind != null) {
                RemoteWorld view = RemoteWorldRegistry.viewOf(behind.destinationDim());
                ShaderPackPortalRenderer.renderCovering(client, behind, view, behindCameraPos(view, behind, camera.getFocusedEntity()),
                        tickCounter, camera, lightmap, positionMatrix, projectionMatrix);
            } else {
                ShaderPackPortalRenderer.render(client, VISIBLE, tickCounter, camera, lightmap, positionMatrix, projectionMatrix);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            restoreMainState(client);
            PortalRenderState.endFrame();
            VISIBLE.clear();
        }
    }

    /** {@code true} si les portails peuvent se dessiner dans cette image ; les mondes distants sont alors alignes sur le monde du joueur. */
    private static boolean ready(MinecraftClient client) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE || !PortalRenderState.canRenderPortals() || client.world == null || client.player == null) {
            return false;
        }
        RemoteWorldRegistry.syncWithMainWorld(client.world);
        return true;
    }

    /**
     * Portail derriere lequel la camera est passee a la troisieme personne, rapporte comme regarde a travers ; sinon
     * {@code null}, et les portails visibles, rapportes comme dessines, sont dans {@link #VISIBLE} (peut-etre vide).
     */
    private static @Nullable BackroomsPortalEntity collect(
            MinecraftClient client, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta) {
        // Aucun portail lointain d'une image precedente ne reste retenu, quel que soit le chemin de cette image.
        FAR_FROZEN.clear();
        BackroomsPortalEntity behind = camera.isThirdPerson() ? portalBehindCamera(client.world, camera, tickDelta) : null;
        if (behind != null) {
            PortalSightReporter.noteLookedThrough(behind);
        } else {
            collectVisiblePortals(client.world, camera, positionMatrix, projectionMatrix);
            PortalSightReporter.noteDrawn(VISIBLE);
        }
        return behind;
    }

    /** Fin du rendu du monde du joueur : si la vue couvrait l'ecran, le stencil laisse de nouveau tout passer (main, interface). */
    public static void endWorldRender() {
        if (coveringScreen) {
            coveringScreen = false;
            GL11.glStencilFunc(GL11.GL_ALWAYS, WORLD_LEVEL, FULL_MASK);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    /** Oeil du joueur suivi par la camera, comme {@code Camera.update} le place avant de reculer la camera. */
    private static Vec3d eyePosition(Camera camera, Entity focused, float tickDelta) {
        CameraAccessor accessor = (CameraAccessor) camera;
        return new Vec3d(
                MathHelper.lerp(tickDelta, focused.prevX, focused.getX()),
                MathHelper.lerp(tickDelta, focused.prevY, focused.getY())
                        + MathHelper.lerp(tickDelta, accessor.backrooms$getLastCameraY(), accessor.backrooms$getCameraY()),
                MathHelper.lerp(tickDelta, focused.prevZ, focused.getZ()));
    }

    /** Portail derriere lequel est passee la camera a la troisieme personne, si elle a pu y aller, ou {@code null}. */
    private static @Nullable BackroomsPortalEntity portalBehindCamera(ClientWorld world, Camera camera, float tickDelta) {
        Entity focused = camera.getFocusedEntity();
        if (focused == null) {
            return null;
        }
        Vec3d eye = eyePosition(camera, focused, tickDelta);
        Vec3d wanted = wantedCameraPos(camera, focused, eye);
        BackroomsPortalEntity portal = ThirdPersonPortal.crossedBy(ClientPortalIndex.portals(world), eye, wanted);
        if (portal == null || !portal.visible() || RemoteWorldRegistry.viewOf(portal.destinationDim()) == null) {
            return null;
        }
        // Un bloc de ce cote, entre l'oeil et le plan, retient la camera : elle ne passe pas le portail. Un bloc que le portail
        // cache derriere son plan (cadre pose contre un mur) n'en est pas un : vanilla s'y arrete, au plan meme ou un peu
        // avant par ses rayons decales, mais la camera passe le cadre comme le corps.
        Vec3d crossing = eye.lerp(wanted, ThirdPersonPortal.crossingFraction(portal, eye, wanted));
        if (blockedBefore(world, focused, eye, crossing, portal)) {
            return null;
        }
        behindEye = eye;
        behindWanted = wanted;
        return portal;
    }

    /**
     * {@code true} si un bloc de ce cote arrete le segment de l'oeil au plan du portail avant le plan, a plus de
     * {@value #CAMERA_CLEARANCE} bloc de lui ; les blocs que ce portail cache ne comptent pas. Un rayon par image, a la
     * troisieme personne quand la camera passe un cadre seulement : il alloue comme les huit que vanilla lance pour sa camera.
     */
    private static boolean blockedBefore(ClientWorld world, Entity focused, Vec3d eye, Vec3d crossing, BackroomsPortalEntity portal) {
        BlockHitResult hit = world.raycast(new RaycastContext(eye, crossing, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focused));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        return !PortalCollision.hides(portal, pos.getX(), pos.getY(), pos.getZ())
                && hit.getPos().distanceTo(eye) + CAMERA_CLEARANCE < crossing.distanceTo(eye);
    }

    /** Camera a sa pleine distance derriere l'oeil, avant que les blocs ne la rapprochent. */
    private static Vec3d wantedCameraPos(Camera camera, Entity focused, Vec3d eye) {
        float distance = THIRD_PERSON_DISTANCE * (focused instanceof LivingEntity living ? living.getScale() : 1.0f);
        Vector3f forward = camera.getHorizontalPlane();
        return eye.add(-forward.x() * distance, -forward.y() * distance, -forward.z() * distance);
    }

    /**
     * Camera de la vue derriere le portail franchi a la troisieme personne : elle passe le portail au point ou son
     * segment le traverse et recule de la distance qui lui reste, arretee par les blocs de l'autre monde comme vanilla
     * l'arrete par ceux du sien.
     */
    private static Vec3d behindCameraPos(RemoteWorld view, BackroomsPortalEntity portal, Entity focused) {
        double fraction = ThirdPersonPortal.crossingFraction(portal, behindEye, behindWanted);
        // Le recul part un peu devant le plan d'arrivee : ses rayons decales n'y commencent pas dans un bloc colle au cadre.
        Vec3d start = portal.toDestination(behindEye.lerp(behindWanted, fraction))
                .add(portal.facingDestination().normal.multiply(2.0 * CAMERA_CLEARANCE));
        Vec3d end = portal.toDestination(behindWanted);
        return clipToSpace(view.world(), focused, start, end);
    }

    /** L'image entiere vue de l'autre cote, depuis la camera passee derriere le portail ({@link #behindCameraPos}). */
    private static void renderFromBehind(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            Camera camera,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        RemoteWorld view = RemoteWorldRegistry.viewOf(portal.destinationDim());
        Vec3d remoteCameraPos = behindCameraPos(view, portal, camera.getFocusedEntity());
        Camera remoteCamera = PortalRenderState.remoteCamera();
        Vec2f remoteLook = aimCamera(remoteCamera, remoteCameraPos, portal, camera, REMOTE_POSITION);

        saveMainFog();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        // Tout l'ecran est la vue : niveau 1 et look de la dimension vue, tous les bits ecrits par l'effacement.
        GL11.glStencilMask(FULL_MASK);
        GL11.glClearStencil(StencilLookMask.value(WORLD_LEVEL + 1, lookOf(portal)));
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        GL11.glClearStencil(WORLD_LEVEL);
        GL11.glStencilFunc(GL11.GL_EQUAL, WORLD_LEVEL + 1, StencilLookMask.LEVEL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        coveringScreen = true;
        try {
            if (renderView(client, view, camera, remoteCamera, remoteCameraPos, REMOTE_POSITION, projectionMatrix, lightmap, tickDelta,
                    portal, null, null, false)) {
                renderNestedPortals(client, null, view, camera, remoteCameraPos, remoteLook, projectionMatrix, lightmap, tickDelta, true);
            }
        } finally {
            // Le reste du rendu du monde du joueur (translucide, particules, nuages, meteo) est derriere la vue : refuse partout.
            GL11.glStencilFunc(GL11.GL_EQUAL, WORLD_LEVEL, StencilLookMask.LEVEL_MASK);
            restoreMainState(client);
        }
    }

    /**
     * Etat de rendu du monde du joueur, rendu apres les vues sur tous les chemins, exception comprise : la suite du rendu
     * vanilla (couche translucide, particules, nuages, meteo) reprend ses tests, son brouillard, sa couleur de fond et
     * l'eclairage de ses entities, que chaque vue avait remplaces par les siens.
     */
    private static void restoreMainState(MinecraftClient client) {
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        restoreMainFog();
        RenderSystem.clearColor(MAIN_FOG[0], MAIN_FOG[1], MAIN_FOG[2], 0.0f);
        applyEntityLighting(client.world);
    }

    /** Garde le brouillard du monde du joueur avant que les vues ne posent le leur. */
    private static void saveMainFog() {
        System.arraycopy(RenderSystem.getShaderFogColor(), 0, MAIN_FOG, 0, MAIN_FOG.length);
        mainFogStart = RenderSystem.getShaderFogStart();
        mainFogEnd = RenderSystem.getShaderFogEnd();
        mainFogShape = RenderSystem.getShaderFogShape();
    }

    /**
     * Rend au reste du rendu vanilla le brouillard du monde du joueur, aux shaders comme a {@code BackgroundRenderer} : les
     * nuages et la meteo y relisent la couleur, qui serait sinon celle du dernier monde vu (le noir du Level 0 sur les
     * nuages de l'Overworld).
     */
    private static void restoreMainFog() {
        RenderSystem.setShaderFogStart(mainFogStart);
        RenderSystem.setShaderFogEnd(mainFogEnd);
        RenderSystem.setShaderFogShape(mainFogShape);
        RenderSystem.setShaderFogColor(MAIN_FOG[0], MAIN_FOG[1], MAIN_FOG[2], MAIN_FOG[3]);
        BackgroundRendererAccessor.backrooms$setRed(MAIN_FOG[0]);
        BackgroundRendererAccessor.backrooms$setGreen(MAIN_FOG[1]);
        BackgroundRendererAccessor.backrooms$setBlue(MAIN_FOG[2]);
    }

    /** Recul de la camera dans l'autre monde, arrete par ses blocs : les huit rayons de {@code Camera.clipToSpace}. */
    private static Vec3d clipToSpace(ClientWorld world, Entity focused, Vec3d start, Vec3d end) {
        double length = start.distanceTo(end);
        double fraction = 1.0;
        for (int corner = 0; corner < CAMERA_CORNERS; corner++) {
            double dx = ((corner & 1) * 2 - 1) * CAMERA_CLEARANCE;
            double dy = ((corner >> 1 & 1) * 2 - 1) * CAMERA_CLEARANCE;
            double dz = ((corner >> 2 & 1) * 2 - 1) * CAMERA_CLEARANCE;
            Vec3d from = start.add(dx, dy, dz);
            HitResult hit = world.raycast(new RaycastContext(from, end.add(dx, dy, dz),
                    RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focused));
            if (hit.getType() != HitResult.Type.MISS && length > 0.0) {
                fraction = Math.min(fraction, hit.getPos().distanceTo(from) / length);
            }
        }
        return start.lerp(end, fraction);
    }

    private static void collectVisiblePortals(ClientWorld world, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        Vec3d cameraPos = camera.getPos();
        double renderDistance = Math.max(1, ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS);
        boolean frustumReady = false;
        NEAR_UNSEEN.clear();
        // Sorties fugaces cachees au joueur comprises : dans le champ, il peut les voir de nouveau.
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (!portal.visible() || portal.signedDistance(cameraPos) <= 0.0) {
                continue;
            }
            boolean near = !portal.hiddenFromPlayer() && !portal.destinationDim().equals(world.getRegistryKey())
                    && portal.squaredDistanceToFrame(cameraPos.x, cameraPos.y, cameraPos.z)
                    <= RemoteViewRenderer.PREBUILD_DISTANCE * RemoteViewRenderer.PREBUILD_DISTANCE;
            if (!PortalRelevance.isRelevant(cameraPos, camera.getPitch(), camera.getYaw(), portal.getPos(), renderDistance)) {
                // Au-dela de la distance de rendu, un portail garde l'image figee prise de plus pres, sans rien en reprendre.
                if (FrozenViews.has(portal) && !portal.hiddenFromPlayer()
                        && PortalRelevance.isRelevant(cameraPos, camera.getPitch(), camera.getYaw(), portal.getPos(), ViewDetail.FROZEN_DISTANCE)) {
                    FAR_FROZEN.add(portal);
                }
                if (near) {
                    NEAR_UNSEEN.add(portal);
                }
                continue;
            }
            if (!frustumReady) {
                ((FrustumAccessor) CULLING_FRUSTUM).backrooms$init(positionMatrix, projectionMatrix);
                CULLING_FRUSTUM.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
                frustumReady = true;
            }
            if (CULLING_FRUSTUM.isVisible(portal.detectionBox())
                    && (!portal.hiddenFromPlayer() || PortalSightReporter.revealed(world, cameraPos, portal))) {
                VISIBLE.add(portal);
            } else if (near) {
                NEAR_UNSEEN.add(portal);
            }
        }
        sortByDistance(cameraPos);
        while (VISIBLE.size() > MAX_PORTALS_PER_FRAME) {
            VISIBLE.remove(VISIBLE.size() - 1);
        }
    }

    /**
     * Du plus proche au plus lointain : la premiere vue d'un renderer dans l'image prepare son terrain, et c'est le
     * portail le plus proche, celui qu'on va traverser, qui doit le faire. Tri par insertion, sans allocation.
     */
    private static void sortByDistance(Vec3d cameraPos) {
        for (int i = 1; i < VISIBLE.size(); i++) {
            BackroomsPortalEntity portal = VISIBLE.get(i);
            double distance = portal.squaredDistanceTo(cameraPos);
            int j = i - 1;
            while (j >= 0 && VISIBLE.get(j).squaredDistanceTo(cameraPos) > distance) {
                VISIBLE.set(j + 1, VISIBLE.get(j));
                j--;
            }
            VISIBLE.set(j + 1, portal);
        }
    }

    private static void renderThroughStencil(
            MinecraftClient client,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        saveMainFog();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        // Niveau 0 et look du monde du joueur partout : l'effacement n'ecrit que les bits du masque d'ecriture, tous ici.
        GL11.glStencilMask(FULL_MASK);
        GL11.glClearStencil(StencilLookMask.value(WORLD_LEVEL, LookProfiles.of(client.world.getRegistryKey()).id()));
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        GL11.glClearStencil(WORLD_LEVEL);
        capturedThisFrame = false;
        try {
            Vec3d cameraPos = camera.getPos();
            for (int i = 0; i < VISIBLE.size(); i++) {
                BackroomsPortalEntity portal = VISIBLE.get(i);
                boolean draw = !hiddenBehindBlocks(client.world, client.worldRenderer, cameraPos, portal);
                renderPortal(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta, draw);
            }
            // Apres les cadres visibles : une vue dessinee de la meme dimension place le terrain de son renderer, pas eux.
            for (int i = 0; i < NEAR_UNSEEN.size(); i++) {
                renderPortal(client, NEAR_UNSEEN.get(i), camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta, false);
            }
            drawFarFrozen(frustumFor(camera, positionMatrix, projectionMatrix), cameraPos, positionMatrix);
            FrozenViews.sweep();
            // Ce qui traverse un portail : chaque moitie de ce cote, dans le brouillard et l'eclairage du monde du joueur.
            restoreMainFog();
            applyEntityLighting(client.world);
            StraddlingEntityRenderer.renderMainWorldParts(client, camera, positionMatrix, projectionMatrix, tickDelta);
            logDebugState(client);
        } finally {
            GL11.glStencilFunc(GL11.GL_ALWAYS, WORLD_LEVEL, FULL_MASK);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            restoreMainState(client);
        }
    }

    /** Eclairage des entities propre a une dimension, comme vanilla le pose avant de les dessiner. */
    public static void applyEntityLighting(ClientWorld world) {
        if (world.getDimensionEffects().isDarkened()) {
            DiffuseLighting.enableForLevel();
        } else {
            DiffuseLighting.disableForLevel();
        }
    }

    /**
     * Un portail du monde du joueur : zone marquee au niveau 1, vue, portails imbriques, puis zone rendue au niveau 0. Cache
     * derriere des blocs ({@code draw} faux), il ne marque ni ne dessine rien, et sa vue est seulement entretenue.
     */
    private static void renderPortal(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta,
            boolean draw) {
        if (!draw) {
            renderPortalContent(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta, false, true);
            return;
        }
        ViewDetail.Tier tier = detailTier(portal, cameraPos, projectionMatrix);
        if (tier == ViewDetail.Tier.FROZEN && renderFrozen(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta)) {
            return;
        }
        if (tier != ViewDetail.Tier.FULL && portal.squaredDistanceTo(cameraPos) >= CAPTURE_EDGE_SQUARED) {
            // Pres du bord de la distance de rendu, l'image figee se prend d'avance : le portail qui en sort la garde.
            captureIfStale(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta);
        }
        markArea(portal, cameraPos, positionMatrix, WORLD_LEVEL);
        try {
            renderPortalContent(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta, true,
                    tier != ViewDetail.Tier.FULL);
        } finally {
            restoreArea(portal, cameraPos, positionMatrix, WORLD_LEVEL);
        }
    }

    /**
     * Palier fige : l'image figee de la vue collee sur le cadre ({@link FrozenViews}), reprise d'abord s'il le faut
     * ({@link #captureIfStale}).
     *
     * @return {@code false} sans image a montrer : la vue se dessine comme une vue allegee
     */
    @SuppressWarnings("try")
    private static boolean renderFrozen(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        captureIfStale(client, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta);
        if (!FrozenViews.has(portal)) {
            return false;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_FROZEN_DRAW)) {
            drawFrozen(portal, cameraPos, positionMatrix);
        }
        return true;
    }

    /**
     * Prend l'image figee de la vue si elle manque ou a vieilli ({@link FrozenViews#wantsCapture}), que le monde de la vue
     * a l'arrivee et que le cadre est entierement a l'ecran : une capture au plus par image.
     */
    private static void captureIfStale(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        RemoteWorld view = RemoteWorldRegistry.viewOf(portal.destinationDim());
        if (!capturedThisFrame && view != null && hasChunkAt(view, portal.destinationPos())) {
            int chunks = view.world().getChunkManager().getLoadedChunkCount();
            Framebuffer screen = client.getFramebuffer();
            if (FrozenViews.wantsCapture(portal, cameraPos, chunks)
                    && screenRect(portal, cameraPos, positionMatrix, projectionMatrix, screen.textureWidth, screen.textureHeight, CAPTURE_RECT)
                    && CAPTURE_RECT[0] > 0 && CAPTURE_RECT[1] > 0 && CAPTURE_RECT[2] < screen.textureWidth && CAPTURE_RECT[3] < screen.textureHeight) {
                capturedThisFrame = true;
                captureView(client, view, portal, camera, cameraPos, positionMatrix, projectionMatrix, lightmap, tickDelta, chunks);
            }
        }
    }

    /**
     * Prend l'image figee d'une vue : la vue allegee et ses portails imbriques, dessines comme a l'ecran mais dans le
     * framebuffer de l'image, ou tout est au niveau de stencil de la vue. Rien du monde du joueur n'y entre.
     */
    @SuppressWarnings("try")
    private static void captureView(
            MinecraftClient client,
            RemoteWorld view,
            BackroomsPortalEntity portal,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta,
            int chunks) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_FROZEN_CAPTURE)) {
            Vec3d remoteCameraPos = portal.toDestination(cameraPos);
            Camera remoteCamera = PortalRenderState.remoteCamera();
            Vec2f remoteLook = aimCamera(remoteCamera, remoteCameraPos, portal, camera, REMOTE_POSITION);
            Framebuffer screen = client.getFramebuffer();
            boolean complete = false;
            try {
                scissorScale = FrozenViews.beginCapture(portal, CAPTURE_RECT, screen.textureWidth, screen.textureHeight, positionMatrix,
                        projectionMatrix, cameraPos, chunks, StencilLookMask.value(WORLD_LEVEL + 1, lookOf(portal)));
                scissorOffsetX = CAPTURE_RECT[0];
                scissorOffsetY = CAPTURE_RECT[1];
                GL11.glStencilFunc(GL11.GL_EQUAL, WORLD_LEVEL + 1, StencilLookMask.LEVEL_MASK);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                if (renderView(client, view, camera, remoteCamera, remoteCameraPos, REMOTE_POSITION, projectionMatrix, lightmap, tickDelta,
                        portal, cameraPos, positionMatrix, true)) {
                    complete = view.viewRenderer().sectionsCurrent();
                    renderNestedPortals(client, portal, view, camera, remoteCameraPos, remoteLook, projectionMatrix, lightmap, tickDelta, true);
                }
            } finally {
                scissorOffsetX = 0;
                scissorOffsetY = 0;
                scissorScale = 1;
                FrozenViews.endCapture(client, complete);
                GL11.glStencilFunc(GL11.GL_EQUAL, WORLD_LEVEL, StencilLookMask.LEVEL_MASK);
            }
        }
    }

    /** Frustum du joueur, pour les portails lointains a image figee. */
    private static Frustum frustumFor(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        Vec3d cameraPos = camera.getPos();
        ((FrustumAccessor) CULLING_FRUSTUM).backrooms$init(positionMatrix, projectionMatrix);
        CULLING_FRUSTUM.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        return CULLING_FRUSTUM;
    }

    /** Portails au-dela de la distance de rendu, dans le champ : leur derniere image figee, sans reprise. */
    @SuppressWarnings("try")
    private static void drawFarFrozen(Frustum frustum, Vec3d cameraPos, Matrix4f positionMatrix) {
        for (int i = 0; i < FAR_FROZEN.size(); i++) {
            BackroomsPortalEntity portal = FAR_FROZEN.get(i);
            if (frustum.isVisible(portal.detectionBox())) {
                try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_FROZEN_DRAW)) {
                    drawFrozen(portal, cameraPos, positionMatrix);
                }
            }
        }
        FAR_FROZEN.clear();
    }

    /**
     * Fait passer la zone de vue du niveau {@code level} au suivant, la ou elle passe le test de profondeur ; une
     * meme zone ne compte qu'une fois par pixel, le stencil ne valant plus {@code level} apres la premiere face.
     */
    private static void markArea(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix, int level) {
        GL11.glStencilFunc(GL11.GL_EQUAL, level, StencilLookMask.LEVEL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        try {
            FrameAreaRenderer.mark(portal, cameraPos, positionMatrix);
            // Les pixels passes au niveau suivant prennent le look de la dimension vue, qu'ils gardent au retour de
            // niveau : en fin de rendu, chaque pixel porte le look de la vue la plus profonde qui s'y voit.
            GL11.glStencilFunc(GL11.GL_EQUAL, StencilLookMask.value(level + 1, lookOf(portal)), StencilLookMask.LEVEL_MASK);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glStencilMask(StencilLookMask.LOOK_MASK);
            RenderSystem.disableDepthTest();
            FrameAreaRenderer.draw(portal, cameraPos, positionMatrix);
        } finally {
            GL11.glStencilMask(FULL_MASK);
            RenderSystem.enableDepthTest();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }
        GL11.glStencilFunc(GL11.GL_EQUAL, level + 1, StencilLookMask.LEVEL_MASK);
    }

    /** Identifiant du look de la dimension que ce portail montre, 0 sans profil. */
    private static int lookOf(BackroomsPortalEntity portal) {
        return LookProfiles.of(portal.destinationDim()).id();
    }

    /**
     * L'image figee d'un portail collee sur son cadre, au niveau du monde du joueur : ses pixels prennent le look de la
     * dimension qu'elle montre, pour le post-traitement, sans changer de niveau.
     */
    private static void drawFrozen(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        GL11.glStencilFunc(GL11.GL_ALWAYS, StencilLookMask.value(WORLD_LEVEL, lookOf(portal)), StencilLookMask.LEVEL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GL11.glStencilMask(StencilLookMask.LOOK_MASK);
        try {
            FrozenViews.draw(portal, cameraPos, positionMatrix);
        } finally {
            GL11.glStencilMask(FULL_MASK);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilFunc(GL11.GL_EQUAL, WORLD_LEVEL, StencilLookMask.LEVEL_MASK);
        }
    }

    /**
     * La zone marquee reprend sa vraie profondeur (la face la plus proche) et redescend au niveau {@code level} : ce qui
     * se dessine ensuite a ce niveau la voit comme une surface opaque, et un portail plus lointain y reste cache.
     */
    private static void restoreArea(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix, int level) {
        GL11.glStencilFunc(GL11.GL_EQUAL, level + 1, StencilLookMask.LEVEL_MASK);
        FrameAreaRenderer.writeDepth(portal, cameraPos, positionMatrix, true);
        FrameAreaRenderer.writeDepth(portal, cameraPos, positionMatrix, false);
        // Sans test de profondeur, chaque pixel de la zone redescend une fois : il ne vaut plus level + 1 ensuite.
        RenderSystem.disableDepthTest();
        RenderSystem.colorMask(false, false, false, false);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
        try {
            FrameAreaRenderer.draw(portal, cameraPos, positionMatrix);
        } finally {
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
        }
        GL11.glStencilFunc(GL11.GL_EQUAL, level, StencilLookMask.LEVEL_MASK);
    }

    private static void renderPortalContent(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta,
            boolean draw,
            boolean reduced) {
        RemoteWorld view = RemoteWorldRegistry.viewOf(portal.destinationDim());
        if (view == null) {
            // Destination pas encore annoncee par le serveur : cadre sombre.
            if (draw) {
                FrameAreaRenderer.fill(portal, cameraPos, positionMatrix, 0.0f, 0.0f, 0.0f);
            }
            return;
        }
        Vec3d remoteCameraPos = portal.toDestination(cameraPos);
        Camera remoteCamera = PortalRenderState.remoteCamera();
        Vec2f remoteLook = aimCamera(remoteCamera, remoteCameraPos, portal, camera, REMOTE_POSITION);
        if (!draw) {
            if (keepWarm(client, view, camera, remoteCamera, remoteCameraPos, REMOTE_POSITION, projectionMatrix, portal)) {
                renderNestedPortals(client, portal, view, camera, remoteCameraPos, remoteLook, projectionMatrix, lightmap, tickDelta, false);
            }
            return;
        }

        FrameAreaRenderer.writeDepth(portal, cameraPos, positionMatrix, true);
        // Rien de la vue ne sort du rectangle d'ecran du cadre : le ciseau epargne au GPU les fragments du reste de l'ecran,
        // quelle que soit la couche (ciel, nuages, terrain, entities, pluie), avant meme le stencil.
        boolean scissored = pushScissor(client, portal, cameraPos, positionMatrix, projectionMatrix);
        try {
            if (renderView(client, view, camera, remoteCamera, remoteCameraPos, REMOTE_POSITION, projectionMatrix, lightmap, tickDelta,
                    portal, cameraPos, positionMatrix, reduced)) {
                renderNestedPortals(client, portal, view, camera, remoteCameraPos, remoteLook, projectionMatrix, lightmap, tickDelta, true);
            } else {
                FrameAreaRenderer.fill(portal, cameraPos, positionMatrix, 0.0f, 0.0f, 0.0f);
            }
        } finally {
            popScissor(scissored);
        }
    }

    /**
     * Pose le ciseau d'ecran sur le rectangle du cadre, coupe avec le rectangle deja pose s'il y en a un, et dit s'il a ete
     * pose. Un cadre qui touche le plan proche (un sommet derriere la camera) couvre potentiellement tout l'ecran : pas de
     * ciseau pour lui, ni pour ce qu'il montre ; un ciseau deja pose reste tel quel.
     */
    private static boolean pushScissor(MinecraftClient client, BackroomsPortalEntity frame, Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f projection) {
        if (scissorDepth == SCISSORS.length) {
            return false;
        }
        int[] rect = SCISSORS[scissorDepth];
        if (!screenRect(frame, cameraPos, positionMatrix, projection, client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight, rect)) {
            return false;
        }
        if (scissorDepth > 0) {
            int[] outer = SCISSORS[scissorDepth - 1];
            rect[0] = Math.max(rect[0], outer[0]);
            rect[1] = Math.max(rect[1], outer[1]);
            rect[2] = Math.min(rect[2], outer[2]);
            rect[3] = Math.min(rect[3], outer[3]);
        }
        scissorDepth++;
        applyScissor(rect);
        return true;
    }

    /** Retire le ciseau pose par {@link #pushScissor} : le rectangle exterieur reprend, ou le ciseau est leve. */
    private static void popScissor(boolean scissored) {
        if (!scissored) {
            return;
        }
        scissorDepth--;
        if (scissorDepth == 0) {
            RenderSystem.disableScissor();
        } else {
            applyScissor(SCISSORS[scissorDepth - 1]);
        }
    }

    private static void applyScissor(int[] rect) {
        RenderSystem.enableScissor((rect[0] - scissorOffsetX) * scissorScale, (rect[1] - scissorOffsetY) * scissorScale,
                Math.max(0, rect[2] - rect[0]) * scissorScale, Math.max(0, rect[3] - rect[1]) * scissorScale);
    }

    /**
     * Part de l'ecran que couvre la vue en cours : le rectangle du ciseau de son cadre, ou tout l'ecran pour une vue qui
     * le couvre (ou dont le cadre touche la camera, sans rectangle). Lue par le plafond de clignotement.
     */
    private static float screenArea(MinecraftClient client, boolean throughFrame) {
        if (!throughFrame || scissorDepth == 0) {
            return 1.0f;
        }
        int[] rect = SCISSORS[scissorDepth - 1];
        float screen = (float) client.getFramebuffer().textureWidth * client.getFramebuffer().textureHeight;
        return MathHelper.clamp(Math.max(0, rect[2] - rect[0]) * (float) Math.max(0, rect[3] - rect[1]) / screen, 0.0f, 1.0f);
    }

    /**
     * Rectangle d'ecran des quatre sommets du cadre, en pixels du framebuffer, elargi d'une marge et borne a l'ecran.
     *
     * @return {@code false} si un sommet est derriere la camera : le rectangle n'est pas defini
     */
    private static boolean screenRect(
            BackroomsPortalEntity frame, Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f projection, int width, int height, int[] out) {
        VIEW_PROJECTION.set(projection).mul(positionMatrix);
        Vec3d center = frame.getPos();
        PortalFacing facing = frame.facingSource();
        double halfWidth = frame.width() / 2.0;
        double halfHeight = frame.height() / 2.0;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            double sw = (corner & 1) != 0 ? halfWidth : -halfWidth;
            double sh = (corner & 2) != 0 ? halfHeight : -halfHeight;
            SCRATCH_CLIP.set(
                    (float) (center.x - cameraPos.x + facing.axisW.x * sw + facing.axisH.x * sh),
                    (float) (center.y - cameraPos.y + facing.axisW.y * sw + facing.axisH.y * sh),
                    (float) (center.z - cameraPos.z + facing.axisW.z * sw + facing.axisH.z * sh),
                    1.0f);
            VIEW_PROJECTION.transform(SCRATCH_CLIP);
            if (SCRATCH_CLIP.w <= 1.0e-4f) {
                return false;
            }
            float x = (SCRATCH_CLIP.x / SCRATCH_CLIP.w * 0.5f + 0.5f) * width;
            float y = (SCRATCH_CLIP.y / SCRATCH_CLIP.w * 0.5f + 0.5f) * height;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        out[0] = MathHelper.clamp(MathHelper.floor(minX) - SCISSOR_MARGIN, 0, width);
        out[1] = MathHelper.clamp(MathHelper.floor(minY) - SCISSOR_MARGIN, 0, height);
        out[2] = MathHelper.clamp(MathHelper.ceil(maxX) + SCISSOR_MARGIN, 0, width);
        out[3] = MathHelper.clamp(MathHelper.ceil(maxY) + SCISSOR_MARGIN, 0, height);
        return true;
    }

    /**
     * Un niveau de plus : les portails visibles dans la vue, depuis sa camera et a travers son cadre, sont dessines a
     * leur tour, sous le plan de decoupe de la vue qui les contient, dont la profondeur est deja ecrite. Sans portail
     * exterieur ({@code outer} nul), la vue couvre l'ecran : tous les portails qu'y voit sa camera comptent. Une vue cachee
     * ({@code draw} faux) n'entretient que les leurs ; dans une vue dessinee, un portail cache derriere des blocs du monde de
     * la vue est seulement entretenu, lui aussi.
     */
    private static void renderNestedPortals(
            MinecraftClient client,
            @Nullable BackroomsPortalEntity outer,
            RemoteWorld view,
            Camera camera,
            Vec3d remoteCameraPos,
            Vec2f remoteLook,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta,
            boolean draw) {
        collectNested(NESTED, outer, view, remoteCameraPos, remoteLook, REMOTE_POSITION, projectionMatrix);
        if (outer != null) {
            PortalFacing arrival = outer.facingDestination();
            PortalClipping.push(arrival.normal, outer.destinationPos(), remoteCameraPos, REMOTE_POSITION);
        }
        int level = WORLD_LEVEL + 1;
        try {
            for (int i = 0; i < NESTED.size(); i++) {
                BackroomsPortalEntity inner = NESTED.get(i);
                if (!draw || hiddenBehindBlocks(view.world(), view.renderer(), remoteCameraPos, inner)) {
                    renderNestedPortal(client, inner, camera, remoteCameraPos, projectionMatrix, lightmap, tickDelta, false);
                    continue;
                }
                markArea(inner, remoteCameraPos, REMOTE_POSITION, level);
                try {
                    renderNestedPortal(client, inner, camera, remoteCameraPos, projectionMatrix, lightmap, tickDelta, true);
                } finally {
                    restoreArea(inner, remoteCameraPos, REMOTE_POSITION, level);
                }
            }
        } finally {
            NESTED.clear();
            if (outer != null) {
                PortalClipping.pop();
            }
        }
    }

    /**
     * Portails de la vue visibles depuis sa camera, dans {@code out} (vide d'abord) : visibles, de face, a portee, dans
     * le frustum de la vue et dans le volume visible a travers son cadre. Sans portail exterieur ({@code outer} nul),
     * la vue couvre l'ecran : tous les portails qu'y voit sa camera comptent.
     *
     * @param remotePosition matrice de vue de la camera de la vue
     */
    static void collectNested(
            List<BackroomsPortalEntity> out,
            @Nullable BackroomsPortalEntity outer,
            RemoteWorld view,
            Vec3d remoteCameraPos,
            Vec2f remoteLook,
            Matrix4f remotePosition,
            Matrix4f projectionMatrix) {
        out.clear();
        ((FrustumAccessor) NESTED_FRUSTUM).backrooms$init(remotePosition, projectionMatrix);
        NESTED_FRUSTUM.setPosition(remoteCameraPos.x, remoteCameraPos.y, remoteCameraPos.z);
        if (outer != null) {
            PortalFacing arrival = outer.facingDestination();
            NESTED_CONE.set(remoteCameraPos, outer.destinationPos(), arrival.axisW, arrival.axisH, arrival.normal,
                    outer.width() / 2.0, outer.height() / 2.0);
        } else {
            NESTED_CONE.unbounded();
        }
        double renderDistance = Math.max(1, ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS);
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(view.world());
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity inner = portals.get(i);
            // Les faces du portail d'arrivee lui-meme sont dans le plan de decoupe : celle qui regarde la camera de la
            // vue (la face arriere d'un portail double face) recouvrirait toute la vue par l'autre cote.
            if (outer != null && inner.centeredAt(outer.destinationPos())) {
                continue;
            }
            if (!inner.visible() || inner.signedDistance(remoteCameraPos) <= 0.0
                    || !PortalRelevance.isRelevant(remoteCameraPos, remoteLook.y, remoteLook.x, inner.getPos(), renderDistance)
                    || !NESTED_FRUSTUM.isVisible(inner.detectionBox())
                    || !NESTED_CONE.intersects(inner.detectionBox())) {
                continue;
            }
            out.add(inner);
        }
    }

    private static void renderNestedPortal(
            MinecraftClient client,
            BackroomsPortalEntity inner,
            Camera camera,
            Vec3d remoteCameraPos,
            Matrix4f projectionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta,
            boolean draw) {
        RemoteWorld innerView = RemoteWorldRegistry.viewOf(inner.destinationDim());
        if (innerView == null || !hasChunkAt(innerView, inner.destinationPos())) {
            // Pas de donnees pour ce que ce portail montre : cadre sombre, comme un portail trop profond.
            if (draw) {
                FrameAreaRenderer.fill(inner, remoteCameraPos, REMOTE_POSITION, 0.0f, 0.0f, 0.0f);
            }
            return;
        }
        Vec3d innerCameraPos = inner.toDestination(remoteCameraPos);
        Camera nestedCamera = PortalRenderState.nestedCamera();
        aimCamera(nestedCamera, innerCameraPos, inner, PortalRenderState.remoteCamera(), NESTED_POSITION);
        if (!draw) {
            keepWarm(client, innerView, camera, nestedCamera, innerCameraPos, NESTED_POSITION, projectionMatrix, inner);
            return;
        }

        FrameAreaRenderer.writeDepth(inner, remoteCameraPos, REMOTE_POSITION, true);
        boolean scissored = pushScissor(client, inner, remoteCameraPos, REMOTE_POSITION, projectionMatrix);
        try {
            if (!renderView(client, innerView, camera, nestedCamera, innerCameraPos, NESTED_POSITION, projectionMatrix, lightmap, tickDelta,
                    inner, remoteCameraPos, REMOTE_POSITION, reducedDetail(inner, remoteCameraPos, projectionMatrix))) {
                FrameAreaRenderer.fill(inner, remoteCameraPos, REMOTE_POSITION, 0.0f, 0.0f, 0.0f);
            }
        } finally {
            popScissor(scissored);
        }
    }

    /**
     * Place et oriente une camera de vue, image par un portail de la camera qui le regarde, roulis compris, et en derive
     * la matrice de vue.
     *
     * @return regard transpose de la camera de vue, pour choisir les portails qu'elle voit
     */
    static Vec2f aimCamera(Camera viewCamera, Vec3d position, BackroomsPortalEntity portal, Camera source, Matrix4f positionMatrix) {
        ((CameraAccessor) viewCamera).backrooms$setPos(position);
        Vec2f look = PortalViewTransform.aim(viewCamera, portal, source);
        PortalViewTransform.positionMatrix(viewCamera, positionMatrix);
        return look;
    }

    /** {@code true} si la vue possede le chunk du point d'arrivee ; sinon elle ne montrerait que du ciel. */
    static boolean hasChunkAt(RemoteWorld view, Vec3d position) {
        return view.world().getChunkManager().isChunkLoaded(
                ChunkSectionPos.getSectionCoord(position.x), ChunkSectionPos.getSectionCoord(position.z));
    }

    /**
     * Palier de la vue de ce portail a cette image, vue de cette camera ({@link ViewDetail}) : sur la taille a l'ecran d'un
     * bloc juste derriere le cadre. Retenu sur le portail, pour l'hysteresis de l'image suivante.
     */
    static ViewDetail.Tier detailTier(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f projection) {
        if (!ModConfig.ENABLE_PORTAL_LOD) {
            return ViewDetail.Tier.FULL;
        }
        double pixels = ViewDetail.blockPixels(Math.sqrt(portal.squaredDistanceTo(cameraPos)), projection.m11(),
                MinecraftClient.getInstance().getWindow().getFramebufferHeight());
        int frame = PortalRenderState.frameIndex();
        ViewDetail.Tier current = ViewDetail.tierOf(portal.detailTier());
        ViewDetail.Tier tier = ViewDetail.tier(current, frame - portal.detailChangedFrame(), pixels);
        if (tier != current) {
            portal.setDetailTier(tier.ordinal(), frame);
        }
        return tier;
    }

    /** {@code true} si la vue de ce portail est allegee a cette image (palier allege ou fige, {@link #detailTier}). */
    static boolean reducedDetail(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f projection) {
        return detailTier(portal, cameraPos, projection) != ViewDetail.Tier.FULL;
    }

    /**
     * {@code true} si le cadre est prouve cache derriere des blocs pleins et opaques du monde {@code world}, vu de
     * {@code cameraPos}, tel que {@code renderer} l'affiche ({@link FrameOcclusion}). Jamais quand un autre mod dessine le
     * terrain : rien ne dit alors ce qui est deja affiche.
     */
    @SuppressWarnings("try")
    private static boolean hiddenBehindBlocks(ClientWorld world, WorldRenderer renderer, Vec3d cameraPos, BackroomsPortalEntity portal) {
        WorldRendererAccessor accessor = (WorldRendererAccessor) renderer;
        if (!ModConfig.ENABLE_PORTAL_OCCLUSION || RemoteViewRenderer.foreignTerrain(accessor)) {
            return false;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_OCCLUSION)) {
            PortalFacing facing = portal.facingSource();
            return FrameOcclusion.hidden(cameraPos, portal.getPos(), facing.axisW, facing.axisH, portal.width() / 2.0, portal.height() / 2.0,
                    OCCLUDERS.set(world, accessor.backrooms$getChunks()));
        } finally {
            // Rien ne garde le monde ni la grille d'une image a l'autre : apres une deconnexion, ils seraient retenus.
            OCCLUDERS.release();
        }
    }

    /**
     * Vue cachee : son entretien a la place de son dessin ({@link RemoteViewRenderer#keepWarm}), avec le monde et le
     * renderer du client remplaces comme pour le dessin.
     *
     * @return {@code true} si la vue a ete entretenue ; {@code false} si l'entretien a echoue et que le rendu distant est coupe
     */
    private static boolean keepWarm(
            MinecraftClient client,
            RemoteWorld view,
            Camera camera,
            Camera viewCamera,
            Vec3d viewCameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            BackroomsPortalEntity frame) {
        MinecraftClientAccessor clientAccessor = (MinecraftClientAccessor) client;
        ClientWorld mainWorld = client.world;
        WorldRenderer mainRenderer = client.worldRenderer;
        try {
            client.world = view.world();
            clientAccessor.backrooms$setWorldRenderer(view.renderer());
            view.viewRenderer().keepWarm(client, viewCamera, viewCameraPos, positionMatrix, projection, frame);
            return true;
        } catch (RuntimeException exception) {
            PortalRenderState.disableRemoteRendering("exception pendant l'entretien d'une vue cachee", exception);
            return false;
        } finally {
            client.world = mainWorld;
            clientAccessor.backrooms$setWorldRenderer(mainRenderer);
            client.getEntityRenderDispatcher().configure(mainWorld, camera, client.targetedEntity);
            client.getBlockEntityRenderDispatcher().configure(mainWorld, camera, client.crosshairTarget);
        }
    }

    /**
     * Cases d'un monde tel qu'il est, et tel qu'un renderer l'affiche : un bloc plein et opaque, dans une section de sa
     * grille construite apres le dernier changement de ses blocs ({@link SectionGenerations}). Un bloc tout juste pose ne
     * cache rien tant que sa section ne le dessine pas ; un chunk absent est vide.
     */
    private static final class ShownOccluders implements FrameOcclusion.Occluders {

        private final BlockPos.Mutable cursor = new BlockPos.Mutable();
        private @Nullable ClientWorld world;
        private @Nullable BuiltChunkStorage chunks;

        ShownOccluders set(ClientWorld shown, BuiltChunkStorage grid) {
            this.world = shown;
            this.chunks = grid;
            return this;
        }

        void release() {
            this.world = null;
            this.chunks = null;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            this.cursor.set(x, y, z);
            return this.world.getBlockState(this.cursor).isOpaqueFullCube(this.world, this.cursor);
        }

        @Override
        public boolean shown(int x, int y, int z) {
            if (!opaque(x, y, z)) {
                return false;
            }
            // La grille est circulaire : la section stockee a cet endroit peut couvrir une autre position.
            ChunkBuilder.BuiltChunk section = ((BuiltChunkStorageAccessor) this.chunks).backrooms$getRenderedChunk(this.cursor);
            if (section == null) {
                return false;
            }
            BlockPos origin = section.getOrigin();
            return origin.getX() == (x & SECTION_MASK) && origin.getY() == (y & SECTION_MASK) && origin.getZ() == (z & SECTION_MASK)
                    && ((SectionGenerations) section).backrooms$current();
        }
    }

    /**
     * Dessine une vue avec le monde et le renderer client temporairement remplaces, pour que la lightmap, le
     * brouillard et les lectures implicites de {@code MinecraftClient.world} voient la bonne dimension. La zone
     * est d'abord remplie de la couleur de brouillard de la vue : la ou rien n'est dessine, c'est son horizon.
     *
     * @param client client, dont le monde et le renderer sont substitues le temps du dessin
     * @param view monde de la vue
     * @param camera camera du joueur, rendue aux dispatchers apres le rendu
     * @param viewCamera camera de la vue, deja orientee
     * @param viewCameraPos position de la camera de la vue
     * @param positionMatrix matrice de vue de la camera de la vue
     * @param projection projection du joueur, la seule : le plan du portail d'arrivee est coupe par les shaders
     * @param lightmap gestionnaire de lightmap du rendu : il porte celle du monde de la vue le temps de la dessiner
     * @param tickDelta fraction de tick de l'image
     * @param frame portail dont c'est la vue
     * @param frameCameraPos camera qui dessine la zone du cadre ; {@code null} quand la vue couvre l'ecran
     * @param framePosition matrice de vue de cette camera ; {@code null} quand la vue couvre l'ecran
     * @return {@code true} si la vue a ete dessinee ; {@code false} si elle a echoue et que le rendu distant est coupe
     */
    private static boolean renderView(
            MinecraftClient client,
            RemoteWorld view,
            Camera camera,
            Camera viewCamera,
            Vec3d viewCameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            LightmapTextureManager lightmap,
            float tickDelta,
            BackroomsPortalEntity frame,
            @Nullable Vec3d frameCameraPos,
            @Nullable Matrix4f framePosition,
            boolean reduced) {
        MinecraftClientAccessor clientAccessor = (MinecraftClientAccessor) client;
        ClientWorld mainWorld = client.world;
        WorldRenderer mainRenderer = client.worldRenderer;
        // Une vue secondaire montre le monde du joueur : sa lightmap est deja celle des shaders.
        boolean ownLightmap = view.world() != mainWorld;
        boolean looked = false;
        try {
            client.world = view.world();
            clientAccessor.backrooms$setWorldRenderer(view.renderer());
            // Le look et les neons de la dimension vue, pour chaque shader que la vue active ; ceux de la vue englobante reprennent apres.
            LookFrame.pushView(view.world(), viewCameraPos, positionMatrix, tickDelta, screenArea(client, framePosition != null));
            looked = true;
            if (ownLightmap) {
                ViewLightmaps.bind(client, lightmap, view.world(), tickDelta);
            }
            view.viewRenderer().prepareFog(client, viewCamera, tickDelta);
            boolean throughFrame = frameCameraPos != null && framePosition != null;
            if (throughFrame) {
                float[] fog = RenderSystem.getShaderFogColor();
                FrameAreaRenderer.fill(frame, frameCameraPos, framePosition, fog[0], fog[1], fog[2]);
            } else {
                // La vue couvre l'ecran : couleur de son brouillard (posee par prepareFog) et profondeur remises a zero partout.
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            }
            view.viewRenderer().render(client, viewCamera, viewCameraPos, positionMatrix, projection, frame, frameCameraPos, lightmap, tickDelta,
                    reduced);
            return true;
        } catch (RuntimeException exception) {
            PortalRenderState.disableRemoteRendering("exception pendant le rendu distant", exception);
            return false;
        } finally {
            client.world = mainWorld;
            clientAccessor.backrooms$setWorldRenderer(mainRenderer);
            client.getEntityRenderDispatcher().configure(mainWorld, camera, client.targetedEntity);
            client.getBlockEntityRenderDispatcher().configure(mainWorld, camera, client.crosshairTarget);
            if (ownLightmap) {
                ViewLightmaps.unbind(lightmap);
            }
            if (looked) {
                LookFrame.popView();
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static void logDebugState(MinecraftClient client) {
        if (!ModConfig.DEBUG_PORTAL_RENDERING) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextDebugLogMillis) {
            return;
        }
        nextDebugLogMillis = now + DEBUG_LOG_INTERVAL_MILLIS;
        // Etat GL au moment des portails : ou le monde se dessine (un autre mod peut l'avoir detourne), avec quel stencil.
        float[] fog = RenderSystem.getShaderFogColor();
        ModConstants.LOGGER.info("[Portal] Framebuffer lie {} (client {}), attachement stencil {}, profondeur {}, programme {}, brouillard {}/{}/{}.",
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), client.getFramebuffer().fbo,
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE),
                GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), fog[0], fog[1], fog[2]);
        for (int i = 0; i < VISIBLE.size(); i++) {
            BackroomsPortalEntity portal = VISIBLE.get(i);
            RemoteWorld view = RemoteWorldRegistry.viewOf(portal.destinationDim());
            ModConstants.LOGGER.info("[Portal] Portail {} vers {}, vue {} : {}.",
                    portal.getBlockPos().toShortString(),
                    portal.destinationDim().getValue(),
                    ViewDetail.tierOf(portal.detailTier()),
                    view == null ? "vue absente" : view.debugString());
        }
    }
}
