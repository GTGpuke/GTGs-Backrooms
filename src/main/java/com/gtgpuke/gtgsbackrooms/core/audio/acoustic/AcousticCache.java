package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.BlockPos;

/**
 * La derniere acoustique connue de chaque cellule d'un monde (cubes de {@value #CELL} blocs) : un son qui part dans une
 * cellule deja evaluee demarre avec elle, sans attendre le calcul ; une cellule revue il y a longtemps est recalculee
 * (un joueur pose et casse des blocs). Partagee entre le thread de rendu, qui lit, et le thread de calcul, qui ecrit ;
 * chaque acces tient le verrou, court.
 */
public final class AcousticCache {

    /** Blocs de cote d'une cellule. */
    public static final int CELL = 4;
    /**
     * Plafond des cellules gardees : le Level 0 est sans fin, et une longue partie y evaluerait des cellules sans limite.
     * Atteint, les cellules plus vieilles que {@link #PRUNE_AGE_TICKS} sont oubliees, et toutes si cela ne suffit pas.
     */
    static final int MAX_CELLS = 16384;
    /** Age, en ticks, d'une cellule oubliee au plafond (cinq minutes). */
    static final int PRUNE_AGE_TICKS = 6000;
    private static final int CELL_SHIFT = 2;

    /** Une cellule evaluee : son resultat et le tick de l'evaluation. */
    private static final class Entry {

        private AcousticResult result;
        private long tick;

        private Entry(AcousticResult result, long tick) {
            this.result = result;
            this.tick = tick;
        }
    }

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

    /** Cle de la cellule d'un point. */
    public static long cellOf(double x, double y, double z) {
        return BlockPos.asLong((int) Math.floor(x) >> CELL_SHIFT, (int) Math.floor(y) >> CELL_SHIFT, (int) Math.floor(z) >> CELL_SHIFT);
    }

    /** Dernier resultat de cette cellule, ou {@code null}. */
    public synchronized @Nullable AcousticResult get(long cell) {
        Entry entry = this.entries.get(cell);
        return entry == null ? null : entry.result;
    }

    /** {@code true} si la cellule a ete evaluee il y a moins de {@code maxAge} ticks. */
    public synchronized boolean fresh(long cell, long tick, int maxAge) {
        Entry entry = this.entries.get(cell);
        return entry != null && tick - entry.tick < maxAge;
    }

    /** Pose le resultat d'une cellule, evalue a ce tick. */
    public synchronized void put(long cell, AcousticResult result, long tick) {
        Entry entry = this.entries.get(cell);
        if (entry == null) {
            if (this.entries.size() >= MAX_CELLS) {
                prune(tick);
            }
            this.entries.put(cell, new Entry(result, tick));
        } else {
            entry.result = result;
            entry.tick = tick;
        }
    }

    /** Nombre de cellules evaluees. */
    public synchronized int size() {
        return this.entries.size();
    }

    /** Plus rien. */
    public synchronized void clear() {
        this.entries.clear();
    }

    /** Au plafond (evenement rare, le parcours est admis) : les cellules anciennes partent, toutes s'il le faut. */
    private void prune(long tick) {
        this.entries.values().removeIf(entry -> tick - entry.tick > PRUNE_AGE_TICKS);
        if (this.entries.size() >= MAX_CELLS) {
            this.entries.clear();
        }
    }
}
