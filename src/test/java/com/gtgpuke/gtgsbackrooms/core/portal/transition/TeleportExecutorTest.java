package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Detection de traversee d'un portail et cooldown des joueurs")
class TeleportExecutorTest {

    /** Boite d'un joueur debout. */
    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;
    /** Hauteur des yeux d'un joueur debout. */
    private static final double EYES = 1.62;

    private static BackroomsPortalEntity portal;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
        // Portail 1x2 oriente nord, centre en (0, 64, 0) : le cadre va de y=63 a y=65 et de x=-0.5 a x=0.5.
        portal = BackroomsPortalEntity.builder(null)
                .position(new Vec3d(0.0, 64.0, 0.0))
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH)
                .destinationDim(BackroomsConstants.LEVEL_ZERO_WORLD)
                .destinationPos(new Vec3d(1.5, 2.0, 1.5))
                .size(1.0f, 2.0f)
                .build();
    }

    private static boolean crossed(double lastX, double lastY, double lastZ, double currentX, double currentY, double currentZ) {
        return TeleportExecutor.crossedPortalPlane(portal, lastX, lastY, lastZ, currentX, currentY, currentZ, HALF_WIDTH, HEIGHT, 0.0, false);
    }

    @Test
    @DisplayName("Un joueur traverse aux yeux, qui doivent passer dans la hauteur du cadre ; un objet passe a toute hauteur des pieds")
    void playersCrossAtTheEyesWithinTheHeight() {
        double eyes = 1.62;
        assertTrue(TeleportExecutor.crossedPortalPlane(portal, 0.0, 63.0, -0.5, 0.0, 63.0, 0.3, HALF_WIDTH, HEIGHT, eyes, true),
                "Les yeux dans l'ouverture (63 a 65) : traversee.");
        assertFalse(TeleportExecutor.crossedPortalPlane(portal, 0.0, 63.6, -0.5, 0.0, 63.6, 0.3, HALF_WIDTH, HEIGHT, eyes, true),
                "Les yeux au-dessus du haut du cadre : pas de traversee pour un joueur.");
        assertTrue(TeleportExecutor.crossedPortalPlane(portal, 0.0, 63.6, -0.5, 0.0, 63.6, 0.3, HALF_WIDTH, HEIGHT, 0.0, false),
                "Aux pieds, a toute hauteur dans un cadre debout : un objet passe.");
    }

    @Test
    @DisplayName("Marcher du cote d'entree vers l'arriere, le corps dans le cadre, compte comme une traversee")
    void walkingThroughCounts() {
        assertTrue(crossed(0.0, 63.0, -0.5, 0.0, 63.0, 0.3), "Du nord vers le sud, les pieds au bas du cadre.");
        assertTrue(crossed(0.45, 63.0, -0.2, 0.45, 63.0, 0.1), "Le centre au bord du cadre, l'epaule au-dela.");
        assertTrue(crossed(0.0, 61.5, -0.2, 0.0, 61.5, 0.1), "Les pieds sous le cadre, la tete dedans.");
    }

    @Test
    @DisplayName("Revenir par l'arriere, passer a cote ou s'arreter avant le plan ne compte pas")
    void otherMovesDoNotCount() {
        assertFalse(crossed(0.0, 63.0, 0.3, 0.0, 63.0, -0.5), "Du sud vers le nord : mauvais sens.");
        assertFalse(crossed(0.85, 63.0, -0.2, 0.85, 63.0, 0.1), "Le corps passe juste a cote du cadre : il ressortirait dans le mur d'en face.");
        assertFalse(crossed(0.75, 63.0, -0.2, 0.75, 63.0, 0.1), "Le centre a cote du cadre, l'epaule dedans : on contourne le portail.");
        assertFalse(crossed(-3.0, 63.0, -0.5, -3.0, 63.0, 0.3), "Trois blocs a l'ouest du cadre.");
        assertFalse(crossed(0.0, 63.0, -2.0, 0.0, 63.0, -0.1), "Le plan n'est pas atteint.");
        assertFalse(crossed(0.0, 63.0, -0.5, 0.0, 63.0, -0.5), "Immobile du cote d'entree.");
        assertFalse(crossed(0.0, 70.0, -0.5, 0.0, 70.0, 0.3), "Bien au-dessus du cadre.");
        assertFalse(crossed(0.0, 58.0, -0.5, 0.0, 58.0, 0.3), "Sous le cadre, la tete comprise.");
    }

    @Test
    @DisplayName("Une entity traverse d'apres son deplacement du tick, mais pas en apparaissant ni en sautant de position")
    void entityCrossingIgnoresSpawnsAndJumps() {
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        item.setPosition(0.0, 63.0, 0.3);
        item.prevX = 0.0;
        item.prevY = 63.0;
        item.prevZ = -0.2;
        assertTrue(TeleportExecutor.crossedThisTick(portal, item), "Un pas du nord vers le sud, dans le cadre.");
        // Avant son premier tick, sa position precedente est l'origine du monde : loin au nord, le trajet couperait le plan.
        item.prevX = 0.0;
        item.prevY = 63.0;
        item.prevZ = -40.0;
        assertFalse(TeleportExecutor.crossedThisTick(portal, item), "Un saut de quarante blocs ne traverse rien.");
    }

    @Test
    @DisplayName("Un cadre couche se franchit aux yeux : en tombant dans un sol, en montant dans un plafond")
    void lyingFramesDecideAtTheEyes() {
        BackroomsPortalEntity floor = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.5, 0.0)).facingSource(PortalFacing.UP)
                .facingDestination(PortalFacing.DOWN).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.5, 0.0)).size(1.0f, 2.0f).build();
        BackroomsPortalEntity ceiling = BackroomsPortalEntity.builder(null).position(new Vec3d(0.5, 64.5, 0.0)).facingSource(PortalFacing.DOWN)
                .facingDestination(PortalFacing.UP).destinationDim(World.OVERWORLD).destinationPos(new Vec3d(10.5, 64.5, 0.0)).size(1.0f, 2.0f).build();
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);
        double eyes = floor.crossingHeight(item);
        assertTrue(eyes > 0.1 && eyes < 0.3, "Au sol, le point de traversee d'un objet est a hauteur de ses yeux : " + eyes + ".");
        assertEquals(eyes, ceiling.crossingHeight(item), 1.0e-9, "Au plafond aussi : la camera ne passe jamais le plan avant lui.");
        assertEquals(0.0, portal.crossingHeight(item), 1.0e-9, "Un cadre debout se franchit aux pieds pour un objet (aux yeux pour un joueur).");
        // Les pieds restent sous le plan (64,5) : seuls les yeux le passent, en tombant.
        assertTrue(TeleportExecutor.crossedPortalPlane(floor, item, 0.5, 64.4, 0.0, 0.5, 64.2, 0.0), "Les yeux passent le plan du sol : traversee.");
        assertFalse(TeleportExecutor.crossedPortalPlane(floor, item, 0.5, 64.2, 0.0, 0.5, 64.4, 0.0), "En remontant sous le plan, rien.");
        // Les yeux (0,2 au-dessus des pieds) passent le plan en montant ; les pieds, eux, restent dessous.
        assertTrue(TeleportExecutor.crossedPortalPlane(ceiling, item, 0.5, 64.2, 0.0, 0.5, 64.4, 0.0), "Les yeux passent le plan du plafond en montant : traversee.");
        assertFalse(TeleportExecutor.crossedPortalPlane(ceiling, item, 0.5, 64.4, 0.0, 0.5, 64.2, 0.0), "En descendant, on revient par l'arriere : rien.");
        assertFalse(TeleportExecutor.crossedPortalPlane(floor, item, 3.5, 64.4, 0.0, 3.5, 64.2, 0.0), "A cote du cadre : rien.");
    }

    /** Portail 1 x 2 d'une orientation a l'autre, entree en (0, 64, 0), arrivee en (20, 70, 0). */
    private static BackroomsPortalEntity between(PortalFacing entry, PortalFacing arrival) {
        return BackroomsPortalEntity.builder(null).position(new Vec3d(0.0, 64.0, 0.0)).facingSource(entry).facingDestination(arrival)
                .destinationDim(World.OVERWORLD).destinationPos(new Vec3d(20.0, 70.0, 0.0)).size(1.0f, 2.0f).build();
    }

    @Test
    @DisplayName("A la sortie, le corps reste du cote de la sortie et dans l'ouverture : pendu sous un plafond, debout dans un mur")
    void exitKeepsTheBodyOnTheExitSideAndInTheOpening() {
        // Debout vers debout : l'image des pieds, a peine devant le cadre, sans retouche (on garde son saut).
        Vec3d straight = TeleportExecutor.exitPoint(between(PortalFacing.NORTH, PortalFacing.SOUTH), new Vec3d(0.2, 63.4, 0.05), HEIGHT, 0.0);
        assertEquals(69.4, straight.y, 1.0e-9, "Debout vers debout, la hauteur des pieds est gardee.");
        // Debout vers plafond : les pieds arriveraient sur le plan, la tete dans le plafond ; le corps pend sous le plan.
        Vec3d hanging = TeleportExecutor.exitPoint(between(PortalFacing.NORTH, PortalFacing.DOWN), new Vec3d(0.0, 63.0, 0.05), HEIGHT, 0.0);
        assertEquals(70.0 - TeleportExecutor.EXIT_OFFSET - HEIGHT, hanging.y, 1.0e-9, "Sous le plafond, la tete juste sous le plan.");
        // Sol vers plafond : les yeux passent le plan, le corps pend deja dessous ; il n'est pas remonte.
        Vec3d falling = TeleportExecutor.exitPoint(between(PortalFacing.UP, PortalFacing.DOWN), new Vec3d(0.0, 61.0, 0.0), HEIGHT, EYES);
        assertEquals(67.0 - TeleportExecutor.EXIT_OFFSET, falling.y, 1.0e-9, "Deja sous le plan : la position est gardee.");
        // Sol vers mur : l'image des yeux donne la hauteur sur le mur, le corps debout sous elle, contre le plan ; les pieds
        // restent entre le bas de l'ouverture et le haut moins le corps.
        BackroomsPortalEntity floorToWall = between(PortalFacing.UP, PortalFacing.NORTH);
        Vec3d fromFloor = TeleportExecutor.exitPoint(floorToWall, new Vec3d(0.0, 62.3, -0.9), HEIGHT, EYES);
        assertEquals(69.2, fromFloor.y, 1.0e-9, "Pas plus haut que le haut moins le corps.");
        assertEquals(-0.08 - TeleportExecutor.EXIT_OFFSET, fromFloor.z, 1.0e-9, "Contre le plan, la ou les yeux le passent.");
        assertEquals(69.0, TeleportExecutor.exitPoint(floorToWall, new Vec3d(0.0, 62.3, 1.5), 0.0, 0.0).y, 1.0e-9, "Pas plus bas que le bas de l'ouverture.");
        assertEquals(69.0, TeleportExecutor.exitPoint(floorToWall, new Vec3d(0.0, 62.3, 0.0), 3.0, 0.0).y, 1.0e-9,
                "Un corps plus haut que l'ouverture part de son bas.");
        assertEquals(69.5, TeleportExecutor.exitPoint(floorToWall, new Vec3d(0.0, 62.3, 0.5), 0.25, 0.0).y, 1.0e-9, "Un petit corps garde sa hauteur.");
        // Mur vers sol : rien a retoucher, le corps est au-dessus du plan.
        Vec3d rising = TeleportExecutor.exitPoint(between(PortalFacing.NORTH, PortalFacing.UP), new Vec3d(0.0, 63.0, 0.05), HEIGHT, 0.0);
        assertTrue(rising.y > 70.0, "Sorti d'un sol, au-dessus du plan : " + rising + ".");
        // Sol vers sol : les yeux passent le plan quand les pieds sont dessous ; on ressort les pieds au ras de l'autre sol,
        // pas les yeux (plus haut que la chute ne le permettait).
        Vec3d bounce = TeleportExecutor.exitPoint(between(PortalFacing.UP, PortalFacing.UP), new Vec3d(0.0, 62.0, 0.0), HEIGHT, EYES);
        assertEquals(70.0 + TeleportExecutor.EXIT_OFFSET, bounce.y, 1.0e-9, "Les pieds au ras du plan d'arrivee.");
        // Plafond vers sol : les yeux passent le plan en montant ; on ressort du sol, les pieds au ras du plan.
        Vec3d jump = TeleportExecutor.exitPoint(between(PortalFacing.DOWN, PortalFacing.UP), new Vec3d(0.0, 62.4, 0.0), HEIGHT, EYES);
        assertEquals(70.0 + TeleportExecutor.EXIT_OFFSET, jump.y, 1.0e-9, "Sorti d'un sol, debout dessus.");
    }

    @Test
    @DisplayName("Par une arrivee retournee, on ressort tete en bas : le corps se redresse dans l'ouverture et la chute s'efface")
    void upsideDownArrivalKeepsTheBodyInTheOpening() {
        BackroomsPortalEntity flipped = between(PortalFacing.NORTH, PortalFacing.SOUTH_UPSIDE_DOWN);
        // Les pieds au bas du cadre d'entree ont leur image en haut du cadre d'arrivee : le corps redresse tient dans l'ouverture.
        Vec3d exit = TeleportExecutor.exitPoint(flipped, new Vec3d(0.2, 63.0, 0.05), HEIGHT, 0.0);
        assertEquals(69.2, exit.y, 1.0e-9, "Les pieds au plus haut ou le corps tient encore dans l'ouverture.");
        assertEquals(19.8, exit.x, 1.0e-9, "Retourne, ce qui etait a droite du cadre ressort a sa gauche (droit, en 20,2).");
        assertEquals(0.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.NORTH, PortalFacing.SOUTH_UPSIDE_DOWN, 12.0f),
                "Le bas devient le haut : la chute s'efface.");
        Vec3d falling = PortalFacing.transformVector(PortalFacing.NORTH, PortalFacing.SOUTH_UPSIDE_DOWN, new Vec3d(0.0, -0.5, 0.3));
        assertEquals(0.0, falling.subtract(new Vec3d(0.0, 0.5, 0.3)).length(), 1.0e-9, "Une chute ressort en montee, l'avance gardee.");
    }

    @Test
    @DisplayName("La chute n'est gardee que si le bas reste le bas de l'autre cote")
    void fallIsKeptOnlyIfDownStaysDown() {
        assertEquals(12.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.NORTH, PortalFacing.EAST, 12.0f), "Debout vers debout : gardee.");
        assertEquals(12.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.UP, PortalFacing.DOWN, 12.0f), "Du sol au plafond, on tombe encore.");
        assertEquals(12.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.DOWN, PortalFacing.UP, 12.0f), "Du plafond au sol aussi.");
        assertEquals(0.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.UP, PortalFacing.NORTH, 12.0f), "Tombe dans un sol, sorti d'un mur : effacee.");
        assertEquals(0.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.UP, PortalFacing.UP, 12.0f), "D'un sol a un sol, on remonte : effacee.");
        assertEquals(0.0f, TeleportExecutor.fallAfterCrossing(PortalFacing.NORTH, PortalFacing.UP, 12.0f), "D'un mur a un sol : effacee.");
    }

    @Test
    @DisplayName("Un joueur qui vient de traverser attend la duree du cooldown avant de repasser")
    void cooldownWindow() {
        UUID player = UUID.randomUUID();
        int cooldown = 20;
        assertFalse(TeleportExecutor.isOnCooldown(player, 100L, cooldown), "Jamais passe : pas de cooldown.");
        TeleportExecutor.markCrossed(player, 100L);
        assertTrue(TeleportExecutor.isOnCooldown(player, 100L, cooldown), "Au tick de la traversee : cooldown.");
        assertTrue(TeleportExecutor.isOnCooldown(player, 100L + cooldown - 1, cooldown), "Juste avant la fin : cooldown.");
        assertFalse(TeleportExecutor.isOnCooldown(player, 100L + cooldown, cooldown), "A la fin du cooldown : libre.");
    }

    @Test
    @DisplayName("Une annonce de traversee n'admet qu'une vitesse et un regard finis et bornes")
    void announcedMotionMustBeSane() {
        assertTrue(TeleportExecutor.isSane(new Vec3d(0.3, -0.08, 2.0), 725.0f, -90.0f), "Course, lacet de deux tours, regard au zenith.");
        assertFalse(TeleportExecutor.isSane(new Vec3d(Double.NaN, 0.0, 0.0), 0.0f, 0.0f), "Vitesse NaN en x.");
        assertFalse(TeleportExecutor.isSane(new Vec3d(0.0, Double.POSITIVE_INFINITY, 0.0), 0.0f, 0.0f), "Vitesse infinie en y.");
        assertFalse(TeleportExecutor.isSane(new Vec3d(0.0, 0.0, Double.NaN), 0.0f, 0.0f), "Vitesse NaN en z.");
        assertFalse(TeleportExecutor.isSane(new Vec3d(0.0, 0.0, 12.0), 0.0f, 0.0f), "Vitesse demesuree.");
        assertFalse(TeleportExecutor.isSane(Vec3d.ZERO, Float.POSITIVE_INFINITY, 0.0f), "Lacet infini.");
        assertFalse(TeleportExecutor.isSane(Vec3d.ZERO, 0.0f, 90.5f), "Regard au-dela de la verticale.");
        assertFalse(TeleportExecutor.isSane(Vec3d.ZERO, 0.0f, Float.NaN), "Regard NaN.");
    }
}
