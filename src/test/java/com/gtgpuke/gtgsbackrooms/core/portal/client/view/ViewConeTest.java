package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Volume visible a travers un cadre")
class ViewConeTest {

    /** Cadre de 1 x 2 centre en (0, 65, 0), largeur selon X, hauteur selon Y, contenu vers +Z. */
    private static final Vec3d CENTER = new Vec3d(0.0, 65.0, 0.0);
    private static final Vec3d AXIS_W = new Vec3d(1.0, 0.0, 0.0);
    private static final Vec3d AXIS_H = new Vec3d(0.0, 1.0, 0.0);
    private static final Vec3d NORMAL = new Vec3d(0.0, 0.0, 1.0);
    /** Camera 5 blocs devant le cadre, du cote oppose au contenu. */
    private static final Vec3d APEX = new Vec3d(0.0, 65.0, -5.0);

    private static ViewCone cone(Vec3d apex) {
        ViewCone cone = new ViewCone();
        cone.set(apex, CENTER, AXIS_W, AXIS_H, NORMAL, 0.5, 1.0);
        return cone;
    }

    @Test
    @DisplayName("Ce qui est derriere le cadre, dans la pyramide, est garde ; la pyramide s'elargit avec la distance")
    void contentInsideThePyramidIsKept() {
        ViewCone cone = cone(APEX);
        assertTrue(cone.intersects(new Box(-0.4, 64.5, 1.0, 0.4, 65.5, 2.0)), "Juste derriere le cadre, dans l'axe.");
        // A 25 blocs de la camera, la demi-largeur visible vaut 0,5 x 25 / 5 = 2,5.
        assertTrue(cone.intersects(new Box(1.5, 64.5, 20.0, 2.5, 65.5, 21.0)), "Loin derriere, decale mais dans la pyramide.");
        assertFalse(cone.intersects(new Box(3.0, 64.5, 20.0, 4.0, 65.5, 21.0)), "Loin derriere, au-dela du bord de la pyramide.");
    }

    @Test
    @DisplayName("Ce qui est devant le plan du cadre est coupe, meme dans l'axe de la pyramide")
    void contentInFrontOfTheFrameIsCut() {
        ViewCone cone = cone(APEX);
        assertFalse(cone.intersects(new Box(-0.4, 64.5, -2.0, 0.4, 65.5, -1.0)), "Entre la camera et le cadre.");
        assertFalse(cone.intersects(new Box(-0.4, 64.5, -9.0, 0.4, 65.5, -8.0)), "Derriere la camera.");
    }

    @Test
    @DisplayName("Ce qui est a cote ou au-dessus du cadre est coupe")
    void contentBesideTheFrameIsCut() {
        ViewCone cone = cone(APEX);
        // A 6 blocs de la camera, la demi-largeur visible vaut 0,6 et la demi-hauteur 1,2.
        assertFalse(cone.intersects(new Box(2.0, 64.5, 0.5, 3.0, 65.5, 1.5)), "A cote du cadre.");
        assertFalse(cone.intersects(new Box(-0.4, 67.0, 0.5, 0.4, 68.0, 1.5)), "Au-dessus du cadre.");
        assertFalse(cone.intersects(new Box(-0.4, 62.0, 0.5, 0.4, 63.0, 1.5)), "Sous le cadre.");
    }

    @Test
    @DisplayName("Une boite a cheval sur une arete de la pyramide est gardee : le test est conservateur")
    void boxesStraddlingAnEdgeAreKept() {
        ViewCone cone = cone(APEX);
        assertTrue(cone.intersects(new Box(0.0, 64.0, 0.5, 1.0, 66.0, 1.5)), "Deborde du cadre a droite.");
        assertTrue(cone.intersects(new Box(-8.0, 60.0, -1.0, 8.0, 70.0, 1.0)), "Enorme boite qui contient le cadre.");
    }

    @Test
    @DisplayName("Camera du cote du contenu : la pyramide se retourne vers le cadre et le plan coupe toujours")
    void cameraOnTheContentSideStillBoundsThePyramid() {
        ViewCone cone = cone(new Vec3d(0.0, 65.0, 5.0));
        assertTrue(cone.intersects(new Box(-0.4, 64.5, 1.0, 0.4, 65.5, 2.0)), "Entre la camera et le cadre, dans la pyramide.");
        // A 4 blocs de la camera, la demi-largeur visible vaut 0,4 : la boite commence a 2.
        assertFalse(cone.intersects(new Box(2.0, 64.5, 1.0, 3.0, 65.5, 2.0)), "A cote de la pyramide.");
        assertFalse(cone.intersects(new Box(-0.4, 64.5, -2.0, 0.4, 65.5, -1.0)), "De l'autre cote du plan du cadre.");
    }

