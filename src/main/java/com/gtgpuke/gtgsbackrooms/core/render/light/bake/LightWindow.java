package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * Une section et ses voisines, a plat : la lecture d'un bloc pendant la cuisson ne coute qu'un acces a un tableau. Une
 * section absente, comme tout ce qui sort de la fenetre, est opaque ({@link LightBlocks#UNKNOWN}) : aucune lumiere n'en
 * vient. Une fenetre appartient au thread de cuisson et se reutilise.
 */
final class LightWindow {

    /** Blocs par cote d'une fenetre ordinaire : trois sections, la section et ses vingt-six voisines. */
    static final int SIZE = 48;
    private static final int SECTION = 16;
    private static final int SECTION_SHIFT = 4;
    private static final int MASK = SECTION - 1;

    /** Sections par cote, en nombre impair : la section du centre et autant de chaque cote. */
    private final int sections;
    /** Blocs par cote. */
    private final int size;
    private final byte[] blocks;
    private int originX;
    private int originY;
    private int originZ;

    /** Une fenetre ordinaire, de trois sections par cote. */
    LightWindow() {
        this(3);
    }

    /** @param sections sections par cote, en nombre impair */
    LightWindow(int sections) {
        this.sections = sections;
        this.size = sections * SECTION;
        this.blocks = new byte[this.size * this.size * this.size];
    }

    /** Recopie les sections autour d'une section, elle au centre. */
    void fill(LightBlocks source, int sectionX, int sectionY, int sectionZ) {
        int half = this.sections / 2;
        this.originX = (sectionX - half) << SECTION_SHIFT;
        this.originY = (sectionY - half) << SECTION_SHIFT;
        this.originZ = (sectionZ - half) << SECTION_SHIFT;
        for (int sz = 0; sz < this.sections; sz++) {
            for (int sy = 0; sy < this.sections; sy++) {
                for (int sx = 0; sx < this.sections; sx++) {
                    copy(source.section(ChunkSectionPos.asLong(sectionX - half + sx, sectionY - half + sy, sectionZ - half + sz)), sx, sy, sz);
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

    private int index(int x, int y, int z) {
        return (z * this.size + y) * this.size + x;
    }

    /** La matiere d'un bloc, en coordonnees du monde ; {@link LightBlocks#UNKNOWN} hors de la fenetre. */
    byte block(int x, int y, int z) {
        int wx = x - this.originX;
        int wy = y - this.originY;
        int wz = z - this.originZ;
        if ((wx | wy | wz) < 0 || wx >= this.size || wy >= this.size || wz >= this.size) {
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
