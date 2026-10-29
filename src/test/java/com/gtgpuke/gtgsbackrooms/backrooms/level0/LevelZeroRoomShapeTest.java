package com.gtgpuke.gtgsbackrooms.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroLayerStackLayout;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticResult;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityGrid;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityWindow;

@DisplayName("Acoustique du Level 0 tiree de sa generation")
class LevelZeroRoomShapeTest {

    private static final long SEED = 0x5EED_1234_ABCDL;
    private static final int LAYER = 0;
    private static final int Y = LevelZeroLayerStackLayout.airMinY(LAYER);

    @Test
    @DisplayName("Autour d'un point de l'air jouable : un espace de la taille d'une salle du Level 0, le meme a chaque fois")
    void openPointsHaveARoom() {
        LevelZeroRoomShape shape = new LevelZeroRoomShape(SEED);
        // La zone d'apparition, autour de l'origine, est toujours ouverte.
        AcousticResult room = shape.estimate(1, Y, 1);
        assertNotNull(room, "L'origine est dans une salle.");
        assertTrue(room.roomSize() > 1.0f && room.roomSize() < 16.0f, "Libre parcours d'une salle sous un plafond de quatre blocs : " + room.roomSize() + ".");
        assertTrue(room.openness() >= 0.0f && room.openness() <= 1.0f, "Ouverture bornee : " + room.openness() + ".");
        assertEquals(0.0f, room.unknown(), "Rien de perdu : la generation est connue partout.");
        assertTrue(room.reliable(), "Fiable.");
        AcousticResult again = new LevelZeroRoomShape(SEED).estimate(1, Y, 1);
        assertNotNull(again, "Une autre instance.");
        assertEquals(room.roomSize(), again.roomSize(), "Meme graine, meme salle.");
        assertEquals(room.openness(), again.openness(), "Meme ouverture.");
        assertNotNull(shape.estimate(1, LevelZeroLayerStackLayout.airMaxY(LAYER), 1), "Tout l'air jouable du layer compte.");
    }

    @Test
    @DisplayName("Hors de l'air jouable, ou dans un mur, la generation ne dit rien")
    void solidPointsHaveNoRoom() {
        LevelZeroRoomShape shape = new LevelZeroRoomShape(SEED);
        assertNull(shape.estimate(1, LevelZeroLayerStackLayout.floorY(LAYER), 1), "Dans le sol.");
        assertNull(shape.estimate(1, LevelZeroLayerStackLayout.ceilingY(LAYER), 1), "Dans le plafond.");
        assertNull(shape.estimate(1, -10, 1), "Sous la pile.");
        assertNull(shape.estimate(1, 500, 1), "Au-dessus.");
        int[] wall = findWall(shape);
        assertNull(shape.estimate(wall[0], Y, wall[1]), "Dans un mur.");
    }

    @Test
    @DisplayName("La copie du monde s'accorde avec la generation tant qu'aucun bloc n'est pose ni casse ; inconnue, elle s'accorde")
    void copyMatchesGeneration() {
        LevelZeroRoomShape shape = new LevelZeroRoomShape(SEED);
        SolidityGrid grid = new SolidityGrid();
        for (int sectionX = -1; sectionX <= 0; sectionX++) {
            for (int sectionZ = -1; sectionZ <= 0; sectionZ++) {
                byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (!shape.open(sectionX * 16 + x, sectionZ * 16 + z, LAYER)) {
                            blocks[SolidityWindow.blockIndex(x, Y & 15, z)] = OcclusionTrace.FULL;
                        }
                    }
                }
                grid.setSection(ChunkSectionPos.asLong(sectionX, Y >> 4, sectionZ), blocks);
            }
        }
        SolidityWindow window = new SolidityWindow();
        grid.window(0, Y >> 4, 0, window);
        assertTrue(shape.matches(window, 1, Y, 1), "La copie suit la generation.");
        SolidityWindow empty = new SolidityWindow();
        new SolidityGrid().window(0, Y >> 4, 0, empty);
        assertTrue(shape.matches(empty, 1, Y, 1), "Rien de copie : rien ne contredit la generation.");
        assertTrue(shape.matches(window, 1, LevelZeroLayerStackLayout.floorY(LAYER), 1), "Hors de l'air jouable : rien a verifier.");
        byte[] placed = new byte[SolidityWindow.SECTION_BLOCKS];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!shape.open(x, z, LAYER)) {
                    placed[SolidityWindow.blockIndex(x, Y & 15, z)] = OcclusionTrace.FULL;
                }
            }
        }
        placed[SolidityWindow.blockIndex(1, Y & 15, 1)] = OcclusionTrace.FULL;
        grid.setSection(ChunkSectionPos.asLong(0, Y >> 4, 0), placed);
        SolidityWindow modified = new SolidityWindow();
        grid.window(0, Y >> 4, 0, modified);
        assertFalse(shape.matches(modified, 1, Y, 1), "Un bloc pose dans un passage : les rayons reprennent la main.");
        int[] wall = findWall(shape);
        byte[] broken = new byte[SolidityWindow.SECTION_BLOCKS];
        SolidityGrid open = new SolidityGrid();
        int sectionX = wall[0] >> 4;
        int sectionZ = wall[1] >> 4;
        open.setSection(ChunkSectionPos.asLong(sectionX, Y >> 4, sectionZ), broken);
        SolidityWindow airOnly = new SolidityWindow();
        open.window(sectionX, Y >> 4, sectionZ, airOnly);
        assertFalse(shape.matches(airOnly, wall[0], Y, wall[1]), "Un mur casse : la copie ne suit plus la generation.");
    }

    @Test
    @DisplayName("Les layouts lus restent en cache, les plus anciens oublies au-dela de la capacite")
    void layoutsAreCachedAndBounded() {
        LevelZeroRoomShape shape = new LevelZeroRoomShape(SEED);
        boolean first = shape.open(1, 1, LAYER);
        for (int chunk = 0; chunk < 80; chunk++) {
            shape.open(chunk * 16 + 3, 1000, LAYER);
        }
        assertEquals(64, shape.cachedLayouts(), "Quatre-vingt-un chunks lus, soixante-quatre gardes.");
        assertEquals(first, shape.open(1, 1, LAYER), "Oublie puis relu : la meme generation.");
        assertEquals(64, shape.cachedLayouts(), "Un chunk relu en remplace un autre : la borne tient.");
    }

    /** Une colonne de mur pres de l'origine, hors de la zone d'apparition. */
    private static int[] findWall(LevelZeroRoomShape shape) {
        for (int x = 20; x < 200; x++) {
            for (int z = 20; z < 40; z++) {
                if (!shape.open(x, z, LAYER)) {
                    return new int[] {x, z};
                }
            }
        }
        throw new AssertionError("Aucun mur trouve.");
    }
}
