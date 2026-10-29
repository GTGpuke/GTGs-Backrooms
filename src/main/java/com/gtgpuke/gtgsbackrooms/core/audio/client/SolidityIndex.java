package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticCache;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.PortalPlanes;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityGrid;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityWindow;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;

/**
 * La copie du monde que lit le calcul acoustique ({@link SolidityGrid}, une par monde client) et la derniere acoustique
 * connue de ses cellules ({@link AcousticCache}). Dans un monde dont le profil calcule par rayons, les sections autour
 * de la camera sont copiees sur le thread de rendu a raison de quelques-unes par tick (une section coute ses 4096 blocs
 * a lire, chaque etat de bloc distinct n'etant classe qu'une fois), de meme, derriere chaque portail visible vers un
 * tel monde, celles autour de son arrivee (les rayons les traversent) ; une section dont un bloc change est recopiee,
 * une colonne dechargee oubliee, et les sections loin de la camera et des arrivees sont oubliees a intervalle regulier.
 * Une section copiee n'est jamais modifiee en place : le thread de calcul peut la lire sans verrou.
 */
public final class SolidityIndex {

    /** Sections copiees par tick, au plus : une section du Level 0 coute 0,3 a 0,7 ms sur le PC de reference. */
    static final int BUDGET_PER_TICK = 2;
    private static final int SWEEP_PERIOD_TICKS = 40;
    private static final int KEEP_RADIUS = SolidityWindow.RADIUS + 1;
    /** Etats de bloc distincts gardes en cache le temps d'une section : au-dela, un etat est reclasse. */
    private static final int STATE_CACHE = 16;
    private static final String PERF_SNAPSHOT = "client.audio.snapshot";
    private static final Map<World, SolidityGrid> GRIDS = new IdentityHashMap<>();
    private static final Map<World, LongOpenHashSet> DIRTY = new IdentityHashMap<>();
    private static final Map<World, AcousticCache> CACHES = new IdentityHashMap<>();
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();
    private static final BlockState[] CACHED_STATES = new BlockState[STATE_CACHE];
    private static final byte[] CACHED_SOLIDITY = new byte[STATE_CACHE];
    /** Points d'ancrage de la copie du monde du joueur : la camera, puis l'arrivee de chaque portail qui y ramene. */
    private static final int[] ANCHOR_X = new int[1 + PortalPlanes.CAPACITY];
    private static final int[] ANCHOR_Y = new int[1 + PortalPlanes.CAPACITY];
    private static final int[] ANCHOR_Z = new int[1 + PortalPlanes.CAPACITY];
    private static int ticks;
    private static int budget;

    private SolidityIndex() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : une colonne dechargee sort de la copie, un monde oublie aussi. */
    public static void register() {
        ClientChunkEvents.CHUNK_UNLOAD.register(SolidityIndex::unloadChunk);
        RemoteWorldRegistry.WORLD_FORGOTTEN.register(SolidityIndex::forget);
    }

    /** La copie d'un monde, ou {@code null} si rien n'y est copie. */
    public static @Nullable SolidityGrid grid(World world) {
        return GRIDS.get(world);
    }

    /** La derniere acoustique connue des cellules d'un monde (creee a la premiere demande, evenement rare). */
    public static AcousticCache cache(World world) {
        AcousticCache cache = CACHES.get(world);
        if (cache == null) {
            cache = new AcousticCache();
            CACHES.put(world, cache);
        }
        return cache;
    }

