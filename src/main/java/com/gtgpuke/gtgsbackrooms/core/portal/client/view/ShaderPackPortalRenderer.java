package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.StraddlingEntityRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookPost;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;
import com.gtgpuke.gtgsbackrooms.mixin.client.CameraAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.MinecraftClientAccessor;

/**
 * Vues des portails sous un pack de shaders ({@link ShaderPackState}) : apres l'image du monde du joueur, que le mod de
 * shaders a composee et ecrite a l'ecran, plutot qu'au milieu de son rendu. Un pack dessine le monde dans son propre
 * pipeline (geometrie dans ses tampons, eclairage differe, composition, passe finale ecrite dans la texture de couleur
 * de l'ecran sans test de stencil) : une vue dessinee au milieu n'y serait qu'une surface de plus, eclairee comme le
 * cadre. Chaque vue est donc un rendu complet de son renderer ({@code WorldRenderer.render},
 * {@code RemoteViewRenderer.renderWhole}), ou le mod deroule tout son pipeline pour la dimension de la vue, avec la
 * camera de la vue (la camera du joueur prend sa position le temps du rendu : le mod y lit la position de camera de ses
 * shaders) et, a travers un cadre, la projection a plan proche oblique ({@link ObliqueProjection}) qui coupe au plan
 * d'arrivee.
 *
 * <p>Le stencil garde l'image du joueur : chaque cadre visible y prend une valeur a lui la ou il passe le test de
 * profondeur de l'image (un mur devant, la main, une surface translucide le cachent), puis, cadre par cadre, sa zone
 * repart du plan lointain, l'ecran est garde dans une texture, la vue est rendue, et l'image gardee est remise partout
 * sauf dans le cadre : la passe finale du pack a ecrit la vue sur tout l'ecran. Un niveau de portails imbriques suit
 * le meme chemin dans la vue (bit haut du stencil, seconde image gardee). Les entities a cheval sur un portail se
 * dessinent en deux moities, rognees sur le processeur. Un cadre derriere l'eau ou le verre n'est pas dessine.
 */
public final class ShaderPackPortalRenderer {

    /** Valeur du stencil hors de tout cadre ; le premier cadre visible prend la suivante, et ainsi de suite (32 cadres au plus). */
    private static final int NO_FRAME = 0;
    private static final int FULL_MASK = 0xFF;
    /** Bit du stencil d'un cadre imbrique, pose sur la valeur du cadre qui le contient (au-dessus des 32 valeurs de cadre). */
    private static final int NESTED_BIT = 0x80;
    /** Niveaux d'image gardee : la vue d'un cadre, puis la vue imbriquee. */
    private static final int OUTER = 0;
    private static final int NESTED = 1;
    private static final Matrix4f VIEW_POSITION = new Matrix4f();
    private static final Matrix4f NESTED_POSITION = new Matrix4f();
    /**
     * Ce que la matrice de vue du joueur ajoute, a cette image, a la seule rotation de sa camera : le mod de shaders y
     * porte le balancement de la vue en marchant, que vanilla met dans la projection (sa projection n'en a plus rien), et
     * tout ce qu'il y ajoute d'autre. Les vues le prennent aussi, sans quoi l'autre cote resterait immobile dans un cadre
     * qui balance. L'identite quand la matrice de vue n'est que la rotation.
     */
    private static final Matrix4f VIEW_EXTRA = new Matrix4f();
    /** Projection avec laquelle la vue en cours a ete dessinee, copiee : le renderer de vue reutilise la sienne pour la vue imbriquee. */
    private static final Matrix4f VIEW_DRAW_PROJECTION = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** Portails imbriques visibles dans la vue en cours, reutilises d'une image a l'autre. */
    private static final List<BackroomsPortalEntity> NESTED_PORTALS = new ArrayList<>();
    /** Images de l'ecran gardees le temps d'une vue (textures GL a la taille de l'ecran), remises ensuite autour du cadre. */
    private static final int[] SAVED_IMAGES = new int[2];
    /** Rotation complete de la camera du joueur, gardee le temps qu'elle prenne celle d'une vue. */
    private static final Camera SAVED_MAIN_CAMERA = new Camera();
    private static int savedWidth;
    private static int savedHeight;
    /** Une vue limitee a un cadre est en cours : son rendu complet ne doit pas effacer l'ecran, deja rendu autour du cadre. */
    private static boolean limitedToFrame;
    /** Vue en cours de rendu complet, pour les moities d'entities qu'elle dessine ({@link #renderViewParts}). */
    private static @Nullable RemoteWorld drawnView;
    private static @Nullable BackroomsPortalEntity drawnFrame;
    private static @Nullable Vec3d drawnFrameCamera;
    private static @Nullable Entity drawnHidden;
    private static @Nullable Vec3d drawnCameraPos;
    private static Matrix4f drawnPosition = VIEW_POSITION;

