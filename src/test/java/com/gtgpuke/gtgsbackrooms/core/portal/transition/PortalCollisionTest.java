package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Collision a travers un portail")
class PortalCollisionTest {

    private static final double EPSILON = 1.0e-9;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    /** Portail 1x2 oriente nord, centre en (0, 64, 0) : on entre par le nord (z < 0). */
    private static BackroomsPortalEntity northPortal() {
        return BackroomsPortalEntity.builder(null)
                .position(new Vec3d(0.0, 64.0, 0.0))
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH)
                .destinationDim(BackroomsConstants.LEVEL_ZERO_WORLD)
                .destinationPos(new Vec3d(10.0, 64.0, 10.0))
                .size(1.0f, 2.0f)
                .build();
    }

    /** Portail oriente nord en {@code center}, vers une arrivee d'orientation donnee. */
    private static BackroomsPortalEntity portal(Vec3d center, PortalFacing exit, Vec3d destination) {
        return BackroomsPortalEntity.builder(null)
                .position(center)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(exit)
                .destinationDim(BackroomsConstants.LEVEL_ZERO_WORLD)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
    }

    private static void assertBox(Box expected, Box actual, String message) {
        assertEquals(expected.minX, actual.minX, EPSILON, message);
        assertEquals(expected.minY, actual.minY, EPSILON, message);
        assertEquals(expected.minZ, actual.minZ, EPSILON, message);
        assertEquals(expected.maxX, actual.maxX, EPSILON, message);
        assertEquals(expected.maxY, actual.maxY, EPSILON, message);
        assertEquals(expected.maxZ, actual.maxZ, EPSILON, message);
    }

    @Test
    @DisplayName("Seule la partie d'une boite du cote de sortie du plan compte, quel que soit l'axe")
    void exitSideKeepsWhatIsBeyondThePlane() {
        Box box = new Box(0.0, 0.0, 0.0, 2.0, 2.0, 2.0);
        Vec3d center = new Vec3d(1.0, 1.0, 1.0);
        assertBox(new Box(1.0, 0.0, 0.0, 2.0, 2.0, 2.0), PortalCollision.exitSide(box, new Vec3d(1.0, 0.0, 0.0), center), "Vers l'est.");
        assertBox(new Box(0.0, 0.0, 0.0, 1.0, 2.0, 2.0), PortalCollision.exitSide(box, new Vec3d(-1.0, 0.0, 0.0), center), "Vers l'ouest.");
        assertBox(new Box(0.0, 1.0, 0.0, 2.0, 2.0, 2.0), PortalCollision.exitSide(box, new Vec3d(0.0, 1.0, 0.0), center), "Vers le haut.");
        assertBox(new Box(0.0, 0.0, 0.0, 2.0, 1.0, 2.0), PortalCollision.exitSide(box, new Vec3d(0.0, -1.0, 0.0), center), "Vers le bas.");
        assertBox(new Box(0.0, 0.0, 1.0, 2.0, 2.0, 2.0), PortalCollision.exitSide(box, new Vec3d(0.0, 0.0, 1.0), center), "Vers le sud.");
        assertBox(new Box(0.0, 0.0, 0.0, 2.0, 2.0, 1.0), PortalCollision.exitSide(box, new Vec3d(0.0, 0.0, -1.0), center), "Vers le nord.");
        Vec3d beyond = new Vec3d(2.0, 2.0, 2.0);
        assertNull(PortalCollision.exitSide(box, new Vec3d(1.0, 0.0, 0.0), beyond), "Entierement derriere un plan est-ouest.");
        assertNull(PortalCollision.exitSide(box, new Vec3d(0.0, 1.0, 0.0), beyond), "Entierement sous un plan horizontal.");
        assertNull(PortalCollision.exitSide(box, new Vec3d(0.0, 0.0, 1.0), beyond), "Entierement derriere un plan nord-sud.");
    }

    @Test
    @DisplayName("L'image d'une boite passe d'un cote a l'autre et revient a l'identique")
    void boxesCrossBothWays() {
        Vec3d source = new Vec3d(0.0, 64.0, 0.0);
        Vec3d destination = new Vec3d(10.0, 64.0, 10.0);
        Box box = new Box(0.0, 63.0, -1.0, 1.0, 65.0, 0.0);
        // Nord vers sud : simple translation, ce qui est devant l'entree est derriere la sortie.
        assertBox(new Box(10.0, 63.0, 9.0, 11.0, 65.0, 10.0),
                PortalCollision.toDestination(portal(source, PortalFacing.SOUTH, destination), box), "Nord vers sud.");
        // Nord vers est : un quart de tour, l'image reste une boite alignee sur les axes.
        BackroomsPortalEntity eastward = portal(source, PortalFacing.EAST, destination);
        Box turned = PortalCollision.toDestination(eastward, box);
        assertBox(new Box(9.0, 63.0, 9.0, 10.0, 65.0, 10.0), turned, "Nord vers est.");
        assertBox(box, PortalCollision.fromDestination(eastward, turned), "Aller-retour.");
    }

    @Test
    @DisplayName("Une entity n'est engagee que les pieds devant un portail ouvert, le centre en face de l'ouverture, la boite contre le cadre")
    void engagesOnlyInFrontAndInTheFrame() {
        BackroomsPortalEntity portal = northPortal();
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        item.setPosition(0.3, 63.5, -0.3);
        assertTrue(PortalCollision.engages(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "Devant le cadre, vers lui.");
        item.setPosition(0.3, 63.5, 0.3);
        assertFalse(PortalCollision.engages(portal, item, item.getBoundingBox()), "Les pieds derriere le plan.");
        item.setPosition(3.0, 63.5, -0.3);
        assertFalse(PortalCollision.engages(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "Devant, mais a cote du cadre.");
        item.setPosition(0.6, 63.5, -0.3);
        assertFalse(PortalCollision.engages(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)),
                "Le centre a cote du cadre, la boite contre son bord : on contourne le portail, rien de l'autre cote ne gene.");
        portal.setTeleportable(false);
        item.setPosition(0.3, 63.5, -0.3);
        assertFalse(PortalCollision.engages(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "Portail ferme.");
        // Un cadre au sol s'engage aux yeux : les pieds deja sous le plan, les yeux encore au-dessus, l'objet est engage.
        BackroomsPortalEntity floor = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.5, 0.0)).facingSource(PortalFacing.UP)
                .facingDestination(PortalFacing.DOWN).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.5, 0.0)).size(1.0f, 2.0f).build();
        item.setPosition(0.5, 64.4, 0.0);
        assertTrue(PortalCollision.engages(floor, item, item.getBoundingBox().stretch(0.0, -0.5, 0.0)), "Les yeux au-dessus du plan du sol : engage.");
        item.setPosition(0.5, 64.0, 0.0);
        assertFalse(PortalCollision.engages(floor, item, item.getBoundingBox().stretch(0.0, -0.5, 0.0)), "Les yeux passes : plus engage.");
    }

    @Test
    @DisplayName("Le plan d'un portail ouvert arrete qui le touche du cote d'entree, le centre hors de l'ouverture, et seulement lui")
    void wallsOnlyWhoCannotCrossOnTheEntrySide() {
        BackroomsPortalEntity portal = northPortal();
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        item.setPosition(0.6, 63.5, -0.3);
        assertTrue(PortalCollision.walls(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)),
                "Sur le bord, devant le plan : il ne peut pas traverser, le plan l'arrete.");
        item.setPosition(0.3, 63.5, -0.3);
        assertFalse(PortalCollision.walls(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "En face de l'ouverture : il s'y engage.");
        item.setPosition(0.6, 63.5, 0.3);
        assertFalse(PortalCollision.walls(portal, item, item.getBoundingBox().stretch(0.0, 0.0, -0.5)), "De dos, rien ne l'arrete.");
        item.setPosition(3.0, 63.5, -0.3);
        assertFalse(PortalCollision.walls(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "Loin a cote du cadre, rien.");
        portal.setTeleportable(false);
        item.setPosition(0.6, 63.5, -0.3);
        assertFalse(PortalCollision.walls(portal, item, item.getBoundingBox().stretch(0.0, 0.0, 0.5)), "Portail ferme : pas de paroi.");
    }

    @Test
    @DisplayName("Un cadre au plafond s'engage aux yeux : la tete peut entrer, les yeux passes, plus rien")
    void ceilingEngagesAtTheEyes() {
        BackroomsPortalEntity ceiling = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.5, 0.0)).facingSource(PortalFacing.DOWN)
                .facingDestination(PortalFacing.UP).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.5, 0.0)).size(1.0f, 2.0f).build();
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        item.setPosition(0.5, 64.2, 0.0);
        assertTrue(PortalCollision.engages(ceiling, item, item.getBoundingBox().stretch(0.0, 0.5, 0.0)), "Le haut de l'objet contre le plan, les yeux dessous.");
        item.setPosition(0.5, 64.4, 0.0);
        assertFalse(PortalCollision.engages(ceiling, item, item.getBoundingBox()), "Les yeux passes : plus engage.");
    }

    @Test
    @DisplayName("Un portail cache les blocs tout entiers derriere son plan, dans son ouverture prolongee, et seulement eux")
    void hidesOnlyTheBlocksBehindTheOpening() {
        // Cadre 1 x 2 face au nord, plan en z = 0 sur le bord des blocs : ouverture x de 0 a 1, y de 63 a 65 ; derriere, z >= 0.
        BackroomsPortalEntity wall = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.0, 0.0)).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.0, 10.0)).size(1.0f, 2.0f).build();
        assertTrue(PortalCollision.hides(wall, 0, 63, 0), "Le bloc colle derriere le bas du cadre.");
        assertTrue(PortalCollision.hides(wall, 0, 64, 0), "Celui derriere son haut.");
        assertTrue(PortalCollision.hides(wall, 0, 63, 7), "Loin derriere, toujours dans l'ouverture prolongee.");
        assertFalse(PortalCollision.hides(wall, 0, 63, -1), "Devant le plan : il reste.");
        assertFalse(PortalCollision.hides(wall, 1, 63, 0), "A cote de l'ouverture : il reste.");
        assertFalse(PortalCollision.hides(wall, -1, 63, 0), "De l'autre cote de l'ouverture : il reste.");
        assertFalse(PortalCollision.hides(wall, 0, 65, 0), "Au-dessus du cadre : il reste.");
        assertFalse(PortalCollision.hides(wall, 0, 62, 0), "Sous le cadre : il reste.");
        // Plan au milieu du bloc : le bloc qu'il coupe n'est pas tout entier derriere.
        BackroomsPortalEntity middle = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.0, 0.5)).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.0, 10.5)).size(1.0f, 2.0f).build();
        assertFalse(PortalCollision.hides(middle, 0, 63, 0), "Le bloc coupe par le plan reste.");
        assertTrue(PortalCollision.hides(middle, 0, 63, 1), "Le suivant est cache.");
        // Cadre au sol : derriere, c'est dessous.
        BackroomsPortalEntity floor = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.0, 0.0)).facingSource(PortalFacing.UP)
                .facingDestination(PortalFacing.DOWN).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.0, 0.0)).size(1.0f, 2.0f).build();
        assertTrue(PortalCollision.hides(floor, 0, 63, -1), "Le sol sous l'ouverture.");
        assertTrue(PortalCollision.hides(floor, 0, 63, 0), "Sous la seconde rangee.");
        assertFalse(PortalCollision.hides(floor, 0, 64, 0), "Au-dessus du plan : il reste.");
        assertFalse(PortalCollision.hides(floor, 0, 63, 1), "Sous le sol, a cote de l'ouverture : il reste.");
    }

    @Test
    @DisplayName("Dans chacune des douze orientations, le cadre ne cache que l'ouverture prolongee derriere son plan")
    void everyOrientationHidesItsOpeningBehindThePlane() {
        for (PortalFacing facing : PortalFacing.values()) {
            // Cadre 1 x 2 sur les bords des blocs : plan sur une face de bloc, ouverture d'un bloc sur deux.
            Vec3d center = new Vec3d(Math.abs(facing.axisW.x) * 0.5, 64.0, Math.abs(facing.axisW.z) * 0.5);
            BackroomsPortalEntity portal = BackroomsPortalEntity.builder(null).position(center).facingSource(facing)
                    .facingDestination(facing.opposite()).destinationDim(World.OVERWORLD).destinationPos(center).size(1.0f, 2.0f).build();
            int hidden = 0;
            for (int x = -4; x < 4; x++) {
                for (int y = 60; y < 68; y++) {
                    for (int z = -4; z < 4; z++) {
                        if (PortalCollision.hides(portal, x, y, z)) {
                            hidden++;
                            assertTrue(portal.signedDistance(x + 0.5, y + 0.5, z + 0.5) < 0.0, facing + " : un bloc cache est derriere le plan.");
                        }
                    }
                }
            }
            assertEquals(8, hidden, facing + " : les deux blocs de l'ouverture sur les quatre rangees derriere le plan.");
        }
    }

    @Test
    @DisplayName("Les quatre bords d'un cadre entourent l'ouverture d'un demi-bloc, du plan a un demi-bloc derriere")
    void frameEdgesSurroundTheOpening() {
        // Cadre 1 x 2 oriente nord en (0, 64, 0) : ouverture x -0,5..0,5, y 63..65 ; derriere le plan, z 0..0,5.
        Box[] edges = PortalCollision.frameEdges(northPortal());
        assertEquals(4, edges.length, "Quatre bords.");
        assertBox(new Box(-1.0, 62.5, 0.0, -0.5, 65.5, 0.5), edges[0], "Bord a l'ouest, coins compris.");
        assertBox(new Box(0.5, 62.5, 0.0, 1.0, 65.5, 0.5), edges[1], "Bord a l'est, coins compris.");
        assertBox(new Box(-0.5, 65.0, 0.0, 0.5, 65.5, 0.5), edges[2], "Bord du haut.");
        assertBox(new Box(-0.5, 62.5, 0.0, 0.5, 63.0, 0.5), edges[3], "Bord du bas.");
    }

    @Test
    @DisplayName("L'ouverture fermee (paroi d'un bord, sol d'une garde) va du plan a un demi-bloc derriere")
    void closedOpeningRunsFromThePlaneBehindIt() {
        BackroomsPortalEntity floor = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.0, 0.0)).facingSource(PortalFacing.UP)
                .facingDestination(PortalFacing.DOWN).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.0, 0.0)).size(1.0f, 2.0f).build();
        assertBox(new Box(0.0, 64.0 - PortalCollision.CLOSED_DEPTH, -1.0, 1.0, 64.0, 1.0), PortalCollision.closedOpening(floor),
                "L'ouverture, du plan a un demi-bloc dessous.");
    }

    @Test
    @DisplayName("Portails et cote viennent des mondes branches : ceux du client, ou le registre et le suivi du serveur par defaut")
    void portalsComeFromThePluggedWorlds() {
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        List<VoxelShape> collisions = List.of();
        boolean[] asked = {false};
        PortalCollision.setWorlds(new PortalCollision.Worlds() {
            @Override
            public List<BackroomsPortalEntity> portals(World world) {
                asked[0] = true;
                return List.of();
            }

            @Override
            public World destination(BackroomsPortalEntity portal) {
                return null;
            }

            @Override
            public PortalCollision.Side side(World world) {
                return PortalCollision.Side.CLIENT;
            }
        });
        try {
            assertSame(collisions, PortalCollision.withOtherSide(item, null, collisions, item.getBoundingBox()),
                    "Aucun portail branche : les collisions vanilla, telles quelles.");
            assertTrue(asked[0], "Les portails viennent des mondes branches.");
            assertSame(PortalCollision.Side.CLIENT, PortalCollision.side(null), "Le cote aussi.");
        } finally {
            PortalCollision.setWorlds(PortalCollision.SERVER_WORLDS);
        }
        assertSame(BackroomsPortalEntityRegistry.all(), PortalCollision.SERVER_WORLDS.portals(null),
                "Par defaut, le registre des portails du serveur, tous mondes confondus.");
        assertSame(PortalCollision.Side.SERVER, PortalCollision.side(null), "Et le suivi du serveur.");
    }
}
