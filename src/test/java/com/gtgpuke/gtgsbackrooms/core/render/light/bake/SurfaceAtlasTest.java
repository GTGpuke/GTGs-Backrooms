package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

@DisplayName("Cache de surfaces : place des tuiles")
class SurfaceAtlasTest {

    private static final int FREE = SurfaceAtlas.CAPACITY - 1;
    private static final int[] LIGHTS = {1, 2, 3, 3};
    /** Surfaces du rayon des tuiles au pire d'une marche dans une grande salle ouverte du Level 0. */
    private static final int OPEN_ROOM = 18700;

    /** Une surface de la cellule (x, y, z) d'une section. */
    private static int surface(int x, int y, int z, int face, int material) {
        return SurfaceAtlas.surface(z << 8 | y << 4 | x, face, material);
    }

    @Test
    @DisplayName("Une surface se relit : cellule, face et matiere, meme la plus grande")
    void surfacePacking() {
        int surface = surface(15, 15, 15, 5, 255);
        assertEquals(LightCell.SECTION_CELLS - 1, SurfaceAtlas.cell(surface), "Cellule.");
        assertEquals(5, SurfaceAtlas.face(surface), "Face.");
        assertEquals(255, SurfaceAtlas.material(surface), "Matiere.");
    }

    @Test
    @DisplayName("Seules les surfaces proches de la camera en hauteur prennent une tuile, jamais la tuile nulle")
    void acceptKeepsTheBand() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        assertEquals(FREE, atlas.freeTiles(), "Toutes libres, sauf la tuile nulle.");
        assertNull(atlas.current(), "Rien a calculer.");
        int low = surface(0, 1, 0, 2, 3);
        int middle = surface(1, 5, 0, 2, 3);
        int high = surface(2, 9, 0, 2, 3);
        atlas.accept(7L, 16, new int[] {low, middle, high}, LIGHTS, 18, 22);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "Une section a calculer.");
        assertEquals(7L, job.key(), "Sa clef.");
        assertArrayEquals(new int[] {middle}, job.surfaces(), "Y = 21 seulement : 17 et 25 sont hors de la bande.");
        assertEquals(1, job.tiles().length, "Une tuile.");
        assertNotEquals(0, job.tiles()[0], "La tuile nulle veut dire aucune tuile.");
        assertSame(LIGHTS, job.lights(), "Ses neons.");
        assertEquals(FREE - 1, atlas.freeTiles(), "Une tuile prise.");
        assertTrue(atlas.holds(7L), "Tenue.");
        atlas.accept(8L, 16, new int[] {low}, LIGHTS, 16, 18);
        atlas.accept(9L, 16, new int[] {high}, LIGHTS, 25, 25);
        assertEquals(FREE - 3, atlas.freeTiles(), "Les bornes de la bande sont comprises.");
    }

    @Test
    @DisplayName("Une section se calcule surface par surface, puis la suivante, dans l'ordre d'arrivee")
    void jobsAdvance() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(1L, 0, new int[] {surface(0, 0, 0, 2, 1), surface(1, 0, 0, 2, 1)}, LIGHTS, 0, 15);
        atlas.accept(2L, 0, new int[] {surface(2, 0, 0, 2, 1)}, LIGHTS, 0, 15);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "La premiere.");
        assertEquals(1L, job.key(), "L'ordre d'arrivee.");
        assertEquals(0, job.next(), "Rien de calcule.");
        assertEquals(2, job.waiting(), "Deux a calculer.");
        assertFalse(job.done(), "Pas finie.");
        assertFalse(job.shown(0), "Sans valeur, une surface ne se montre pas.");
        job.advance();
        assertEquals(1, job.next(), "Une calculee.");
        assertTrue(job.shown(0), "Elle a sa valeur.");
        job.advance();
        assertTrue(job.done(), "Finie.");
        assertEquals(2, job.next(), "Plus rien a calculer.");
        atlas.finish();
        SurfaceAtlas.Job second = atlas.current();
        assertNotNull(second, "La suivante.");
        assertEquals(2L, second.key(), "Sa clef.");
        second.advance();
        atlas.finish();
        assertNull(atlas.current(), "Tout est calcule.");
        assertTrue(atlas.holds(1L), "Ses tuiles restent : elles sont montrees.");
        assertEquals(2, atlas.takeMoved().size(), "L'indirection des deux sections doit suivre.");
        assertTrue(atlas.takeMoved().isEmpty(), "Une seule fois.");
    }

    /** Calcule tout ce qui attend. */
    private static void bakeAll(SurfaceAtlas atlas) {
        SurfaceAtlas.Job job = atlas.current();
        while (job != null) {
            while (!job.done()) {
                job.advance();
            }
            atlas.finish();
            job = atlas.current();
        }
        atlas.takeMoved();
    }

    @Test
    @DisplayName("Une section recuite a l'identique garde ses tuiles et ne recalcule rien : ce qui change seul repart")
    void rebakeKeepsTiles() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int kept = surface(0, 0, 0, 2, 1);
        int gone = surface(1, 0, 0, 2, 1);
        int repainted = surface(2, 0, 0, 2, 1);
        atlas.accept(1L, 0, new int[] {kept, gone, repainted}, LIGHTS, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(1L);
        assertNotNull(job, "Tenue.");
        int tileKept = job.tiles()[0];
        int tileRepainted = job.tiles()[2];
        atlas.accept(1L, 0, new int[] {kept, gone, repainted}, LIGHTS.clone(), 0, 15);
        assertNull(atlas.current(), "A l'identique : rien a calculer.");
        assertTrue(atlas.takeMoved().isEmpty(), "Et rien a republier.");
        assertSame(job, atlas.job(1L), "La meme section.");
        int fresh = surface(3, 0, 0, 2, 1);
        atlas.accept(1L, 0, new int[] {kept, surface(2, 0, 0, 2, 9), fresh}, LIGHTS, 0, 15);
        assertEquals(tileKept, job.tiles()[0], "La surface qui reste garde sa tuile.");
        assertEquals(tileRepainted, job.tiles()[1], "Une autre matiere garde sa tuile aussi.");
        assertEquals(2, job.waiting(), "Elle repart, avec la nouvelle.");
        assertTrue(job.shown(0) && job.shown(1), "Pendant ce temps, les anciennes valeurs restent montrees.");
        assertFalse(job.shown(2), "La nouvelle attend sa valeur.");
        assertEquals(FREE - 3, atlas.freeTiles(), "La surface partie a rendu sa tuile.");
    }

    @Test
    @DisplayName("D'autres neons remettent toute la section a calculer, sans rien cacher ; une surface qui attend le reste")
    void otherLightsRecompute() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int[] surfaces = {surface(0, 0, 0, 2, 1), surface(1, 0, 0, 2, 1)};
        atlas.accept(1L, 0, surfaces, LIGHTS, 0, 15);
        atlas.accept(1L, 0, surfaces, LIGHTS, 0, 15);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "En attente.");
        assertEquals(2, job.waiting(), "Une seule fois chacune.");
        bakeAll(atlas);
        atlas.accept(1L, 0, surfaces, new int[] {4, 5, 6, 3}, 0, 15);
        assertEquals(2, job.waiting(), "Tout repart.");
        assertTrue(job.shown(0) && job.shown(1), "Sans rien cacher.");
        assertArrayEquals(new int[] {4, 5, 6, 3}, job.lights(), "Les nouveaux neons.");
    }

    @Test
    @DisplayName("La bande qui bouge ne rend que ce qui en sort, et ce qui y reste garde sa valeur")
    void bandMoves() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int low = surface(0, 1, 0, 2, 3);
        int high = surface(0, 9, 0, 2, 3);
        atlas.accept(1L, 0, new int[] {low, high}, LIGHTS, 0, 10);
        bakeAll(atlas);
        atlas.accept(1L, 0, new int[] {low, high}, LIGHTS, 5, 15);
        SurfaceAtlas.Job job = atlas.job(1L);
        assertNotNull(job, "Tenue.");
        assertArrayEquals(new int[] {high}, job.surfaces(), "Seule celle du haut reste.");
        assertEquals(0, job.waiting(), "Rien a recalculer.");
        assertEquals(FREE - 1, atlas.freeTiles(), "L'autre a rendu sa tuile.");
        assertEquals(1, atlas.takeMoved().size(), "L'indirection suit.");
    }

    @Test
    @DisplayName("Une section deja a republier qui repart a calculer ne se republie qu'une fois")
    void movedOnce() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int kept = surface(0, 0, 0, 2, 1);
        atlas.accept(0L, 0, new int[] {kept, surface(1, 0, 0, 2, 1)}, LIGHTS, 0, 15);
        bakeAll(atlas);
        atlas.accept(0L, 0, new int[] {kept}, LIGHTS, 0, 15);
        atlas.sectionChanged(0L, 14.0f);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "Elle repart.");
        job.advance();
        atlas.finish();
        assertEquals(1, atlas.takeMoved().size(), "Une seule fois.");
    }

    @Test
    @DisplayName("Rendre une section libere ses tuiles, et l'oublie dans la file")
    void release() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int[] surfaces = {surface(0, 0, 0, 2, 1), surface(1, 0, 0, 2, 1)};
        atlas.accept(1L, 0, surfaces, LIGHTS, 0, 15);
        atlas.accept(1L, 0, new int[] {surfaces[0]}, LIGHTS, 0, 15);
        assertEquals(FREE - 1, atlas.freeTiles(), "Remplacee.");
        atlas.release(1L);
        assertEquals(FREE, atlas.freeTiles(), "Rendue.");
        assertFalse(atlas.holds(1L), "Plus tenue.");
        assertNull(atlas.current(), "Plus rien a calculer.");
        atlas.release(99L);
        assertEquals(FREE, atlas.freeTiles(), "Une section inconnue ne change rien.");
    }

    /** Une salle : un sol a Y = 0 sur toute la section, un plafond a Y = 8, un neon au plafond en (8, 8, 8). */
    private static LightBlocks room() {
        LightBlocks blocks = new LightBlocks();
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                section[LightBlocks.index(x, 0, z)] = STONE;
                section[LightBlocks.index(x, 8, z)] = STONE;
            }
        }
        blocks.setSection(0L, section);
        return blocks;
    }

    /** Les surfaces du sol de la salle : les faces du dessus. */
    private static int[] floor() {
        int[] surfaces = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                surfaces[z * 16 + x] = surface(x, 1, z, UP, STONE);
            }
        }
        return surfaces;
    }

    private static void setBlock(LightBlocks blocks, int x, int y, int z, byte block) {
        byte[] next = blocks.section(0L).clone();
        next[LightBlocks.index(x, y, z)] = block;
        blocks.setSection(0L, next);
    }

    private static final byte STONE = 2;
    private static final int UP = 2;
    private static final int[] CEILING_LIGHT = {8, 8, 8, 3};
    private static final float REACH = 14.0f;
    private static final float RADIUS = 0.15f;

    @Test
    @DisplayName("Un bloc pose ajoute ses faces et retire celle qu'il cache, tout de suite, et un bloc retire fait l'inverse")
    void blockChangesSurfaces() {
        LightBlocks blocks = room();
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(0L);
        assertNotNull(job, "Tenue.");
        int before = job.surfaces().length;
        setBlock(blocks, 3, 1, 3, STONE);
        atlas.blockChanged(blocks, 3, 1, 3, false, REACH, RADIUS, 0, 15);
        assertEquals(before + 4, job.surfaces().length, "Quatre cotes et un dessus en plus, le sol dessous en moins.");
        assertEquals(5, countNew(job), "Les nouvelles faces attendent leur valeur.");
        assertEquals(job.surfaces().length, sorted(job.surfaces()), "Elles restent dans l'ordre de leur place.");
        assertSame(job, atlas.current(), "Elles passent en tete.");
        bakeAll(atlas);
        setBlock(blocks, 3, 1, 3, LightBlocks.AIR);
        atlas.blockChanged(blocks, 3, 1, 3, false, REACH, RADIUS, 0, 15);
        assertEquals(before, job.surfaces().length, "Retire : on revient au sol seul.");
        assertEquals(1, countNew(job), "Le sol reapparu attend sa valeur.");
    }

    private static int countNew(SurfaceAtlas.Job job) {
        int count = 0;
        for (int i = 0; i < job.surfaces().length; i++) {
            if (!job.shown(i)) {
                count++;
            }
        }
        return count;
    }

    private static int sorted(int[] surfaces) {
        int count = surfaces.length == 0 ? 0 : 1;
        for (int i = 1; i < surfaces.length; i++) {
            if ((surfaces[i] & 0xFFFF) > (surfaces[i - 1] & 0xFFFF)) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("Un bloc ne remet a calculer que ce qu'il peut assombrir : ce qui l'entoure, et ce qui voit un neon a travers lui")
    void blockDarkensOnlyWhatItCanReach() {
        LightBlocks blocks = room();
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(0L);
        assertNotNull(job, "Tenue.");
        // Un bloc haut, a mi-chemin du neon : il coupe le rayon des surfaces du sol derriere lui, vu du neon.
        setBlock(blocks, 11, 4, 8, STONE);
        atlas.blockChanged(blocks, 11, 4, 8, false, REACH, RADIUS, 0, 15);
        int stale = job.waiting();
        assertTrue(stale > 0 && stale < 80, "Une petite partie du sol repart : " + stale + " sur 256.");
        assertEquals(256 + 6, job.surfaces().length, "Le bloc en l'air ajoute ses six faces.");
        bakeAll(atlas);
        setBlock(blocks, 2, 1, 2, STONE);
        setBlock(blocks, 2, 1, 2, LightBlocks.AIR);
        atlas.blockChanged(blocks, 2, 1, 2, false, REACH, RADIUS, 0, 15);
        int near = job.waiting();
        assertTrue(near > 0 && near < 40, "Un bloc sans effet au final touche encore ce qui l'entoure, pas toute la salle : " + near + ".");
        bakeAll(atlas);
        atlas.blockChanged(blocks, 8, 8, 8, true, REACH, RADIUS, 0, 15);
        assertTrue(job.waiting() > 200, "Un neon change : tout ce qu'il atteint repart.");
    }

    @Test
    @DisplayName("Un bloc loin, ou une section loin, ne touche pas a une section ; hors de la bande, rien n'apparait")
    void farBlocksDoNothing() {
        LightBlocks blocks = room();
        long far = ChunkSectionPos.asLong(0, 0, 3);
        byte[] section = new byte[LightBlocks.SECTION_BLOCKS];
        blocks.setSection(far, section);
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(0L);
        assertNotNull(job, "Tenue.");
        atlas.blockChanged(blocks, 8, 4, 56, false, REACH, RADIUS, 0, 15);
        assertEquals(0, job.waiting(), "Trop loin pour un rayon.");
        atlas.sectionChanged(far, REACH);
        assertEquals(0, job.waiting(), "Une section trop loin non plus.");
        atlas.sectionChanged(0L, REACH);
        assertEquals(256, job.waiting(), "Sa propre section : tout repart.");
        bakeAll(atlas);
        setBlock(blocks, 3, 5, 3, STONE);
        atlas.blockChanged(blocks, 3, 5, 3, false, REACH, RADIUS, 0, 3);
        assertEquals(256, job.surfaces().length, "Hors de la bande : pas de nouvelle face.");
        setBlock(blocks, 3, 5, 3, (byte) 3);
        atlas.blockChanged(blocks, 3, 5, 3, false, REACH, RADIUS, 0, 15);
        int rank = rankOf(job, surface(3, 6, 3, UP, 3));
        assertTrue(rank >= 0, "Le dessus du bloc apparait, avec sa matiere.");
        bakeAll(atlas);
        setBlock(blocks, 3, 5, 3, STONE);
        atlas.blockChanged(blocks, 3, 5, 3, false, REACH, RADIUS, 0, 15);
        assertTrue(rankOf(job, surface(3, 6, 3, UP, STONE)) >= 0, "Une autre matiere : la surface reste, repeinte.");
        assertTrue(job.waiting() > 0, "Et repart.");
    }

    @Test
    @DisplayName("Hors de portee sur chaque axe, un bloc ou une section ne touchent rien ; sous la bande, rien ne bouge")
    void farOnEveryAxis() {
        LightBlocks blocks = room();
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(0L);
        assertNotNull(job, "Tenue.");
        int[][] far = {{-20, 4, 8}, {40, 4, 8}, {8, -24, 8}, {8, 40, 8}, {8, 4, -20}, {8, 4, 40}};
        for (int[] pos : far) {
            atlas.blockChanged(blocks, pos[0], pos[1], pos[2], false, REACH, RADIUS, 0, 15);
            assertEquals(0, job.waiting(), "Bloc en " + Arrays.toString(pos) + ".");
        }
        for (long key : new long[] {ChunkSectionPos.asLong(3, 0, 0), ChunkSectionPos.asLong(0, 3, 0), ChunkSectionPos.asLong(0, 0, 3)}) {
            atlas.sectionChanged(key, REACH);
            assertEquals(0, job.waiting(), "Section " + key + ".");
        }
        setBlock(blocks, 3, 1, 3, STONE);
        atlas.blockChanged(blocks, 3, 1, 3, false, 0.0f, RADIUS, 5, 15);
        assertEquals(256, job.surfaces().length, "Sous la bande, ni nouvelle face ni face retiree.");
    }

    @Test
    @DisplayName("Cache plein : un bloc pose n'a pas de tuile pour ses faces, et ce qui est deja tenu ne bouge pas")
    void blockInFullAtlas() {
        LightBlocks blocks = room();
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        bakeAll(atlas);
        SurfaceAtlas.Job job = atlas.job(0L);
        assertNotNull(job, "Tenue.");
        atlas.accept(99L, 0, new int[SurfaceAtlas.CAPACITY], LIGHTS, 0, 15);
        assertEquals(0, atlas.freeTiles(), "Plus aucune tuile.");
        setBlock(blocks, 3, 1, 3, STONE);
        atlas.blockChanged(blocks, 3, 1, 3, false, REACH, RADIUS, 0, 15);
        assertEquals(256, job.surfaces().length, "Le sol cache rend sa tuile, que prend une seule des nouvelles faces : les autres n'en ont pas.");
        assertEquals(0, atlas.freeTiles(), "Toujours plein.");
        assertTrue(atlas.starved(0L), "Ses nouvelles faces attendent une tuile.");
    }

    @Test
    @DisplayName("Une surface est neuve jusqu'a son premier calcul, et ne le redevient pas quand elle repart a calculer")
    void freshSurface() {
        LightBlocks blocks = room();
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(0L, 0, floor(), CEILING_LIGHT, 0, 15);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "Une section a calculer.");
        assertTrue(job.fresh(0), "Jamais calculee.");
        bakeAll(atlas);
        assertFalse(job.fresh(0), "Calculee.");
        setBlock(blocks, 3, 1, 3, STONE);
        atlas.blockChanged(blocks, 3, 1, 3, false, REACH, RADIUS, 0, 15);
        for (int rank = 0; rank < job.surfaces().length; rank++) {
            assertEquals(!job.shown(rank), job.fresh(rank), "Neuve si et seulement si rien n'est a montrer, au rang " + rank + ".");
        }
    }

    private static int rankOf(SurfaceAtlas.Job job, int surface) {
        for (int i = 0; i < job.surfaces().length; i++) {
            if (job.surfaces()[i] == surface) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("Un segment traverse une boite, la frole sans la toucher, ou court le long d'un axe")
    void segmentCrossesBox() {
        assertTrue(SurfaceAtlas.crosses(0.0f, 0.0f, 0.0f, 4.0f, 4.0f, 4.0f, 1.0f, 1.0f, 1.0f, 2.0f, 2.0f, 2.0f), "En diagonale.");
        assertFalse(SurfaceAtlas.crosses(0.0f, 3.0f, 0.0f, 4.0f, 3.0f, 0.0f, 1.0f, 1.0f, 1.0f, 2.0f, 2.0f, 2.0f), "A cote.");
        assertTrue(SurfaceAtlas.crosses(1.5f, 1.5f, 0.0f, 1.5f, 1.5f, 4.0f, 1.0f, 1.0f, 1.0f, 2.0f, 2.0f, 2.0f), "Le long d'un axe.");
        assertFalse(SurfaceAtlas.crosses(0.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.5f, 1.0f, 1.0f, 1.0f, 2.0f, 2.0f, 2.0f), "Il s'arrete avant.");
    }

    @Test
    @DisplayName("Quand le cache est plein, les surfaces en trop n'ont pas de tuile, et la remise a zero rend tout")
    void fullAtlas() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int[] surfaces = new int[SurfaceAtlas.CAPACITY + 5];
        atlas.accept(1L, 0, surfaces, LIGHTS, 0, 15);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "Une section.");
        assertEquals(FREE, job.surfaces().length, "Autant de surfaces que de tuiles.");
        assertEquals(0, atlas.freeTiles(), "Plus aucune tuile.");
        assertTrue(atlas.starving(), "Il en manque.");
        atlas.reset();
        assertEquals(FREE, atlas.freeTiles(), "Toutes libres.");
        assertFalse(atlas.holds(1L), "Rien de tenu.");
        assertNull(atlas.current(), "Rien a calculer.");
        assertFalse(atlas.starving(), "Plus personne n'attend.");
    }

    @Test
    @DisplayName("Une section servie a moitie reste en manque, et se reprend quand des tuiles se liberent, sans recalculer ce qu'elle a")
    void starvedSectionIsServedLater() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.limit(4);
        assertEquals(3, atlas.freeTiles(), "Trois tuiles : la nulle ne sert pas.");
        int[] first = {surface(0, 0, 0, 2, 1), surface(1, 0, 0, 2, 1)};
        int[] second = {surface(2, 0, 0, 2, 1), surface(3, 0, 0, 2, 1)};
        atlas.accept(1L, 0, first, LIGHTS, 0, 15);
        assertFalse(atlas.starving(), "La premiere a tout.");
        atlas.accept(2L, 0, second, LIGHTS, 0, 15);
        assertTrue(atlas.starving(), "La seconde manque d'une tuile.");
        assertTrue(atlas.starved(2L), "La seconde.");
        assertFalse(atlas.starved(1L), "Pas la premiere.");
        assertFalse(atlas.starved(3L), "Une section inconnue n'attend rien.");
        SurfaceAtlas.Job job = atlas.job(2L);
        assertNotNull(job, "Tenue.");
        assertEquals(1, job.surfaces().length, "Une seule surface servie.");
        bakeAll(atlas);
        atlas.accept(2L, 0, second, LIGHTS, 0, 15);
        assertTrue(atlas.starved(2L), "Rien de libre : toujours en manque.");
        atlas.release(1L);
        assertTrue(atlas.starving(), "Une tuile rendue ne suffit pas a la servir : il faut la reprendre.");
        atlas.accept(2L, 0, second, LIGHTS, 0, 15);
        assertFalse(atlas.starving(), "Servie.");
        assertFalse(atlas.starved(2L), "Servie.");
        assertEquals(2, job.surfaces().length, "Ses deux surfaces.");
        assertEquals(1, job.waiting(), "Seule la nouvelle est a calculer.");
    }

    @Test
    @DisplayName("Une section rendue en manque ne laisse personne en attente, et un cache limite le reste apres une remise a zero")
    void releaseStarvedSection() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.limit(2);
        atlas.accept(1L, 0, new int[] {surface(0, 0, 0, 2, 1), surface(1, 0, 0, 2, 1)}, LIGHTS, 0, 15);
        assertTrue(atlas.starving(), "Une seule tuile pour deux surfaces.");
        atlas.release(1L);
        assertFalse(atlas.starving(), "Elle est partie.");
        atlas.release(1L);
        assertFalse(atlas.starving(), "Rendre deux fois ne compte qu'une.");
        atlas.reset();
        assertEquals(1, atlas.freeTiles(), "La limite reste.");
        atlas.limit(SurfaceAtlas.CAPACITY);
        assertEquals(FREE, atlas.freeTiles(), "La limite se retire.");
    }

    @Test
    @DisplayName("La plus grande salle ouverte relevee dans le monde d'un joueur tient dans le cache, avec de la marge")
    void openRoomFits() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        atlas.accept(1L, 0, new int[(int) (OPEN_ROOM * 1.5)], LIGHTS, 0, 15);
        assertFalse(atlas.starving(), "Une fois et demie ce que demande la salle.");
    }

    @Test
    @DisplayName("Une section qui n'avait aucun neon se recalcule au premier qu'elle recoit")
    void firstLightRecomputes() {
        SurfaceAtlas atlas = new SurfaceAtlas();
        int[] surfaces = {surface(0, 0, 0, 2, 1)};
        atlas.accept(1L, 0, surfaces, new int[0], 0, 15);
        SurfaceAtlas.Job job = atlas.current();
        assertNotNull(job, "En attente.");
        bakeAll(atlas);
        assertEquals(0, job.waiting(), "Calculee, dans le noir.");
        atlas.accept(1L, 0, surfaces, LIGHTS, 0, 15);
        assertEquals(1, job.waiting(), "Le neon l'eclaire.");
    }
}
