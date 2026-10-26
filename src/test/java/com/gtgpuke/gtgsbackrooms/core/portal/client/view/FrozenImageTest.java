package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Image figee d'une vue")
class FrozenImageTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final float EPSILON = 1.0e-4f;

    /** Camera a l'origine, regard vers -Z, champ vertical de 90 degres. */
    private static Matrix4f viewProjection() {
        return new Matrix4f().perspective((float) Math.toRadians(90.0), (float) WIDTH / HEIGHT, 0.05f, 1000.0f);
    }

    /** Point a cette profondeur (positive) qui tombe sur ce pixel de l'ecran. */
    private static Vector4f pointAt(float pixelX, float pixelY, float depth) {
        float aspect = (float) WIDTH / HEIGHT;
        return new Vector4f((2.0f * pixelX / WIDTH - 1.0f) * depth * aspect, (2.0f * pixelY / HEIGHT - 1.0f) * depth, -depth, 1.0f);
    }

    private static Vector4f uv(Matrix4f transform, Vector4f point) {
        return FrozenImage.textureCoordinates(transform, point.x, point.y, point.z, new Vector4f());
    }

    @Test
    @DisplayName("Un point prend dans la texture la place qu'il avait dans le rectangle capture, a toute profondeur")
    void pointsKeepTheirPlaceInTheCapturedRectangle() {
        Matrix4f out = new Matrix4f();
        Matrix4f transform = FrozenImage.captureTransform(viewProjection(), WIDTH, HEIGHT, 620, 340, 40, 40, out);
        assertSame(out, transform, "La transformation est ecrite dans la matrice donnee.");
        Vector4f corner = uv(transform, pointAt(620.0f, 340.0f, 10.0f));
        assertEquals(0.0f, corner.x, EPSILON, "Coin bas gauche du rectangle, en largeur.");
        assertEquals(0.0f, corner.y, EPSILON, "Coin bas gauche du rectangle, en hauteur.");
        Vector4f center = uv(transform, pointAt(640.0f, 360.0f, 25.0f));
        assertEquals(0.5f, center.x, EPSILON, "Centre de l'ecran, au milieu du rectangle centre.");
        assertEquals(0.5f, center.y, EPSILON, "Centre de l'ecran, au milieu du rectangle centre.");
        Vector4f far = uv(transform, pointAt(660.0f, 380.0f, 80.0f));
        assertEquals(1.0f, far.x, EPSILON, "Coin haut droit, loin : la profondeur ne compte pas.");
        assertEquals(1.0f, far.y, EPSILON, "Coin haut droit, loin : la profondeur ne compte pas.");
    }

    @Test
    @DisplayName("Un rectangle decentre et allonge se rapporte a ses propres bords")
    void anOffCenterRectangleMapsToItsOwnEdges() {
        Matrix4f transform = FrozenImage.captureTransform(viewProjection(), WIDTH, HEIGHT, 100, 500, 200, 50, new Matrix4f());
        Vector4f inside = uv(transform, pointAt(150.0f, 525.0f, 12.0f));
        assertEquals(0.25f, inside.x, EPSILON, "Un quart de la largeur.");
        assertEquals(0.5f, inside.y, EPSILON, "La moitie de la hauteur.");
    }

    @Test
    @DisplayName("Une image se reprend vieille, apres un deplacement visible, ou incomplete passe un court delai")
    void imagesAreRetakenWhenStale() {
        assertTrue(FrozenImage.stale(FrozenImage.REFRESH_MILLIS, true, 0.0, 60.0, false), "Assez vieille.");
        assertTrue(FrozenImage.stale(0L, true, 3.1, 60.0, false), "Camera deplacee de plus d'un vingtieme de sa distance.");
        assertFalse(FrozenImage.stale(1000L, true, 2.9, 60.0, false), "Deplacement invisible, image complete.");
        assertFalse(FrozenImage.stale(FrozenImage.RETRY_MILLIS - 1L, false, 0.0, 60.0, true), "Incomplete, mais tout juste prise.");
        assertTrue(FrozenImage.stale(FrozenImage.RETRY_MILLIS, false, 0.0, 60.0, false), "Incomplete, apres le court delai.");
        assertTrue(FrozenImage.stale(FrozenImage.RETRY_MILLIS, true, 0.0, 60.0, true), "Le monde de la vue a change.");
    }
}
