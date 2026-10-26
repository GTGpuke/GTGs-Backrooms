package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.FrozenImage;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Images figees des vues de portail ({@link FrozenImage}) : un framebuffer par portail, avec profondeur et stencil (le jeu
 * en donne un a tous ses framebuffers), ou la vue se dessine le temps d'une capture, puis, a chaque image, un quad sur le
 * cadre qui en porte la texture. Ecart accorde par l'auteur le 26 octobre 2026 a la regle "pas de framebuffer de vue
 * separe" : seul ce palier en a un, et seulement sur le chemin sans pack de shaders.
 *
 * <p>La capture garde la projection du joueur ; seule la fenetre de rendu se decale, pour que le rectangle d'ecran du
 * cadre tombe dans le framebuffer, a {@value #SUPERSAMPLING} fois sa resolution : l'image est exactement celle de la vue
 * dessinee a cet instant, sans rien du monde du joueur devant le cadre. Une image sans usage depuis {@value #UNUSED_MILLIS}
 * ms, ou dont le portail a disparu, est rendue. Thread de rendu seulement.
 */
public final class FrozenViews {

    /** Resolution de la capture, en multiple de celle du rectangle a l'ecran : la texture se reduit proprement. */
    private static final int SUPERSAMPLING = 2;
    /** Cote maximal d'une capture, en pixels. */
    private static final int MAX_SIZE = 1024;
    /**
     * Marge d'un framebuffer d'image, en pixels : ses cotes s'arrondissent a ce multiple, et une capture un peu plus grande
     * ou plus petite que la precedente (la camera a bouge) y tient sans le reallouer.
     */
    private static final int SLACK = 64;
    /** Cases de la grille du quad, par cote : la texture suit une projection, qu'un seul quad deformerait. */
    private static final int GRID = 4;
    /** Intervalle entre deux passages du menage, en millisecondes. */
    private static final long SWEEP_MILLIS = 1000L;
    /** Image sans usage (ni prise ni montree) depuis ce delai, en millisecondes : rendue. */
    private static final long UNUSED_MILLIS = 30000L;
    /** Avance du quad vers la camera, comme la zone de vue ({@code FrameAreaRenderer}). */
    private static final double TOWARD_CAMERA = 1.0e-4;
    /** Decalage de polygone du quad : il l'emporte sur la face d'un bloc dans le plan du cadre. */
    private static final float OFFSET_FACTOR = -1.0f;
    private static final float OFFSET_UNITS = -10.0f;

    /** Image figee d'un portail : son framebuffer, la transformation de sa capture et ce qui la rend perimee. */
    private static final class Image {
        final Framebuffer framebuffer;
        final Matrix4f transform = new Matrix4f();
        double cameraX;
        double cameraY;
        double cameraZ;
        double distance;
        long capturedAt;
        int chunks;
        boolean complete;
        long usedAt;

        Image(int width, int height) {
            this.framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            this.framebuffer.setTexFilter(GL11.GL_LINEAR);
        }
    }

    private static final Reference2ObjectOpenHashMap<BackroomsPortalEntity, Image> IMAGES = new Reference2ObjectOpenHashMap<>();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f UV = new Vector4f();
    private static final Vector3f VERTEX = new Vector3f();
    private static Image capturing;
    private static long lastSweep;

    private FrozenViews() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si le portail a une image figee a montrer. */
    public static boolean has(BackroomsPortalEntity portal) {
        return IMAGES.containsKey(portal);
    }

    /**
     * {@code true} si l'image du portail manque ou doit etre reprise ({@link FrozenImage#stale}).
     *
     * @param chunks chunks presents dans le monde de la vue
     */
    public static boolean wantsCapture(BackroomsPortalEntity portal, Vec3d cameraPos, int chunks) {
        Image image = IMAGES.get(portal);
        if (image == null) {
            return true;
        }
        double dx = cameraPos.x - image.cameraX;
        double dy = cameraPos.y - image.cameraY;
        double dz = cameraPos.z - image.cameraZ;
        return FrozenImage.stale(Util.getMeasuringTimeMs() - image.capturedAt, image.complete, Math.sqrt(dx * dx + dy * dy + dz * dz),
                image.distance, chunks != image.chunks);
    }

    /**
     * Prepare la capture de la vue d'un portail : son framebuffer a la taille du rectangle d'ecran du cadre, lie, la fenetre
     * de rendu decalee pour y faire tomber ce rectangle, efface (couleur, profondeur, et stencil au niveau ou la vue se
     * dessine).
     *
     * @param rect rectangle d'ecran du cadre, en pixels du framebuffer du jeu : {x0, y0, x1, y1}, entierement a l'ecran
     * @param positionMatrix matrice de vue de la camera du joueur
     * @param projection projection du joueur
     * @param cameraPos camera du joueur
     * @param chunks chunks presents dans le monde de la vue
     * @param stencilLevel niveau du stencil ou la vue se dessine
     * @return echelle de la capture : pixels du framebuffer de l'image par pixel de l'ecran
     */
    public static int beginCapture(BackroomsPortalEntity portal, int[] rect, int screenWidth, int screenHeight, Matrix4f positionMatrix,
            Matrix4f projection, Vec3d cameraPos, int chunks, int stencilLevel) {
        int width = rect[2] - rect[0];
        int height = rect[3] - rect[1];
        int scale = Math.max(1, Math.min(SUPERSAMPLING, MAX_SIZE / Math.max(width, height)));
        int usedWidth = width * scale;
        int usedHeight = height * scale;
        Image image = IMAGES.get(portal);
        if (image == null) {
            image = new Image(slack(usedWidth), slack(usedHeight));
            IMAGES.put(portal, image);
        } else if (!fits(image.framebuffer.textureWidth, usedWidth) || !fits(image.framebuffer.textureHeight, usedHeight)) {
            image.framebuffer.resize(slack(usedWidth), slack(usedHeight), MinecraftClient.IS_SYSTEM_MAC);
            image.framebuffer.setTexFilter(GL11.GL_LINEAR);
        }
        // L'image n'occupe que le coin bas gauche du framebuffer : la texture se lit sur cette part seulement.
        FrozenImage.captureTransform(VIEW_PROJECTION.set(projection).mul(positionMatrix), screenWidth, screenHeight, rect[0], rect[1], width,
                height, image.transform)
                .scaleLocal(usedWidth / (float) image.framebuffer.textureWidth, usedHeight / (float) image.framebuffer.textureHeight, 1.0f);
        image.cameraX = cameraPos.x;
        image.cameraY = cameraPos.y;
        image.cameraZ = cameraPos.z;
        image.distance = Math.sqrt(portal.squaredDistanceTo(cameraPos));
        image.capturedAt = Util.getMeasuringTimeMs();
        image.usedAt = image.capturedAt;
        image.chunks = chunks;
        image.framebuffer.beginWrite(false);
        RenderSystem.viewport(-rect[0] * scale, -rect[1] * scale, screenWidth * scale, screenHeight * scale);
        GL11.glClearStencil(stencilLevel);
        // Couleur comprise : le filtrage mele au bord du cadre les texels de la marge, qui ne gardent ainsi rien d'une
        // capture precedente ni d'une memoire tout juste allouee.
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        GL11.glClearStencil(0);
        capturing = image;
        return scale;
    }

    /** Cote d'un framebuffer pour une image de cette taille : arrondi au multiple de {@value #SLACK} superieur. */
    private static int slack(int size) {
        return (size + SLACK - 1) / SLACK * SLACK;
    }

    /** {@code true} si un framebuffer de ce cote tient une image de cette taille sans gaspiller plus de la moitie de sa place. */
    private static boolean fits(int side, int size) {
        return side >= size && side <= 2 * slack(size);
    }

    /**
     * Fin de la capture : le framebuffer du jeu reprend, avec sa fenetre de rendu.
     *
     * @param complete toutes les sections de la vue etaient a jour
     */
    public static void endCapture(MinecraftClient client, boolean complete) {
        // Sans image en cours, la capture a echoue avant de lier son framebuffer : celui du jeu est repris quand meme.
        if (capturing != null) {
            capturing.complete = complete;
            capturing = null;
        }
        client.getFramebuffer().beginWrite(true);
    }

    /** Fin de partie : toutes les images sont rendues, avec leurs portails et le monde qu'ils retenaient. */
    public static void clear() {
        if (IMAGES.isEmpty()) {
            return;
        }
        ObjectIterator<Reference2ObjectMap.Entry<BackroomsPortalEntity, Image>> iterator = IMAGES.reference2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            iterator.next().getValue().framebuffer.delete();
        }
        IMAGES.clear();
        capturing = null;
        // Un framebuffer rendu laisse lie celui par defaut.
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
    }

    /**
     * Colle l'image du portail sur son cadre, au test de profondeur du monde du joueur : ce qui est devant le cadre le
     * cache. Grille de {@value #GRID} x {@value #GRID} cases, chaque sommet a la place qu'il avait dans la capture.
     *
     * @param positionMatrix matrice de vue de la camera du joueur
     */
    public static void draw(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        Image image = IMAGES.get(portal);
        image.usedAt = Util.getMeasuringTimeMs();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, image.framebuffer.getColorAttachment());
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        PortalFacing facing = portal.facingSource();
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                vertex(buffer, portal, facing, image, cameraPos, positionMatrix, i, j);
                vertex(buffer, portal, facing, image, cameraPos, positionMatrix, i + 1, j);
                vertex(buffer, portal, facing, image, cameraPos, positionMatrix, i + 1, j + 1);
                vertex(buffer, portal, facing, image, cameraPos, positionMatrix, i, j + 1);
            }
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.polygonOffset(OFFSET_FACTOR, OFFSET_UNITS);
        RenderSystem.enablePolygonOffset();
        try {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
            RenderSystem.enableCull();
        }
    }

    /** Sommet (i, j) de la grille du cadre : position vue de la camera du joueur, coordonnees de texture vues de la capture. */
    private static void vertex(BufferBuilder buffer, BackroomsPortalEntity portal, PortalFacing facing, Image image, Vec3d cameraPos,
            Matrix4f positionMatrix, int i, int j) {
        double w = (i / (double) GRID - 0.5) * portal.width();
        double h = (j / (double) GRID - 0.5) * portal.height();
        Vec3d center = portal.getPos();
        double x = center.x + facing.axisW.x * w + facing.axisH.x * h + facing.normal.x * TOWARD_CAMERA;
        double y = center.y + facing.axisW.y * w + facing.axisH.y * h + facing.normal.y * TOWARD_CAMERA;
        double z = center.z + facing.axisW.z * w + facing.axisH.z * h + facing.normal.z * TOWARD_CAMERA;
        FrozenImage.textureCoordinates(image.transform, (float) (x - image.cameraX), (float) (y - image.cameraY), (float) (z - image.cameraZ), UV);
        positionMatrix.transformPosition((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z), VERTEX);
        buffer.vertex(VERTEX.x, VERTEX.y, VERTEX.z).texture(UV.x, UV.y);
    }

    /** Rend les images sans usage depuis {@value #UNUSED_MILLIS} ms, ou dont le portail a disparu ; au plus une fois par seconde. */
    public static void sweep() {
        long now = Util.getMeasuringTimeMs();
        if (now - lastSweep < SWEEP_MILLIS) {
            return;
        }
        lastSweep = now;
        boolean deleted = false;
        ObjectIterator<Reference2ObjectMap.Entry<BackroomsPortalEntity, Image>> iterator = IMAGES.reference2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Reference2ObjectMap.Entry<BackroomsPortalEntity, Image> entry = iterator.next();
            if (entry.getKey().isRemoved() || now - entry.getValue().usedAt > UNUSED_MILLIS) {
                entry.getValue().framebuffer.delete();
                iterator.remove();
                deleted = true;
            }
        }
        if (deleted) {
            // Un framebuffer rendu laisse lie celui par defaut : le reste de l'image irait a cote de l'ecran du jeu.
            MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        }
    }
}
