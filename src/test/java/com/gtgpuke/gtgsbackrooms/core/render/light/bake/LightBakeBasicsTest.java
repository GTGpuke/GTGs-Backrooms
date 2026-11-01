package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

@DisplayName("Cuisson de la lumiere : stockage, copie du monde, rectangle, rayons")
class LightBakeBasicsTest {

    private static final float STEP = LightCell.MAX / 1023.0f;
    private static final int DOWN = 3;
    private static final float REACH = 14.0f;

    @Test
    @DisplayName("Un eclairement se relit a un pas pres, plafonne, jamais negatif, et marque la surface")
    void packedLight() {
        int packed = LightCell.pack(1.0f, 2.0f, 3.0f);
        assertEquals(1.0f, LightCell.red(packed), STEP, "Rouge.");
        assertEquals(2.0f, LightCell.green(packed), STEP, "Vert.");
        assertEquals(3.0f, LightCell.blue(packed), STEP, "Bleu.");
        assertTrue(LightCell.valid(packed), "Une surface.");
        assertTrue(LightCell.valid(LightCell.pack(0.0f, 0.0f, 0.0f)), "Une surface noire reste une surface.");
        assertFalse(LightCell.valid(0), "Pas de surface.");
        assertEquals(LightCell.MAX, LightCell.red(LightCell.pack(9.0f, 0.0f, 0.0f)), 0.0f, "Plafonne.");
        assertEquals(0.0f, LightCell.blue(LightCell.pack(0.0f, 0.0f, -1.0f)), 0.0f, "Jamais negatif.");
    }

    @Test
    @DisplayName("Une valeur par cellule et par face, X le plus vite, puis Y, puis Z, et chaque face a son opposee")
    void cellLayout() {
        assertEquals(0, LightCell.index(0, 0, 0, 0), "Premiere valeur.");
        assertEquals(1, LightCell.index(1, 0, 0, 0), "X le plus vite.");
        assertEquals(16, LightCell.index(0, 1, 0, 0), "Puis Y.");
        assertEquals(256, LightCell.index(0, 0, 1, 0), "Puis Z.");
        assertEquals(LightCell.SECTION_CELLS, LightCell.index(0, 0, 0, 1), "Une tranche par face.");
        assertEquals(LightCell.VALUES - 1, LightCell.index(15, 15, 15, 5), "Derniere valeur.");
        for (int face = 0; face < LightCell.FACES; face++) {
            int opposite = LightCell.opposite(face);
            assertEquals(-LightCell.NORMAL_X[face], LightCell.NORMAL_X[opposite], "Opposee sur X.");
            assertEquals(-LightCell.NORMAL_Y[face], LightCell.NORMAL_Y[opposite], "Opposee sur Y.");
            assertEquals(-LightCell.NORMAL_Z[face], LightCell.NORMAL_Z[opposite], "Opposee sur Z.");
        }
    }

    @Test
    @DisplayName("La copie garde ses sections et ses matieres, sans depasser la palette")
    void blocksCopy() {
        LightBlocks blocks = new LightBlocks();
        long key = ChunkSectionPos.asLong(1, 2, 3);
        assertNull(blocks.section(key), "Rien de copie.");
        blocks.setSection(key, LightBlocks.EMPTY);
        assertSame(LightBlocks.EMPTY, blocks.section(key), "Section posee.");
        assertEquals(1, blocks.sectionCount(), "Une section.");
        blocks.removeSection(key);
        assertEquals(0, blocks.sectionCount(), "Oubliee.");
        byte wall = blocks.addMaterial(0.25f, 0.5f, 0.75f, false);
        byte neon = blocks.addMaterial(1.0f, 1.0f, 1.0f, true);
        assertEquals(2, wall, "Apres l'air et l'inconnu.");
        assertEquals(0.5f, blocks.albedos()[wall * 3 + 1], 0.0f, "Albedo.");
        assertFalse(blocks.emissive()[wall], "Le mur n'emet pas.");
        assertTrue(blocks.emissive()[neon], "Le neon emet.");
        assertFalse(blocks.emissive()[LightBlocks.AIR], "L'air non plus.");
        assertEquals(LightBlocks.UNKNOWN, LightBlocks.UNLOADED[100], "Une colonne absente est opaque.");
        for (int i = 4; i < 256; i++) {
            blocks.addMaterial(0.0f, 0.0f, 0.0f, false);
        }
        assertEquals(LightBlocks.UNKNOWN, blocks.addMaterial(0.0f, 0.0f, 0.0f, false), "Palette pleine : l'inconnu.");
    }

