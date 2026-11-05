package com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.write;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.block.LevelZeroWallpaperBlock;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroVerticalSlice;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.StageRandom;

/**
 * Petits details d'une colonne de mur visible : plinthe sur chaque face exposee et,
 * rarement, un interrupteur ou une prise sur une des faces.
 *
 * <p>Ces details sont purement visuels et rendus cote client par-dessus le
 * papier peint ; le resolveur est deterministe et independant du seed monde
 * pour que le client le recalcule seul.
 *
 * @param baseboardFaceMask faces portant une plinthe (bits de {@link LevelZeroWallpaperBlock})
 * @param fixture element mural eventuel
 * @param fixtureFaceMask face portant l'element mural
 * @param fixtureY Y absolu de l'element mural
 */
public record LevelZeroWallProps(int baseboardFaceMask, Fixture fixture, int fixtureFaceMask, int fixtureY) {

    /** Element mural leger. */
    public enum Fixture {
        NONE,
        SWITCH,
        OUTLET
    }

    /** Colonne sans aucun detail mural. */
    public static final LevelZeroWallProps NONE = new LevelZeroWallProps(0, Fixture.NONE, 0, 0);

    private static final int FIXTURE_ROLL_MODULO = 1024;
    private static final int SWITCH_ROLL_MAX_EXCLUSIVE = 4;
    private static final int OUTLET_ROLL_MAX_EXCLUSIVE = 10;
    private static final int[] FACE_PICK_ORDER = {
            LevelZeroWallpaperBlock.NORTH_MASK,
            LevelZeroWallpaperBlock.EAST_MASK,
            LevelZeroWallpaperBlock.SOUTH_MASK,
            LevelZeroWallpaperBlock.WEST_MASK,
    };

    /**
     * Resout les details muraux d'une colonne de mur.
     *
     * @param worldX coordonnee monde X
     * @param worldZ coordonnee monde Z
     * @param exposedFaceMask faces de la colonne donnant sur l'air
     * @param slice slice verticale du layer
     * @return details muraux ; {@link #NONE} si aucune face n'est exposee
     */
    public static LevelZeroWallProps resolve(int worldX, int worldZ, int exposedFaceMask, LevelZeroVerticalSlice slice) {
        if (exposedFaceMask == 0) {
            return NONE;
        }
        long hash = StageRandom.mix(0L, StageRandom.Stage.WALL_PROPS, worldX, worldZ);
        int roll = Math.floorMod(hash, FIXTURE_ROLL_MODULO);
        if (roll < SWITCH_ROLL_MAX_EXCLUSIVE) {
            return new LevelZeroWallProps(exposedFaceMask, Fixture.SWITCH, pickVisibleFace(exposedFaceMask, hash),
                    slice.floorY() + 2);
        }
        if (roll < OUTLET_ROLL_MAX_EXCLUSIVE) {
            return new LevelZeroWallProps(exposedFaceMask, Fixture.OUTLET, pickVisibleFace(exposedFaceMask, hash),
                    slice.floorY() + 1);
        }
        return new LevelZeroWallProps(exposedFaceMask, Fixture.NONE, 0, 0);
    }

    /** {@code true} si un interrupteur ou une prise est present. */
    public boolean hasFixture() {
        return fixture != Fixture.NONE && fixtureFaceMask != 0;
    }

    /** Face visible choisie par le hash parmi celles du masque, dans l'ordre de {@link #FACE_PICK_ORDER} ; 0 sans face. */
    static int pickVisibleFace(int faceMask, long hash) {
        int count = Integer.bitCount(faceMask & LevelZeroWallpaperBlock.FULL_MASK);
        if (count == 0) {
            return 0;
        }
        int target = Math.floorMod(hash, count);
        int picked = 0;
        int seen = 0;
        for (int face : FACE_PICK_ORDER) {
            if ((faceMask & face) == 0) {
                continue;
            }
            if (seen == target) {
                picked = face;
            }
            seen++;
        }
        return picked;
    }
}
