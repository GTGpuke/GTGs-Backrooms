package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkPos;

@DisplayName("Fenetre de chunks distants")
class RemoteChunkWindowTest {

    @Test
    @DisplayName("La fenetre part du centre, grandit anneau par anneau et couvre le carre complet")
    void windowIsOrderedFromTheCenter() {
        LongLinkedOpenHashSet window = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collect(3, -7, 2, window);
        LongArrayList out = new LongArrayList(window);
        assertEquals(25, out.size(), "Rayon 2 : 5x5 chunks.");
        assertEquals(ChunkPos.toLong(3, -7), out.getLong(0), "Le centre vient en premier.");
        for (int i = 1; i <= 8; i++) {
            long key = out.getLong(i);
            int distance = Math.max(Math.abs(ChunkPos.getPackedX(key) - 3), Math.abs(ChunkPos.getPackedZ(key) + 7));
            assertEquals(1, distance, "Les huit suivants sont a distance 1.");
        }
        LongLinkedOpenHashSet single = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collect(0, 0, 0, single);
        assertEquals(1, single.size(), "Rayon 0 : le seul chunk central.");
    }

    @Test
    @DisplayName("Deux fenetres qui se chevauchent se cumulent dans un ensemble ordonne, la premiere en tete")
    void overlappingWindowsMerge() {
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collect(0, 0, 1, out);
        RemoteChunkWindow.collect(1, 0, 1, out);
        assertEquals(12, out.size(), "3x3 + 3x3 avec six chunks communs = douze chunks.");
        assertEquals(ChunkPos.toLong(0, 0), out.firstLong(), "La premiere fenetre garde sa place en tete.");
    }

    /** Fenetre en cone d'un cadre d'un bloc de large centre en (8, 8), qui montre le sud (+Z), vu depuis cette camera. */
    private static LongLinkedOpenHashSet cone(double apexX, double apexZ, int radius, int maxReach) {
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        int count = RemoteChunkWindow.collectCone(apexX, apexZ, 8.0, 8.0, 1.0, 0.0, 0.0, 1.0, 0.5, radius, maxReach, out);
        assertEquals(out.size(), count, "Le compte rendu est celui de la fenetre.");
        return out;
    }

