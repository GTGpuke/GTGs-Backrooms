package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.propagation.PortalPursuer;
import com.gtgpuke.gtgsbackrooms.core.portal.propagation.PortalPursuit;
import com.gtgpuke.gtgsbackrooms.core.portal.propagation.PortalPursuitGoal;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteEntityPacketPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.core.registry.ModSounds;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu de ce qui traverse un portail sans etre une entity : les sons et les particules, le regard des mobs.
 * Le Nether tient lieu de dimension d'arrivee ; chaque test a son propre point d'arrivee, dans l'air au-dessus du
 * plafond de bedrock. Les tests de poursuite y posent une plateforme et forcent le chunk qui la porte le temps du test.
 */
public final class PortalPropagationGameTests implements FabricGameTest {

    private static final Vec3d RELAY_EXIT = new Vec3d(104.5, 200.0, 104.5);
    private static final Vec3d PURSUIT_EXIT = new Vec3d(72.5, 200.0, 72.5);
    private static final Vec3d REDIRECT_EXIT = new Vec3d(136.5, 200.0, 136.5);
    private static final Vec3d PERCEPTION_EXIT = new Vec3d(168.5, 200.0, 168.5);
    private static final Vec3d RULES_EXIT = new Vec3d(200.5, 200.0, 200.5);
    /** Portail flottant du test de relais, loin du sol de l'arene. */
    private static final BlockPos RELAY_PORTAL = new BlockPos(1, 2, 6);
    /** Portail pose au sol, que les mobs peuvent traverser a pied. */
    private static final Vec3d GROUND_PORTAL = new Vec3d(1.5, 1.0, 6.5);
    /** Trois blocs et demi au nord du portail : cote d'entree, de face. */
    private static final Vec3d VIEWER = new Vec3d(1.5, 1.0, 3.0);

    /** Portail regardant le nord vers une arrivee du Nether regardant le sud. */
    private static BackroomsPortalEntity netherPortal(ServerWorld world, Vec3d center, Vec3d destination) {
        return GameTestServerWorlds.spawnNorthPortal(world, center, World.NETHER, destination);
    }

    /**
     * Apres un lot de poursuite, reussi ou non : joueurs deconnectes, plateforme retiree, chunk d'arrivee libere. Un test
     * qui echoue en route ne passe pas par son propre nettoyage.
     */
    private static void release(ServerWorld world, Vec3d exit) {
        GameTestPlayers.disconnectAll();
        ServerWorld nether = world.getServer().getWorld(World.NETHER);
        platform(nether, exit, Blocks.AIR);
        GameTestServerWorlds.releaseForcedChunks(nether);
    }

