package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.Nullable;

import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalCrossPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockUpdatePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkUnloadPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionReleasePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteEntityPacketPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.DestinationLoader;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;
import com.gtgpuke.gtgsbackrooms.mixin.WorldAccessor;

/**
 * Tests en jeu de la synchronisation des dimensions distantes et des traversees entre dimensions.
 *
 * <p>Le serveur de test possede l'overworld, le Nether et l'End, mais pas le Level 0 : le Nether tient lieu de
 * dimension d'arrivee. Son point d'arrivee est au-dessus du plafond de bedrock, dans l'air. Le monde est neuf a
 * chaque lancement : les tests qui attendent des chunks du Nether guettent leur arrivee au lieu de compter les ticks.
 */
public final class PortalSyncGameTests implements FabricGameTest {

    private static final Vec3d NETHER_EXIT = new Vec3d(8.5, 200.0, 8.5);
    private static final BlockPos PORTAL = new BlockPos(1, 2, 6);
    /** Trois blocs et demi au nord du portail, qui regarde le nord : cote d'entree, de face. */
    private static final Vec3d VIEWER = new Vec3d(1.5, 1.0, 3.0);
    private static final double EPSILON = 1.0e-3;
    /** Ce qu'un test laisse loin de l'arene, ou sans nettoyage si une attente expire : retire apres son lot, reussi ou non. */
    private static final List<Entity> LEFTOVERS = new ArrayList<>();
    /** Ticks laisses a la generation du Nether avant de declarer un test en echec (le serveur de test tique sans attendre). */
    private static final int SYNC_PATIENCE_TICKS = 2000;
    /** Ticks sans portail a portee avant que le serveur ne fasse liberer un monde distant, par les reglages par defaut. */
    private static final int RELEASE_DELAY_TICKS = ModConfig.PORTAL_REMOTE_WORLD_RELEASE_SECONDS * SharedConstants.TICKS_PER_SECOND;
    /** Plus que le delai de grace des tickets de fenetre (cent ticks) et que l'intervalle du chargeur qui les relache. */
    private static final int HELD_WINDOW_TICKS = 150;
    /** Arrivee du portail invisible du test de fenetre, a 480 blocs a l'ouest : ses chunks sont loin de tous les autres. */
    private static final Vec3d HIDDEN_EXIT = NETHER_EXIT.add(-480.0, 0.0, 0.0);
    /** Arrivee du second portail du test a deux dimensions, dans le vide de l'End : rien a y generer. */
    private static final Vec3d END_EXIT = new Vec3d(504.5, 100.0, 504.5);
    /** Second portail du test a deux dimensions, trois blocs a l'est du premier : devant le joueur lui aussi. */
    private static final BlockPos SECOND_PORTAL = new BlockPos(4, 2, 6);
    /** Depart du test du cote quitte, a 512 blocs au nord de l'arene : hors de ses chunks forces, seul le joueur le tient. */
    private static final double DEPARTURE_OFFSET = 512.0;
    /** Ticks laisses apres un depart : vanilla a retire depuis longtemps les tickets du joueur. */
    private static final int DEPARTURE_SETTLE_TICKS = 40;
    /** Pluie et orage du Nether au debut du test de meteo, remis apres son lot. */
    private static final float[] NETHER_WEATHER = new float[2];

    /**
     * Le Nether genere d'avance autour de l'arrivee, et aucun joueur d'un test precedent : les tests qui guettent les
     * chunks envoyes ({@link PortalViewTracker#watchedChunkCount()}, tous joueurs confondus) ne voient que les leurs.
     */
    private static ServerWorld prepareNether(TestContext context) {
        GameTestPlayers.disconnectAll();
        context.assertTrue(PortalViewTracker.watchedChunkCount() == 0, "Aucun joueur d'un autre test n'est encore suivi.");
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.pregenerateArrival(nether, NETHER_EXIT);
        return nether;
    }

    /** Portail vers le Nether, regardant le nord, au point donne du test. */
    private static BackroomsPortalEntity netherPortal(TestContext context, BlockPos relative, Vec3d destination) {
        return GameTestServerWorlds.spawnNorthPortal(context.getWorld(), Vec3d.ofCenter(context.getAbsolutePos(relative)), World.NETHER, destination);
    }

    /** Milieu du bloc central du chunk qui contient cette coordonnee. */
    private static double chunkMiddle(double coordinate) {
        return ChunkSectionPos.getOffsetPos(ChunkSectionPos.getSectionCoord(coordinate), 8) + 0.5;
    }

    /** {@code true} si ce payload fait apparaitre chez le joueur l'entity de cet identifiant durable. */
    private static boolean spawnOf(RemoteEntityPacketPayload payload, UUID uuid) {
        return payload.packet() instanceof EntitySpawnS2CPacket spawn && spawn.getUuid().equals(uuid);
    }

    private static BackroomsPortalEntity netherPortal(TestContext context) {
        return netherPortal(context, PORTAL, NETHER_EXIT);
    }

    private static ServerPlayerEntity viewer(TestContext context, GameMode mode) {
        return GameTestPlayers.connect(context, mode, context.getAbsolute(VIEWER), 0.0f, 0.0f);
    }

    private static void moveTo(ServerPlayerEntity player, Vec3d position, float yaw) {
        player.refreshPositionAndAngles(position.x, position.y, position.z, yaw, 0.0f);
        player.networkHandler.syncWithPlayerPosition();
    }

