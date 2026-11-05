package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.biome.LevelZeroSurfaceBiome;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroCoords;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.sector.LevelZeroSectorCache;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.sector.LevelZeroSectorData;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.sector.LevelZeroSectorGenerator;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.sector.LevelZeroSectorRoomKind;
import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;

/**
 * Layout logique d'un chunk pour un layer : les cellules evaluees qui le couvrent.
 *
 * <p>Point d'entree de toute la generation logique du Level 0 :
 * secteur (labyrinthe brut, en cache) -&gt; fenetre de traversabilite avec marge
 * -&gt; evaluation des cellules du chunk. Aucun bloc n'est pose ici ; le writer
 * traduit ensuite ce layout en colonnes.
 */
public final class LevelZeroLayout {

    /**
     * Marge de cellules lue autour du chunk. Deux cellules suffisent pour que la
     * topologie, les neons et les gaps soient identiques des deux cotes d'une
     * frontiere de chunk.
     */
    public static final int REGION_MARGIN_CELLS = 2;

    /** Demi-taille, en cellules, de la zone forcee ouverte autour de l'origine (spawn sur). */
    public static final int ORIGIN_SAFE_RADIUS = 2;

    private static final LevelZeroSectorCache SECTORS = new LevelZeroSectorCache(
            new LevelZeroSectorGenerator(LevelZeroSectorGenerator.Settings.LEVEL_ZERO),
            LevelZeroSectorCache.DEFAULT_CAPACITY);
    private static final LevelZeroCellEvaluator EVALUATOR =
            new LevelZeroCellEvaluator(ModConfig.ENABLE_LEVEL_ZERO_NOISE_GEOMETRY);

    private final int chunkX;
    private final int chunkZ;
    private final long layoutSeed;
    private final int layerIndex;
    private final int minCellX;
    private final int minCellZ;
    private final int cellWidth;
    private final LevelZeroCellEvaluation[] cells;
    private final LevelZeroRegionWalkability walkability;