    /** Plateforme d'obsidienne sous une arrivee (ou de l'air pour la retirer) : on y ressort debout. */
    private static void platform(ServerWorld nether, Vec3d arrival, Block block) {
        BlockPos floor = BlockPos.ofFloored(arrival).down(2);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 6; dz++) {
                nether.setBlockState(floor.add(dx, 0, dz), block.getDefaultState());
            }
        }
    }

    /** {@code true} si le mob a recu l'objectif de poursuite a travers les portails (champ protege, lu par reflexion). */
    private static boolean hasPursuitGoal(MobEntity mob) {
        try {
            Field field = MobEntity.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getGoals().stream().anyMatch(goal -> goal.getGoal() instanceof PortalPursuitGoal);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestException("Objectifs du mob inaccessibles : " + exception.getMessage() + ".");
        }
    }

    /** {@code true} si un payload relaye au joueur transporte un paquet de ce type. */
    private static boolean relayed(List<RemoteEntityPacketPayload> payloads, Class<?> packetType) {
        return payloads.stream().anyMatch(payload -> packetType.isInstance(payload.packet()));
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 3000, batchId = "portal_relay")
    public void soundsAndParticlesReachPortalViewers(TestContext context) {
        ServerWorld world = context.getWorld();
        // Le test compte les joueurs servis : aucun joueur d'un test precedent ne doit regarder l'arrivee.
        GameTestPlayers.disconnectAll();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.pregenerateArrival(nether, RELAY_EXIT);
        BackroomsPortalEntity portal = netherPortal(world, Vec3d.ofCenter(context.getAbsolutePos(RELAY_PORTAL)), RELAY_EXIT);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(VIEWER), 0.0f, 0.0f);
        PlaySoundS2CPacket bell = new PlaySoundS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS,
                RELAY_EXIT.x, RELAY_EXIT.y, RELAY_EXIT.z, 1.0f, 1.0f, 0L);
        // Le serveur de test tique sans attendre : on guette l'arrivee du chunk de l'arrivee dans la fenetre du joueur.
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(PortalViewTracker.relayToViewers(World.NETHER, RELAY_EXIT.x, RELAY_EXIT.z, bell) == 1,
                        "Un son de l'arrivee part au joueur qui la regarde par le portail."))
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(PortalViewTracker.relayToViewers(World.NETHER, RELAY_EXIT.x + 4096.0, RELAY_EXIT.z, bell) == 0,
                                "Hors de sa fenetre, rien.");
                        context.assertTrue(PortalViewTracker.relayToViewers(World.END, RELAY_EXIT.x, RELAY_EXIT.z, bell) == 0,
                                "Une dimension qu'il ne regarde pas, rien.");
                        context.assertTrue(PortalViewTracker.relayToViewers(World.NETHER, RELAY_EXIT.x, RELAY_EXIT.z,
                                new GameMessageS2CPacket(Text.literal("Bonjour."), false)) == 0, "Seuls sons, evenements de monde et particules passent.");
                        // Les chemins vanilla passent par les mixins : son, evenement de monde, particules diffusees. Les envois
                        // precedents (le son relaye directement ci-dessus) sont d'abord ecartes.
                        GameTestPlayers.sent(player, RemoteEntityPacketPayload.class);
                        nether.playSound(null, RELAY_EXIT.x, RELAY_EXIT.y, RELAY_EXIT.z, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 1.0f, 1.0f);
                        nether.syncWorldEvent(WorldEvents.BLOCK_BROKEN, BlockPos.ofFloored(RELAY_EXIT), Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
                        nether.spawnParticles(ParticleTypes.FLAME, RELAY_EXIT.x, RELAY_EXIT.y, RELAY_EXIT.z, 3, 0.0, 0.0, 0.0, 0.0);
                        List<RemoteEntityPacketPayload> received = GameTestPlayers.sent(player, RemoteEntityPacketPayload.class);
                        context.assertTrue(relayed(received, PlaySoundS2CPacket.class), "Le son du Nether arrive au joueur, enveloppe.");
                        context.assertTrue(relayed(received, WorldEventS2CPacket.class), "L'evenement de monde aussi.");
                        context.assertTrue(relayed(received, ParticleS2CPacket.class), "Et les particules.");
                        // Un son lie a l'image (le cri) passe par le meme relais : le client le reconnait a son nom et sa dimension,
                        // celle du son de la cloche juste avant (le Nether, sous l'index annonce au joueur).
                        int netherIndex = received.stream().filter(payload -> payload.packet() instanceof PlaySoundS2CPacket).findFirst()
                                .orElseThrow().dimension();
                        nether.playSound(null, RELAY_EXIT.x, RELAY_EXIT.y, RELAY_EXIT.z, ModSounds.HOWLER_SCREAM, SoundCategory.HOSTILE, 1.0f, 1.0f);
                        context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream().anyMatch(payload ->
                                        payload.packet() instanceof PlaySoundS2CPacket sound && sound.getSound().value() == ModSounds.HOWLER_SCREAM
                                                && payload.dimension() == netherIndex),
                                "Le cri lie du Nether arrive au joueur, enveloppe avec sa dimension.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 1200, batchId = "portal_pursuit")
    public void mobsChaseThroughOpenPortals(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.forceChunk(nether, PURSUIT_EXIT, true);
        platform(nether, PURSUIT_EXIT, Blocks.OBSIDIAN);
        BackroomsPortalEntity portal = netherPortal(world, context.getAbsolute(GROUND_PORTAL), PURSUIT_EXIT);
        // Une proie de l'autre cote, en face de l'arrivee ; un joueur en creatif plus pres, que les mobs ignorent.
        ServerPlayerEntity prey = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(20.5, 0.0, 20.5)), 0.0f, 0.0f);
        prey.teleport(nether, PURSUIT_EXIT.x, PURSUIT_EXIT.y - 1.0, PURSUIT_EXIT.z + 3.0, 180.0f, 0.0f);
        ServerPlayerEntity builder = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(22.5, 0.0, 20.5)), 0.0f, 0.0f);
        builder.teleport(nether, PURSUIT_EXIT.x, PURSUIT_EXIT.y - 1.0, PURSUIT_EXIT.z + 1.5, 180.0f, 0.0f);
        // Un husk ne brule pas au soleil : il a tout le temps de voir la proie par le cadre, de marcher et de traverser.
        HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 0.0, 2.5));
        husk.setPersistent();
        boolean[] threatening = {false};
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // En chemin, avant le cadre : posture d'attaque, sans cible de ce cote.
                    threatening[0] |= !husk.isRemoved() && husk.isAttacking() && husk.getTarget() == null;
                    // Vanilla garde l'identifiant de l'entity qui change de monde : c'est par lui qu'on la retrouve.
                    if (!(nether.getEntity(husk.getUuid()) instanceof HuskEntity)) {
                        throw new GameTestException("Le husk n'a pas encore traverse le portail.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        HuskEntity arrived = (HuskEntity) nether.getEntity(husk.getUuid());
                        context.assertTrue(threatening[0], "En marchant vers le portail, le husk avait deja la posture d'attaque.");
                        context.assertTrue(arrived.getTarget() == prey, "De l'autre cote, la proie qu'il a vue par le cadre est sa cible.");
                        context.assertTrue(arrived.isAttacking(), "Arrive, il garde la posture d'attaque sans attendre ses objectifs.");
                    } finally {
                        GameTestServerWorlds.discardEverywhere(nether, husk);
                        GameTestPlayers.disconnect(prey);
                        GameTestPlayers.disconnect(builder);
                        portal.discard();
                        platform(nether, PURSUIT_EXIT, Blocks.AIR);
                        GameTestServerWorlds.forceChunk(nether, PURSUIT_EXIT, false);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100, batchId = "portal_perception")
    public void mobsSeeOnlyThroughOpenUnobstructedFrames(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.forceChunk(nether, PERCEPTION_EXIT, true);
        platform(nether, PERCEPTION_EXIT, Blocks.OBSIDIAN);
        BackroomsPortalEntity portal = netherPortal(world, context.getAbsolute(GROUND_PORTAL), PERCEPTION_EXIT);
        // A cote : un portail ferme, et un portail ouvert vers une dimension absente. Aucun ne montre la proie.
        BackroomsPortalEntity closed = netherPortal(world, context.getAbsolute(GROUND_PORTAL.add(8.0, 0.0, 0.0)), PERCEPTION_EXIT);
        closed.setTeleportable(false);
        BackroomsPortalEntity lost = BackroomsPortalEntity.builder(world).position(context.getAbsolute(GROUND_PORTAL.add(4.0, 0.0, 0.0)))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(GameTestServerWorlds.nowhere())
                .destinationPos(PERCEPTION_EXIT).size(1.0f, 2.0f).build();
        world.spawnEntity(lost);
        ServerPlayerEntity prey = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(20.5, 0.0, 20.5)), 0.0f, 0.0f);
        prey.teleport(nether, PERCEPTION_EXIT.x, PERCEPTION_EXIT.y - 1.0, PERCEPTION_EXIT.z + 3.0, 180.0f, 0.0f);
        // Une seconde proie, hors de portee de poursuite : vue, mais trop loin.
        ServerPlayerEntity remote = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(22.5, 0.0, 20.5)), 0.0f, 0.0f);
        remote.teleport(nether, PERCEPTION_EXIT.x, PERCEPTION_EXIT.y - 1.0, PERCEPTION_EXIT.z + 60.0, 180.0f, 0.0f);
        HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 0.0, 2.5));
        husk.setAiDisabled(true);
        TargetPredicate rules = TargetPredicate.createAttackable().ignoreVisibility();
        BlockPos wall = context.getAbsolutePos(new BlockPos(1, 1, 4));
        BlockPos screen = BlockPos.ofFloored(PERCEPTION_EXIT).south(2);
        try {
            // Portail ferme : le cadre ne laisse rien voir.
            portal.setTeleportable(false);
            PortalPursuit.perceive(husk, rules);
            context.assertTrue(PortalPursuit.active(husk) == null && BackroomsPortalEntityRegistry.all().contains(closed),
                    "Un portail ferme cache la proie.");
            portal.setTeleportable(true);
            // Portee de poursuite reduite a deux blocs : le cadre, a quatre blocs, est trop loin pour regarder au travers.
            EntityAttributeInstance followRange = husk.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            double range = followRange.getBaseValue();
            followRange.setBaseValue(2.0);
            PortalPursuit.perceive(husk, rules);
            followRange.setBaseValue(range);
            context.assertTrue(PortalPursuit.active(husk) == null, "Un portail hors de portee de poursuite ne montre rien.");
            // Un mur entre le husk et le cadre : il ne voit rien.
            world.setBlockState(wall, Blocks.STONE.getDefaultState());
            PortalPursuit.perceive(husk, rules);
            context.assertTrue(PortalPursuit.active(husk) == null, "Un mur devant le cadre cache la proie.");
            world.setBlockState(wall, Blocks.AIR.getDefaultState());
            // Un ecran de l'autre cote, entre l'arrivee et la proie : toujours rien.
            nether.setBlockState(screen, Blocks.STONE.getDefaultState());
            PortalPursuit.perceive(husk, rules);
            context.assertTrue(PortalPursuit.active(husk) == null, "Un bloc de l'autre cote cache la proie.");
            nether.setBlockState(screen, Blocks.AIR.getDefaultState());
            // Vue degagee des deux cotes : le husk voit la proie par le seul portail ouvert qui y mene.
            PortalPursuit.perceive(husk, rules);
            PortalPursuit.Pursuit pursuit = PortalPursuit.active(husk);
            context.assertTrue(pursuit != null && pursuit.portal() == portal && pursuit.target() == prey,
                    "Vue degagee : le husk poursuit la proie par le portail ouvert.");
            context.assertTrue(PortalPursuit.targetAfterCrossing(husk, lost) == null, "Un autre portail ne mene pas a la proie.");
            PortalPursuit.end(husk);
            context.assertTrue(PortalPursuit.targetAfterCrossing(husk, portal) == null, "Sans poursuite, rien a reprendre de l'autre cote.");
        } finally {
            world.setBlockState(wall, Blocks.AIR.getDefaultState());
            nether.setBlockState(screen, Blocks.AIR.getDefaultState());
            husk.discard();
            GameTestPlayers.disconnect(prey);
            GameTestPlayers.disconnect(remote);
            portal.discard();
            closed.discard();
            lost.discard();
            platform(nether, PERCEPTION_EXIT, Blocks.AIR);
            GameTestServerWorlds.forceChunk(nether, PERCEPTION_EXIT, false);
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 1200, batchId = "portal_pursuit_redirect")
    public void aTargetInAnotherWorldBecomesAPursuit(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.forceChunk(nether, REDIRECT_EXIT, true);
        platform(nether, REDIRECT_EXIT, Blocks.OBSIDIAN);
        BackroomsPortalEntity portal = netherPortal(world, context.getAbsolute(GROUND_PORTAL), REDIRECT_EXIT);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(20.5, 0.0, 20.5)), 0.0f, 0.0f);
        player.teleport(nether, REDIRECT_EXIT.x, REDIRECT_EXIT.y - 1.0, REDIRECT_EXIT.z + 3.0, 180.0f, 0.0f);
        HuskEntity front = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 0.0, 2.5));
        HuskEntity behind = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 0.0, 10.5));
        HuskEntity walker = context.spawnEntity(EntityType.HUSK, new Vec3d(3.5, 0.0, 2.5));
        front.setAiDisabled(true);
        behind.setAiDisabled(true);
        walker.setAiDisabled(true);
        // Qui recoit l'objectif de poursuite : le slime cible les joueurs sans se venger, le golem de fer se venge apres un
        // premier objectif d'un autre genre, le chat ne cible aucun joueur.
        SlimeEntity slime = context.spawnEntity(EntityType.SLIME, new Vec3d(12.5, 0.0, 12.5));
        CatEntity cat = context.spawnEntity(EntityType.CAT, new Vec3d(14.5, 0.0, 12.5));
        IronGolemEntity golem = context.spawnEntity(EntityType.IRON_GOLEM, new Vec3d(16.5, 0.0, 12.5));
        // Le chunk force du Nether ne suit ses entities qu'une fois actif : le husk n'y arrive qu'ensuite.
        BlockPos arrival = BlockPos.ofFloored(REDIRECT_EXIT);
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (!nether.shouldTickEntity(arrival)) {
                        throw new GameTestException("Le chunk d'arrivee du Nether n'est pas encore actif.");
                    }
                })
                .createAndAddReported(() -> redirect(context, nether, portal, player, front, behind, walker, new MobEntity[] {slime, cat, golem}))
                .completeIfSuccessful();
    }

    private static void redirect(
            TestContext context,
            ServerWorld nether,
            BackroomsPortalEntity portal,
            ServerPlayerEntity player,
            HuskEntity front,
            HuskEntity behind,
            HuskEntity walker,
            MobEntity[] bystanders) {
        try {
            context.assertTrue(hasPursuitGoal(front) && hasPursuitGoal(bystanders[0]) && hasPursuitGoal(bystanders[2]),
                    "Le husk, le slime et le golem de fer ciblent des joueurs : ils peuvent les poursuivre par un portail.");
            context.assertTrue(!hasPursuitGoal(bystanders[1]), "Le chat ne cible aucun joueur : pas d'objectif de poursuite.");
            // Frappe a travers le portail : vanilla donnerait au husk une cible dans un autre monde.
            front.setTarget(player);
            context.assertTrue(front.getTarget() == null, "Une cible dans un autre monde n'est jamais gardee telle quelle.");
            PortalPursuit.Pursuit pursuit = PortalPursuit.active(front);
            context.assertTrue(pursuit != null && pursuit.portal() == portal && pursuit.target() == player,
                    "Elle devient une poursuite par le portail ouvert qui y mene.");
            behind.setTarget(player);
            context.assertTrue(behind.getTarget() == null && PortalPursuit.active(behind) == null,
                    "Derriere le portail, aucun chemin : ni cible ni poursuite.");
            context.assertTrue(PortalPursuit.targetAfterCrossing(player, portal) == null, "Un joueur ne poursuit personne.");

            // L'objectif de poursuite : actif tant que la poursuite tient, il pousse le mob vers le cadre, puis s'arrete.
            walker.setTarget(player);
            walker.setOnGround(true);
            PortalPursuitGoal goal = new PortalPursuitGoal(walker);
            context.assertTrue(goal.canStart() && goal.shouldContinue() && goal.shouldRunEveryTick(), "La poursuite active l'objectif.");
            goal.start();
            goal.tick();
            context.assertTrue(walker.getNavigation().isFollowingPath()
                    && walker.getNavigation().getTargetPos().equals(context.getAbsolutePos(new BlockPos(1, 0, 5))),
                    "De biais, le husk vient d'abord en face de l'ouverture.");
            Vec3d inFront = context.getAbsolute(new Vec3d(1.5, 0.0, 5.5));
            walker.refreshPositionAndAngles(inFront.x, inFront.y, inFront.z, 0.0f, 0.0f);
            goal.tick();
            Vec3d through = context.getAbsolute(new Vec3d(1.5, 0.0, 8.5));
            context.assertTrue(!walker.getNavigation().isFollowingPath() && walker.getMoveControl().isMoving()
                    && walker.getMoveControl().getTargetX() == through.x && walker.getMoveControl().getTargetZ() == through.z,
                    "Aligne, il marche aussitot droit a travers, vers le point derriere le cadre.");
            goal.stop();
            context.assertTrue(walker.getMoveControl().getSpeed() == 0.0, "Arrete, il ne marche plus droit vers le cadre.");
            context.assertTrue(!goal.canStart() && PortalPursuit.active(walker) == null, "Arrete, l'objectif oublie la poursuite.");
            goal.tick();
            context.assertTrue(!walker.getNavigation().isFollowingPath(), "Sans poursuite, le tick de l'objectif ne relance pas la marche.");

            // Le portail se ferme : la poursuite cesse.
            walker.setTarget(player);
            portal.setTeleportable(false);
            context.assertTrue(PortalPursuit.active(walker) == null, "Portail ferme, plus de poursuite.");
            portal.setTeleportable(true);

            // Le husk passe le portail : de l'autre cote, sa poursuite devient sa cible.
            context.assertTrue(PortalPursuit.targetAfterCrossing(front, portal) == player, "La cible suit le husk de l'autre cote.");
            context.assertTrue(TeleportExecutor.execute(front, portal), "Le husk traverse.");
            // Vanilla garde l'identifiant de l'entity qui change de monde : c'est par lui qu'on la retrouve.
            context.assertTrue(nether.getEntity(front.getUuid()) instanceof HuskEntity arrived && arrived.getTarget() == player,
                    "Arrive dans le Nether, le husk cible le joueur.");
        } finally {
            GameTestServerWorlds.discardEverywhere(nether, front);
            behind.discard();
            walker.discard();
            for (MobEntity bystander : bystanders) {
                bystander.discard();
            }
            GameTestPlayers.disconnect(player);
            portal.discard();
            platform(nether, REDIRECT_EXIT, Blocks.AIR);
            GameTestServerWorlds.forceChunk(nether, REDIRECT_EXIT, false);
        }
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 1200, batchId = "portal_pursuit_rules")
    public void aPursuitNeedsAnOpenVisiblePortalTowardsAnotherWorldAndALivingTarget(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.forceChunk(nether, RULES_EXIT, true);
        platform(nether, RULES_EXIT, Blocks.OBSIDIAN);
        BlockPos arrival = BlockPos.ofFloored(RULES_EXIT);
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (!nether.shouldTickEntity(arrival)) {
                        throw new GameTestException("Le chunk d'arrivee du Nether n'est pas encore actif.");
                    }
                })
                .createAndAddReported(() -> pursuitRules(context, world, nether))
                .completeIfSuccessful();
    }

    /**
     * Toutes les regles d'une poursuite, de cote : chaque portail ou cible ecarte l'est pour une seule raison. Le husk se
     * tient du cote d'entree des portails, au nord ; le seul portail valable est devant lui, les autres sont a l'est, de dos
     * ou de l'autre cote.
     */
    private static void pursuitRules(TestContext context, ServerWorld world, ServerWorld nether) {
        BackroomsPortalEntity portal = netherPortal(world, context.getAbsolute(GROUND_PORTAL), RULES_EXIT);
        BackroomsPortalEntity closed = netherPortal(world, context.getAbsolute(GROUND_PORTAL.add(4.0, 0.0, 0.0)), RULES_EXIT);
        closed.setTeleportable(false);
        BackroomsPortalEntity invisible = BackroomsPortalEntity.builder(world).position(context.getAbsolute(GROUND_PORTAL.add(8.0, 0.0, 0.0)))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(World.NETHER)
                .destinationPos(RULES_EXIT).size(1.0f, 2.0f).visible(false).build();
        world.spawnEntity(invisible);
        BackroomsPortalEntity local = BackroomsPortalEntity.builder(world).position(context.getAbsolute(GROUND_PORTAL.add(12.0, 0.0, 0.0)))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(World.OVERWORLD)
                .destinationPos(context.getAbsolute(new Vec3d(20.5, 1.0, 20.5))).size(1.0f, 2.0f).build();
        world.spawnEntity(local);
        BackroomsPortalEntity toTheEnd = BackroomsPortalEntity.builder(world).position(context.getAbsolute(GROUND_PORTAL.add(16.0, 0.0, 0.0)))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(World.END)
                .destinationPos(RULES_EXIT).size(1.0f, 2.0f).build();
        world.spawnEntity(toTheEnd);
        // Le husk se tient au sud de celui-ci, qu'on entre par le nord : il le voit de dos.
        BackroomsPortalEntity behind = netherPortal(world, context.getAbsolute(new Vec3d(5.5, 1.0, 0.5)), RULES_EXIT);
        // Un portail du Nether vers l'overworld : il n'est pas dans le monde du husk.
        BackroomsPortalEntity elsewhere = BackroomsPortalEntity.builder(nether).position(RULES_EXIT.add(0.0, 0.0, 6.0))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(World.OVERWORLD)
                .destinationPos(context.getAbsolute(GROUND_PORTAL)).size(1.0f, 2.0f).build();
        nether.spawnEntity(elsewhere);
        HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 0.0, 2.5));
        husk.setAiDisabled(true);
        HuskEntity neighbour = context.spawnEntity(EntityType.HUSK, new Vec3d(3.5, 0.0, 2.5));
        neighbour.setAiDisabled(true);
        // De l'autre cote : une proie de biais (son image tombe a cote du cadre) et un joueur en creatif, jamais cible.
        ServerPlayerEntity lateral = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(20.5, 0.0, 20.5)), 0.0f, 0.0f);
        lateral.teleport(nether, RULES_EXIT.x + 6.0, RULES_EXIT.y - 1.0, RULES_EXIT.z + 3.0, 180.0f, 0.0f);
        ServerPlayerEntity creative = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(22.5, 0.0, 20.5)), 0.0f, 0.0f);
        creative.teleport(nether, RULES_EXIT.x, RULES_EXIT.y - 1.0, RULES_EXIT.z + 2.0, 180.0f, 0.0f);
        ServerPlayerEntity prey = null;
        TargetPredicate rules = TargetPredicate.createAttackable().ignoreVisibility();
        try {
            PortalPursuit.perceive(husk, rules);
            context.assertTrue(PortalPursuit.active(husk) == null, "Une proie de biais, a cote du cadre, et un joueur en creatif : rien a poursuivre.");
            prey = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(18.5, 0.0, 20.5)), 0.0f, 0.0f);
            prey.teleport(nether, RULES_EXIT.x, RULES_EXIT.y - 1.0, RULES_EXIT.z + 3.0, 180.0f, 0.0f);
            PortalPursuit.perceive(husk, rules);
            PortalPursuit.Pursuit seen = PortalPursuit.active(husk);
            context.assertTrue(seen != null && seen.portal() == portal && seen.target() == prey,
                    "Seul le portail ouvert, visible, devant le husk et vers le Nether montre la proie.");

            // Une poursuite cesse des qu'une de ses conditions tombe.
            long later = world.getTime() + 100L;
            PortalPursuer pursuer = (PortalPursuer) husk;
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(portal, prey, world.getTime() - 1L));
            context.assertTrue(PortalPursuit.active(husk) == null, "Delai depasse : la poursuite cesse.");
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(closed, prey, later));
            context.assertTrue(PortalPursuit.active(husk) == null, "Portail ferme : la poursuite cesse.");
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(elsewhere, prey, later));
            context.assertTrue(PortalPursuit.active(husk) == null, "Portail d'un autre monde : la poursuite cesse.");
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(portal, neighbour, later));
            context.assertTrue(PortalPursuit.active(husk) == null, "Cible restee de ce cote : la poursuite cesse.");
            prey.setHealth(0.0f);
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(portal, prey, later));
            context.assertTrue(PortalPursuit.active(husk) == null, "Cible morte : la poursuite cesse.");
            prey.setHealth(prey.getMaxHealth());
            BackroomsPortalEntity gone = netherPortal(world, context.getAbsolute(GROUND_PORTAL.add(2.0, 0.0, 0.0)), RULES_EXIT);
            gone.discard();
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(gone, prey, later));
            context.assertTrue(PortalPursuit.active(husk) == null, "Portail disparu : la poursuite cesse.");

            // Frappe a travers le cadre : la cible devient une poursuite par le portail valable le plus proche.
            husk.setTarget(prey);
            PortalPursuit.Pursuit redirected = PortalPursuit.active(husk);
            context.assertTrue(redirected != null && redirected.portal() == portal, "La cible lointaine passe par le seul portail qui y mene.");
            PortalPursuit.end(husk);
            EntityAttributeInstance followRange = husk.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            double range = followRange.getBaseValue();
            followRange.setBaseValue(2.0);
            husk.setTarget(prey);
            followRange.setBaseValue(range);
            context.assertTrue(PortalPursuit.active(husk) == null, "Portee de poursuite trop courte : le portail est hors d'atteinte.");

            // Une cible de ce cote passe avant toute poursuite.
            PortalPursuitGoal goal = new PortalPursuitGoal(husk);
            pursuer.backrooms$setPursuit(new PortalPursuit.Pursuit(portal, prey, later));
            husk.setTarget(neighbour);
            context.assertTrue(!goal.canStart(), "Le husk a deja une cible dans son monde : l'objectif de poursuite attend.");

            // Posture d'attaque le temps de la poursuite, le regard sur la proie, la ou elle semble etre par le cadre.
            husk.setTarget(null);
            goal.start();
            goal.tick();
            context.assertTrue(husk.isAttacking(), "En poursuite, le husk prend la posture d'attaque.");
            goal.stop();
            context.assertTrue(!husk.isAttacking() && PortalPursuit.active(husk) == null, "La poursuite finie, il la quitte.");
            // Une poursuite qui finit alors qu'il a une cible de ce cote ne la lui retire pas.
            husk.setTarget(neighbour);
            goal.stop();
            context.assertTrue(husk.getTarget() == neighbour, "La poursuite finie, il garde sa cible de ce cote.");
            husk.setTarget(null);

            // Arrive de l'autre cote : il reprend sa proie aussitot, la posture seulement s'il a un chemin vers elle.
            husk.setOnGround(true);
            PortalPursuit.resumeAfterCrossing(husk, neighbour);
            context.assertTrue(husk.getTarget() == neighbour && husk.isAttacking(), "Un chemin vers sa proie : il la cible et garde la posture.");
            husk.setAttacking(false);
            husk.setOnGround(false);
            husk.getNavigation().stop();
            PortalPursuit.resumeAfterCrossing(husk, neighbour);
            context.assertTrue(husk.getTarget() == neighbour && !husk.isAttacking(),
                    "En l'air, sans chemin : il la cible, sans posture qu'aucune attaque ne rendrait.");
        } finally {
            husk.discard();
            neighbour.discard();
            GameTestPlayers.disconnect(lateral);
            GameTestPlayers.disconnect(creative);
            if (prey != null) {
                GameTestPlayers.disconnect(prey);
            }
            for (BackroomsPortalEntity each : new BackroomsPortalEntity[] {portal, closed, invisible, local, toTheEnd, behind, elsewhere}) {
                each.discard();
            }
            platform(nether, RULES_EXIT, Blocks.AIR);
            GameTestServerWorlds.forceChunk(nether, RULES_EXIT, false);
        }
    }

    /** Apres le lot de relais, reussi ou non : le joueur qui regardait l'arrivee est deconnecte. */
    @AfterBatch(batchId = "portal_relay")
    public void afterRelay(ServerWorld world) {
        GameTestPlayers.disconnectAll();
    }

    /** Apres le lot de poursuite, reussi ou non. */
    @AfterBatch(batchId = "portal_pursuit")
    public void afterPursuit(ServerWorld world) {
        release(world, PURSUIT_EXIT);
    }

    /** Apres le lot du regard des mobs, reussi ou non. */
    @AfterBatch(batchId = "portal_perception")
    public void afterPerception(ServerWorld world) {
        release(world, PERCEPTION_EXIT);
    }

    /** Apres le lot de la cible devenue poursuite, reussi ou non. */
    @AfterBatch(batchId = "portal_pursuit_redirect")
    public void afterRedirect(ServerWorld world) {
        release(world, REDIRECT_EXIT);
    }

    /** Apres le lot des regles de poursuite, reussi ou non. */
    @AfterBatch(batchId = "portal_pursuit_rules")
    public void afterPursuitRules(ServerWorld world) {
        release(world, RULES_EXIT);
    }
}
