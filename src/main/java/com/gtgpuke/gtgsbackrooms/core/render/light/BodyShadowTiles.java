package com.gtgpuke.gtgsbackrooms.core.render.light;

import org.joml.FrustumIntersection;

import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.render.light.bake.SurfaceAtlas;

/**
 * Les tuiles du cache de surfaces ou un corps peut porter son ombre et que la camera voit : celles que la passe des ombres des corps
 * calcule a chaque image. Une tuile compte si sa cellule est a portee d'un corps retenu (la portee des ombres, plus la demi-diagonale
 * d'un bloc : chacun de ses texels est alors a portee), si elle est publiee (le terrain ne lit que celles-la) et si sa cellule, grossie
 * d'un bloc, entre dans le frustum de la camera (la marge couvre ce que le frustum relu ne porte pas exactement : un cadre a demi visible
 * au bord de l'ecran gardait une tuile sans ombre). Chaque tuile recoit un emplacement du cache des ombres, dans l'ordre ou elle est trouvee, une fois
 * meme si plusieurs corps en sont proches. Sans allocation par image : les listes sont preparees une fois.
 */
public final class BodyShadowTiles {

    /** Distance a un corps, en blocs, au-dela de laquelle il ne fait plus d'ombre : celle de {@code gtgs_casterRemoved}. */
    public static final float REACH = 10.0f;
    /** Emplacements du cache des ombres : tuiles calculees par image, au plus. */
    public static final int SLOTS = 2048;
    /** Pixels de cote d'un emplacement du cache des ombres : une tuile et un texel de bordure de chaque cote. */
    public static final int SLOT = SurfaceAtlas.TILE + 2;
    /** Emplacements par rangee de l'atlas des ombres. */
    public static final int SLOTS_ACROSS = 64;
    /** Largeur de l'atlas des ombres, en pixels. */
    public static final int ATLAS_WIDTH = SLOTS_ACROSS * SLOT;
    /** Hauteur de l'atlas des ombres, en pixels. */
    public static final int ATLAS_HEIGHT = SLOTS / SLOTS_ACROSS * SLOT;
    /** Demi-diagonale d'un bloc : ce qu'un texel d'une cellule peut s'ecarter de son centre. */
    static final float CELL_MARGIN = 0.8660254f;
    /** Ce dont une cellule est grossie, en blocs, avant le test du frustum. */
    static final float FRUSTUM_MARGIN = 1.0f;
    private static final int SECTION_SHIFT = 4;
    private static final int SECTION_MASK = 15;

    private final int[] tiles = new int[SLOTS];
    private final int[] cells = new int[SLOTS * 3];
    private final int[] faces = new int[SLOTS];
    private final int[] stamps = new int[SurfaceAtlas.CAPACITY];
    private int stamp;
    private int count;
    private int dropped;

