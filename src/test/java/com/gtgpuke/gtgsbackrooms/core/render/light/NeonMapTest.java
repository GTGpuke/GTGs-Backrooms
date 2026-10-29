package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

@DisplayName("Carte des neons d'un monde")
class NeonMapTest {

    @Test
    @DisplayName("Une section se remplit, se remplace et s'oublie")
    void sectionsAreReplaced() {
        NeonMap map = new NeonMap();
        long key = ChunkSectionPos.asLong(1, 2, 3);
        map.setSection(key, new long[] {BlockPos.asLong(16, 32, 48), BlockPos.asLong(20, 40, 50)});
        assertEquals(1, map.sectionCount(), "Une section.");
        map.setSection(key, new long[] {BlockPos.asLong(17, 33, 49)});
        assertEquals(1, map.sectionCount(), "Remplacee, pas ajoutee.");
        NeonSelection selection = new NeonSelection(4);
        map.collect(17.5, 33.5, 49.5, 2.0, selection);
        assertEquals(1, selection.count(), "Le releve en place.");
        assertEquals(BlockPos.asLong(17, 33, 49), selection.position(0), "Le nouveau neon.");
        map.setSection(key, new long[0]);
        assertEquals(0, map.sectionCount(), "Vide : oubliee.");
        map.collect(17.5, 33.5, 49.5, 2.0, selection);
        assertEquals(0, selection.count(), "Plus rien a portee.");
    }

    @Test
    @DisplayName("Un bloc pose ou retire met sa section a jour, sans doublon")
    void blocksAreTracked() {
        NeonMap map = new NeonMap();
        long a = BlockPos.asLong(5, 70, 5);
        long b = BlockPos.asLong(6, 70, 5);
        assertFalse(map.update(a, false), "Retirer ce qui n'y est pas : rien.");
        assertEquals(0, map.sectionCount(), "Aucune section creee pour rien.");
        assertTrue(map.update(a, true), "Pose.");
        assertFalse(map.update(a, true), "Pose deux fois : rien.");
        assertTrue(map.update(b, true), "Un second dans la meme section.");
        assertEquals(1, map.sectionCount(), "Une section.");
        NeonSelection selection = new NeonSelection(4);
        map.collect(5.5, 70.5, 5.5, 3.0, selection);
        assertEquals(2, selection.count(), "Les deux.");
        assertTrue(map.update(a, false), "Retire.");
        map.collect(5.5, 70.5, 5.5, 3.0, selection);
        assertEquals(1, selection.count(), "Le second reste.");
        assertEquals(b, selection.position(0), "Celui qui reste.");
        assertTrue(map.update(b, false), "Le dernier retire.");
        assertEquals(0, map.sectionCount(), "La section vide est oubliee.");
        long c = BlockPos.asLong(8, 70, 8);
        map.update(a, true);
        map.update(b, true);
        map.update(c, true);
        assertTrue(map.update(b, false), "Retrait au milieu.");
        map.collect(5.5, 70.5, 5.5, 8.0, selection);
        assertEquals(2, selection.count(), "Les deux autres.");
        assertEquals(a, selection.position(0), "Le premier, le plus proche.");
        assertEquals(c, selection.position(1), "Le dernier, decale.");
    }

    @Test
    @DisplayName("Une colonne dechargee emporte ses sections, pas celles des voisines")
    void columnsAreCleared() {
        NeonMap map = new NeonMap();
        map.update(BlockPos.asLong(3, 10, 3), true);
        map.update(BlockPos.asLong(3, 200, 3), true);
        map.update(BlockPos.asLong(19, 10, 3), true);
        assertEquals(3, map.sectionCount(), "Trois sections.");
        map.clearColumn(0, 0, -4, 20);
        assertEquals(1, map.sectionCount(), "Celle de la colonne voisine reste.");
        NeonSelection selection = new NeonSelection(4);
        map.collect(19.5, 10.5, 3.5, 1.0, selection);
        assertEquals(1, selection.count(), "Toujours la.");
    }

    @Test
    @DisplayName("La collecte ne propose que les neons a portee, sections voisines comprises")
    void collectsWithinReach() {
        NeonMap map = new NeonMap();
        long near = BlockPos.asLong(15, 64, 15);
        long next = BlockPos.asLong(16, 64, 16);
        long far = BlockPos.asLong(40, 64, 15);
        long tooFar = BlockPos.asLong(19, 64, 19);
        map.update(near, true);
        map.update(next, true);
        map.update(far, true);
        map.update(tooFar, true);
        NeonSelection selection = new NeonSelection(4);
        map.collect(15.5, 64.5, 15.5, 4.0, selection);
        assertEquals(2, selection.count(), "Les deux proches, de part et d'autre d'une limite de section ; pas celui a cinq blocs dans la section voisine.");
        assertEquals(near, selection.position(0), "Le plus proche d'abord.");
        assertEquals(next, selection.position(1), "Puis le voisin.");
        map.collect(-100.0, 64.5, 15.5, 4.0, selection);
        assertEquals(0, selection.count(), "Loin de tout.");
        assertEquals(ChunkSectionPos.asLong(-1, 4, 0), NeonMap.sectionKeyOf(BlockPos.asLong(-1, 64, 0)), "Cle d'une section negative.");
    }
}
