package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.BitSet;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.chunk.ChunkManager;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewCone;
import com.gtgpuke.gtgsbackrooms.mixin.client.BuiltChunkStorageAccessor;

/**
 * Choisit, de maniere synchrone, les sections a dessiner dans une vue de portail : parcours en largeur depuis la
 * section de la camera, ou depuis celles du cadre d'arrivee quand la camera est en deca du cadre, limite aux sections qui
 * touchent le frustum et le volume visible a travers le cadre, et dont les chunks voisins sont charges (comme vanilla, une
 * section sans voisin ne se construit pas).
 *
 * <p>Le parcours asynchrone de vanilla ({@code ChunkRenderingDataPreparer}) ne convient pas aux vues : il tient un
 * seul graphe par renderer, le recalcule en arriere-plan quand la camera change de section, et filtre entre-temps
 * l'ancien graphe par le nouveau frustum. Deux vues du meme monde dans une image (deux portails, un portail imbrique)
 * alterneraient donc des listes de sections incompletes : des pans de la vue clignoteraient. Ici chaque vue fait son
 * propre parcours, sans etat partage et sans allocation une fois les tampons dimensionnes. Les sections sortent dans
 * l'ordre du parcours, de la plus proche a la plus lointaine, celui qu'attend le rendu des couches translucides.
 */
final class ViewSectionWalker {

    private static final Direction[] DIRECTIONS = Direction.values();
    /** Demi-section, en blocs : le milieu d'une section, d'ou part le parcours hors des limites du monde, comme vanilla. */
    static final int HALF_SECTION = 8;
    /** Colonnes retenues par le cache des voisins au-dela desquelles il repart de zero (la camera s'est beaucoup deplacee). */
    private static final int NEIGHBOUR_CACHE_LIMIT = 16_384;

    private final BlockPos.Mutable scratch = new BlockPos.Mutable();
    /**
     * Voisins deja verifies, par chunk : numero du parcours, decale d'un bit, et ce bit a un si ses quatre voisins sont
     * charges. Une colonne compte jusqu'a vingt-quatre sections : chaque chunk n'est verifie qu'une fois par parcours.
     */
    private final Long2IntOpenHashMap neighbours = new Long2IntOpenHashMap();
    private int pass;
    private final BitSet visited = new BitSet();
    private int[] queue = new int[0];

    /**
     * Remplit {@code out} avec les sections a dessiner.
     *
     * @param storage grille de sections du renderer de la vue
     * @param chunkManager chunks du monde de la vue
     * @param cameraPos position en blocs de la camera de la vue
     * @param frustum frustum de la vue, deja positionne
     * @param cone volume visible a travers le cadre
     * @param out liste des sections, videe puis remplie
     */
    void discover(BuiltChunkStorage storage, ChunkManager chunkManager, BlockPos cameraPos, Frustum frustum, ViewCone cone,
                  ObjectArrayList<ChunkBuilder.BuiltChunk> out) {
        out.clear();
        if (++this.pass >= Integer.MAX_VALUE >> 1 || this.neighbours.size() > NEIGHBOUR_CACHE_LIMIT) {
            this.neighbours.clear();
            this.pass = 1;
        }
        ChunkBuilder.BuiltChunk[] chunks = storage.chunks;
        if (this.queue.length < chunks.length) {
            this.queue = new int[chunks.length];
        }
        this.visited.clear();
        BuiltChunkStorageAccessor accessor = (BuiltChunkStorageAccessor) storage;
        int tail = 0;
        ChunkBuilder.BuiltChunk start = accessor.backrooms$getRenderedChunk(cameraPos);
        if (cone.startsAtFrame()) {
            tail = enqueueFrameSections(accessor, frustum, cone, chunkManager, tail);
        } else if (start != null && contains(start, cameraPos)) {
            this.visited.set(start.index);
            this.queue[tail++] = start.index;
        } else {
            tail = enqueueBoundaryLayer(storage, accessor, cameraPos, frustum, cone, chunkManager, tail);
        }
        int head = 0;
        while (head < tail) {
            ChunkBuilder.BuiltChunk chunk = chunks[this.queue[head++]];
            out.add(chunk);
            for (Direction direction : DIRECTIONS) {
                BlockPos neighborPos = chunk.getNeighborPosition(direction);
                ChunkBuilder.BuiltChunk neighbor = accessor.backrooms$getRenderedChunk(neighborPos);
                if (neighbor == null || this.visited.get(neighbor.index)) {
                    continue;
                }
                this.visited.set(neighbor.index);
                // Une section d'une autre origine occupe cet emplacement : la grille ne couvre pas la position voulue.
                if (neighbor.getOrigin().equals(neighborPos) && accept(neighbor, frustum, cone, chunkManager)) {
                    this.queue[tail++] = neighbor.index;
                }
            }
        }
    }

