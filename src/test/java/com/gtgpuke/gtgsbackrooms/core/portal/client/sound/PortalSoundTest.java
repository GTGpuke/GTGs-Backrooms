package com.gtgpuke.gtgsbackrooms.core.portal.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Son a travers un portail")
class PortalSoundTest {

    private static final double EPSILON = 1.0e-6;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    /** Portail regardant le nord en (10.5, 65, 10.5), d'un bloc sur deux : son cote d'entree est vers les Z decroissants. */
    private static BackroomsPortalEntity portal() {
        return BackroomsPortalEntity.builder(null)
                .position(new Vec3d(10.5, 65.0, 10.5))
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.EAST)
                .destinationDim(World.NETHER)
                .destinationPos(new Vec3d(100.5, 70.0, -40.5))
                .size(1.0f, 2.0f)
                .build();
    }

    /** Portail regardant le nord, d'un bloc sur deux, en {@code center}, vers le Nether ; reglable ferme ou invisible. */
    private static BackroomsPortalEntity netherPortal(Vec3d center, boolean teleportable, boolean visible) {
        return BackroomsPortalEntity.builder(null)
                .position(center)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.NETHER)
                .destinationPos(center.add(100.0, 0.0, 0.0))
                .size(1.0f, 2.0f)
                .teleportable(teleportable)
                .visible(visible)
                .build();
    }

    @Test
    @DisplayName("Le son d'un autre monde ressort du cadre ouvert et visible vers ce monde dont la sortie est la plus pres de sa source")
    void bestExitPicksTheNearestOpenFrameTowardsTheSoundsWorld() {
        BackroomsPortalEntity near = netherPortal(new Vec3d(10.5, 65.0, 10.5), true, true);
        BackroomsPortalEntity far = netherPortal(new Vec3d(30.5, 65.0, 10.5), true, true);
        BackroomsPortalEntity closed = netherPortal(new Vec3d(10.5, 65.0, 10.5), false, true);
        BackroomsPortalEntity invisible = netherPortal(new Vec3d(10.5, 65.0, 10.5), true, false);
        BackroomsPortalEntity elsewhere = BackroomsPortalEntity.builder(null).position(new Vec3d(10.5, 65.0, 10.5)).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.END).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f).build();
        BackroomsPortalEntity backwards = BackroomsPortalEntity.builder(null).position(new Vec3d(10.5, 65.0, 10.5)).facingSource(PortalFacing.SOUTH)
                .facingDestination(PortalFacing.NORTH).destinationDim(World.NETHER).destinationPos(new Vec3d(110.5, 65.0, 10.5)).size(1.0f, 2.0f).build();
        Vec3d listener = new Vec3d(10.5, 65.0, 6.5);
        // La source, dans le Nether, est l'image de quatre blocs derriere le cadre proche ; le cadre lointain la voit vingt blocs plus loin.
        Vec3d source = near.toDestination(new Vec3d(10.5, 65.0, 14.5));
        PortalSound.Exit exit = PortalSound.bestExit(List.of(closed, invisible, elsewhere, backwards, far, near), World.NETHER, source, listener, 64.0);
        assertNotNull(exit, "Un cadre ouvert et visible vers le Nether rapproche le son.");
        assertEquals(new Vec3d(10.5, 65.0, 10.5), exit.point(), "Il ressort du cadre proche, au milieu.");
        assertEquals(4.0, exit.distance(), EPSILON, "Quatre blocs de la source au cadre.");
        assertSame(near, exit.portal(), "Le cadre par lequel le son ressort est retenu (le moteur audio y lit le point d'entree).");
        assertEquals(new Vec3d(10.5, 65.0, 10.5), PortalSound.bestExit(List.of(near, far), World.NETHER, source, listener, 64.0).point(),
                "Le cadre proche d'abord : le lointain, vu ensuite, ne le remplace pas.");
        assertEquals(new Vec3d(30.0, 65.0, 10.5), PortalSound.bestExit(List.of(far), World.NETHER, source, new Vec3d(30.5, 65.0, 6.5), 64.0).point(),
                "Seul, le cadre lointain sert quand meme : le son ressort par son bord le plus pres de l'image.");
        assertNull(PortalSound.bestExit(List.of(closed, invisible, elsewhere, backwards), World.NETHER, source, listener, 64.0),
                "Ferme, invisible, vers un autre monde ou de dos : aucun chemin.");
        assertNull(PortalSound.bestExit(List.of(), World.NETHER, source, listener, 64.0), "Sans portail, aucun chemin.");
    }

    @Test
    @DisplayName("La portee d'un son suit son volume, entre seize et soixante-quatre blocs")
    void radiusFollowsVolume() {
        assertEquals(PortalSound.MIN_RADIUS, PortalSound.radius(0.2f), EPSILON, "Un son faible porte quand meme seize blocs.");
        assertEquals(32.0, PortalSound.radius(2.0f), EPSILON, "Seize blocs par unite de volume.");
        assertEquals(PortalSound.MAX_RADIUS, PortalSound.radius(10000.0f), EPSILON, "Un tonnerre ne porte pas au-dela de soixante-quatre blocs.");
    }

    @Test
    @DisplayName("Le trajet jusqu'au cadre consomme un seizieme de volume par bloc, sans descendre sous zero")
    void travelToTheFrameCostsVolume() {
        assertEquals(1.0f, PortalSound.volumeThroughPortal(1.0f, 0.0), 1.0e-6f, "Un son colle au cadre passe entier.");
        assertEquals(0.5f, PortalSound.volumeThroughPortal(1.0f, 8.0), 1.0e-6f, "A huit blocs, la moitie.");
        assertEquals(0.0f, PortalSound.volumeThroughPortal(1.0f, 40.0), 1.0e-6f, "Trop loin : le son s'est eteint en chemin.");
        assertEquals(0.1f, PortalSound.volumeThroughPortal(0.2f, 8.0), 1.0e-6f,
                "Un son faible garde la portee de seize blocs : a huit blocs, la moitie de son volume, pas rien.");
        assertEquals(1.0f, PortalSound.volumeThroughPortal(2.0f, 16.0), 1.0e-6f, "Un son fort porte plus loin : a seize blocs, la moitie.");
    }

    @Test
    @DisplayName("Un son ressort du cadre par lequel l'auditeur voit l'autre cote, s'il est de l'autre cote et a portee")
    void soundLeavesThroughTheFrameTheListenerLooksThrough() {
        BackroomsPortalEntity portal = portal();
        Vec3d listener = new Vec3d(10.5, 65.0, 6.5);
        assertEquals(new Vec3d(10.5, 65.0, 10.5), PortalSound.exitPoint(portal, new Vec3d(10.5, 65.0, 14.5), listener, 16.0),
                "Dans l'axe, derriere le cadre : il ressort au milieu du cadre.");
        assertEquals(new Vec3d(11.0, 65.0, 10.5), PortalSound.exitPoint(portal, new Vec3d(13.5, 65.0, 14.5), listener, 16.0),
                "De biais : il ressort au bord du cadre le plus proche.");
        assertNull(PortalSound.exitPoint(portal, new Vec3d(10.5, 65.0, 8.5), listener, 16.0), "Une source de ce cote n'est pas vue par le cadre.");
        assertNull(PortalSound.exitPoint(portal, new Vec3d(10.5, 65.0, 14.5), new Vec3d(10.5, 65.0, 12.5), 16.0),
                "Un auditeur derriere la face n'entend rien par elle.");
        assertNull(PortalSound.exitPoint(portal, new Vec3d(10.5, 65.0, 30.5), listener, 16.0), "Une source trop loin du cadre.");
        assertNull(PortalSound.exitPoint(portal, new Vec3d(10.5, 65.0, 14.5), new Vec3d(10.5, 65.0, -9.5), 16.0),
                "Un auditeur trop loin du cadre.");
    }

    @Test
    @DisplayName("Sans portail vers le monde du son, il passe deux cadres : un portail vu a travers un autre, trajet additionne")
    void soundsPassTwoFrames() {
        BackroomsPortalEntity first = netherPortal(new Vec3d(10.5, 65.0, 10.5), true, true);
        Vec3d listener = new Vec3d(10.5, 65.0, 6.5);
        // Dans le Nether, huit blocs derriere le premier cadre (vu de l'auditeur), un portail vers l'End, tourne vers lui.
        Vec3d innerCenter = first.toDestination(new Vec3d(10.5, 65.0, 18.5));
        Vec3d listenerThere = first.toDestination(listener);
        PortalFacing innerFacing = listenerThere.z < innerCenter.z ? PortalFacing.NORTH : PortalFacing.SOUTH;
        BackroomsPortalEntity second = BackroomsPortalEntity.builder(null).position(innerCenter).facingSource(innerFacing)
                .facingDestination(innerFacing.opposite()).destinationDim(World.END).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f).build();
        // La source, dans l'End : l'image d'un point quatre blocs derriere le second cadre.
        Vec3d behindSecond = innerCenter.add(0.0, 0.0, innerFacing == PortalFacing.NORTH ? 4.0 : -4.0);
        Vec3d source = second.toDestination(behindSecond);
        Function<RegistryKey<World>, List<BackroomsPortalEntity>> portalsOf =
                world -> world.equals(World.NETHER) ? List.of(second) : List.of();
        PortalSound.Exit exit = PortalSound.bestExit(List.of(first), portalsOf, World.END, source, listener, 64.0);
        assertNotNull(exit, "Le son passe par les deux cadres.");
        assertSame(first, exit.portal(), "Il ressort du premier, celui du monde de l'auditeur.");
        assertEquals(new Vec3d(10.5, 65.0, 10.5), exit.point(), "Au milieu du premier cadre.");
        assertEquals(12.0, exit.distance(), EPSILON, "Quatre blocs jusqu'au second cadre, puis huit jusqu'au premier.");
        assertEquals(second.toDestination(innerCenter).distanceTo(exit.arrival()), 0.0, EPSILON, "Il est entre par le second cadre, cote de l'End.");
        assertNull(PortalSound.bestExit(List.of(first), World.END, source, listener, 64.0), "Sans monde intermediaire tenu, aucun chemin.");
        assertNull(PortalSound.bestExit(List.of(first), portalsOf, World.END, source, listener, 10.0), "Trop loin pour la portee du son.");
        BackroomsPortalEntity hidden = netherPortal(new Vec3d(10.5, 65.0, 10.5), true, false);
        assertNull(PortalSound.bestExit(List.of(hidden), portalsOf, World.END, source, listener, 64.0), "Un premier cadre invisible ne laisse rien passer.");
        BackroomsPortalEntity closedSecond = BackroomsPortalEntity.builder(null).position(innerCenter).facingSource(innerFacing)
                .facingDestination(innerFacing.opposite()).destinationDim(World.END).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f)
                .teleportable(false).build();
        assertNull(PortalSound.bestExit(List.of(first), world -> List.of(closedSecond), World.END, source, listener, 64.0), "Un second cadre ferme non plus.");
        assertNull(PortalSound.bestExit(List.of(first), portalsOf, World.OVERWORLD, source, listener, 64.0), "Le second cadre ne mene pas a ce monde.");
        Vec3d frontOfSecond = innerCenter.add(0.0, 0.0, innerFacing == PortalFacing.NORTH ? -40.0 : 40.0);
        assertNull(PortalSound.bestExit(List.of(first), portalsOf, World.END, second.toDestination(frontOfSecond), listener, 64.0),
                "Une source du mauvais cote du second cadre ne passe pas.");
        BackroomsPortalEntity farFirst = netherPortal(new Vec3d(10.5, 65.0, -60.5), true, true);
        assertNull(PortalSound.bestExit(List.of(farFirst), world -> List.of(second), World.END, source, listener, 64.0),
                "Un premier cadre que l'auditeur a derriere lui (de l'autre cote de son plan) ne passe rien.");
        BackroomsPortalEntity direct = BackroomsPortalEntity.builder(null).position(new Vec3d(10.5, 65.0, 10.5)).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.END).destinationPos(source.add(0.0, 0.0, -2.0)).size(1.0f, 2.0f).build();
        assertSame(direct, PortalSound.bestExit(List.of(first, direct), portalsOf, World.END, source, listener, 64.0).portal(),
                "Un cadre direct plus proche de la source passe avant deux cadres.");
        BackroomsPortalEntity farDirect = BackroomsPortalEntity.builder(null).position(new Vec3d(10.5, 65.0, 10.5)).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.END).destinationPos(source.add(0.0, 0.0, -30.0)).size(1.0f, 2.0f).build();
        assertSame(first, PortalSound.bestExit(List.of(farDirect, first), portalsOf, World.END, source, listener, 64.0).portal(),
                "Mais deux cadres plus courts qu'un cadre direct l'emportent.");
        assertEquals(0, PortalSound.NO_SECOND_FRAME.apply(World.NETHER).size(), "Aucun monde intermediaire par defaut.");
        Vec3d backOff = new Vec3d(10.5, 65.0, 0.5);
        assertNotNull(PortalSound.bestExit(List.of(first), portalsOf, World.END, source, backOff, 14.0),
                "Le second cadre se voit de plus loin que la portee : seul le trajet entier compte (quatre et huit blocs).");
        BackroomsPortalEntity hiddenSecond = BackroomsPortalEntity.builder(null).position(innerCenter).facingSource(innerFacing)
                .facingDestination(innerFacing.opposite()).destinationDim(World.END).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f)
                .visible(false).build();
        assertNull(PortalSound.bestExit(List.of(first), world -> List.of(hiddenSecond), World.END, source, listener, 64.0), "Un second cadre invisible non plus.");
        Vec3d close = new Vec3d(10.5, 65.0, 10.0);
        assertNull(PortalSound.bestExit(List.of(first), portalsOf, World.END, source, close, 10.0),
                "Chaque segment est a portee, mais le trajet entier (douze blocs) ne l'est pas.");
        BackroomsPortalEntity farther = BackroomsPortalEntity.builder(null).position(first.toDestination(new Vec3d(10.5, 65.0, 22.5)))
                .facingSource(innerFacing).facingDestination(innerFacing.opposite()).destinationDim(World.END).destinationPos(new Vec3d(0.5, 65.0, 0.5))
                .size(1.0f, 2.0f).build();
        assertEquals(12.0, PortalSound.bestExit(List.of(first), world -> List.of(farther, second), World.END, source, listener, 64.0).distance(), EPSILON,
                "Deux seconds cadres : le trajet le plus court l'emporte, vu en second.");
        assertEquals(12.0, PortalSound.bestExit(List.of(first), world -> List.of(second, farther), World.END, source, listener, 64.0).distance(), EPSILON,
                "Et vu en premier, il n'est pas remplace.");
    }
}
