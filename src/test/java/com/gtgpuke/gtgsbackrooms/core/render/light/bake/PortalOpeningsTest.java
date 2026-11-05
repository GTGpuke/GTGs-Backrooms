package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

@DisplayName("Cuisson de la lumiere : les portails, murs et passages")
class PortalOpeningsTest {

    /** Sol en Y = 0, air de 1 a 4, plafond en Y = 5. */
    private static final int FLOOR = 0;
    private static final int CEILING = 5;
    private static final int EAST = 0;
    private static final int WEST = 1;
    private static final int UP = 2;
    private static final int DOWN = 3;
    private static final float AMBIENT = 0.1f;
    private static final LightBakeSettings SETTINGS = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f);
    private static final double[] IDENTITY = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
    /** Un cadre d'un bloc sur deux, debout dans le plan X = 8, pose sur le sol : on y entre par l'ouest. */
    private static final double[] FRAME = {8.0, 2.0, 8.5};
    private static final double[] HALF_WIDTH = {0.0, 0.0, 0.5};
    private static final double[] HALF_HEIGHT = {0.0, 1.0, 0.0};
    /** Un mur qui fait face au cadre, a un bloc de lui, de ce cote : beaucoup de ses rayons passent le cadre. */
    private static final int NEAR_FRAME = LightCell.index(7, 2, 8, EAST);
    private static final long CENTER_SECTION = ChunkSectionPos.asLong(0, 0, 0);

    private LightBlocks blocks;
    private byte stone;
    private byte neon;
    private byte[] room;
    /** L'autre cote : une piece pareille, ses propres blocs, une dalle au plafond a l'est du cadre. */
    private LightBlocks other;
    private byte[] otherRoom;

    @BeforeEach
    void setUp() {
        this.blocks = new LightBlocks();
        this.stone = this.blocks.addMaterial(0.5f, 0.5f, 0.5f, false);
        this.neon = this.blocks.addMaterial(1.0f, 1.0f, 1.0f, true);
        this.room = emptyRoom(this.stone);
        this.other = new LightBlocks();
        byte otherStone = this.other.addMaterial(0.5f, 0.5f, 0.5f, false);
        byte otherNeon = this.other.addMaterial(1.0f, 1.0f, 1.0f, true);
        this.otherRoom = emptyRoom(otherStone);
        this.otherRoom[LightWindow.sectionIndex(11, CEILING, 8)] = otherNeon;
    }

    private static byte[] emptyRoom(byte wall) {
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (y == FLOOR || y >= CEILING) {
                        section[LightWindow.sectionIndex(x, y, z)] = wall;
                    }
                }
            }
        }
        return section;
    }

    /** Le mur qui fait face au cadre, a l'ouest de la cellule regardee. */
    private void wallFacingTheFrame() {
        for (int y = 1; y < CEILING; y++) {
            this.room[LightWindow.sectionIndex(6, y, 8)] = this.stone;
        }
    }

    private void fill(int radius) {
        fill(this.blocks, this.room, this.stone, radius);
        fill(this.other, this.otherRoom, (byte) 2, radius);
    }

    private static void fill(LightBlocks target, byte[] center, byte wall, int radius) {
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                target.setSection(ChunkSectionPos.asLong(x, 0, z), x == 0 && z == 0 ? center : emptyRoom(wall));
                target.setSection(ChunkSectionPos.asLong(x, 1, z), LightBlocks.UNLOADED);
                target.setSection(ChunkSectionPos.asLong(x, -1, z), LightBlocks.UNLOADED);
            }
        }
    }

    /** Le passage du cadre vers ce point de l'autre cote, dans une zone, sans tourner. */
    private static PortalOpenings passage(LightZone beyond, double[] arrival) {
        float[] values = new float[PortalOpenings.VALUES];
        PortalOpenings.writePassage(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, arrival);
        return PortalOpenings.of(values, new LightZone[] {beyond}, 1);
    }

    private static PortalOpenings wall() {
        float[] values = new float[PortalOpenings.VALUES];
        PortalOpenings.writeWall(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST);
        return PortalOpenings.of(values, new LightZone[1], 1);
    }

    private LightZone zone(LightBlocks source) {
        LightZone zone = new LightZone(source, SETTINGS, 0, 2);
        zone.anchor(0, 0, 0);
        return zone;
    }

    private int[] bake(PortalOpenings openings) {
        SectionBaker baker = new SectionBaker();
        baker.openings(openings);
        int[] out = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, out);
        return out;
    }

    private static int drain(LightZone zone) {
        int steps = 0;
        while (zone.step()) {
            steps++;
        }
        return steps;
    }

    /** L'eclairement publie d'une cellule de la section de l'ancrage, depuis la derniere lecture ; -1 si rien n'a ete publie. */
    private static float publishedAt(LightZone zone, int index) {
        float value = -1.0f;
        for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
            if (baked.key() == CENTER_SECTION) {
                value = LightCell.red(baked.values()[index]);
            }
        }
        return value;
    }

    @Test
    @DisplayName("Une liste se copie et se compare zones comprises, et rend coins, face, sorte, passage, transformation et zone")
    void openingsHoldTheirValues() {
        assertEquals(0, PortalOpenings.NONE.count(), "La liste vide.");
        assertSame(PortalOpenings.NONE, PortalOpenings.of(new float[PortalOpenings.VALUES], new LightZone[1], 0), "Sans element, la liste vide.");
        LightZone beyond = zone(this.other);
        float[] values = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writeWall(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST);
        PortalOpenings.writePassage(values, 1, new double[] {4.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, EAST, new double[] {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0},
                new double[] {100.0, 2.0, 50.0});
        LightZone[] zones = {null, beyond};
        PortalOpenings openings = PortalOpenings.of(values, zones, 2);
        assertEquals(2, openings.count(), "Deux elements.");
        assertTrue(openings.sameAs(values, zones, 2), "Les memes valeurs, les memes zones.");
        assertFalse(openings.sameAs(values, zones, 1), "Un de moins.");
        assertFalse(openings.sameAs(values, new LightZone[] {null, zone(this.other)}, 2), "Une autre zone.");
        values[PortalOpenings.VALUES] = 5.0f;
        assertFalse(openings.sameAs(values, zones, 2), "Copiee : le tableau peut changer ensuite.");
        zones[1] = null;
        assertSame(beyond, openings.zone(1), "Copiees aussi : la zone de l'autre cote.");
        assertNull(openings.zone(0), "Un mur n'a pas d'autre cote.");
        float[] rects = new float[AreaLight.VALUES];
        openings.corners(1, rects, 0);
        assertArrayEquals(new float[] {4.0f, 3.0f, 8.0f, 4.0f, 3.0f, 9.0f, 4.0f, 1.0f, 9.0f, 4.0f, 1.0f, 8.0f}, rects, "Les coins, dans l'ordre d'une dalle.");
        assertEquals(EAST, openings.faceOf(1), "Sa face.");
        assertEquals(2.0f, openings.center(1, 1), "Son centre.");
        assertEquals(PortalOpenings.PASSAGE, openings.kind(1), "Un passage.");
        assertEquals(PortalOpenings.WALL, openings.kind(0), "Un mur.");
        assertEquals(0.0f, openings.color(0, 0), "Sans couleur.");
        assertEquals(1, openings.passage(1), "Son propre passage.");
        assertTrue(openings.isFrame(1) && openings.isFrame(0), "Deux cadres.");
        assertEquals(9.0f, openings.corner(1, 1, 2), "Son deuxieme coin, en Z.");
        assertEquals(1.0f, openings.value(1, AreaLight.VALUES + 6 + 2), "Ses valeurs, dans l'ordre ou elles sont ecrites.");
        // Un quart de tour autour de la verticale : un pas vers l'est devient un pas vers le nord, et le centre va a l'arrivee.
        float[] point = {5.0f, 3.0f, 8.5f};
        openings.toArrival(1, point);
        assertArrayEquals(new float[] {100.0f, 3.0f, 49.0f}, point, 1.0e-5f, "Le point passe de l'autre cote, tourne autour de l'arrivee.");
        float[] direction = {1.0f, 0.0f, 0.0f};
        openings.turn(1, direction);
        assertArrayEquals(new float[] {0.0f, 0.0f, -1.0f}, direction, 1.0e-6f, "La direction tourne, sans se deplacer.");
        assertTrue(openings.near(1, new float[] {10.0f, 0.0f, 0.0f}, new float[] {16.0f, 16.0f, 16.0f}, 7.0f), "A six blocs d'une boite.");
        assertFalse(openings.near(1, new float[] {10.0f, 0.0f, 0.0f}, new float[] {16.0f, 16.0f, 16.0f}, 6.0f), "Pas a moins de six.");
        assertFalse(openings.near(1, new float[] {-16.0f, 0.0f, 0.0f}, new float[] {-6.0f, 16.0f, 16.0f}, 10.0f), "De l'autre cote non plus.");
    }

    @Test
    @DisplayName("Un segment passe un cadre s'il le traverse dans son rectangle ; la forme ne compte ni les couleurs ni les zones")
    void framesCutSegments() {
        float[] values = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writeWall(values, 0, new double[] {4.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, EAST);
        PortalOpenings.writeVirtual(values, 1, new float[AreaLight.VALUES], UP, new float[] {1.0f, 0.5f, 0.0f}, 0);
        PortalOpenings openings = PortalOpenings.of(values, new LightZone[2], 2);
        assertEquals(PortalOpenings.VIRTUAL, openings.kind(1), "Un neon vu par le portail.");
        assertFalse(openings.isFrame(1), "Pas un cadre.");
        assertEquals(0, openings.passage(1), "Il passe par le cadre.");
        assertEquals(0.5f, openings.color(1, 1), "Sa couleur.");
        assertEquals(0.5f, openings.crossing(0, new float[] {2.0f, 2.0f, 8.5f}, new float[] {6.0f, 2.0f, 8.5f}), 1.0e-6f, "Au milieu.");
        assertEquals(0.5f, openings.crossing(0, new float[] {6.0f, 2.0f, 8.5f}, new float[] {2.0f, 2.0f, 8.5f}), 1.0e-6f, "Dans l'autre sens aussi.");
        assertEquals(-1.0f, openings.crossing(0, new float[] {6.0f, 2.0f, 8.5f}, new float[] {4.0f, 2.0f, 8.5f}), "Fini sur le plan.");
        assertEquals(-1.0f, openings.crossing(0, new float[] {2.0f, 2.0f, 8.5f}, new float[] {3.0f, 2.0f, 8.5f}), "Pas jusqu'au plan.");
        assertEquals(-1.0f, openings.crossing(0, new float[] {2.0f, 2.0f, 12.0f}, new float[] {6.0f, 2.0f, 12.0f}), "A cote du cadre.");
        assertEquals(-1.0f, openings.crossing(0, new float[] {2.0f, 5.0f, 8.5f}, new float[] {6.0f, 5.0f, 8.5f}), "Au-dessus.");
        float[] moved = values.clone();
        moved[PortalOpenings.VALUES + AreaLight.VALUES + 1] = 0.9f;
        assertTrue(openings.sameShapeAs(PortalOpenings.of(moved, new LightZone[] {zone(this.other), null}, 2)), "Une autre couleur, une zone : la meme forme.");
        moved[0] = 3.0f;
        assertFalse(openings.sameShapeAs(PortalOpenings.of(moved, new LightZone[2], 2)), "Un coin deplace : une autre forme.");
        assertFalse(openings.sameShapeAs(PortalOpenings.of(values, new LightZone[2], 1)), "Un element de moins.");
    }

    @Test
    @DisplayName("La face d'une normale alignee sur un axe, aucune pour une autre ; la face d'un neon est celle du bas")
    void facesOfNormals() {
        for (int face = 0; face < LightCell.FACES; face++) {
            assertEquals(face, PortalOpenings.face(LightCell.NORMAL_X[face], LightCell.NORMAL_Y[face], LightCell.NORMAL_Z[face]), "Face " + face + ".");
        }
        assertEquals(-1, PortalOpenings.face(0.0, 0.0, 0.0), "Pas de normale.");
        assertEquals(-1, PortalOpenings.face(1.0, 1.0, 0.0), "Pas sur un axe.");
        float[] corners = new float[AreaLight.VALUES];
        PortalOpenings.neonFace(corners, 2, 4, 8, 1.0f);
        float[] expected = new float[AreaLight.VALUES];
        AreaLight.blockFace(expected, 0, 2, 4, 8, DOWN, 1.0f);
        assertArrayEquals(expected, corners, "La face d'un neon : celle du bas, comme la cuisson la prend.");
    }

    @Test
    @DisplayName("Un cadre est un mur pour la lumiere de son monde : un neon ne passe pas, et un rayon de rebond qui le traverse ne ramene rien")
    void framesBlockTheirWorld() {
        this.room[LightWindow.sectionIndex(8, 2, 8)] = this.neon;
        fill(1);
        float[] values = new float[PortalOpenings.VALUES];
        PortalOpenings.writeWall(values, 0, new double[] {10.0, 2.5, 8.0}, new double[] {0.0, 0.0, 8.0}, new double[] {0.0, 2.5, 0.0}, EAST);
        int beyond = LightCell.index(12, 1, 8, UP);
        float open = LightCell.red(bake(PortalOpenings.NONE)[beyond]);
        float walled = LightCell.red(bake(PortalOpenings.of(values, new LightZone[1], 1))[beyond]);
        assertTrue(open > AMBIENT + 0.1f, "Sans cadre, le neon eclaire au-dela : " + open + ".");
        assertEquals(AMBIENT, walled, 0.01f, "Le cadre arrete sa lumiere.");
        float[] lying = new float[PortalOpenings.VALUES];
        PortalOpenings.writeWall(lying, 0, new double[] {8.0, 3.5, 8.0}, new double[] {8.0, 0.0, 0.0}, new double[] {0.0, 0.0, 8.0}, UP);
        int ceiling = LightCell.index(4, CEILING - 1, 8, DOWN);
        float bounced = afterBounce(PortalOpenings.NONE, ceiling, 0);
        float cut = afterBounce(PortalOpenings.of(lying, new LightZone[1], 1), ceiling, 0);
        assertTrue(cut < bounced - 0.05f, "Le rebond du sol ne passe plus le cadre : " + cut + " contre " + bounced + ".");
        // Deux cadres couches l'un sur l'autre : le rayon s'arrete au premier, comme a un seul.
        float[] stacked = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writeWall(stacked, 0, new double[] {8.0, 3.5, 8.0}, new double[] {8.0, 0.0, 0.0}, new double[] {0.0, 0.0, 8.0}, UP);
        PortalOpenings.writeWall(stacked, 1, new double[] {8.0, 2.5, 8.0}, new double[] {8.0, 0.0, 0.0}, new double[] {0.0, 0.0, 8.0}, UP);
        assertEquals(cut, afterBounce(PortalOpenings.of(stacked, new LightZone[2], 2), ceiling, 0), 0.02f, "Deux cadres coupent comme un.");
        float[] upward = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writeWall(upward, 0, new double[] {8.0, 2.5, 8.0}, new double[] {8.0, 0.0, 0.0}, new double[] {0.0, 0.0, 8.0}, UP);
        PortalOpenings.writeWall(upward, 1, new double[] {8.0, 3.5, 8.0}, new double[] {8.0, 0.0, 0.0}, new double[] {0.0, 0.0, 8.0}, UP);
        assertEquals(cut, afterBounce(PortalOpenings.of(upward, new LightZone[2], 2), ceiling, 0), 0.02f, "Dans l'autre ordre aussi.");
    }

    @Test
    @DisplayName("Un point sur le plan d'un cadre, dans son rectangle a la marge pres, est sur lui ; et une face de cellule que le plan coupe se cuit du cote ou il s'ouvre")
    void facesOnThePlaneBakeOnTheFramesSide() {
        float[] values = new float[PortalOpenings.VALUES];
        PortalOpenings.writeWall(values, 0, new double[] {8.5, 2.0, 8.0}, new double[] {0.0, 0.0, 1.5}, new double[] {0.0, 2.0, 0.0}, EAST);
        PortalOpenings openings = PortalOpenings.of(values, new LightZone[1], 1);
        assertEquals(EAST, openings.faceOf(0), "La face du cadre.");
        assertTrue(openings.onPlane(0, 8.5f, 1.0f, 8.5f), "Le point d'un sol sur le plan.");
        assertTrue(openings.onPlane(0, 8.5f, 0.0f, 9.5f + PortalOpenings.EDGE * 0.5f), "Au bord du cadre, a la marge pres.");
        assertFalse(openings.onPlane(0, 8.5f, 1.0f, 9.6f), "A cote du cadre.");
        assertFalse(openings.onPlane(0, 8.4f, 1.0f, 8.5f), "Devant le plan.");
        assertFalse(openings.onPlane(0, 8.5f, 4.1f, 8.5f), "Au-dessus du cadre.");
        // Un neon a l'ouest du plan, un cadre au milieu du bloc 8 : le sol de ce bloc a son point sur le plan. Ouvert vers l'est, le cadre
        // le cuit a l'est, ou le neon est derriere le cadre : sombre. Ouvert vers l'ouest, a l'ouest : eclaire.
        this.room[LightWindow.sectionIndex(4, 2, 8)] = this.neon;
        fill(1);
        int floor = LightCell.index(8, 1, 8, UP);
        float open = LightCell.red(bake(PortalOpenings.NONE)[floor]);
        float east = LightCell.red(bake(openings)[floor]);
        float[] west = new float[PortalOpenings.VALUES];
        PortalOpenings.writeWall(west, 0, new double[] {8.5, 2.0, 8.0}, new double[] {0.0, 0.0, 1.5}, new double[] {0.0, 2.0, 0.0}, WEST);
        float lit = LightCell.red(bake(PortalOpenings.of(west, new LightZone[1], 1))[floor]);
        assertTrue(open > AMBIENT + 0.1f, "Sans cadre, le neon eclaire ce sol : " + open + ".");
        assertEquals(AMBIENT, east, 0.01f, "Cuit du cote est, le sol est derriere le cadre pour le neon : " + east + ".");
        assertEquals(open, lit, 0.02f, "Cuit du cote ouest, le sol garde le neon : " + lit + " contre " + open + ".");
    }

    /** Un rebond de la section de l'ancrage, sur la lumiere directe de ses seuls blocs, en lisant l'autre cote a cet etat. */
    private float afterBounce(PortalOpenings openings, int index, int crossStage) {
        SectionBaker baker = new SectionBaker();
        baker.openings(openings);
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        int[] out = new int[LightCell.VALUES];
        baker.place(this.blocks, 0, 0, 0);
        baker.crossStage(crossStage);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, out);
        return LightCell.red(out[index]);
    }

    @Test
    @DisplayName("Beaucoup de cadres a portee d'une section comptent tous : le plus loin arrete encore la lumiere")
    void manyFrames() {
        this.room[LightWindow.sectionIndex(8, 2, 8)] = this.neon;
        fill(1);
        int frames = 12;
        float[] values = new float[PortalOpenings.VALUES * frames];
        for (int i = 0; i < frames - 1; i++) {
            PortalOpenings.writeWall(values, i, new double[] {2.0, 2.5, i + 0.5}, new double[] {0.0, 0.0, 0.25}, new double[] {0.0, 0.25, 0.0}, EAST);
        }
        PortalOpenings.writeWall(values, frames - 1, new double[] {10.0, 2.5, 8.0}, new double[] {0.0, 0.0, 8.0}, new double[] {0.0, 2.5, 0.0}, EAST);
        assertEquals(AMBIENT, LightCell.red(bake(PortalOpenings.of(values, new LightZone[frames], frames))[LightCell.index(12, 1, 8, UP)]), 0.01f,
                "Le douzieme cadre compte.");
    }

    @Test
    @DisplayName("Un neon vu par un portail eclaire par l'ouverture seulement, et les blocs des deux cotes font de l'ombre a son faisceau")
    void virtualNeonsShineThroughTheOpening() {
        fill(1);
        LightZone beyond = zone(this.other);
        // Le neon de l'autre cote, au plafond a l'est du cadre, reporte a la meme place : la transformation ne tourne pas.
        float[] corners = new float[AreaLight.VALUES];
        PortalOpenings.neonFace(corners, 11, CEILING, 8, 1.0f);
        float[] values = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writePassage(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        PortalOpenings.writeVirtual(values, 1, corners, DOWN, new float[] {2.0f, 0.0f, 0.0f}, 0);
        PortalOpenings openings = PortalOpenings.of(values, new LightZone[] {beyond, null}, 2);
        int through = LightCell.index(6, 1, 8, UP);
        int[] lit = bake(openings);
        assertTrue(LightCell.red(lit[through]) > AMBIENT + 0.1f, "Le sol que le faisceau atteint par l'ouverture : " + LightCell.red(lit[through]) + ".");
        assertEquals(AMBIENT, LightCell.green(lit[through]), 0.01f, "De la couleur du neon.");
        assertEquals(AMBIENT, LightCell.red(lit[LightCell.index(6, 1, 13, UP)]), 0.01f, "Hors du faisceau qui passe le cadre, rien.");
        // Sans zone de l'autre cote, ses blocs ne comptent pas : le neon passe pareil.
        assertArrayEquals(lit, bake(PortalOpenings.of(values, new LightZone[2], 2)), "Sans blocs de l'autre cote, rien ne l'arrete la-bas.");
        // Un pilier de l'autre cote, entre l'arrivee et le neon : il fait de l'ombre de ce cote.
        for (int y = 1; y < CEILING; y++) {
            this.otherRoom[LightWindow.sectionIndex(9, y, 8)] = 2;
        }
        assertEquals(AMBIENT, LightCell.red(bake(openings)[through]), 0.01f, "Un bloc de l'autre cote fait de l'ombre au faisceau.");
        for (int y = 1; y < CEILING; y++) {
            this.otherRoom[LightWindow.sectionIndex(9, y, 8)] = LightBlocks.AIR;
            this.otherRoom[LightWindow.sectionIndex(8, y, 8)] = 2;
        }
        assertEquals(AMBIENT, LightCell.red(bake(openings)[through]), 0.01f, "Un bloc contre l'arrivee, de l'autre cote : le faisceau s'y arrete.");
        for (int y = 1; y < CEILING; y++) {
            this.otherRoom[LightWindow.sectionIndex(8, y, 8)] = LightBlocks.AIR;
        }
        // Un autre cadre, debout entre le sol et l'ouverture : la lumiere qui le traverse part ailleurs.
        float[] walled = new float[PortalOpenings.VALUES * 3];
        System.arraycopy(values, 0, walled, 0, values.length);
        PortalOpenings.writeWall(walled, 2, new double[] {7.0, 2.5, 8.0}, new double[] {0.0, 0.0, 4.0}, new double[] {0.0, 2.5, 0.0}, WEST);
        assertEquals(AMBIENT, LightCell.red(bake(PortalOpenings.of(walled, new LightZone[] {beyond, null, null}, 3))[through]), 0.01f,
                "Un cadre devant l'ouverture coupe le faisceau.");
        this.room[LightWindow.sectionIndex(7, 1, 8)] = this.stone;
        this.room[LightWindow.sectionIndex(7, 2, 8)] = this.stone;
        assertEquals(AMBIENT, LightCell.red(bake(openings)[through]), 0.01f, "Un bloc de ce cote, devant le cadre, lui fait de l'ombre.");
    }

    @Test
    @DisplayName("Un rayon de rebond qui entre dans un passage par sa face ramene la lumiere cuite de l'autre cote ; un mur, rien")
    void bouncesCrossPassages() {
        wallFacingTheFrame();
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone beyond = zone(this.other);
        drain(beyond);
        PortalOpenings opened = passage(beyond, FRAME);
        float walled = afterBounce(wall(), NEAR_FRAME, 1);
        float crossed = afterBounce(opened, NEAR_FRAME, 1);
        assertTrue(crossed > walled + 0.05f, "La piece eclairee de l'autre cote renvoie sa lumiere par le cadre : " + crossed + " contre " + walled + ".");
        float open = afterBounce(PortalOpenings.NONE, NEAR_FRAME, 1);
        assertEquals(open, afterBounce(opened, NEAR_FRAME, 0), 1.0e-6f, "Sans etat a lire de l'autre cote, comme sans portail.");
        assertEquals(open, afterBounce(passage(null, FRAME), NEAR_FRAME, 1), 1.0e-6f, "Sans zone la-bas, comme sans portail.");
        // Un portail double : ses deux faces dans le meme plan, celle de dos d'abord. Le rayon passe par celle ou il entre.
        float[] both = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writePassage(both, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, EAST, IDENTITY, FRAME);
        PortalOpenings.writePassage(both, 1, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        assertEquals(crossed, afterBounce(PortalOpenings.of(both, new LightZone[] {beyond, beyond}, 2), NEAR_FRAME, 1), 1.0e-6f,
                "Les deux faces d'un portail double : la face d'entree l'emporte, celle de dos d'abord.");
        float[] reversed = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writePassage(reversed, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        PortalOpenings.writePassage(reversed, 1, FRAME, HALF_WIDTH, HALF_HEIGHT, EAST, IDENTITY, FRAME);
        assertEquals(crossed, afterBounce(PortalOpenings.of(reversed, new LightZone[] {beyond, beyond}, 2), NEAR_FRAME, 1), 1.0e-6f,
                "Et celle d'entree d'abord.");
        // Le meme passage, pris de l'autre cote : les rayons qui le passent sortent par son dos, et le cadre les arrete.
        float[] values = new float[PortalOpenings.VALUES];
        PortalOpenings.writePassage(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, EAST, IDENTITY, FRAME);
        assertEquals(walled, afterBounce(PortalOpenings.of(values, new LightZone[] {beyond}, 1), NEAR_FRAME, 1), 1.0e-6f, "Entre par son dos, il arrete.");
        // Une arrivee loin de la dalle de l'autre cote : les rayons qui passent le cadre y trouvent moins de lumiere.
        PortalOpenings far = passage(beyond, new double[] {8.0, 2.0, -40.0});
        float farther = afterBounce(far, NEAR_FRAME, 1);
        assertTrue(farther < crossed, "Une arrivee loin de la dalle ramene moins : " + farther + ".");
        // Un mur juste derriere l'arrivee : les rayons qui passent le cadre s'y arretent aussitot.
        for (int y = 1; y < CEILING; y++) {
            this.otherRoom[LightWindow.sectionIndex(8, y, 8)] = 2;
        }
        assertEquals(walled, afterBounce(opened, NEAR_FRAME, 1), 1.0e-6f, "Un mur contre l'arrivee cache la piece : comme un mur ici.");
        for (int y = 1; y < CEILING; y++) {
            this.otherRoom[LightWindow.sectionIndex(8, y, 8)] = LightBlocks.AIR;
        }
        // Une zone sans rien de cuit : ses surfaces n'ont pas d'eclairement a ramener.
        float unbaked = afterBounce(passage(zone(this.other), FRAME), NEAR_FRAME, 1);
        assertTrue(unbaked > walled + 0.01f, "Rien de cuit la-bas : le rayon continue de ce cote, plutot qu'un mur sombre : " + unbaked + " contre " + walled + ".");
    }

    @Test
    @DisplayName("Quatre passages par section ont leurs blocs de l'autre cote ; au-dela, les rayons passent un passage comme sans portail")
    void crossingsPerSection() {
        wallFacingTheFrame();
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone beyond = zone(this.other);
        drain(beyond);
        int frames = SectionBaker.CROSSINGS + 1;
        float[] values = new float[PortalOpenings.VALUES * frames];
        LightZone[] zones = new LightZone[frames];
        // Quatre petits passages dans le sol, ou aucun rayon ne va, puis le cadre devant le sol regarde.
        for (int i = 0; i < frames - 1; i++) {
            PortalOpenings.writePassage(values, i, new double[] {2.0, -2.0, i + 0.5}, new double[] {0.0, 0.0, 0.1}, new double[] {0.0, 0.1, 0.0}, WEST, IDENTITY, FRAME);
            zones[i] = beyond;
        }
        PortalOpenings.writePassage(values, frames - 1, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        zones[frames - 1] = beyond;
        float open = afterBounce(PortalOpenings.NONE, NEAR_FRAME, 1);
        assertEquals(open, afterBounce(PortalOpenings.of(values, zones, frames), NEAR_FRAME, 1), 1.0e-6f,
                "Le cinquieme passage n'a pas ses blocs de l'autre cote : les rayons le passent comme s'il n'etait pas la.");
        // Le meme cadre parmi les premiers : il a ses blocs de l'autre cote, et ses rayons ramenent ce qu'ils y touchent.
        float[] first = new float[PortalOpenings.VALUES * frames];
        PortalOpenings.writePassage(first, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        for (int i = 1; i < frames; i++) {
            PortalOpenings.writePassage(first, i, new double[] {2.0, -2.0, i - 0.5}, new double[] {0.0, 0.0, 0.1}, new double[] {0.0, 0.1, 0.0}, WEST, IDENTITY, FRAME);
        }
        float crossed = afterBounce(PortalOpenings.of(first, zones, frames), NEAR_FRAME, 1);
        assertTrue(Math.abs(crossed - open) > 1.0e-4f, "Parmi les premiers, le passage regarde mene de l'autre cote : " + crossed + " contre " + open + ".");
    }

    @Test
    @DisplayName("Une zone lit l'autre cote de ses passages : le mur face au cadre recoit la lumiere de la piece eclairee de la-bas ; abandonnee, la zone"
            + " d'ici n'est plus tenue par celle de la-bas")
    void zonesReadEachOther() {
        wallFacingTheFrame();
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone here = zone(this.blocks);
        LightZone beyond = zone(this.other);
        here.openings(wall());
        drain(here);
        float walled = publishedAt(here, NEAR_FRAME);
        here.openings(passage(beyond, FRAME));
        assertTrue(drain(here) > 0, "Le cadre change de sorte : la zone recuit autour de lui.");
        float crossed = publishedAt(here, NEAR_FRAME);
        assertTrue(crossed > walled + 0.02f, "Sans que l'autre zone ait cuit, celle-ci cuit ce qu'elle lit la-bas : " + crossed + " contre " + walled + ".");
        assertTrue(beyond.covers(9.0, 2.0, 8.0), "L'arrivee est dans la fenetre de l'autre zone.");
        assertFalse(beyond.covers(9.0, 40.0, 8.0), "Pas au-dessus de sa hauteur.");
        assertFalse(beyond.covers(9.0, -1.0, 8.0), "Ni au-dessous.");
        assertFalse(beyond.covers(200.0, 2.0, 8.0), "Ni loin a l'est.");
        assertFalse(beyond.covers(9.0, 2.0, -200.0), "Ni loin au nord.");
        assertNull(beyond.peek(ChunkSectionPos.asLong(30, 0, 30), 1), "Une section jamais cuite ne se lit pas.");
        assertNotNull(beyond.peek(CENTER_SECTION, 2), "Celle de l'arrivee, cuite a la demande, se lit.");
        assertEquals(1, beyond.incomingZones(), "La zone de la-bas sait qu'un passage mene a elle.");
        takeAll(here);
        assertTrue(here.idle(), "Tout pris, la zone d'ici est au repos.");
        drain(beyond);
        assertFalse(here.idle(), "La-bas a publie sa lumiere : la mesure des passages d'ici est a refaire, elle n'est pas au repos.");
        here.retire();
        drain(beyond);
        assertEquals(0, beyond.incomingZones(), "Abandonnee, la zone d'ici n'est plus tenue par celle de la-bas.");
    }

    @Test
    @DisplayName("Un bloc change de l'autre cote fait recuire ce qui est pres du cadre, une fois, et le cadre de retour suit, sans aller-retour sans fin")
    void changesBeyondComeBack() throws InterruptedException {
        wallFacingTheFrame();
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone here = zone(this.blocks);
        LightZone beyond = zone(this.other);
        here.openings(passage(beyond, FRAME));
        // Le retour : un passage de l'autre cote, vers ici, au meme endroit.
        float[] back = new float[PortalOpenings.VALUES];
        PortalOpenings.writePassage(back, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, EAST, IDENTITY, FRAME);
        beyond.openings(PortalOpenings.of(back, new LightZone[] {here}, 1));
        drain(here);
        drain(beyond);
        float before = publishedAt(here, NEAR_FRAME);
        publishedAt(beyond, NEAR_FRAME);
        // La dalle de l'autre cote s'eteint.
        this.otherRoom[LightWindow.sectionIndex(11, CEILING, 8)] = 2;
        this.other.setSection(CENTER_SECTION, this.otherRoom.clone());
        beyond.onChanged(CENTER_SECTION);
        drain(beyond);
        // Le dernier changement de l'autre cote, a l'instant : la cuisson de la-bas a pu durer plus d'un quart de seconde.
        here.crossChanged(beyond, CENTER_SECTION, 1);
        assertFalse(here.idle(), "Ici, on attend que l'autre cote se calme.");
        assertFalse(here.step(), "Pas avant un quart de seconde.");
        Thread.sleep(300L);
        assertTrue(drain(here) > 0, "Puis ce qui est pres du cadre recuit.");
        float after = publishedAt(here, NEAR_FRAME);
        assertTrue(after < before - 0.005f, "Sans la dalle de l'autre cote, moins de lumiere : " + after + " contre " + before + ".");
        Thread.sleep(300L);
        assertTrue(drain(beyond) > 0, "L'autre cote recuit pres de son cadre de retour.");
        Thread.sleep(300L);
        assertEquals(0, drain(here), "Et rien ne revient ici : pas d'aller-retour sans fin.");
        assertEquals(0, drain(beyond), "Ni la-bas.");
        // Un changement ici, loin de l'arrivee du retour : l'autre cote ne recuit pas.
        here.onChanged(ChunkSectionPos.asLong(-3, 0, -3));
        drain(here);
        Thread.sleep(300L);
        assertEquals(0, drain(beyond), "Un changement loin de l'arrivee ne touche pas l'autre cote.");
    }

    @Test
    @DisplayName("La lumiere qui sort d'un passage se mesure de l'autre cote, par direction, pour les shaders")
    void passageLightIsMeasured() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone here = zone(this.blocks);
        LightZone beyond = zone(this.other);
        assertNull(here.passageLight(), "Rien avant la premiere mesure.");
        // Une zone dont les blocs s'arretent avant ce qu'elle copie : ses sections du bord ne cuisent pas.
        LightBlocks sparse = new LightBlocks();
        byte sparseStone = sparse.addMaterial(0.5f, 0.5f, 0.5f, false);
        fill(sparse, emptyRoom(sparseStone), sparseStone, LightZone.RADIUS);
        LightZone thin = zone(sparse);
        float[] values = new float[PortalOpenings.VALUES * 4];
        PortalOpenings.writeWall(values, 0, new double[] {-20.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, WEST);
        PortalOpenings.writePassage(values, 1, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        // Une arrivee haut et loin de l'autre cote, hors de ce que sa zone copie, et une vers les blocs qui manquent.
        PortalOpenings.writePassage(values, 2, new double[] {-30.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, new double[] {70.0, 20.0, 8.5});
        PortalOpenings.writePassage(values, 3, new double[] {-40.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, new double[] {40.0, 2.0, 8.5});
        PortalOpenings openings = PortalOpenings.of(values, new LightZone[] {null, beyond, beyond, thin}, 4);
        here.openings(openings);
        drain(here);
        LightZone.PassageLight measured = here.passageLight();
        assertNotNull(measured, "Mesuree une fois la zone au repos.");
        assertSame(openings, measured.openings(), "Pour ces portails.");
        assertEquals(4 * LightZone.PASSAGE_VALUES, measured.values().length, "Toutes les directions de chaque element.");
        float wallLight = 0.0f;
        float direct = 0.0f;
        float once = 0.0f;
        for (int direction = 0; direction < LightZone.PASSAGE_DIRECTIONS; direction++) {
            wallLight += measured.values()[direction * 6];
            direct += measured.values()[LightZone.PASSAGE_VALUES + direction * 6];
            once += measured.values()[LightZone.PASSAGE_VALUES + direction * 6 + 3];
        }
        assertEquals(0.0f, wallLight, "Un mur ne laisse rien sortir.");
        float high = 0.0f;
        float missing = 0.0f;
        for (int direction = 0; direction < LightZone.PASSAGE_DIRECTIONS; direction++) {
            high += measured.values()[2 * LightZone.PASSAGE_VALUES + direction * 6 + 3];
            missing += measured.values()[3 * LightZone.PASSAGE_VALUES + direction * 6 + 3];
        }
        assertEquals(0.0f, high, "Une arrivee hors de ce que copie la zone de la-bas ne ramene rien.");
        assertEquals(0.0f, missing, "Vers les blocs qui manquent la-bas, rien non plus.");
        assertTrue(direct > 0.0f, "La piece de l'autre cote renvoie sa lumiere directe : " + direct + ".");
        assertTrue(once >= direct, "Avec un rebond, au moins autant : " + once + ".");
        assertFalse(here.step(), "Mesuree, rien a refaire.");
        assertSame(measured, here.passageLight(), "La meme mesure.");
        beyond.onChanged(CENTER_SECTION);
        drain(beyond);
        drain(here);
        assertTrue(here.passageLight() != measured, "L'autre cote publie : la mesure se refait.");
    }

    @Test
    @DisplayName("Un portail qui mene a sa propre place est une continuite parfaite : la lumiere autour de lui est celle sans portail")
    void aPortalIntoItselfChangesNothing() {
        // Une dalle a l'est du cadre, dans ce monde ; le portail mene a sa propre place, dans la meme zone : ce qu'on voit par lui est
        // ce qui est derriere lui.
        this.room[LightWindow.sectionIndex(11, CEILING, 8)] = this.neon;
        wallFacingTheFrame();
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone reference = zone(this.blocks);
        drain(reference);
        LightZone.Baked expected = publishedSection(reference);
        LightZone itself = zone(this.blocks);
        float[] corners = new float[AreaLight.VALUES];
        PortalOpenings.neonFace(corners, 11, CEILING, 8, 1.0f);
        float[] values = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writePassage(values, 0, FRAME, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        PortalOpenings.writeVirtual(values, 1, corners, DOWN, new float[] {SETTINGS.red(), SETTINGS.green(), SETTINGS.blue()}, 0);
        itself.openings(PortalOpenings.of(values, new LightZone[] {itself, null}, 2));
        drain(itself);
        LightZone.Baked crossed = publishedSection(itself);
        // Devant le cadre : le mur qui lui fait face, le sol et le plafond a ses pieds et au-dessus.
        int[] cells = {NEAR_FRAME, LightCell.index(6, 1, 8, UP), LightCell.index(7, 1, 8, UP), LightCell.index(7, CEILING - 1, 8, DOWN),
            LightCell.index(5, 1, 9, UP), LightCell.index(7, 1, 10, UP)};
        StringBuilder report = new StringBuilder();
        boolean close = true;
        for (int cell : cells) {
            float want = LightCell.red(expected.values()[cell]);
            float got = LightCell.red(crossed.values()[cell]);
            float wantDirect = LightCell.red(expected.values()[cell]) - LightCell.red(expected.indirect()[cell]);
            float gotDirect = LightCell.red(crossed.values()[cell]) - LightCell.red(crossed.indirect()[cell]);
            report.append(" [").append(cell).append(' ').append(got).append(" contre ").append(want).append(", direct ").append(gotDirect).append(" contre ")
                    .append(wantDirect).append(']');
            close &= Math.abs(got - want) <= want * 0.02f + 0.005f;
        }
        assertTrue(close, "Par le portail contre sans lui :" + report + ".");
    }

    /** Prend tout ce qu'une zone a publie, du proche et du loin. */
    private static void takeAll(LightZone zone) {
        for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
            assertNotNull(baked.values(), "Une section publiee a son eclairement.");
        }
        for (LightZone.FarBaked far = zone.pollFar(); far != null; far = zone.pollFar()) {
            assertNotNull(far.values(), "Une section du loin publiee a son eclairement.");
        }
    }

    /** La derniere section de l'ancrage publiee par une zone. */
    private static LightZone.Baked publishedSection(LightZone zone) {
        LightZone.Baked found = null;
        for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
            found = baked.key() == CENTER_SECTION ? baked : found;
        }
        assertNotNull(found, "La section de l'ancrage est publiee.");
        return found;
    }

    @Test
    @DisplayName("Le loin lit les neons vus par un portail")
    void farCountsVirtualNeons() {
        fill(LightZone.FAR_RADIUS + 1);
        LightZone zone = new LightZone(this.blocks, SETTINGS, 0, 2);
        zone.anchor(0, 0, 0);
        float[] corners = new float[AreaLight.VALUES];
        PortalOpenings.neonFace(corners, 5 * 16 + 11, CEILING, 8, 1.0f);
        float[] values = new float[PortalOpenings.VALUES * 2];
        PortalOpenings.writePassage(values, 0, new double[] {5 * 16 + 8.0, 2.0, 8.5}, HALF_WIDTH, HALF_HEIGHT, WEST, IDENTITY, FRAME);
        PortalOpenings.writeVirtual(values, 1, corners, DOWN, new float[] {2.0f, 2.0f, 2.0f}, 0);
        zone.openings(PortalOpenings.of(values, new LightZone[] {zone(this.other), null}, 2));
        drain(zone);
        long key = ChunkSectionPos.asLong(5, 0, 0);
        LightZone.FarBaked far = null;
        for (LightZone.FarBaked baked = zone.pollFar(); baked != null; baked = zone.pollFar()) {
            far = baked.key() == key ? baked : far;
        }
        assertNotNull(far, "La section du cadre est du loin.");
        int front = LightZone.farIndex(3, 1, 4, UP);
        assertTrue(LightCell.red(far.values()[front]) > AMBIENT + 0.1f, "Le loin devant le cadre recoit le neon vu par lui.");
    }
}