    private LevelZeroLayout(int chunkX, int chunkZ, long layoutSeed, int layerIndex,
                            int minCellX, int minCellZ, int cellWidth,
                            LevelZeroCellEvaluation[] cells, LevelZeroRegionWalkability walkability) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.layoutSeed = layoutSeed;
        this.layerIndex = layerIndex;
        this.minCellX = minCellX;
        this.minCellZ = minCellZ;
        this.cellWidth = cellWidth;
        this.cells = cells;
        this.walkability = walkability;
    }

    /**
     * Genere le layout d'un chunk pour un layer, avec la configuration du mod.
     *
     * @param chunkX coordonnee X du chunk
     * @param chunkZ coordonnee Z du chunk
     * @param layoutSeed seed deterministe du layer
     * @param layerIndex index du layer
     * @return layout du chunk
     */
    public static LevelZeroLayout generate(int chunkX, int chunkZ, long layoutSeed, int layerIndex) {
        return generate(chunkX, chunkZ, layoutSeed, layerIndex, EVALUATOR);
    }

    /**
     * Genere le layout d'un chunk avec un evaluateur explicite (tests, comparaisons avant/apres).
     *
     * @param chunkX coordonnee X du chunk
     * @param chunkZ coordonnee Z du chunk
     * @param layoutSeed seed deterministe du layer
     * @param layerIndex index du layer
     * @param evaluator evaluateur de cellules
     * @return layout du chunk
     */
    @SuppressWarnings("try")
    public static LevelZeroLayout generate(int chunkX, int chunkZ, long layoutSeed, int layerIndex,
                                           LevelZeroCellEvaluator evaluator) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope("level0.layout.generate")) {
            int minCellX = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkStart(chunkX));
            int minCellZ = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkStart(chunkZ));
            int maxCellX = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkEnd(chunkX));
            int maxCellZ = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkEnd(chunkZ));
            SectorLookup sectors = new SectorLookup(layoutSeed);
            LevelZeroRegionWalkability walkability = LevelZeroRegionWalkability.sample(
                    minCellX - REGION_MARGIN_CELLS,
                    minCellZ - REGION_MARGIN_CELLS,
                    maxCellX + REGION_MARGIN_CELLS,
                    maxCellZ + REGION_MARGIN_CELLS,
                    (cellX, cellZ) -> sectors.isWalkable(cellX, cellZ) ? 1 : 0,
                    (cellX, cellZ) -> sectors.roomKind(cellX, cellZ).ordinal());

            int cellWidth = maxCellX - minCellX + 1;
            int cellHeight = maxCellZ - minCellZ + 1;
            LevelZeroCellEvaluation[] cells = new LevelZeroCellEvaluation[cellWidth * cellHeight];
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    cells[(cellZ - minCellZ) * cellWidth + (cellX - minCellX)] = evaluator.evaluate(
                            new LevelZeroCellContext(cellX, cellZ, layoutSeed, layerIndex),
                            walkability);
                }
            }
            return new LevelZeroLayout(chunkX, chunkZ, layoutSeed, layerIndex,
                    minCellX, minCellZ, cellWidth, cells, walkability);
        }
    }

    /** Evaluation de la cellule couvrant une position locale du chunk. */
    public LevelZeroCellEvaluation cellAt(int localX, int localZ) {
        int cellX = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkStart(chunkX) + localX);
        int cellZ = LevelZeroCoords.worldToCell(LevelZeroCoords.chunkStart(chunkZ) + localZ);
        return cells[(cellZ - minCellZ) * cellWidth + (cellX - minCellX)];
    }

    /** {@code true} si la cellule couvrant la position locale est ouverte. */
    public boolean isWalkable(int localX, int localZ) {
        return cellAt(localX, localZ).walkable();
    }

    /** {@code true} si le bloc a la position locale reste ouvert apres micro-geometrie. */
    public boolean isLocallyWalkable(int localX, int localZ) {
        int worldX = LevelZeroCoords.chunkStart(chunkX) + localX;
        int worldZ = LevelZeroCoords.chunkStart(chunkZ) + localZ;
        return cellAt(localX, localZ).isLocallyWalkable(
                LevelZeroCoords.subCell(worldX),
                LevelZeroCoords.subCell(worldZ));
    }

    /** {@code true} si un neon est pose au plafond de ce bloc (centre d'une cellule eclairee). */
    public boolean hasLight(int localX, int localZ) {
        int worldX = LevelZeroCoords.chunkStart(chunkX) + localX;
        int worldZ = LevelZeroCoords.chunkStart(chunkZ) + localZ;
        return cellAt(localX, localZ).lighted()
                && LevelZeroCoords.subCell(worldX) == LevelZeroCoords.CELL_SCALE / 2
                && LevelZeroCoords.subCell(worldZ) == LevelZeroCoords.CELL_SCALE / 2;
    }

    /** Biome cosmetique de la position locale. */
    public LevelZeroSurfaceBiome surfaceBiome(int localX, int localZ) {
        return cellAt(localX, localZ).surfaceBiome();
    }

    /** Fenetre de traversabilite (chunk + marge) utilisee pour ce layout. */
    public LevelZeroRegionWalkability walkability() {
        return walkability;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long layoutSeed() {
        return layoutSeed;
    }

    public int layerIndex() {
        return layerIndex;
    }

    /**
     * Traversabilite brute d'une cellule logique, lue dans le secteur en cache.
     *
     * @param layoutSeed seed du layer
     * @param cellX coordonnee logique X
     * @param cellZ coordonnee logique Z
     * @return {@code true} si la cellule est ouverte
     */
    public static boolean isWalkableCell(long layoutSeed, int cellX, int cellZ) {
        return new SectorLookup(layoutSeed).isWalkable(cellX, cellZ);
    }

    /**
     * Traversabilite brute d'une position monde.
     *
     * @param worldX coordonnee monde X
     * @param worldZ coordonnee monde Z
     * @param layoutSeed seed du layer
     * @return {@code true} si la cellule couvrant la position est ouverte
     */
    public static boolean isWalkableAtWorld(int worldX, int worldZ, long layoutSeed) {
        return isWalkableCell(layoutSeed, LevelZeroCoords.worldToCell(worldX), LevelZeroCoords.worldToCell(worldZ));
    }

    /** Nombre de secteurs actuellement en cache. */
    public static long cachedSectorCount() {
        return SECTORS.size();
    }

    /** Vide le cache de secteurs (arret du serveur). */
    public static void clearCache() {
        SECTORS.clear();
    }

    private static boolean isInOriginSafeZone(int cellX, int cellZ) {
        return cellX >= -ORIGIN_SAFE_RADIUS && cellX <= ORIGIN_SAFE_RADIUS
                && cellZ >= -ORIGIN_SAFE_RADIUS && cellZ <= ORIGIN_SAFE_RADIUS;
    }

    /**
     * Lecture de cellules dans les secteurs d'un layer, avec memo du dernier secteur
     * touche : une fenetre de chunk ne couvre qu'un a quatre secteurs.
     */
    private static final class SectorLookup {

        private final long layoutSeed;
        private final int cols;
        private final int rows;
        private int lastSectorX = Integer.MIN_VALUE;
        private int lastSectorZ = Integer.MIN_VALUE;
        private LevelZeroSectorData lastSector;

        private SectorLookup(long layoutSeed) {
            this.layoutSeed = layoutSeed;
            this.cols = SECTORS.settings().cols();
            this.rows = SECTORS.settings().rows();
        }

        private boolean isWalkable(int cellX, int cellZ) {
            return isInOriginSafeZone(cellX, cellZ)
                    || sector(cellX, cellZ).isWalkable(Math.floorMod(cellX, cols), Math.floorMod(cellZ, rows));
        }

        private LevelZeroSectorRoomKind roomKind(int cellX, int cellZ) {
            return isInOriginSafeZone(cellX, cellZ)
                    ? LevelZeroSectorRoomKind.NONE
                    : sector(cellX, cellZ).roomKind(Math.floorMod(cellX, cols), Math.floorMod(cellZ, rows));
        }

        private LevelZeroSectorData sector(int cellX, int cellZ) {
            int sectorX = Math.floorDiv(cellX, cols);
            int sectorZ = Math.floorDiv(cellZ, rows);
            if (lastSector == null || sectorX != lastSectorX || sectorZ != lastSectorZ) {
                lastSector = SECTORS.sector(layoutSeed, sectorX, sectorZ);
                lastSectorX = sectorX;
                lastSectorZ = sectorZ;
            }
            return lastSector;
        }
    }
}
