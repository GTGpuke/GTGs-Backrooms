package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * Une section et ses vingt-six voisines, a plat : la lecture d'un bloc pendant la cuisson ne coute qu'un acces a un
 * tableau. Une section absente, comme tout ce qui sort de la fenetre, est opaque ({@link LightBlocks#UNKNOWN}) : aucune
 * lumiere n'en vient. Une fenetre appartient au thread de cuisson et se reutilise.
 */
final class LightWindow {

    /** Blocs par cote : trois sections. */
    static final int SIZE = 48;
    private static final int SECTION = 16;
    private static final int SECTION_SHIFT = 4;
    private static final int MASK = SECTION - 1;

    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];
    private int originX;
    private int originY;
    private int originZ;

    /** Recopie les vingt-sept sections autour d'une section. */
    void fill(LightBlocks source, int sectionX, int sectionY, int sectionZ) {
        this.originX = (sectionX - 1) << SECTION_SHIFT;
        this.originY = (sectionY - 1) << SECTION_SHIFT;
        this.originZ = (sectionZ - 1) << SECTION_SHIFT;
        for (int sz = 0; sz < 3; sz++) {
            for (int sy = 0; sy < 3; sy++) {
                for (int sx = 0; sx < 3; sx++) {
                    copy(source.section(ChunkSectionPos.asLong(sectionX - 1 + sx, sectionY - 1 + sy, sectionZ - 1 + sz)), sx, sy, sz);
                }
            }
        }
    }

    private void copy(byte[] section, int sx, int sy, int sz) {
        for (int z = 0; z < SECTION; z++) {
            for (int y = 0; y < SECTION; y++) {
                int to = index(sx << SECTION_SHIFT, (sy << SECTION_SHIFT) + y, (sz << SECTION_SHIFT) + z);
                if (section == null) {
                    Arrays.fill(this.blocks, to, to + SECTION, LightBlocks.UNKNOWN);
                } else {
                    System.arraycopy(section, sectionIndex(0, y, z), this.blocks, to, SECTION);
                }
            }
        }
    }

    /** Indice d'un bloc dans une section copiee : X varie le plus vite, puis Y, puis Z. */
    static int sectionIndex(int x, int y, int z) {
        return (z & MASK) << 8 | (y & MASK) << SECTION_SHIFT | x & MASK;
    }

    private static int index(int x, int y, int z) {
        return (z * SIZE + y) * SIZE + x;
    }

    /** La matiere d'un bloc, en coordonnees du monde ; {@link LightBlocks#UNKNOWN} hors de la fenetre. */
    byte block(int x, int y, int z) {
        int wx = x - this.originX;
        int wy = y - this.originY;
        int wz = z - this.originZ;
        if ((wx | wy | wz) < 0 || wx >= SIZE || wy >= SIZE || wz >= SIZE) {
            return LightBlocks.UNKNOWN;
        }
        return this.blocks[index(wx, wy, wz)];
    }

    /** X du premier bloc de la fenetre. */
    int originX() {
        return this.originX;
    }

    /** Y du premier bloc de la fenetre. */
    int originY() {
        return this.originY;
    }

    /** Z du premier bloc de la fenetre. */
    int originZ() {
        return this.originZ;
    }
}
