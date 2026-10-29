package com.gtgpuke.gtgsbackrooms.gametest.support;

import java.lang.reflect.Field;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Mondes du serveur de test pour les tests de portails : le Nether tient lieu de dimension distante (generation et
 * forcage de ses chunks, entities passees d'un monde a l'autre), et une dimension inexistante sert de destination
 * perdue.
 *
 * <p>Le serveur de test vanilla ignore les dimensions de datapack : il n'a pas de Level 0. Pour exercer ce qui le
 * cherche par sa cle (cible des blocs portail, portails de retour), un monde existant lui est prete sous cette cle le temps
 * d'un test, par reflexion sur la table des mondes du serveur (noms Yarn de l'environnement de dev).
 */
public final class GameTestServerWorlds {

    /** Rayon, en chunks, des voisins generes d'avance autour d'un chunk force. */
    private static final int FORCED_NEIGHBOURS = 2;
    /**
     * Rayon, en chunks, d'une arrivee generee d'avance : la fenetre synchronisee d'un joueur pres du portail (rayon de
     * prechargement plus deux) tient dedans.
     */
    private static final int ARRIVAL_RADIUS = 5;

    private GameTestServerWorlds() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Nether du serveur de test : la dimension distante des tests de portails. */
    public static ServerWorld nether(TestContext context) {
        ServerWorld nether = context.getWorld().getServer().getWorld(World.NETHER);
        if (nether == null) {
            throw new GameTestException("Le serveur de test n'a pas de Nether.");
        }
        return nether;
    }

    /** Dimension absente du serveur : un portail qui y mene n'a pas d'arrivee. */
    public static RegistryKey<World> nowhere() {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ModConstants.MOD_ID, "nulle_part"));
    }

    /**
     * Genere d'avance les chunks autour d'un point. Le serveur de test tique sans attendre et repart d'un monde neuf :
     * la generation asynchrone, elle, prend ses secondes, et depasserait la patience des tests.
     */
    public static void pregenerate(ServerWorld world, Vec3d center, int radius) {
        int centerX = ChunkSectionPos.getSectionCoord(center.x);
        int centerZ = ChunkSectionPos.getSectionCoord(center.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getChunk(centerX + dx, centerZ + dz);
            }
        }
    }

    /** Genere d'avance les chunks d'une arrivee du Nether (11 x 11 chunks autour d'elle). */
    public static void pregenerateArrival(ServerWorld nether, Vec3d arrival) {
        pregenerate(nether, arrival, ARRIVAL_RADIUS);
    }

    /**
     * Pose un portail d'un bloc sur deux, regardant le nord (on y entre par le nord), vers une arrivee regardant le sud.
     *
     * @param world monde du portail
     * @param center centre du cadre
     * @param destinationDim dimension d'arrivee
     * @param destination centre du cadre d'arrivee
     * @return le portail, ajoute au monde
     */
    public static BackroomsPortalEntity spawnNorthPortal(ServerWorld world, Vec3d center, RegistryKey<World> destinationDim, Vec3d destination) {
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(PortalFacing.NORTH)
                .facingDestination(PortalFacing.SOUTH)
                .destinationDim(destinationDim)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
        world.spawnEntity(portal);
        return portal;
    }

    /**
     * Force (ou libere) le chunk d'un point. Ses voisins sont generes d'avance : un chunk ne suit ses entities
     * qu'entoure de chunks complets, et il ne le fait qu'apres quelques ticks ({@code shouldTickEntity}).
     */
    public static void forceChunk(ServerWorld world, Vec3d at, boolean forced) {
        ChunkPos chunk = new ChunkPos(BlockPos.ofFloored(at));
        if (forced) {
            pregenerate(world, at, FORCED_NEIGHBOURS);
        }
        world.setChunkForced(chunk.x, chunk.z, forced);
    }

    /** Libere tous les chunks forces d'un monde : apres un lot, reussi ou non, rien n'y reste charge pour les suivants. */
    public static void releaseForcedChunks(ServerWorld world) {
        for (long chunk : new LongOpenHashSet(world.getForcedChunks())) {
            world.setChunkForced(ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk), false);
        }
    }

    /**
     * Retire une entity, et dans {@code world} l'instance vivante de meme identifiant : vanilla garde l'identifiant de
     * l'entity qui change de monde, et un chunk decharge puis recharge en fait une autre instance.
     */
    public static void discardEverywhere(ServerWorld world, Entity entity) {
        entity.discard();
        Entity live = world.getEntity(entity.getUuid());
        if (live != null) {
            live.discard();
        }
    }

    /** Inscrit {@code standIn} sous la cle du Level 0 ; a defaire par {@link #removeStandInLevelZero}. */
    public static void registerStandInLevelZero(MinecraftServer server, ServerWorld standIn) {
        worlds(server).put(BackroomsConstants.LEVEL_ZERO_WORLD, standIn);
    }

    /** Retire le monde prete sous la cle du Level 0. */
    public static void removeStandInLevelZero(MinecraftServer server) {
        worlds(server).remove(BackroomsConstants.LEVEL_ZERO_WORLD);
    }

    @SuppressWarnings("unchecked")
    private static Map<RegistryKey<World>, ServerWorld> worlds(MinecraftServer server) {
        try {
            Field field = MinecraftServer.class.getDeclaredField("worlds");
            field.setAccessible(true);
            return (Map<RegistryKey<World>, ServerWorld>) field.get(server);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestException("Table des mondes du serveur inaccessible : " + exception.getMessage() + ".");
        }
    }
}