    @Test
    @DisplayName("De loin, la fenetre garde le compte du carre mais s'allonge dans l'axe du cadre, chaque chunk avec ses voisins")
    void farWindowFollowsTheAxisOfTheFrame() {
        // Camera a trente blocs en deca du cadre : le coin de vue ne quitte pas la colonne de chunks du cadre.
        LongLinkedOpenHashSet window = cone(8.0, -22.0, 3, 17);
        assertEquals(49, window.size(), "Autant de chunks que le carre de rayon 3.");
        assertEquals(ChunkPos.toLong(0, 0), window.firstLong(), "Le chunk de l'arrivee vient en premier.");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(window.contains(ChunkPos.toLong(dx, dz)), "Le carre de rayon 1 autour de l'arrivee.");
            }
        }
        assertTrue(window.contains(ChunkPos.toLong(0, 15)), "Quinze chunks dans l'axe, cinq fois le carre.");
        assertTrue(window.contains(ChunkPos.toLong(1, 14)) && window.contains(ChunkPos.toLong(-1, 14)), "Les voisins du dernier chunk du cone.");
        assertFalse(window.contains(ChunkPos.toLong(0, 16)), "Le compte arrete le cone.");
        assertFalse(window.contains(ChunkPos.toLong(1, 15)), "Pas de voisin d'un chunk qui n'a pas tenu.");
        assertFalse(window.contains(ChunkPos.toLong(3, 0)), "Rien sur les cotes, hors du coin de vue.");
        assertFalse(window.contains(ChunkPos.toLong(0, -2)), "Rien en deca du cadre.");
    }

    @Test
    @DisplayName("Le compte ne porte que sur ce que le cone ajoute : les chunks deja voulus par un autre portail sont gratuits")
    void coneCountsOnlyWhatItAdds() {
        LongLinkedOpenHashSet shared = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collect(0, 0, 1, shared);
        int added = RemoteChunkWindow.collectCone(8.0, -22.0, 8.0, 8.0, 1.0, 0.0, 0.0, 1.0, 0.5, 3, 30, shared);
        assertEquals(49, added, "Le compte du carre, ajoute en plus des neuf deja la.");
        assertEquals(9 + 49, shared.size(), "Neuf deja voulus et quarante-neuf ajoutes.");
        assertTrue(shared.contains(ChunkPos.toLong(0, 18)), "Trois chunks de plus dans l'axe : le carre de rayon 1 etait gratuit.");
    }

    @Test
    @DisplayName("Le cone s'arrete a la portee permise, voisins compris")
    void coneStopsAtTheReach() {
        LongLinkedOpenHashSet window = cone(8.0, -22.0, 3, 5);
        assertEquals(19, window.size(), "Neuf, un chunk devant, puis trois par anneau.");
        assertTrue(window.contains(ChunkPos.toLong(0, 5)), "Le dernier voisin, a la portee.");
        assertFalse(window.contains(ChunkPos.toLong(0, 6)), "Au-dela de la portee.");
    }

    @Test
    @DisplayName("De pres d'un cadre large, le coin de vue s'ouvre sur les cotes ; dans l'autre sens, il suit la normale")
    void coneOpensNearAWideFrameAndFollowsTheNormal() {
        LongLinkedOpenHashSet wide = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collectCone(8.0, 4.0, 8.0, 8.0, 1.0, 0.0, 0.0, 1.0, 8.0, 3, 17, wide);
        assertEquals(49, wide.size(), "Le compte du carre.");
        assertTrue(wide.contains(ChunkPos.toLong(2, 1)), "A quatre blocs d'un cadre de seize, le cone deborde sur les cotes.");
        // Cadre qui montre le nord (-Z), largeur selon -X, camera au sud.
        LongLinkedOpenHashSet north = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collectCone(8.0, 38.0, 8.0, 8.0, -1.0, 0.0, 0.0, -1.0, 0.5, 3, 17, north);
        assertTrue(north.contains(ChunkPos.toLong(0, -15)), "Le cone part vers le nord.");
        assertFalse(north.contains(ChunkPos.toLong(0, 3)), "Rien au sud, en deca du cadre.");
        // Cadre retourne (tete en bas) : largeur inversee, meme normale ; ses bords echangent leurs roles, le cone ne change pas.
        LongLinkedOpenHashSet flipped = new LongLinkedOpenHashSet();
        RemoteChunkWindow.collectCone(8.0, -22.0, 8.0, 8.0, -1.0, 0.0, 0.0, 1.0, 0.5, 3, 17, flipped);
        assertEquals(cone(8.0, -22.0, 3, 17), flipped, "Cadre retourne : le meme cone que le cadre droit.");
    }

    @Test
    @DisplayName("Camera a cote du cadre ou derriere : pas de coin de vue ; sans rayon, le seul chunk de l'arrivee")
    void noConeWithoutACameraBeforeTheFrame() {
        assertTrue(cone(40.0, 8.0, 3, 17).isEmpty(), "Camera dans le plan du cadre, a cote de lui.");
        assertTrue(cone(8.0, 30.0, 3, 17).isEmpty(), "Camera du cote de ce que montre le cadre.");
        LongLinkedOpenHashSet single = cone(8.0, -22.0, 0, 17);
        assertEquals(1, single.size(), "Rayon 0 : le chunk de l'arrivee, sans place pour ses voisins.");
    }

    @Test
    @DisplayName("Une fenetre tient dans la carte client tant qu'elle laisse la marge d'un rayon de chaque cote")
    void fitsAroundKeepsAMargin() {
        int limit = RemoteChunkWindow.MAP_RADIUS - 2 * 2;
        assertTrue(RemoteChunkWindow.fitsAround(0, 0, limit, 0, 2), "A la limite exacte.");
        assertFalse(RemoteChunkWindow.fitsAround(0, 0, limit + 1, 0, 2), "Un chunk trop loin.");
        assertTrue(RemoteChunkWindow.fitsAround(10, 10, 10, 10, 2), "Le foyer lui-meme tient toujours.");
        assertEquals(Math.max(2, RemoteChunkWindow.LOAD_DISTANCE) + 3, RemoteChunkWindow.MAP_RADIUS, "Regle vanilla du rayon de carte.");
    }
}
