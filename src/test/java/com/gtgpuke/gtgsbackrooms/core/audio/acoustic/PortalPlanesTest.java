package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Cadres de portail traverses par les rayons de reverb")
class PortalPlanesTest {

    private static final double EPSILON = 1.0e-9;
    private static final Vec3d CENTER = new Vec3d(10.5, 65.0, 10.5);
    private static final Vec3d ARRIVAL = new Vec3d(100.5, 65.0, 100.5);

    private static PortalPlanes northToSouth(SolidityGrid grid) {
        PortalPlanes planes = new PortalPlanes();
        assertTrue(planes.add(PortalFacing.NORTH, CENTER, 1.0f, 2.0f, PortalFacing.SOUTH, ARRIVAL, grid), "Ajoute.");
        return planes;
    }

    @Test
    @DisplayName("Un rayon venu du cote d'entree qui passe dans le rectangle traverse le cadre ; les autres non")
    void raysThroughTheOpeningAreFound() {
        SolidityGrid grid = new SolidityGrid();
        PortalPlanes planes = northToSouth(grid);
        assertEquals(0, planes.intersect(10.5, 65.0, 8.5, 0.0, 0.0, 1.0, 48.0), "Vers le sud, au milieu du cadre : traverse.");
        assertEquals(2.0, planes.hitDistance(), EPSILON, "A deux blocs.");
        assertEquals(-1, planes.intersect(10.5, 65.0, 12.5, 0.0, 0.0, -1.0, 48.0), "Vers le nord depuis l'arriere : un cadre ne se traverse que d'avant en arriere.");
        assertEquals(-1, planes.intersect(10.5, 65.0, 8.5, 1.0, 0.0, 0.0, 48.0), "Le long du plan : jamais.");
        assertEquals(-1, planes.intersect(12.5, 65.0, 8.5, 0.0, 0.0, 1.0, 48.0), "A cote du cadre : non.");
        assertEquals(-1, planes.intersect(10.5, 67.5, 8.5, 0.0, 0.0, 1.0, 48.0), "Au-dessus du cadre : non.");
        assertEquals(-1, planes.intersect(10.5, 65.0, 8.5, 0.0, 0.0, 1.0, 1.5), "Plus loin que la distance permise : non.");
        assertEquals(-1, planes.intersect(10.5, 65.0, 12.5, 0.0, 0.0, 1.0, 48.0), "Deja passe (le cadre est derriere) : non.");
        assertEquals(48.0, planes.hitDistance(), EPSILON, "Sans cadre, la distance rendue est celle permise.");
        assertSame(grid, planes.destination(0), "La copie du monde d'arrivee.");
        assertEquals(100.5, planes.arrivalX(0), EPSILON, "Arrivee X.");
        assertEquals(65.0, planes.arrivalY(0), EPSILON, "Arrivee Y.");
        assertEquals(100.5, planes.arrivalZ(0), EPSILON, "Arrivee Z.");
    }

    @Test
    @DisplayName("Entre deux cadres sur le trajet, le plus proche est retenu ; la liste est bornee, copiable et vidable")
    void nearestFrameWinsAndListIsBounded() {
        SolidityGrid near = new SolidityGrid();
        SolidityGrid far = new SolidityGrid();
        PortalPlanes planes = new PortalPlanes();
        planes.add(PortalFacing.NORTH, new Vec3d(10.5, 65.0, 20.5), 2.0f, 2.0f, PortalFacing.SOUTH, ARRIVAL, far);
        planes.add(PortalFacing.NORTH, CENTER, 2.0f, 2.0f, PortalFacing.SOUTH, ARRIVAL, near);
        assertEquals(1, planes.intersect(10.5, 65.0, 8.5, 0.0, 0.0, 1.0, 48.0), "Le cadre a deux blocs avant celui a douze.");
        assertEquals(2.0, planes.hitDistance(), EPSILON, "Sa distance.");
        assertEquals(0, planes.intersect(10.5, 65.0, 15.5, 0.0, 0.0, 1.0, 48.0), "Passe le premier, le second.");
        planes.add(PortalFacing.EAST, CENTER, 1.0f, 1.0f, PortalFacing.WEST, ARRIVAL, far);
        planes.add(PortalFacing.UP, CENTER, 1.0f, 1.0f, PortalFacing.DOWN, ARRIVAL, far);
        assertEquals(PortalPlanes.CAPACITY, planes.count(), "Quatre.");
        assertFalse(planes.add(PortalFacing.UP, CENTER, 1.0f, 1.0f, PortalFacing.DOWN, ARRIVAL, far), "Pas de cinquieme.");
        PortalPlanes copy = new PortalPlanes();
        copy.copyFrom(planes);
        assertEquals(1, copy.intersect(10.5, 65.0, 8.5, 0.0, 0.0, 1.0, 48.0), "La copie trouve le meme cadre.");
        assertSame(near, copy.destination(1), "Et sa copie du monde.");
        copy.clear();
        assertEquals(0, copy.count(), "Videe.");
        assertNull(copy.destination(1), "Les copies de monde sont lachees.");
        assertEquals(-1, copy.intersect(10.5, 65.0, 8.5, 0.0, 0.0, 1.0, 48.0), "Plus de cadre.");
    }

    @Test
    @DisplayName("Un point du cadre et une direction se transposent comme ce qui traverse")
    void transformFollowsThePortalRule() {
        PortalPlanes planes = northToSouth(new SolidityGrid());
        Vec3d point = new Vec3d(10.25, 65.5, 10.5);
        Vec3d direction = new Vec3d(0.1, -0.2, 0.9);
        planes.transform(0, point.x, point.y, point.z, direction.x, direction.y, direction.z);
        Vec3d expectedPoint = PortalFacing.transformPoint(PortalFacing.NORTH, CENTER, PortalFacing.SOUTH, ARRIVAL, point);
        Vec3d expectedDirection = PortalFacing.transformVector(PortalFacing.NORTH, PortalFacing.SOUTH, direction);
        assertEquals(expectedPoint.x, planes.pointX(), EPSILON, "Point X comme une entite qui traverse.");
        assertEquals(expectedPoint.y, planes.pointY(), EPSILON, "Point Y.");
        assertEquals(expectedPoint.z, planes.pointZ(), EPSILON, "Point Z.");
        assertEquals(expectedDirection.x, planes.directionX(), EPSILON, "Direction X.");
        assertEquals(expectedDirection.y, planes.directionY(), EPSILON, "Direction Y.");
        assertEquals(expectedDirection.z, planes.directionZ(), EPSILON, "Direction Z.");
        assertEquals(0.9, planes.directionZ(), EPSILON, "Entre vers le sud, ressort vers le sud.");
    }
}