    /** Un bloc change dans un monde client : sa section, si elle est copiee, est a recopier. */
    public static void onBlockChanged(ClientWorld world, BlockPos pos) {
        SolidityGrid grid = GRIDS.get(world);
        if (grid == null) {
            return;
        }
        long key = ChunkSectionPos.asLong(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getY()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));
        if (grid.has(key)) {
            LongOpenHashSet dirty = DIRTY.get(world);
            if (dirty == null) {
                dirty = new LongOpenHashSet();
                DIRTY.put(world, dirty);
            }
            dirty.add(key);
        }
    }

    /**
     * Chaque tick : dans un monde calcule par rayons, les sections a portee de la camera manquantes ou a recopier sont
     * copiees, quelques-unes par tick, puis celles autour de l'arrivee des portails visibles vers un tel monde ; de temps
     * en temps, celles loin de la camera et des arrivees sont oubliees.
     */
    @SuppressWarnings("try")
    public static void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ModConfig.ENABLE_AUDIO || world == null) {
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_SNAPSHOT)) {
            budget = BUDGET_PER_TICK;
            int anchors = 0;
            Vec3d listener = client.gameRenderer.getCamera().getPos();
            RegistryKey<World> playerDimension = world.getRegistryKey();
            SolidityGrid grid = null;
            if (AudioProfiles.of(playerDimension).acoustics().computed()) {
                grid = gridFor(world);
                anchor(anchors++, listener);
                copyAround(world, grid, ANCHOR_X[0], ANCHOR_Y[0], ANCHOR_Z[0]);
            }
            List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
            for (int i = 0; i < portals.size() && anchors <= PortalPlanes.CAPACITY; i++) {
                BackroomsPortalEntity portal = portals.get(i);
                RegistryKey<World> destination = portal.destinationDim();
                if (!portal.visible() || !portal.teleportable() || !AudioProfiles.of(destination).acoustics().computed()) {
                    continue;
                }
                ClientWorld arrivalWorld = RemoteWorldRegistry.worldOf(destination);
                if (arrivalWorld == null) {
                    continue;
                }
                Vec3d arrival = portal.destinationPos();
                if (arrivalWorld == world) {
                    // Un portail qui ramene dans ce monde : son arrivee est un point d'ancrage de plus de la meme copie.
                    anchor(anchors, arrival);
                    copyAround(world, grid, ANCHOR_X[anchors], ANCHOR_Y[anchors], ANCHOR_Z[anchors]);
                    anchors++;
                } else {
                    copyAround(arrivalWorld, gridFor(arrivalWorld), ChunkSectionPos.getSectionCoord(arrival.x), ChunkSectionPos.getSectionCoord(arrival.y),
                            ChunkSectionPos.getSectionCoord(arrival.z));
                }
            }
            if (grid != null && ++ticks % SWEEP_PERIOD_TICKS == 0) {
                grid.retainAround(ANCHOR_X, ANCHOR_Y, ANCHOR_Z, anchors, KEEP_RADIUS);
            }
        }
    }

    private static SolidityGrid gridFor(World world) {
        SolidityGrid grid = GRIDS.get(world);
        if (grid == null) {
            grid = new SolidityGrid();
            GRIDS.put(world, grid);
        }
        return grid;
    }

    private static void anchor(int index, Vec3d point) {
        ANCHOR_X[index] = ChunkSectionPos.getSectionCoord(point.x);
        ANCHOR_Y[index] = ChunkSectionPos.getSectionCoord(point.y);
        ANCHOR_Z[index] = ChunkSectionPos.getSectionCoord(point.z);
    }

    /** Copie les sections manquantes ou a recopier autour d'une section, dans la limite du budget du tick. */
    private static void copyAround(ClientWorld world, SolidityGrid grid, int sectionX, int sectionY, int sectionZ) {
        if (budget == 0) {
            return;
        }
        LongOpenHashSet dirty = DIRTY.get(world);
        int bottom = world.getBottomSectionCoord();
        int top = world.getTopSectionCoord();
        for (int dy = -SolidityWindow.RADIUS; dy <= SolidityWindow.RADIUS; dy++) {
            int sy = sectionY + dy;
            if (sy < bottom || sy >= top) {
                continue;
            }
            for (int dz = -SolidityWindow.RADIUS; dz <= SolidityWindow.RADIUS; dz++) {
                for (int dx = -SolidityWindow.RADIUS; dx <= SolidityWindow.RADIUS; dx++) {
                    int sx = sectionX + dx;
                    int sz = sectionZ + dz;
                    long key = ChunkSectionPos.asLong(sx, sy, sz);
                    boolean wanted = dirty != null && dirty.contains(key) || !grid.has(key);
                    if (!wanted) {
                        continue;
                    }
                    WorldChunk chunk = world.getChunkManager().getWorldChunk(sx, sz);
                    if (chunk == null) {
                        continue;
                    }
                    grid.setSection(key, copy(world, chunk, sy));
                    if (dirty != null) {
                        dirty.remove(key);
                    }
                    if (--budget == 0) {
                        return;
                    }
                }
            }
        }
    }

    /** La solidite des blocs d'une section : un tableau neuf (jamais modifie ensuite), ou la section vide partagee. */
    private static byte[] copy(ClientWorld world, WorldChunk chunk, int sectionY) {
        ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
        if (section.isEmpty()) {
            return SolidityGrid.EMPTY;
        }
        // Une allocation par section copiee, au plus deux par tick : la copie est relue par un autre thread, jamais recyclee.
        byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
        ChunkPos pos = chunk.getPos();
        int originX = pos.getStartX();
        int originY = ChunkSectionPos.getBlockCoord(sectionY);
        int originZ = pos.getStartZ();
        int cached = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    int slot = 0;
                    while (slot < cached && CACHED_STATES[slot] != state) {
                        slot++;
                    }
                    if (slot == cached) {
                        // Un etat pas encore vu dans cette section : classe une fois (sa forme ne depend pas de sa place ici).
                        CURSOR.set(originX + x, originY + y, originZ + z);
                        byte solidity = (byte) BlockSolidity.of(state, world, CURSOR);
                        if (cached < STATE_CACHE) {
                            CACHED_STATES[cached] = state;
                            CACHED_SOLIDITY[cached++] = solidity;
                        }
                        blocks[SolidityWindow.blockIndex(x, y, z)] = solidity;
                    } else {
                        blocks[SolidityWindow.blockIndex(x, y, z)] = CACHED_SOLIDITY[slot];
                    }
                }
            }
        }
        for (int i = 0; i < cached; i++) {
            CACHED_STATES[i] = null;
        }
        return blocks;
    }

    static void unloadChunk(ClientWorld world, WorldChunk chunk) {
        SolidityGrid grid = GRIDS.get(world);
        if (grid == null) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        grid.clearColumn(pos.x, pos.z, world.getBottomSectionCoord(), world.getTopSectionCoord());
        LongOpenHashSet dirty = DIRTY.get(world);
        if (dirty != null) {
            for (int sectionY = world.getBottomSectionCoord(); sectionY < world.getTopSectionCoord(); sectionY++) {
                dirty.remove(ChunkSectionPos.asLong(pos.x, sectionY, pos.z));
            }
        }
    }

    /** Monde oublie (libere, ou remplace par vanilla) : sa copie et son cache sont oublies. */
    public static void forget(World world) {
        GRIDS.remove(world);
        DIRTY.remove(world);
        CACHES.remove(world);
    }

    /** Fin de partie. */
    public static void clear() {
        GRIDS.clear();
        DIRTY.clear();
        CACHES.clear();
    }

    /** Nombre de mondes clients tenus, copie ou cache (diagnostic : un monde oublie n'y est plus). */
    public static int trackedWorlds() {
        return Math.max(GRIDS.size(), CACHES.size());
    }
}
