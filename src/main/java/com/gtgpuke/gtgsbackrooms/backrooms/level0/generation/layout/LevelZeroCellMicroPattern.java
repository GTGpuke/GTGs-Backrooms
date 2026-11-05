package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout;

/**
 * Masque d'ouverture bloc par bloc a l'interieur d'une cellule 3x3.
 *
 * <p>Un bit a 1 signifie "ouvert". L'ordre est ligne-major : {@code z * 3 + x}.
 */
public final class LevelZeroCellMicroPattern {

    /** Cellule entierement fermee. */
    public static final int FULL_CLOSED = 0;

    /** Cellule entierement ouverte. */
    public static final int FULL_OPEN = 0b111_111_111;

    private LevelZeroCellMicroPattern() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Bit du bloc a la position locale donnee. */
    public static int bit(int subCellX, int subCellZ) {
        return 1 << (subCellZ * 3 + subCellX);
    }

    /** {@code true} si le bloc a la position locale donnee est ouvert. */
    public static boolean isOpen(int pattern, int subCellX, int subCellZ) {
        return (pattern & bit(subCellX, subCellZ)) != 0;
    }
}
