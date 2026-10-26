package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Image figee d'une vue de portail (palier fige de {@link ViewDetail}, decisions de l'auteur du 26 octobre 2026) : la vue
 * se dessine une fois dans une texture, collee ensuite sur le cadre a chaque image. Logique pure de ce collage et de sa
 * reprise.
 *
 * <p>La capture est le rendu de la vue lui-meme, avec la projection du joueur, dans le seul rectangle d'ecran du cadre :
 * la texture couvre ce rectangle. Un point du cadre y prend la place qu'il avait a l'ecran au moment de la capture
 * ({@link #captureTransform}) : vue un peu plus tard, d'un peu plus loin ou de biais, l'image reste collee au cadre.
 *
 * <p>Une image se reprend ({@link #stale}) quand elle a {@value #REFRESH_MILLIS} ms, quand la camera s'est deplacee de
 * plus d'un vingtieme de sa distance au cadre (la parallaxe se verrait), ou, apres {@value #RETRY_MILLIS} ms, quand le
 * monde de la vue a recu ou perdu des chunks depuis, ou que des sections de la vue n'etaient pas encore a jour a la
 * capture.
 */
public final class FrozenImage {

    /** Age au-dela duquel une image se reprend, en millisecondes. */
    static final long REFRESH_MILLIS = 5000L;
    /** Deplacement de la camera, en part de sa distance au cadre a la capture, au-dela duquel une image se reprend. */
    static final double MOVE_FRACTION = 0.05;
    /** Age minimal, en millisecondes, d'une image incomplete ou d'un monde qui change avant de la reprendre : pas une capture par image. */
    static final long RETRY_MILLIS = 250L;

    private FrozenImage() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Transformation d'un point (relatif a la camera de la capture) vers la texture : ses coordonnees homogenes, dont
     * {@code x / w} et {@code y / w} sont la place du point dans le rectangle capture, de 0 a 1.
     *
     * @param viewProjection projection par matrice de vue de la camera de la capture
     * @param screenWidth largeur de l'ecran, en pixels
     * @param screenHeight hauteur de l'ecran, en pixels
     * @param rectX premier pixel du rectangle capture, en largeur
     * @param rectY premier pixel du rectangle capture, en hauteur (depuis le bas)
     * @param rectWidth largeur du rectangle, en pixels
     * @param rectHeight hauteur du rectangle, en pixels
     * @param out transformation, remplacee
     * @return {@code out}
     */
    public static Matrix4f captureTransform(Matrix4f viewProjection, int screenWidth, int screenHeight, int rectX, int rectY, int rectWidth,
            int rectHeight, Matrix4f out) {
        // Coordonnees normalisees de l'ecran (-1 a 1) vers le rectangle (0 a 1), sur les coordonnees homogenes.
        return out.translation((screenWidth * 0.5f - rectX) / rectWidth, (screenHeight * 0.5f - rectY) / rectHeight, 0.0f)
                .scale(screenWidth / (2.0f * rectWidth), screenHeight / (2.0f * rectHeight), 1.0f)
                .mul(viewProjection);
    }

    /**
     * Coordonnees de texture d'un point, relatif a la camera de la capture : dans {@code out.x} et {@code out.y}.
     *
     * @param transform transformation de la capture ({@link #captureTransform})
     * @return {@code out}
     */
    public static Vector4f textureCoordinates(Matrix4f transform, float x, float y, float z, Vector4f out) {
        out.set(x, y, z, 1.0f).mul(transform);
        return out.set(out.x / out.w, out.y / out.w, 0.0f, 1.0f);
    }

    /**
     * {@code true} si l'image doit etre reprise.
     *
     * @param ageMillis age de l'image
     * @param complete toutes les sections de la vue etaient a jour a la capture
     * @param moved deplacement de la camera depuis la capture, en blocs
     * @param distance distance de la camera au cadre a la capture, en blocs
     * @param chunksChanged le monde de la vue a recu ou perdu des chunks depuis la capture
     */
    public static boolean stale(long ageMillis, boolean complete, double moved, double distance, boolean chunksChanged) {
        if (ageMillis >= REFRESH_MILLIS || moved > MOVE_FRACTION * distance) {
            return true;
        }
        return ageMillis >= RETRY_MILLIS && (!complete || chunksChanged);
    }
}