    @Test
    @DisplayName("Seules les sections proches restent copiees, sur chacun des trois axes")
    void retainAround() {
        LightBlocks blocks = new LightBlocks();
        long kept = ChunkSectionPos.asLong(0, 0, 0);
        long[] far = {ChunkSectionPos.asLong(3, 0, 0), ChunkSectionPos.asLong(-3, 0, 0), ChunkSectionPos.asLong(0, 3, 0), ChunkSectionPos.asLong(0, 0, 3)};
        blocks.setSection(kept, LightBlocks.EMPTY);
        blocks.setSection(ChunkSectionPos.asLong(2, 2, 2), LightBlocks.EMPTY);
        for (long key : far) {
            blocks.setSection(key, LightBlocks.EMPTY);
        }
        blocks.retainAround(0, 0, 0, 2);
        assertEquals(2, blocks.sectionCount(), "Le rayon est compris, au-dela tout part.");
        assertSame(LightBlocks.EMPTY, blocks.section(kept), "La section du centre reste.");
    }

    @Test
    @DisplayName("La fenetre lit vingt-sept sections a plat ; une section absente, comme le dehors, est opaque")
    void windowReadsNeighbors() {
        LightBlocks blocks = new LightBlocks();
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        section[LightWindow.sectionIndex(3, 4, 5)] = 7;
        blocks.setSection(ChunkSectionPos.asLong(1, 0, 0), section);
        LightWindow window = new LightWindow();
        window.fill(blocks, 0, 0, 0);
        assertEquals(7, window.block(16 + 3, 4, 5), "Le bloc de la voisine, en coordonnees du monde.");
        assertEquals(LightBlocks.AIR, window.block(16 + 4, 4, 5), "Son voisin.");
        assertEquals(LightBlocks.UNKNOWN, window.block(0, 0, 0), "Section absente.");
        assertEquals(-16, window.originX(), "Origine X.");
        assertEquals(-16, window.originY(), "Origine Y.");
        assertEquals(-16, window.originZ(), "Origine Z.");
        for (int[] outside : new int[][] {{-17, 0, 0}, {32, 0, 0}, {0, -17, 0}, {0, 32, 0}, {0, 0, -17}, {0, 0, 32}}) {
            assertEquals(LightBlocks.UNKNOWN, window.block(outside[0], outside[1], outside[2]), "Hors de la fenetre : " + Arrays.toString(outside) + ".");
        }
    }

    @Test
    @DisplayName("Un bloc se lit a sa place dans sa section, en coordonnees negatives aussi ; une section absente est opaque")
    void blockLookup() {
        LightBlocks blocks = new LightBlocks();
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        section[LightBlocks.index(-1, -2, -3)] = 7;
        blocks.setSection(ChunkSectionPos.asLong(-1, -1, -1), section);
        assertEquals(7, blocks.block(-1, -2, -3), "A sa place.");
        assertEquals(LightBlocks.AIR, blocks.block(-1, -2, -4), "A cote, de l'air.");
        assertEquals(LightBlocks.UNKNOWN, blocks.block(0, 0, 0), "Section absente.");
        assertEquals(15 << 8 | 14 << 4 | 15, LightBlocks.index(-1, -2, -1), "Rang : Z, puis Y, puis X.");
    }

    @Test
    @DisplayName("La lumiere d'une dalle s'eteint sans cassure : entiere pres d'elle, nulle a sa portee, et jamais negative au-dela")
    void lightFalloff() {
        float reach2 = 14.0f * 14.0f;
        assertEquals(1.0f, AreaLight.falloff(0.0f, reach2), 1.0e-6f, "Entiere au depart.");
        assertEquals(0.0f, AreaLight.falloff(reach2, reach2), 1.0e-6f, "Nulle a sa portee.");
        assertEquals(0.0f, AreaLight.falloff(reach2 * 2.0f, reach2), 1.0e-6f, "Nulle au-dela.");
        float last = 1.0f;
        for (float distance = 0.5f; distance <= 14.0f; distance += 0.5f) {
            float value = AreaLight.falloff(distance * distance, reach2);
            assertTrue(value <= last, "Toujours en baisse, a " + distance + ".");
            assertTrue(last - value < 0.12f, "Sans marche, a " + distance + " : " + last + " puis " + value + ".");
            last = value;
        }
    }