    @Test
    @DisplayName("Camera dans le plan du cadre : plus de pyramide, seul le plan coupe")
    void cameraInTheFramePlaneKeepsOnlyThePlane() {
        ViewCone cone = cone(new Vec3d(0.0, 65.0, 0.0));
        assertTrue(cone.intersects(new Box(5.0, 64.0, 1.0, 6.0, 66.0, 2.0)), "Loin a cote, mais derriere le plan.");
        assertFalse(cone.intersects(new Box(-0.4, 64.5, -2.0, 0.4, 65.5, -1.0)), "Devant le plan.");
    }

    @Test
    @DisplayName("Une instance se redefinit d'une image a l'autre")
    void instanceIsReused() {
        ViewCone cone = cone(APEX);
        assertFalse(cone.intersects(new Box(1.5, 64.5, 0.5, 2.5, 65.5, 1.5)), "Coupe par la pyramide depuis 5 blocs.");
        cone.set(new Vec3d(0.0, 65.0, -0.5), CENTER, AXIS_W, AXIS_H, NORMAL, 0.5, 1.0);
        assertTrue(cone.intersects(new Box(1.5, 64.5, 0.5, 2.5, 65.5, 1.5)), "Depuis un demi-bloc, la pyramide est bien plus ouverte.");
    }

    @Test
    @DisplayName("Entre la camera et le cadre : seul ce qui est dans la pyramide, de ce cote du plan, compte")
    void volumeBeforeTheFrameIsTheNearPartOfThePyramid() {
        ViewCone cone = cone(APEX);
        assertTrue(cone.intersectsBeforeFrame(-0.4, 64.5, -2.0, 0.4, 65.5, -1.0), "Dans l'axe, entre la camera et le cadre.");
        assertFalse(cone.intersectsBeforeFrame(-0.4, 64.5, 1.0, 0.4, 65.5, 2.0), "Derriere le cadre, dans le contenu.");
        assertFalse(cone.intersectsBeforeFrame(-0.4, 64.5, -9.0, 0.4, 65.5, -8.0), "Derriere la camera.");
        // A 2 blocs de la camera, la demi-largeur de la pyramide vaut 0,2 et sa demi-hauteur 0,4.
        assertFalse(cone.intersectsBeforeFrame(0.5, 64.5, -3.5, 1.5, 65.5, -2.5), "A droite de la pyramide.");
        assertFalse(cone.intersectsBeforeFrame(-1.5, 64.5, -3.5, -0.5, 65.5, -2.5), "A gauche de la pyramide.");
        assertFalse(cone.intersectsBeforeFrame(-0.4, 66.0, -3.5, 0.4, 67.0, -2.5), "Au-dessus de la pyramide.");
        assertFalse(cone.intersectsBeforeFrame(-0.4, 63.0, -3.5, 0.4, 64.0, -2.5), "Sous la pyramide.");
    }

    @Test
    @DisplayName("Un mur ou un sol qui borde le cadre, a peine retreci, ne touche pas le volume en deca")
    void blocksAroundTheFrameDoNotObstruct() {
        ViewCone cone = cone(APEX);
        double inset = 1.0e-4;
        assertFalse(cone.intersectsBeforeFrame(0.5 + inset, 64.0 + inset, -0.5 + inset, 1.5 - inset, 65.0 - inset, 0.5 - inset),
                "Mur a droite du cadre, a cheval sur son plan.");
        assertFalse(cone.intersectsBeforeFrame(-0.5 + inset, 63.0 + inset, -0.5 + inset, 0.5 - inset, 64.0 - inset, 0.5 - inset),
                "Sol sous le cadre, a cheval sur son plan.");
    }

    @Test
    @DisplayName("Quelle que soit l'orientation du cadre, la boite est coupee a son plan avant le test")
    void everyFacingCutsTheBoxAtTheFramePlane() {
        // Centre hors de l'origine sur chaque axe : la coupe au plan depend de sa position, pas seulement de la normale.
        Vec3d center = new Vec3d(3.0, 65.0, -7.0);
        double inset = 1.0e-4;
        for (PortalFacing facing : PortalFacing.values()) {
            ViewCone cone = new ViewCone();
            cone.set(center.subtract(facing.normal.multiply(5.0)), center, facing.axisW, facing.axisH, facing.normal, 0.5, 1.0);
            Vec3d between = center.subtract(facing.normal.multiply(2.0));
            assertTrue(cone.intersectsBeforeFrame(between.x - 0.1, between.y - 0.1, between.z - 0.1, between.x + 0.1, between.y + 0.1, between.z + 0.1),
                    facing + " : entre la camera et le cadre.");
            Vec3d content = center.add(facing.normal.multiply(2.0));
            assertFalse(cone.intersectsBeforeFrame(content.x - 0.1, content.y - 0.1, content.z - 0.1, content.x + 0.1, content.y + 0.1, content.z + 0.1),
                    facing + " : dans le contenu.");
            // Bloc qui borde le cadre sur sa largeur, a cheval sur son plan.
            Vec3d wall = center.add(facing.axisW.multiply(1.0));
            Vec3d half = new Vec3d(0.5, 0.5, 0.5);
            Vec3d low = wall.subtract(half);
            Vec3d high = wall.add(half);
            assertFalse(cone.intersectsBeforeFrame(low.x + inset, low.y + inset, low.z + inset, high.x - inset, high.y - inset, high.z - inset),
                    facing + " : mur qui borde le cadre.");
        }
    }