    /** Portail du meme monde, du nord vers l'est, entre deux points du test. */
    private static BackroomsPortalEntity localPortal(TestContext context, Vec3d source, Vec3d destination, boolean teleportable) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(source).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.EAST)
                .destinationDim(World.OVERWORLD).destinationPos(destination).size(1.0f, 2.0f).teleportable(teleportable).build();
        world.spawnEntity(portal);
        return portal;
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 3000, batchId = "portal_sync_window")
    public void aPlayerLookingAtDimensionPortalsReceivesTheRemoteWindow(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = prepareNether(context);
        // Moniteur actif : chaque payload envoye est mesure, et le resume reseau les compte par type.
        ModPerformanceMonitor.setEnabled(true);
        // Le portail lointain est inscrit en premier : le portail proche, examine ensuite, le remplace comme foyer.
        BackroomsPortalEntity farPortal = netherPortal(context, new BlockPos(13, 2, 16), NETHER_EXIT.add(480.0, 0.0, 0.0));
        BackroomsPortalEntity portal = netherPortal(context);
        // Un troisieme portail dont l'arrivee tient dans la fenetre du premier : leurs chunks voulus se cumulent. Sa fenetre
        // tient aussi autour de l'arrivee du portail lointain.
        BackroomsPortalEntity sibling = netherPortal(context, PORTAL.east(2), NETHER_EXIT.add(16.0, 0.0, 0.0));
        // Un quatrieme portail vers la meme arrivee que le premier : ils partagent la fenetre chargee et son ticket.
        BackroomsPortalEntity twin = netherPortal(context, PORTAL.west(), NETHER_EXIT);
        // Un portail du meme monde, ferme, a cote : il ne mene a aucune dimension distante et n'entre pas dans la fenetre.
        BackroomsPortalEntity local = localPortal(context, Vec3d.ofCenter(context.getAbsolutePos(PORTAL.east(4))),
                context.getAbsolute(new Vec3d(20.5, 2.0, 20.5)), false);
        // Un portail invisible (et ferme) vers le Nether, a cote aussi : invisible, sa fenetre n'est jamais envoyee.
        BackroomsPortalEntity hidden = BackroomsPortalEntity.builder(world).position(Vec3d.ofCenter(context.getAbsolutePos(PORTAL.east(6))))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH).destinationDim(World.NETHER)
                .destinationPos(HIDDEN_EXIT).size(1.0f, 2.0f).teleportable(false).visible(false).build();
        world.spawnEntity(hidden);
        LEFTOVERS.addAll(List.of(farPortal, portal, sibling, twin, local, hidden));
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        BlockPos remoteBlock = BlockPos.ofFloored(NETHER_EXIT).down(3);
        BlockPos farRemoteBlock = BlockPos.ofFloored(NETHER_EXIT).add(600, 0, 0);
        Vec3d plane = portal.getPos();
        int[] phase = {0};
        int[] phaseStart = {0};
        ItemEntity[] remoteItem = new ItemEntity[1];
        MarkerEntity[] marker = new MarkerEntity[1];
        ItemEntity[] thrown = new ItemEntity[1];
        int[] destroyedId = {-1};
        BlockPos localBlock = context.getAbsolutePos(new BlockPos(20, 0, 20));
        int hiddenChunkX = ChunkSectionPos.getSectionCoord(HIDDEN_EXIT.x);

        for (int tick = 1; tick < 2990; tick++) {
            int now = tick;
            context.runAtTick(tick, () -> {
                int elapsed = now - phaseStart[0];
                switch (phase[0]) {
                    case 0 -> {
                        // La fenetre du premier portail arrive, chunk par chunk, des que le Nether est genere.
                        if (PortalViewTracker.watchedChunkCount() > 0) {
                            phase[0] = 1;
                            phaseStart[0] = now;
                        } else if (now > SYNC_PATIENCE_TICKS) {
                            throw new GameTestException("Aucun chunk du Nether envoye au joueur en " + now + " ticks.");
                        }
                    }
                    case 1 -> {
                        if (elapsed == 20) {
                            context.assertTrue(ModPerformanceMonitor.networkSummary().contains("portal_remote_chunk:"),
                                    "Moniteur actif, les chunks envoyes sont comptes : " + ModPerformanceMonitor.networkSummary() + ".");
                            for (RemoteChunkPayload chunk : GameTestPlayers.sent(player, RemoteChunkPayload.class)) {
                                context.assertTrue(chunk.dimension().equals(World.NETHER.getValue()) && Math.abs(chunk.chunkX() - hiddenChunkX) > 16,
                                        "Seules les fenetres des portails visibles vers le Nether sont envoyees : ni celle du portail du "
                                                + "meme monde, ni celle du portail invisible.");
                            }
                            // Le ticket du chargeur d'arrivee tient maintenant l'arrivee : un objet qui y apparait y reste.
                            remoteItem[0] = new ItemEntity(nether, NETHER_EXIT.x, NETHER_EXIT.y, NETHER_EXIT.z + 1.0, new ItemStack(Items.NETHER_STAR));
                            remoteItem[0].setNoGravity(true);
                            nether.spawnEntity(remoteItem[0]);
                            LEFTOVERS.add(remoteItem[0]);
                            // Un marqueur n'a pas de suivi vanilla : la vue distante le voit et passe son chemin.
                            marker[0] = EntityType.MARKER.create(nether);
                            marker[0].refreshPositionAndAngles(NETHER_EXIT.x + 1.0, NETHER_EXIT.y, NETHER_EXIT.z, 0.0f, 0.0f);
                            nether.spawnEntity(marker[0]);
                            LEFTOVERS.add(marker[0]);
                            nether.setBlockState(remoteBlock, Blocks.GLOWSTONE.getDefaultState());
                            // Un bloc change hors de la fenetre : relaye a personne.
                            nether.setBlockState(farRemoteBlock, Blocks.GLOWSTONE.getDefaultState());
                        } else if (elapsed == 50) {
                            // L'objet distant reste : son suivi sera retire avec la fenetre, puis avec la vue.
                            context.assertTrue(nether.getEntity(remoteItem[0].getUuid()) != null, "L'objet distant vit dans le Nether.");
                            List<RemoteEntityPacketPayload> entities = GameTestPlayers.sent(player, RemoteEntityPacketPayload.class);
                            context.assertTrue(entities.stream().anyMatch(payload -> spawnOf(payload, remoteItem[0].getUuid())),
                                    "L'objet de l'arrivee apparait chez le joueur, par le portail.");
                            context.assertTrue(entities.stream().noneMatch(payload -> spawnOf(payload, marker[0].getUuid())),
                                    "Un marqueur, sans suivi vanilla, n'est envoye a personne.");
                            List<RemoteBlockUpdatePayload> blocks = GameTestPlayers.sent(player, RemoteBlockUpdatePayload.class);
                            context.assertTrue(blocks.stream().anyMatch(update -> update.pos().equals(remoteBlock)),
                                    "Un bloc change dans la fenetre est relaye au joueur.");
                            context.assertTrue(blocks.stream().noneMatch(update -> update.pos().equals(farRemoteBlock)),
                                    "Un bloc change hors de la fenetre ne l'est pas.");
                            nether.setBlockState(remoteBlock, Blocks.AIR.getDefaultState());
                            nether.setBlockState(farRemoteBlock, Blocks.AIR.getDefaultState());
                            // Un bloc change dans la dimension du joueur, qu'aucune vue distante ne tient : relaye a personne.
                            world.setBlockState(localBlock, Blocks.GLOWSTONE.getDefaultState());
                            // Un objet lance a travers le portail change de dimension.
                            thrown[0] = new ItemEntity(world, plane.x, plane.y, plane.z - 0.3, new ItemStack(Items.GOLD_NUGGET));
                            thrown[0].setNoGravity(true);
                            thrown[0].setVelocity(0.0, 0.0, 0.5);
                            world.spawnEntity(thrown[0]);
                            LEFTOVERS.add(thrown[0]);
                            // Le jumeau disparait : la fenetre reste chargee pour le premier portail.
                            twin.discard();
                            // Le joueur se rapproche du portail lointain, sans etre a portee imminente : le foyer change. La
                            // fenetre du premier portail, qui ne tient pas autour du nouveau foyer, est dechargee ; celle du
                            // troisieme y tient, et garde l'objet distant. Le joueur reste devant le troisieme portail : vue
                            // de dos, sa vue ne se verrait pas, et il n'aurait aucune fenetre.
                            moveTo(player, context.getAbsolute(new Vec3d(20.5, 1.0, 5.0)), 0.0f);
                        } else if (elapsed == 70) {
                            context.assertTrue(nether.getEntity(thrown[0].getUuid()) != null, "La pepite est passee dans le Nether par le portail.");
                            context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                                    .noneMatch(payload -> payload.packet() instanceof EntitiesDestroyS2CPacket destroy
                                            && destroy.getEntityIds().contains(remoteItem[0].getId())),
                                    "L'objet distant, vu par le troisieme portail, reste suivi quand le foyer change.");
                            context.assertTrue(GameTestPlayers.sent(player, RemoteBlockUpdatePayload.class).stream()
                                    .noneMatch(update -> update.pos().equals(localBlock)),
                                    "Un bloc change dans la dimension du joueur, qu'aucune vue distante ne tient, n'est relaye a personne.");
                            world.setBlockState(localBlock, Blocks.AIR.getDefaultState());
                        } else if (elapsed == 130) {
                            // Retour devant le premier portail : sa fenetre, toujours chargee, est renvoyee.
                            moveTo(player, context.getAbsolute(VIEWER), 0.0f);
                            phase[0] = 2;
                            phaseStart[0] = now;
                        }
                    }
                    case 2 -> {
                        if (PortalViewTracker.watchedChunkCount() > 0) {
                            phase[0] = 3;
                            phaseStart[0] = now;
                        } else if (elapsed > SYNC_PATIENCE_TICKS) {
                            throw new GameTestException("La fenetre du premier portail n'est pas renvoyee.");
                        }
                    }
                    case 3 -> {
                        if (elapsed == 10) {
                            // L'objet distant, toujours suivi, disparait : la vue retire son suivi au passage suivant.
                            Entity live = nether.getEntity(remoteItem[0].getUuid());
                            destroyedId[0] = live.getId();
                            live.discard();
                        } else if (elapsed == 20) {
                            context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                                    .anyMatch(payload -> payload.packet() instanceof EntitiesDestroyS2CPacket destroy
                                            && destroy.getEntityIds().contains(destroyedId[0])),
                                    "L'objet disparu est retire chez le joueur.");
                            // Puis le joueur tourne le dos aux portails, a trente blocs : a portee de rendu, leurs fenetres restent.
                            moveTo(player, context.getAbsolute(VIEWER).add(0.0, 0.0, -30.0), 180.0f);
                            phase[0] = 5;
                            phaseStart[0] = now;
                        }
                    }
                    case 5 -> {
                        if (elapsed == 120) {
                            context.assertTrue(PortalViewTracker.watchedChunkCount() > 0,
                                    "Dos tourne mais a portee de rendu, la fenetre reste chez le joueur, meme passe le delai de grace.");
                            // Puis le joueur part a cent blocs : plus aucun portail a portee, tout est decharge apres le delai de grace.
                            moveTo(player, context.getAbsolute(VIEWER).add(100.0, 0.0, 0.0), 0.0f);
                            phase[0] = 4;
                            phaseStart[0] = now;
                        }
                    }
                    case 4 -> {
                        if (elapsed == 120) {
                            context.assertTrue(PortalViewTracker.watchedChunkCount() == 0,
                                    "Les chunks du Nether sont decharges chez un joueur qui ne regarde plus aucun portail.");
                        } else if (elapsed == 230) {
                            // Le chargeur a relache l'arrivee a son tour (dix secondes). Un joueur meurt juste devant le plan.
                            moveTo(player, new Vec3d(plane.x, plane.y - 1.0, plane.z - 0.3), 0.0f);
                            player.kill();
                        } else if (elapsed == 232) {
                            // Son corps franchit ensuite le plan et continue d'avancer sans etre teleporte : la detection ne suit
                            // que les vivants, et un franchissement suspendu serait execute apres le delai laisse au client.
                            moveTo(player, new Vec3d(plane.x, plane.y - 1.0, plane.z + 0.05), 0.0f);
                        } else if (elapsed > 232 && elapsed < 245) {
                            moveTo(player, player.getPos().add(0.0, 0.0, 0.01), 0.0f);
                        } else if (elapsed == 250) {
                            try {
                                context.assertTrue(!player.isAlive() && player.getServerWorld() == world,
                                        "Un joueur mort qui passe le plan reste dans l'overworld.");
                            } finally {
                                ModPerformanceMonitor.setEnabled(false);
                                GameTestPlayers.disconnect(player);
                                GameTestServerWorlds.discardEverywhere(nether, remoteItem[0]);
                                GameTestServerWorlds.discardEverywhere(nether, marker[0]);
                                GameTestServerWorlds.discardEverywhere(nether, thrown[0]);
                                portal.discard();
                                farPortal.discard();
                                sibling.discard();
                                twin.discard();
                                local.discard();
                                hidden.discard();
                            }
                            context.complete();
                        }
                    }
                    default -> {
                        // Test termine.
                    }
                }
            });
        }
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 2200, batchId = "portal_sync_crossing")
    public void crossingADimensionPortalMovesThePlayerToTheNetherWithoutResendingItsChunks(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        // Un temoin dans le Nether, a l'arrivee : vanilla lui envoie chaque bloc signale de ses chunks, meme revenu a
        // l'identique dans le meme tick (une case de lave qui se vide et se remplit). Le serveur renvoie a bon droit un chunk
        // tenu ainsi signale, et aucune empreinte de ses blocs, relevee entre deux ticks, ne le verrait.
        ServerPlayerEntity witness = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(VIEWER), 0.0f, 0.0f);
        seesWholeWindow(witness);
        witness.teleportTo(new TeleportTarget(nether, NETHER_EXIT, Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
        Vec3d plane = portal.getPos();
        int[] crossedAt = {-1};
        LongSet held = new LongOpenHashSet();
        LongSet witnessed = new LongOpenHashSet();
        LongSet notified = new LongOpenHashSet();
        LongSet resent = new LongOpenHashSet();

        // Le joueur regarde le portail et traverse des que le temoin a recu tous les chunks du Nether que le portail lui a
        // envoyes : vanilla lui reproposera ensuite ces chunks-la, tous proches de l'arrivee, et ne renverra que ceux dont un
        // bloc a ete signale depuis le passage (un changement encore en attente au moment du passage compris).
        for (int tick = 1; tick < 2190; tick++) {
            int now = tick;
            context.runAtTick(tick, () -> {
                GameTestPlayers.tick(witness);
                for (ChunkDataS2CPacket chunk : GameTestPlayers.sent(witness, ChunkDataS2CPacket.class)) {
                    witnessed.add(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
                }
                if (crossedAt[0] < 0) {
                    for (RemoteChunkPayload chunk : GameTestPlayers.sent(player, RemoteChunkPayload.class)) {
                        held.add(ChunkPos.toLong(chunk.chunkX(), chunk.chunkZ()));
                    }
                    if (held.isEmpty() || !witnessed.containsAll(held)) {
                        noteNotified(witness, null);
                        if (now > SYNC_PATIENCE_TICKS) {
                            throw new GameTestException("Le temoin n'a pas recu tous les chunks du Nether envoyes au joueur avant la traversee.");
                        }
                        return;
                    }
                    crossedAt[0] = now;
                    noteNotified(witness, notified);
                    player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                    context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                    context.assertTrue(player.getServerWorld() == nether, "Le joueur est dans le Nether.");
                    Vec3d expected = NETHER_EXIT.add(0.0, -1.0, 0.05 + TeleportExecutor.EXIT_OFFSET);
                    context.assertTrue(player.getPos().isInRange(expected, EPSILON), "Le joueur ressort devant le portail d'arrivee : " + player.getPos() + ".");
                    // Un chunk que le joueur n'a jamais recu par le portail n'est pas tenu.
                    context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, new ChunkPos(10_000, 10_000)), "Un chunk lointain n'est pas tenu.");
                } else if (now - crossedAt[0] < 60) {
                    // Le gestionnaire reseau du joueur tique : vanilla lui propose les chunks du Nether, sauf ceux qu'il tient deja.
                    noteNotified(witness, notified);
                    GameTestPlayers.tick(player);
                    for (ChunkDataS2CPacket chunk : GameTestPlayers.sent(player, ChunkDataS2CPacket.class)) {
                        resent.add(ChunkPos.toLong(chunk.getChunkX(), chunk.getChunkZ()));
                    }
                } else if (now - crossedAt[0] == 60) {
                    try {
                        noteNotified(witness, notified);
                        context.assertTrue(player.getServerWorld() == nether && player.isAlive(), "Le joueur reste dans le Nether.");
                        context.assertTrue(!resent.isEmpty(), "Vanilla envoie au joueur les autres chunks du Nether.");
                        LongIterator again = resent.iterator();
                        while (again.hasNext()) {
                            long key = again.nextLong();
                            context.assertTrue(!held.contains(key) || notified.contains(key), "Vanilla ne renvoie aucun des " + held.size()
                                    + " chunks deja recus par le portail, sauf ceux dont un bloc a ete signale depuis : " + new ChunkPos(key) + ".");
                        }
                        // Ceux que vanilla n'a pas encore reproposes se consomment jusqu'au dernier ; ceux qu'il a reproposes sans
                        // les renvoyer, ni renvoyes ni signales, ne sont deja plus tenus. Ensuite, plus rien n'est tenu.
                        int skipped = 0;
                        LongIterator remaining = held.iterator();
                        while (remaining.hasNext()) {
                            long key = remaining.nextLong();
                            if (!PortalViewTracker.consumeHeldChunk(player, new ChunkPos(key)) && !resent.contains(key) && !notified.contains(key)) {
                                skipped++;
                            }
                        }
                        context.assertTrue(skipped > 0, "Vanilla repropose des chunks tenus sans les renvoyer.");
                        context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, new ChunkPos(held.iterator().nextLong())),
                                "Le dernier chunk tenu consomme, le joueur n'en tient plus aucun.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        GameTestPlayers.disconnect(witness);
                        portal.discard();
                    }
                    context.complete();
                }
            });
        }
    }

    /**
     * Donne au temoin la portee qui couvre toute la fenetre que le portail synchronise, un carre de
     * {@code DestinationLoader.syncRadius} chunks autour de l'arrivee. A la portee par defaut d'un client (deux chunks), il
     * ne recevait jamais les chunks plus lointains que le portail avait deja envoyes au joueur, et l'attente de la
     * traversee expirait selon la course entre les deux envois (vu sur la CI, monde deja joue). Vanilla voit un chunk
     * si, un chunk retranche de l'ecart sur chaque axe, sa distance est sous la portee : le coin du carre est a
     * {@code (rayon - 1) * sqrt(2)}.
     */
    private static void seesWholeWindow(ServerPlayerEntity witness) {
        withViewDistance(witness, (int) Math.floor((DestinationLoader.syncRadius(true) - 1) * Math.sqrt(2.0)) + 1);
    }

    /**
     * Chunks dont un bloc a ete signale au temoin depuis la derniere lecture : vanilla envoie a ceux qui suivent un chunk
     * chaque bloc change pendant le tick, avec son etat de fin de tick. Sans {@code into}, les signalements sont oublies.
     */
    private static void noteNotified(ServerPlayerEntity witness, @Nullable LongSet into) {
        for (BlockUpdateS2CPacket update : GameTestPlayers.sent(witness, BlockUpdateS2CPacket.class)) {
            if (into != null) {
                into.add(ChunkPos.toLong(update.getPos()));
            }
        }
        for (ChunkDeltaUpdateS2CPacket delta : GameTestPlayers.sent(witness, ChunkDeltaUpdateS2CPacket.class)) {
            if (into != null) {
                delta.visitUpdates((pos, state) -> into.add(ChunkPos.toLong(pos)));
            }
        }
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void theClientAnnouncesItsCrossingAndTheServerChecksIt(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = GameTestServerWorlds.nether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        BackroomsPortalEntity closed = netherPortal(context, PORTAL.east(2), NETHER_EXIT);
        closed.setTeleportable(false);
        BackroomsPortalEntity broken = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(PORTAL.east(4)))).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(GameTestServerWorlds.nowhere()).destinationPos(NETHER_EXIT).size(1.0f, 2.0f).build();
        world.spawnEntity(broken);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        Vec3d plane = portal.getPos();
        Vec3d feet = new Vec3d(plane.x, plane.y - 1.0, plane.z + 0.05);
        BoatEntity boat = EntityType.BOAT.create(world);
        Vec3d boatPos = context.getAbsolute(new Vec3d(8.5, 1.0, 3.5));
        boat.refreshPositionAndAngles(boatPos.x, boatPos.y, boatPos.z, 0.0f, 0.0f);
        world.spawnEntity(boat);
        // Portail du meme monde : une annonce acceptee, puis une seconde aussitot, refusee par le cooldown du serveur de test.
        Vec3d localSource = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(12, 2, 1)));
        BackroomsPortalEntity local = localPortal(context, localSource, Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(16, 2, 6))), true);
        Vec3d localFeet = localSource.add(0.0, -1.0, 0.05);

        context.runAtTick(5, () -> {
            player.refreshPositionAndAngles(localFeet.x, localFeet.y, localFeet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(local.getUuid(), localFeet, Vec3d.ZERO, 0.0f, 0.0f));
            context.assertTrue(player.getX() > localSource.x + 2.0, "L'annonce locale plausible est appliquee : " + player.getPos() + ".");
            // De retour devant le cadre, la meme annonce aussitot : seul le cooldown la refuse.
            player.refreshPositionAndAngles(localFeet.x, localFeet.y, localFeet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(local.getUuid(), localFeet, Vec3d.ZERO, 0.0f, 0.0f));
            context.assertTrue(player.getX() < localSource.x + 1.0, "Sous cooldown, la seconde annonce est refusee sans teleporter : " + player.getPos() + ".");
        });
        context.runAtTick(10, () -> {
            // Cooldown ecoule. Annonces refusees : position invraisemblable, portail inconnu, portail ferme, dimension
            // d'arrivee absente, position hors du cadre, joueur en bateau.
            player.refreshPositionAndAngles(feet.x, feet.y, feet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet.add(20.0, 0.0, 0.0), Vec3d.ZERO, 0.0f, 0.0f));
            GameTestPlayers.receive(player, new PortalCrossPayload(UUID.randomUUID(), feet, Vec3d.ZERO, 0.0f, 0.0f));
            Vec3d closedFeet = closed.getPos().add(0.0, -1.0, 0.05);
            player.refreshPositionAndAngles(closedFeet.x, closedFeet.y, closedFeet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(closed.getUuid(), closedFeet, Vec3d.ZERO, 0.0f, 0.0f));
            Vec3d brokenFeet = broken.getPos().add(0.0, -1.0, 0.05);
            player.refreshPositionAndAngles(brokenFeet.x, brokenFeet.y, brokenFeet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(broken.getUuid(), brokenFeet, Vec3d.ZERO, 0.0f, 0.0f));
            player.refreshPositionAndAngles(feet.x, feet.y, feet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet.add(2.5, 0.0, 0.0), Vec3d.ZERO, 0.0f, 0.0f));
            // Vitesse impossible (elle couperait le controle vanilla des deplacements trop rapides) : refusee.
            GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet, new Vec3d(0.0, 0.0, Double.NaN), 0.0f, 0.0f));
            context.assertTrue(player.startRiding(boat, true), "Le joueur monte dans le bateau.");
            GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet, Vec3d.ZERO, 0.0f, 0.0f));
            player.stopRiding();
            context.assertTrue(player.getServerWorld() == world, "Aucune annonce refusee ne teleporte.");
            // Annonce plausible : le joueur a franchi le plan de 5 cm. Le serveur s'aligne sur l'etat du client.
            player.refreshPositionAndAngles(feet.x, feet.y, feet.z - 0.3, 0.0f, 0.0f);
            GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), feet, new Vec3d(0.0, 0.0, 0.2), 0.0f, 0.0f));
            context.assertTrue(player.getServerWorld() == nether, "L'annonce plausible est appliquee : le joueur est dans le Nether.");
        });
        context.runAtTick(11, () -> {
            try {
                // Depuis le Nether, le portail de l'overworld n'existe pas : l'annonce est refusee sans teleporter.
                GameTestPlayers.receive(player, new PortalCrossPayload(portal.getUuid(), player.getPos(), Vec3d.ZERO, 0.0f, 0.0f));
                context.assertTrue(player.getServerWorld() == nether, "Un portail d'un autre monde ne teleporte pas.");
            } finally {
                GameTestPlayers.disconnect(player);
                boat.discard();
                local.discard();
                closed.discard();
                broken.discard();
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void theServerCatchesAWalkingPlayerWhenTheClientStaysSilent(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        BackroomsPortalEntity portal = localPortal(context, source, destination, true);
        // Six couches de neige a l'arrivee (collision a cinq huitiemes) : les pieds du joueur y chevaucheraient le sol, il est remonte.
        BlockPos arrivalFloor = BlockPos.ofFloored(destination.x, destination.y - 1.0, destination.z);
        world.setBlockState(arrivalFloor.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(arrivalFloor, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 6));
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.SURVIVAL,
                new Vec3d(source.x, source.y - 1.0, source.z - 0.6), 0.0f, 0.0f);
        double[] z = {source.z - 0.6};

        // Le joueur marche vers le sud a 0,2 bloc par tick, sans jamais annoncer son franchissement, puis continue a
        // avancer d'un centimetre par tick : le serveur ne reexamine un joueur qu'en mouvement.
        for (int tick = 1; tick <= 20; tick++) {
            context.runAtTick(tick, () -> {
                if (player.getX() < destination.x) {
                    z[0] += z[0] < source.z + 0.5 ? 0.2 : 0.01;
                    player.refreshPositionAndAngles(source.x, source.y - 1.0, z[0], 0.0f, 0.0f);
                } else {
                    // Arrive : il continue vers l'est, loin du portail de sortie, pendant le cooldown de traversee.
                    player.refreshPositionAndAngles(player.getX() + 0.01, player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                }
            });
        }
        context.runAtTick(25, () -> {
            try {
                context.assertTrue(player.getX() > destination.x, "Le serveur a detecte la marche et teleporte le joueur a l'arrivee : " + player.getPos() + ".");
                context.assertTrue(player.getY() > destination.y - 1.0 + 0.1, "Le joueur a ete remonte au-dessus de la neige : " + player.getY() + ".");
            } finally {
                GameTestPlayers.disconnect(player);
                portal.discard();
                world.setBlockState(arrivalFloor, Blocks.AIR.getDefaultState());
                world.setBlockState(arrivalFloor.down(), Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void aRidingPlayerCrossesWithTheBoatAndAJumpingPlayerStays(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        BackroomsPortalEntity portal = localPortal(context, source, destination, true);
        BoatEntity boat = EntityType.BOAT.create(world);
        boat.setNoGravity(true);
        boat.refreshPositionAndAngles(source.x, source.y - 1.0, source.z - 0.6, 0.0f, 0.0f);
        world.spawnEntity(boat);
        ServerPlayerEntity rider = GameTestPlayers.connect(context, GameMode.SURVIVAL, new Vec3d(source.x, source.y - 1.0, source.z - 0.6), 0.0f, 0.0f);
        ServerPlayerEntity jumper = GameTestPlayers.connect(context, GameMode.SURVIVAL, new Vec3d(source.x, source.y - 1.0, source.z - 10.0), 0.0f, 0.0f);
        context.runAtTick(1, () -> context.assertTrue(rider.startRiding(boat, true), "Le joueur monte dans le bateau."));
        for (int tick = 2; tick <= 10; tick++) {
            context.runAtTick(tick, () -> {
                // Le bateau et son passager franchissent le plan ; le second joueur saute de dix blocs d'un coup.
                boat.refreshPositionAndAngles(boat.getX(), boat.getY(), boat.getZ() + 0.2, 0.0f, 0.0f);
                rider.refreshPositionAndAngles(boat.getX(), boat.getY(), boat.getZ(), 0.0f, 0.0f);
                jumper.refreshPositionAndAngles(source.x, source.y - 1.0, jumper.getZ() < source.z ? source.z + 0.3 : source.z - 10.0, 0.0f, 0.0f);
            });
        }
        context.runAtTick(20, () -> {
            try {
                context.assertTrue(rider.getVehicle() == boat && boat.getX() > destination.x && rider.getX() > destination.x,
                        "En bateau, le joueur traverse avec son bateau, bascule menee par le serveur : " + rider.getPos() + ".");
                context.assertTrue(jumper.getX() < destination.x, "Un saut de position n'est pas une marche : pas de teleportation.");
            } finally {
                rider.stopRiding();
                GameTestPlayers.disconnect(rider);
                GameTestPlayers.disconnect(jumper);
                boat.discard();
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void anArrivalInsideAFullBlockIsLeftWhereItIs(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        BackroomsPortalEntity portal = localPortal(context, source, destination, true);
        BlockPos arrivalBlock = BlockPos.ofFloored(destination.x, destination.y - 1.0, destination.z);
        world.setBlockState(arrivalBlock, Blocks.STONE.getDefaultState());
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE,
                new Vec3d(source.x, source.y - 1.0, source.z + 0.05), 0.0f, 0.0f);
        try {
            context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
            context.assertTrue(Math.abs(player.getY() - (destination.y - 1.0)) < EPSILON,
                    "Un bloc plein ne se contourne pas par une remontee de trente centimetres : " + player.getY() + ".");
        } finally {
            GameTestPlayers.disconnect(player);
            portal.discard();
            world.setBlockState(arrivalBlock, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 2200, batchId = "portal_sync_vanilla")
    public void vanillaWorldChangesResetThePlayersSynchronization(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        ServerPlayerEntity[] respawned = new ServerPlayerEntity[1];
        int[] syncedAt = {-1};

        for (int tick = 20; tick < 2190; tick++) {
            int now = tick;
            context.runAtTick(tick, () -> {
                if (syncedAt[0] < 0) {
                    if (PortalViewTracker.watchedChunkCount() > 0) {
                        syncedAt[0] = now;
                        RemoteChunkPayload first = GameTestPlayers.sent(player, RemoteChunkPayload.class).get(0);
                        // Comme a la veille d'une traversee : son client tient des chunks du Nether.
                        context.assertTrue(PortalViewTracker.keepHeldChunks(player, World.NETHER) > 0, "Le joueur tient des chunks du Nether.");
                        Entity moved = player.teleportTo(new TeleportTarget(nether, NETHER_EXIT, Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
                        context.assertTrue(moved == player && player.getServerWorld() == nether, "Changement de dimension vanilla.");
                        // Vanilla recree le monde du client : il ne tient plus rien, chaque chunk doit lui etre envoye.
                        context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, new ChunkPos(first.chunkX(), first.chunkZ())),
                                "Apres un changement de dimension vanilla, plus aucun chunk n'est tenu.");
                    } else if (now > SYNC_PATIENCE_TICKS) {
                        throw new GameTestException("Aucun chunk du Nether envoye au joueur.");
                    }
                } else if (now - syncedAt[0] == 20) {
                    respawned[0] = context.getWorld().getServer().getPlayerManager().respawnPlayer(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
                    // Comme vanilla a la demande de respawn : la connexion suit le nouveau joueur, sinon la deconnexion
                    // retirerait l'ancien et laisserait le nouveau dans le monde, compte parmi les joueurs des tests suivants.
                    player.networkHandler.player = respawned[0];
                    context.assertTrue(respawned[0] != null && respawned[0] != player, "Le respawn vanilla cree un nouveau joueur.");
                } else if (now - syncedAt[0] == 40) {
                    try {
                        context.assertTrue(respawned[0].isAlive(), "Le joueur reapparu vit.");
                    } finally {
                        GameTestPlayers.disconnect(respawned[0]);
                        portal.discard();
                    }
                    context.complete();
                }
            });
        }
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 200, batchId = "portal_sync_world_change")
    public void aVanillaWorldChangeIsNotTakenForACrossing(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = prepareNether(context);
        // Un portail de l'overworld aux coordonnees de l'arrivee du Nether, hors de l'arene : le trajet de la derniere
        // position du joueur dans le Nether a sa position dans l'overworld couperait son plan.
        int chunkX = ChunkSectionPos.getSectionCoord(NETHER_EXIT.x);
        int chunkZ = ChunkSectionPos.getSectionCoord(NETHER_EXIT.z);
        world.setChunkForced(chunkX, chunkZ, true);
        Vec3d inNether = new Vec3d(NETHER_EXIT.x, NETHER_EXIT.y, NETHER_EXIT.z - 1.0);
        Vec3d inOverworld = new Vec3d(NETHER_EXIT.x, NETHER_EXIT.y, NETHER_EXIT.z + 1.0);
        BackroomsPortalEntity[] portal = new BackroomsPortalEntity[1];
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        long[] since = new long[1];
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (!world.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("Le chunk force de l'overworld ne suit pas encore ses entities.");
                    }
                })
                .createAndAddReported(() -> {
                    portal[0] = localPortal(context, NETHER_EXIT.add(0.0, 1.0, 0.0), context.getAbsolute(new Vec3d(16.5, 2.0, 6.5)), true);
                    LEFTOVERS.add(portal[0]);
                    player.teleportTo(new TeleportTarget(nether, inNether, Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
                    since[0] = context.getTick();
                })
                // Le serveur note la position du joueur dans le Nether a chaque tick.
                .createAndAdd(() -> {
                    if (context.getTick() < since[0] + 3) {
                        throw new GameTestException("Le serveur n'a pas encore note la position du joueur dans le Nether.");
                    }
                })
                .createAndAddReported(() -> {
                    player.teleportTo(new TeleportTarget(world, inOverworld, Vec3d.ZERO, 0.0f, 0.0f, TeleportTarget.NO_OP));
                    since[0] = context.getTick();
                })
                // Au-dela du delai que le serveur laisse au client pour annoncer un passage. Le joueur fait un petit pas a
                // chaque tick, comme un client qui bouge : immobile, le serveur ne reprendrait pas sa detection.
                .createAndAdd(() -> {
                    player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ() + 0.01, 0.0f, 0.0f);
                    if (context.getTick() < since[0] + 12) {
                        throw new GameTestException("Le delai d'annonce d'un passage n'est pas encore ecoule.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(player.getServerWorld() == world && player.getPos().isInRange(inOverworld, 0.5),
                                "Un changement de monde vanilla n'est pas un passage de portail : " + player.getPos() + ".");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal[0].discard();
                        world.setChunkForced(chunkX, chunkZ, false);
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du changement de monde vanilla, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_world_change")
    public void afterWorldChange(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 260)
    public void orphanPortalsRemoveThemselvesOnlyWhenTheirBlockIsProvablyGone(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos noBlockHere = context.getAbsolutePos(new BlockPos(3, 1, 3));
        BackroomsPortalEntity blockless = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1))))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(noBlockHere))
                .size(1.0f, 2.0f).owner(World.OVERWORLD, noBlockHere).build();
        BackroomsPortalEntity unknownOwner = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 2, 1))))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(noBlockHere))
                .size(1.0f, 2.0f).owner(GameTestServerWorlds.nowhere(), noBlockHere).build();
        BackroomsPortalEntity farOwner = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5, 2, 1))))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(noBlockHere))
                .size(1.0f, 2.0f).owner(World.OVERWORLD, new BlockPos(1_000_000, 64, 1_000_000)).build();
        world.spawnEntity(blockless);
        world.spawnEntity(unknownOwner);
        world.spawnEntity(farOwner);
        // Un chargement annonce deux fois n'inscrit pas deux fois le portail.
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(farOwner, world);
        context.assertTrue(BackroomsPortalEntityRegistry.all().stream().filter(portal -> portal == farOwner).count() == 1,
                "Le registre ne tient chaque portail qu'une fois.");

        context.runAtTick(120, () -> {
            try {
                context.assertTrue(blockless.isRemoved(), "Sans bloc portail a l'emplacement annonce, le portail se retire.");
                context.assertTrue(unknownOwner.isRemoved(), "Dimension du bloc inconnue : le portail se retire.");
                context.assertTrue(!farOwner.isRemoved(), "Chunk du bloc non charge : le portail attend.");
            } finally {
                farOwner.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60, batchId = "portal_debug_command")
    public void theDebugCommandListsThenClearsLoadedPortals(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        BackroomsPortalEntity portal = netherPortal(context);
        BackroomsPortalEntity owned = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(PORTAL.east(2))))
                .facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.NETHER).destinationPos(NETHER_EXIT)
                .size(1.0f, 2.0f).owner(World.OVERWORLD, new BlockPos(1_000_000, 64, 1_000_000)).build();
        world.spawnEntity(owned);
        List<String> lines = new ArrayList<>();
        ServerCommandSource source = server.getCommandSource().withOutput(new CommandOutput() {
            @Override
            public void sendMessage(Text message) {
                lines.add(message.getString());
            }

            @Override
            public boolean shouldReceiveFeedback() {
                return true;
            }

            @Override
            public boolean shouldTrackOutput() {
                return true;
            }

            @Override
            public boolean shouldBroadcastConsoleToOps() {
                return false;
            }
        });
        context.runAtTick(5, () -> {
            context.assertTrue(BackroomsPortalEntityRegistry.all().contains(portal) && BackroomsPortalEntityRegistry.all().contains(owned),
                    "Les deux portails sont charges.");
            server.getCommandManager().executeWithPrefix(source, "backrooms portal list");
            context.assertTrue(lines.stream().anyMatch(line -> line.contains("(libre)"))
                    && lines.stream().anyMatch(line -> line.contains("(bloc 1000000, 64, 1000000)"))
                    && lines.stream().anyMatch(line -> line.contains("portail(s) charge(s)")),
                    "La liste montre le portail libre, celui d'un bloc portail et le total : " + lines + ".");
            lines.clear();
            server.getCommandManager().executeWithPrefix(source, "backrooms portal clear");
            context.assertTrue(lines.stream().anyMatch(line -> line.contains("portail(s) supprime(s)")), "La commande annonce sa suppression.");
        });
        context.runAtTick(10, () -> {
            context.assertTrue(portal.isRemoved() && owned.isRemoved(), "Les portails du test sont supprimes.");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void remoteDimensionPayloadDescribesTheNether(TestContext context) {
        ServerWorld nether = GameTestServerWorlds.nether(context);
        RemoteDimensionPayload payload = RemoteDimensionPayload.of(nether, 1);
        context.assertTrue(payload.dimension().equals(World.NETHER.getValue()), "Dimension du Nether.");
        context.assertTrue(payload.dimensionType().equals(Identifier.ofVanilla("the_nether")), "Type de dimension du Nether.");
        context.assertTrue(payload.index() == 1, "Index de la dimension chez le joueur.");
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60, batchId = "portal_sync_invisible")
    public void aPlayerWhoNeverLookedAtThePortalCrossesWithoutHeldChunks(TestContext context) {
        ServerWorld world = context.getWorld();
        // Arrivee generee d'avance : un portail visible ferait envoyer sa fenetre des le premier passage.
        ServerWorld nether = prepareNether(context);
        // Portail invisible : il ne se regarde pas, aucune synchronisation n'a lieu pour ce joueur avant sa teleportation.
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(PORTAL))).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.NETHER).destinationPos(NETHER_EXIT).size(1.0f, 2.0f).visible(false).build();
        world.spawnEntity(portal);
        Vec3d plane = portal.getPos();
        // Dix blocs au nord du portail, qui regarde le nord : face a lui (lacet 0, vers le sud).
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, plane.add(0.0, -1.0, -10.0), 0.0f, 0.0f);
        // Quatre passages de synchronisation plus tard, face au portail invisible : toujours rien recu.
        context.runAtTick(20, () -> {
            try {
                context.assertTrue(GameTestPlayers.sent(player, RemoteChunkPayload.class).isEmpty(), "Un portail invisible ne fait rien envoyer.");
                player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                context.assertTrue(player.getServerWorld() == nether, "Le joueur est dans le Nether, sans chunk tenu a epargner.");
            } finally {
                GameTestPlayers.disconnect(player);
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_weather")
    public void theRemoteWindowCarriesTheWeatherOfItsOwnDimension(TestContext context) {
        ServerWorld nether = prepareNether(context);
        NETHER_WEATHER[0] = ((WorldAccessor) nether).backrooms$getRainGradient();
        NETHER_WEATHER[1] = ((WorldAccessor) nether).backrooms$getThunderGradient();
        BackroomsPortalEntity portal = netherPortal(context);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        // Pluie, puis orage, vus chez le joueur depuis la derniere remise a zero.
        boolean[] seen = new boolean[2];
        long[] since = new long[1];
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(PortalViewTracker.watchedChunkCount() > 0, "Aucun chunk du Nether envoye au joueur."))
                .createAndAddReported(() -> {
                    // La meteo part avec les premiers chunks : celle du Nether, pas celle du monde du joueur.
                    weatherSent(player, seen, NETHER_WEATHER[0], NETHER_WEATHER[1]);
                    context.assertTrue(seen[0] && seen[1], "La pluie et l'orage du Nether partent avec sa fenetre.");
                    seen[0] = false;
                    seen[1] = false;
                    nether.setRainGradient(0.6f);
                    nether.setThunderGradient(0.3f);
                })
                .createAndAdd(() -> {
                    weatherSent(player, seen, 0.6f, 0.3f);
                    context.assertTrue(seen[0] && seen[1], "La meteo du Nether change : le joueur la recoit.");
                    since[0] = context.getTick();
                })
                .createAndAdd(() -> {
                    // Quatre passages de synchronisation (un toutes les 5 ticks), sans changement de meteo.
                    if (context.getTick() < since[0] + 20) {
                        throw new GameTestException("Encore quelques passages de synchronisation.");
                    }
                })
                .createAndAddReported(() -> {
                    context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                            .noneMatch(payload -> payload.packet() instanceof GameStateChangeS2CPacket),
                            "Une meteo inchangee n'est pas renvoyee.");
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    /**
     * Note la pluie et l'orage de cette valeur que le serveur a envoyes au joueur pour le Nether, seule dimension annoncee
     * dans ce test : son index est zero.
     */
    private static void weatherSent(ServerPlayerEntity player, boolean[] seen, float rain, float thunder) {
        for (RemoteEntityPacketPayload payload : GameTestPlayers.sent(player, RemoteEntityPacketPayload.class)) {
            if (payload.dimension() == 0 && payload.packet() instanceof GameStateChangeS2CPacket weather) {
                seen[0] |= weather.getReason() == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED && weather.getValue() == rain;
                seen[1] |= weather.getReason() == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED && weather.getValue() == thunder;
            }
        }
    }

    /** Apres le lot de la meteo, reussi ou non : le Nether retrouve sa meteo, qu'il ne change jamais de lui-meme. */
    @AfterBatch(batchId = "portal_sync_weather")
    public void afterWeather(ServerWorld world) {
        GameTestPlayers.disconnectAll();
        ServerWorld nether = world.getServer().getWorld(World.NETHER);
        nether.setRainGradient(NETHER_WEATHER[0]);
        nether.setThunderGradient(NETHER_WEATHER[1]);
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void portalsToAnUnknownDimensionTeleportNothing(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        BackroomsPortalEntity broken = BackroomsPortalEntity.builder(world)
                .position(source).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(GameTestServerWorlds.nowhere()).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f).build();
        world.spawnEntity(broken);
        BackroomsPortalEntity toNether = netherPortal(context);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, source.add(0.0, -1.0, 0.05), 0.0f, 0.0f);
        ItemEntity item = new ItemEntity(world, source.x, source.y, source.z + 0.05, new ItemStack(Items.IRON_NUGGET));
        world.spawnEntity(item);
        try {
            context.assertTrue(!TeleportExecutor.execute(player, broken), "Sans dimension d'arrivee, le joueur reste.");
            context.assertTrue(!TeleportExecutor.execute(item, broken), "Sans dimension d'arrivee, l'objet reste.");
            context.assertTrue(player.getServerWorld() == world && item.getWorld() == world, "Rien n'a bouge.");
            // Un objet deja retire ne part nulle part, meme vers une dimension qui existe.
            item.discard();
            context.assertTrue(!TeleportExecutor.execute(item, toNether), "Un objet retire ne se teleporte pas.");
        } finally {
            GameTestPlayers.disconnect(player);
            item.discard();
            broken.discard();
            toNether.discard();
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void aCrossingCaughtByTheServerIsDroppedIfThePortalVanishesClosesOrLeadsNowhere(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        Vec3d[] sources = {
                Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1))),
                Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 12))),
                Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(12, 2, 1)))};
        BackroomsPortalEntity[] portals = new BackroomsPortalEntity[3];
        ServerPlayerEntity[] walkers = new ServerPlayerEntity[3];
        portals[0] = localPortal(context, sources[0], destination, true);
        portals[1] = localPortal(context, sources[1], destination, true);
        portals[2] = BackroomsPortalEntity.builder(world)
                .position(sources[2]).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.EAST)
                .destinationDim(GameTestServerWorlds.nowhere()).destinationPos(destination).size(1.0f, 2.0f).build();
        world.spawnEntity(portals[2]);
        for (int i = 0; i < 3; i++) {
            walkers[i] = GameTestPlayers.connect(context, GameMode.SURVIVAL, new Vec3d(sources[i].x, sources[i].y - 1.0, sources[i].z - 0.3), 0.0f, 0.0f);
        }
        double[] z = {sources[0].z - 0.3, sources[1].z - 0.3, sources[2].z - 0.3};
        for (int tick = 1; tick <= 12; tick++) {
            context.runAtTick(tick, () -> {
                for (int i = 0; i < 3; i++) {
                    z[i] += z[i] < sources[i].z + 0.2 ? 0.2 : 0.01;
                    walkers[i].refreshPositionAndAngles(sources[i].x, sources[i].y - 1.0, z[i], 0.0f, 0.0f);
                }
            });
        }
        // Le serveur a vu les franchissements : avant la fin du delai laisse au client, un portail disparait, un autre se
        // ferme, le troisieme n'a pas de dimension d'arrivee.
        context.runAtTick(4, () -> {
            portals[0].discard();
            portals[1].setTeleportable(false);
        });
        context.runAtTick(20, () -> {
            try {
                for (int i = 0; i < 3; i++) {
                    // Un marcheur ne bouge qu'en Z : teleporte, il aurait rejoint l'abscisse de l'arrivee.
                    context.assertTrue(Math.abs(walkers[i].getX() - sources[i].x) < 1.0, "Le marcheur " + i + " n'a pas ete teleporte.");
                }
            } finally {
                for (int i = 0; i < 3; i++) {
                    GameTestPlayers.disconnect(walkers[i]);
                    portals[i].discard();
                }
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 80)
    public void entitiesInCooldownJumpingWithPassengersOrDeadDoNotCross(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        BackroomsPortalEntity portal = localPortal(context, source, destination, true);
        // Un objet encore en cooldown de portail, un bateau et son passager (le bateau passe le plan par sauts de position,
        // sans trajet de tick : ce n'est pas une traversee, et un passager ne traverse jamais seul), un mouton mort : tous
        // dans la boite de traversee.
        ItemEntity cooling = new ItemEntity(world, source.x, source.y, source.z - 0.3, new ItemStack(Items.DIAMOND));
        cooling.setNoGravity(true);
        cooling.setVelocity(0.0, 0.0, 0.5);
        cooling.setPortalCooldown(200);
        world.spawnEntity(cooling);
        BoatEntity boat = EntityType.BOAT.create(world);
        boat.setNoGravity(true);
        boat.refreshPositionAndAngles(source.x, source.y - 1.0, source.z - 0.3, 0.0f, 0.0f);
        world.spawnEntity(boat);
        SheepEntity passenger = EntityType.SHEEP.create(world);
        passenger.setNoGravity(true);
        passenger.refreshPositionAndAngles(source.x, source.y - 1.0, source.z - 0.3, 0.0f, 0.0f);
        passenger.setAiDisabled(true);
        world.spawnEntity(passenger);
        // A plus d'un bloc du bateau, qui embarque de lui-meme les creatures qui le touchent.
        SheepEntity dead = EntityType.SHEEP.create(world);
        dead.setNoGravity(true);
        dead.refreshPositionAndAngles(source.x + 1.5, source.y - 1.0, source.z - 0.3, 0.0f, 0.0f);
        dead.setAiDisabled(true);
        world.spawnEntity(dead);
        context.runAtTick(1, () -> {
            // Le bateau a pu embarquer le mouton de lui-meme au premier tick : on s'assure seulement qu'il est a bord.
            passenger.startRiding(boat, true);
            context.assertTrue(passenger.getVehicle() == boat, "Le mouton est dans le bateau.");
            // Mort sans passer par vanilla (kill) : pas de butin laisse dans l'arene pour les tests suivants.
            dead.setHealth(0.0f);
        });
        for (int tick = 2; tick <= 8; tick++) {
            context.runAtTick(tick, () -> {
                boat.refreshPositionAndAngles(boat.getX(), boat.getY(), boat.getZ() + 0.2, 0.0f, 0.0f);
                dead.refreshPositionAndAngles(dead.getX(), dead.getY(), dead.getZ() + 0.2, 0.0f, 0.0f);
            });
        }
        context.runAtTick(12, () -> {
            try {
                context.assertTrue(cooling.getX() < destination.x, "En cooldown, l'objet ne traverse pas.");
                context.assertTrue(boat.getX() < destination.x && passenger.getX() < destination.x,
                        "Deplaces par sauts de position, bateau et passager restent ensemble, de ce cote.");
                context.assertTrue(dead.getX() < destination.x, "Un mouton mort ne traverse pas.");
            } finally {
                passenger.stopRiding();
                cooling.discard();
                boat.discard();
                passenger.discard();
                dead.discard();
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 2200, batchId = "portal_sync_arrival")
    public void anEntityArrivingInAWatchedWindowReachesItsViewersInTheSameTick(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        Vec3d plane = portal.getPos();
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // L'arrivee doit faire partie de la fenetre envoyee, et son chunk suivre ses entities.
                    if (PortalViewTracker.watchedChunkCount() == 0 || !nether.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("La fenetre du Nether n'est pas encore chez le joueur.");
                    }
                })
                .createAndAddReported(() -> {
                    ItemEntity nugget = new ItemEntity(world, plane.x, plane.y, plane.z - 0.3, new ItemStack(Items.GOLD_NUGGET));
                    nugget.setNoGravity(true);
                    world.spawnEntity(nugget);
                    ItemEntity local = new ItemEntity(world, plane.x + 3.0, plane.y, plane.z - 3.0, new ItemStack(Items.IRON_NUGGET));
                    local.setNoGravity(true);
                    world.spawnEntity(local);
                    try {
                        context.assertTrue(TeleportExecutor.execute(nugget, portal), "La pepite passe dans le Nether.");
                        // Dans ce meme tick, avant tout alignement periodique de la fenetre : la copie est deja partie.
                        context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                                        .anyMatch(payload -> spawnOf(payload, nugget.getUuid())),
                                "La copie arrivee part au joueur qui regarde, au meme tick que la traversee.");
                        // Le joueur ne regarde pas sa propre dimension par un portail : une arrivee y vient de vanilla.
                        PortalViewTracker.onEntityArrived(world, local);
                        context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                                        .noneMatch(payload -> spawnOf(payload, local.getUuid())),
                                "Une arrivee dans une dimension que le joueur ne regarde pas par un portail ne lui est pas envoyee.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        GameTestServerWorlds.discardEverywhere(nether, nugget);
                        local.discard();
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 2200, batchId = "portal_departure")
    public void theSideAPlayerLeavesStaysLoadedWithTheSameEntities(TestContext context) {
        ServerWorld world = context.getWorld();
        prepareNether(context);
        // Au milieu d'un chunk : portail, joueur et statue dans le meme, celui que les tickets du joueur font tiquer.
        Vec3d far = Vec3d.ofCenter(context.getAbsolutePos(PORTAL)).add(0.0, 0.0, -DEPARTURE_OFFSET);
        Vec3d center = new Vec3d(chunkMiddle(far.x), far.y, chunkMiddle(far.z));
        GameTestServerWorlds.pregenerate(world, center, 2);
        BackroomsPortalEntity portal = GameTestServerWorlds.spawnNorthPortal(world, center, World.NETHER, NETHER_EXIT);
        ArmorStandEntity stand = new ArmorStandEntity(world, center.x + 2.0, center.y, center.z - 2.0);
        stand.setNoGravity(true);
        world.spawnEntity(stand);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(center.x, center.y - 1.0, center.z - 3.0), 0.0f, 0.0f);
        // Loin de l'arene, rien ne nettoierait ce qui reste : le lot le retire apres lui, reussi ou non.
        LEFTOVERS.add(stand);
        LEFTOVERS.add(portal);
        long[] crossedAt = {-1L};
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // Les chunks du joueur sont prets : l'entity y tique, comme autour de lui dans une partie.
                    if (!world.shouldTickEntity(stand.getBlockPos())) {
                        throw new GameTestException("Les chunks du joueur ne sont pas encore prets.");
                    }
                })
                .createAndAddReported(() -> {
                    player.refreshPositionAndAngles(center.x, center.y - 1.0, center.z + 0.05, 0.0f, 0.0f);
                    context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                    context.assertTrue(player.getServerWorld() != world, "Le joueur a quitte l'overworld.");
                    crossedAt[0] = context.getTick();
                })
                .createAndAdd(() -> {
                    if (context.getTick() - crossedAt[0] < DEPARTURE_SETTLE_TICKS) {
                        throw new GameTestException("Les tickets du joueur ne sont pas encore retires depuis assez longtemps.");
                    }
                })
                // Aucun portail de retour ne tient ce cote : seul le ticket de depart le garde charge.
                .createAndAddReported(() -> context.assertTrue(!stand.isRemoved() && world.getEntity(stand.getUuid()) == stand,
                        "Le cote quitte reste charge : l'entity est la meme, sans dechargement ni nouvel identifiant."))
                .completeIfSuccessful();
    }

    /** Apres le lot de l'arrivee suivie, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_arrival")
    public void afterArrival(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot du cote quitte, reussi ou non : joueur deconnecte, portail et statue retires, loin de l'arene. */
    @AfterBatch(batchId = "portal_departure")
    public void afterDeparture(ServerWorld world) {
        releaseLeftovers();
    }

    // Deux attentes de synchronisation, le delai de liberation par defaut (1200 ticks) et une marge.
    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 6000, batchId = "portal_sync_release")
    public void aDimensionWithNoPortalInRangeIsReleasedThenAnnouncedAgain(TestContext context) {
        prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        int[] phase = {0};
        int[] phaseStart = {0};
        for (int tick = 1; tick < 2 * SYNC_PATIENCE_TICKS + RELEASE_DELAY_TICKS + 390; tick++) {
            int now = tick;
            context.runAtTick(tick, () -> {
                int elapsed = now - phaseStart[0];
                switch (phase[0]) {
                    case 0 -> {
                        // La fenetre arrive : la dimension a ete annoncee une fois, sous l'index 0.
                        if (PortalViewTracker.watchedChunkCount() > 0) {
                            List<RemoteDimensionPayload> announced = GameTestPlayers.sent(player, RemoteDimensionPayload.class);
                            context.assertTrue(announced.size() == 1 && announced.get(0).index() == 0,
                                    "Le Nether est annonce une fois, sous l'index 0 : " + announced + ".");
                            // Puis le joueur part a cent blocs : plus aucun portail a portee.
                            moveTo(player, context.getAbsolute(VIEWER).add(100.0, 0.0, 0.0), 0.0f);
                            phase[0] = 1;
                            phaseStart[0] = now;
                        } else if (now > SYNC_PATIENCE_TICKS) {
                            throw new GameTestException("Aucun chunk du Nether envoye au joueur en " + now + " ticks.");
                        }
                    }
                    case 1 -> {
                        if (elapsed == 120) {
                            // Passe le delai de grace (cent ticks), la fenetre est dechargee, le monde distant garde.
                            context.assertTrue(PortalViewTracker.watchedChunkCount() == 0
                                    && GameTestPlayers.sent(player, RemoteDimensionReleasePayload.class).isEmpty(),
                                    "Passe le delai de grace, la fenetre est dechargee mais le monde distant est garde.");
                        } else if (elapsed == RELEASE_DELAY_TICKS + 20) {
                            List<RemoteDimensionReleasePayload> released = GameTestPlayers.sent(player, RemoteDimensionReleasePayload.class);
                            context.assertTrue(released.size() == 1 && released.get(0).dimension() == 0,
                                    "Passe le delai de liberation, le serveur fait liberer le Nether, une fois : " + released + ".");
                            // Le joueur revient devant le portail : le Nether est annonce de nouveau, sous le meme index, puis renvoye.
                            moveTo(player, context.getAbsolute(VIEWER), 0.0f);
                            phase[0] = 2;
                            phaseStart[0] = now;
                        }
                    }
                    case 2 -> {
                        if (PortalViewTracker.watchedChunkCount() > 0) {
                            List<RemoteDimensionPayload> again = GameTestPlayers.sent(player, RemoteDimensionPayload.class);
                            context.assertTrue(again.size() == 1 && again.get(0).index() == 0
                                    && !GameTestPlayers.sent(player, RemoteChunkPayload.class).isEmpty(),
                                    "De retour, le Nether est annonce de nouveau sous l'index 0 et ses chunks renvoyes : " + again + ".");
                            context.assertTrue(GameTestPlayers.sent(player, RemoteDimensionReleasePayload.class).isEmpty(),
                                    "Une seule liberation, pas une par passage.");
                            phase[0] = 3;
                            context.complete();
                        } else if (elapsed > SYNC_PATIENCE_TICKS) {
                            throw new GameTestException("La fenetre n'est pas renvoyee apres la liberation.");
                        }
                    }
                    default -> {
                        // Test termine.
                    }
                }
            });
        }
    }

    /** Apres le lot de la liberation, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_release")
    public void afterRelease(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot de la fenetre, reussi ou non : joueur deconnecte, portails et objets du Nether retires. */
    @AfterBatch(batchId = "portal_sync_window")
    public void afterWindow(ServerWorld world) {
        ModPerformanceMonitor.setEnabled(false);
        releaseLeftovers();
    }

    /** Apres le lot de la traversee, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_crossing")
    public void afterCrossing(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot des changements de monde vanilla, reussi ou non : joueurs deconnectes, portail retire. */
    @AfterBatch(batchId = "portal_sync_vanilla")
    public void afterVanilla(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot du ticket, reussi ou non : joueur deconnecte, portail et objet du Nether retires. */
    @AfterBatch(batchId = "portal_sync_ticket")
    public void afterTicket(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_window_edge")
    public void aBlockChangedAtTheEdgeOfTheWindowIsRelayed(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        Vec3d plane = portal.getPos();
        // Regarde de loin : le ticket de l'arrivee a le rayon de la fenetre synchronisee, son anneau exterieur est charge
        // sans etre actif, et vanilla n'y previent aucun ecouteur d'un changement de bloc. Le bord pris est dans l'axe du
        // cadre (l'arrivee montre le sud) : de loin, la fenetre suit cet axe.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(plane.x, plane.y - 1.0, plane.z - 12.0), 0.0f, 0.0f);
        BlockPos block = windowEdgeBlock();
        ChunkPos chunk = new ChunkPos(block);
        List<RemoteChunkPayload> chunks = new ArrayList<>();
        List<RemoteBlockUpdatePayload> blocks = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    if (!chunkSent(chunks, World.NETHER.getValue(), chunk)) {
                        throw new GameTestException("Le chunk du bord de la fenetre n'est pas encore synchronise.");
                    }
                    WorldChunk edge = nether.getChunkManager().getWorldChunk(chunk.x, chunk.z);
                    if (edge == null || edge.getLevelType().isAfter(ChunkLevelType.BLOCK_TICKING)) {
                        throw new GameTestException("Le chunk du bord de la fenetre n'est pas encore seulement charge.");
                    }
                })
                .createAndAddReported(() -> nether.setBlockState(block, Blocks.GLOWSTONE.getDefaultState()))
                .createAndAdd(() -> {
                    blocks.addAll(GameTestPlayers.sent(player, RemoteBlockUpdatePayload.class));
                    if (blocks.stream().noneMatch(update -> update.pos().equals(block) && update.state().isOf(Blocks.GLOWSTONE))) {
                        throw new GameTestException("Le bloc change au bord de la fenetre n'est pas encore relaye.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        nether.setBlockState(block, Blocks.AIR.getDefaultState());
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du bord de la fenetre, reussi ou non : joueur deconnecte, portail retire, bloc rendu. */
    @AfterBatch(batchId = "portal_sync_window_edge")
    public void afterWindowEdge(ServerWorld world) {
        releaseLeftovers();
        world.getServer().getWorld(World.NETHER).setBlockState(windowEdgeBlock(), Blocks.AIR.getDefaultState());
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 400 + HELD_WINDOW_TICKS, batchId = "portal_sync_cone")
    public void aFarViewerGetsTheWindowAlongTheFrameAxis(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        Vec3d plane = portal.getPos();
        // Douze blocs au nord, de face, avec la portee d'un vrai client : la fenetre suit l'axe du cadre, l'arrivee montre le sud.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(plane.x, plane.y - 1.0, plane.z - 12.0), 0.0f, 0.0f);
        withViewDistance(player, 8);
        int exitX = ChunkSectionPos.getSectionCoord(NETHER_EXIT.x);
        int exitZ = ChunkSectionPos.getSectionCoord(NETHER_EXIT.z);
        // Au-dela du carre de la fenetre et du ticket de l'arrivee, dans la zone generee d'avance.
        ChunkPos deep = new ChunkPos(exitX, exitZ + DestinationLoader.syncRadius(false) + 2);
        ChunkPos side = new ChunkPos(exitX + DestinationLoader.syncRadius(false), exitZ);
        List<RemoteChunkPayload> chunks = new ArrayList<>();
        List<RemoteChunkUnloadPayload> unloads = new ArrayList<>();
        int[] heldFor = {0};
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    if (!chunkSent(chunks, World.NETHER.getValue(), deep)) {
                        throw new GameTestException("Le chunk dans l'axe du cadre, au-dela du carre, n'est pas encore synchronise.");
                    }
                })
                .createAndAddReported(() -> context.assertTrue(!chunkSent(chunks, World.NETHER.getValue(), side),
                        "De loin, rien sur le cote de l'arrivee, hors du coin de vue."))
                .createAndAdd(() -> {
                    // Le joueur reste devant le cadre plus longtemps que le delai de grace des tickets de fenetre.
                    if (++heldFor[0] <= HELD_WINDOW_TICKS) {
                        throw new GameTestException("Le joueur regarde encore le cadre, le temps du delai de grace.");
                    }
                })
                .createAndAddReported(() -> {
                    context.assertTrue(nether.getChunkManager().getWorldChunk(deep.x, deep.z) != null,
                            "Toujours voulu, le chunk du cone reste tenu entier par son ticket, delai de grace passe.");
                    // Derriere le portail, de loin : sa vue ne se voit pas d'ici, sa fenetre est dechargee apres le delai de grace.
                    moveTo(player, new Vec3d(plane.x, plane.y - 1.0, plane.z + 12.0), 180.0f);
                })
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    unloads.addAll(GameTestPlayers.sent(player, RemoteChunkUnloadPayload.class));
                    if (unloads.stream().noneMatch(unload -> unload.chunkPos().equals(deep))) {
                        throw new GameTestException("Derriere le portail, la fenetre n'est pas encore dechargee.");
                    }
                })
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    if (nether.getChunkManager().getWorldChunk(deep.x, deep.z) != null) {
                        throw new GameTestException("Plus voulu, le chunk du cone n'est pas encore relache apres le delai de grace.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        // Tout ce qui est parti depuis le passage derriere le portail, delai de grace compris.
                        chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                        context.assertTrue(!chunkSent(chunks, World.NETHER.getValue(), side), "Derriere le portail, aucune fenetre carree a la place.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du cone, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_cone")
    public void afterCone(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS, batchId = "portal_sync_cone_lying")
    public void aLyingArrivalKeepsTheSquareWindowFromAfar(TestContext context) {
        prepareNether(context);
        // Arrivee couchee au sol : ce qu'elle montre est au-dessus d'elle, pas dans un axe horizontal.
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(context.getWorld())
                .position(Vec3d.ofCenter(context.getAbsolutePos(PORTAL)))
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.UP)
                .destinationDim(World.NETHER)
                .destinationPos(NETHER_EXIT)
                .size(1.0f, 2.0f)
                .build();
        context.getWorld().spawnEntity(portal);
        LEFTOVERS.add(portal);
        Vec3d plane = portal.getPos();
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(plane.x, plane.y - 1.0, plane.z - 12.0), 0.0f, 0.0f);
        withViewDistance(player, 8);
        ChunkPos side = new ChunkPos(ChunkSectionPos.getSectionCoord(NETHER_EXIT.x) + DestinationLoader.syncRadius(false),
                ChunkSectionPos.getSectionCoord(NETHER_EXIT.z));
        List<RemoteChunkPayload> chunks = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    if (!chunkSent(chunks, World.NETHER.getValue(), side)) {
                        throw new GameTestException("Le bord de la fenetre carree d'une arrivee couchee n'est pas encore synchronise.");
                    }
                })
                .createAndAddReported(() -> {
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    /** Apres le lot de l'arrivee couchee, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_cone_lying")
    public void afterConeLying(ServerWorld world) {
        releaseLeftovers();
    }

    /** Donne au joueur simule la distance de vue d'un vrai client, en chunks. */
    private static void withViewDistance(ServerPlayerEntity player, int chunks) {
        SyncedClientOptions options = player.getClientOptions();
        player.setClientOptions(new SyncedClientOptions(options.language(), chunks, options.chatVisibility(), options.chatColorsEnabled(),
                options.playerModelParts(), options.mainArm(), options.filtersText(), options.allowsServerListing()));
    }

    /** Bloc du chunk au bord de la fenetre d'arrivee, dans l'axe du cadre, sous l'arrivee. */
    private static BlockPos windowEdgeBlock() {
        int edgeChunkZ = ChunkSectionPos.getSectionCoord(NETHER_EXIT.z) + DestinationLoader.syncRadius(false);
        return new BlockPos(BlockPos.ofFloored(NETHER_EXIT).getX(), BlockPos.ofFloored(NETHER_EXIT).getY() - 2,
                ChunkSectionPos.getBlockCoord(edgeChunkZ) + 8);
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_unload")
    public void aRemoteEntityThatLeavesItsWorldVanishesForItsViewersAtOnce(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        ItemEntity[] item = new ItemEntity[1];
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // Sous le ticket du chargeur d'arrivee, un objet pose y reste, suivi par vanilla.
                    if (PortalViewTracker.watchedChunkCount() == 0 || !nether.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("L'arrivee n'est pas encore synchronisee et tenue par le chargeur.");
                    }
                })
                .createAndAddReported(() -> {
                    item[0] = new ItemEntity(nether, NETHER_EXIT.x, NETHER_EXIT.y, NETHER_EXIT.z + 1.0, new ItemStack(Items.EMERALD));
                    item[0].setNoGravity(true);
                    nether.spawnEntity(item[0]);
                    LEFTOVERS.add(item[0]);
                })
                .createAndAdd(() -> {
                    if (GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream().noneMatch(payload -> spawnOf(payload, item[0].getUuid()))) {
                        throw new GameTestException("L'objet de l'arrivee n'est pas encore apparu chez le joueur.");
                    }
                })
                .createAndAddReported(() -> {
                    int id = item[0].getId();
                    item[0].discard();
                    // Le meme tick, avant tout alignement de la fenetre : la destruction de sa copie distante est partie.
                    context.assertTrue(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                            .anyMatch(payload -> payload.packet() instanceof EntitiesDestroyS2CPacket destroy && destroy.getEntityIds().contains(id)),
                            "Retiree de son monde, l'entity disparait aussitot chez qui la voyait par le portail.");
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du retrait d'une entity distante, reussi ou non : joueur deconnecte, portail et objet retires. */
    @AfterBatch(batchId = "portal_sync_unload")
    public void afterUnload(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_spectator")
    public void aSpectatorIsShownThroughAPortalOnlyToSpectators(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        ServerPlayerEntity[] spectator = new ServerPlayerEntity[1];
        ItemEntity[] item = new ItemEntity[1];
        // Tout ce que le joueur a recu, lu au fil des etapes : une lecture consomme ce qu'elle rend.
        List<RemoteEntityPacketPayload> received = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (PortalViewTracker.watchedChunkCount() == 0 || !nether.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("L'arrivee n'est pas encore synchronisee et tenue par le chargeur.");
                    }
                })
                .createAndAddReported(() -> {
                    // Un spectateur passe le portail (son arrivee est suivie aussitot par qui la voit), et un objet a cote de
                    // l'arrivee : temoin qu'un alignement de la fenetre est passe depuis.
                    Vec3d plane = portal.getPos();
                    spectator[0] = GameTestPlayers.connect(context, GameMode.SPECTATOR, new Vec3d(plane.x, plane.y - 1.0, plane.z + 0.05), 0.0f, 0.0f);
                    context.assertTrue(TeleportExecutor.execute(spectator[0], portal), "Le spectateur passe le portail.");
                    item[0] = new ItemEntity(nether, NETHER_EXIT.x + 1.0, NETHER_EXIT.y, NETHER_EXIT.z + 2.0, new ItemStack(Items.EMERALD));
                    item[0].setNoGravity(true);
                    nether.spawnEntity(item[0]);
                    LEFTOVERS.add(item[0]);
                })
                .createAndAdd(() -> {
                    received.addAll(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class));
                    if (received.stream().noneMatch(payload -> spawnOf(payload, item[0].getUuid()))) {
                        throw new GameTestException("L'objet a cote du spectateur n'est pas encore apparu chez le joueur.");
                    }
                })
                .createAndAddReported(() -> {
                    received.addAll(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class));
                    context.assertTrue(received.stream().noneMatch(payload -> spawnOf(payload, spectator[0].getUuid())),
                            "Comme vanilla, un spectateur n'est pas montre par le portail a qui ne l'est pas.");
                    spectator[0].changeGameMode(GameMode.CREATIVE);
                })
                .createAndAdd(() -> {
                    received.addAll(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class));
                    if (received.stream().noneMatch(payload -> spawnOf(payload, spectator[0].getUuid()))) {
                        throw new GameTestException("Redevenu createur, le joueur n'est pas encore montre par le portail.");
                    }
                })
                .createAndAddReported(() -> {
                    GameTestPlayers.disconnect(spectator[0]);
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_block_entity")
    public void aSignWrittenOnTheOtherSideIsReadThroughThePortal(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        BlockPos sign = BlockPos.ofFloored(NETHER_EXIT).add(1, 0, 2);
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    ChunkPos chunk = new ChunkPos(sign);
                    if (GameTestPlayers.sent(player, RemoteChunkPayload.class).stream()
                            .noneMatch(payload -> payload.chunkX() == chunk.x && payload.chunkZ() == chunk.z)) {
                        throw new GameTestException("Le chunk du panneau n'est pas encore synchronise.");
                    }
                })
                .createAndAddReported(() -> {
                    nether.setBlockState(sign.down(), Blocks.STONE.getDefaultState());
                    nether.setBlockState(sign, Blocks.OAK_SIGN.getDefaultState());
                    // Le texte change ensuite, l'etat du bloc non : seules les donnees de la block entity le portent.
                    ((SignBlockEntity) nether.getBlockEntity(sign)).setText(new SignText().withMessage(0, Text.literal("Bonjour")), true);
                })
                .createAndAdd(() -> {
                    if (GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                            .noneMatch(payload -> payload.packet() instanceof BlockEntityUpdateS2CPacket update && update.getPos().equals(sign)
                                    && update.getNbt().toString().contains("Bonjour"))) {
                        throw new GameTestException("Le texte du panneau n'est pas encore parti vers qui le voit par le portail.");
                    }
                })
                .createAndAddReported(() -> {
                    nether.setBlockState(sign, Blocks.AIR.getDefaultState());
                    nether.setBlockState(sign.down(), Blocks.AIR.getDefaultState());
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_cracks")
    public void cracksOnTheOtherSideAreSeenThroughThePortal(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        BlockPos block = BlockPos.ofFloored(NETHER_EXIT).add(1, 0, 2);
        ChunkPos chunk = new ChunkPos(block);
        List<RemoteEntityPacketPayload> received = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (GameTestPlayers.sent(player, RemoteChunkPayload.class).stream()
                            .noneMatch(payload -> payload.chunkX() == chunk.x && payload.chunkZ() == chunk.z)) {
                        throw new GameTestException("Le chunk du bloc n'est pas encore synchronise.");
                    }
                })
                // Quelqu'un mine ce bloc de l'autre cote.
                .createAndAddReported(() -> nether.setBlockBreakingInfo(4242, block, 5))
                .createAndAdd(() -> {
                    received.addAll(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class));
                    if (received.stream().noneMatch(payload -> payload.packet() instanceof BlockBreakingProgressS2CPacket crack
                            && crack.getEntityId() == 4242 && crack.getPos().equals(block) && crack.getProgress() == 5)) {
                        throw new GameTestException("Les fissures de l'autre cote ne sont pas encore parties vers qui les voit par le portail.");
                    }
                })
                .createAndAddReported(() -> {
                    nether.setBlockBreakingInfo(4242, block, -1);
                    GameTestPlayers.disconnect(player);
                    portal.discard();
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_quick_view")
    public void theViewOfAPlayerWhoCrossedRestartsAtOnce(TestContext context) {
        ServerWorld nether = prepareNether(context);
        MinecraftServer server = context.getWorld().getServer();
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        BackroomsPortalEntity[] back = new BackroomsPortalEntity[1];
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        Vec3d plane = portal.getPos();
        long[] crossedAt = new long[1];
        long[] announcedAt = new long[1];
        List<RemoteDimensionPayload> announced = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (PortalViewTracker.watchedChunkCount() == 0 || !nether.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("L'arrivee n'est pas encore synchronisee et tenue par le chargeur.");
                    }
                })
                // A l'arrivee, un portail qui ramene dans l'arene : de l'autre cote, la vue du joueur montrera l'overworld.
                .createAndAddReported(() -> {
                    back[0] = GameTestServerWorlds.spawnNorthPortal(nether, NETHER_EXIT.add(0.0, 1.0, 3.0), World.OVERWORLD,
                            context.getAbsolute(new Vec3d(12.5, 2.0, 12.5)));
                    LEFTOVERS.add(back[0]);
                })
                .createAndAdd(() -> {
                    if (BackroomsPortalEntityRegistry.find(nether, back[0].getUuid()) == null) {
                        throw new GameTestException("Le portail de l'arrivee n'est pas encore charge.");
                    }
                })
                // Juste apres un passage regulier des vues : le suivant est a quatre ticks.
                .createAndAdd(() -> {
                    if (server.getTicks() % 5 != 1) {
                        throw new GameTestException("Pas encore juste apres un passage regulier des vues.");
                    }
                })
                .createAndAddReported(() -> {
                    GameTestPlayers.sent(player, RemoteDimensionPayload.class);
                    player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                    context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                    crossedAt[0] = server.getTicks();
                })
                .createAndAdd(() -> {
                    announced.addAll(GameTestPlayers.sent(player, RemoteDimensionPayload.class));
                    if (announced.stream().noneMatch(payload -> payload.dimension().equals(World.OVERWORLD.getValue()))) {
                        throw new GameTestException("La vue de l'overworld n'est pas encore annoncee au joueur passe dans le Nether.");
                    }
                    announcedAt[0] = server.getTicks();
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(announcedAt[0] - crossedAt[0] <= 2,
                                "La vue d'un joueur qui vient de passer repart des le tick suivant, sans attendre le passage regulier : "
                                        + (announcedAt[0] - crossedAt[0]) + " ticks.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                        back[0].discard();
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot de la vue qui repart, reussi ou non : joueur deconnecte, portails retires. */
    @AfterBatch(batchId = "portal_sync_quick_view")
    public void afterQuickView(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 200, batchId = "portal_sync_two_dimensions")
    public void eachDimensionOfAViewHasItsOwnIndex(TestContext context) {
        ServerWorld nether = prepareNether(context);
        ServerWorld end = context.getWorld().getServer().getWorld(World.END);
        context.assertTrue(end != null, "Le serveur de test a un End.");
        GameTestServerWorlds.pregenerateArrival(end, END_EXIT);
        BackroomsPortalEntity toNether = netherPortal(context);
        BackroomsPortalEntity toEnd = GameTestServerWorlds.spawnNorthPortal(context.getWorld(), Vec3d.ofCenter(context.getAbsolutePos(SECOND_PORTAL)), World.END, END_EXIT);
        LEFTOVERS.add(toNether);
        LEFTOVERS.add(toEnd);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        BlockPos netherBlock = BlockPos.ofFloored(NETHER_EXIT).down(2);
        BlockPos endBlock = BlockPos.ofFloored(END_EXIT).down(2);
        // Tout ce que le joueur a recu, lu au fil des etapes : une lecture consomme ce qu'elle rend.
        List<RemoteDimensionPayload> announced = new ArrayList<>();
        List<RemoteEntityPacketPayload> entities = new ArrayList<>();
        List<RemoteBlockUpdatePayload> blocks = new ArrayList<>();
        List<RemoteChunkUnloadPayload> unloads = new ArrayList<>();
        List<RemoteChunkPayload> chunks = new ArrayList<>();
        ChunkPos netherChunk = new ChunkPos(netherBlock);
        ChunkPos endChunk = new ChunkPos(endBlock);
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    announced.addAll(GameTestPlayers.sent(player, RemoteDimensionPayload.class));
                    if (announced.stream().map(RemoteDimensionPayload::dimension).distinct().count() < 2) {
                        throw new GameTestException("Les deux dimensions ne sont pas encore annoncees au joueur.");
                    }
                })
                // Un changement de bloc n'est relaye que dans un chunk deja envoye : sur un monde neuf, le chunk du bloc
                // part quelques ticks apres l'annonce de sa dimension, qui accompagne le premier chunk pret.
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    if (!chunkSent(chunks, World.NETHER.getValue(), netherChunk) || !chunkSent(chunks, World.END.getValue(), endChunk)) {
                        throw new GameTestException("Les chunks des deux blocs ne sont pas encore synchronises.");
                    }
                })
                .createAndAddReported(() -> {
                    int netherIndex = indexOf(announced, World.NETHER.getValue());
                    int endIndex = indexOf(announced, World.END.getValue());
                    context.assertTrue(announced.size() == 2, "Chaque dimension est annoncee une fois : " + announced + ".");
                    context.assertTrue(Math.min(netherIndex, endIndex) == 0 && Math.max(netherIndex, endIndex) == 1,
                            "Les index sont 0 et 1, dans l'ordre des annonces : " + announced + ".");
                    nether.setBlockState(netherBlock, Blocks.GLOWSTONE.getDefaultState());
                    end.setBlockState(endBlock, Blocks.GLOWSTONE.getDefaultState());
                })
                .createAndAdd(() -> {
                    blocks.addAll(GameTestPlayers.sent(player, RemoteBlockUpdatePayload.class));
                    entities.addAll(GameTestPlayers.sent(player, RemoteEntityPacketPayload.class));
                    if (blocks.stream().noneMatch(update -> update.pos().equals(netherBlock)) || blocks.stream().noneMatch(update -> update.pos().equals(endBlock))) {
                        throw new GameTestException("Les blocs changes des deux arrivees ne sont pas encore relayes.");
                    }
                })
                .createAndAddReported(() -> {
                    int netherIndex = indexOf(announced, World.NETHER.getValue());
                    int endIndex = indexOf(announced, World.END.getValue());
                    for (RemoteBlockUpdatePayload update : blocks) {
                        context.assertTrue(update.dimension() == (update.pos().equals(netherBlock) ? netherIndex : endIndex),
                                "Un bloc relaye porte l'index de sa dimension : " + update + ".");
                    }
                    context.assertTrue(weatherUnder(entities, netherIndex) && weatherUnder(entities, endIndex),
                            "La meteo de chaque dimension est envoyee sous son index.");
                    toNether.discard();
                    toEnd.discard();
                })
                .createAndAdd(() -> {
                    unloads.addAll(GameTestPlayers.sent(player, RemoteChunkUnloadPayload.class));
                    if (PortalViewTracker.watchedChunkCount() > 0) {
                        throw new GameTestException("Les chunks des deux dimensions ne sont pas encore decharges.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        unloads.addAll(GameTestPlayers.sent(player, RemoteChunkUnloadPayload.class));
                        context.assertTrue(unloads.stream().anyMatch(unload -> unload.chunkPos().equals(netherChunk))
                                && unloads.stream().anyMatch(unload -> unload.chunkPos().equals(endChunk)), "Les chunks des deux arrivees sont decharges.");
                        int netherIndex = indexOf(announced, World.NETHER.getValue());
                        int endIndex = indexOf(announced, World.END.getValue());
                        for (RemoteChunkUnloadPayload unload : unloads) {
                            boolean nearNether = unload.chunkPos().getChebyshevDistance(netherChunk) < unload.chunkPos().getChebyshevDistance(endChunk);
                            context.assertTrue(unload.dimension() == (nearNether ? netherIndex : endIndex),
                                    "Un dechargement porte l'index de sa dimension : " + unload + ".");
                        }
                    } finally {
                        GameTestPlayers.disconnect(player);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 400, batchId = "portal_sync_nested")
    public void aPortalSeenThroughAnotherOpensItsOwnWorld(TestContext context) {
        ServerWorld nether = prepareNether(context);
        ServerWorld end = context.getWorld().getServer().getWorld(World.END);
        context.assertTrue(end != null, "Le serveur de test a un End.");
        GameTestServerWorlds.pregenerateArrival(end, END_EXIT);
        BackroomsPortalEntity toNether = netherPortal(context);
        LEFTOVERS.add(toNether);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        // Les portails du Nether, poses une fois la fenetre de l'arrivee chargee : tous dans cette fenetre.
        BackroomsPortalEntity[] nested = new BackroomsPortalEntity[4];
        Vec3d farEnd = END_EXIT.add(4000.0, 0.0, 0.0);
        ChunkPos endChunk = new ChunkPos(BlockPos.ofFloored(END_EXIT));
        int farEndChunkX = ChunkSectionPos.getSectionCoord(farEnd.x);
        int farEndChunkZ = ChunkSectionPos.getSectionCoord(farEnd.z);
        int[] settle = new int[1];
        List<RemoteChunkPayload> chunks = new ArrayList<>();
        List<RemoteDimensionPayload> announced = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (PortalViewTracker.watchedChunkCount() == 0) {
                        throw new GameTestException("La fenetre du Nether n'est pas encore envoyee.");
                    }
                })
                .createAndAddReported(() -> {
                    // Vers l'End : foyer de l'End, que seul ce portail atteint ; puis vers l'End trop loin de ce foyer pour la
                    // carte du client ; vers un autre point du Nether, autour du foyer du Nether ; vers le monde du joueur.
                    nested[0] = GameTestServerWorlds.spawnNorthPortal(nether, NETHER_EXIT.add(2.0, 0.0, -3.0), World.END, END_EXIT);
                    nested[1] = GameTestServerWorlds.spawnNorthPortal(nether, NETHER_EXIT.add(-2.0, 0.0, -3.0), World.END, farEnd);
                    nested[2] = GameTestServerWorlds.spawnNorthPortal(nether, NETHER_EXIT.add(4.0, 0.0, -3.0), World.NETHER, NETHER_EXIT.add(16.0, 0.0, 0.0));
                    nested[3] = GameTestServerWorlds.spawnNorthPortal(nether, NETHER_EXIT.add(-4.0, 0.0, -3.0), World.OVERWORLD,
                            context.getAbsolute(new Vec3d(20.5, 2.0, 20.5)));
                    LEFTOVERS.addAll(List.of(nested));
                })
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    announced.addAll(GameTestPlayers.sent(player, RemoteDimensionPayload.class));
                    if (!chunkSent(chunks, World.END.getValue(), endChunk)) {
                        throw new GameTestException("L'arrivee du portail vu a travers l'autre n'est pas encore envoyee.");
                    }
                })
                // Quelques recalculs de plus : une fenetre voulue a tort aurait deja son ticket, et ses chunks proches partiraient.
                .createAndAdd(() -> {
                    chunks.addAll(GameTestPlayers.sent(player, RemoteChunkPayload.class));
                    // Quatre recalculs : le serveur revoit les fenetres d'un joueur toutes les cinq ticks.
                    if (++settle[0] < 20) {
                        throw new GameTestException("Recalculs des fenetres en cours.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertFalse(end.isChunkLoaded(farEndChunkX, farEndChunkZ),
                                "Une arrivee hors de la carte du client autour du foyer de l'End n'a aucun ticket : elle n'est pas voulue.");
                        context.assertTrue(indexOf(announced, World.END.getValue()) >= 0,
                                "L'End, que montre un portail vu a travers celui du Nether, est annonce au joueur : " + announced + ".");
                        context.assertTrue(chunks.stream().noneMatch(chunk -> chunk.dimension().equals(World.END.getValue())
                                && Math.abs(chunk.chunkX() - farEndChunkX) < 8), "Une arrivee hors de la carte du client autour du foyer de l'End n'est pas envoyee.");
                        context.assertTrue(chunks.stream().noneMatch(chunk -> chunk.dimension().equals(World.OVERWORLD.getValue())),
                                "Un portail qui ramene dans le monde du joueur n'ouvre rien : le client le tient deja.");
                    } finally {
                        for (BackroomsPortalEntity portal : nested) {
                            portal.discard();
                        }
                        toNether.discard();
                        GameTestPlayers.disconnect(player);
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du portail vu a travers un autre, reussi ou non : joueur deconnecte, portails retires. */
    @AfterBatch(batchId = "portal_sync_nested")
    public void afterNested(ServerWorld world) {
        releaseLeftovers();
    }

    /** Index annonce au joueur pour cette dimension, {@code -1} si elle ne l'a pas ete. */
    private static int indexOf(List<RemoteDimensionPayload> announced, Identifier dimension) {
        for (RemoteDimensionPayload payload : announced) {
            if (payload.dimension().equals(dimension)) {
                return payload.index();
            }
        }
        return -1;
    }

    /** {@code true} si ce chunk de cette dimension a ete envoye au joueur. */
    private static boolean chunkSent(List<RemoteChunkPayload> chunks, Identifier dimension, ChunkPos chunk) {
        return chunks.stream().anyMatch(payload -> payload.dimension().equals(dimension) && payload.chunkX() == chunk.x && payload.chunkZ() == chunk.z);
    }

    /** {@code true} si une meteo (pluie ou orage) a ete envoyee sous cet index de dimension. */
    private static boolean weatherUnder(List<RemoteEntityPacketPayload> entities, int index) {
        return entities.stream().anyMatch(payload -> payload.dimension() == index && payload.packet() instanceof GameStateChangeS2CPacket);
    }

    /** Apres le lot des deux dimensions, reussi ou non : joueur deconnecte, portails retires, blocs des arrivees rendus. */
    @AfterBatch(batchId = "portal_sync_two_dimensions")
    public void afterTwoDimensions(ServerWorld world) {
        releaseLeftovers();
        world.getServer().getWorld(World.NETHER).setBlockState(BlockPos.ofFloored(NETHER_EXIT).down(2), Blocks.AIR.getDefaultState());
        world.getServer().getWorld(World.END).setBlockState(BlockPos.ofFloored(END_EXIT).down(2), Blocks.AIR.getDefaultState());
    }

    /** Apres le lot des fissures, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_cracks")
    public void afterCracks(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot du panneau, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_block_entity")
    public void afterBlockEntity(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot du spectateur, reussi ou non : joueurs deconnectes, portail et objet retires. */
    @AfterBatch(batchId = "portal_sync_spectator")
    public void afterSpectator(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 300, batchId = "portal_sync_held")
    public void chunksHeldAfterACrossingOnlyCountForAWhile(TestContext context) {
        prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        Vec3d plane = portal.getPos();
        LongSet held = new LongOpenHashSet();
        long[] crossedAt = new long[1];
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // Les chunks arrivent au fil des passages de synchronisation : il en faut deux, l'un consomme, l'autre perime.
                    for (RemoteChunkPayload chunk : GameTestPlayers.sent(player, RemoteChunkPayload.class)) {
                        held.add(ChunkPos.toLong(chunk.chunkX(), chunk.chunkZ()));
                    }
                    if (held.size() < 2) {
                        throw new GameTestException("Le joueur ne tient pas encore deux chunks du Nether : " + held.size() + ".");
                    }
                })
                .createAndAddReported(() -> {
                    // Le joueur ne tique pas : vanilla ne lui propose aucun chunk, ceux qu'il tient restent a consommer.
                    player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                    context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                    crossedAt[0] = context.getTick();
                    // Un chunk qui changeait a cet instant, pas encore relaye, part avec les autres : au moins un reste tenu.
                    boolean consumed = false;
                    LongIterator keys = held.iterator();
                    while (!consumed && keys.hasNext()) {
                        consumed = PortalViewTracker.consumeHeldChunk(player, new ChunkPos(keys.nextLong()));
                        keys.remove();
                    }
                    context.assertTrue(consumed && !held.isEmpty(), "Juste apres la traversee, un chunk tenu n'est pas renvoye.");
                })
                .createAndAdd(() -> {
                    if (context.getTick() <= crossedAt[0] + PortalViewTracker.HELD_CHUNKS_TTL_TICKS + 1) {
                        throw new GameTestException("Le delai des chunks tenus n'est pas encore passe.");
                    }
                })
                .createAndAddReported(() -> {
                    LongIterator keys = held.iterator();
                    try {
                        context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, new ChunkPos(keys.nextLong())),
                                "Passe le delai, plus rien n'est tenu : vanilla envoie le chunk, que le client a pu oublier.");
                    } finally {
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot des chunks tenus, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_held")
    public void afterHeld(ServerWorld world) {
        releaseLeftovers();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 100, batchId = "portal_sync_held_change")
    public void aHeldChunkThatChangesAroundACrossingIsSentAgain(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity player = viewer(context, GameMode.CREATIVE);
        Vec3d plane = portal.getPos();
        List<ChunkPos> held = new ArrayList<>();
        List<BlockPos> changed = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    for (RemoteChunkPayload chunk : GameTestPlayers.sent(player, RemoteChunkPayload.class)) {
                        ChunkPos pos = new ChunkPos(chunk.chunkX(), chunk.chunkZ());
                        if (!held.contains(pos)) {
                            held.add(pos);
                        }
                    }
                    if (held.size() < 3) {
                        throw new GameTestException("Le joueur ne tient pas encore trois chunks du Nether : " + held.size() + ".");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        // Change pendant la synchronisation, pas encore relaye au moment de la traversee.
                        changed.add(changeSky(nether, held.get(0)));
                        player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                        context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
                        // Change juste apres, avant que vanilla ne propose le chunk au joueur : il ne lui relaierait pas le changement.
                        changed.add(changeSky(nether, held.get(1)));
                        context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, held.get(0)),
                                "Un chunk change avant la traversee, sans relais encore, part avec les autres.");
                        context.assertTrue(!PortalViewTracker.consumeHeldChunk(player, held.get(1)),
                                "Un chunk change apres la traversee n'est plus a jour chez le client : vanilla l'envoie.");
                        // Le Nether neuf bouge encore : au moins un des autres chunks, inchange, reste tenu.
                        boolean kept = false;
                        for (int i = 2; i < held.size() && !kept; i++) {
                            kept = PortalViewTracker.consumeHeldChunk(player, held.get(i));
                        }
                        context.assertTrue(kept, "Un chunk inchange reste tenu.");
                    } finally {
                        for (BlockPos pos : changed) {
                            nether.setBlockState(pos, Blocks.AIR.getDefaultState());
                        }
                        GameTestPlayers.disconnect(player);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = SYNC_PATIENCE_TICKS + 100, batchId = "portal_sync_held_players")
    public void heldChunksBelongToTheirPlayerAndTheirDimension(TestContext context) {
        ServerWorld world = context.getWorld();
        prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        ServerPlayerEntity first = viewer(context, GameMode.CREATIVE);
        ServerPlayerEntity second = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(VIEWER).add(1.0, 0.0, 0.0), 0.0f, 0.0f);
        Vec3d plane = portal.getPos();
        LongSet firstChunks = new LongOpenHashSet();
        LongSet secondChunks = new LongOpenHashSet();
        List<BlockPos> changed = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    for (RemoteChunkPayload chunk : GameTestPlayers.sent(first, RemoteChunkPayload.class)) {
                        firstChunks.add(ChunkPos.toLong(chunk.chunkX(), chunk.chunkZ()));
                    }
                    for (RemoteChunkPayload chunk : GameTestPlayers.sent(second, RemoteChunkPayload.class)) {
                        secondChunks.add(ChunkPos.toLong(chunk.chunkX(), chunk.chunkZ()));
                    }
                    LongSet common = new LongOpenHashSet(firstChunks);
                    common.retainAll(secondChunks);
                    if (common.size() < 3) {
                        throw new GameTestException("Les deux joueurs ne tiennent pas encore trois memes chunks du Nether : " + common.size() + ".");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        first.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                        context.assertTrue(TeleportExecutor.execute(first, portal), "Le premier joueur traverse.");
                        second.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z + 0.05, 0.0f, 0.0f);
                        context.assertTrue(TeleportExecutor.execute(second, portal), "Le second joueur traverse.");
                        LongSet common = new LongOpenHashSet(firstChunks);
                        common.retainAll(secondChunks);
                        // Dans l'overworld, aux memes coordonnees de chunk (le Level 0 et la surface partagent leurs X/Z) :
                        // un changement d'une autre dimension ne dit rien des chunks tenus du Nether.
                        LongIterator keys = common.iterator();
                        while (keys.hasNext()) {
                            ChunkPos chunk = new ChunkPos(keys.nextLong());
                            BlockPos pos = new BlockPos(chunk.getStartX() + 8, world.getTopY() - 2, chunk.getStartZ() + 8);
                            world.setBlockState(pos, Blocks.GLASS.getDefaultState());
                            changed.add(pos);
                        }
                        // Chacun tient les siens : en consommer un chez l'un ne le retire pas a l'autre.
                        boolean shared = false;
                        LongIterator candidates = common.iterator();
                        while (!shared && candidates.hasNext()) {
                            ChunkPos chunk = new ChunkPos(candidates.nextLong());
                            if (PortalViewTracker.consumeHeldChunk(first, chunk)) {
                                context.assertTrue(PortalViewTracker.consumeHeldChunk(second, chunk),
                                        "Chaque joueur tient ses propres chunks : en consommer un chez l'un ne le retire pas a l'autre.");
                                shared = true;
                            }
                        }
                        context.assertTrue(shared, "Un changement de bloc dans une autre dimension ne libere aucun chunk tenu du Nether.");
                    } finally {
                        for (BlockPos pos : changed) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        }
                        GameTestPlayers.disconnect(first);
                        GameTestPlayers.disconnect(second);
                        portal.discard();
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot des chunks tenus par deux joueurs, reussi ou non : joueurs deconnectes, portail retire. */
    @AfterBatch(batchId = "portal_sync_held_players")
    public void afterHeldPlayers(ServerWorld world) {
        releaseLeftovers();
    }

    /** Apres le lot des chunks tenus qui changent, reussi ou non : joueur deconnecte, portail retire. */
    @AfterBatch(batchId = "portal_sync_held_change")
    public void afterHeldChange(ServerWorld world) {
        releaseLeftovers();
    }

    /** Pose du verre dans le ciel d'un chunk du Nether, ou il n'y a que de l'air, et rend sa position. */
    private static BlockPos changeSky(ServerWorld nether, ChunkPos chunk) {
        BlockPos pos = new BlockPos(chunk.getStartX() + 8, 205, chunk.getStartZ() + 8);
        nether.setBlockState(pos, Blocks.GLASS.getDefaultState());
        return pos;
    }

    private static void releaseLeftovers() {
        GameTestPlayers.disconnectAll();
        LEFTOVERS.forEach(Entity::discard);
        LEFTOVERS.clear();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 2200, batchId = "portal_sync_ticket")
    public void approachingAWatchedPortalWidensItsArrivalTicket(TestContext context) {
        ServerWorld nether = prepareNether(context);
        BackroomsPortalEntity portal = netherPortal(context);
        LEFTOVERS.add(portal);
        Vec3d plane = portal.getPos();
        // Douze blocs au nord : le portail est regarde de loin, son arrivee est chargee sur la fenetre synchronisee.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(plane.x, plane.y - 1.0, plane.z - 12.0), 0.0f, 0.0f);
        int farChunkX = ChunkSectionPos.getSectionCoord(NETHER_EXIT.x) + 4;
        int farChunkZ = ChunkSectionPos.getSectionCoord(NETHER_EXIT.z);
        ItemEntity[] watched = new ItemEntity[1];
        boolean[] seen = {false};
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // Ses entities ne tiquent que sous le ticket du chargeur d'arrivee (celui de la generation d'avance ne
                    // fait que charger) : un objet pose alors y reste, sans dechargement ni nouvel identifiant.
                    if (!nether.shouldTickEntity(BlockPos.ofFloored(NETHER_EXIT))) {
                        throw new GameTestException("L'arrivee n'est pas encore tenue par le chargeur.");
                    }
                    // Un objet a l'arrivee : il apparait chez le joueur par le portail, puis est oublie sans paquet a sa deconnexion.
                    watched[0] = new ItemEntity(nether, NETHER_EXIT.x, NETHER_EXIT.y, NETHER_EXIT.z, new ItemStack(Items.QUARTZ));
                    watched[0].setNoGravity(true);
                    nether.spawnEntity(watched[0]);
                    LEFTOVERS.add(watched[0]);
                })
                .createAndAdd(() -> {
                    seen[0] |= GameTestPlayers.sent(player, RemoteEntityPacketPayload.class).stream()
                            .anyMatch(payload -> spawnOf(payload, watched[0].getUuid()));
                    if (!seen[0]) {
                        throw new GameTestException("L'objet de l'arrivee n'est pas encore apparu chez le joueur.");
                    }
                    context.assertTrue(!nether.getChunkManager().isChunkLoaded(farChunkX, farChunkZ),
                            "Regarde de loin, le portail ne tient pas le chunk a quatre chunks de son arrivee.");
                    // A trois blocs, la traversee est imminente : le ticket s'elargit a la distance de vue du serveur.
                    player.refreshPositionAndAngles(plane.x, plane.y - 1.0, plane.z - 3.0, 0.0f, 0.0f);
                    player.networkHandler.syncWithPlayerPosition();
                })
                .createAndAdd(() -> {
                    if (!nether.getChunkManager().isChunkLoaded(farChunkX, farChunkZ)) {
                        throw new GameTestException("Le chunk a quatre chunks de l'arrivee n'est pas encore charge par le ticket elargi.");
                    }
                    // Deconnecte en regardant encore le portail : l'objet suivi est oublie sans paquet.
                    GameTestPlayers.disconnect(player);
                    GameTestServerWorlds.discardEverywhere(nether, watched[0]);
                    portal.discard();
                })
                .completeIfSuccessful();
    }
}
