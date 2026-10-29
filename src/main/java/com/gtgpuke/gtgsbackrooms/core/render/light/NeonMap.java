package com.gtgpuke.gtgsbackrooms.core.render.light;

import java.util.Arrays;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/**
 * Les neons d'un monde, section par section : positions emballees ({@link BlockPos#asLong}) par cle de section
 * ({@link ChunkSectionPos#asLong}). Rempli au chargement de chaque chunk, suivi bloc par bloc, et lu par la camera de
 * chaque vue parmi les sections a portee.
 */
public final class NeonMap {

    private static final int SECTION = 16;
    private static final long[] NONE = new long[0];

    private final Long2ObjectOpenHashMap<long[]> sections = new Long2ObjectOpenHashMap<>();

    /**
     * Les neons d'une section, remplaces.
     *
     * @param sectionKey cle de la section
     * @param positions positions emballees de ses neons ; vide, la section est oubliee
     */
    public void setSection(long sectionKey, long[] positions) {
        if (positions.length == 0) {
            this.sections.remove(sectionKey);
        } else {
            this.sections.put(sectionKey, positions);
        }
    }

    /**
     * Un bloc change : un neon pose entre dans sa section, un neon retire en sort.
     *
     * @param packedPos position emballee du bloc
     * @param neon {@code true} si le bloc est maintenant un neon
     * @return {@code true} si la carte a change
     */
    public boolean update(long packedPos, boolean neon) {
        long key = sectionKeyOf(packedPos);
        long[] positions = this.sections.getOrDefault(key, NONE);
        int index = indexOf(positions, packedPos);
        if (neon == (index >= 0)) {
            return false;
        }
        if (neon) {
            long[] grown = Arrays.copyOf(positions, positions.length + 1);
            grown[positions.length] = packedPos;
            this.sections.put(key, grown);
        } else if (positions.length == 1) {
            this.sections.remove(key);
        } else {
            long[] shrunk = new long[positions.length - 1];
            System.arraycopy(positions, 0, shrunk, 0, index);
            System.arraycopy(positions, index + 1, shrunk, index, positions.length - index - 1);
            this.sections.put(key, shrunk);
        }
        return true;
    }

    /**
     * Une colonne de chunk dechargee : ses sections sont oubliees.
     *
     * @param chunkX colonne
     * @param chunkZ colonne
     * @param bottomSectionY premiere section du monde
     * @param topSectionY section au-dessus de la derniere
     */
    public void clearColumn(int chunkX, int chunkZ, int bottomSectionY, int topSectionY) {
        for (int sectionY = bottomSectionY; sectionY < topSectionY; sectionY++) {
            this.sections.remove(ChunkSectionPos.asLong(chunkX, sectionY, chunkZ));
        }
    }

    /**
     * Les neons les plus proches d'un point, parmi les sections a portee.
     *
     * @param x position de la camera
     * @param y position de la camera
     * @param z position de la camera
     * @param reach distance au-dela de laquelle un neon n'est pas propose
     * @param selection liste a remplir, videe d'abord
     */
    public void collect(double x, double y, double z, double reach, NeonSelection selection) {
        selection.clear();
        if (this.sections.isEmpty()) {
            return;
        }
        int span = (int) Math.ceil(reach / SECTION);
        int cx = (int) Math.floor(x) >> 4;
        int cy = (int) Math.floor(y) >> 4;
        int cz = (int) Math.floor(z) >> 4;
        double squaredReach = reach * reach;
        for (int sx = cx - span; sx <= cx + span; sx++) {
            for (int sy = cy - span; sy <= cy + span; sy++) {
                for (int sz = cz - span; sz <= cz + span; sz++) {
                    long[] positions = this.sections.getOrDefault(ChunkSectionPos.asLong(sx, sy, sz), NONE);
                    for (int i = 0; i < positions.length; i++) {
                        long packed = positions[i];
                        double dx = BlockPos.unpackLongX(packed) + 0.5 - x;
                        double dy = BlockPos.unpackLongY(packed) + 0.5 - y;
                        double dz = BlockPos.unpackLongZ(packed) + 0.5 - z;
                        double squared = dx * dx + dy * dy + dz * dz;
                        if (squared <= squaredReach) {
                            selection.offer(packed, squared);
                        }
                    }
                }
            }
        }
    }

    /** Nombre de sections qui ont au moins un neon. */
    public int sectionCount() {
        return this.sections.size();
    }

    /** Cle de la section d'une position emballee. */
    public static long sectionKeyOf(long packedPos) {
        return ChunkSectionPos.asLong(BlockPos.unpackLongX(packedPos) >> 4, BlockPos.unpackLongY(packedPos) >> 4, BlockPos.unpackLongZ(packedPos) >> 4);
    }

    private static int indexOf(long[] positions, long packedPos) {
        for (int i = 0; i < positions.length; i++) {
            if (positions[i] == packedPos) {
                return i;
            }
        }
        return -1;
    }
}
