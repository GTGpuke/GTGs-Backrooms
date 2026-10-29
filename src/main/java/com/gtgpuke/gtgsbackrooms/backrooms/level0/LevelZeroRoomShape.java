package com.gtgpuke.gtgsbackrooms.backrooms.level0;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroCoords;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroLayerStackLayout;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroVerticalSlice;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroLayout;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.LevelZeroSeedResolver;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticResult;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityWindow;

/**
 * L'acoustique du Level 0 tiree de sa generation (specification audio, section 6.4) : le layout de chaque layer dit,
 * colonne par colonne, ou passe l'air jouable (plafond a hauteur fixe). Autour d'un point, les colonnes ouvertes
 * atteignables a {@value #RADIUS} blocs au plus forment la salle : son aire, ses murs et ses sorties au-dela du rayon
 * donnent le libre parcours moyen d'un son entre deux parois (quatre fois le volume sur la surface) et l'ouverture (part
 * du pourtour qui ne se ferme pas). Lit la generation sans la changer ({@link LevelZeroLayout}) ; les layouts de chunk
 * lus restent en cache. Une instance par monde, pour le seul thread de calcul acoustique.
 */
public final class LevelZeroRoomShape implements GeneratedAcoustics.Shape {

    /** Portee de la salle autour d'un point, en blocs (de chaque cote). */
    public static final int RADIUS = 16;
    /** Portee de la verification de la copie du monde autour d'une source, en blocs. */
    static final int MATCH_RADIUS = 4;
    private static final int SIDE = 2 * RADIUS + 1;
    private static final int LAYOUTS = 64;
    private static final int CHUNK_MASK = 0x1FFFFFF;
    private static final int CHUNK_BITS = 25;
    private static final int LAYER_SHIFT = 50;
    private static final int[] STEP_X = {1, -1, 0, 0};
    private static final int[] STEP_Z = {0, 0, 1, -1};

    private final long layoutSeed;
    /** Layouts des derniers chunks lus, du plus ancien au plus recent ; cles primitives, sans boxing a chaque lecture. */
    private final Long2ObjectLinkedOpenHashMap<LevelZeroLayout> layouts = new Long2ObjectLinkedOpenHashMap<>(LAYOUTS + 1);
    private final int[] queue = new int[SIDE * SIDE];
    private final int[] seen = new int[SIDE * SIDE];
    private int stamp;

    /**
     * @param layoutSeed graine du layout du Level 0 de ce monde, celle du serveur
     */
    public LevelZeroRoomShape(long layoutSeed) {
        this.layoutSeed = layoutSeed;
    }

    @Override
    public @Nullable AcousticResult estimate(int x, int y, int z) {
        LevelZeroVerticalSlice slice = interior(y);
        if (slice == null || !open(x, z, slice.layerIndex())) {
            return null;
        }
        int layer = slice.layerIndex();
        this.stamp++;
        int head = 0;
        int tail = 0;
        this.queue[tail++] = index(0, 0);
        this.seen[index(0, 0)] = this.stamp;
        int area = 0;
        int walls = 0;
        int exits = 0;
        while (head < tail) {
            int cell = this.queue[head++];
            int dx = cell % SIDE - RADIUS;
            int dz = cell / SIDE - RADIUS;
            area++;
            for (int side = 0; side < STEP_X.length; side++) {
                int nx = dx + STEP_X[side];
                int nz = dz + STEP_Z[side];
                boolean beyond = Math.abs(nx) > RADIUS || Math.abs(nz) > RADIUS;
                if (!open(x + nx, z + nz, layer)) {
                    walls++;
                } else if (beyond) {
                    exits++;
                } else if (this.seen[index(nx, nz)] != this.stamp) {
                    this.seen[index(nx, nz)] = this.stamp;
                    this.queue[tail++] = index(nx, nz);
                }
            }
        }
        int height = slice.airMaxY() - slice.airMinY() + 1;
        // Libre parcours moyen d'un espace clos : quatre fois le volume sur la surface (sol, plafond et murs) ; les sorties
        // au-dela du rayon ne renvoient rien, elles ne comptent pas dans la surface.
        float volume = (float) area * height;
        float surface = 2.0f * area + (float) walls * height;
        // Le pourtour d'une region bornee par le rayon n'est jamais vide : des murs, des sorties, ou les deux.
        float openness = (float) exits / (walls + exits);
        return new AcousticResult(4.0f * volume / surface, openness, 0.0f, 0.0f);
    }

    @Override
    public boolean matches(SolidityWindow window, int x, int y, int z) {
        LevelZeroVerticalSlice slice = interior(y);
        if (slice == null) {
            return true;
        }
        int row = slice.airMinY();
        for (int dx = -MATCH_RADIUS; dx <= MATCH_RADIUS; dx++) {
            for (int dz = -MATCH_RADIUS; dz <= MATCH_RADIUS; dz++) {
                int bx = x + dx;
                int bz = z + dz;
                if (window.known(bx, row, bz) && (window.at(bx, row, bz) == OcclusionTrace.FULL) == open(bx, bz, slice.layerIndex())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** La tranche dont l'air jouable contient ce Y, ou {@code null}. */
    private static @Nullable LevelZeroVerticalSlice interior(int y) {
        if (!LevelZeroLayerStackLayout.isInsideStack(y)) {
            return null;
        }
        LevelZeroVerticalSlice slice = LevelZeroLayerStackLayout.sliceAtY(y);
        return y >= slice.airMinY() && y <= slice.airMaxY() ? slice : null;
    }

    /** Nombre de layouts gardes (borne : {@value #LAYOUTS}). */
    int cachedLayouts() {
        return this.layouts.size();
    }

    /** {@code true} si la colonne est de l'air jouable dans ce layer, micro-geometrie comprise. */
    boolean open(int x, int z, int layer) {
        int chunkX = Math.floorDiv(x, LevelZeroCoords.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, LevelZeroCoords.CHUNK_SIZE);
        long key = chunkX & CHUNK_MASK | (long) (chunkZ & CHUNK_MASK) << CHUNK_BITS | (long) layer << LAYER_SHIFT;
        LevelZeroLayout layout = this.layouts.getAndMoveToLast(key);
        if (layout == null) {
            layout = LevelZeroLayout.generate(chunkX, chunkZ, LevelZeroSeedResolver.resolveLayerSeed(this.layoutSeed, layer), layer);
            this.layouts.putAndMoveToLast(key, layout);
            if (this.layouts.size() > LAYOUTS) {
                this.layouts.removeFirst();
            }
        }
        return layout.isLocallyWalkable(LevelZeroCoords.worldToLocal(x), LevelZeroCoords.worldToLocal(z));
    }

    private static int index(int dx, int dz) {
        return (dz + RADIUS) * SIDE + dx + RADIUS;
    }
}