    /**
     * Cherche les tuiles a calculer.
     *
     * @param atlas place des surfaces de la zone liee
     * @param frustum frustum de la camera, dans ses axes, relatif a sa position
     * @param casters corps retenus, {@link ShadowCasters#BODY_VALUES} valeurs chacun, relatifs au bloc de la camera
     * @param bodies nombre de corps
     * @param originX bloc de la camera
     * @param originY bloc de la camera
     * @param originZ bloc de la camera
     * @param fractX reste de la camera dans son bloc
     * @param fractY reste de la camera dans son bloc
     * @param fractZ reste de la camera dans son bloc
     * @return nombre de tuiles trouvees, {@value #SLOTS} au plus
     */
    public int collect(SurfaceAtlas atlas, FrustumIntersection frustum, float[] casters, int bodies, int originX, int originY, int originZ,
            float fractX, float fractY, float fractZ) {
        this.stamp++;
        this.count = 0;
        this.dropped = 0;
        float reach = REACH + CELL_MARGIN;
        for (int b = 0; b < bodies; b++) {
            float bodyX = casters[b * ShadowCasters.BODY_VALUES];
            float bodyY = casters[b * ShadowCasters.BODY_VALUES + 1];
            float bodyZ = casters[b * ShadowCasters.BODY_VALUES + 2];
            int minX = (int) Math.floor(originX + bodyX - reach) >> SECTION_SHIFT;
            int maxX = (int) Math.floor(originX + bodyX + reach) >> SECTION_SHIFT;
            int minY = (int) Math.floor(originY + bodyY - reach) >> SECTION_SHIFT;
            int maxY = (int) Math.floor(originY + bodyY + reach) >> SECTION_SHIFT;
            int minZ = (int) Math.floor(originZ + bodyZ - reach) >> SECTION_SHIFT;
            int maxZ = (int) Math.floor(originZ + bodyZ + reach) >> SECTION_SHIFT;
            for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                    for (int sectionX = minX; sectionX <= maxX; sectionX++) {
                        SurfaceAtlas.Job job = atlas.job(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ));
                        if (job != null) {
                            collect(job, frustum, bodyX, bodyY, bodyZ, reach * reach, originX, originY, originZ, fractX, fractY, fractZ);
                        }
                    }
                }
            }
        }
        return this.count;
    }

    private void collect(SurfaceAtlas.Job job, FrustumIntersection frustum, float bodyX, float bodyY, float bodyZ, float reach2, int originX, int originY,
            int originZ, float fractX, float fractY, float fractZ) {
        long key = job.key();
        int baseX = (ChunkSectionPos.unpackX(key) << SECTION_SHIFT) - originX;
        int baseY = (ChunkSectionPos.unpackY(key) << SECTION_SHIFT) - originY;
        int baseZ = (ChunkSectionPos.unpackZ(key) << SECTION_SHIFT) - originZ;
        int[] surfaces = job.surfaces();
        int[] held = job.tiles();
        for (int i = 0; i < surfaces.length; i++) {
            int tile = held[i];
            if (!job.shown(i) || this.stamps[tile] == this.stamp) {
                continue;
            }
            int cell = SurfaceAtlas.cell(surfaces[i]);
            int cellX = baseX + (cell & SECTION_MASK);
            int cellY = baseY + (cell >> SECTION_SHIFT & SECTION_MASK);
            int cellZ = baseZ + (cell >> SECTION_SHIFT * 2);
            float dx = cellX + 0.5f - bodyX;
            float dy = cellY + 0.5f - bodyY;
            float dz = cellZ + 0.5f - bodyZ;
            if (dx * dx + dy * dy + dz * dz > reach2
                    || !frustum.testAab(cellX - FRUSTUM_MARGIN - fractX, cellY - FRUSTUM_MARGIN - fractY, cellZ - FRUSTUM_MARGIN - fractZ,
                            cellX + 1.0f + FRUSTUM_MARGIN - fractX, cellY + 1.0f + FRUSTUM_MARGIN - fractY, cellZ + 1.0f + FRUSTUM_MARGIN - fractZ)) {
                continue;
            }
            this.stamps[tile] = this.stamp;
            if (this.count == SLOTS) {
                this.dropped++;
                continue;
            }
            this.tiles[this.count] = tile;
            this.cells[this.count * 3] = cellX;
            this.cells[this.count * 3 + 1] = cellY;
            this.cells[this.count * 3 + 2] = cellZ;
            this.faces[this.count] = SurfaceAtlas.face(surfaces[i]);
            this.count++;
        }
    }

    /** Tuiles trouvees par la derniere recherche, dans l'ordre de leur emplacement. */
    public int[] tiles() {
        return this.tiles;
    }

    /** Cellule d'air devant chaque tuile trouvee, relative au bloc de la camera, trois valeurs par tuile. */
    public int[] cells() {
        return this.cells;
    }

    /** Face de chaque tuile trouvee. */
    public int[] faces() {
        return this.faces;
    }

    /** Tuiles qui auraient compte, mais que la derniere recherche a laissees faute d'emplacement. */
    public int dropped() {
        return this.dropped;
    }
}