    /** Camera au-dessus ou au-dessous du monde : le parcours part de la couche de sections la plus proche, comme vanilla. */
    private int enqueueBoundaryLayer(
            BuiltChunkStorage storage,
            BuiltChunkStorageAccessor accessor,
            BlockPos cameraPos,
            Frustum frustum,
            ViewCone cone,
            ChunkManager chunkManager,
            int tail) {
        HeightLimitView height = storage.getWorld();
        int y = cameraPos.getY() > height.getBottomY() ? height.getTopY() - HALF_SECTION : height.getBottomY() + HALF_SECTION;
        int centerX = ChunkSectionPos.getSectionCoord(cameraPos.getX());
        int centerZ = ChunkSectionPos.getSectionCoord(cameraPos.getZ());
        int radius = storage.getViewDistance();
        int count = tail;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                this.scratch.set(ChunkSectionPos.getOffsetPos(centerX + dx, HALF_SECTION), y, ChunkSectionPos.getOffsetPos(centerZ + dz, HALF_SECTION));
                ChunkBuilder.BuiltChunk chunk = accessor.backrooms$getRenderedChunk(this.scratch);
                if (chunk == null || this.visited.get(chunk.index)) {
                    continue;
                }
                this.visited.set(chunk.index);
                if (contains(chunk, this.scratch) && accept(chunk, frustum, cone, chunkManager)) {
                    this.queue[count++] = chunk.index;
                }
            }
        }
        return count;
    }

    /**
     * Camera en deca du cadre : le parcours part des sections qui touchent le cadre d'arrivee, ou commence le volume visible.
     * Parti de la camera, il n'atteignait le cadre qu'a travers des sections hors du volume, toutes refusees : a vingt blocs
     * du cadre, la vue ne montrait plus que le fond de son ciel.
     */
    private int enqueueFrameSections(BuiltChunkStorageAccessor accessor, Frustum frustum, ViewCone cone, ChunkManager chunkManager, int tail) {
        // Un bloc de marge : un cadre pose sur la limite de deux sections touche les deux.
        int minX = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMin(0)) - 1);
        int minY = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMin(1)) - 1);
        int minZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMin(2)) - 1);
        int maxX = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMax(0)) + 1);
        int maxY = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMax(1)) + 1);
        int maxZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(cone.frameMax(2)) + 1);
        int count = tail;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    this.scratch.set(ChunkSectionPos.getOffsetPos(x, HALF_SECTION), ChunkSectionPos.getOffsetPos(y, HALF_SECTION),
                            ChunkSectionPos.getOffsetPos(z, HALF_SECTION));
                    ChunkBuilder.BuiltChunk chunk = accessor.backrooms$getRenderedChunk(this.scratch);
                    if (chunk == null || this.visited.get(chunk.index)) {
                        continue;
                    }
                    this.visited.set(chunk.index);
                    if (contains(chunk, this.scratch) && accept(chunk, frustum, cone, chunkManager)) {
                        this.queue[count++] = chunk.index;
                    }
                }
            }
        }
        return count;
    }

    private boolean accept(ChunkBuilder.BuiltChunk chunk, Frustum frustum, ViewCone cone, ChunkManager chunkManager) {
        return cone.intersects(chunk.getBoundingBox()) && frustum.isVisible(chunk.getBoundingBox()) && neighboursLoaded(chunk, chunkManager);
    }

    /**
     * {@code true} si les quatre chunks voisins de la section sont charges, comme vanilla l'exige avant de construire une
     * section ; sans l'exemption de vanilla autour de la camera du joueur, qui n'est pas celle de la vue. Une fois par
     * chunk et par parcours.
     */
    private boolean neighboursLoaded(ChunkBuilder.BuiltChunk chunk, ChunkManager chunkManager) {
        int chunkX = ChunkSectionPos.getSectionCoord(chunk.getOrigin().getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(chunk.getOrigin().getZ());
        long key = ChunkPos.toLong(chunkX, chunkZ);
        int known = this.neighbours.get(key);
        if (known >> 1 == this.pass) {
            return (known & 1) != 0;
        }
        boolean loaded = chunkManager.isChunkLoaded(chunkX + 1, chunkZ) && chunkManager.isChunkLoaded(chunkX - 1, chunkZ)
                && chunkManager.isChunkLoaded(chunkX, chunkZ + 1) && chunkManager.isChunkLoaded(chunkX, chunkZ - 1);
        this.neighbours.put(key, this.pass << 1 | (loaded ? 1 : 0));
        return loaded;
    }

    /** {@code true} si la section stockee a cet emplacement est bien celle qui contient la position. */
    private static boolean contains(ChunkBuilder.BuiltChunk chunk, BlockPos pos) {
        BlockPos origin = chunk.getOrigin();
        return (pos.getX() >> 4) == (origin.getX() >> 4) && (pos.getY() >> 4) == (origin.getY() >> 4) && (pos.getZ() >> 4) == (origin.getZ() >> 4);
    }
}