    @Test
    @DisplayName("Sans volume entre la camera et le cadre, rien n'y est jamais")
    void noVolumeBeforeTheFrameWithoutAPyramidOnTheCutSide() {
        double[] box = {-0.4, 64.5, -2.0, 0.4, 65.5, -1.0};
        assertFalse(cone(new Vec3d(0.0, 65.0, 5.0)).intersectsBeforeFrame(box[0], box[1], box[2], box[3], box[4], box[5]),
                "Camera du cote du contenu.");
        assertFalse(cone(new Vec3d(0.0, 65.0, 0.0)).intersectsBeforeFrame(box[0], box[1], box[2], box[3], box[4], box[5]),
                "Camera dans le plan du cadre.");
        ViewCone unbounded = cone(APEX);
        unbounded.unbounded();
        assertFalse(unbounded.intersectsBeforeFrame(box[0], box[1], box[2], box[3], box[4], box[5]), "Volume sans limite.");
    }

    @Test
    @DisplayName("Camera en deca du cadre, a distance : le volume commence au cadre, dont la boite est connue")
    void theVolumeStartsAtTheFrameWhenTheCameraIsBeforeIt() {
        ViewCone cone = cone(new Vec3d(0.0, 65.0, -30.0));
        assertTrue(cone.startsAtFrame(), "Camera a trente blocs en deca du cadre.");
        assertEquals(-0.5, cone.frameMin(0), 1.0e-9, "Largeur du cadre, selon X.");
        assertEquals(0.5, cone.frameMax(0), 1.0e-9, "Largeur du cadre, selon X.");
        assertEquals(64.0, cone.frameMin(1), 1.0e-9, "Hauteur du cadre, selon Y.");
        assertEquals(66.0, cone.frameMax(1), 1.0e-9, "Hauteur du cadre, selon Y.");
        assertEquals(0.0, cone.frameMin(2), 1.0e-9, "Plan du cadre.");
        assertEquals(0.0, cone.frameMax(2), 1.0e-9, "Plan du cadre.");
        ViewCone below = new ViewCone();
        below.set(new Vec3d(0.0, 60.0, 0.0), CENTER, AXIS_W, new Vec3d(0.0, 0.0, -1.0), AXIS_H, 0.5, 1.0);
        assertEquals(-1.0, below.frameMin(2), 1.0e-9, "Cadre couche, hauteur selon -Z : bornes remises dans l'ordre.");
        assertEquals(1.0, below.frameMax(2), 1.0e-9, "Cadre couche, hauteur selon -Z : bornes remises dans l'ordre.");
        assertTrue(below.startsAtFrame(), "Camera sous un cadre couche, contenu au-dessus.");
        assertFalse(cone(new Vec3d(0.0, 65.0, 5.0)).startsAtFrame(), "Camera du cote du contenu.");
        ViewCone inPlane = cone(new Vec3d(0.0, 65.0, -1.0e-4));
        assertFalse(inPlane.startsAtFrame(), "Camera dans le plan du cadre.");
        assertEquals(66.0, inPlane.frameMax(1), 1.0e-9, "La boite du cadre est connue meme sans pyramide.");
        ViewCone unbounded = cone(APEX);
        unbounded.unbounded();
        assertFalse(unbounded.startsAtFrame(), "Volume sans limite.");
    }

    @Test
    @DisplayName("Sans limite, quand la camera est passee de l'autre cote, tout est garde, meme devant le plan")
    void unboundedKeepsEverything() {
        ViewCone cone = cone(APEX);
        cone.unbounded();
        assertTrue(cone.intersects(new Box(2.0, 64.5, 0.5, 3.0, 65.5, 1.5)), "Hors de la pyramide.");
        assertTrue(cone.intersects(new Box(-0.4, 64.5, -2.0, 0.4, 65.5, -1.0)), "Devant le plan.");
    }
}
