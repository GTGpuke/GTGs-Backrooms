package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La copie du monde que lit le calcul acoustique hors du thread de rendu : la solidite de chaque bloc
 * ({@link OcclusionTrace#AIR}, {@link OcclusionTrace#PARTIAL}, {@link OcclusionTrace#FULL}) des sections copiees, par cle
 * de section ({@link ChunkSectionPos#asLong}). Le thread de rendu y pose des sections entieres (jamais modifiees
 * ensuite : un bloc qui change fait recopier sa section), le thread de calcul en tire une fenetre
 * ({@link SolidityWindow}) ; chaque acces tient le verrou de la grille, court.
 */
public final class SolidityGrid {

    /** Une section sans rien de solide : partagee, jamais ecrite. */
    public static final byte[] EMPTY = new byte[SolidityWindow.SECTION_BLOCKS];

    private final Long2ObjectOpenHashMap<byte[]> sections = new Long2ObjectOpenHashMap<>();
    private final LongArrayList removed = new LongArrayList();

    /**
     * Pose une section copiee, ou l'oublie.
     *
     * @param sectionKey cle de la section
     * @param blocks solidite de ses blocs, {@link #EMPTY} si rien n'y est solide, {@code null} pour l'oublier
     */
    public synchronized void setSection(long sectionKey, @Nullable byte[] blocks) {
        if (blocks == null) {
            this.sections.remove(sectionKey);
        } else {
            this.sections.put(sectionKey, blocks);
        }
    }

    /** {@code true} si cette section est copiee. */
    public synchronized boolean has(long sectionKey) {
        return this.sections.containsKey(sectionKey);
    }

    /**
     * Une colonne de chunk dechargee : ses sections sont oubliees.
     *
     * @param chunkX colonne
     * @param chunkZ colonne
     * @param bottomSectionY premiere section du monde
     * @param topSectionY section au-dessus de la derniere
     */
    public synchronized void clearColumn(int chunkX, int chunkZ, int bottomSectionY, int topSectionY) {
        for (int sectionY = bottomSectionY; sectionY < topSectionY; sectionY++) {
            this.sections.remove(ChunkSectionPos.asLong(chunkX, sectionY, chunkZ));
        }
    }

    /**
     * Oublie les sections a plus de {@code radius} sections (sur un axe au moins) de chacun de ces points d'ancrage
     * (l'auditeur, l'arrivee des portails qu'il voit) : la memoire de la copie les suit.
     *
     * @param anchorX sections des points d'ancrage
     * @param anchorY sections des points d'ancrage
     * @param anchorZ sections des points d'ancrage
     * @param anchors nombre de points d'ancrage, au moins un
     * @param radius sections gardees autour de chaque point, sur chaque axe
     * @return nombre de sections oubliees
     */
    public synchronized int retainAround(int[] anchorX, int[] anchorY, int[] anchorZ, int anchors, int radius) {
        this.removed.clear();
        for (LongIterator keys = this.sections.keySet().iterator(); keys.hasNext();) {
            long key = keys.nextLong();
            boolean near = false;
            for (int i = 0; i < anchors && !near; i++) {
                near = Math.abs(ChunkSectionPos.unpackX(key) - anchorX[i]) <= radius
                        && Math.abs(ChunkSectionPos.unpackY(key) - anchorY[i]) <= radius
                        && Math.abs(ChunkSectionPos.unpackZ(key) - anchorZ[i]) <= radius;
            }
            if (!near) {
                this.removed.add(key);
            }
        }
        for (int i = 0; i < this.removed.size(); i++) {
            this.sections.remove(this.removed.getLong(i));
        }
        return this.removed.size();
    }

    /** Nombre de sections copiees. */
    public synchronized int size() {
        return this.sections.size();
    }

    /** Plus aucune section. */
    public synchronized void clear() {
        this.sections.clear();
    }

    /**
     * Remplit une fenetre avec les sections copiees autour d'une section centrale.
     *
     * @param sectionX section centrale
     * @param sectionY section centrale
     * @param sectionZ section centrale
     * @param window fenetre a remplir
     */
    public synchronized void window(int sectionX, int sectionY, int sectionZ, SolidityWindow window) {
        window.center(sectionX, sectionY, sectionZ);
        for (int dy = -SolidityWindow.RADIUS; dy <= SolidityWindow.RADIUS; dy++) {
            for (int dz = -SolidityWindow.RADIUS; dz <= SolidityWindow.RADIUS; dz++) {
                for (int dx = -SolidityWindow.RADIUS; dx <= SolidityWindow.RADIUS; dx++) {
                    byte[] blocks = this.sections.get(ChunkSectionPos.asLong(sectionX + dx, sectionY + dy, sectionZ + dz));
                    if (blocks != null) {
                        window.set(sectionX + dx, sectionY + dy, sectionZ + dz, blocks);
                    }
                }
            }
        }
    }
}