    private ShaderPackPortalRenderer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} pendant une vue limitee a un cadre : {@code WorldRenderer.render} y est appele en entier et son
     * effacement de l'ecran (couleur et profondeur) est passe, l'image du joueur etant deja la et la zone du cadre deja
     * au plan lointain. Le mod de shaders n'efface que ses propres tampons de couleur.
     */
    public static boolean skipsWorldClear() {
        return limitedToFrame;
    }

    /** {@code true} pendant le rendu complet d'une vue qui couvre l'ecran : ses entities coupees s'y dessinent en moities. */
    public static boolean coversScreen() {
        return drawnView != null && drawnFrame == null;
    }

    /**
     * Pendant le rendu complet d'une vue, au point ou vanilla a fini ses entities : les moities d'entities a cheval
     * qu'elle montre ({@code StraddlingEntityRenderer.renderPackViewParts}). Rien hors d'une vue.
     */
    public static void renderViewParts(float tickDelta) {
        if (drawnView != null && drawnCameraPos != null) {
            StraddlingEntityRenderer.renderPackViewParts(MinecraftClient.getInstance(), drawnFrame, drawnFrameCamera, drawnHidden,
                    drawnView.world(), drawnCameraPos, drawnPosition, tickDelta);
        }
    }

    /**
     * Les portails visibles, du plus proche au plus lointain, chacun par le rendu complet de son renderer sous le
     * stencil de son cadre. La pile de matrices de vue est a l'identite ; l'appelant rend ensuite l'etat de rendu du
     * monde du joueur.
     */
    static void render(
            MinecraftClient client,
            List<BackroomsPortalEntity> portals,
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f positionMatrix,
            Matrix4f projection) {
        captureViewExtra(camera, positionMatrix);
        Framebuffer framebuffer = client.getFramebuffer();
        framebuffer.beginWrite(false);
        Vec3d cameraPos = camera.getPos();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClearStencil(NO_FRAME);
        // Le bit de la main, pose pendant son dessin, reste : aucun cadre ne s'y marque.
        GL11.glStencilMask(FULL_MASK & ~HandMask.BIT);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        GL11.glStencilMask(FULL_MASK);
        try {
            markFrames(portals, cameraPos, positionMatrix);
            for (int i = 0; i < portals.size(); i++) {
                renderPortal(client, portals.get(i), NO_FRAME + 1 + i, tickCounter, camera, cameraPos, positionMatrix, projection, lightmap);
            }
        } finally {
            GL11.glStencilFunc(GL11.GL_ALWAYS, NO_FRAME, FULL_MASK);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    /**
     * La camera a la troisieme personne est passee derriere un portail : tout l'ecran est la vue de l'autre cote, par le
     * rendu complet de son renderer, effacement de l'ecran compris, par-dessus l'image du monde du joueur ; puis les
     * portails que voit cette camera, dont celui que le joueur vient de franchir, ou il se voit.
     */
    static void renderCovering(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            RemoteWorld view,
            Vec3d remoteCameraPos,
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f positionMatrix,
            Matrix4f projection) {
        captureViewExtra(camera, positionMatrix);
        Camera remoteCamera = PortalRenderState.remoteCamera();
        Vec2f remoteLook = PortalStencilRenderer.aimCamera(remoteCamera, remoteCameraPos, portal, camera, VIEW_POSITION);
        VIEW_POSITION.mulLocal(VIEW_EXTRA);
        client.getFramebuffer().beginWrite(false);
        Matrix4f drawn = renderView(client, view, tickCounter, camera, remoteCamera, remoteCameraPos, VIEW_POSITION, projection, lightmap, portal, null,
                false);
        if (drawn == null) {
            return;
        }
        // La vue couvre l'ecran : le look de sa dimension sur tout l'ecran, avant ses portails imbriques.
        LookPost.renderUnderShaderPackView(client, view.world().getRegistryKey(), LookPost.NO_FRAME);
        // La passe finale du pack a pu laisser son framebuffer lie : le stencil est celui de l'ecran.
        client.getFramebuffer().beginWrite(false);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glClearStencil(NO_FRAME);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        try {
            renderNested(client, null, view, remoteCameraPos, remoteLook, drawn, NO_FRAME, tickCounter, camera, lightmap, projection);
        } finally {
            GL11.glStencilFunc(GL11.GL_ALWAYS, NO_FRAME, FULL_MASK);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    /**
     * Garde ce que la matrice de vue du joueur ajoute a la rotation de sa camera ({@link #VIEW_EXTRA}) : vanilla la
     * construit depuis la seule rotation de la camera.
     */
    private static void captureViewExtra(Camera camera, Matrix4f positionMatrix) {
        VIEW_EXTRA.set(positionMatrix).rotate(camera.getRotation());
    }

    /**
     * Chaque cadre prend sa valeur dans le stencil la ou il passe le test de profondeur de l'image du joueur, puis y
     * ecrit sa propre profondeur : un cadre plus lointain derriere lui n'y passe plus, un cadre plus proche devant lui
     * le recouvre, quel que soit l'ordre. La main du joueur ({@link HandMask}) n'est recouverte par aucun.
     */
    private static void markFrames(List<BackroomsPortalEntity> portals, Vec3d cameraPos, Matrix4f positionMatrix) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            int value = NO_FRAME + 1 + i;
            // Compare le seul bit de la main : la valeur d'un cadre ne l'a pas.
            GL11.glStencilFunc(GL11.GL_EQUAL, value, HandMask.BIT);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            drawAreaMask(portal, cameraPos, positionMatrix);
            GL11.glStencilFunc(GL11.GL_EQUAL, value, FULL_MASK);
            FrameAreaRenderer.writeDepth(portal, cameraPos, positionMatrix, false);
        }
    }

    /** Zone d'un cadre, test de profondeur sans ecriture, ni couleur ni profondeur : seul le stencil change. */
    private static void drawAreaMask(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        try {
            FrameAreaRenderer.mark(portal, cameraPos, positionMatrix);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        }
    }

    /** Un portail : la zone de son cadre repart du plan lointain, l'ecran est garde, la vue rendue, l'ecran remis autour. */
    private static void renderPortal(
            MinecraftClient client,
            BackroomsPortalEntity portal,
            int value,
            RenderTickCounter tickCounter,
            Camera camera,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            Matrix4f projection,
            LightmapTextureManager lightmap) {
        GL11.glStencilFunc(GL11.GL_EQUAL, value, FULL_MASK);
        RemoteWorld view = RemoteWorldRegistry.viewOf(portal.destinationDim());
        if (view == null) {
            // Destination pas encore annoncee par le serveur : cadre sombre.
            FrameAreaRenderer.fill(portal, cameraPos, positionMatrix, 0.0f, 0.0f, 0.0f);
            return;
        }
        Vec3d remoteCameraPos = portal.toDestination(cameraPos);
        Camera remoteCamera = PortalRenderState.remoteCamera();
        Vec2f remoteLook = PortalStencilRenderer.aimCamera(remoteCamera, remoteCameraPos, portal, camera, VIEW_POSITION);
        VIEW_POSITION.mulLocal(VIEW_EXTRA);
        Framebuffer framebuffer = client.getFramebuffer();
        saveImage(framebuffer, OUTER);
        FrameAreaRenderer.writeDepth(portal, cameraPos, positionMatrix, true);
        limitedToFrame = true;
        try {
            Matrix4f drawn = renderView(client, view, tickCounter, camera, remoteCamera, remoteCameraPos, VIEW_POSITION, projection, lightmap, portal,
                    cameraPos, PortalStencilRenderer.reducedDetail(portal, cameraPos, projection));
            if (drawn == null) {
                FrameAreaRenderer.fill(portal, cameraPos, positionMatrix, 0.0f, 0.0f, 0.0f);
            } else {
                // Le look de la dimension vue, sur les pixels du cadre, avant les vues imbriquees qui remplacent les leurs.
                LookPost.renderUnderShaderPackView(client, view.world().getRegistryKey(), value);
                renderNested(client, portal, view, remoteCameraPos, remoteLook, drawn, value, tickCounter, camera, lightmap, projection);
            }
        } finally {
            limitedToFrame = false;
            restoreImage(framebuffer, OUTER, value);
        }
    }

    /**
     * Un niveau de plus : les portails visibles dans la vue qui vient d'etre dessinee, depuis sa camera et a travers son
     * cadre, chacun par le rendu complet de son renderer. La zone d'un cadre imbrique prend le bit haut du stencil la ou
     * elle passe le test de profondeur de la vue (dessinee avec la projection de la vue, celle de sa profondeur), la
     * vue imbriquee se rend sous ce stencil, l'image de la vue est remise autour, puis la zone reprend la profondeur du
     * cadre et perd le bit : un cadre imbrique derriere lui n'y passe plus.
     *
     * @param outer portail dont c'est la vue ; {@code null} quand la vue couvre l'ecran : tous les portails qu'y voit
     *              sa camera comptent, dont celui que le joueur vient de franchir
     * @param viewDrawProjection projection avec laquelle la vue a ete dessinee
     * @param value valeur du stencil de la vue (zero quand elle couvre l'ecran)
     */
    private static void renderNested(
            MinecraftClient client,
            @Nullable BackroomsPortalEntity outer,
            RemoteWorld view,
            Vec3d remoteCameraPos,
            Vec2f remoteLook,
            Matrix4f viewDrawProjection,
            int value,
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f projection) {
        PortalStencilRenderer.collectNested(NESTED_PORTALS, outer, view, remoteCameraPos, remoteLook, VIEW_POSITION, projection);
        if (NESTED_PORTALS.isEmpty()) {
            return;
        }
        Framebuffer framebuffer = client.getFramebuffer();
        framebuffer.beginWrite(false);
        int nestedValue = value | NESTED_BIT;
        VIEW_DRAW_PROJECTION.set(viewDrawProjection);
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        try {
            for (int i = 0; i < NESTED_PORTALS.size(); i++) {
                BackroomsPortalEntity inner = NESTED_PORTALS.get(i);
                RenderSystem.setProjectionMatrix(VIEW_DRAW_PROJECTION, VertexSorter.BY_DISTANCE);
                markNested(inner, remoteCameraPos, value);
                try {
                    renderNestedPortal(client, inner, nestedValue, remoteCameraPos, tickCounter, camera, lightmap, projection, framebuffer);
                } finally {
                    RenderSystem.setProjectionMatrix(VIEW_DRAW_PROJECTION, VertexSorter.BY_DISTANCE);
                    unmarkNested(inner, remoteCameraPos, nestedValue, value);
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, sorter);
            NESTED_PORTALS.clear();
        }
    }

    private static void renderNestedPortal(
            MinecraftClient client,
            BackroomsPortalEntity inner,
            int nestedValue,
            Vec3d remoteCameraPos,
            RenderTickCounter tickCounter,
            Camera camera,
            LightmapTextureManager lightmap,
            Matrix4f projection,
            Framebuffer framebuffer) {
        GL11.glStencilFunc(GL11.GL_EQUAL, nestedValue, FULL_MASK);
        RemoteWorld innerView = RemoteWorldRegistry.viewOf(inner.destinationDim());
        if (innerView == null || !PortalStencilRenderer.hasChunkAt(innerView, inner.destinationPos())) {
            // Pas de donnees pour ce que ce portail montre : cadre sombre, comme un portail trop profond.
            FrameAreaRenderer.fill(inner, remoteCameraPos, VIEW_POSITION, 0.0f, 0.0f, 0.0f);
            return;
        }
        Vec3d innerCameraPos = inner.toDestination(remoteCameraPos);
        Camera nestedCamera = PortalRenderState.nestedCamera();
        PortalStencilRenderer.aimCamera(nestedCamera, innerCameraPos, inner, PortalRenderState.remoteCamera(), NESTED_POSITION);
        NESTED_POSITION.mulLocal(VIEW_EXTRA);
        saveImage(framebuffer, NESTED);
        FrameAreaRenderer.writeDepth(inner, remoteCameraPos, VIEW_POSITION, true);
        limitedToFrame = true;
        try {
            if (renderView(client, innerView, tickCounter, camera, nestedCamera, innerCameraPos, NESTED_POSITION, projection, lightmap, inner,
                    remoteCameraPos, PortalStencilRenderer.reducedDetail(inner, remoteCameraPos, projection)) == null) {
                FrameAreaRenderer.fill(inner, remoteCameraPos, VIEW_POSITION, 0.0f, 0.0f, 0.0f);
            } else {
                LookPost.renderUnderShaderPackView(client, innerView.world().getRegistryKey(), nestedValue);
            }
        } finally {
            limitedToFrame = false;
            restoreImage(framebuffer, NESTED, nestedValue);
        }
    }

    /** La zone d'un cadre imbrique prend le bit haut la ou le stencil vaut {@code value} et ou elle passe la profondeur de la vue. */
    private static void markNested(BackroomsPortalEntity inner, Vec3d remoteCameraPos, int value) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glStencilFunc(GL11.GL_EQUAL, value, FULL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
        GL11.glStencilMask(NESTED_BIT);
        try {
            drawAreaMask(inner, remoteCameraPos, VIEW_POSITION);
        } finally {
            GL11.glStencilMask(FULL_MASK);
        }
    }

    /** La zone reprend la vraie profondeur du cadre imbrique et perd le bit haut : le stencil y revaut {@code value}. */
    private static void unmarkNested(BackroomsPortalEntity inner, Vec3d remoteCameraPos, int nestedValue, int value) {
        GL11.glStencilFunc(GL11.GL_EQUAL, nestedValue, FULL_MASK);
        // La vue imbriquee a laisse sa profondeur, prise sous une autre projection : plan lointain d'abord, puis le cadre.
        FrameAreaRenderer.writeDepth(inner, remoteCameraPos, VIEW_POSITION, true);
        FrameAreaRenderer.writeDepth(inner, remoteCameraPos, VIEW_POSITION, false);
        RenderSystem.disableDepthTest();
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_ZERO);
        GL11.glStencilMask(NESTED_BIT);
        try {
            drawAreaMask(inner, remoteCameraPos, VIEW_POSITION);
        } finally {
            GL11.glStencilMask(FULL_MASK);
            RenderSystem.enableDepthTest();
        }
        GL11.glStencilFunc(GL11.GL_EQUAL, value, FULL_MASK);
    }

    /**
     * Rendu complet du renderer de la vue avec le monde, le renderer, la lightmap, la camera et l'entity camera du
     * client remplaces par ceux de la vue, rendus ensuite ; une exception coupe le rendu distant pour la session.
     *
     * @param viewPosition matrice de vue de la camera de la vue
     * @param portal portail dont c'est la vue : son arrivee donne le plan de decoupe et l'oeil de la vue
     * @param frameCameraPos camera qui regarde le cadre, dans le monde du portail, pour une vue a travers le cadre
     *                       (projection coupee au plan d'arrivee) ; {@code null} quand la vue couvre l'ecran (troisieme
     *                       personne)
     * @return la projection avec laquelle la vue a ete dessinee, ou {@code null} si elle a echoue
     */
    private static @Nullable Matrix4f renderView(
            MinecraftClient client,
            RemoteWorld view,
            RenderTickCounter tickCounter,
            Camera camera,
            Camera viewCamera,
            Vec3d viewCameraPos,
            Matrix4f viewPosition,
            Matrix4f projection,
            LightmapTextureManager lightmap,
            BackroomsPortalEntity portal,
            @Nullable Vec3d frameCameraPos,
            boolean reduced) {
        MinecraftClientAccessor clientAccessor = (MinecraftClientAccessor) client;
        CameraAccessor mainCamera = (CameraAccessor) camera;
        CameraAccessor remoteCamera = (CameraAccessor) viewCamera;
        ClientWorld mainWorld = client.world;
        WorldRenderer mainRenderer = client.worldRenderer;
        Entity mainCameraEntity = client.cameraEntity;
        Vec3d mainPos = camera.getPos();
        float mainYaw = camera.getYaw();
        float mainPitch = camera.getPitch();
        BackroomsPortalEntity frame = frameCameraPos != null ? portal : null;
        // Le joueur a la premiere personne n'est jamais dessine, ni entier ni en moities : capte avant que l'entity camera change.
        Entity hidden = camera.isThirdPerson() ? null : mainCameraEntity;
        // Une vue secondaire montre le monde du joueur : sa lightmap est deja celle des shaders.
        boolean ownLightmap = view.world() != mainWorld;
        boolean looked = false;
        try {
            client.world = view.world();
            clientAccessor.backrooms$setWorldRenderer(view.renderer());
            // Le look de la dimension vue, pour chaque shader vanilla que le rendu complet active.
            LookState.push(LookProfiles.of(view.world().getRegistryKey()), viewCameraPos.x, viewCameraPos.y, viewCameraPos.z,
                    CueClock.tick(view.world().getTime()));
            // Sous un pack, la vue compte pour tout l'ecran au plafond de clignotement : prudent, son rectangle n'est pas tenu ici.
            CueTracker.viewSeen(view.world().getRegistryKey().getValue(), 1.0f);
            looked = true;
            // Le mod de shaders lit la lumiere et la couleur du ciel a l'entity camera, dans le monde courant : le portail
            // d'arrivee, dans le monde de la vue, tient ce role (le joueur y serait a des coordonnees quelconques).
            Entity arrival = arrivalEntity(view, portal);
            if (arrival != null) {
                client.cameraEntity = arrival;
            }
            // Le mod de shaders lit la camera du joueur (position de camera, oeil dans l'eau) : elle prend celle de la vue.
            mainCamera.backrooms$setPos(viewCameraPos);
            // Rotation complete du joueur gardee : roulis d'une traversee compris, qu'un lacet et un tangage ne rendraient pas.
            PortalViewTransform.copyRotation(camera, SAVED_MAIN_CAMERA);
            mainCamera.backrooms$setRotation(viewCamera.getYaw(), viewCamera.getPitch());
            PortalViewTransform.copyRotation(viewCamera, camera);
            mainCamera.backrooms$setArea(view.world());
            // La camera de la vue suit le joueur a la troisieme personne : le rendu vanilla dessine alors toutes les
            // entities, le joueur compris dans une vue de son propre monde, et le mod de shaders ne dessine pas la main.
            remoteCamera.backrooms$setFocusedEntity(client.player);
            remoteCamera.backrooms$setThirdPerson(true);
            if (ownLightmap) {
                ViewLightmaps.bind(client, lightmap, view.world(), tickCounter.getTickDelta(false));
            }
            // La passe finale du mod de shaders laisse l'ecriture de la profondeur coupee : l'effacement et les dessins du
            // rendu complet n'ecriraient rien, et la vue serait cachee par la profondeur de l'image du joueur.
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            drawnView = view;
            drawnFrame = frame;
            drawnFrameCamera = frameCameraPos;
            drawnHidden = hidden;
            drawnCameraPos = viewCameraPos;
            drawnPosition = viewPosition;
            return view.viewRenderer().renderWhole(client, tickCounter, viewCamera, viewCameraPos, viewPosition, projection, frame, lightmap, reduced);
        } catch (RuntimeException exception) {
            PortalRenderState.disableRemoteRendering("exception pendant le rendu distant sous un pack de shaders", exception);
            return null;
        } finally {
            if (looked) {
                LookState.pop();
            }
            drawnView = null;
            drawnFrame = null;
            drawnFrameCamera = null;
            drawnHidden = null;
            drawnCameraPos = null;
            remoteCamera.backrooms$setFocusedEntity(null);
            remoteCamera.backrooms$setThirdPerson(false);
            mainCamera.backrooms$setArea(mainWorld);
            mainCamera.backrooms$setRotation(mainYaw, mainPitch);
            PortalViewTransform.copyRotation(SAVED_MAIN_CAMERA, camera);
            mainCamera.backrooms$setPos(mainPos);
            client.cameraEntity = mainCameraEntity;
            client.world = mainWorld;
            clientAccessor.backrooms$setWorldRenderer(mainRenderer);
            client.getEntityRenderDispatcher().configure(mainWorld, camera, client.targetedEntity);
            client.getBlockEntityRenderDispatcher().configure(mainWorld, camera, client.crosshairTarget);
            if (ownLightmap) {
                ViewLightmaps.unbind(lightmap);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /** Portail d'arrivee de {@code portal} dans le monde de la vue, ou {@code null} (aller simple, arrivee pas encore recue). */
    private static @Nullable BackroomsPortalEntity arrivalEntity(RemoteWorld view, BackroomsPortalEntity portal) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(view.world());
        Vec3d destination = portal.destinationPos();
        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).centeredAt(destination)) {
                return portals.get(i);
            }
        }
        return null;
    }

    /** Libere les images gardees de l'ecran : fin de session de portails, ou taille de l'ecran changee. Fil de rendu. */
    public static void release() {
        for (int i = 0; i < SAVED_IMAGES.length; i++) {
            if (SAVED_IMAGES[i] != 0) {
                TextureUtil.releaseTextureId(SAVED_IMAGES[i]);
                SAVED_IMAGES[i] = 0;
            }
        }
        savedWidth = 0;
        savedHeight = 0;
    }

    /** Garde l'image de l'ecran (le monde du joueur et les vues deja dessinees) dans la texture du niveau, a sa taille. */
    private static void saveImage(Framebuffer framebuffer, int level) {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        if (width != savedWidth || height != savedHeight) {
            release();
            savedWidth = width;
            savedHeight = height;
        }
        if (SAVED_IMAGES[level] == 0) {
            SAVED_IMAGES[level] = TextureUtil.generateTextureId();
            GlStateManager._bindTexture(SAVED_IMAGES[level]);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
        } else {
            GlStateManager._bindTexture(SAVED_IMAGES[level]);
        }
        // Copie depuis le framebuffer lie en lecture : celui de l'ecran.
        framebuffer.beginWrite(false);
        GlStateManager._glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    /**
     * Remet l'image gardee du niveau partout sauf dans le cadre de la vue qui vient d'etre dessinee ({@code value}) : la
     * passe finale du pack a ecrit la vue sur tout l'ecran. Un rectangle plein ecran texture, sans profondeur.
     */
    private static void restoreImage(Framebuffer framebuffer, int level, int value) {
        framebuffer.beginWrite(false);
        GL11.glStencilFunc(GL11.GL_NOTEQUAL, value, FULL_MASK);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, SAVED_IMAGES[level]);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(IDENTITY, VertexSorter.BY_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            buffer.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f);
            buffer.vertex(1.0f, -1.0f, 0.0f).texture(1.0f, 0.0f);
            buffer.vertex(1.0f, 1.0f, 0.0f).texture(1.0f, 1.0f);
            buffer.vertex(-1.0f, 1.0f, 0.0f).texture(0.0f, 1.0f);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, sorter);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            GL11.glStencilFunc(GL11.GL_EQUAL, value, FULL_MASK);
        }
    }
}
