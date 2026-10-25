package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Dessine la zone de vue d'un portail pour le stencil, la profondeur et le fond.
 *
 * <p>La zone est le cadre lui-meme, plat. Quand la camera touche le plan dans l'encadrement, le plan proche de la
 * projection couperait ce quad et laisserait un vide a l'ecran : la zone devient alors une boite mince, le cadre
 * prolonge de {@link #DEPTH} derriere son plan, decoupee en logiciel contre ce plan proche, la section ouverte refermee
 * par un polygone. Vue de plus loin ou de cote, une boite donnerait au portail une epaisseur : ses flancs montreraient
 * la vue la ou le regard passe a cote du cadre, et elle basculerait d'un coup de l'autre cote du plan en passant d'une
 * face a l'autre. La zone couvre ainsi toujours ce que la camera voit a travers le cadre : ni plus, ni moins.
 *
 * <p>La boite se dessine sans elimination des faces arriere, le temps de son seul dessin : vanilla suppose cette
 * elimination toujours active (ses couches de terrain ne la retablissent pas), et une vue dessinee sans elle montrerait
 * les faces arriere de la glace, du verre et de l'eau, deux fois plus opaques qu'en vrai.
 *
 * <p>Contre un cadre plaque sur un mur (plan au bord du bloc), ou au sol sur un sol plein, la face du bloc est dans le
 * plan, et dispute la profondeur a la zone. De loin, le decalage de polygone du marquage suffit ; de pres, l'imprecision
 * des positions du terrain (en flottants, relatives a leur section) depasse de loin ce decalage : a quelques
 * centimetres du plan, la face du mur l'emporterait, et apparaitrait a la place de la vue au moment de passer. La zone
 * est donc avancee de {@link #TOWARD_CAMERA} vers la camera, une distance fixe qui l'emporte a toute distance proche,
 * invisible a l'ecran. La decoupe logicielle, elle, passe un peu au-dela du plan proche vanilla ({@link #CLIP_Z}) : entre
 * les deux, la section qui referme la boite est plus loin que la face du mur, et se marque sans test de profondeur
 * ({@link #mark}).
 */
public final class FrameAreaRenderer {

    /** Epaisseur de la boite derriere le plan : couvre le plan proche vanilla (0,05) meme vu de biais. */
    private static final double DEPTH = 0.2;
    /**
     * Avance de la zone vers la camera, en blocs : un dixieme de millimetre. Assez pour l'emporter sur l'imprecision des
     * positions du terrain pres de la camera, assez peu pour que les bords de la zone ne bougent que d'un dixieme de pixel
     * vus a 30 cm (un millimetre les deplacerait deja d'un pixel par endroits).
     */
    private static final double TOWARD_CAMERA = 1.0e-4;
    /** Plan de decoupe en espace camera, un peu au-dela du plan proche vanilla pour que la section ne soit pas coupee a son tour. */
    private static final float CLIP_Z = -(GameRenderer.CAMERA_DEPTH + 0.002f);
    private static final float WELD_EPSILON = 1.0e-5f;
    /** Decalage de polygone du marquage, celui que vanilla donne aux fissures du minage sur la face d'un bloc. */
    private static final float MARK_OFFSET_FACTOR = -1.0f;
    private static final float MARK_OFFSET_UNITS = -10.0f;
    private static final int CORNER_COUNT = 8;
    private static final int FACE_VERTICES = 4;
    /** Une face decoupee par un plan a au plus cinq sommets ; la section d'une boite au plus six, douze avant soudure. */
    private static final int CLIPPED_MAX = 5;
    private static final int CAP_MAX = 12;
    private static final Vector3f SCRATCH_VERTEX = new Vector3f();

    /** Coins de la boite en espace camera (x, y, z par coin) : bit 0 = +largeur, bit 1 = +hauteur, bit 2 = derriere le plan. */
    private static final float[] CORNERS = new float[CORNER_COUNT * 3];
    private static final float[] CLIPPED = new float[CLIPPED_MAX * 3];
    private static final float[] CAP = new float[CAP_MAX * 3];
    private static final float[] CAP_ANGLES = new float[CAP_MAX];
    private static int capCount;

    private static final int[][] FACES = {
            {0, 1, 3, 2}, // Plan du portail.
            {4, 6, 7, 5}, // Fond.
            {0, 4, 5, 1}, // Bas.
            {2, 3, 7, 6}, // Haut.
            {0, 2, 6, 4}, // Cote largeur negative.
            {1, 5, 7, 3}, // Cote largeur positive.
    };

    private FrameAreaRenderer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Zone de vue pour le marquage du stencil, tiree vers la camera le temps du dessin (decalage de polygone, celui des
     * fissures du minage) : posee sur une face de bloc (cadre au bord du bloc, au sol sur un sol plein), elle l'emporte sur
     * elle au test de profondeur ; a egalite, l'une ou l'autre gagnait selon le pixel et l'image, et le cadre scintillait.
     *
     * <p>La section du plan proche, elle, se marque sans test de profondeur : elle n'existe que si la camera est dans
     * l'encadrement, a moins de {@link #DEPTH} du plan, et ne couvre que ce que la camera voit a travers le cadre ; ce qui
     * se dessine devant elle, a moins de {@link #CLIP_Z} de la camera, est derriere le plan ou dans son plan (la face d'un
     * mur contre lequel le cadre est plaque) : l'ouverture d'un cadre est libre de tout bloc plein. Seul un bloc remplacable
     * (herbe, neige) laisse dans l'ouverture pourrait s'y trouver, sur les 2 mm entre le plan proche vanilla et la section.
     */
    public static void mark(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        RenderSystem.polygonOffset(MARK_OFFSET_FACTOR, MARK_OFFSET_UNITS);
        RenderSystem.enablePolygonOffset();
        try {
            RenderSystem.setShader(GameRenderer::getPositionProgram);
            BufferBuilder faces = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION);
            emitFaces(faces, portal, cameraPos, positionMatrix, false, 0.0f, 0.0f, 0.0f);
            drawIfAny(faces);
            // Une section n'existe que la camera contre le plan, dans l'encadrement : pas de second tampon le reste du temps.
            if (capCount >= 3) {
                BufferBuilder cap = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION);
                emitCap(cap, false, 0.0f, 0.0f, 0.0f);
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                try {
                    drawIfAny(cap);
                } finally {
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                }
            }
        } finally {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        }
    }

    /** Zone de vue, position seule (marquage du stencil) : le shader des positions est pose ici, les masques par l'appelant. */
    public static void draw(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION);
        emit(buffer, portal, cameraPos, positionMatrix, false, 0.0f, 0.0f, 0.0f);
        drawIfAny(buffer);
    }

    /** Dessine sans eliminer les faces arriere : la boite est vue de dedans quand la camera touche le plan. */
    private static void drawIfAny(BufferBuilder buffer) {
        BuiltBuffer built = buffer.endNullable();
        if (built == null) {
            return;
        }
        RenderSystem.disableCull();
        try {
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.enableCull();
        }
    }

    /**
     * N'ecrit que la profondeur de la zone. Au plan lointain ({@code farPlane}), quelle que soit la profondeur deja
     * ecrite : avant de dessiner la vue, ou pour effacer la sienne. Sinon a sa vraie profondeur, la face la plus proche
     * de la boite l'emportant : juste apres un passage au plan lointain, pour tout ce qui se dessine ensuite devant le portail.
     */
    public static void writeDepth(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix, boolean farPlane) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(farPlane ? GL11.GL_ALWAYS : GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        if (farPlane) {
            GL11.glDepthRange(1.0, 1.0);
        }
        try {
            draw(portal, cameraPos, positionMatrix);
        } finally {
            if (farPlane) {
                GL11.glDepthRange(0.0, 1.0);
            }
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    /** Remplit la zone d'une couleur unie, sans toucher a la profondeur (horizon de la vue ou repli opaque). */
    public static void fill(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix, float red, float green, float blue) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            emit(buffer, portal, cameraPos, positionMatrix, true, red, green, blue);
            drawIfAny(buffer);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** Toute la boite, decoupee contre le plan proche et refermee, en triangles. */
    private static void emit(
            BufferBuilder buffer,
            BackroomsPortalEntity portal,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            boolean colored,
            float red,
            float green,
            float blue) {
        emitFaces(buffer, portal, cameraPos, positionMatrix, colored, red, green, blue);
        emitCap(buffer, colored, red, green, blue);
    }

    /** Les six faces de la boite, decoupees contre le plan proche ; les points de la section sont gardes pour {@link #emitCap}. */
    private static void emitFaces(
            BufferBuilder buffer,
            BackroomsPortalEntity portal,
            Vec3d cameraPos,
            Matrix4f positionMatrix,
            boolean colored,
            float red,
            float green,
            float blue) {
        computeCorners(portal, cameraPos, positionMatrix);
        capCount = 0;
        for (int[] face : FACES) {
            emitFan(buffer, CLIPPED, clipFace(face), colored, red, green, blue);
        }
    }

    private static void computeCorners(BackroomsPortalEntity portal, Vec3d cameraPos, Matrix4f positionMatrix) {
        Vec3d center = portal.getPos();
        PortalFacing facing = portal.facingSource();
        double halfWidth = portal.width() / 2.0;
        double halfHeight = portal.height() / 2.0;
        double depth = cameraInFrame(portal, cameraPos) ? DEPTH : 0.0;
        for (int corner = 0; corner < CORNER_COUNT; corner++) {
            double sw = (corner & 1) != 0 ? halfWidth : -halfWidth;
            double sh = (corner & 2) != 0 ? halfHeight : -halfHeight;
            double sn = TOWARD_CAMERA + ((corner & 4) != 0 ? -depth : 0.0);
            float x = (float) (center.x - cameraPos.x + facing.axisW.x * sw + facing.axisH.x * sh + facing.normal.x * sn);
            float y = (float) (center.y - cameraPos.y + facing.axisW.y * sw + facing.axisH.y * sh + facing.normal.y * sn);
            float z = (float) (center.z - cameraPos.z + facing.axisW.z * sw + facing.axisH.z * sh + facing.normal.z * sn);
            positionMatrix.transformPosition(x, y, z, SCRATCH_VERTEX);
            CORNERS[corner * 3] = SCRATCH_VERTEX.x;
            CORNERS[corner * 3 + 1] = SCRATCH_VERTEX.y;
            CORNERS[corner * 3 + 2] = SCRATCH_VERTEX.z;
        }
    }

    /**
     * {@code true} si la camera est dans l'encadrement, a moins de {@link #DEPTH} devant le plan : la seule place ou le
     * plan proche coupe le cadre la ou il couvre l'ecran. Ailleurs le cadre plat suffit, et n'a pas d'epaisseur.
     */
    private static boolean cameraInFrame(BackroomsPortalEntity portal, Vec3d cameraPos) {
        return portal.signedDistance(cameraPos) < DEPTH && portal.frameContains(cameraPos.x, cameraPos.y, cameraPos.z);
    }

    /**
     * Decoupe une face contre le plan proche (Sutherland-Hodgman) : les sommets gardes vont dans {@link #CLIPPED},
     * les points d'intersection aussi dans {@link #CAP}.
     *
     * @return nombre de sommets de la face decoupee
     */
    private static int clipFace(int[] face) {
        int count = 0;
        for (int i = 0; i < FACE_VERTICES; i++) {
            int a = face[i] * 3;
            int b = face[(i + 1) % FACE_VERTICES] * 3;
            boolean insideA = CORNERS[a + 2] <= CLIP_Z;
            boolean insideB = CORNERS[b + 2] <= CLIP_Z;
            if (insideA) {
                count = append(CLIPPED, count, CORNERS[a], CORNERS[a + 1], CORNERS[a + 2]);
            }
            if (insideA != insideB) {
                float t = (CLIP_Z - CORNERS[a + 2]) / (CORNERS[b + 2] - CORNERS[a + 2]);
                float x = CORNERS[a] + (CORNERS[b] - CORNERS[a]) * t;
                float y = CORNERS[a + 1] + (CORNERS[b + 1] - CORNERS[a + 1]) * t;
                count = append(CLIPPED, count, x, y, CLIP_Z);
                if (capCount < CAP_MAX) {
                    capCount = append(CAP, capCount, x, y, CLIP_Z);
                }
            }
        }
        return count;
    }

    private static int append(float[] vertices, int count, float x, float y, float z) {
        vertices[count * 3] = x;
        vertices[count * 3 + 1] = y;
        vertices[count * 3 + 2] = z;
        return count + 1;
    }

    /** Section de la boite par le plan proche : points soudes, ordonnes autour de leur centre, en eventail. */
    private static void emitCap(BufferBuilder buffer, boolean colored, float red, float green, float blue) {
        int distinct = 0;
        for (int i = 0; i < capCount; i++) {
            float x = CAP[i * 3];
            float y = CAP[i * 3 + 1];
            boolean duplicate = false;
            for (int j = 0; j < distinct && !duplicate; j++) {
                duplicate = Math.abs(CAP[j * 3] - x) < WELD_EPSILON && Math.abs(CAP[j * 3 + 1] - y) < WELD_EPSILON;
            }
            if (!duplicate) {
                distinct = append(CAP, distinct, x, y, CLIP_Z);
            }
        }
        if (distinct < 3) {
            return;
        }
        float centerX = 0.0f;
        float centerY = 0.0f;
        for (int i = 0; i < distinct; i++) {
            centerX += CAP[i * 3];
            centerY += CAP[i * 3 + 1];
        }
        centerX /= distinct;
        centerY /= distinct;
        for (int i = 0; i < distinct; i++) {
            CAP_ANGLES[i] = (float) Math.atan2(CAP[i * 3 + 1] - centerY, CAP[i * 3] - centerX);
        }
        // Tri par insertion : six points au plus.
        for (int i = 1; i < distinct; i++) {
            float angle = CAP_ANGLES[i];
            float x = CAP[i * 3];
            float y = CAP[i * 3 + 1];
            int j = i - 1;
            while (j >= 0 && CAP_ANGLES[j] > angle) {
                CAP_ANGLES[j + 1] = CAP_ANGLES[j];
                CAP[(j + 1) * 3] = CAP[j * 3];
                CAP[(j + 1) * 3 + 1] = CAP[j * 3 + 1];
                j--;
            }
            CAP_ANGLES[j + 1] = angle;
            CAP[(j + 1) * 3] = x;
            CAP[(j + 1) * 3 + 1] = y;
        }
        emitFan(buffer, CAP, distinct, colored, red, green, blue);
    }

    private static void emitFan(BufferBuilder buffer, float[] vertices, int count, boolean colored, float red, float green, float blue) {
        for (int i = 1; i + 1 < count; i++) {
            emitVertex(buffer, vertices, 0, colored, red, green, blue);
            emitVertex(buffer, vertices, i, colored, red, green, blue);
            emitVertex(buffer, vertices, i + 1, colored, red, green, blue);
        }
    }

    private static void emitVertex(BufferBuilder buffer, float[] vertices, int index, boolean colored, float red, float green, float blue) {
        buffer.vertex(vertices[index * 3], vertices[index * 3 + 1], vertices[index * 3 + 2]);
        if (colored) {
            buffer.color(red, green, blue, 1.0f);
        }
    }
}
