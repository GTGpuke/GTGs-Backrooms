package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Terrain simplifie d'une section")
class CoarseSectionMesherTest {

    private record Face(int direction, int x, int y, int z, int size, int key) {
    }

    private static List<Face> mesh(int maxSize, CoarseSectionMesher.Blocks blocks) {
        List<Face> faces = new ArrayList<>();
        int emitted = CoarseSectionMesher.mesh(0, 0, 0, maxSize, blocks, (direction, x, y, z, size, key) ->
                faces.add(new Face(direction, x, y, z, size, key)));
        assertEquals(faces.size(), emitted, "Le compte rendu est celui des faces emises.");
        return faces;
    }

    private static List<Face> facing(List<Face> faces, int direction) {
        return faces.stream().filter(face -> face.direction() == direction).toList();
    }

    private static boolean inSection(int x, int y, int z) {
        return x >= 0 && x < 16 && y >= 0 && y < 16 && z >= 0 && z < 16;
    }

    @Test
    @DisplayName("Une section vide n'a aucune face ; pleine dans du plein non plus")
    void emptyOrBuriedSectionsHaveNoFace() {
        assertEquals(0, mesh(4, (x, y, z) -> 0).size(), "Section vide.");
        assertEquals(0, mesh(4, (x, y, z) -> 1).size(), "Section pleine, entouree de plein.");
    }

    @Test
    @DisplayName("Une section pleine dans le vide montre ses six cotes en carres du plus grand cote permis")
    void fullSectionInTheVoidShowsItsSides() {
        List<Face> coarse = mesh(4, (x, y, z) -> inSection(x, y, z) ? 1 : 0);
        assertEquals(6 * 4 * 4, coarse.size(), "Carres de quatre blocs.");
        assertTrue(coarse.stream().allMatch(face -> face.size() == 4 && face.key() == 1), "Cote et etat des carres.");
        assertTrue(coarse.contains(new Face(5, 15, 12, 0, 4, 1)), "Face est : posee sur la derniere tranche, a son premier bloc.");
        assertTrue(coarse.contains(new Face(0, 4, 0, 8, 4, 1)), "Face du dessous : posee sur la premiere tranche.");
        assertEquals(6 * 8 * 8, mesh(2, (x, y, z) -> inSection(x, y, z) ? 1 : 0).size(), "Carres de deux blocs au plus.");
    }

    @Test
    @DisplayName("Un couloir garde sa hauteur exacte : le sol et le plafond restent a leur place")
    void aCorridorKeepsItsHeight() {
        // Sol en y = 0, quatre blocs d'air, plafond en y = 5 : un couloir du Level 0, non aligne sur les carres.
        List<Face> faces = mesh(4, (x, y, z) -> !inSection(x, y, z) ? 0 : y == 0 ? 3 : y == 5 ? 4 : 0);
        List<Face> floorTop = facing(faces, 1).stream().filter(face -> face.key() == 3).toList();
        List<Face> ceilingUnder = facing(faces, 0).stream().filter(face -> face.key() == 4).toList();
        assertEquals(16, floorTop.size(), "Le dessus du sol en seize carres.");
        assertTrue(floorTop.stream().allMatch(face -> face.y() == 0), "Le dessus du sol reste sur sa tranche.");
        assertEquals(16, ceilingUnder.size(), "Le dessous du plafond en seize carres.");
        assertTrue(ceilingUnder.stream().allMatch(face -> face.y() == 5), "Le dessous du plafond reste sur sa tranche.");
    }

    @Test
    @DisplayName("Un mur se fusionne en carres depuis son premier bloc, sans grille imposee")
    void aWallMergesFromItsFirstBlock() {
        // Mur d'un bloc d'epaisseur en x = 5, quatre blocs de haut a partir de y = 1, quatre de large.
        List<Face> faces = mesh(4, (x, y, z) -> x == 5 && y >= 1 && y <= 4 && z >= 0 && z < 4 ? 2 : 0);
        assertEquals(List.of(new Face(4, 5, 1, 0, 4, 2)), facing(faces, 4), "Un seul carre a l'ouest.");
        assertEquals(List.of(new Face(5, 5, 1, 0, 4, 2)), facing(faces, 5), "Un seul carre a l'est.");
        assertEquals(4, facing(faces, 1).size(), "Le dessus, d'un bloc d'epaisseur : quatre faces seules.");
    }

