package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroCoords;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.LevelZeroSeedResolver;

@DisplayName("Layout de chunk du Level 0")
class LevelZeroLayoutTest {

    @Test
    @DisplayName("La zone d'origine reste toujours traversable")
    void originIsWalkable() {
        for (int layer = 0; layer < 5; layer++) {
            LevelZeroLayout layout = LevelZeroLayout.generate(0, 0, 12345L, layer);
            assertTrue(layout.isWalkable(0, 0), "L'origine du layer " + layer + " est ouverte.");
            assertTrue(layout.isWalkable(8, 8), "Le centre du chunk d'origine du layer " + layer + " est ouvert.");
            assertTrue(layout.isLocallyWalkable(1, 1), "Le bloc central de la cellule d'origine est ouvert.");
        }
        assertTrue(LevelZeroLayout.isWalkableCell(999L, LevelZeroLayout.ORIGIN_SAFE_RADIUS, -LevelZeroLayout.ORIGIN_SAFE_RADIUS),
                "Le coin de la zone de securite est ouvert.");
    }

    @Test
    @DisplayName("Une cellule logique couvre 3x3 blocs avec la meme evaluation")
    void cellCoversThreeByThreeBlocks() {
        LevelZeroLayout layout = LevelZeroLayout.generate(6, 9, 424242L, 0);
        LevelZeroCellEvaluation reference = layout.cellAt(3, 6);
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                assertEquals(reference, layout.cellAt(3 + dx, 6 + dz), "Meme cellule sur toute la surface 3x3.");
            }
        }
        assertEquals(reference.walkable(), layout.isWalkable(5, 8), "Traversabilite identique dans la cellule.");
        assertEquals(reference.surfaceBiome(), layout.surfaceBiome(4, 7), "Biome identique dans la cellule.");
    }

    @Test
    @DisplayName("Les cellules a cheval sur deux chunks recoivent exactement la meme evaluation")
    void chunkBordersAreSeamless() {
        long seed = 998877L;
        for (int layer = 0; layer < 5; layer++) {
            LevelZeroLayout left = LevelZeroLayout.generate(4, 7, seed, layer);
            LevelZeroLayout right = LevelZeroLayout.generate(5, 7, seed, layer);
            LevelZeroLayout south = LevelZeroLayout.generate(4, 8, seed, layer);
            for (int local = 0; local < LevelZeroCoords.CHUNK_SIZE; local++) {
                assertEquals(left.cellAt(15, local), right.cellAt(0, local),
                        "Topologie, gaps et lumiere identiques de part et d'autre de la frontiere est-ouest.");
                assertEquals(left.cellAt(local, 15), south.cellAt(local, 0),
                        "Topologie, gaps et lumiere identiques de part et d'autre de la frontiere nord-sud.");
            }
        }
    }

    @Test
    @DisplayName("Le layout est deterministe et distingue seed et layer")
    void deterministicAndSeedDependent() {
        // Les seeds de layer sont derivees comme en jeu : deux mondes voisins divergent des le premier layer.
        long seed = LevelZeroSeedResolver.resolveLayerSeed(556677L, 2);
        long otherWorldSeed = LevelZeroSeedResolver.resolveLayerSeed(556678L, 2);
        LevelZeroLayout first = LevelZeroLayout.generate(12, -3, seed, 2);
        LevelZeroLayout second = LevelZeroLayout.generate(12, -3, seed, 2);
        LevelZeroLayout otherSeed = LevelZeroLayout.generate(12, -3, otherWorldSeed, 2);
        int differences = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(first.cellAt(x, z), second.cellAt(x, z), "Meme seed : meme evaluation.");
                differences += first.isWalkable(x, z) != otherSeed.isWalkable(x, z) ? 1 : 0;
            }
        }
        assertTrue(differences > 0, "Une autre seed donne un autre labyrinthe.");
        assertEquals(2, first.layerIndex(), "Index du layer conserve.");
        assertEquals(seed, first.layoutSeed(), "Seed conservee.");
        assertEquals(12, first.chunkX(), "Chunk X conserve.");
        assertEquals(-3, first.chunkZ(), "Chunk Z conserve.");
    }

    @Test
    @DisplayName("Un neon n'est marque qu'au bloc central d'une cellule eclairee")
    void lightOnlyAtCellCenter() {
        boolean foundLight = false;
        for (int chunkX = -3; chunkX <= 3 && !foundLight; chunkX++) {
            for (int chunkZ = -3; chunkZ <= 3 && !foundLight; chunkZ++) {
                LevelZeroLayout layout = LevelZeroLayout.generate(chunkX, chunkZ, 12345L, 0);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = LevelZeroCoords.chunkStart(chunkX) + x;
                        int worldZ = LevelZeroCoords.chunkStart(chunkZ) + z;
                        boolean center = LevelZeroCoords.subCell(worldX) == 1 && LevelZeroCoords.subCell(worldZ) == 1;
                        assertEquals(layout.cellAt(x, z).lighted() && center, layout.hasLight(x, z),
                                "Le neon est au centre de la cellule et seulement la.");
                        foundLight |= layout.hasLight(x, z);
                    }
                }
            }
        }
        assertTrue(foundLight, "La zone de test doit contenir au moins un neon.");
    }

    @Test
    @DisplayName("La fenetre de traversabilite couvre le chunk avec sa marge")
    void walkabilityWindowIncludesMargin() {
        LevelZeroLayout layout = LevelZeroLayout.generate(0, 0, 1L, 0);
        LevelZeroRegionWalkability window = layout.walkability();
        assertEquals(-LevelZeroLayout.REGION_MARGIN_CELLS, window.minCellX(), "Marge a gauche du chunk 0.");
        assertEquals(LevelZeroCoords.worldToCell(15) + LevelZeroLayout.REGION_MARGIN_CELLS, window.maxCellX(), "Marge a droite.");
        assertTrue(window.contains(-2, -2), "La marge est lisible.");
        assertFalse(window.contains(-3, 0), "Au-dela de la marge, rien n'est lisible.");
    }

    @Test
    @DisplayName("La lecture monde correspond a la lecture par cellule")
    void worldLookupMatchesCellLookup() {
        for (int worldX = -40; worldX <= 40; worldX += 7) {
            for (int worldZ = -40; worldZ <= 40; worldZ += 5) {
                assertEquals(LevelZeroLayout.isWalkableCell(4242L, LevelZeroCoords.worldToCell(worldX), LevelZeroCoords.worldToCell(worldZ)),
                        LevelZeroLayout.isWalkableAtWorld(worldX, worldZ, 4242L), "Meme lecture en coordonnees monde.");
            }
        }
        assertTrue(LevelZeroLayout.cachedSectorCount() > 0, "Les secteurs utilises sont en cache.");
        LevelZeroLayout.clearCache();
        assertEquals(0, LevelZeroLayout.cachedSectorCount(), "Le cache est vide apres clearCache.");
    }
}
