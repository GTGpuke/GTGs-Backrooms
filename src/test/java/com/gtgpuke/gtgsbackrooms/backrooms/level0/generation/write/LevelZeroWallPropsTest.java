package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.block.LevelZeroWallpaperBlock;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroVerticalSlice;

@DisplayName("Details muraux du Level 0")
class LevelZeroWallPropsTest {

    private static final LevelZeroVerticalSlice SLICE = LevelZeroVerticalSlice.of(2);

    @Test
    @DisplayName("Une colonne sans face exposee n'a aucun detail")
    void noExposureNoProps() {
        assertEquals(LevelZeroWallProps.NONE, LevelZeroWallProps.resolve(12, -7, 0, SLICE), "Rien sans face exposee.");
        assertEquals(0, LevelZeroWallProps.NONE.baseboardFaceMask(), "Pas de plinthe.");
        assertFalse(LevelZeroWallProps.NONE.hasFixture(), "Pas d'element mural.");
    }

    @Test
    @DisplayName("Toute face exposee porte une plinthe et le resultat est deterministe")
    void baseboardOnEveryExposedFace() {
        for (int x = 0; x < 128; x++) {
            int mask = LevelZeroWallpaperBlock.NORTH_MASK | (x % 2 == 0 ? LevelZeroWallpaperBlock.EAST_MASK : 0);
            LevelZeroWallProps props = LevelZeroWallProps.resolve(x, 3, mask, SLICE);
            assertEquals(mask, props.baseboardFaceMask(), "La plinthe suit exactement les faces exposees.");
            assertNotEquals(0, props.baseboardFaceMask(), "Une plinthe est presente.");
            assertEquals(props, LevelZeroWallProps.resolve(x, 3, mask, SLICE), "Resolution deterministe.");
        }
    }

    @Test
    @DisplayName("Un element mural pointe toujours vers une face visible, a la bonne hauteur")
    void fixturesStayOnVisibleFaces() {
        int switches = 0;
        int outlets = 0;
        int mask = LevelZeroWallpaperBlock.NORTH_MASK | LevelZeroWallpaperBlock.EAST_MASK;
        for (int i = 0; i < 4096; i++) {
            LevelZeroWallProps props = LevelZeroWallProps.resolve(i, -i, mask, SLICE);
            if (!props.hasFixture()) {
                assertEquals(0, props.fixtureFaceMask(), "Sans element, pas de face d'element.");
                continue;
            }
            assertTrue(props.fixtureFaceMask() == LevelZeroWallpaperBlock.NORTH_MASK
                    || props.fixtureFaceMask() == LevelZeroWallpaperBlock.EAST_MASK, "Face visible uniquement.");
            if (props.fixture() == LevelZeroWallProps.Fixture.SWITCH) {
                switches++;
                assertEquals(SLICE.floorY() + 2, props.fixtureY(), "Un interrupteur est a deux blocs du sol.");
            } else {
                outlets++;
                assertEquals(SLICE.floorY() + 1, props.fixtureY(), "Une prise est a un bloc du sol.");
            }
        }
        assertTrue(switches > 0 && outlets > 0, "Les deux elements apparaissent : " + switches + " / " + outlets);
        assertTrue(switches + outlets < 4096 / 50, "Les elements restent rares.");
    }

    @Test
    @DisplayName("Le choix de face parcourt toutes les faces exposees")
    void facePickCoversAllFaces() {
        int seenFaces = 0;
        for (int i = 0; i < 8192 && seenFaces != LevelZeroWallpaperBlock.FULL_MASK; i++) {
            LevelZeroWallProps props = LevelZeroWallProps.resolve(i * 3, i * 7, LevelZeroWallpaperBlock.FULL_MASK, SLICE);
            if (props.hasFixture()) {
                seenFaces |= props.fixtureFaceMask();
            }
        }
        assertEquals(LevelZeroWallpaperBlock.FULL_MASK, seenFaces, "Chaque face doit pouvoir porter un element.");
    }

    @Test
    @DisplayName("La face visible choisie est toujours une face du masque, et aucune sans masque")
    void visibleFacePick() {
        assertEquals(0, LevelZeroWallProps.pickVisibleFace(0, 123456789L), "Sans face exposee, pas de face choisie.");
        int mask = LevelZeroWallpaperBlock.NORTH_MASK | LevelZeroWallpaperBlock.EAST_MASK;
        boolean north = false;
        boolean east = false;
        for (long hash = 0; hash < 16; hash++) {
            int face = LevelZeroWallProps.pickVisibleFace(mask, hash);
            assertTrue(face == LevelZeroWallpaperBlock.NORTH_MASK || face == LevelZeroWallpaperBlock.EAST_MASK, "Face du masque.");
            north |= face == LevelZeroWallpaperBlock.NORTH_MASK;
            east |= face == LevelZeroWallpaperBlock.EAST_MASK;
        }
        assertTrue(north && east, "Les deux faces sont choisies selon le hash.");
    }

    @Test
    @DisplayName("Un element mural sans face n'existe pas")
    void fixtureWithoutFaceIsNoFixture() {
        LevelZeroWallProps props = new LevelZeroWallProps(LevelZeroWallpaperBlock.NORTH_MASK, LevelZeroWallProps.Fixture.SWITCH, 0, 3);
        assertFalse(props.hasFixture(), "Sans face, pas d'element mural.");
        assertNotEquals(0, props.baseboardFaceMask(), "La plinthe, elle, est la.");
    }
}
