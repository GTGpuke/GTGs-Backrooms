package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
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
    private static final LightBakeSettings SETTINGS = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f);
    /** Comme un profil : coins assombris, rebonds teintes et rebonds suivants comptes. */
    private static final LightBakeSettings PROFILE_LIKE = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.6f, 0.95f,
            1.03f, 1.0f, 0.72f, SectionBaker.FOLLOWING, 1, 0.0f, 0.0f, 0.0f);
    /** Le meme, sans coins assombris. */
    private static final LightBakeSettings PROFILE_OPEN = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.95f,
            1.03f, 1.0f, 0.72f, SectionBaker.FOLLOWING, 1, 0.0f, 0.0f, 0.0f);

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
        LightBakeSettings dead = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1,
                0.0f, 1.0f, 0.0f);
        assertEquals(AMBIENT, LightCell.red(bakeDirect(dead)[LightCell.index(5, 1, 8, UP)]), 0.01f, "Neon mort : rien.");
    }

    @Test
    @DisplayName("Portee, face tournee, surface derriere la dalle : rien ne s'allume, et beaucoup de dalles passent")
    void lightsThatDoNotReach() {
        set(this.room, 2, CEILING, 2, this.neon);
        fill(1, this.room);
        LightBakeSettings shortReach = new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 2.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1, 0.0f,
                0.0f, 0.0f);
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
    @DisplayName("Le dernier rebond estime les suivants comme les rebonds traces : ce que le second ajoute au premier compte deux fois")
    void lastBounceCountsTheFollowing() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        baker.place(this.blocks, 0, 0, 0);
        int[] first = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, first);
        sources[13] = first;
        int[] second = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, second);
        int[] all = new int[LightCell.VALUES];
        baker.keepFollowing(first, SectionBaker.FOLLOWING);
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, all);
        int surface = LightCell.index(7, 4, 8, DOWN);
        float once = LightCell.red(first[surface]) - LightCell.red(direct[surface]);
        float twice = LightCell.red(second[surface]) - LightCell.red(direct[surface]);
        assertTrue(twice > once + 0.01f, "Le second rebond ajoute : " + once + " puis " + twice + ".");
        assertEquals(once + (twice - once) * SectionBaker.FOLLOWING, LightCell.red(all[surface]) - LightCell.red(direct[surface]), 0.02f,
                "Les suivants valent le second.");
        int[] again = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, again);
        assertEquals(second[surface], again[surface], "L'estimation ne vaut que pour le rebond qui la suit.");
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

    private static int farPublished(LightZone zone) {
        int count = 0;
        while (zone.pollFar() != null) {
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
            assertTrue(drain(zone) > all, "Une toute nouvelle fenetre, et son loin.");
            assertEquals(all, published(zone), "La fenetre publiee.");
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
        farPublished(zone);
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
    @DisplayName("La couleur des rebonds du profil colore ce que les surfaces se renvoient, et rien de la lumiere directe")
    void bounceColor() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(1, this.room);
        SectionBaker baker = new SectionBaker();
        int[] direct = new int[LightCell.VALUES];
        baker.direct(this.blocks, SETTINGS, 0, 0, 0, direct);
        int[][] sources = new int[SectionBaker.neighbors()][];
        sources[13] = direct;
        int[] white = new int[LightCell.VALUES];
        int[] amber = new int[LightCell.VALUES];
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, white);
        baker.bounceColor(new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.5f, 1.0f, 0.5f, 1.0f, 1, 0.0f, 0.0f, 0.0f));
        baker.bounce(this.blocks, 0, 0, 0, direct, sources, 0.0f, 1.0f, amber);
        int surface = LightCell.index(7, 4, 8, DOWN);
        float alone = LightCell.green(direct[surface]);
        float added = LightCell.green(white[surface]) - alone;
        assertTrue(added > 0.02f, "Les rebonds ajoutent : " + added + ".");
        assertEquals(added * 1.5f, LightCell.red(amber[surface]) - LightCell.red(direct[surface]), 0.02f, "Plus de rouge.");
        assertEquals(added, LightCell.green(amber[surface]) - alone, 0.02f, "Autant de vert.");
        assertEquals(added * 0.5f, LightCell.blue(amber[surface]) - LightCell.blue(direct[surface]), 0.02f, "Moins de bleu.");
    }

    @Test
    @DisplayName("Une zone cuit avec la couleur de rebonds de ses reglages")
    void zoneBouncesInColor() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        LightZone zone = new LightZone(this.blocks, new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
                0.25f, 1.0f, 1, 0.0f, 0.0f,
                0.0f), 0, 2);
        zone.anchor(0, 0, 0);
        int ceiling = 0;
        boolean working = true;
        while (working) {
            working = zone.step();
            for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
                if (baked.key() == ChunkSectionPos.asLong(0, 0, 0)) {
                    ceiling = baked.values()[LightCell.index(7, 4, 8, DOWN)];
                }
            }
        }
        assertTrue(LightCell.green(ceiling) > AMBIENT + 0.02f, "Le plafond ne vit que de rebonds.");
        assertTrue(LightCell.blue(ceiling) < LightCell.green(ceiling) * 0.9f, "Ils ont la couleur des reglages de la zone.");
    }

    @Test
    @DisplayName("La zone compte aussi les rebonds qui suivent le second ; reglee a 1, ses deux rebonds seulement")
    void zoneCountsTheFollowing() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN, this.room);
        float plain = LightCell.green(bakedCeiling(1.0f));
        float traced = LightCell.green(bakedCeiling(SectionBaker.FOLLOWING));
        assertTrue(plain > AMBIENT + 0.02f, "Le plafond ne vit que de rebonds : " + plain + ".");
        assertTrue(traced > plain + 0.005f, "Les rebonds suivants l'eclairent davantage : " + traced + " contre " + plain + ".");
    }

    private int bakedCeiling(float following) {
        LightZone zone = new LightZone(this.blocks, new LightBakeSettings(2.0f, 2.0f, 2.0f, AMBIENT, AMBIENT, AMBIENT, 14.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
                1.0f, following, 1, 0.0f, 0.0f, 0.0f), 0, 2);
        zone.anchor(0, 0, 0);
        int ceiling = 0;
        boolean working = true;
        while (working) {
            working = zone.step();
            for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
                if (baked.key() == ChunkSectionPos.asLong(0, 0, 0)) {
                    ceiling = baked.values()[LightCell.index(7, 4, 8, DOWN)];
                }
            }
        }
        return ceiling;
    }

    @Test
    @DisplayName("Une section vaut la meme chose quel que soit l'ordre de cuisson de ses voisines : son bord ne lit jamais une voisine deja finie")
    void sectionIgnoresBakingOrder() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(LightZone.RADIUS + LightZone.COPY_MARGIN + 1, emptyRoom());
        this.blocks.setSection(ChunkSectionPos.asLong(0, 0, 0), this.room);
        this.blocks.setSection(ChunkSectionPos.asLong(1, 0, 0), this.room);
        int[] first = bakedOrigin(0);
        int[] later = bakedOrigin(1);
        assertNotNull(first, "La section de l'origine est cuite.");
        assertArrayEquals(first, later, "La section cuite apres sa voisine lit ses rebonds en trop.");
    }

    /** La section de l'origine, cuite par une zone ancree ici : la plus proche de l'ancrage cuit d'abord. */
    private int @Nullable [] bakedOrigin(int anchorX) {
        LightZone zone = new LightZone(this.blocks, SETTINGS, 0, 2);
        zone.anchor(anchorX, 0, 0);
        int[] values = null;
        boolean working = true;
        while (working) {
            working = zone.step();
            for (LightZone.Baked baked = zone.poll(); baked != null; baked = zone.poll()) {
                if (baked.key() == ChunkSectionPos.asLong(0, 0, 0)) {
                    values = baked.values();
                }
            }
        }
        return values;
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
        assertFalse(zone.idle(), "Le loin attend d'etre pris.");
        farPublished(zone);
        assertTrue(zone.idle(), "Tout est cuit et pris.");
    }

    @Test
    @DisplayName("Le loin d'une section moyenne les surfaces de chaque cellule de deux blocs, laisse vide une cellule sans surface et se relit a pleine resolution")
    void farCells() {
        int[] values = new int[LightCell.VALUES];
        values[LightCell.index(0, 3, 0, UP)] = LightCell.pack(0.2f, 0.4f, 0.6f);
        values[LightCell.index(1, 3, 1, UP)] = LightCell.pack(0.4f, 0.4f, 0.2f);
        int[] far = new int[LightZone.FAR_VALUES];
        LightZone.reduce(values, far, LightCell.FACES);
        int cell = far[LightZone.farIndex(0, 3, 0, LightCell.FACES + UP)];
        assertTrue(LightCell.valid(cell), "Deux surfaces sur quatre.");
        assertEquals(0.3f, LightCell.red(cell), 0.01f, "Leur moyenne.");
        assertEquals(0.4f, LightCell.green(cell), 0.01f, "Leur moyenne.");
        assertEquals(0.4f, LightCell.blue(cell), 0.01f, "Leur moyenne.");
        assertEquals(0, far[LightZone.farIndex(0, 3, 0, UP)], "Les tranches d'avant ne sont pas touchees.");
        assertEquals(0, far[LightZone.farIndex(1, 3, 0, LightCell.FACES + UP)], "Sans surface : vide.");
        int[] state = LightZone.reduced(values);
        assertEquals(LightZone.FAR_STATE_VALUES, state.length, "Une tranche par face.");
        int[] full = LightZone.expand(state, new int[LightCell.VALUES]);
        assertEquals(state[LightZone.farIndex(0, 3, 0, UP)], full[LightCell.index(1, 3, 0, UP)], "Chaque bloc prend la valeur de sa cellule.");
        assertEquals(0, full[LightCell.index(2, 3, 0, UP)], "La cellule voisine, vide, le reste.");
        assertEquals(LightZone.FAR_VALUES - 1, LightZone.farIndex(LightZone.FAR_CELLS - 1, LightZone.FAR_ROWS - 1, LightZone.FAR_CELLS - 1,
                LightZone.FAR_LAYERS - 1), "La derniere cellule.");
        assertEquals(1, LightZone.kindOf(UP), "Un sol.");
        assertEquals(2, LightZone.kindOf(DOWN), "Un plafond.");
        assertEquals(0, LightZone.kindOf(EAST), "Un mur.");
    }

    private static List<LightZone.FarBaked> farBaked(LightZone zone) {
        List<LightZone.FarBaked> all = new ArrayList<>();
        for (LightZone.FarBaked far = zone.pollFar(); far != null; far = zone.pollFar()) {
            all.add(far);
        }
        return all;
    }

    @Test
    @DisplayName("Le loin vient de la fenetre a sa publication, puis cuit autour d'elle en trois etats, du plus proche au plus loin, une fois chaque section")
    void farBakes() {
        set(this.room, 8, CEILING, 8, this.neon);
        fill(LightZone.FAR_RADIUS + 1, this.room);
        LightZone zone = zone();
        assertTrue(zone.step(), "La section de l'ancrage.");
        LightZone.FarBaked center = zone.pollFar();
        assertNotNull(center, "Son loin part avec elle.");
        assertEquals(ChunkSectionPos.asLong(0, 0, 0), center.key(), "Le sien.");
        LightZone.Baked near = zone.poll();
        assertNotNull(near, "Publiee.");
        int[] exact = new int[LightZone.FAR_VALUES];
        LightZone.reduce(near.values(), exact, 0);
        LightZone.reduce(near.indirect(), exact, LightCell.FACES);
        assertArrayEquals(exact, center.values(), "Le loin d'une section de la fenetre est son propre eclairement, reduit.");
        int under = LightZone.farIndex(4, 1, 4, UP);
        assertTrue(LightCell.red(center.values()[under]) > LightCell.red(center.values()[LightZone.farIndex(0, 1, 0, UP)]) + 0.1f,
                "Le sol sous la dalle est plus clair que le coin.");
        assertTrue(LightCell.red(center.values()[under + LightCell.FACES * LightZone.FAR_FACE_VALUES]) < LightCell.red(center.values()[under]) - 0.1f,
                "Sa part indirecte est bien moindre que son eclairement.");
        int window = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT;
        for (int i = 1; i < window; i++) {
            assertTrue(zone.step(), "La fenetre d'abord.");
        }
        assertEquals(window - 1, published(zone), "Toute la fenetre.");
        assertEquals(window - 1, farPublished(zone), "Avec son loin.");
        int all = (2 * LightZone.FAR_RADIUS + 1) * (2 * LightZone.FAR_RADIUS + 1) * LightZone.HEIGHT;
        assertEquals(3 * (all - window), drain(zone), "Trois etats par section du loin : lumiere directe, premier rebond, le reste.");
        assertNull(zone.poll(), "Rien pour la fenetre.");
        List<LightZone.FarBaked> far = farBaked(zone);
        assertEquals(all - window, far.size(), "Chacune publiee une fois.");
        long first = far.get(0).key();
        assertEquals(LightZone.RADIUS + 1, Math.max(Math.abs(ChunkSectionPos.unpackX(first)), Math.abs(ChunkSectionPos.unpackZ(first))),
                "Les plus proches d'abord.");
    }

    @Test
    @DisplayName("Un bloc change recuit le loin autour de lui, d'un etat de moins par section d'ecart ; un loin oublie ou republie repart, un loin quitte puis retrouve recuit")
    void farFollowsChanges() {
        fill(LightZone.FAR_RADIUS + 1, this.room);
        LightZone zone = zone();
        drain(zone);
        published(zone);
        farPublished(zone);
        zone.onChanged(ChunkSectionPos.asLong(7, 0, 0));
        assertEquals(128, drain(zone), "Trois etats a moins d'une section, deux a deux sections, un a trois, sur les deux rangees.");
        assertEquals(70, farPublished(zone), "Republiees.");
        long key = ChunkSectionPos.asLong(LightZone.FAR_RADIUS, 1, LightZone.FAR_RADIUS);
        zone.forgetFar(key);
        zone.forgetFar(ChunkSectionPos.asLong(100, 0, 100));
        assertFalse(zone.idle(), "L'oubli attend le thread de cuisson.");
        assertTrue(zone.step(), "Le loin repart, sans recuire ses premiers etats.");
        LightZone.FarBaked again = zone.pollFar();
        assertNotNull(again, "Publie.");
        assertEquals(key, again.key(), "Lui seul.");
        assertFalse(zone.step(), "Une seule fois.");
        int window = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT;
        int all = (2 * LightZone.FAR_RADIUS + 1) * (2 * LightZone.FAR_RADIUS + 1) * LightZone.HEIGHT;
        zone.republish();
        assertEquals(all, drain(zone), "Republier relit la fenetre et refait le dernier etat du loin.");
        assertEquals(window, published(zone), "La fenetre.");
        assertEquals(all, farPublished(zone), "Et tout le loin.");
        zone.anchor(40, 0, 0);
        assertFalse(zone.step(), "Rien de copie la-bas.");
        zone.anchor(0, 0, 0);
        assertEquals(window + 3 * (all - window), drain(zone), "De retour : ce qui a ete quitte recuit en entier.");
        assertEquals(all, farPublished(zone), "Tout le loin.");
    }

    /** Une piece eclairee par section : une dalle au plafond et un pilier, sur toutes les sections voulues. */
    private void fillLit(int radius) {
        set(this.room, 8, CEILING, 8, this.neon);
        for (int y = 1; y < CEILING; y++) {
            set(this.room, 3, y, 3, this.yellow);
        }
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                this.blocks.setSection(ChunkSectionPos.asLong(x, 0, z), this.room);
                this.blocks.setSection(ChunkSectionPos.asLong(x, 1, z), LightBlocks.UNLOADED);
            }
        }
    }

    @Test
    @DisplayName("Le loin d'une section vaut, a peu pres, ce qu'elle vaut dans la fenetre : eclairement et part indirecte")
    void farMatchesZone() {
        fillLit(LightZone.FAR_RADIUS + 1);
        long key = ChunkSectionPos.asLong(5, 0, 0);
        LightZone zone = new LightZone(this.blocks, PROFILE_LIKE, 0, 2);
        zone.anchor(0, 0, 0);
        drain(zone);
        LightZone.FarBaked far = null;
        for (LightZone.FarBaked baked : farBaked(zone)) {
            far = baked.key() == key ? baked : far;
        }
        assertNotNull(far, "La section est du loin.");
        LightZone near = new LightZone(this.blocks, PROFILE_LIKE, 0, 2);
        near.anchor(5, 0, 0);
        assertTrue(near.step(), "La meme section, au milieu d'une fenetre.");
        LightZone.Baked baked = near.poll();
        assertNotNull(baked, "Publiee.");
        int[] exact = new int[LightZone.FAR_VALUES];
        LightZone.reduce(baked.values(), exact, 0);
        LightZone.reduce(baked.indirect(), exact, LightCell.FACES);
        for (int layer = 0; layer < 2; layer++) {
            double farSum = 0.0;
            double exactSum = 0.0;
            int worst = 0;
            int cells = 0;
            for (int i = layer * LightCell.FACES * LightZone.FAR_FACE_VALUES; i < (layer + 1) * LightCell.FACES * LightZone.FAR_FACE_VALUES; i++) {
                if (!LightCell.valid(exact[i])) {
                    continue;
                }
                assertTrue(LightCell.valid(far.values()[i]), "Les memes surfaces.");
                float a = LightCell.red(far.values()[i]) + LightCell.green(far.values()[i]) + LightCell.blue(far.values()[i]);
                float b = LightCell.red(exact[i]) + LightCell.green(exact[i]) + LightCell.blue(exact[i]);
                farSum += a;
                exactSum += b;
                worst += Math.abs(a - b) > 0.25f * b + 0.02f ? 1 : 0;
                cells++;
            }
            String what = layer == 0 ? "Eclairement" : "Part indirecte";
            assertEquals(1.0, farSum / exactSum, 0.05, what + " : le loin vaut la fenetre, en moyenne.");
            assertTrue(worst < cells / 10, what + " : " + worst + " cellules sur " + cells + " a plus de 25 % de la fenetre.");
        }
    }

    @Test
    @DisplayName("La piece moyenne se mesure sur ce qui est publie : rien avant, un sol sous une dalle surtout direct, un plafond indirect, les murs du pilier")
    void roomIsMeasured() {
        fillLit(LightZone.RADIUS + LightZone.COPY_MARGIN);
        LightZone zone = zone();
        assertNull(zone.room(), "Rien avant la premiere section.");
        assertTrue(zone.step(), "La section de l'ancrage.");
        float[] room = zone.room();
        assertNotNull(room, "Mesuree.");
        assertEquals(LightZone.ROOM_VALUES, room.length, "Trois genres, l'eclairement et sa part indirecte, en couleur.");
        assertTrue(room[6] > room[9] + 0.05f, "Un sol sous une dalle recoit surtout de la lumiere directe : " + room[6] + " contre " + room[9] + ".");
        assertEquals(room[12], room[15], 0.02f, "Un plafond ne recoit que le fond et les rebonds.");
        assertTrue(room[12] > AMBIENT, "Le plafond recoit les rebonds du sol.");
        assertTrue(room[0] > AMBIENT, "Les murs du pilier comptent.");
        drain(zone);
        assertNotNull(zone.room(), "Toujours mesuree.");
    }

    /** Le loin publie d'une section, cuit autour de l'origine avec ces reglages : la cuisson s'arrete des qu'il part. */
    private int[] farOf(LightBakeSettings settings, long key) {
        LightZone zone = new LightZone(this.blocks, settings, 0, 2);
        zone.anchor(0, 0, 0);
        while (zone.step()) {
            for (LightZone.FarBaked far = zone.pollFar(); far != null; far = zone.pollFar()) {
                if (far.key() == key) {
                    return far.values();
                }
            }
        }
        throw new AssertionError("Loin jamais publie.");
    }

    @Test
    @DisplayName("Le loin assombrit les coins comme la fenetre : au pied d'un pilier, sa surface est plus sombre avec l'occlusion du profil que sans")
    void farDarkensCorners() {
        fillLit(LightZone.FAR_RADIUS + 1);
        long key = ChunkSectionPos.asLong(LightZone.RADIUS + 1, 0, 0);
        int corner = LightZone.farIndex(1, 1, 1, UP);
        int dark = farOf(PROFILE_LIKE, key)[corner];
        int open = farOf(PROFILE_OPEN, key)[corner];
        assertTrue(LightCell.valid(dark) && LightCell.valid(open), "Le sol au pied du pilier.");
        assertTrue(luma(LightCell.red(dark), LightCell.green(dark), LightCell.blue(dark)) < 0.97f * luma(LightCell.red(open), LightCell.green(open), LightCell.blue(open)),
                "Le coin s'assombrit.");
    }

    private static float luma(float red, float green, float blue) {
        return 0.299f * red + 0.587f * green + 0.114f * blue;
    }

    @Test
    @DisplayName("La piece moyenne est celle des pieces bien eclairees : la moyenne du quart le plus clair des sections, plus claire que celle de toutes")
    void roomKeepsTheBrightestQuarter() {
        // Une section sur deux a sa dalle : les autres ne recoivent que ce qui deborde de leurs voisines.
        set(this.room, 8, CEILING, 8, this.neon);
        byte[] dark = emptyRoom();
        for (int z = -LightZone.FAR_RADIUS - 1; z <= LightZone.FAR_RADIUS + 1; z++) {
            for (int x = -LightZone.FAR_RADIUS - 1; x <= LightZone.FAR_RADIUS + 1; x++) {
                this.blocks.setSection(ChunkSectionPos.asLong(x, 0, z), Math.floorMod(x + z, 2) == 0 ? this.room : dark);
                this.blocks.setSection(ChunkSectionPos.asLong(x, 1, z), LightBlocks.UNLOADED);
            }
        }
        LightZone zone = zone();
        drain(zone);
        float threshold = 3.0f * luma(AMBIENT, AMBIENT, AMBIENT);
        List<float[]> floors = new ArrayList<>();
        for (LightZone.FarBaked far : farBaked(zone)) {
            float red = 0.0f;
            int count = 0;
            for (int i = UP * LightZone.FAR_FACE_VALUES; i < (UP + 1) * LightZone.FAR_FACE_VALUES; i++) {
                int total = far.values()[i];
                if (LightCell.valid(total) && luma(LightCell.red(total), LightCell.green(total), LightCell.blue(total)) > threshold) {
                    red += LightCell.red(total);
                    count++;
                }
            }
            if (count > 0) {
                floors.add(new float[] {red / count});
            }
        }
        assertTrue(floors.size() >= 4, "Plusieurs sections ont un sol eclaire : " + floors.size() + ".");
        floors.sort((a, b) -> Float.compare(a[0], b[0]));
        float bright = floors.get(floors.size() * 3 / 4)[0];
        float kept = 0.0f;
        float all = 0.0f;
        int brightCount = 0;
        for (float[] floor : floors) {
            all += floor[0];
            if (floor[0] >= bright) {
                kept += floor[0];
                brightCount++;
            }
        }
        float[] room = zone.room();
        assertNotNull(room, "Mesuree.");
        assertEquals(kept / brightCount, room[6], 0.01f, "La moyenne du quart le plus clair.");
        assertTrue(room[6] > all / floors.size(), "Plus claire que la moyenne de toutes les sections : " + room[6] + " contre " + all / floors.size() + ".");
    }

    @Test
    @DisplayName("Dans un monde haut, le loin ne compte que les rangees de la fenetre, et ses voisines au-dessus et au-dessous restent de simples sources")
    void farInTallWorld() {
        for (int z = -LightZone.FAR_RADIUS - 1; z <= LightZone.FAR_RADIUS + 1; z++) {
            for (int x = -LightZone.FAR_RADIUS - 1; x <= LightZone.FAR_RADIUS + 1; x++) {
                for (int y = 2; y <= 5; y++) {
                    this.blocks.setSection(ChunkSectionPos.asLong(x, y, z), this.room);
                }
            }
        }
        LightZone zone = new LightZone(this.blocks, SETTINGS, 0, 8);
        zone.anchor(0, 3, 0);
        int window = (2 * LightZone.RADIUS + 1) * (2 * LightZone.RADIUS + 1) * LightZone.HEIGHT;
        int all = (2 * LightZone.FAR_RADIUS + 1) * (2 * LightZone.FAR_RADIUS + 1) * LightZone.HEIGHT;
        drain(zone);
        assertEquals(window, published(zone), "La fenetre, sur ses deux rangees.");
        assertEquals(all, farPublished(zone), "Et le loin, sur les memes.");
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
    @DisplayName("La zone compte les rebonds suivants comme les rebonds traces : la meme part que dans leur shader")
    void followingMatchesTracedBounces() throws IOException {
        String source;
        try (InputStream stream = LightBakeSceneTest.class.getResourceAsStream("/assets/gtgs_backrooms/shaders/bake/bounce.fsh")) {
            assertNotNull(stream, "Shader des rebonds introuvable.");
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher matcher = Pattern.compile("const float FOLLOWING = ([0-9.]+);").matcher(source);
        assertTrue(matcher.find(), "Part des rebonds suivants introuvable dans le shader.");
        assertEquals(Float.parseFloat(matcher.group(1)), SectionBaker.FOLLOWING, 1.0e-6f, "Meme part des deux cotes.");
    }

    @Test
    @DisplayName("Les reglages viennent du profil, et le calendrier decide des neons morts")
    void settingsFromProfile() {
        LookProfile profile = LookProfile.fromJson(JsonParser.parseString("{ \"dimension\": \"m:d\", \"id\": 1,"
                + " \"neons\": { \"block\": \"m:tube\", \"color\": [0.5, 1, 0.25], \"dead\": 1 },"
                + " \"baked_light\": { \"emission\": 4, \"ambient\": [0.1, 0.2, 0.3], \"reach\": 9, \"size\": 2, \"occlusion\": 0.5,"
                + " \"bounce_color\": [1.5, 1, 0.5] } }").getAsJsonObject());
        LightBakeSettings settings = LightBakeSettings.of(profile, Identifier.of("m", "d"));
        assertEquals(SectionBaker.FOLLOWING, settings.following(), "La zone compte aussi les rebonds qui suivent le second.");
        assertEquals(2.0f, settings.red(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(4.0f, settings.green(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(1.0f, settings.blue(), 1.0e-6f, "Couleur fois emission.");
        assertEquals(0.2f, settings.ambientGreen(), 1.0e-6f, "Fond.");
        assertEquals(9.0f, settings.reach(), 1.0e-6f, "Portee.");
        assertEquals(2.0f, settings.size(), 1.0e-6f, "Taille.");
        assertEquals(0.0f, settings.cone(), 1.0e-6f, "Tout le demi-espace devant la dalle.");
        assertEquals(0.5f, settings.occlusion(), 1.0e-6f, "Coins.");
        assertEquals(1.5f, settings.bounceRed(), 1.0e-6f, "Couleur des rebonds.");
        assertEquals(1.0f, settings.bounceGreen(), 1.0e-6f, "Couleur des rebonds.");
        assertEquals(0.5f, settings.bounceBlue(), 1.0e-6f, "Couleur des rebonds.");
        assertFalse(settings.lit(1, 2, 3), "Tous morts.");
        assertTrue(new LightBakeSettings(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1, 0.0f, 0.0f, 0.0f).lit(1, 2, 3),
                "Aucun mort.");
        assertFalse(new LightBakeSettings(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1, 0.0f, 0.0f, 1.0f).lit(1, 2, 3),
                "Secteur en panne.");
    }
}