    @Test
    @DisplayName("Un carre ne fusionne que des faces visibles d'un meme etat, puis se rabat sur un carre plus petit")
    void squaresHoldOneStateAndShrinkToFit() {
        // Sol en damier de deux etats : aucune fusion.
        List<Face> checker = facing(mesh(4, (x, y, z) -> inSection(x, y, z) && y == 0 ? 1 + ((x + z) & 1) : 0), 1);
        assertEquals(256, checker.size(), "Une face par bloc.");
        // Sol de trois blocs sur trois : un carre de deux, puis cinq faces seules.
        List<Face> small = facing(mesh(4, (x, y, z) -> x >= 0 && x < 3 && y == 0 && z >= 0 && z < 3 ? 1 : 0), 1);
        assertEquals(6, small.size(), "Un carre de deux et cinq faces.");
        assertEquals(1, small.stream().filter(face -> face.size() == 2).count(), "Un seul carre de deux.");
        // Bande de deux blocs contre le bord de la section : les carres ne debordent pas.
        List<Face> edge = facing(mesh(4, (x, y, z) -> x >= 14 && x < 16 && y == 0 && z >= 0 && z < 16 ? 1 : 0), 1);
        assertEquals(8, edge.size(), "Huit carres de deux le long du bord.");
        List<Face> corner = facing(mesh(2, (x, y, z) -> x >= 0 && x < 16 && y == 0 && z == 15 ? 1 : 0), 1);
        assertEquals(16, corner.size(), "Une rangee contre l'autre bord : une face par bloc.");
        // Deux rangees aux bords opposes du plan : un carre ne passe pas de la fin d'une rangee au debut de la suivante.
        List<Face> opposite = facing(mesh(2, (x, y, z) -> x >= 0 && x < 16 && y == 0 && (z == 0 || z == 15) ? 1 : 0), 1);
        assertEquals(32, opposite.size(), "Deux rangees : une face par bloc, aucun carre.");
    }

    @Test
    @DisplayName("Un bloc impossible a dessiner garde la section au rendu vanilla, mais compte comme vide chez la voisine")
    void unrepresentableBlocksKeepTheSectionVanilla() {
        List<Face> faces = new ArrayList<>();
        int emitted = CoarseSectionMesher.mesh(0, 0, 0, 4, (x, y, z) -> x == 7 && y == 7 && z == 7 ? CoarseSectionMesher.UNREPRESENTABLE : 1,
                (direction, x, y, z, size, key) -> faces.add(new Face(direction, x, y, z, size, key)));
        assertEquals(CoarseSectionMesher.UNREPRESENTABLE, emitted, "Section non simplifiee.");
        assertTrue(faces.isEmpty(), "Aucune face emise.");
        List<Face> neighbour = mesh(4, (x, y, z) -> inSection(x, y, z) ? 1 : x == 16 ? CoarseSectionMesher.UNREPRESENTABLE : 1);
        assertEquals(16, neighbour.size(), "Seul le cote tourne vers la voisine se montre.");
        assertTrue(neighbour.stream().allMatch(face -> face.direction() == 5), "Le cote est.");
    }

    @Test
    @DisplayName("Au bord de la section, le bloc voisin se lit dans la section d'a cote")
    void neighbourSectionsHideFacesAcrossTheBorder() {
        // Une colonne contre le bord est, et du plein juste de l'autre cote : pas de face est.
        CoarseSectionMesher.Blocks blocks = (x, y, z) -> x >= 14 && x < 18 && y >= 0 && y < 16 && z >= 0 && z < 2 ? 1 : 0;
        List<Face> faces = mesh(2, blocks);
        assertEquals(0, facing(faces, 5).size(), "Rien vers l'est : la section voisine est pleine.");
        assertEquals(8, facing(faces, 4).size(), "Un carre ouest par paire de blocs de la colonne.");
    }
}
