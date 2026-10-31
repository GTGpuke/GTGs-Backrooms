package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.WorldChunk;

import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.client.interaction.RemoteTargeting;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ObliqueProjection;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalClipping;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalStencilRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalViewTransform;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.SectionGenerations;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewCone;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewRenderStart;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.mixin.client.BuiltChunkStorageAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.CameraAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.FrustumAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Dessine un {@link RemoteWorld} depuis une camera transposee : ciel, couches de blocs, entities, block
 * entities, fissures de minage, contour du bloc vise, particules, nuages, pluie et neige, avec le brouillard et la
 * meteo de sa dimension.
 *
 * <p>L'etat GL (stencil, profondeur) est pose par l'appelant, qui remet ensuite les dispatchers de rendu sur le
 * monde principal. Tout se dessine avec la projection du joueur ; le plan du portail d'arrivee coupe ce qui est
 * derriere lui par les shaders ({@link PortalClipping}). Les sections a dessiner sont choisies par un parcours
 * synchrone propre a la vue ({@link ViewSectionWalker}), limite au volume visible a travers le cadre
 * ({@link ViewCone}) ; la grille de sections du renderer est ancree sur l'arrivee du portail, pas sur la camera.
 *
 * <p>Sous un pack de shaders, la vue est au contraire un rendu complet de son renderer ({@link #renderWhole}), que le
 * mod de shaders dessine en entier dans son pipeline.
 */
public final class RemoteViewRenderer {

    /** Au-dela de cette distance au carre, les fissures de minage ne sont plus dessinees (comme vanilla). */
    private static final double BLOCK_DAMAGE_RANGE_SQUARED = 1024.0;
    /** Cote, en blocs, de la cellule autour de la camera que le frustum doit couvrir : le recul de vanilla. */
    private static final int FRUSTUM_COVER_BOX = 8;
    /** Cote, en blocs, de la cellule de camera dont le changement fait repartir le graphe de culling vanilla (comme vanilla). */
    private static final double CAMERA_CELL = 8.0;
    /**
     * Distance du joueur au cadre d'un portail de son monde sous laquelle il peut passer bientot : les sections autour de
     * l'arrivee se construisent alors d'avance ({@link #prebuildArrival}).
     */
    public static final double PREBUILD_DISTANCE = 8.0;
    /** Sections construites d'avance tout autour de l'arrivee : rayon horizontal, en sections ; au-dela, devant la sortie. */
    private static final int PREBUILD_RADIUS = 4;
    /** Sections construites d'avance sous celle de l'arrivee, et au moins autant au-dessus. */
    private static final int PREBUILD_HEIGHT = 2;
    /**
     * Constructions demandees d'avance au plus par image : la file du chunk builder reste courte, et un client a peu
     * d'images par seconde (machine chargee, rendu logiciel) prepare quand meme l'arrivee pendant son approche.
     */
    private static final int PREBUILD_PER_FRAME = 32;
    /** Position de la section examinee par la construction d'avance (fil de rendu seulement). */
    private static final BlockPos.Mutable PREBUILD_CURSOR = new BlockPos.Mutable();
    /**
     * Millisecondes pendant lesquelles un renderer garde les tampons de construction du joueur apres sa derniere
     * construction d'avance : qui tourne autour de la distance d'un cadre ne les fait pas allouer et fermer sans cesse.
     */
    private static final long PREBUILD_HOLD_MS = 5_000L;
    /**
     * Repos de la construction d'avance, en millisecondes, apres un parcours qui n'a plus rien trouve a construire : sans
     * lui, le parcours des colonnes jusqu'a la distance de vue se refait a chaque image pres d'un cadre, pour rien. En
     * millisecondes et non en images : un client a peu d'images par seconde reprend aussi vite les chunks qui arrivent.
     */
    private static final long PREBUILD_REST_MS = 100L;
    /**
     * La grille de sections d'un renderer ne se recentre que si l'ancrage s'eloigne de plus de ce nombre de chunks de
     * son centre : deux portails voisins vers la meme dimension, ou le retour juste apres une traversee, la partagent
     * sans la redecouper (chaque recentrage remet des colonnes entieres de sections a reconstruire).
     */
    static final int GRID_MARGIN_CHUNKS = 2;
    private static final List<RenderLayer> BLOCK_LAYERS = RenderLayer.getBlockLayers();
    /**
     * Entities engagees dans le cadre d'arrivee d'une vue imbriquee dans une autre vue a travers un cadre, dessinees apres
     * les autres sans la decoupe : leur partie encore derriere l'arrivee ne se dessine nulle part ailleurs a ce niveau.
     */
    private static final List<Entity> STRADDLING = new ArrayList<>();
    /** Plans empiles dans une vue imbriquee dans une autre vue a travers un cadre. */
    private static final int NESTED_DEPTH = 2;
    /** Pile de matrices reutilisee pour les entities et block entities ; vide entre deux rendus. */
    private static final MatrixStack MATRICES = new MatrixStack();
    /**
     * Tampons des vues, distincts de ceux du monde principal : les vues se dessinent au milieu du rendu principal, dont
     * les couches translucides attendent encore dans les siens.
     */
    private static final VertexConsumerProvider.Immediate ENTITY_BUFFERS = VertexConsumerProvider.immediate(new BufferAllocator(786_432));
    private static final VertexConsumerProvider.Immediate EFFECT_BUFFERS = VertexConsumerProvider.immediate(new BufferAllocator(1536));
    private static final Matrix4f IDENTITY = new Matrix4f();
    /**
     * Plan d'arrivee en espace camera et projection a plan proche oblique d'une vue dont le terrain est dessine par un
     * autre mod ({@link ObliqueProjection}) ; reutilises, thread de rendu seulement.
     */
    private static final Vector4f ARRIVAL_PLANE = new Vector4f();
    private static final Matrix4f OBLIQUE = new Matrix4f();
    /** Bloc examine entre la camera d'une vue et son cadre d'arrivee, reutilise : thread de rendu seulement. */
    private static final BlockPos.Mutable SCAN_POS = new BlockPos.Mutable();
    /** Retrait de chaque bloc examine : un bloc qui borde le cadre le touche sans y entrer. */
    private static final double BLOCK_INSET = 1.0e-4;
    /** Au-dela de ce nombre de blocs a examiner, le volume entre la camera et le cadre est tenu pour occupe. */
    private static final long MAX_OBSTRUCTION_SCAN = 32_768L;
    /** Sections du moniteur de performance : une vue entiere, son terrain (grille et parcours des sections), ses entities. */
    private static final String PERF_VIEW = "client.portal.view";
    private static final String PERF_TERRAIN = "client.portal.terrain";
    private static final String PERF_ENTITIES = "client.portal.entities";
    private static final String PERF_START = "client.portal.render_start";
    /** Section du moniteur de performance : l'entretien d'une vue cachee derriere des blocs, a la place de son dessin. */
    private static final String PERF_HIDDEN = "client.portal.hidden_view";
    /** Images sans dessin allege d'un portail apres lesquelles ses entities figees sont rendues. */
    private static final int FROZEN_UNUSED_FRAMES = 600;
    /** Elargissement du cadre, en blocs, pour le volume de la prise des entities figees : la camera bouge entre deux prises. */
    private static final double FROZEN_CONE_MARGIN = 1.0;

    private final RemoteWorld owner;
    private final ClientWorld world;
    private final WorldRenderer renderer;
    /** Volume que montre le cadre d'une vue allegee, elargi d'un bloc : celui de la prise de ses entities figees. */
    private final ViewCone frozenCone = new ViewCone();
    /** Frustum du rendu, reutilise d'une image a l'autre : vanilla ne le reinitialise que par constructeur. */
    private final Frustum frustum = new Frustum(IDENTITY, IDENTITY);
    private final ViewCone cone = new ViewCone();
    /** Pyramide de la camera de la vue au cadre d'arrivee, pour savoir s'il y a quelque chose a couper entre les deux. */
    private final ViewCone clipVolume = new ViewCone();
    private final ViewSectionWalker sectionWalker = new ViewSectionWalker();
    /** Entities figees des vues allegees de ce renderer, une par portail ; parcourues par index. */
    private final List<FrozenEntityLayers> frozen = new ArrayList<>();
    /** Terrain simplifie des vues allegees de ce renderer. */
    private final CoarseTerrain coarse;
    /** Palier de la vue en cours de dessin : {@code true} si elle est allegee (ni meteo ni particules, entities figees). */
    private boolean reduced;
    /** Heure de la derniere construction d'avance de l'arrivee ({@link #prebuildArrival}), en millisecondes. */
    private long lastPrebuildMs = Long.MIN_VALUE / 2;
    /** Heure jusqu'a laquelle la construction d'avance se repose, rien n'y restant a construire. */
    private long prebuildRestUntilMs = Long.MIN_VALUE / 2;
    /** Brouillard du ciel, passe au rendu du ciel : cree une fois, il lit la camera et l'instant poses avant chaque dessin. */
    private final Runnable skyFog = this::applySkyFog;
    /** Camera et fraction de tick du ciel en cours de dessin, lues par {@link #skyFog}. */
    private Camera skyFogCamera;
    private float skyFogTickDelta;
    /** Image ou ce renderer a prepare son terrain pour la derniere fois : une seule fois par image, pour sa premiere vue. */
    private int terrainFrame = -1;
    /** Image ou ce renderer a eu son debut de rendu a blanc pour la derniere fois ({@link #startRender}). */
    private int startedFrame = -1;
    /** Portee de vue (blocs) et brouillard epais poses par {@link #prepareFog} pour le rendu qui suit. */
    private float viewDistance;
    private boolean thickFog;

    RemoteViewRenderer(RemoteWorld owner) {
        this.owner = owner;
        this.world = owner.world();
        this.renderer = owner.renderer();
        this.coarse = new CoarseTerrain(this.world);
    }

    /**
     * Pose couleur et portee du brouillard pour cette vue, depuis sa camera, comme le fait le rendu principal : le
     * fond du cadre et le terrain s'y fondent. A appeler avec {@code MinecraftClient.world} deja remplace par ce monde.
     */
    public void prepareFog(MinecraftClient client, Camera camera, float tickDelta) {
        this.viewDistance = client.gameRenderer.getViewDistance();
        this.thickFog = thickFog(client, this.world, camera);
        BackgroundRenderer.render(camera, tickDelta, this.world, client.options.getClampedViewDistance(), client.gameRenderer.getSkyDarkness(tickDelta));
        // BackgroundRenderer.render calcule la couleur, applyFogColor la donne aux shaders : sans lui, la vue se fondrait dans la
        // couleur du brouillard du monde du joueur (le noir du Level 0 sur l'horizon de l'Overworld).
        BackgroundRenderer.applyFogColor();
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, Math.max(this.viewDistance, 32.0f), this.thickFog, tickDelta);
    }

    /** Meme regle que {@code WorldRenderer.render} : brouillard epais de la dimension a la camera, ou impose par un boss. */
    private static boolean thickFog(MinecraftClient client, ClientWorld world, Camera camera) {
        BlockPos cameraBlock = camera.getBlockPos();
        return world.getDimensionEffects().useThickFog(cameraBlock.getX(), cameraBlock.getY()) || client.inGameHud.getBossBarHud().shouldThickenFog();
    }

    /**
     * Dessine la vue a travers un portail depuis la camera transposee.
     *
     * @param client client, dont le monde et le renderer sont deja ceux de la vue
     * @param camera camera de la vue, position et rotation deja ecrites
     * @param cameraPos position de la camera de la vue
     * @param positionMatrix matrice de vue de cette camera
     * @param projection projection du joueur
     * @param portal portail dont c'est la vue : son arrivee donne le plan de decoupe, le volume visible et l'ancrage de la grille
     * @param frameCameraPos camera qui regarde le cadre, dans le monde du portail, pour une vue a travers lui ; {@code null}
     *                       quand la camera elle-meme est passee de l'autre cote (troisieme personne) : elle voit alors
     *                       tout, sans plan de decoupe ni volume limite
     * @param lightmap gestionnaire de lightmap du rendu, deja sur celle de ce monde
     * @param tickDelta fraction de tick de l'image
     */
    @SuppressWarnings("try")
    public void render(
            MinecraftClient client,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            LightmapTextureManager lightmap,
            float tickDelta,
            boolean reducedDetail) {
        this.reduced = reducedDetail;
        PortalRenderState.setReducedView(reducedDetail);
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_VIEW)) {
            renderMeasured(client, camera, cameraPos, positionMatrix, projection, portal, frameCameraPos, lightmap, tickDelta);
        } finally {
            this.reduced = false;
            PortalRenderState.setReducedView(false);
        }
        if (!reducedDetail) {
            dropFrozen(portal);
        }
    }

    private void renderMeasured(
            MinecraftClient client,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            LightmapTextureManager lightmap,
            float tickDelta) {
        boolean throughFrame = frameCameraPos != null;
        startRender(PortalRenderState.frameIndex());
        PortalRenderState.setViewCameraPos(cameraPos);
        this.owner.markViewed(cameraPos);
        PortalFacing arrival = portal.facingDestination();
        if (throughFrame) {
            PortalClipping.push(arrival.normal, portal.destinationPos(), cameraPos, positionMatrix);
            this.cone.set(cameraPos, portal.destinationPos(), arrival.axisW, arrival.axisH, arrival.normal, portal.width() / 2.0, portal.height() / 2.0);
        } else {
            this.cone.unbounded();
        }
        // Terrain dessine par un autre mod : ses shaders ignorent la distance de decoupe, la projection coupe au plan.
        WorldRendererAccessor accessor = (WorldRendererAccessor) this.renderer;
        boolean foreign = foreignTerrain(accessor);
        Matrix4f drawProjection = projection;
        if (foreign && throughFrame && arrivalObstructed(portal, cameraPos)) {
            PortalViewTransform.clipPlane(arrival.normal, portal.destinationPos(), cameraPos, positionMatrix, ARRIVAL_PLANE);
            drawProjection = ObliqueProjection.apply(projection, ARRIVAL_PLANE, OBLIQUE);
        }
        try {
            renderView(client, accessor, foreign, camera, cameraPos, positionMatrix, projection, drawProjection, portal, frameCameraPos, lightmap,
                    tickDelta);
        } finally {
            if (throughFrame) {
                PortalClipping.pop();
            }
            PortalRenderState.setViewCameraPos(null);
        }
    }

    /**
     * Dessine la vue par le rendu complet de son renderer ({@code WorldRenderer.render}), avec les parametres du rendu
     * du joueur sauf la camera, sa matrice de vue et, a travers un cadre, la projection : un mod de shaders y deroule
     * tout son pipeline pour le monde de la vue ({@code ShaderPackPortalRenderer}). Le monde et le renderer du client
     * sont deja ceux de la vue ; l'appelant limite le dessin au cadre et remet l'image du joueur autour. Le terrain est
     * celui que le renderer prepare lui-meme ({@code setupTerrain}) : ce chemin ne sert qu'avec un mod qui dessine le
     * terrain, ce qu'un mod de shaders exige.
     *
     * @param portal portail dont c'est la vue, pour le plan d'arrivee de la projection a plan proche oblique ;
     *               {@code null} quand la camera est passee de l'autre cote (troisieme personne) et voit tout
     * @return la projection avec laquelle la vue a ete dessinee : celle du joueur, ou la projection oblique a travers un cadre
     */
    @SuppressWarnings("try")
    public Matrix4f renderWhole(
            MinecraftClient client,
            RenderTickCounter tickCounter,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            @Nullable BackroomsPortalEntity portal,
            LightmapTextureManager lightmap,
            boolean reducedDetail) {
        // Sous un pack, la vue allegee perd meteo et particules ; ses entities restent animees (le pack les groupe lui-meme).
        PortalRenderState.setReducedView(reducedDetail);
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_VIEW)) {
            return renderWholeMeasured(client, tickCounter, camera, cameraPos, positionMatrix, projection, portal, lightmap);
        } finally {
            PortalRenderState.setReducedView(false);
        }
    }

    private Matrix4f renderWholeMeasured(
            MinecraftClient client,
            RenderTickCounter tickCounter,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            @Nullable BackroomsPortalEntity portal,
            LightmapTextureManager lightmap) {
        PortalRenderState.setViewCameraPos(cameraPos);
        this.owner.markViewed(cameraPos);
        Matrix4f drawProjection = projection;
        if (portal != null && arrivalObstructed(portal, cameraPos)) {
            PortalFacing arrival = portal.facingDestination();
            PortalViewTransform.clipPlane(arrival.normal, portal.destinationPos(), cameraPos, positionMatrix, ARRIVAL_PLANE);
            drawProjection = ObliqueProjection.apply(projection, ARRIVAL_PLANE, OBLIQUE);
        }
        // Le rendu vanilla empile lui-meme sa matrice de vue sur la pile de RenderSystem (identite a son entree, comme
        // GameRenderer la lui laisse) et lit sa projection dans RenderSystem.
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(drawProjection, VertexSorter.BY_DISTANCE);
        RemoteParticles particles = this.owner.particles();
        RemoteParticles previousParticles = particles != null ? particles.enter(client.particleManager) : null;
        // Un mod de shaders groupe les dessins d'entities dans les tampons du client et ne vide que ceux-la en fin de
        // rendu : ceux du renderer de la vue ne seraient jamais dessines. Les tampons du client sont libres a ce moment.
        WorldRendererAccessor accessor = (WorldRendererAccessor) this.renderer;
        BufferBuilderStorage ownBuffers = accessor.backrooms$getBufferBuilders();
        accessor.backrooms$setBufferBuilders(client.getBufferBuilders());
        try {
            // Frustum que le renderer tient, pose avant render comme pour le joueur : projection du joueur, jamais l'oblique.
            this.renderer.setupFrustum(cameraPos, positionMatrix, projection);
            this.renderer.render(tickCounter, false, camera, client.gameRenderer, lightmap, positionMatrix, drawProjection);
            return drawProjection;
        } finally {
            accessor.backrooms$setBufferBuilders(ownBuffers);
            if (particles != null) {
                particles.exit(client.particleManager, previousParticles);
            }
            RenderSystem.setProjectionMatrix(previousProjection, sorter);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            PortalRenderState.setViewCameraPos(null);
        }
    }

    /**
     * {@code true} si quelque chose se dessinerait entre la camera de la vue et le plan d'arrivee, dans la pyramide qui
     * s'appuie sur le cadre d'arrivee : un bloc (hors le bloc portail, invisible) ou une entity (hors le portail d'arrivee,
     * pose sur le plan). Seul cela demande la projection a plan proche oblique ; sans rien a couper, la projection du
     * joueur donne la meme image. Elle est preferee : le plan lointain de la projection oblique est incline, et un pack de
     * shaders qui reconstruit ciel, nuages et brouillard depuis la profondeur par la projection inverse fait repartir
     * derriere la camera une partie des rayons du ciel (ciel delave, nuages absents dans la vue). Chaque bloc est retreci
     * d'un rien : un mur ou un sol qui borde le cadre ne compte pas. Un volume trop grand pour etre parcouru est tenu
     * pour occupe.
     */
    private boolean arrivalObstructed(BackroomsPortalEntity portal, Vec3d cameraPos) {
        PortalFacing arrival = portal.facingDestination();
        Vec3d center = portal.destinationPos();
        double halfWidth = portal.width() / 2.0;
        double halfHeight = portal.height() / 2.0;
        this.clipVolume.set(cameraPos, center, arrival.axisW, arrival.axisH, arrival.normal, halfWidth, halfHeight);
        double spanX = Math.abs(arrival.axisW.x) * halfWidth + Math.abs(arrival.axisH.x) * halfHeight;
        double spanY = Math.abs(arrival.axisW.y) * halfWidth + Math.abs(arrival.axisH.y) * halfHeight;
        double spanZ = Math.abs(arrival.axisW.z) * halfWidth + Math.abs(arrival.axisH.z) * halfHeight;
        int minX = MathHelper.floor(Math.min(cameraPos.x, center.x - spanX));
        int minY = MathHelper.floor(Math.min(cameraPos.y, center.y - spanY));
        int minZ = MathHelper.floor(Math.min(cameraPos.z, center.z - spanZ));
        int maxX = MathHelper.floor(Math.max(cameraPos.x, center.x + spanX));
        int maxY = MathHelper.floor(Math.max(cameraPos.y, center.y + spanY));
        int maxZ = MathHelper.floor(Math.max(cameraPos.z, center.z + spanZ));
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > MAX_OBSTRUCTION_SCAN) {
            return true;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (this.clipVolume.intersectsBeforeFrame(x + BLOCK_INSET, y + BLOCK_INSET, z + BLOCK_INSET,
                            x + 1 - BLOCK_INSET, y + 1 - BLOCK_INSET, z + 1 - BLOCK_INSET)) {
                        BlockState state = this.world.getBlockState(SCAN_POS.set(x, y, z));
                        if (!state.isAir() && !(state.getBlock() instanceof BackroomsPortalBlock)) {
                            return true;
                        }
                    }
                }
            }
        }
        for (Entity entity : this.world.getEntities()) {
            if (entity instanceof BackroomsPortalEntity other && other.centeredAt(center)) {
                continue;
            }
            Box box = entity.getBoundingBox();
            if (this.clipVolume.intersectsBeforeFrame(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Entretien d'une vue dont le cadre est cache derriere des blocs, a la place de son dessin : tout ce que le dessin fait
     * pour les images suivantes et pour la traversee (monde marque comme regarde, pour ses particules ; lumiere ; grille,
     * graphe de culling et sections a reconstruire), rien de ce qu'il dessine. Une vue qui sort de derriere son mur se
     * dessine des sa premiere image comme si elle n'avait jamais cesse. Le monde et le renderer du client sont deja ceux de
     * la vue.
     *
     * @param camera camera de la vue, position et rotation deja ecrites
     * @param cameraPos position de la camera de la vue
     * @param positionMatrix matrice de vue de cette camera
     * @param projection projection du joueur
     * @param portal portail dont c'est la vue : son arrivee ancre la grille
     */
    @SuppressWarnings("try")
    public void keepWarm(MinecraftClient client, Camera camera, Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f projection,
            BackroomsPortalEntity portal) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_HIDDEN)) {
            startRender(PortalRenderState.frameIndex());
            this.owner.markViewed(cameraPos);
            this.world.getChunkManager().getLightingProvider().doLightUpdates();
            ((FrustumAccessor) this.frustum).backrooms$init(positionMatrix, projection);
            this.frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
            this.frustum.coverBoxAroundSetPosition(FRUSTUM_COVER_BOX);
            WorldRendererAccessor accessor = (WorldRendererAccessor) this.renderer;
            if (foreignTerrain(accessor)) {
                setupForeignTerrain(accessor, camera);
            } else {
                placeTerrainOnce(client, accessor, camera, cameraPos, portal.destinationPos());
                accessor.backrooms$updateChunks(camera);
                prebuildArrival(client, accessor, portal);
            }
        }
    }

    /**
     * Dessine la vue. Le frustum de culling suit {@code projection}, celle du joueur ; les dessins suivent
     * {@code drawProjection}, la meme sauf quand un autre mod dessine le terrain ({@code foreign}) : alors une projection
     * a plan proche oblique coupe au plan d'arrivee ce que ses shaders ne couperaient pas, et tout ce qui se dessine dans
     * la vue la partage, pour une seule echelle de profondeur.
     */
    @SuppressWarnings("try")
    private void renderView(
            MinecraftClient client,
            WorldRendererAccessor accessor,
            boolean foreign,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            Matrix4f drawProjection,
            BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            LightmapTextureManager lightmap,
            float tickDelta) {
        this.world.getChunkManager().getLightingProvider().doLightUpdates();
        ((FrustumAccessor) this.frustum).backrooms$init(positionMatrix, projection);
        this.frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        // Comme vanilla, le frustum recule jusqu'a couvrir la cellule de la camera : les sections au bord ne clignotent pas.
        this.frustum.coverBoxAroundSetPosition(FRUSTUM_COVER_BOX);
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_TERRAIN)) {
            if (foreign) {
                setupForeignTerrain(accessor, camera);
            } else {
                setupTerrain(client, accessor, camera, cameraPos, portal.destinationPos(), projection);
                accessor.backrooms$updateChunks(camera);
                prebuildArrival(client, accessor, portal);
            }
        }
        // Ciel de la vue avec son brouillard de ciel, puis brouillard du terrain, comme le rendu principal. Le dome du ciel se
        // dessine avec le shader courant : celui des positions, comme vanilla, et non le dernier pose par le remplissage du
        // cadre, dont l'attribut couleur absent du tampon vaudrait noir.
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        this.skyFogCamera = camera;
        this.skyFogTickDelta = tickDelta;
        this.renderer.renderSky(positionMatrix, drawProjection, tickDelta, camera, this.thickFog, this.skyFog);
        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, Math.max(this.viewDistance, 32.0f), this.thickFog, tickDelta);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        for (int i = 0; i < BLOCK_LAYERS.size(); i++) {
            RenderLayer layer = BLOCK_LAYERS.get(i);
            if (layer == RenderLayer.getTranslucent()) {
                // Meme ordre que vanilla : entities et block entities avant les couches translucides.
                try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_ENTITIES)) {
                    renderEntities(client, camera, cameraPos, positionMatrix, drawProjection, portal, frameCameraPos, tickDelta);
                }
            }
            accessor.backrooms$renderLayer(layer, cameraPos.x, cameraPos.y, cameraPos.z, positionMatrix, drawProjection);
            if (layer == RenderLayer.getSolid() && !foreign) {
                this.coarse.draw(cameraPos, positionMatrix, drawProjection);
            }
        }
        if (drawProjection == projection) {
            renderAfterTranslucent(client, accessor, camera, cameraPos, positionMatrix, drawProjection, lightmap, tickDelta);
        } else {
            // Particules et meteo dessinent avec la projection de RenderSystem : celle de la vue le temps de leur dessin.
            Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
            VertexSorter sorter = RenderSystem.getVertexSorting();
            RenderSystem.setProjectionMatrix(drawProjection, VertexSorter.BY_DISTANCE);
            try {
                renderAfterTranslucent(client, accessor, camera, cameraPos, positionMatrix, drawProjection, lightmap, tickDelta);
            } finally {
                RenderSystem.setProjectionMatrix(previousProjection, sorter);
            }
        }
    }

    /** Meme ordre que vanilla : les particules apres les couches translucides, puis les nuages, puis la meteo. */
    private void renderAfterTranslucent(
            MinecraftClient client,
            WorldRendererAccessor accessor,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f drawProjection,
            LightmapTextureManager lightmap,
            float tickDelta) {
        // Vue allegee : ni particules ni meteo ; les nuages restent.
        if (!this.reduced) {
            renderParticles(client, camera, positionMatrix, lightmap, tickDelta);
        }
        if (client.options.getCloudRenderModeValue() != CloudRenderMode.OFF) {
            renderClouds(client, cameraPos, positionMatrix, drawProjection, tickDelta);
        }
        if (!this.reduced) {
            renderWeather(accessor, cameraPos, positionMatrix, lightmap, tickDelta);
        }
    }

    /**
     * Nuages du monde de la vue depuis sa camera. Un mod de rendu peut les dessiner depuis la camera du joueur
     * ({@code gameRenderer.getCamera()}) plutot que depuis la position recue : sans rien changer, les nuages de la vue se
     * placeraient par rapport au joueur, dans l'autre monde (trop bas, ou vus de l'autre cote de leur couche). La camera
     * du joueur prend la position et le monde de la vue le temps du dessin.
     */
    private void renderClouds(MinecraftClient client, Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f drawProjection, float tickDelta) {
        Camera mainCamera = client.gameRenderer.getCamera();
        CameraAccessor mainAccessor = (CameraAccessor) mainCamera;
        Vec3d mainPos = mainCamera.getPos();
        BlockView mainArea = mainAccessor.backrooms$getArea();
        mainAccessor.backrooms$setPos(cameraPos);
        mainAccessor.backrooms$setArea(this.world);
        try {
            this.renderer.renderClouds(MATRICES, positionMatrix, drawProjection, tickDelta, cameraPos.x, cameraPos.y, cameraPos.z);
        } finally {
            mainAccessor.backrooms$setArea(mainArea);
            mainAccessor.backrooms$setPos(mainPos);
        }
    }

    /**
     * Pluie et neige du monde de la vue autour de sa camera, sous la decoupe de la vue. La chute s'anime sur l'horloge du
     * renderer de la vue, qui tique avec son monde ({@link RemoteWorld#tickClock}).
     */
    private static void renderWeather(
            WorldRendererAccessor accessor,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            LightmapTextureManager lightmap,
            float tickDelta) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(positionMatrix);
        RenderSystem.applyModelViewMatrix();
        try {
            accessor.backrooms$renderWeather(lightmap, tickDelta, cameraPos.x, cameraPos.y, cameraPos.z);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(true);
        }
    }

    private void applySkyFog() {
        BackgroundRenderer.applyFog(this.skyFogCamera, BackgroundRenderer.FogType.FOG_SKY, this.viewDistance, this.thickFog, this.skyFogTickDelta);
    }

    /**
     * Prepare le terrain de la vue. La premiere vue de ce renderer dans l'image place sa grille et fait avancer son
     * graphe de culling ({@link #placeTerrain}) ; les vues suivantes du meme renderer n'y touchent plus : la grille
     * reste ou elle est. Dans tous les cas, les sections dessinees viennent du parcours synchrone propre a la vue.
     */
    private void setupTerrain(MinecraftClient client, WorldRendererAccessor accessor, Camera camera, Vec3d cameraPos, Vec3d anchor,
            Matrix4f projection) {
        placeTerrainOnce(client, accessor, camera, cameraPos, anchor);
        this.sectionWalker.discover(accessor.backrooms$getChunks(), this.world.getChunkManager(), camera.getBlockPos(), this.frustum, this.cone,
                accessor.backrooms$getBuiltChunks());
        // Vue allegee : les sections aux blocs petits a l'ecran passent au terrain simplifie ; une vue pleine reste le rendu
        // sans LOD, sections comprises.
        if (this.reduced) {
            this.coarse.select(accessor.backrooms$getBuiltChunks(), cameraPos, projection.m11(), client.getWindow().getFramebufferHeight());
        } else {
            this.coarse.clearSelection();
        }
    }

    /** Grille et graphe de culling places par la premiere vue de ce renderer dans l'image ; les suivantes donnent leur camera. */
    private void placeTerrainOnce(MinecraftClient client, WorldRendererAccessor accessor, Camera camera, Vec3d cameraPos, Vec3d anchor) {
        int frame = PortalRenderState.frameIndex();
        if (this.terrainFrame != frame) {
            this.terrainFrame = frame;
            placeTerrain(client, accessor, accessor.backrooms$getChunks(), camera, cameraPos, anchor);
        } else {
            this.renderer.getChunkBuilder().setCameraPosition(cameraPos);
        }
    }

    /**
     * Debut de rendu a blanc du renderer, une fois par image : il passe par la tete de {@code WorldRenderer.render}, ou
     * des mods posent l'etat par renderer qu'attendent les methodes de rendu appelees ensuite, et s'y arrete
     * ({@link ViewRenderStart}, {@code MixinWorldRenderStart}). Les parametres sont ceux du rendu du joueur.
     */
    @SuppressWarnings("try")
    private void startRender(int frame) {
        if (this.startedFrame == frame || !ViewRenderStart.hasFrame()) {
            return;
        }
        this.startedFrame = frame;
        ViewRenderStart.setStarting(true);
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_START)) {
            this.renderer.render(ViewRenderStart.tickCounter(), false, ViewRenderStart.camera(), ViewRenderStart.gameRenderer(),
                    ViewRenderStart.lightmap(), ViewRenderStart.positionMatrix(), ViewRenderStart.projection());
        } finally {
            ViewRenderStart.setStarting(false);
        }
    }

    /**
     * Terrain dessine par un autre mod : son {@code setupTerrain} choisit ses sections pour cette camera, une fois par
     * renderer et par image, et son {@code renderLayer} les dessine ensuite. La grille vanilla, absente ou sans portee,
     * n'est pas touchee : la recharger parce que sa distance n'est pas celle des options (c'est lui qui l'impose) ferait
     * tout reconstruire a chaque image. Les evenements de rendu du monde que Fabric declenche dans {@code setupTerrain}
     * partent alors pour la vue, avec le contexte vide d'un renderer qui n'est pas celui du joueur.
     */
    private void setupForeignTerrain(WorldRendererAccessor accessor, Camera camera) {
        int frame = PortalRenderState.frameIndex();
        if (this.terrainFrame != frame) {
            this.terrainFrame = frame;
            // Comme un spectateur : pas d'occlusion depuis l'interieur d'un bloc plein si la camera y tombe.
            accessor.backrooms$setupTerrain(camera, this.frustum, false, true);
        }
    }

    /**
     * {@code true} si un autre mod dessine le terrain de ce renderer : la grille vanilla est absente, ou sans portee (il
     * la cree avec une distance de vue nulle pour qu'elle ne coute rien, la distance du renderer restant celle des options).
     */
    public static boolean foreignTerrain(WorldRendererAccessor accessor) {
        BuiltChunkStorage chunks = accessor.backrooms$getChunks();
        return chunks == null || chunks.getViewDistance() <= 0;
    }

    /**
     * Ce que le {@code setupTerrain} vanilla fait pour le monde du joueur, autour de l'ancrage de la vue plutot que du
     * joueur : la grille de sections se place (centre retenu par {@link #gridCenter}), le chunk builder connait sa
     * camera, et le graphe de culling vanilla, asynchrone, repart de la camera quand elle change de cellule et tient sa
     * liste de sections a jour : il reste chaud pour servir tel quel si ce renderer devient le principal a la
     * traversee. Le {@code setupTerrain} vanilla lui-meme n'est pas appele : il centre sa grille sur le joueur, un mod
     * qui remplace le pipeline de terrain le reecrit (une injection dans une methode reecrite est refusee et le jeu ne
     * demarre pas), et les evenements de rendu du monde que Fabric y declenche ne concernent pas une vue (leur contexte,
     * prepare par le seul rendu du joueur, y serait vide). Les methodes du graphe n'ont pas de nom Yarn.
     */
    private void placeTerrain(MinecraftClient client, WorldRendererAccessor accessor, BuiltChunkStorage chunks, Camera camera, Vec3d cameraPos,
            Vec3d anchor) {
        if (client.options.getClampedViewDistance() != accessor.backrooms$getViewDistance()) {
            this.renderer.reload();
        }
        int centerX = gridCenter(accessor.backrooms$getCameraChunkX(), ChunkSectionPos.getSectionCoord(anchor.x));
        int centerY = ChunkSectionPos.getSectionCoord(anchor.y);
        int centerZ = gridCenter(accessor.backrooms$getCameraChunkZ(), ChunkSectionPos.getSectionCoord(anchor.z));
        if (accessor.backrooms$getCameraChunkX() != centerX || accessor.backrooms$getCameraChunkY() != centerY
                || accessor.backrooms$getCameraChunkZ() != centerZ) {
            accessor.backrooms$setCameraChunkX(centerX);
            accessor.backrooms$setCameraChunkY(centerY);
            accessor.backrooms$setCameraChunkZ(centerZ);
            chunks.updateCameraPosition(ChunkSectionPos.getOffsetPos(centerX, ViewSectionWalker.HALF_SECTION),
                    ChunkSectionPos.getOffsetPos(centerZ, ViewSectionWalker.HALF_SECTION));
        }
        this.renderer.getChunkBuilder().setCameraPosition(cameraPos);
        ChunkRenderingDataPreparer graph = accessor.backrooms$getChunkRenderingDataPreparer();
        double cellX = Math.floor(cameraPos.x / CAMERA_CELL);
        double cellY = Math.floor(cameraPos.y / CAMERA_CELL);
        double cellZ = Math.floor(cameraPos.z / CAMERA_CELL);
        if (cellX != accessor.backrooms$getLastCameraX() || cellY != accessor.backrooms$getLastCameraY() || cellZ != accessor.backrooms$getLastCameraZ()) {
            graph.method_52817();
        }
        accessor.backrooms$setLastCameraX(cellX);
        accessor.backrooms$setLastCameraY(cellY);
        accessor.backrooms$setLastCameraZ(cellZ);
        // Comme un spectateur : pas d'occlusion depuis l'interieur d'un bloc plein si la camera y tombe.
        BlockPos cameraBlock = camera.getBlockPos();
        boolean culling = client.chunkCullingEnabled && !this.world.getBlockState(cameraBlock).isOpaqueFullCube(this.world, cameraBlock);
        graph.method_52834(culling, camera, this.frustum, accessor.backrooms$getBuiltChunks());
        double pitch = Math.floor(camera.getPitch() / 2.0f);
        double yaw = Math.floor(camera.getYaw() / 2.0f);
        if (graph.method_52836() || pitch != accessor.backrooms$getLastCameraPitch() || yaw != accessor.backrooms$getLastCameraYaw()) {
            // Le frustum couvre deja la cellule de la camera : c'est celui que vanilla decalerait avant de l'appliquer.
            accessor.backrooms$applyFrustum(this.frustum);
            accessor.backrooms$setLastCameraPitch(pitch);
            accessor.backrooms$setLastCameraYaw(yaw);
        }
    }

    /**
     * Le joueur approche du cadre d'un portail vers une autre dimension : il peut passer, et ce renderer deviendrait le
     * sien. La vue ne construit que les sections que montre le cadre ; celles qui entourent l'arrivee se construisent ici
     * d'avance, les plus proches d'abord, quelques-unes par image : tout autour sur {@link #PREBUILD_RADIUS} sections (ce
     * que le joueur voit en se retournant), puis devant la sortie jusqu'a sa distance de vue (ce qu'il voit en sortant). En
     * hauteur, de {@link #PREBUILD_HEIGHT} sections sous l'arrivee jusqu'a la plus haute section non vide de la colonne
     * (une colline, une montagne au loin). Sans cela, ce qui entoure le joueur apres la traversee apparaitrait section par
     * section. Une section vide ne dessine rien : elle ne se construit pas d'avance. Une section ne se construit que dans
     * un chunk charge entoure de ses quatre voisins : les chunks d'un monde distant arrivent avec leur lumiere
     * ({@link RemoteWorld#loadChunk}). Le tampon de regions du chunk builder ne s'alloue que s'il y a une section a
     * construire, comme a chaque {@code updateChunks} de vanilla.
     */
    private void prebuildArrival(MinecraftClient client, WorldRendererAccessor accessor, BackroomsPortalEntity portal) {
        ClientPlayerEntity player = client.player;
        BuiltChunkStorage chunks = accessor.backrooms$getChunks();
        ChunkBuilder builder = this.renderer.getChunkBuilder();
        if (player == null || chunks == null || builder == null || portal.getWorld() != player.getWorld()
                || portal.destinationDim().equals(player.getWorld().getRegistryKey())
                || portal.squaredDistanceToFrame(player.getX(), player.getEyeY(), player.getZ()) > PREBUILD_DISTANCE * PREBUILD_DISTANCE) {
            return;
        }
        long now = Util.getMeasuringTimeMs();
        this.lastPrebuildMs = now;
        if (now < this.prebuildRestUntilMs) {
            return;
        }
        Vec3d arrival = portal.destinationPos();
        int centerX = ChunkSectionPos.getSectionCoord(arrival.x);
        int centerY = ChunkSectionPos.getSectionCoord(arrival.y);
        int centerZ = ChunkSectionPos.getSectionCoord(arrival.z);
        Direction out = portal.facingDestination().direction;
        int reach = Math.max(PREBUILD_RADIUS, client.options.getClampedViewDistance());
        ChunkRendererRegionBuilder regions = null;
        int budget = PREBUILD_PER_FRAME;
        for (int ring = 0; ring <= reach && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || ring > PREBUILD_RADIUS && dx * out.getOffsetX() + dz * out.getOffsetZ() < 0
                            || !columnReady(centerX + dx, centerZ + dz)) {
                        continue;
                    }
                    WorldChunk chunk = this.world.getChunkManager().getWorldChunk(centerX + dx, centerZ + dz);
                    int bottom = Math.max(centerY - PREBUILD_HEIGHT, this.world.getBottomSectionCoord());
                    int top = Math.min(Math.max(centerY + PREBUILD_HEIGHT, chunk.sectionIndexToCoord(chunk.getHighestNonEmptySection())),
                            this.world.getTopSectionCoord() - 1);
                    for (int sectionY = bottom; sectionY <= top && budget > 0; sectionY++) {
                        if (chunk.getSection(chunk.sectionCoordToIndex(sectionY)).isEmpty()) {
                            continue;
                        }
                        PREBUILD_CURSOR.set(ChunkSectionPos.getBlockCoord(centerX + dx), ChunkSectionPos.getBlockCoord(sectionY),
                                ChunkSectionPos.getBlockCoord(centerZ + dz));
                        ChunkBuilder.BuiltChunk section = ((BuiltChunkStorageAccessor) chunks).backrooms$getRenderedChunk(PREBUILD_CURSOR);
                        // La grille boucle : la section rangee a cette place peut en couvrir une autre.
                        if (section == null || !section.needsRebuild() || !section.getOrigin().equals(PREBUILD_CURSOR)) {
                            continue;
                        }
                        if (regions == null) {
                            regions = new ChunkRendererRegionBuilder();
                        }
                        section.scheduleRebuild(builder, regions);
                        section.cancelRebuild();
                        budget--;
                    }
                }
            }
        }
        if (budget == PREBUILD_PER_FRAME) {
            // Tout ce qui peut se construire l'est : le parcours se repose un instant.
            this.prebuildRestUntilMs = now + PREBUILD_REST_MS;
        }
    }

    /**
     * {@code true} si ce renderer construit d'avance l'arrivee d'un cadre que le joueur peut passer, ou l'a fait il y a peu
     * ({@link #PREBUILD_HOLD_MS}) : il deviendra peut-etre le sien, et construit alors au rythme de celui du joueur
     * ({@link BuildBuffers}).
     */
    boolean prebuilding() {
        return Util.getMeasuringTimeMs() - this.lastPrebuildMs < PREBUILD_HOLD_MS;
    }

    /** {@code true} si le chunk de cette colonne et ses quatre voisins sont charges : ses sections peuvent se construire. */
    private boolean columnReady(int chunkX, int chunkZ) {
        ClientChunkManager manager = this.world.getChunkManager();
        return manager.getWorldChunk(chunkX, chunkZ) != null && manager.getWorldChunk(chunkX - 1, chunkZ) != null
                && manager.getWorldChunk(chunkX + 1, chunkZ) != null && manager.getWorldChunk(chunkX, chunkZ - 1) != null
                && manager.getWorldChunk(chunkX, chunkZ + 1) != null;
    }

    /**
     * Centre de grille retenu pour un ancrage : l'ancien tant que l'ancrage en reste a {@link #GRID_MARGIN_CHUNKS} pres,
     * l'ancrage sinon (et toujours lui pour une grille jamais centree).
     */
    static int gridCenter(int current, int anchor) {
        return current != Integer.MIN_VALUE && Math.abs(anchor - current) <= GRID_MARGIN_CHUNKS ? current : anchor;
    }

    private void renderEntities(
            MinecraftClient client,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            float tickDelta) {
        EntityRenderDispatcher entityDispatcher = client.getEntityRenderDispatcher();
        BlockEntityRenderDispatcher blockEntityDispatcher = client.getBlockEntityRenderDispatcher();
        entityDispatcher.configure(this.world, camera, null);
        blockEntityDispatcher.configure(this.world, camera, null);
        PortalStencilRenderer.applyEntityLighting(this.world);
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(positionMatrix);
        RenderSystem.applyModelViewMatrix();
        WorldRendererAccessor accessor = (WorldRendererAccessor) this.renderer;
        VertexConsumerProvider.Immediate immediate = ENTITY_BUFFERS;
        try {
            if (this.reduced) {
                renderFrozen(accessor, blockEntityDispatcher, portal, cameraPos, positionMatrix, projection, tickDelta);
            } else {
                renderLiveEntities(entityDispatcher, accessor, portal, cameraPos, frameCameraPos, tickDelta, immediate);
            }
            if (frameCameraPos != null) {
                // Ce qui entre dans le cadre de ce cote : la partie deja passee, ici, au-dela du plan d'arrivee.
                Entity hidden = client.gameRenderer.getCamera().isThirdPerson() ? null : client.getCameraEntity();
                StraddlingEntityRenderer.renderEnteringParts(client, accessor, portal, frameCameraPos, hidden, tickDelta, immediate);
            } else {
                StraddlingEntityRenderer.renderWorldParts(client, this.world, cameraPos, positionMatrix, tickDelta);
            }
            immediate.drawCurrentLayer();
            if (!this.reduced) {
                renderBlockEntities(accessor, blockEntityDispatcher, cameraPos, tickDelta, immediate);
            }
            renderBlockDamage(client, accessor, cameraPos);
            renderTargetOutline(client, cameraPos, immediate);
            immediate.draw();
            renderStraddling(accessor, cameraPos, tickDelta, immediate);
            EFFECT_BUFFERS.draw();
        } finally {
            STRADDLING.clear();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, sorter);
            // Etat vanilla par defaut, que les couches de terrain supposent sans le poser : faces arriere eliminees.
            RenderSystem.enableCull();
            // Un renderer d'entity qui a leve une exception laisse la pile sale : elle est partagee entre les vues.
            while (!MATRICES.isEmpty()) {
                MATRICES.pop();
            }
        }
    }

    /** Entities du monde de la vue, une par une, dans le frustum et le volume visible a travers le cadre. */
    private void renderLiveEntities(
            EntityRenderDispatcher entityDispatcher,
            WorldRendererAccessor accessor,
            BackroomsPortalEntity portal,
            Vec3d cameraPos,
            @Nullable Vec3d frameCameraPos,
            float tickDelta,
            VertexConsumerProvider.Immediate immediate) {
        for (Entity entity : this.world.getEntities()) {
            if (!entityDispatcher.shouldRender(entity, this.frustum, cameraPos.x, cameraPos.y, cameraPos.z)
                    || !this.cone.intersects(entity.getVisibilityBoundingBox())) {
                continue;
            }
            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }
            // Vue qui couvre l'ecran : ce qui est coupe par un portail de ce monde s'y dessine en deux moities, plus bas.
            if (frameCameraPos == null && StraddlingEntityRenderer.drawnInParts(entity, cameraPos.x, cameraPos.y, cameraPos.z, tickDelta)) {
                continue;
            }
            // Vue imbriquee : ce qui sort de son arrivee n'a nulle part ou dessiner la partie encore derriere, coupee au
            // plan, son modele s'ouvrirait ; il se dessine entier, dans le cadre.
            if (PortalClipping.depth() >= NESTED_DEPTH && StraddlingEntityRenderer.straddlesArrival(portal, entity, tickDelta)) {
                STRADDLING.add(entity);
                continue;
            }
            accessor.backrooms$renderEntity(entity, cameraPos.x, cameraPos.y, cameraPos.z, tickDelta, MATRICES, immediate);
        }
    }

    /**
     * Les entities engagees dans le cadre d'arrivee d'une vue imbriquee, entieres : la partie encore devant le plan reste
     * dans le cadre (le stencil l'y limite), la ou on la voit sortir du portail.
     */
    private static void renderStraddling(WorldRendererAccessor accessor, Vec3d cameraPos, float tickDelta, VertexConsumerProvider.Immediate immediate) {
        if (STRADDLING.isEmpty()) {
            return;
        }
        PortalClipping.suspend();
        try {
            for (int i = 0; i < STRADDLING.size(); i++) {
                accessor.backrooms$renderEntity(STRADDLING.get(i), cameraPos.x, cameraPos.y, cameraPos.z, tickDelta, MATRICES, immediate);
            }
            immediate.draw();
        } finally {
            PortalClipping.resume();
        }
    }

    /** Particules du monde de la vue, depuis sa camera : la matrice de vue est empilee comme vanilla le fait pour les siennes. */
    private void renderParticles(MinecraftClient client, Camera camera, Matrix4f positionMatrix, LightmapTextureManager lightmap, float tickDelta) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(positionMatrix);
        RenderSystem.applyModelViewMatrix();
        try {
            this.owner.renderParticles(client, lightmap, camera, tickDelta);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
        }
    }

    /**
     * Vue allegee : entities et block entities de la derniere prise ({@link FrozenEntityLayers}), reprise quand elle a
     * vieilli ou que la camera s'est deplacee. La prise garde les entities du volume que montre le cadre, elargi d'un bloc,
     * sans frustum : la camera tourne entre deux prises, et la decoupe au plan d'arrivee comme le stencil limitent le
     * dessin au cadre. Les block entities sont celles du dessin vivant.
     */
    private void renderFrozen(
            WorldRendererAccessor accessor,
            BlockEntityRenderDispatcher blockEntityDispatcher,
            BackroomsPortalEntity portal,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            float tickDelta) {
        FrozenEntityLayers layers = frozenFor(portal);
        long time = this.world.getTime();
        if (layers.stale(time, cameraPos)) {
            PortalFacing arrival = portal.facingDestination();
            this.frozenCone.set(cameraPos, portal.destinationPos(), arrival.axisW, arrival.axisH, arrival.normal,
                    portal.width() / 2.0 + FROZEN_CONE_MARGIN, portal.height() / 2.0 + FROZEN_CONE_MARGIN);
            layers.begin(cameraPos);
            for (Entity entity : this.world.getEntities()) {
                if (!entity.shouldRender(cameraPos.x, cameraPos.y, cameraPos.z) || !this.frozenCone.intersects(entity.getVisibilityBoundingBox())) {
                    continue;
                }
                if (entity.age == 0) {
                    entity.lastRenderX = entity.getX();
                    entity.lastRenderY = entity.getY();
                    entity.lastRenderZ = entity.getZ();
                }
                accessor.backrooms$renderEntity(entity, cameraPos.x, cameraPos.y, cameraPos.z, tickDelta, MATRICES, layers);
            }
            renderBlockEntities(accessor, blockEntityDispatcher, cameraPos, tickDelta, layers);
            layers.end(time);
        }
        layers.draw(cameraPos, positionMatrix, projection);
    }

    /**
     * Entities figees de la vue de ce portail, creees au premier dessin allege ; au passage, celles d'un portail retire ou
     * qui ne s'est plus dessine allege depuis {@value #FROZEN_UNUSED_FRAMES} images sont rendues.
     */
    private FrozenEntityLayers frozenFor(BackroomsPortalEntity portal) {
        int frame = PortalRenderState.frameIndex();
        FrozenEntityLayers found = null;
        for (int i = this.frozen.size() - 1; i >= 0; i--) {
            FrozenEntityLayers layers = this.frozen.get(i);
            if (layers.portal == portal) {
                found = layers;
            } else if (layers.portal.isRemoved() || frame - layers.usedFrame > FROZEN_UNUSED_FRAMES) {
                layers.close();
                this.frozen.remove(i);
            }
        }
        if (found == null) {
            found = new FrozenEntityLayers(portal);
            this.frozen.add(found);
        }
        found.usedFrame = frame;
        return found;
    }

    /** La vue de ce portail repasse en pleine qualite : ses entities figees sont rendues. */
    private void dropFrozen(BackroomsPortalEntity portal) {
        for (int i = 0; i < this.frozen.size(); i++) {
            if (this.frozen.get(i).portal == portal) {
                this.frozen.remove(i).close();
                return;
            }
        }
    }

    /**
     * {@code true} si chaque section que la derniere vue a dessinee au rendu vanilla est a jour ({@link SectionGenerations}) :
     * une image figee prise de cette vue est complete. Toujours vrai quand un autre mod dessine le terrain : rien ne dit
     * alors ce qui est affiche.
     */
    public boolean sectionsCurrent() {
        ObjectArrayList<ChunkBuilder.BuiltChunk> sections = ((WorldRendererAccessor) this.renderer).backrooms$getBuiltChunks();
        for (int i = 0; i < sections.size(); i++) {
            if (!((SectionGenerations) sections.get(i)).backrooms$current()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rend les tampons du GPU des vues allegees (entities figees, terrain simplifie) : le monde de ce renderer est jete, ou
     * devient le monde principal.
     */
    void releaseReduced() {
        for (int i = 0; i < this.frozen.size(); i++) {
            this.frozen.get(i).close();
        }
        this.frozen.clear();
        this.coarse.close();
    }

    private void renderBlockEntities(
            WorldRendererAccessor accessor,
            BlockEntityRenderDispatcher dispatcher,
            Vec3d cameraPos,
            float tickDelta,
            VertexConsumerProvider immediate) {
        ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks = accessor.backrooms$getBuiltChunks();
        for (int i = 0; i < builtChunks.size(); i++) {
            List<BlockEntity> blockEntities = builtChunks.get(i).getData().getBlockEntities();
            for (int j = 0; j < blockEntities.size(); j++) {
                renderBlockEntity(dispatcher, blockEntities.get(j), cameraPos, tickDelta, immediate);
            }
        }
        Set<BlockEntity> noCulling = accessor.backrooms$getNoCullingBlockEntities();
        synchronized (noCulling) {
            for (BlockEntity blockEntity : noCulling) {
                renderBlockEntity(dispatcher, blockEntity, cameraPos, tickDelta, immediate);
            }
        }
    }

    private static void renderBlockEntity(
            BlockEntityRenderDispatcher dispatcher,
            BlockEntity blockEntity,
            Vec3d cameraPos,
            float tickDelta,
            VertexConsumerProvider vertexConsumers) {
        BlockPos pos = blockEntity.getPos();
        MATRICES.push();
        MATRICES.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
        dispatcher.render(blockEntity, tickDelta, MATRICES, vertexConsumers);
        MATRICES.pop();
    }

    /** Fissures des blocs en cours de minage a travers un portail (voir {@code WorldRenderer.setBlockBreakingInfo}). */
    private void renderBlockDamage(MinecraftClient client, WorldRendererAccessor accessor, Vec3d cameraPos) {
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> progressions = accessor.backrooms$getBlockBreakingProgressions();
        if (progressions.isEmpty()) {
            return;
        }
        for (Long2ObjectMap.Entry<SortedSet<BlockBreakingInfo>> entry : progressions.long2ObjectEntrySet()) {
            SortedSet<BlockBreakingInfo> infos = entry.getValue();
            if (infos == null || infos.isEmpty()) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            if (pos.getSquaredDistance(cameraPos) > BLOCK_DAMAGE_RANGE_SQUARED) {
                continue;
            }
            MATRICES.push();
            MATRICES.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            VertexConsumer overlay = new OverlayVertexConsumer(
                    EFFECT_BUFFERS.getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(infos.last().getStage())),
                    MATRICES.peek(),
                    1.0f);
            client.getBlockRenderManager().renderDamage(this.world.getBlockState(pos), pos, this.world, MATRICES, overlay);
            MATRICES.pop();
        }
    }

    /** Contour du bloc vise a travers un portail, quand le regard ressort dans cette vue. */
    private void renderTargetOutline(MinecraftClient client, Vec3d cameraPos, VertexConsumerProvider.Immediate immediate) {
        BlockHitResult hit = RemoteTargeting.blockHit();
        if (hit == null || RemoteTargeting.view() != this.owner || client.player == null || !PortalRenderState.blockOutlineAllowed()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = this.world.getBlockState(pos);
        if (state.isAir() || !this.world.getWorldBorder().contains(pos)) {
            return;
        }
        // Pas drawBlockOutline : Fabric API l'intercepte avec un contexte de rendu qui n'existe que dans WorldRenderer.render.
        WorldRenderer.drawShapeOutline(MATRICES, immediate.getBuffer(RenderLayer.getLines()),
                state.getOutlineShape(this.world, pos, ShapeContext.of(client.player)),
                pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z, 0.0f, 0.0f, 0.0f, 0.4f, false);
    }
}
