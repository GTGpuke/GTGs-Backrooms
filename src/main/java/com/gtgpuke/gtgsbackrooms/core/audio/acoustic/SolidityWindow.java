package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

/**
 * La fenetre de monde qu'un calcul acoustique lit : les sections copiees ({@link SolidityGrid}) autour d'une section
 * centrale, un cube de {@value #SIDE} sections de cote, tenues par reference. Remplie sous le verrou de la grille, lue
 * ensuite sans verrou par le thread de calcul : les blocs d'une section copiee ne changent jamais, la grille remplace
 * la section entiere. Un bloc hors de la fenetre, ou d'une section non copiee, est de l'air pour l'occlusion ; pour les
 * rayons de reverb, en sortir vaut sortir du monde connu ({@link #known}).
 */
public final class SolidityWindow implements OcclusionTrace.Solidity {

    /** Sections de chaque cote de la section centrale. */
    public static final int RADIUS = 3;
    /** Sections de cote de la fenetre. */
    public static final int SIDE = 2 * RADIUS + 1;
    /** Blocs d'une section. */
    public static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_SHIFT = 4;
    private static final int SECTION_MASK = 15;

    private final byte[][] sections = new byte[SIDE * SIDE * SIDE][];
    private int originX;
    private int originY;
    private int originZ;

    /** Place la fenetre autour de cette section ; toutes les sections sont oubliees. */
    public void center(int sectionX, int sectionY, int sectionZ) {
        this.originX = sectionX - RADIUS;
        this.originY = sectionY - RADIUS;
        this.originZ = sectionZ - RADIUS;
        Arrays.fill(this.sections, null);
    }

    /**
     * Pose les blocs d'une section de la fenetre.
     *
     * @param sectionX section
     * @param sectionY section
     * @param sectionZ section
     * @param blocks solidite de ses {@value #SECTION_BLOCKS} blocs ({@link #blockIndex})
     * @return {@code false} si la section est hors de la fenetre
     */
    public boolean set(int sectionX, int sectionY, int sectionZ, byte[] blocks) {
        int rx = sectionX - this.originX;
        int ry = sectionY - this.originY;
        int rz = sectionZ - this.originZ;
        if (!inside(rx) || !inside(ry) || !inside(rz)) {
            return false;
        }
        this.sections[(ry * SIDE + rz) * SIDE + rx] = blocks;
        return true;
    }

    @Override
    public int at(int x, int y, int z) {
        byte[] blocks = section(x, y, z);
        return blocks == null ? OcclusionTrace.AIR : blocks[blockIndex(x, y, z)];
    }

    /** {@code true} si la section de ce bloc est dans la fenetre et copiee : au-dela, le monde n'est pas connu. */
    public boolean known(int x, int y, int z) {
        return section(x, y, z) != null;
    }

    /** Index d'un bloc dans les blocs de sa section. */
    public static int blockIndex(int x, int y, int z) {
        return (y & SECTION_MASK) << 8 | (z & SECTION_MASK) << 4 | x & SECTION_MASK;
    }

    private @Nullable byte[] section(int x, int y, int z) {
        int rx = (x >> SECTION_SHIFT) - this.originX;
        int ry = (y >> SECTION_SHIFT) - this.originY;
        int rz = (z >> SECTION_SHIFT) - this.originZ;
        if (!inside(rx) || !inside(ry) || !inside(rz)) {
            return null;
        }
        return this.sections[(ry * SIDE + rz) * SIDE + rx];
    }

    private static boolean inside(int relative) {
        return relative >= 0 && relative < SIDE;
    }
}
