package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonMap;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Ou sont les neons de chaque monde client (celui du joueur et les mondes distants des portails) : releves a l'arrivee
 * de chaque chunk, section par section (seules celles dont la palette contient le bloc sont parcourues), suivis a chaque
 * changement de bloc, oublies avec la colonne dechargee ou le monde libere. Independant du rendu du terrain : un mod qui
 * construit les sections a sa facon n'y change rien. Tout se passe sur le thread de rendu, ou arrivent les paquets.
 */
public final class NeonIndex {

    private static final int SECTION = 16;
    private static final long[] NONE = new long[0];
    private static final Map<World, NeonMap> WORLDS = new IdentityHashMap<>();
    private static final LongArrayList FOUND = new LongArrayList();
    private static final BlockMatch MATCH = new BlockMatch();

    private NeonIndex() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : les chunks de chaque monde client sont releves a leur arrivee. */
    public static void register() {
        ClientChunkEvents.CHUNK_LOAD.register(NeonIndex::scanChunk);
        ClientChunkEvents.CHUNK_UNLOAD.register(NeonIndex::unloadChunk);
        RemoteWorldRegistry.WORLD_FORGOTTEN.register(NeonIndex::forget);
    }

    /** Un chunk recu (ou remplace) : ses neons sont releves, section par section. */
    static void scanChunk(ClientWorld world, WorldChunk chunk) {
        Block block = neonBlock(world);
        if (block == null) {
            return;
        }
        NeonMap map = WORLDS.computeIfAbsent(world, key -> new NeonMap());
        ChunkPos pos = chunk.getPos();
        ChunkSection[] sections = chunk.getSectionArray();
        MATCH.block = block;
        for (int index = 0; index < sections.length; index++) {
            int sectionY = chunk.sectionIndexToCoord(index);
            ChunkSection section = sections[index];
            FOUND.clear();
            if (section.hasAny(MATCH)) {
                int originX = pos.getStartX();
                int originY = ChunkSectionPos.getBlockCoord(sectionY);
                int originZ = pos.getStartZ();
                for (int y = 0; y < SECTION; y++) {
                    for (int z = 0; z < SECTION; z++) {
                        for (int x = 0; x < SECTION; x++) {
                            if (section.getBlockState(x, y, z).isOf(block)) {
                                FOUND.add(BlockPos.asLong(originX + x, originY + y, originZ + z));
                            }
                        }
                    }
                }
            }
            map.setSection(ChunkSectionPos.asLong(pos.x, sectionY, pos.z), FOUND.isEmpty() ? NONE : FOUND.toLongArray());
        }
    }

    /** Un bloc change dans un monde client : un neon pose entre dans l'index, un neon retire en sort. */
    public static void onBlockChanged(ClientWorld world, BlockPos pos, BlockState state) {
        Block block = neonBlock(world);
        if (block == null) {
            return;
        }
        boolean neon = state.isOf(block);
        NeonMap map = WORLDS.get(world);
        if (map == null) {
            if (!neon) {
                return;
            }
            map = new NeonMap();
            WORLDS.put(world, map);
        }
        map.update(pos.asLong(), neon);
    }

    /** Une colonne dechargee : ses neons sont oublies. */
    static void unloadChunk(ClientWorld world, WorldChunk chunk) {
        NeonMap map = WORLDS.get(world);
        if (map != null) {
            ChunkPos pos = chunk.getPos();
            map.clearColumn(pos.x, pos.z, world.getBottomSectionCoord(), world.getTopSectionCoord());
        }
    }

    /**
     * Les neons les plus proches d'un point, parmi les sections a portee.
     *
     * @param world monde
     * @param x position de la camera
     * @param y position de la camera
     * @param z position de la camera
     * @param reach distance au-dela de laquelle un neon n'est pas propose
     * @param selection liste a remplir, videe d'abord
     */
    public static void collect(World world, double x, double y, double z, double reach, NeonSelection selection) {
        NeonMap map = WORLDS.get(world);
        if (map == null) {
            selection.clear();
            return;
        }
        map.collect(x, y, z, reach, selection);
    }

    /** Monde oublie (libere, ou remplace par vanilla) : ses neons sont oublies, et le monde n'est plus retenu. */
    public static void forget(World world) {
        WORLDS.remove(world);
    }

    /** Fin de partie. */
    public static void clear() {
        WORLDS.clear();
    }

    /** Nombre de mondes clients tenus (diagnostic : un monde oublie n'y est plus). */
    public static int trackedWorlds() {
        return WORLDS.size();
    }

    /** Le bloc des neons d'un monde, ou {@code null} si sa dimension n'en a pas ou si le bloc du profil n'existe pas. */
    private static @Nullable Block neonBlock(World world) {
        LookProfile.Neons neons = LookProfiles.of(world.getRegistryKey()).neons();
        if (!neons.any()) {
            return null;
        }
        Block block = Registries.BLOCK.get(neons.block());
        return block == Blocks.AIR ? null : block;
    }

    /** Le bloc cherche dans la palette d'une section, sans rien allouer par section. */
    private static final class BlockMatch implements Predicate<BlockState> {

        private Block block = Blocks.AIR;

        @Override
        public boolean test(BlockState state) {
            return state.isOf(this.block);
        }
    }
}
