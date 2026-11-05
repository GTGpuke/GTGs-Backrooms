package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu des portails sur un vrai serveur : teleportation du joueur et des objets dans le meme monde, saut court
 * envoye aux clients en position absolue, collision avec ce qui est de l'autre cote et avec le bord du cadre, payload de
 * chunk.
 *
 * <p>Les portails sont poses a la main entre deux points de l'overworld, ce qui exerce la transposition, le cooldown
 * et le reseau sans autre dimension.
 */
public final class PortalGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-3;
    /** Ce que le test du saut court laisse derriere lui si une attente expire : retire apres son lot, reussi ou non. */
    private static final List<Entity> SHORT_JUMP_LEFTOVERS = new ArrayList<>();

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void crossingAPortalTeleportsThePlayerTowardsTheExit(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(6, 2, 6)));
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(source)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.EAST)
                .destinationDim(World.OVERWORLD)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
        world.spawnEntity(portal);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, source, 0.0f, 0.0f);
        try {
            // Le joueur vient de franchir le plan (5 cm derriere), les pieds au bas du cadre, et regarde vers le sud.
            player.refreshPositionAndAngles(source.x, source.y - 1.0, source.z + 0.05, 0.0f, 0.0f);
            context.assertTrue(TeleportExecutor.execute(player, portal), "La teleportation a lieu.");

            // 5 cm derriere le portail d'entree = 5 cm devant le portail de sortie, plus la marge de sortie, vers l'est.
            Vec3d expected = destination.add(0.05 + TeleportExecutor.EXIT_OFFSET, -1.0, 0.0);
            context.assertTrue(player.getPos().isInRange(expected, EPSILON), "Le joueur ressort devant le portail de sortie.");
            context.assertTrue(Math.abs(player.getYaw() + 90.0f) < 0.01f, "Le joueur regarde vers l'est, loin du portail de sortie.");
        } finally {
            GameTestPlayers.disconnect(player);
            portal.discard();
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void itemsThrownThroughAPortalComeOutOfTheExit(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(4, 2, 6)));
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(source)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.WEST)
                .destinationDim(World.OVERWORLD)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
        world.spawnEntity(portal);
        // Un diamant lance du cote d'entree, droit vers le plan, sans gravite pour une trajectoire previsible.
        ItemEntity item = new ItemEntity(world, source.x, source.y, source.z - 0.3, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setVelocity(0.0, 0.0, 0.5);
        world.spawnEntity(item);

        // Sortie vers l'ouest et verification rapide : l'objet reste dans la zone degagee du test (les voisins sont a quelques blocs).
        context.runAtTick(6, () -> {
            try {
                context.assertTrue(!item.isRemoved(), "Dans la meme dimension, l'objet reste la meme entity.");
                context.assertTrue(item.getX() < destination.x - TeleportExecutor.EXIT_OFFSET
                        && Math.abs(item.getZ() - destination.z) < 0.5 && Math.abs(item.getY() - destination.y) < 0.5,
                        "L'objet ressort devant le portail de sortie et file vers l'ouest : " + item.getPos() + ".");
                context.assertTrue(item.getVelocity().x < 0.0 && Math.abs(item.getVelocity().z) < 1.0e-6,
                        "La vitesse est transposee vers l'ouest : " + item.getVelocity() + ".");
                context.assertTrue(item.hasPortalCooldown(), "Le cooldown de portail vanilla est arme.");
            } finally {
                item.discard();
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 300, batchId = "portal_short_jump")
    public void aShortJumpThroughAPortalReachesClientsAsAnAbsolutePosition(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        // Moins de huit blocs entre l'entree et l'arrivee : vanilla enverrait un deplacement relatif.
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(4, 2, 6)));
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(source)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.WEST)
                .destinationDim(World.OVERWORLD)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
        world.spawnEntity(portal);
        // Immobile devant le cadre, sans gravite, le temps que le joueur la suive.
        ItemEntity item = new ItemEntity(world, source.x, source.y, source.z - 1.0, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setVelocity(Vec3d.ZERO);
        item.setPickupDelayInfinite();
        world.spawnEntity(item);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(12.5, 1.0, 12.5)), 0.0f, 0.0f);
        SHORT_JUMP_LEFTOVERS.add(portal);
        SHORT_JUMP_LEFTOVERS.add(item);
        boolean[] tracked = {false};
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    // Le faux client acquitte ses chunks et redonne sa position, comme un vrai : vanilla reevalue alors les
                    // entities qu'il suit, dans les chunks qu'il a recus.
                    GameTestPlayers.tick(player);
                    world.getChunkManager().updatePosition(player);
                    // Vanilla groupe les paquets d'apparition d'une entity.
                    for (BundleS2CPacket bundle : GameTestPlayers.sent(player, BundleS2CPacket.class)) {
                        for (Packet<?> packet : bundle.getPackets()) {
                            tracked[0] |= packet instanceof EntitySpawnS2CPacket spawn && spawn.getEntityId() == item.getId();
                        }
                    }
                    context.assertTrue(tracked[0], "Le joueur ne suit pas encore l'objet.");
                })
                .createAndAddReported(() -> {
                    GameTestPlayers.sent(player, EntityPositionS2CPacket.class);
                    item.setVelocity(0.0, 0.0, 0.5);
                })
                .createAndAdd(() -> {
                    GameTestPlayers.tick(player);
                    context.assertTrue(GameTestPlayers.sent(player, EntityPositionS2CPacket.class).stream()
                                    .anyMatch(packet -> packet.getEntityId() == item.getId()),
                            "Le saut par le portail n'est pas encore parti au joueur en position absolue.");
                })
                .createAndAddReported(() -> {
                    context.assertTrue(item.hasPortalCooldown() && item.getX() < destination.x && Math.abs(item.getZ() - destination.z) < 0.5,
                            "L'objet a saute par le portail et file vers l'ouest : " + item.getPos() + ".");
                    // Un marqueur, que vanilla ne suit pas, traverse sans rien a envoyer.
                    MarkerEntity marker = EntityType.MARKER.create(world);
                    marker.refreshPositionAndAngles(source.x, source.y - 1.0, source.z + 0.05, 0.0f, 0.0f);
                    world.spawnEntity(marker);
                    SHORT_JUMP_LEFTOVERS.add(marker);
                    context.assertTrue(TeleportExecutor.execute(marker, portal), "Le marqueur traverse.");
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du saut court, reussi ou non : joueur deconnecte, portail et entities retires. */
    @AfterBatch(batchId = "portal_short_jump")
    public void afterShortJump(ServerWorld world) {
        GameTestPlayers.disconnectAll();
        SHORT_JUMP_LEFTOVERS.forEach(Entity::discard);
        SHORT_JUMP_LEFTOVERS.clear();
    }

    /** Pousse un objet d'un demi-bloc vers le sud depuis un point, et renvoie le Z ou il s'arrete. */
    private static double pushSouth(ServerWorld world, double x, double y, double z) {
        return push(world, x, y, z, 0.5);
    }

    /** Pousse un objet le long de Z depuis un point, et renvoie le Z ou il s'arrete. */
    private static double push(ServerWorld world, double x, double y, double z, double dz) {
        ItemEntity item = new ItemEntity(world, x, y, z, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.move(MovementType.SELF, new Vec3d(0.0, 0.0, dz));
        return item.getZ();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void entitiesBumpIntoWhatIsBeyondThePortal(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d source = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 3)));
        Vec3d destination = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(8, 2, 3)));
        // Nord vers sud : l'arrivee est une translation de l'entree. La pierre a l'est de l'arrivee, cote sortie, revient a
        // l'est du cadre d'entree, derriere son plan : le mur qui encadrerait un passage.
        BlockPos wall = new BlockPos(9, 2, 3);
        context.setBlockState(wall, Blocks.STONE);
        BackroomsPortalEntity portal = GameTestServerWorlds.spawnNorthPortal(world, source, World.OVERWORLD, destination);
        BackroomsPortalEntity lost = null;
        BackroomsPortalEntity twin = null;
        double start = source.z - 0.3;
        double blocked = source.z - 0.125;
        double through = source.z + 0.2;
        // Un joueur devant le plan, engage dans le cadre : le serveur le laisse pourtant a son client.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, new Vec3d(source.x, source.y - 1.0, start), 0.0f, 0.0f);
        try {
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.4, source.y, start) - blocked) < EPSILON,
                    "De biais, l'objet bute au plan contre le mur de l'autre cote.");
            context.assertTrue(Math.abs(pushSouth(world, source.x, source.y, start) - through) < EPSILON,
                    "Au milieu du cadre, rien ne l'arrete.");
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.4, source.y, source.z + 0.1) - (source.z + 0.6)) < EPSILON,
                    "Deja passe derriere le plan, il n'est plus engage.");
            // Sur un bord, le centre hors de l'ouverture : il ne peut pas traverser correctement, le plan fait paroi.
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.6, source.y, start) - blocked) < EPSILON,
                    "Sur le bord, l'objet bute sur le plan, meme sans rien de l'autre cote.");
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.7, source.y, start) - through) < EPSILON,
                    "Tout a cote du cadre, rien ne l'arrete.");
            // Parti de derriere l'ouverture fermee (un demi-bloc derriere le plan) : une paroi de dos l'arreterait.
            context.assertTrue(Math.abs(push(world, source.x + 0.6, source.y, source.z + 0.8, -1.0) - (source.z - 0.2)) < EPSILON,
                    "De dos, sur le bord, rien ne l'arrete non plus.");
            portal.setTeleportable(false);
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.4, source.y, start) - through) < EPSILON,
                    "Portail ferme : l'autre cote n'existe pas.");
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.6, source.y, start) - through) < EPSILON,
                    "Portail ferme : son bord n'arrete rien.");
            portal.setTeleportable(true);
            // Un cadre vers une dimension absente ne change rien ; deux cadres engages ajoutent chacun leur autre cote.
            lost = GameTestServerWorlds.spawnNorthPortal(world, source, GameTestServerWorlds.nowhere(), destination);
            twin = GameTestServerWorlds.spawnNorthPortal(world, source, World.OVERWORLD, destination);
            context.assertTrue(Math.abs(pushSouth(world, source.x + 0.4, source.y, start) - blocked) < EPSILON,
                    "Le mur reste la, vu par les deux cadres.");
            List<VoxelShape> none = List.of();
            Box box = new Box(source.x, source.y, start, source.x + 0.5, source.y + 0.5, source.z + 0.5);
            context.assertTrue(PortalCollision.withOtherSide(player, world, none, box) == none,
                    "Le serveur laisse les joueurs a leur client.");
            ItemEntity item = new ItemEntity(world, source.x, source.y, start, new ItemStack(Items.DIAMOND));
            context.assertTrue(PortalCollision.withOtherSide(item, GameTestServerWorlds.nether(context), none, box) == none,
                    "Les portails d'un autre monde ne comptent pas.");
        } finally {
            GameTestPlayers.disconnect(player);
            context.setBlockState(wall, Blocks.AIR);
            portal.discard();
            if (lost != null) {
                lost.discard();
            }
            if (twin != null) {
                twin.discard();
            }
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void remoteChunkPayloadSurvivesEncoding(TestContext context) {
        ServerWorld world = context.getWorld();
        WorldChunk chunk = world.getWorldChunk(context.getAbsolutePos(BlockPos.ORIGIN));
        RemoteChunkPayload payload = RemoteChunkPayload.of(world.getRegistryKey(), world, chunk);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
        RemoteChunkPayload.CODEC.encode(buf, payload);
        RemoteChunkPayload decoded = RemoteChunkPayload.CODEC.decode(buf);

        context.assertTrue(buf.readableBytes() == 0, "Tout le tampon est consomme.");
        context.assertTrue(decoded.dimension().equals(payload.dimension())
                && decoded.chunkX() == chunk.getPos().x && decoded.chunkZ() == chunk.getPos().z, "Dimension et coordonnees conservees.");
        context.assertTrue(ByteBufUtil.equals(decoded.chunkData().getSectionsDataBuf(), payload.chunkData().getSectionsDataBuf()),
                "Les sections sont identiques octet pour octet.");
        context.assertTrue(decoded.chunkData().getHeightmap().equals(payload.chunkData().getHeightmap()), "Les heightmaps sont identiques.");
        context.assertTrue(decoded.lightData().getInitedBlock().equals(payload.lightData().getInitedBlock())
                && decoded.lightData().getInitedSky().equals(payload.lightData().getInitedSky())
                && decoded.lightData().getSkyNibbles().size() == payload.lightData().getSkyNibbles().size(), "La lumiere est conservee.");
        context.complete();
    }
}
