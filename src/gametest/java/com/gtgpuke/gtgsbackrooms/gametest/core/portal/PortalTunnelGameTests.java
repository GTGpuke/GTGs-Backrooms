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
import com.gtgpuke.gtgsbackrooms.core.portal.transition.ArrivalGuard;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu des blocs caches derriere le plan d'un portail : un cadre pose sur un sol plein s'ouvre comme une trappe sous
 * qui y tombe et lui garde son elan, un cadre pose contre un mur se traverse comme si le mur n'y etait pas, un corps
 * engage dans un cadre n'est pas dans le mur qu'il traverse et le reste quelques ticks une fois le plan passe, sans que la
 * face arriere du cadre lui cache le cote d'ou il vient. Le cadre d'arrivee au sol garde qui en sort, meme au-dessus du
 * vide, jusqu'a ce qu'il en descende, s'accroupisse ou change de monde ; la garde refuse aussi une traversee annoncee.
 * Sur le bord d'un cadre en l'air, qui ne peut pas le traverser se pose sur son plan.
 *
 * <p>Le sol de l'arene est sous elle : la pierre posee a la premiere couche (Y = 0) fait un sol plein a Y = 1.
 */
public final class PortalTunnelGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-3;
    /** Vitesse des objets qui filent vers un mur, en blocs par tick. */
    private static final double ITEM_SPEED = 0.3;
    /** Pas d'un joueur simule qui descend, par tick, comme le dirait son client. */
    private static final double FALL_STEP = 0.4;
    /**
     * Ticks pendant lesquels un corps garde doit tenir sur son cadre d'arrivee, compte de sa pose : plus que le cooldown de
     * portail d'une entity (dix ticks, compte de son arrivee), moins que le repit de la garde.
     */
    private static final int HOLD_TICKS = 12;

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aFloorFrameOnSolidGroundOpensUnderAFallingBodyAndKeepsItsSpeed(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 2), new BlockPos(7, 0, 5), Blocks.STONE);
        // Un cadre au sol a ras de la pierre, vers un cadre au plafond en l'air : on y tombe, on en ressort en tombant.
        Vec3d entry = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 6.0, 4.0));
        BackroomsPortalEntity floor = spawn(world, entry, PortalFacing.UP, PortalFacing.DOWN, arrival);
        // Des porte-armures : des corps qui tombent, sans IA qui les ferait deriver hors d'un cadre d'un bloc de large.
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
    public void anArrivalFloorFrameHoldsWhoComesOutOfItBrieflyUnlessTheyStepOffOrSneak(TestContext context) {
        ServerWorld world = context.getWorld();
        fill(context, new BlockPos(2, 0, 2), new BlockPos(8, 0, 5), Blocks.STONE);
        // Deux cadres au sol, l'un vers l'autre : A a ras de la pierre, B en l'air, trois blocs au-dessus du sol de l'arene.
        Vec3d a = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d b = context.getAbsolute(new Vec3d(14.5, 3.0, 4.0));
        BackroomsPortalEntity toB = spawn(world, a, PortalFacing.UP, PortalFacing.UP, b);
        BackroomsPortalEntity toA = spawn(world, b, PortalFacing.UP, PortalFacing.UP, a);
        ArmorStandEntity body = stand(world, a.add(0.0, 3.0, 0.0), true);
        int[] held = {0};
        long[] landed = {0L};
        long[] pushed = {0L};
        context.createTimedTaskRunner()
                // Tombe dans A, il ressort de B vers le haut et retombe dessus : garde, il s'y pose, au-dessus du vide.
                .createAndAdd(() -> {
                    context.assertTrue(near(ArrivalGuard.guardedCenter(body), b) && body.isOnGround(), "Pas encore pose sur B.");
                    landed[0] = world.getTime();
                })
                .createAndAdd(() -> context.assertTrue(++held[0] > HOLD_TICKS, "Il doit tenir sur B."))
                .createAndAddReported(() -> {
                    context.assertTrue(near(ArrivalGuard.guardedCenter(body), b) && Math.abs(body.getY() - b.y) < EPSILON
                            && Math.abs(body.getX() - b.x) < 0.5, "Garde, il tient sur B pendant son repit, au lieu de repartir : " + body.getPos() + ".");
                    // Pousse a travers le plan (une commande, un piston) : garde, il ne repart pas non plus.
                    body.setPosition(b.x, b.y - 2.5, b.z);
                    pushed[0] = world.getTime();
                })
                // Le serveur juge ce pas a la fin du tick : la suite attend qu'il l'ait juge.
                .createAndAdd(() -> context.assertTrue(world.getTime() > pushed[0] + 1, "Le pas pousse n'est pas encore juge."))
                .createAndAddReported(() -> {
                    context.assertTrue(Math.abs(body.getX() - b.x) < 0.5 && body.getY() < b.y && near(ArrivalGuard.guardedCenter(body), b),
                            "Passe sous B, il n'est pas reparti par lui : " + body.getPos() + ".");
                    body.setPosition(b.x, b.y, b.z);
                    // Accroupi, on reprend son arrivee : la garde tombe, avant la fin du repit.
                    body.setSneaking(true);
                })
                .createAndAdd(() -> context.assertTrue(ArrivalGuard.guardedCenter(body) == null, "Accroupi, il est encore garde."))
                .createAndAddReported(() -> {
                    context.assertTrue(world.getTime() - landed[0] < ArrivalGuard.GRACE_TICKS, "La garde est tombee a l'accroupissement, pas a la fin du repit.");
                    body.setSneaking(false);
                })
                // B s'ouvre sous lui : il ressort de A, garde sur A cette fois, pose sur la pierre.
                .createAndAdd(() -> context.assertTrue(near(ArrivalGuard.guardedCenter(body), a) && body.isOnGround(), "Pas encore pose sur A."))
                .createAndAddReported(() -> {
                    context.assertTrue(Math.abs(body.getX() - a.x) < 0.5 && Math.abs(body.getY() - a.y) < EPSILON,
                            "Reparti par B, il tient sur A : " + body.getPos() + ".");
                    // Il en descend : la garde tombe.
                    body.setPosition(a.x + 2.0, a.y, a.z);
                })
                .createAndAdd(() -> context.assertTrue(ArrivalGuard.guardedCenter(body) == null, "Descendu du cadre, il est encore garde."))
                .createAndAddReported(() -> body.setPosition(a.x, a.y, a.z))
                // Revenu sur A, il y tombe et ressort de B ; cette fois, il y reste.
                .createAndAdd(() -> {
                    context.assertTrue(near(ArrivalGuard.guardedCenter(body), b) && body.isOnGround(), "Revenu sur A, il n'est pas encore pose sur B.");
                    landed[0] = world.getTime();
                })
                // Le repit passe, B s'ouvre sous lui : il retombe et ressort de A, sans avoir bouge.
                .createAndAdd(() -> context.assertTrue(near(ArrivalGuard.guardedCenter(body), a), "Reste sur B, il n'y est pas encore retombe."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(world.getTime() - landed[0] >= ArrivalGuard.GRACE_TICKS,
                                "B s'est ouvert avant la fin du repit : " + (world.getTime() - landed[0]) + " ticks.");
                    } finally {
                        body.discard();
                        toA.discard();
                        toB.discard();
                        fill(context, new BlockPos(2, 0, 2), new BlockPos(8, 0, 5), Blocks.AIR);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 200)
    public void aPlayerSetDownOnAFloorFrameIsHeldUntilTheyLeaveTheWorld(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        Vec3d frame = context.getAbsolute(new Vec3d(4.5, 1.0, 4.0));
        Vec3d arrival = context.getAbsolute(new Vec3d(14.5, 6.0, 4.0));
        // Le cadre au sol sous le joueur, puis ceux que la recherche du cadre de pose passe : d'un autre monde, debout, au-dessus
        // des pieds, trop bas sous eux, a cote.
        BackroomsPortalEntity floor = spawn(world, frame, PortalFacing.UP, PortalFacing.DOWN, arrival);
        BackroomsPortalEntity elsewhere = build(nether, frame, PortalFacing.UP, PortalFacing.DOWN, arrival);
        List<BackroomsPortalEntity> passed = List.of(
                elsewhere,
                build(world, frame, PortalFacing.NORTH, PortalFacing.SOUTH, arrival),
                build(world, frame.add(0.0, 0.5, 0.0), PortalFacing.UP, PortalFacing.DOWN, arrival),
                build(world, frame.add(0.0, -0.5, 0.0), PortalFacing.UP, PortalFacing.DOWN, arrival),
                build(world, frame.add(3.0, 0.0, 0.0), PortalFacing.UP, PortalFacing.DOWN, arrival));
        Vec3d onFrame = frame.add(0.0, 0.05, 0.0);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, onFrame, 0.0f, 90.0f);
        ArmorStandEntity stray = stand(world, onFrame, false);
        Vec3d[] feet = {onFrame};
        try {
            ArrivalGuard.arriveOnFrame(stray, passed);
            context.assertTrue(ArrivalGuard.guardedCenter(stray) == null, "Aucun de ces cadres ne porte ses pieds : pas de garde.");
            ArrivalGuard.arriveOnFrame(player, List.of(elsewhere, floor));
            context.assertTrue(near(ArrivalGuard.guardedCenter(player), frame), "Pose sur le cadre au sol, le joueur en est garde.");
            context.assertTrue(ArrivalGuard.guards(player, floor) && !ArrivalGuard.guards(player, elsewhere),
                    "La garde tient pour ce cadre, pas pour un cadre au meme endroit d'un autre monde.");
            // Il annonce une traversee de son cadre d'arrivee : refusee.
            GameTestPlayers.receive(player, new PortalCrossPayload(floor.getUuid(), frame.add(0.0, -0.3 - player.getStandingEyeHeight(), 0.0),
                    new Vec3d(0.0, -1.0, 0.0), 0.0f, 90.0f));
            context.assertTrue(player.getServerWorld() == world && player.getPos().squaredDistanceTo(onFrame) < EPSILON,
                    "Garde, son annonce de traversee est refusee : " + player.getPos() + ".");
        } catch (RuntimeException failure) {
            GameTestPlayers.disconnect(player);
            floor.discard();
            throw failure;
        }
        // Son client ne dit rien et le fait descendre a travers le plan : le serveur ne le fait pas repartir non plus.
        for (int tick = 1; tick <= 8; tick++) {
            context.runAtTick(tick, () -> {
                feet[0] = feet[0].subtract(0.0, FALL_STEP, 0.0);
                player.refreshPositionAndAngles(feet[0].x, feet[0].y, feet[0].z, 0.0f, 90.0f);
            });
        }
        // Il quitte le monde avant la fin du repit de la garde : c'est le changement de monde qui la fait tomber.
        context.runAtTick(ArrivalGuard.GRACE_TICKS / 2, () -> {
            context.assertTrue(player.getServerWorld() == world && Math.abs(player.getX() - frame.x) < EPSILON && player.getY() < frame.y - 2.0,
                    "Garde, il descend sous son cadre d'arrivee sans repartir : " + player.getPos() + ".");
            // Il quitte le monde (portail vanilla, commande) : la garde tombe.
            player.teleport(nether, frame.x, frame.y, frame.z, 0.0f, 0.0f);
        });
        context.runAtTick(ArrivalGuard.GRACE_TICKS / 2 + 2, () -> {
            context.assertTrue(ArrivalGuard.guardedCenter(player) == null, "Parti dans un autre monde, il n'est plus garde.");
            player.teleport(world, onFrame.x, onFrame.y, onFrame.z, 0.0f, 90.0f);
        });
        context.runAtTick(ArrivalGuard.GRACE_TICKS / 2 + 4, () -> {
            try {
                // Il tombe dans le cadre : ses yeux ont passe le plan de trois dixiemes, ses pieds sont bien plus bas. Le serveur
                // juge l'annonce aux yeux, qui decident de la traversee d'un cadre couche.
                GameTestPlayers.receive(player, new PortalCrossPayload(floor.getUuid(), frame.add(0.0, -0.3 - player.getStandingEyeHeight(), 0.0),
                        new Vec3d(0.0, -1.0, 0.0), 0.0f, 90.0f));
                context.assertTrue(Math.abs(player.getX() - arrival.x) < 0.5 && player.getY() < arrival.y,
                        "Les yeux juste passes, l'annonce est acceptee : il ressort sous le cadre d'arrivee : " + player.getPos() + ".");
                context.assertTrue(ArrivalGuard.guardedCenter(player) == null, "Sorti par un plafond, il n'est garde par rien.");
            } finally {
                GameTestPlayers.disconnect(player);
                floor.discard();
            }
            context.complete();
        });
    }

    /** Remplit de ce bloc la boite entre deux positions relatives a l'arene. */
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
