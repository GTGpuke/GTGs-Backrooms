package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Pile verticale canonique du Level 0 : cinq layers de dix blocs empiles a partir de Y=0.
 *
 * <p>Un layer se decompose en : sol (offset 0), air jouable (1 a 4), plafond (5)
 * puis bedrock technique (6 a 9) qui le separe du layer suivant. La dimension
 * fait 64 blocs de haut (contrainte moteur : multiple de 16), les 14 derniers Y
 * restent vides.
 */
public final class LevelZeroLayerStackLayout {

    /** Nombre de layers empiles. */
    public static final int LAYER_COUNT = 5;

    /** Hauteur technique de la dimension, multiple de 16 impose par Minecraft. */
    public static final int DIMENSION_HEIGHT = 64;

    /** Y minimal de la dimension. */
    public static final int MIN_Y = 0;

    /** Ecart vertical entre deux layers successifs. */
    public static final int LAYER_PITCH = 10;

    /** Offset du sol visible a l'interieur d'un layer. */
    public static final int FLOOR_OFFSET = 0;

    /** Debut de l'air jouable a l'interieur d'un layer. */
    public static final int AIR_MIN_OFFSET = 1;

    /** Fin de l'air jouable a l'interieur d'un layer. */
    public static final int AIR_MAX_OFFSET = 4;

    /** Offset du plafond a l'interieur d'un layer. */
    public static final int CEILING_OFFSET = 5;

    /** Debut de la bedrock technique entre deux layers. */
    public static final int INTER_LAYER_BEDROCK_MIN_OFFSET = 6;

    /** Fin de la bedrock technique entre deux layers. */
    public static final int INTER_LAYER_BEDROCK_MAX_OFFSET = 9;

    private static final List<LevelZeroVerticalSlice> SLICES = IntStream.range(0, LAYER_COUNT)
            .mapToObj(LevelZeroVerticalSlice::of)
            .toList();

    private LevelZeroLayerStackLayout() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Y de base d'un layer. */
    public static int baseY(int layerIndex) {
        requireLayerIndex(layerIndex);
        return MIN_Y + layerIndex * LAYER_PITCH;
    }

    /** Y du sol d'un layer. */
    public static int floorY(int layerIndex) {
        return baseY(layerIndex) + FLOOR_OFFSET;
    }

    /** Premier Y d'air jouable d'un layer. */
    public static int airMinY(int layerIndex) {
        return baseY(layerIndex) + AIR_MIN_OFFSET;
    }

    /** Dernier Y d'air jouable d'un layer. */
    public static int airMaxY(int layerIndex) {
        return baseY(layerIndex) + AIR_MAX_OFFSET;
    }

    /** Y du plafond d'un layer. */
    public static int ceilingY(int layerIndex) {
        return baseY(layerIndex) + CEILING_OFFSET;
    }

    /** Premier Y de bedrock technique au-dessus du plafond d'un layer. */
    public static int interLayerBedrockMinY(int layerIndex) {
        return baseY(layerIndex) + INTER_LAYER_BEDROCK_MIN_OFFSET;
    }

    /** Dernier Y de bedrock technique au-dessus du plafond d'un layer. */
    public static int interLayerBedrockMaxY(int layerIndex) {
        return baseY(layerIndex) + INTER_LAYER_BEDROCK_MAX_OFFSET;
    }

    /** Hauteur reellement occupee par la pile (50 blocs). */
    public static int worldHeight() {
        return LAYER_COUNT * LAYER_PITCH;
    }

    /** Sommet du heightmap : dernier bloc solide + 1. */
    public static int heightmapTopY() {
        return ceilingY(LAYER_COUNT - 1) + 1;
    }

    /** Slices canoniques, du layer 0 vers le plus haut. */
    public static List<LevelZeroVerticalSlice> slices() {
        return SLICES;
    }

    /** {@code true} si Y est le premier bloc de paroi d'un layer (base des plinthes). */
    public static boolean isCanonicalWallBaseY(int y) {
        return isInsideStack(y) && Math.floorMod(y - MIN_Y, LAYER_PITCH) == AIR_MIN_OFFSET;
    }

    /** {@code true} si Y appartient a la pile (sol, air, plafond ou bedrock d'un layer). */
    public static boolean isInsideStack(int y) {
        return y >= MIN_Y && y < MIN_Y + worldHeight();
    }

    /** Slice contenant un Y de la pile. */
    public static LevelZeroVerticalSlice sliceAtY(int y) {
        if (!isInsideStack(y)) {
            throw new IllegalArgumentException("Le Y " + y + " est hors de la pile canonique.");
        }
        return SLICES.get(Math.floorDiv(y - MIN_Y, LAYER_PITCH));
    }

    private static void requireLayerIndex(int layerIndex) {
        if (layerIndex < 0 || layerIndex >= LAYER_COUNT) {
            throw new IllegalArgumentException(
                    "Le layer doit etre compris entre 0 et " + (LAYER_COUNT - 1) + " : " + layerIndex + '.');
        }
    }
}
