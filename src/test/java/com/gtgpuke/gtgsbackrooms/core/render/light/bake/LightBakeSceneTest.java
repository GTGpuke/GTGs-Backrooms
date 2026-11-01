package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("Cuisson de la lumiere : une piece connue")
class LightBakeSceneTest {

    /** Sol en Y = 0, air de 1 a 4, plafond en Y = 5 : comme un layer du Level 0. */
    private static final int FLOOR = 0;
    private static final int CEILING = 5;
    private static final int UP = 2;
    private static final int DOWN = 3;
    private static final int EAST = 0;
    private static final int WEST = 1;
    private static final float AMBIENT = 0.1f;
    private static final LightBakeSettings SETTINGS = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f);

    private LightBlocks blocks;
    private byte stone;
    private byte yellow;
    private byte neon;
    private byte[] room;

    @BeforeEach
    void setUp() {
        this.blocks = new LightBlocks();
        this.stone = this.blocks.addMaterial(0.5f, 0.5f, 0.5f, false);
        this.yellow = this.blocks.addMaterial(0.9f, 0.9f, 0.1f, false);
        this.neon = this.blocks.addMaterial(1.0f, 1.0f, 1.0f, true);
        this.room = emptyRoom();
    }

    private byte[] emptyRoom() {
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (y == FLOOR || y >= CEILING) {
                        section[LightWindow.sectionIndex(x, y, z)] = this.stone;
                    }
                }
            }
        }
        return section;
    }

    private void set(byte[] section, int x, int y, int z, byte material) {
        section[LightWindow.sectionIndex(x, y, z)] = material;
    }

    /** La piece sur toutes les sections voulues : une rangee de sections en Y = 0, de l'opaque au-dessus. */
    private void fill(int radius, byte[] center) {
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                this.blocks.setSection(ChunkSectionPos.asLong(x, 0, z), x == 0 && z == 0 ? center : emptyRoom());
                this.blocks.setSection(ChunkSectionPos.asLong(x, 1, z), LightBlocks.UNLOADED);
            }
        }
    }

    private int[] bakeDirect(LightBakeSettings settings) {
        int[] out = new int[LightCell.VALUES];
        new SectionBaker().direct(this.blocks, settings, 0, 0, 0, out);
        return out;
    }

    @Test
    @DisplayName("Une dalle eclaire le sol juste dessous, de moins en moins loin d'elle, et rien sur le plafond qui la porte")
    void directLight() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(1, this.room);
        int[] direct = bakeDirect(SETTINGS);
        float under = LightCell.red(direct[LightCell.index(8, 1, 8, UP)]);
        float far = LightCell.red(direct[LightCell.index(8, 1, 15, UP)]);
        assertTrue(under > far && far > AMBIENT, "Sous la dalle " + under + ", loin " + far + ".");
        assertEquals(AMBIENT, LightCell.red(direct[LightCell.index(2, 4, 8, DOWN)]), 0.01f, "Le plafond ne recoit que le fond.");
        assertFalse(LightCell.valid(direct[LightCell.index(8, 2, 8, UP)]), "Pas de surface au milieu de l'air.");
        assertFalse(LightCell.valid(direct[LightCell.index(8, 1, 8, EAST)]), "Rien d'opaque de ce cote.");
        assertFalse(LightCell.valid(direct[LightCell.index(8, FLOOR, 8, UP)]), "Un bloc opaque n'a pas de cellule d'air.");
    }

    @Test
    @DisplayName("Seule la face du bas d'une dalle eclaire : ses cotes libres et son dessus ne donnent rien, et couverte par dessous elle n'eclaire plus")
    void onlyTheBottomFaceLights() {
        set(this.room, 8, 2, 8, this.neon);
        for (int y = 1; y < CEILING; y++) {
            set(this.room, 12, y, 8, this.stone);
        }
        fill(1, this.room);
        int[] hanging = bakeDirect(SETTINGS);
        assertTrue(LightCell.red(hanging[LightCell.index(7, 1, 8, UP)]) > AMBIENT + 0.5f, "Le sol sous elle est eclaire.");
        assertEquals(AMBIENT, LightCell.red(hanging[LightCell.index(11, 3, 8, WEST)]), 0.01f, "Rien sur un mur, au-dessus de son plan.");
        assertEquals(AMBIENT, LightCell.red(hanging[LightCell.index(8, CEILING - 1, 8, DOWN)]), 0.01f, "Rien vers le haut.");
        set(this.room, 8, 1, 8, this.stone);
        assertEquals(AMBIENT, LightCell.red(bakeDirect(SETTINGS)[LightCell.index(5, 1, 8, UP)]), 0.01f, "Couverte par dessous, elle n'eclaire plus.");
    }

    @Test
    @DisplayName("Un mur porte une ombre, la lumiere ne le traverse pas, et un neon mort ne cuit pas")
    void shadows() {
        set(this.room, 5, CEILING, 8, this.neon);
        fill(1, this.room);
        int[] open = bakeDirect(SETTINGS);
        for (int z = 0; z < 16; z++) {
            for (int y = 1; y < CEILING; y++) {
                set(this.room, 7, y, z, this.stone);
            }
        }
        int[] walled = bakeDirect(SETTINGS);
        float before = LightCell.red(open[LightCell.index(9, 1, 8, UP)]);
        assertTrue(before > AMBIENT + 0.02f, "Sans mur, la lumiere arrive : " + before + ".");
        assertEquals(AMBIENT, LightCell.red(walled[LightCell.index(9, 1, 8, UP)]), 0.01f, "Derriere le mur, le fond seul.");
        assertTrue(LightCell.red(walled[LightCell.index(5, 1, 8, UP)]) > before, "Du bon cote, la lumiere reste.");
        LightBakeSettings dead = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1, 0.0f, 1.0f, 0.0f);
        assertEquals(AMBIENT, LightCell.red(bakeDirect(dead)[LightCell.index(5, 1, 8, UP)]), 0.01f, "Neon mort : rien.");
    }

    @Test
    @DisplayName("Portee, face tournee, surface derriere la dalle : rien ne s'allume, et beaucoup de dalles passent")
    void lightsThatDoNotReach() {
        set(this.room, 2, CEILING, 2, this.neon);
        fill(1, this.room);
        LightBakeSettings shortReach = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 2.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f);
        assertEquals(AMBIENT, LightCell.red(bakeDirect(shortReach)[LightCell.index(14, 1, 14, UP)]), 0.01f, "Hors de portee.");
        // Une poutre sous la dalle : sa face du dessous regarde le sol, la dalle est derriere elle.
        set(this.room, 8, 3, 8, this.stone);
        set(this.room, 9, CEILING, 8, this.neon);
        assertEquals(AMBIENT, LightCell.red(bakeDirect(SETTINGS)[LightCell.index(8, 2, 8, DOWN)]), 0.01f, "Face tournee vers le bas.");
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                set(this.room, x, CEILING, z, this.neon);
            }
        }
        int[] many = new int[LightCell.VALUES];
        new SectionBaker().direct(this.blocks, SETTINGS, 0, 0, 0, many);
        assertTrue(LightCell.red(many[LightCell.index(4, 1, 4, UP)]) > 0.5f, "Plus de lumieres que la reserve de depart.");
    }

    @Test
    @DisplayName("Le rebond eclaire le plafond depuis le sol, et un mur colore teinte ce qui lui fait face")
    void bounceColors() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        int[] bounced = new int[LightCell.VALUES];
        baker.place(this.blocks, 0, 0, 0);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, bounced);
        int ceiling = LightCell.index(7, 4, 8, DOWN);
        assertTrue(LightCell.red(bounced[ceiling]) > LightCell.red(direct[ceiling]) + 0.02f, "Le plafond recoit le sol.");
        assertFalse(LightCell.valid(bounced[LightCell.index(8, 2, 8, UP)]), "Toujours pas de surface dans l'air.");
        byte[] colored = emptyRoom();
        set(colored, 8, CEILING, 8, this.neon);
        for (int z = 0; z < 16; z++) {
            for (int y = 1; y < CEILING; y++) {
                set(colored, 10, y, z, this.yellow);
            }
        }
        fill(1, colored);
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        sources[13] = direct;
        baker.place(this.blocks, 0, 0, 0);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, bounced);
        int facing = LightCell.index(7, 2, 8, EAST + 1);
        int wallFace = LightCell.index(5, 2, 8, EAST);
        assertFalse(LightCell.valid(bounced[facing]), "Pas de surface ici.");
        int floor = LightCell.index(9, 1, 8, UP);
        assertTrue(LightCell.red(bounced[floor]) - LightCell.red(direct[floor]) > (LightCell.blue(bounced[floor]) - LightCell.blue(direct[floor])) * 1.2f,
                "Pres du mur jaune, le rebond tire sur le jaune.");
        assertFalse(LightCell.valid(bounced[wallFace]), "Toujours aucune surface au milieu.");
    }

    @Test
    @DisplayName("Sans source ou sans surface touchee, le rebond n'ajoute rien, et un rayon perdu ne compte pas")
    void bounceWithoutSources() {
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[] out = new int[LightCell.VALUES];
        baker.place(this.blocks, 0, 0, 0);
        baker.bounce(this.blocks, 0, 0, 0, direct, new int[SectionBaker.neighbors()][], 0.0f, 1.0f, out);
        int floor = LightCell.index(8, 1, 8, UP);
        assertEquals(direct[floor], out[floor], "Aucune source.");
        int[][] silent = new int[SectionBaker.neighbors()][];
        silent[13] = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, silent, 0.0f, 1.0f, out);
        assertEquals(direct[floor], out[floor], "Une source sans valeur.");
        // Un sol dans le vide : la plupart des rayons se perdent, ceux qui restent ne comptent que ce qu'ils touchent.
        LightBlocks open = new LightBlocks();
        byte[] ground = new byte[LightBlocks.SECTION_BLOCKS];
        ground[LightWindow.sectionIndex(8, 0, 8)] = LightBlocks.UNKNOWN;
        for (int i = 0; i < SectionBaker.neighbors(); i++) {
            open.setSection(SectionBaker.neighborKey(i, 0, 0, 0), i == 13 ? ground : LightBlocks.EMPTY);
        }
        baker.direct(open, SETTINGS, 0, 0, 0, direct);
        int[][] self = new int[SectionBaker.neighbors()][];
        self[13] = direct;
        baker.bounce(open, 0, 0, 0, direct, self, 0.0f, 1.0f, out);
        assertEquals(direct[LightCell.index(8, 1, 8, UP)], out[LightCell.index(8, 1, 8, UP)], "Rien au-dessus du sol.");
    }

    @Test
    @DisplayName("Le dernier rebond assombrit les coins : le pied d'un mur perd plus que le milieu du sol")
    void cornersDarken() {
        byte[] walled = emptyRoom();
        set(walled, 8, CEILING, 8, this.neon);
        for (int z = 0; z < 16; z++) {
            for (int y = 1; y < CEILING; y++) {
                set(walled, 12, y, z, this.stone);
            }
        }
        fill(1, walled);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        int[] open = new int[LightCell.VALUES];
        int[] dark = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, open);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 1.0f, 1.0f, dark);
        int corner = LightCell.index(11, 1, 8, UP);
        int middle = LightCell.index(6, 1, 8, UP);
        float cornerLoss = LightCell.red(dark[corner]) / LightCell.red(open[corner]);
        float middleLoss = LightCell.red(dark[middle]) / LightCell.red(open[middle]);
        assertTrue(cornerLoss < middleLoss - 0.1f, "Le coin garde " + cornerLoss + ", le milieu " + middleLoss + ".");
        assertTrue(middleLoss > 0.9f, "Loin de tout, presque rien ne se perd : " + middleLoss + ".");
    }

    @Test
    @DisplayName("Les indices des sections voisines se relisent dans les deux sens")
    void neighbors() {
        assertEquals(13, SectionBaker.neighbor(5, 6, 7, 5, 6, 7), "Le centre.");
        for (int i = 0; i < SectionBaker.neighbors(); i++) {
            long key = SectionBaker.neighborKey(i, 5, 6, 7);
            assertEquals(i, SectionBaker.neighbor(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key), 5, 6, 7), "Indice " + i + ".");
        }
    }

    private LightZone zone() {
        LightZone zone = new LightZone(this.blocks, SETTINGS, 0, 2);
        zone.anchor(0, 0, 0);
        return zone;
    }

    private static int drain(LightZone zone) {
        int steps = 0;
        while (zone.step()) {
            steps++;
        }
        return steps;
    }

    private static int published(LightZone zone) {
        int count = 0;
        while (zone.poll() != null) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("La zone cuit d'abord le plus proche, attend ce qui manque, puis publie une fois chaque section")
    void zoneBakes() {
        set(this.room, 8, CEILING, 8, this.neon);
        LightZone zone = zone();
        assertTrue(zone.blocks() == this.blocks, "Sa copie.");
        assertFalse(zone.step(), "Rien de copie : rien a cuire.");
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        assertTrue(zone.step(), "Tout est copie.");
        LightZone.Baked first = zone.poll();
        assertNotNull(first, "Une section publiee.");
        assertEquals(ChunkSectionPos.asLong(0, 0, 0), first.key(), "La plus proche d'abord.");
        assertTrue(LightCell.red(first.values()[LightCell.index(7, 4, 8, DOWN)]) > AMBIENT + 0.02f, "Deja avec son rebond.");
        assertNull(zone.poll(), "Une section par pas.");
        int expected = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT - 1;
        assertEquals(expected, drain(zone), "Toute la fenetre.");
        assertEquals(expected, published(zone), "Chacune publiee.");
        assertFalse(zone.step(), "Tout est cuit.");
    }

    @Test
    @DisplayName("Un bloc change recuit ses voisines, et le dernier rebond des voisines de celles-ci, sans toucher au reste")
    void zoneRebakes() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        LightZone zone = zone();
        drain(zone);
        published(zone);
        zone.onChanged(ChunkSectionPos.asLong(0, 0, 0));
        int near = 3 * 3 * 2;
        int ring = 5 * 5 * 2 - near;
        assertEquals(near + ring, drain(zone), "Deux sections autour.");
        assertEquals(near + ring, published(zone), "Republiees.");
        zone.onChanged(ChunkSectionPos.asLong(100, 0, 100));
        assertFalse(zone.step(), "Rien a ces coordonnees.");
        zone.republish();
        int all = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT;
        assertEquals(all, drain(zone), "Republier relit ce qui est cuit.");
        assertEquals(all, published(zone), "Tout repart.");
    }

    @Test
    @DisplayName("La zone suit son ancrage, oublie ce qui s'eloigne dans chaque direction, et ne cuit rien hors du monde")
    void zoneMoves() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN + 12, this.room);
        LightZone zone = zone();
        drain(zone);
        published(zone);
        int all = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT;
        for (int[] move : new int[][] {{12, 0, 0}, {-12, 0, 0}, {0, 0, 12}, {0, 0, -12}}) {
            zone.anchor(move[0], 0, move[2]);
            assertEquals(all, drain(zone), "Une toute nouvelle fenetre.");
            assertEquals(all, published(zone), "Publiee.");
        }
        // Hauteurs : d'abord loin en dessous (les sections du haut partent), puis loin au-dessus (celles du bas partent).
        zone.anchor(-0, -6, -12);
        assertFalse(zone.step(), "Sous le monde : rien.");
        zone.anchor(-0, 6, -12);
        assertFalse(zone.step(), "Au-dessus du monde : rien.");
        zone.anchor(0, 1, -12);
        assertTrue(drain(zone) > 0, "De retour : une partie de la fenetre est dans le monde.");
    }

    @Test
    @DisplayName("Une section oubliee par le rendu repart quand la fenetre revient sur elle, sans etre recuite")
    void forgottenSectionComesBack() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN + 3, this.room);
        LightZone zone = zone();
        drain(zone);
        published(zone);
        assertTrue(zone.idle(), "Tout est au repos.");
        long key = ChunkSectionPos.asLong(LightZone.RADIUS, 0, 0);
        assertFalse(zone.step(), "Rien a publier.");
        zone.forget(key);
        assertFalse(zone.idle(), "L'oubli attend le thread de cuisson.");
        assertTrue(zone.step(), "La section repart.");
        LightZone.Baked baked = zone.poll();
        assertNotNull(baked, "Publiee.");
        assertEquals(key, baked.key(), "Elle seule.");
        assertFalse(zone.step(), "Une seule fois.");
        zone.forget(ChunkSectionPos.asLong(100, 0, 100));
        assertFalse(zone.step(), "Une section que la zone ne tient plus ne compte pas.");
    }

    @Test
    @DisplayName("Une section publiee donne a part ses rebonds, ses surfaces et ses neons, les plus proches d'abord")
    void bakedSectionForTheCache() {
        set(this.room, 8, CEILING, 8, this.neon);
        set(this.room, 2, CEILING, 2, this.neon);
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        LightZone zone = zone();
        assertTrue(zone.settings() == SETTINGS, "Ses reglages.");
        assertTrue(zone.step(), "Une section.");
        LightZone.Baked baked = zone.poll();
        assertNotNull(baked, "Publiee.");
        int floor = LightCell.index(8, 1, 8, UP);
        assertTrue(LightCell.red(baked.indirect()[floor]) < LightCell.red(baked.values()[floor]) - 0.1f, "Sous la dalle, l'indirect est bien moindre que le total.");
        assertTrue(LightCell.red(baked.indirect()[floor]) >= AMBIENT - 0.01f, "Il garde le fond.");
        assertEquals(baked.values().length, baked.indirect().length, "Meme forme.");
        assertFalse(LightCell.valid(baked.indirect()[LightCell.index(8, 2, 8, UP)]), "Pas de surface : rien.");
        assertTrue(baked.surfaces().length > 200, "Sol et plafond de la piece : " + baked.surfaces().length + ".");
        int surface = baked.surfaces()[0];
        assertEquals(this.stone & 0xFF, SurfaceAtlas.material(surface), "La matiere du bloc qui porte la surface.");
        assertTrue(LightCell.valid(baked.values()[LightCell.index(SurfaceAtlas.cell(surface) & 15, SurfaceAtlas.cell(surface) >> 4 & 15, SurfaceAtlas.cell(surface) >> 8,
                SurfaceAtlas.face(surface))]), "Une surface liste bien une valeur.");
        assertEquals(8, baked.lights()[0], "Le neon le plus proche du centre de la section, X.");
        assertEquals(CEILING, baked.lights()[1], "Son Y.");
        assertEquals(8, baked.lights()[2], "Son Z.");
        assertEquals(DOWN, baked.lights()[3], "Il eclaire vers le bas.");
    }

    @Test
    @DisplayName("La part des rebonds retire de la lumiere ce que les surfaces se renvoient, et rien de la lumiere directe")
    void bounceShare() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        int[] full = new int[LightCell.VALUES];
        int[] half = new int[LightCell.VALUES];
        int[] none = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, full);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 0.5f, half);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 0.0f, none);
        int surface = LightCell.index(7, 4, 8, DOWN);
        float alone = LightCell.red(direct[surface]);
        assertEquals(alone, LightCell.red(none[surface]), 0.01f, "Sans rebond, la lumiere directe seule.");
        assertTrue(LightCell.red(full[surface]) > alone + 0.02f, "Les rebonds ajoutent.");
        assertEquals((LightCell.red(full[surface]) + alone) / 2.0f, LightCell.red(half[surface]), 0.01f, "Moitie de ce qu'ils ajoutent.");
    }

    @Test
    @DisplayName("Une section cuite dit quel changement elle connaissait : plus vieille qu'un bloc change, elle est perimee")
    void bakedStamp() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        LightZone zone = zone();
        assertNull(zone.poll(), "Rien de cuit.");
        assertTrue(zone.idle(), "Rien en cours.");
        assertFalse(zone.inWorld(-1), "Sous le monde.");
        assertTrue(zone.inWorld(0), "Premiere section.");
        assertTrue(zone.inWorld(1), "Derniere section.");
        assertFalse(zone.inWorld(2), "Au-dessus du monde.");
        long first = zone.onChanged(ChunkSectionPos.asLong(0, 0, 0));
        assertEquals(1L, first, "Le premier changement.");
        assertFalse(zone.idle(), "Un changement attend.");
        assertTrue(zone.step(), "Une section cuit.");
        assertFalse(zone.idle(), "Elle attend d'etre prise, et d'autres suivent.");
        LightZone.Baked baked = zone.poll();
        assertNotNull(baked, "Cuite.");
        assertEquals(first, baked.stamp(), "Elle connait ce changement.");
        assertEquals(first + 1L, zone.onChanged(ChunkSectionPos.asLong(0, 0, 0)), "Le suivant.");
        assertTrue(baked.stamp() < first + 1L, "Elle ne le connait pas.");
        drain(zone);
        assertFalse(zone.idle(), "Tout est cuit, rien n'a ete pris.");
        published(zone);
        assertTrue(zone.idle(), "Tout est cuit et pris.");
    }

    @Test
    @DisplayName("La fenetre ne bouge pas tant que la camera reste loin de ses bords : un saut ne la deplace pas")
    void baseHoldsNearItsMiddle() {
        assertEquals(-1, LightZone.baseFor(Integer.MIN_VALUE, 2), "Premiere base : huit blocs de part et d'autre.");
        assertEquals(0, LightZone.baseFor(Integer.MIN_VALUE, 12), "Plus haut.");
        assertEquals(-1, LightZone.baseFor(-1, 8), "Y = 8 appellerait la base 0 : la precedente tient encore, a plus de quatre blocs du bord.");
        assertEquals(0, LightZone.baseFor(0, 7), "Et dans l'autre sens.");
        assertEquals(0, LightZone.baseFor(0, 4), "Le dernier bloc tenu en bas.");
        assertEquals(-1, LightZone.baseFor(0, 3), "Trop pres du bas.");
        assertEquals(0, LightZone.baseFor(0, 27), "Le dernier bloc tenu en haut.");
        assertEquals(1, LightZone.baseFor(0, 28), "Trop pres du haut.");
    }

    @Test
    @DisplayName("Le cache recoit les neons les plus proches de la section, chaque tuile y prenant les siens")
    void nearLightsAreLimited() {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                set(this.room, x, CEILING, z, this.neon);
            }
        }
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, new int[LightCell.VALUES]);
        int[] near = baker.nearLights(0, 0, 0);
        assertEquals(SectionBaker.MAX_NEAR_LIGHTS * SectionBaker.LIGHT_INTS, near.length, "Le maximum.");
    }

    @Test
    @DisplayName("Dans un monde plus haut, seules les sections proches de la fenetre comptent, et un changement sous elle touche le dernier rebond")
    void zoneInTallWorld() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        LightZone zone = new LightZone(this.blocks, SETTINGS, 0, 8);
        zone.anchor(0, 0, 0);
        for (int z = -LightZone.RADIUS - 1; z <= LightZone.RADIUS + 1; z++) {
            for (int x = -LightZone.RADIUS - 1; x <= LightZone.RADIUS + 1; x++) {
                this.blocks.setSection(ChunkSectionPos.asLong(x, 2, z), LightBlocks.UNLOADED);
            }
        }
        assertEquals((2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT, drain(zone), "Rien n'attend ce qui n'est pas copie au-dessus.");
        published(zone);
        zone.onChanged(ChunkSectionPos.asLong(0, -1, 0));
        assertEquals(25 + 25, drain(zone), "Une voisine sous la fenetre : la rangee du bas, et le dernier rebond de celle du haut.");
        published(zone);
        zone.anchor(0, 4, 0);
        assertFalse(zone.step(), "Fenetre pas encore copiee.");
        zone.anchor(0, 7, 0);
        assertFalse(zone.step(), "Tout est oublie par le haut.");
        this.blocks.setSection(ChunkSectionPos.asLong(0, 7, 0), this.room);
        zone.anchor(0, -6, 0);
        assertFalse(zone.step(), "Sous le monde.");
    }

    @Test
    @DisplayName("Une section dont une voisine de la zone manque attend, meme toute seule au bord de ce qui est copie")
    void zoneWaitsForNeighbors() {
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        this.blocks.removeSection(ChunkSectionPos.asLong(1, 0, 1));
        LightZone zone = zone();
        drain(zone);
        published(zone);
        assertNull(zone.poll(), "Tout est lu.");
        this.blocks.setSection(ChunkSectionPos.asLong(1, 0, 1), this.room);
        zone.onChanged(ChunkSectionPos.asLong(1, 0, 1));
        assertTrue(drain(zone) > 0, "La section arrive : ce qui attendait cuit.");
    }

    @Test
    @DisplayName("Les reglages viennent du profil, et le calendrier decide des neons morts")
    void settingsFromProfile() {
        LookProfile profile = LookProfile.fromJson(JsonParser.parseString("{ \"dimension\": \"m:d\", \"id\": 1,"
                + " \"neons\": { \"block\": \"m:tube\", \"color\": [0.5, 1, 0.25], \"dead\": 1 },"
                + " \"baked_light\": { \"emission\": 4, \"ambient\": [0.1, 0.2, 0.3], \"reach\": 9, \"size\": 2, \"occlusion\": 0.5 } }").getAsJsonObject());
        LightBakeSettings settings = LightBakeSettings.of(profile, Identifier.of("m", "d"));
        assertEquals(2.0f, settings.red(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(4.0f, settings.green(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(1.0f, settings.blue(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(0.2f, settings.ambientGreen(), 1.0e-6f, "Fond.");
        assertEquals(9.0f, settings.reach(), 1.0e-6f, "Portee.");
        assertEquals(2.0f, settings.size(), 1.0e-6f, "Taille.");
        assertEquals(0.0f, settings.cone(), 1.0e-6f, "Tout le demi-espace devant la dalle.");
        assertEquals(0.5f, settings.occlusion(), 1.0e-6f, "Coins.");
        assertFalse(settings.lit(1, 2, 3), "Tous morts.");
        assertTrue(new LightBakeSettings(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f).lit(1, 2, 3), "Aucun mort.");
        assertFalse(new LightBakeSettings(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1, 0.0f, 0.0f, 1.0f).lit(1, 2, 3), "Secteur en panne.");
    }
}