    @Test
    @DisplayName("Une dalle eclaire sous elle : entiere dans son aplomb, moins de biais, jamais au-dessus de son plan")
    void lightGoesDownOnly() {
        float[] rects = new float[AreaLight.VALUES];
        AreaLight.blockFace(rects, 0, 0, 10, 0, DOWN, 1.0f);
        float below = AreaLight.received(rects, 0, DOWN, 0.5f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, 0.0f);
        assertEquals(AreaLight.falloff(25.0f, REACH * REACH), below, 1.0e-5f, "Droit dessous : tout ce que permet la distance.");
        assertEquals(below, AreaLight.received(rects, 0, DOWN, 0.1f, 5.0f, 0.9f, 0.0f, 1.0f, 0.0f, REACH, 0.0f), 1.0e-5f, "Partout dans l'aplomb du rectangle.");
        float aside = AreaLight.received(rects, 0, DOWN, 5.5f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, 0.0f);
        assertTrue(aside > 0.0f && aside < below * 0.6f, "De biais, bien moins : " + aside + " pour " + below + ".");
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 3.0f, 10.0f, 0.5f, 0.0f, -1.0f, 0.0f, REACH, 0.0f), 0.0f, "Rien sur le plafond qui la porte.");
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 3.0f, 11.0f, 0.5f, 0.0f, -1.0f, 0.0f, REACH, 0.0f), 0.0f, "Rien au-dessus de son plan.");
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 0.5f, 10.0f, 0.5f, 0.0f, -1.0f, 0.0f, REACH, 0.0f), 0.0f, "Un point sur la dalle : aucune division par zero.");
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 0.5f, 5.0f, 0.5f, 0.0f, -1.0f, 0.0f, REACH, 0.0f), 0.0f, "Une surface qui lui tourne le dos.");
    }

    @Test
    @DisplayName("Un mur que la dalle frole recoit moins que le sol sous elle, et le haut du mur moins que son milieu")
    void wallsStayBelowTheFloor() {
        float[] rects = new float[AreaLight.VALUES];
        AreaLight.blockFace(rects, 0, 0, 10, 0, DOWN, 1.0f);
        float floor = AreaLight.received(rects, 0, DOWN, 0.5f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, 0.0f);
        float wallTop = AreaLight.received(rects, 0, DOWN, 2.0f, 9.75f, 0.5f, -1.0f, 0.0f, 0.0f, REACH, 0.0f);
        float wallMiddle = AreaLight.received(rects, 0, DOWN, 2.0f, 8.0f, 0.5f, -1.0f, 0.0f, 0.0f, REACH, 0.0f);
        assertTrue(wallTop < wallMiddle, "Pas de bande brulee en haut du mur : " + wallTop + " pour " + wallMiddle + ".");
        assertTrue(wallMiddle < floor, "Le mur ne depasse pas le sol : " + wallMiddle + " pour " + floor + ".");
    }

    @Test
    @DisplayName("La portee et l'angle bornent une dalle, sans cassure a leur limite")
    void reachAndAngle() {
        float[] rects = new float[AreaLight.VALUES];
        AreaLight.blockFace(rects, 0, 0, 10, 0, DOWN, 1.0f);
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 0.5f, -10.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, 0.0f), 0.0f, "Hors de portee.");
        float cone = (float) Math.cos(Math.toRadians(30.0));
        assertTrue(AreaLight.received(rects, 0, DOWN, 0.5f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, cone) > 0.0f, "Dans l'angle.");
        assertEquals(0.0f, AreaLight.received(rects, 0, DOWN, 6.0f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, cone), 0.0f, "Hors de l'angle.");
        float last = AreaLight.received(rects, 0, DOWN, 1.0f, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, cone);
        for (float x = 1.1f; x < 6.0f; x += 0.1f) {
            float value = AreaLight.received(rects, 0, DOWN, x, 5.0f, 0.5f, 0.0f, 1.0f, 0.0f, REACH, cone);
            assertTrue(last - value < 0.06f, "Sans marche, a " + x + " : " + last + " puis " + value + ".");
            last = value;
        }
    }

    @Test
    @DisplayName("La face d'un bloc donne un rectangle qui eclaire devant elle, quelle que soit la face")
    void blockFaces() {
        float[] rects = new float[AreaLight.VALUES];
        for (int face = 0; face < LightCell.FACES; face++) {
            AreaLight.blockFace(rects, 0, 4, 4, 4, face, 1.0f);
            float nx = LightCell.NORMAL_X[face];
            float ny = LightCell.NORMAL_Y[face];
            float nz = LightCell.NORMAL_Z[face];
            float x = 4.5f + nx * 3.0f;
            float y = 4.5f + ny * 3.0f;
            float z = 4.5f + nz * 3.0f;
            assertEquals(AreaLight.falloff(6.25f, REACH * REACH), AreaLight.received(rects, 0, face, x, y, z, -nx, -ny, -nz, REACH, 0.0f), 1.0e-5f,
                    "Face " + face + " : vue depuis l'air, elle eclaire.");
            assertEquals(0.0f, AreaLight.received(rects, 0, face, x, y, z, nx, ny, nz, REACH, 0.0f), 0.0f, "Face " + face + " : derriere la surface.");
            assertEquals(0.0f, AreaLight.received(rects, 0, face, 4.5f - nx * 3.0f, 4.5f - ny * 3.0f, 4.5f - nz * 3.0f, nx, ny, nz, REACH, 0.0f), 0.0f,
                    "Face " + face + " : derriere elle.");
        }
    }

    @Test
    @DisplayName("Un rayon s'arrete au premier bloc, dit la face touchee et la derniere cellule d'air, sur les six directions")
    void rayStopsAtBlocks() {
        for (int face = 0; face < LightCell.FACES; face++) {
            LightBlocks blocks = new LightBlocks();
            byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
            int dx = LightCell.NORMAL_X[face];
            int dy = LightCell.NORMAL_Y[face];
            int dz = LightCell.NORMAL_Z[face];
            section[LightWindow.sectionIndex(8 + dx * 3, 8 + dy * 3, 8 + dz * 3)] = 5;
            for (int i = 0; i < 27; i++) {
                blocks.setSection(SectionBaker.neighborKey(i, 0, 0, 0), i == 13 ? section : LightBlocks.EMPTY);
            }
            LightWindow window = new LightWindow();
            window.fill(blocks, 0, 0, 0);
            BlockRay ray = new BlockRay();
            assertTrue(ray.march(window, 8.5f, 8.5f, 8.5f, dx, dy, dz, 10.0f), "Touche, face " + face + ".");
            assertEquals(LightCell.opposite(face), ray.face(), "La face touchee regarde le rayon.");
            assertEquals(8 + dx * 2, ray.cellX(), "Derniere cellule d'air, X.");
            assertEquals(8 + dy * 2, ray.cellY(), "Derniere cellule d'air, Y.");
            assertEquals(8 + dz * 2, ray.cellZ(), "Derniere cellule d'air, Z.");
            assertEquals(5, ray.block(), "Matiere touchee.");
            assertFalse(ray.march(window, 8.5f, 8.5f, 8.5f, dx, dy, dz, 2.0f), "Trop court.");
            assertFalse(ray.clear(window, 8.5f, 8.5f, 8.5f, 8.5f + dx * 5, 8.5f + dy * 5, 8.5f + dz * 5), "Le segment passe par le bloc.");
            assertTrue(ray.clear(window, 8.5f, 8.5f, 8.5f, 8.5f + dx * 2, 8.5f + dy * 2, 8.5f + dz * 2), "Le segment s'arrete avant.");
            assertTrue(ray.clear(window, 8.5f, 8.5f, 8.5f, 8.5f - dx, 8.5f - dy, 8.5f - dz), "L'autre sens est libre.");
        }
    }

    @Test
    @DisplayName("Un rayon en biais passe de cellule en cellule, et un rayon nul ne va nulle part")
    void slantedRay() {
        LightBlocks blocks = new LightBlocks();
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        section[LightWindow.sectionIndex(10, 10, 10)] = 9;
        for (int i = 0; i < 27; i++) {
            blocks.setSection(SectionBaker.neighborKey(i, 0, 0, 0), i == 13 ? section : LightBlocks.EMPTY);
        }
        LightWindow window = new LightWindow();
        window.fill(blocks, 0, 0, 0);
        BlockRay ray = new BlockRay();
        assertTrue(ray.march(window, 8.5f, 8.5f, 8.5f, 1.0f, 1.0f, 1.0f, 3.0f), "La diagonale touche le bloc.");
        assertEquals(9, ray.block(), "Sa matiere.");
        assertFalse(ray.march(window, 8.5f, 8.5f, 8.5f, 1.0f, 1.0f, 0.2f, 3.0f), "Un peu a cote, il passe.");
        assertFalse(ray.march(window, 8.5f, 8.5f, 8.5f, 0.0f, 0.0f, 0.0f, 3.0f), "Rayon nul : aucun pas.");
        assertArrayEquals(new byte[] {9}, new byte[] {ray.block()}, "Le dernier arret reste lisible.");
    }
}
