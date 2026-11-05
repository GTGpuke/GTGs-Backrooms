package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pile verticale du Level 0")
class LevelZeroLayerStackLayoutTest {

    @Test
    @DisplayName("Le layer 0 occupe les Y 0 a 9 avec sol, air, plafond puis bedrock")
    void layerZeroOffsets() {
        assertEquals(0, LevelZeroLayerStackLayout.floorY(0), "Sol du layer 0.");
        assertEquals(1, LevelZeroLayerStackLayout.airMinY(0), "Debut de l'air du layer 0.");
        assertEquals(4, LevelZeroLayerStackLayout.airMaxY(0), "Fin de l'air du layer 0.");
        assertEquals(5, LevelZeroLayerStackLayout.ceilingY(0), "Plafond du layer 0.");
        assertEquals(6, LevelZeroLayerStackLayout.interLayerBedrockMinY(0), "Debut de la bedrock du layer 0.");
        assertEquals(9, LevelZeroLayerStackLayout.interLayerBedrockMaxY(0), "Fin de la bedrock du layer 0.");
    }

    @Test
    @DisplayName("Chaque layer avance de 10 blocs et la pile fait 5 layers sur 50 blocs")
    void stackDimensions() {
        assertEquals(5, LevelZeroLayerStackLayout.LAYER_COUNT, "Cinq layers.");
        assertEquals(30, LevelZeroLayerStackLayout.baseY(3), "Le layer 3 commence en 30.");
        assertEquals(50, LevelZeroLayerStackLayout.worldHeight(), "La pile occupe 50 blocs.");
        assertEquals(46, LevelZeroLayerStackLayout.heightmapTopY(), "Le heightmap s'arrete au-dessus du dernier plafond.");
        assertEquals(64, LevelZeroLayerStackLayout.DIMENSION_HEIGHT, "La dimension fait 64 blocs (multiple de 16).");
        assertTrue(LevelZeroLayerStackLayout.worldHeight() <= LevelZeroLayerStackLayout.DIMENSION_HEIGHT,
                "La pile tient dans la dimension.");
    }

    @Test
    @DisplayName("Les slices sont ordonnees et coherentes avec les helpers")
    void slicesMatchHelpers() {
        List<LevelZeroVerticalSlice> slices = LevelZeroLayerStackLayout.slices();
        assertEquals(5, slices.size(), "Une slice par layer.");
        for (int layer = 0; layer < slices.size(); layer++) {
            LevelZeroVerticalSlice slice = slices.get(layer);
            assertEquals(layer, slice.layerIndex(), "Index du layer.");
            assertEquals(LevelZeroLayerStackLayout.floorY(layer), slice.floorY(), "Sol.");
            assertEquals(LevelZeroLayerStackLayout.airMinY(layer), slice.airMinY(), "Air min.");
            assertEquals(LevelZeroLayerStackLayout.airMaxY(layer), slice.airMaxY(), "Air max.");
            assertEquals(LevelZeroLayerStackLayout.ceilingY(layer), slice.ceilingY(), "Plafond.");
            assertEquals(LevelZeroLayerStackLayout.interLayerBedrockMinY(layer), slice.bedrockMinY(), "Bedrock min.");
            assertEquals(LevelZeroLayerStackLayout.interLayerBedrockMaxY(layer), slice.bedrockMaxY(), "Bedrock max.");
            assertEquals(slice, LevelZeroVerticalSlice.of(layer), "La fabrique produit la meme slice.");
        }
    }

    @Test
    @DisplayName("Un Y se rattache au bon layer et a la bonne zone")
    void yClassification() {
        assertEquals(1, LevelZeroLayerStackLayout.sliceAtY(14).layerIndex(), "Y=14 est dans le layer 1.");
        assertEquals(4, LevelZeroLayerStackLayout.sliceAtY(49).layerIndex(), "Y=49 est dans le layer 4.");
        assertTrue(LevelZeroLayerStackLayout.isCanonicalWallBaseY(21), "Y=21 est la base des murs du layer 2.");
        assertFalse(LevelZeroLayerStackLayout.isCanonicalWallBaseY(22), "Y=22 n'est pas une base de mur.");
        assertTrue(LevelZeroLayerStackLayout.isInsideStack(0), "Y=0 est dans la pile.");
        assertFalse(LevelZeroLayerStackLayout.isInsideStack(50), "Y=50 est au-dessus de la pile.");
        assertFalse(LevelZeroLayerStackLayout.isInsideStack(-1), "Y=-1 est sous la pile.");
    }

    @Test
    @DisplayName("Les index et Y invalides sont rejetes")
    void invalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> LevelZeroLayerStackLayout.baseY(-1), "Layer negatif.");
        assertThrows(IllegalArgumentException.class, () -> LevelZeroLayerStackLayout.baseY(5), "Layer trop haut.");
        assertThrows(IllegalArgumentException.class, () -> LevelZeroLayerStackLayout.sliceAtY(50), "Y hors pile.");
    }

    @Test
    @DisplayName("Hors de la pile ou hors du layer, les classifications de Y repondent non sans aller plus loin")
    void yClassificationOutside() {
        assertFalse(LevelZeroLayerStackLayout.isCanonicalWallBaseY(LevelZeroLayerStackLayout.MIN_Y - 1), "Sous la pile : pas une base de mur.");
    }
}
