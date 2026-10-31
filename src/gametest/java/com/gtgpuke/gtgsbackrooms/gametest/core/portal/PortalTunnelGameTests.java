package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalCrossPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu des blocs caches derriere le plan d'un portail : un cadre pose sur un sol plein s'ouvre comme une trappe sous
 * qui y tombe et lui garde son elan, un cadre pose contre un mur se traverse comme si le mur n'y etait pas, un corps
 * engage dans un cadre n'est pas dans le mur qu'il traverse et le reste quelques ticks une fois le plan passe, sans que la
 * face arriere du cadre lui cache le cote d'ou il vient. Qui sort d'un cadre d'arrivee au sol y retombe aussitot, meme
 * pose sur la pierre, et un joueur qui tombe dans un cadre au sol le traverse aux yeux. Sur le bord d'un cadre en l'air, qui ne peut pas le traverser se pose sur son plan.
 *
 * <p>Le sol de l'arene est sous elle : la pierre posee a la premiere couche (Y = 0) fait un sol plein a Y = 1.
 */
public final class PortalTunnelGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-3;
    /** Vitesse de marche d'un joueur simule qui entre dans un cadre, en blocs par tick. */
    private static final double WALK = 0.2;
    /** Ecart a la poussee la plus forte une fois le saut ajoute : le saut s'allonge par pas, la poussee retombe juste dessous. */
    private static final double RISE_MARGIN = 0.05;
    /** Vitesse des objets qui filent vers un mur, en blocs par tick. */
    private static final double ITEM_SPEED = 0.3;

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aFloorFrameOnSolidGroundOpensUnderAFallingBodyAndKeepsItsSpeed(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 2), new BlockPos(7, 0, 5), Blocks.STONE);
        // Un cadre au sol a ras de la pierre, vers un cadre au plafond en l'air : on y tombe, on en ressort en tombant.
        Vec3d entry = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 6.0, 4.0));
        BackroomsPortalEntity floor = spawn(world, entry, PortalFacing.UP, PortalFacing.DOWN, arrival);
        // Des porte-armures : des corps qui tombent, sans comportement qui les ferait deriver hors d'un cadre d'un bloc de large.
        ArmorStandEntity body = stand(world, entry.add(0.0, 4.0, 0.0), true);
        ArmorStandEntity beside = stand(world, context.getAbsolute(new Vec3d(6.5, 1.0, 4.0)), true);
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(Math.abs(body.getX() - arrival.x) < 0.5, "Le corps n'est pas encore passe."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(body.getVelocity().y < -0.5,
                                "Tombe sur un cadre pose sur la pierre, il passe et ressort en tombant, son elan garde : " + body.getVelocity() + ".");
                        context.assertTrue(body.getY() + body.getHeight() < arrival.y, "Il ressort sous le cadre d'arrivee : " + body.getPos() + ".");
                        context.assertTrue(beside.isOnGround() && Math.abs(beside.getY() - entry.y) < EPSILON,
                                "A cote de l'ouverture, la pierre porte encore : " + beside.getPos() + ".");
                        context.assertTrue(world.getBlockState(context.getAbsolutePos(new BlockPos(4, 0, 4))).isOf(Blocks.STONE),
                                "La pierre sous le cadre est toujours la : elle n'est cachee qu'a qui s'y engage.");
                    } finally {
                        body.discard();
                        beside.discard();
                        floor.discard();
                        fill(context, new BlockPos(2, 0, 2), new BlockPos(7, 0, 5), Blocks.AIR);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aBodyOnTheEdgeOfAFloorFrameInTheAirStandsOnItsPlane(TestContext context) {
        ServerWorld world = context.getWorld();
        // Un cadre au sol en l'air, trois blocs au-dessus du sol de l'arene : ouverture de 4 a 5 en X, de 3 a 5 en Z.
        Vec3d frame = context.getAbsolute(new Vec3d(4.5, 3.0, 4.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 6.0, 4.0));
        BackroomsPortalEntity floor = spawn(world, frame, PortalFacing.UP, PortalFacing.DOWN, arrival);
        // Le centre hors de l'ouverture, la boite a cheval sur son bord ; tout a cote ; en plein milieu.
        ArmorStandEntity edge = stand(world, frame.add(0.0, 1.0, 1.2), true);
        ArmorStandEntity beside = stand(world, frame.add(0.0, 1.0, 1.6), true);
        ArmorStandEntity middle = stand(world, frame.add(0.0, 1.0, 0.0), true);
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(edge.isOnGround() && beside.isOnGround() && Math.abs(middle.getX() - arrival.x) < 0.5,
                        "Les corps ne sont pas encore poses."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(Math.abs(edge.getY() - frame.y) < EPSILON,
                                "Sur le bord, il ne peut pas traverser : il se pose sur le plan, en l'air : " + edge.getPos() + ".");
                        context.assertTrue(beside.getY() < frame.y - 2.0, "Tout a cote du cadre, il tombe au sol : " + beside.getPos() + ".");
                        context.assertTrue(middle.getY() < arrival.y, "En plein milieu, il passe : " + middle.getPos() + ".");
                    } finally {
                        edge.discard();
                        beside.discard();
                        middle.discard();
                        floor.discard();
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void aStandingFrameAgainstAWallLetsAnObjectThroughTheWall(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 6), new BlockPos(7, 3, 6), Blocks.STONE);
        // Un cadre debout regardant le nord, son plan sur la face du mur : derriere lui, le mur plein.
        Vec3d entry = context.getAbsolute(new Vec3d(4.5, 1.0, 6.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 1.0, 14.0));
        BackroomsPortalEntity wall = spawn(world, entry, PortalFacing.NORTH, PortalFacing.SOUTH, arrival);
        ItemEntity through = item(world, context.getAbsolute(new Vec3d(4.5, 0.2, 4.0)), Items.FEATHER);
        ItemEntity against = item(world, context.getAbsolute(new Vec3d(6.5, 0.2, 4.0)), Items.STICK);
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(through.hasPortalCooldown(), "La plume n'est pas encore passee."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(Math.abs(through.getX() - arrival.x) < 0.5 && through.getZ() > arrival.z,
                                "La plume passe le mur par le cadre et ressort de l'arrivee : " + through.getPos() + ".");
                        context.assertTrue(!against.hasPortalCooldown() && against.getZ() < entry.z && against.getZ() > entry.z - 0.5,
                                "A cote du cadre, le baton bute contre le mur : " + against.getPos() + ".");
                    } finally {
                        through.discard();
                        against.discard();
                        wall.discard();
                        fill(context, new BlockPos(2, 0, 6), new BlockPos(7, 3, 6), Blocks.AIR);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void aBodyEngagedInAFrameIsNotInTheWallBehindIt(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 6), new BlockPos(7, 3, 6), Blocks.STONE);
        Vec3d entry = context.getAbsolute(new Vec3d(4.5, 1.0, 6.0));
        BackroomsPortalEntity wall = spawn(world, entry, PortalFacing.NORTH, PortalFacing.SOUTH, context.getAbsolute(new Vec3d(14.5, 1.0, 14.0)));
        BlockPos behind = context.getAbsolutePos(new BlockPos(4, 1, 6));
        BlockPos aside = context.getAbsolutePos(new BlockPos(5, 1, 6));
        // Les pieds a un dixieme du plan, devant lui : les yeux, plus larges que le corps n'est profond, entrent dans le mur.
        ArmorStandEntity body = stand(world, entry.add(0.0, -1.0, -0.1), false);
        try {
            context.assertTrue(!body.isInsideWall(), "Engage dans le cadre, le corps n'est pas dans le mur qu'il traverse.");
            context.assertTrue(PortalCollision.hiddenFrom(body, behind), "Le bloc derriere l'ouverture lui est cache.");
            context.assertTrue(!PortalCollision.hiddenFrom(body, aside), "A cote de l'ouverture, le mur reste.");
            context.assertTrue(PortalCollision.tunnel(wall, wall.getBoundingBox()) == null, "Un portail ne s'engage dans aucun autre.");
            wall.setTeleportable(false);
            context.assertTrue(body.isInsideWall() && !PortalCollision.hiddenFrom(body, behind),
                    "Portail ferme : le mur est la, et le corps dedans.");
        } finally {
            wall.discard();
            fill(context, new BlockPos(2, 0, 6), new BlockPos(7, 3, 6), Blocks.AIR);
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void anEngagementOutlivesThePlaneForAFewTicksWithoutTheBackFace(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d center = context.getAbsolute(new Vec3d(4.5, 1.0, 6.0));
        Vec3d far = context.getAbsolute(new Vec3d(14.5, 1.0, 20.0));
        // Un cadre double face (deux portails dos a dos), un cadre en travers au meme centre, un autre un demi-bloc derriere.
        BackroomsPortalEntity front = spawn(world, center, PortalFacing.NORTH, PortalFacing.SOUTH, far);
        BackroomsPortalEntity back = spawn(world, center, PortalFacing.SOUTH, PortalFacing.NORTH, far);
        BackroomsPortalEntity across = spawn(world, center, PortalFacing.EAST, PortalFacing.WEST, far);
        BackroomsPortalEntity further = spawn(world, center.add(0.0, 0.0, 0.5), PortalFacing.SOUTH, PortalFacing.NORTH, far);
        // Un cadre a une face, a part, pour l'oubli.
        Vec3d single = context.getAbsolute(new Vec3d(14.5, 1.0, 6.0));
        BackroomsPortalEntity alone = spawn(world, single, PortalFacing.NORTH, PortalFacing.SOUTH, far);
        BackroomsPortalEntity doomed = spawn(world, context.getAbsolute(new Vec3d(18.5, 1.0, 6.0)), PortalFacing.NORTH, PortalFacing.SOUTH, far);
        ArmorStandEntity crossing = stand(world, center.add(0.0, -1.0, -0.1), false);
        ArmorStandEntity deeper = stand(world, center.add(0.0, -1.0, -0.1), false);
        ArmorStandEntity forgotten = stand(world, single.add(0.0, -1.0, -0.1), false);
        ArmorStandEntity orphan = stand(world, doomed.getPos().add(0.0, -1.0, -0.1), false);
        BackroomsPortalEntity left = spawn(world, context.getAbsolute(new Vec3d(9.5, 1.0, 14.0)), PortalFacing.NORTH, PortalFacing.SOUTH, far);
        ServerPlayerEntity traveller = GameTestPlayers.connect(context, GameMode.CREATIVE, left.getPos().add(0.0, -1.0, -0.1), 0.0f, 0.0f);
        long[] engagedAt = {0L};
        try {
            context.assertTrue(PortalCollision.tunnel(crossing, crossing.getBoundingBox()) == front, "Devant le cadre, le corps s'engage dans la face avant.");
            moveTo(crossing, center.add(0.0, -1.0, 0.3));
            context.assertTrue(PortalCollision.tunnel(crossing, crossing.getBoundingBox()) == front,
                    "Le plan passe, il traverse encore la face avant : la face arriere, devant lui, ne lui cache pas le cote d'ou il vient.");
            moveTo(crossing, center.add(0.7, -1.0, 0.3));
            context.assertTrue(PortalCollision.tunnel(crossing, crossing.getBoundingBox()) == null,
                    "Sorti de la largeur de l'ouverture, il n'y est plus engage.");
            moveTo(crossing, center.add(0.0, -1.0, -0.1));
            context.assertTrue(PortalCollision.tunnel(crossing, crossing.getBoundingBox()) == front, "Revenu devant le cadre, il s'y engage de nouveau.");
            moveTo(crossing, center.add(0.1, -1.0, 0.3));
            context.assertTrue(PortalCollision.tunnel(crossing, crossing.getBoundingBox()) == across,
                    "Un cadre au meme centre qui n'est pas dos a dos avec lui compte.");

            context.assertTrue(PortalCollision.tunnel(deeper, deeper.getBoundingBox()) == front, "Un autre corps s'engage dans la face avant.");
            moveTo(deeper, center.add(0.0, -1.0, 0.6));
            context.assertTrue(PortalCollision.tunnel(deeper, deeper.getBoundingBox()) == further, "Un cadre derriere le premier, a un autre centre, compte.");

            context.assertTrue(PortalCollision.tunnel(orphan, orphan.getBoundingBox()) == doomed, "Engage dans un cadre...");
            moveTo(orphan, doomed.getPos().add(0.0, -1.0, 0.3));
            doomed.discard();
            context.assertTrue(PortalCollision.tunnel(orphan, orphan.getBoundingBox()) == null, "... retire depuis : il n'y est plus engage.");

            context.assertTrue(PortalCollision.tunnel(forgotten, forgotten.getBoundingBox()) == alone, "Devant un cadre a une face, il s'y engage.");
            engagedAt[0] = world.getTime();
            moveTo(forgotten, single.add(0.0, -1.0, 0.3));

            // Un joueur engage qui change de monde (portail vanilla, commande) : le cadre de l'autre monde ne compte plus.
            context.assertTrue(PortalCollision.tunnel(traveller, traveller.getBoundingBox()) == left, "Devant un cadre, le joueur s'y engage.");
            traveller.teleport(GameTestServerWorlds.nether(context), traveller.getX(), traveller.getY(), traveller.getZ(), 0.0f, 0.0f);
            context.assertTrue(PortalCollision.tunnel(traveller, traveller.getBoundingBox()) == null, "Parti dans un autre monde, il n'y est plus engage.");
        } catch (RuntimeException failure) {
            discard(front, back, across, further, alone, doomed, left);
            throw failure;
        } finally {
            GameTestPlayers.disconnect(traveller);
        }
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(world.getTime() >= engagedAt[0] + PortalCollision.CROSSING_TICKS / 2, "Quelques ticks d'abord."))
                .createAndAddReported(() -> context.assertTrue(PortalCollision.tunnel(forgotten, forgotten.getBoundingBox()) == alone,
                        "Quelques ticks apres, le plan passe, il traverse encore."))
                .createAndAdd(() -> context.assertTrue(world.getTime() > engagedAt[0] + PortalCollision.CROSSING_TICKS + 1, "L'engagement n'est pas encore oublie."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(PortalCollision.tunnel(forgotten, forgotten.getBoundingBox()) == null,
                                "Plus de " + PortalCollision.CROSSING_TICKS + " ticks apres, l'engagement est oublie.");
                    } finally {
                        discard(front, back, across, further, alone, doomed, left);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 400)
    public void whoComesOutOfAnArrivalFloorFrameFallsBackAtOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 2), new BlockPos(8, 0, 5), Blocks.STONE);
        // Deux cadres au sol, l'un vers l'autre : A a ras de la pierre, B en l'air, trois blocs au-dessus du sol de l'arene.
        Vec3d a = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d b = context.getAbsolute(new Vec3d(14.5, 3.0, 4.0));
        BackroomsPortalEntity toB = spawn(world, a, PortalFacing.UP, PortalFacing.UP, b);
        BackroomsPortalEntity toA = spawn(world, b, PortalFacing.UP, PortalFacing.UP, a);
        ArmorStandEntity body = stand(world, a.add(0.0, 3.0, 0.0), true);
        boolean[] restedOnA = {false};
        context.createTimedTaskRunner()
                // Tombe dans A, il ressort de B vers le haut, retombe dans B (en l'air) et ressort de A.
                .createAndAdd(() -> context.assertTrue(Math.abs(body.getX() - b.x) < 0.5, "Pas encore ressorti de B."))
                .createAndAdd(() -> context.assertTrue(Math.abs(body.getX() - a.x) < 0.5, "Pas encore retombe dans B."))
                // Sorti de A, il retombe dedans aussitot, la pierre sous le cadre n'y change rien : il ne s'y pose jamais.
                .createAndAdd(() -> {
                    restedOnA[0] |= body.isOnGround() && Math.abs(body.getX() - a.x) < 0.5;
                    context.assertTrue(Math.abs(body.getX() - b.x) < 0.5, "Sorti de A, il n'y est pas encore retombe : " + body.getPos() + ".");
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(!restedOnA[0], "Sorti de A, il s'est pose sur la pierre sous le cadre au lieu d'y retomber.");
                    } finally {
                        body.discard();
                        toA.discard();
                        toB.discard();
                        fill(context, new BlockPos(2, 0, 2), new BlockPos(8, 0, 5), Blocks.AIR);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 400)
    public void aSlowBodyFallingBackIntoAnArrivalFloorFrameDoesNotSinkIntoTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 2), new BlockPos(16, 0, 5), Blocks.STONE);
        // Deux cadres au sol, l'un vers l'autre, tous deux a ras de la pierre.
        Vec3d a = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d c = context.getAbsolute(new Vec3d(14.5, 1.0, 4.0));
        BackroomsPortalEntity toC = spawn(world, a, PortalFacing.UP, PortalFacing.UP, c);
        BackroomsPortalEntity toA = spawn(world, c, PortalFacing.UP, PortalFacing.UP, a);
        // Un objet qui glisse dans A presque sans elan : il ressort de C a peine plus vite et y retombe avant la fin du cooldown
        // de portail qu'il vient de recevoir.
        ItemEntity body = new ItemEntity(world, a.x, a.y + 0.05, a.z, new ItemStack(Items.DIAMOND));
        body.setPickupDelayInfinite();
        body.setVelocity(0.0, -0.1, 0.0);
        world.spawnEntity(body);
        boolean[] sunk = {false};
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(Math.abs(body.getX() - c.x) < 0.5, "Pas encore ressorti de C."))
                // Ses yeux ne passent jamais le plan de C sans le traverser : il ne s'enfonce pas dans la pierre, il repart par C.
                .createAndAdd(() -> {
                    sunk[0] |= Math.abs(body.getX() - c.x) < 0.5 && body.getEyeY() < c.y - 0.3;
                    context.assertTrue(Math.abs(body.getX() - a.x) < 0.5, "Sorti de C, il n'y est pas encore retombe : " + body.getPos() + ".");
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(!sunk[0], "Retombe dans C pendant son cooldown, il est passe sous le plan sans traverser.");
                    } finally {
                        body.discard();
                        toA.discard();
                        toC.discard();
                        fill(context, new BlockPos(2, 0, 2), new BlockPos(16, 0, 5), Blocks.AIR);
                    }
                })
                .completeIfSuccessful();
    }

    /**
     * Entre en marchant par un cadre debout et sorti d'un cadre couche au sol, un joueur est pousse dans le sens de son
     * regard, juste assez pour retomber au-dela du bord devant lui : un petit saut s'ajoute quand la poussee la plus forte
     * ne suffit pas, au plus celui d'un saut vanilla. D'un cadre au sol a un autre, rien ne s'ajoute.
     */
    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void aPlayerComingOutOfAFloorFrameIsPushedPastTheEdgeAhead(TestContext context) {
        ServerWorld world = context.getWorld();
        // Trois cadres debout vers des cadres au sol, et un cadre au sol vers un autre.
        Vec3d high = context.getAbsolute(new Vec3d(3.5, 2.0, 4.0));
        Vec3d low = context.getAbsolute(new Vec3d(9.5, 4.0, 4.0));
        Vec3d tall = context.getAbsolute(new Vec3d(15.5, 3.0, 4.0));
        Vec3d floor = context.getAbsolute(new Vec3d(21.5, 3.0, 4.0));
        BackroomsPortalEntity fromHigh = sized(world, high, PortalFacing.NORTH, PortalFacing.UP, context.getAbsolute(new Vec3d(3.5, 10.0, 16.0)), 2.0f);
        BackroomsPortalEntity fromLow = sized(world, low, PortalFacing.NORTH, PortalFacing.UP, context.getAbsolute(new Vec3d(9.5, 10.0, 16.0)), 2.0f);
        BackroomsPortalEntity fromTall = sized(world, tall, PortalFacing.NORTH, PortalFacing.UP, context.getAbsolute(new Vec3d(15.5, 10.0, 16.0)), 4.0f);
        BackroomsPortalEntity fromFloor = sized(world, floor, PortalFacing.UP, PortalFacing.UP, context.getAbsolute(new Vec3d(21.5, 10.0, 16.0)), 2.0f);
        // Les pieds au bas d'un cadre de deux : les yeux en haut, qui vont a l'arriere du cadre couche, loin du bord devant.
        ServerPlayerEntity atBack = walkInto(context, fromHigh, high.add(0.0, -1.0, -0.05));
        // Les pieds sous un cadre de deux : les yeux en bas, qui vont pres du bord devant.
        ServerPlayerEntity atFront = walkInto(context, fromLow, low.add(0.0, -2.5, -0.05));
        // Les pieds au milieu d'un cadre de quatre : les yeux tout en haut, a plus de trois blocs et demi du bord devant.
        ServerPlayerEntity farFromEdge = walkInto(context, fromTall, tall.add(0.0, 0.0, -0.05));
        ServerPlayerEntity falling = GameTestPlayers.connect(context, GameMode.CREATIVE, floor.add(0.0, 0.05, 0.0), 0.0f, 90.0f);
        try {
            GameTestPlayers.receive(falling, new PortalCrossPayload(fromFloor.getUuid(), floor.add(0.0, -0.3 - falling.getStandingEyeHeight(), 0.0),
                    new Vec3d(0.0, -0.5, 0.0), 0.0f, 90.0f));
            requireCrossed(context, atBack, fromHigh);
            requireCrossed(context, atFront, fromLow);
            requireCrossed(context, farFromEdge, fromTall);
            requireCrossed(context, falling, fromFloor);
            Vec3d back = atBack.getVelocity();
            context.assertTrue(horizontal(back) > TeleportExecutor.MAX_EDGE_PUSH - RISE_MARGIN && horizontal(back) <= TeleportExecutor.MAX_EDGE_PUSH + EPSILON
                    && back.y > WALK + EPSILON && back.y < TeleportExecutor.MAX_EXIT_RISE,
                    "Sorti a l'arriere du cadre : presque la poussee la plus forte et un petit saut, " + back + ".");
            context.assertTrue(alongLook(atBack), "Poussee dans le sens du regard : " + back + ", lacet " + atBack.getYaw() + ".");
            Vec3d front = atFront.getVelocity();
            context.assertTrue(horizontal(front) > EPSILON && horizontal(front) < TeleportExecutor.MAX_EDGE_PUSH && Math.abs(front.y - WALK) < EPSILON,
                    "Sorti pres du bord devant : une petite poussee, sans saut, " + front + ".");
            Vec3d far = farFromEdge.getVelocity();
            context.assertTrue(Math.abs(horizontal(far) - TeleportExecutor.MAX_EDGE_PUSH) < EPSILON
                    && Math.abs(far.y - TeleportExecutor.MAX_EXIT_RISE) < EPSILON, "Trop loin du bord : poussee et saut au plus, " + far + ".");
            Vec3d up = falling.getVelocity();
            context.assertTrue(horizontal(up) < EPSILON && up.y > 0.0, "D'un cadre au sol a un autre, rien ne s'ajoute : " + up + ".");
        } finally {
            GameTestPlayers.disconnect(atBack);
            GameTestPlayers.disconnect(atFront);
            GameTestPlayers.disconnect(farFromEdge);
            GameTestPlayers.disconnect(falling);
            discard(fromHigh, fromLow, fromTall, fromFloor);
        }
        context.complete();
    }

    /** Un joueur, pieds la, qui annonce entrer en marchant dans un cadre debout tourne vers le nord (il va vers le sud). */
    private static ServerPlayerEntity walkInto(TestContext context, BackroomsPortalEntity portal, Vec3d feet) {
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, feet, 0.0f, 0.0f);
        GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet, new Vec3d(0.0, 0.0, WALK), 0.0f, 0.0f));
        return player;
    }

    private static void requireCrossed(TestContext context, ServerPlayerEntity player, BackroomsPortalEntity portal) {
        context.assertTrue(player.getPos().squaredDistanceTo(portal.destinationPos()) < 16.0, "Pas de l'autre cote : " + player.getPos() + ".");
    }

    private static double horizontal(Vec3d velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    /** La vitesse horizontale du joueur va dans le sens de son regard. */
    private static boolean alongLook(ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        Vec3d velocity = player.getVelocity();
        return (-Math.sin(yaw) * velocity.x + Math.cos(yaw) * velocity.z) / horizontal(velocity) > 1.0 - EPSILON;
    }

    /** Portail d'un bloc de large et de cette hauteur, ajoute au monde. */
    private static BackroomsPortalEntity sized(ServerWorld world, Vec3d center, PortalFacing source, PortalFacing exit, Vec3d arrival, float height) {
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(source)
                .facingDestination(exit)
                .destinationDim(World.OVERWORLD)
                .destinationPos(arrival)
                .size(1.0f, height)
                .build();
        world.spawnEntity(portal);
        return portal;
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void aPlayerFallingIntoAFloorFrameCrossesOnceTheirEyesPassThePlane(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d frame = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 6.0, 4.0));
        BackroomsPortalEntity floor = spawn(world, frame, PortalFacing.UP, PortalFacing.DOWN, arrival);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, frame.add(0.0, 0.05, 0.0), 0.0f, 90.0f);
        context.runAtTick(2, () -> {
            try {
                // Il tombe dans le cadre : ses yeux ont passe le plan de trois dixiemes, ses pieds sont bien plus bas. Le serveur
                // juge l'annonce aux yeux, qui decident de la traversee d'un cadre couche.
                GameTestPlayers.receive(player, new PortalCrossPayload(floor.getUuid(), frame.add(0.0, -0.3 - player.getStandingEyeHeight(), 0.0),
                        new Vec3d(0.0, -1.0, 0.0), 0.0f, 90.0f));
                context.assertTrue(Math.abs(player.getX() - arrival.x) < 0.5 && player.getY() < arrival.y,
                        "Les yeux juste passes, l'annonce est acceptee : il ressort sous le cadre d'arrivee : " + player.getPos() + ".");
            } finally {
                GameTestPlayers.disconnect(player);
                floor.discard();
            }
            context.complete();
        });
    }

    /**
     * Un joueur franchit tout cadre aux yeux, et sa tete comme ses epaules doivent passer l'ouverture : engage, il bute sur
     * les bords du cadre qui depassent, et un cadre trop bas pour ses yeux ne se franchit pas, la ou un objet passe aux pieds.
     */
    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void aPlayerCrossesAtTheEyesAndMustFitTheOpening(TestContext context) {
        ServerWorld world = context.getWorld();
        // Cadre 1 x 2 oriente nord, centre en (8,5 ; 3 ; 8,5) : ouverture x 8..9, y 2..4, plan z = 8,5, entree au nord.
        Vec3d center = context.getAbsolute(new Vec3d(8.5, 3.0, 8.5));
        BackroomsPortalEntity frame = spawn(world, center, PortalFacing.NORTH, PortalFacing.SOUTH, context.getAbsolute(new Vec3d(8.5, 3.0, 20.5)));
        // Cadre 1 x 1 a cote, centre en (12,5 ; 2,5 ; 8,5) : ouverture y 2..3, trop bas pour les yeux d'un joueur debout.
        Vec3d lowCenter = context.getAbsolute(new Vec3d(12.5, 2.5, 8.5));
        BackroomsPortalEntity low = BackroomsPortalEntity.builder(world).position(lowCenter).facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH).destinationDim(World.OVERWORLD).destinationPos(context.getAbsolute(new Vec3d(12.5, 2.5, 20.5)))
                .size(1.0f, 1.0f).build();
        world.spawnEntity(low);
        PlayerEntity player = new PlayerEntity(world, BlockPos.ORIGIN, 0.0f, new GameProfile(UUID.randomUUID(), "bord")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        ServerPlayerEntity real = GameTestPlayers.connect(context, GameMode.SURVIVAL, center.add(0.0, -1.0, -0.5), 0.0f, 0.0f);
        ItemEntity item = item(world, lowCenter.add(0.0, -0.5, -0.5), Items.STONE);
        try {
            double eyes = player.getEyeY() - player.getY();
            context.assertTrue(Math.abs(frame.crossingHeight(player) - eyes) < 1.0e-9 && Math.abs(low.crossingHeight(player) - eyes) < 1.0e-9,
                    "Un joueur franchit un cadre debout aux yeux : " + frame.crossingHeight(player) + ".");
            context.assertTrue(frame.crossingHeight(item) == 0.0, "Un objet le franchit aux pieds.");
            // Aligne devant le cadre, la tete dans l'ouverture : engage, rien de l'autre cote ne gene, aucun bord ne depasse.
            player.setPosition(center.x, center.y - 1.0, center.z - 0.5);
            List<VoxelShape> fitting = PortalCollision.withOtherSide(player, world, List.of(), player.getBoundingBox().stretch(0.0, 0.0, 0.5));
            context.assertTrue(fitting.isEmpty(), "Tete et epaules dans l'ouverture : rien n'arrete le joueur : " + fitting + ".");
            // Decale de trois dixiemes vers l'est : une epaule depasse, le bord est l'arrete (du plan a un demi-bloc derriere).
            player.setPosition(center.x + 0.3, center.y - 1.0, center.z - 0.5);
            List<VoxelShape> shoulder = PortalCollision.withOtherSide(player, world, List.of(), player.getBoundingBox().stretch(0.0, 0.0, 0.5));
            context.assertTrue(shoulder.size() == 1 && near(shoulder.get(0).getBoundingBox().getMinPos(), new Vec3d(center.x + 0.5, center.y - 1.5, center.z))
                    && near(shoulder.get(0).getBoundingBox().getMaxPos(), new Vec3d(center.x + 1.0, center.y + 1.5, center.z + 0.5)),
                    "Une epaule qui depasse bute sur le bord est : " + shoulder + ".");
            // Un demi-bloc plus bas (cadre sureleve) : les yeux encore dans l'ouverture, les pieds sous le bord bas, qui bloque.
            player.setPosition(center.x, center.y - 1.5, center.z - 0.5);
            List<VoxelShape> feet = PortalCollision.withOtherSide(player, world, List.of(), player.getBoundingBox().stretch(0.0, 0.0, 0.5));
            context.assertTrue(feet.size() == 1 && near(feet.get(0).getBoundingBox().getMinPos(), new Vec3d(center.x - 0.5, center.y - 1.5, center.z))
                    && near(feet.get(0).getBoundingBox().getMaxPos(), new Vec3d(center.x + 0.5, center.y - 1.0, center.z + 0.5)),
                    "Les pieds sous le bord bas butent dessus : " + feet + ".");
            // Devant le cadre trop bas, les yeux au-dessus de l'ouverture : pas engage, l'ouverture fermee l'arrete au front.
            player.setPosition(lowCenter.x, lowCenter.y - 0.5, lowCenter.z - 0.5);
            List<VoxelShape> front = PortalCollision.withOtherSide(player, world, List.of(), player.getBoundingBox().stretch(0.0, 0.0, 0.5));
            context.assertTrue(front.size() == 1 && near(front.get(0).getBoundingBox().getMinPos(), new Vec3d(lowCenter.x - 0.5, lowCenter.y - 0.5, lowCenter.z))
                    && near(front.get(0).getBoundingBox().getMaxPos(), new Vec3d(lowCenter.x + 0.5, lowCenter.y + 0.5, lowCenter.z + 0.5)),
                    "Les yeux au-dessus d'un cadre trop bas : l'ouverture fermee l'arrete : " + front + ".");
            // Le serveur juge de meme : un pas des pieds a travers le plan du cadre trop bas ne traverse pas pour un joueur, si pour un objet.
            context.assertTrue(!TeleportExecutor.crossedPortalPlane(low, real, lowCenter.x, lowCenter.y - 0.5, lowCenter.z - 0.2,
                    lowCenter.x, lowCenter.y - 0.5, lowCenter.z + 0.2), "Les yeux hors de l'ouverture : un joueur ne traverse pas un cadre trop bas.");
            context.assertTrue(TeleportExecutor.crossedPortalPlane(frame, real, center.x, center.y - 1.0, center.z - 0.2,
                    center.x, center.y - 1.0, center.z + 0.2), "Les yeux dans l'ouverture : il traverse le cadre a sa taille.");
            context.assertTrue(TeleportExecutor.crossedPortalPlane(low, item, lowCenter.x, lowCenter.y - 0.5, lowCenter.z - 0.2,
                    lowCenter.x, lowCenter.y - 0.5, lowCenter.z + 0.2), "Un objet, aux pieds, traverse le cadre trop bas.");
            // Au retour, face au cadre d'arrivee (centre 8,5 ; 3 ; 20,5, sortie au sud) : la meme regle, aux yeux et dans sa hauteur.
            Vec3d arrival = frame.destinationPos();
            context.assertTrue(frame.crossingPointFacesArrival(player, arrival.x, arrival.y - 1.0, arrival.z + 0.3),
                    "Les yeux dans l'ouverture d'arrivee : un joueur y est engage au retour.");
            context.assertTrue(!frame.crossingPointFacesArrival(player, arrival.x, arrival.y - 0.2, arrival.z + 0.3),
                    "Les yeux au-dessus de l'ouverture d'arrivee : un joueur n'y est pas engage.");
            context.assertTrue(frame.crossingPointFacesArrival(item, arrival.x, arrival.y - 0.2, arrival.z + 0.3),
                    "Un objet, aux pieds, l'est a cette hauteur.");
        } finally {
            GameTestPlayers.disconnect(real);
            item.discard();
            discard(frame, low);
        }
        context.complete();
    }

    private static void fill(TestContext context, BlockPos from, BlockPos to, Block block) {
        for (BlockPos pos : BlockPos.iterate(from, to)) {
            context.setBlockState(pos, block);
        }
    }

    /** Portail d'un bloc sur deux, pas encore dans le monde, d'une orientation a une autre, dans le meme monde. */
    private static BackroomsPortalEntity build(ServerWorld world, Vec3d center, PortalFacing source, PortalFacing exit, Vec3d arrival) {
        return BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(source)
                .facingDestination(exit)
                .destinationDim(World.OVERWORLD)
                .destinationPos(arrival)
                .size(1.0f, 2.0f)
                .build();
    }

    /** Comme {@link #build}, ajoute au monde. */
    private static BackroomsPortalEntity spawn(ServerWorld world, Vec3d center, PortalFacing source, PortalFacing exit, Vec3d arrival) {
        BackroomsPortalEntity portal = build(world, center, source, exit, arrival);
        world.spawnEntity(portal);
        return portal;
    }

    /** Porte-armure aux pieds donnes ; ajoute au monde, il tombe et traverse, sinon il ne sert qu'aux requetes du test. */
    private static ArmorStandEntity stand(ServerWorld world, Vec3d feet, boolean spawned) {
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        stand.refreshPositionAndAngles(feet.x, feet.y, feet.z, 0.0f, 0.0f);
        if (spawned) {
            world.spawnEntity(stand);
        }
        return stand;
    }

    /** Objet sans gravite qui file vers le sud. */
    private static ItemEntity item(ServerWorld world, Vec3d feet, Item item) {
        ItemEntity entity = new ItemEntity(world, feet.x, feet.y, feet.z, new ItemStack(item));
        entity.setNoGravity(true);
        entity.setPickupDelayInfinite();
        entity.setVelocity(0.0, 0.0, ITEM_SPEED);
        world.spawnEntity(entity);
        return entity;
    }

    /** Place un corps hors du monde, sans trajet : seules les requetes du test le voient. */
    private static void moveTo(ArmorStandEntity body, Vec3d feet) {
        body.setPosition(feet.x, feet.y, feet.z);
    }

    private static boolean near(@Nullable Vec3d point, Vec3d expected) {
        return point != null && point.squaredDistanceTo(expected) < EPSILON * EPSILON;
    }

    private static void discard(BackroomsPortalEntity... portals) {
        for (BackroomsPortalEntity portal : portals) {
            portal.discard();
        }
    }
}
