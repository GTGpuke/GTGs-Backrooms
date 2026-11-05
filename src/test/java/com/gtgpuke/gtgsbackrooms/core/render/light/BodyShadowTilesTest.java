package com.gtgpuke.gtgsbackrooms.core.render.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;

class BodyShadowTilesTest {

    private static final int[] LIGHTS = {1, 2, 3, 3};
    private static final int FLOOR = 2;
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Y = 64;
    private static final int ORIGIN_Z = 0;
    private static final float FRACT_X = 0.5f;
    private static final float FRACT_Y = 0.62f;
    private static final float FRACT_Z = 0.5f;

    /** Une camera a l'origine qui regarde vers -z, 70 degres d'ouverture, en 16:9. */
    private static FrustumIntersection frustum() {
        return new FrustumIntersection(new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f, 0.05f, 100.0f));
    }

    private static float[] body(float x, float y, float z) {
        float[] casters = new float[ShadowCasters.BODY_VALUES * 2];
        casters[0] = x;
        casters[1] = y;
        casters[2] = z;
        casters[3] = 0.3f;
        casters[ShadowCasters.BODY_VALUES] = x + 1.0f;
        casters[ShadowCasters.BODY_VALUES + 1] = y;
        casters[ShadowCasters.BODY_VALUES + 2] = z;
        casters[ShadowCasters.BODY_VALUES + 3] = 0.3f;
        return casters;
    }

    private static int surface(int worldX, int worldY, int worldZ, int face) {
        return SurfaceAtlas.surface((worldZ & 15) << 8 | (worldY & 15) << 4 | (worldX & 15), face, 1);
    }

    private static long section(int worldX, int worldY, int worldZ) {
        return ChunkSectionPos.asLong(worldX >> 4, worldY >> 4, worldZ >> 4);
    }

    /** Pose des surfaces de sol dans l'atlas, calculees (montrees) sauf les {@code unbaked} dernieres de chaque section. */
    private static SurfaceAtlas atlasOf(List<int[]> cells, int unbaked) {
        Map<Long, List<Integer>> bySection = new HashMap<>();
        for (int[] cell : cells) {
            bySection.computeIfAbsent(section(cell[0], cell[1], cell[2]), key -> new ArrayList<>()).add(surface(cell[0], cell[1], cell[2], FLOOR));
        }
        SurfaceAtlas atlas = new SurfaceAtlas();
        for (Map.Entry<Long, List<Integer>> entry : bySection.entrySet()) {
            int[] surfaces = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            atlas.accept(entry.getKey(), ChunkSectionPos.unpackY(entry.getKey()) << 4, surfaces, LIGHTS, 0, 255);
            SurfaceAtlas.Job job = atlas.job(entry.getKey());
            for (int i = 0; i < surfaces.length - unbaked; i++) {
                job.advance();
            }
        }
        return atlas;
    }

    @Test
    @DisplayName("Une tuile de sol proche d'un corps, devant la camera, est a calculer : sa cellule relative au bloc de la camera, sa face, son emplacement")
    void nearTileInFront() {
        SurfaceAtlas atlas = atlasOf(List.of(new int[] {0, 62, -5}), 0);
        BodyShadowTiles tiles = new BodyShadowTiles();
        int count = tiles.collect(atlas, frustum(), body(0.5f, 0.0f, 0.5f), 1, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z);
        assertEquals(1, count, "Une tuile.");
        assertEquals(atlas.job(section(0, 62, -5)).tiles()[0], tiles.tiles()[0], "La tuile de la surface.");
        assertEquals(0, tiles.cells()[0], "Cellule, x.");
        assertEquals(-2, tiles.cells()[1], "Cellule, y, relative au bloc de la camera.");
        assertEquals(-5, tiles.cells()[2], "Cellule, z.");
        assertEquals(FLOOR, tiles.faces()[0], "Face.");
        assertEquals(0, tiles.dropped(), "Rien de laisse.");
    }

    @Test
    @DisplayName("Une tuile hors de portee du corps, derriere la camera, ou pas encore calculee ne compte pas")
    void farBehindOrUnbakedTilesAreLeft() {
        // Par section, la derniere surface n'est pas calculee : celle de z = 7 laisse calculee celle de z = 6, que seul le champ de la
        // camera ecarte.
        List<int[]> cells = List.of(new int[] {0, 62, -14}, new int[] {0, 62, 6}, new int[] {0, 62, 7}, new int[] {0, 62, -3}, new int[] {1, 62, -3});
        SurfaceAtlas atlas = atlasOf(cells, 1);
        BodyShadowTiles tiles = new BodyShadowTiles();
        int count = tiles.collect(atlas, frustum(), body(0.5f, 0.0f, 0.5f), 1, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z);
        assertEquals(1, count, "Seule la tuile proche, devant et calculee.");
        assertEquals(-3, tiles.cells()[2], "Celle a z = -3.");
    }

    @Test
    @DisplayName("Deux corps proches d'une meme tuile ne la comptent qu'une fois, et une nouvelle recherche repart de zero")
    void twoBodiesShareATile() {
        SurfaceAtlas atlas = atlasOf(List.of(new int[] {0, 62, -5}), 0);
        BodyShadowTiles tiles = new BodyShadowTiles();
        assertEquals(1, tiles.collect(atlas, frustum(), body(0.5f, 0.0f, 0.5f), 2, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z), "Une tuile.");
        assertEquals(1, tiles.collect(atlas, frustum(), body(0.5f, 0.0f, 0.5f), 2, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z), "Encore une.");
        assertEquals(0, tiles.collect(atlas, frustum(), body(0.5f, 0.0f, 0.5f), 0, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z), "Sans corps, rien.");
    }

    @Test
    @DisplayName("Plus de tuiles que d'emplacements : les premieres trouvees sont gardees, les autres comptees comme laissees")
    void moreTilesThanSlots() {
        List<int[]> cells = new ArrayList<>();
        int expected = 0;
        float reach = BodyShadowTiles.REACH + BodyShadowTiles.CELL_MARGIN;
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -26; z <= -14; z++) {
                    cells.add(new int[] {ORIGIN_X + x, ORIGIN_Y + y, ORIGIN_Z + z});
                    float dx = x + 0.5f - 0.5f;
                    float dy = y + 0.5f - 0.5f;
                    float dz = z + 0.5f + 19.5f;
                    if (dx * dx + dy * dy + dz * dz <= reach * reach) {
                        expected++;
                    }
                }
            }
        }
        assertTrue(expected > BodyShadowTiles.SLOTS, "Le cas deborde.");
        BodyShadowTiles tiles = new BodyShadowTiles();
        int count = tiles.collect(atlasOf(cells, 0), frustum(), body(0.5f, 0.5f, -19.5f), 1, ORIGIN_X, ORIGIN_Y, ORIGIN_Z, FRACT_X, FRACT_Y, FRACT_Z);
        assertEquals(BodyShadowTiles.SLOTS, count, "Autant que d'emplacements.");
        assertEquals(expected - BodyShadowTiles.SLOTS, tiles.dropped(), "Le reste est laisse.");
    }

    @Test
    @DisplayName("Les shaders declarent la meme forme d'emplacement que le code")
    void shadersAgreeOnSlots() throws IOException {
        String look = resource("/assets/gtgs_backrooms/shaders/include/look.glsl");
        String pass = resource("/assets/gtgs_backrooms/shaders/bake/body_shadow.fsh");
        assertTrue(look.contains("const int GTGS_BODY_SLOT = " + BodyShadowTiles.SLOT + ";"), "Cote d'un emplacement dans look.glsl.");
        assertTrue(look.contains("const int GTGS_BODY_SLOTS_ACROSS = " + BodyShadowTiles.SLOTS_ACROSS + ";"), "Emplacements par rangee dans look.glsl.");
        assertTrue(pass.contains("const int SLOT = " + BodyShadowTiles.SLOT + ";"), "Cote d'un emplacement dans la passe.");
        assertTrue(pass.contains("const int SLOTS_ACROSS = " + BodyShadowTiles.SLOTS_ACROSS + ";"), "Emplacements par rangee dans la passe.");
        assertTrue(pass.contains("const int TILE = " + SurfaceAtlas.TILE + ";"), "Cote d'une tuile dans la passe.");
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = BodyShadowTilesTest.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
}
