package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La place des surfaces dans le cache de lumiere : une tuile de {@value #TILE} pixels de cote par face de bloc, un pixel de
 * cache par pixel de texture. Le thread de rendu y range les surfaces d'une section cuite, proches de la camera en hauteur,
 * puis le processeur graphique les calcule tuile par tuile. Une surface garde sa tuile tant qu'elle existe : une section
 * recuite ne recalcule que ce qui a change, et une surface a recalculer montre l'ancienne valeur jusqu'a la nouvelle, ecrite
 * d'un seul dessin. Un bloc change ne touche que les surfaces qu'il peut assombrir ({@link #blockChanged}). Quand il ne reste
 * plus de tuile, les surfaces en trop gardent la lumiere de la zone, et leur section reste en manque ({@link #starved}) : elle est
 * a reprendre, la plus proche de la camera d'abord.
 */
public final class SurfaceAtlas {

    /** Pixels de cote d'une tuile. */
    public static final int TILE = 32;
    /** Tuiles du cache en largeur. */
    public static final int ACROSS = 128;
    /** Rangees de tuiles du cache : une grande salle ouverte demande dix-huit mille tuiles sur le rayon qu'elles couvrent. */
    public static final int ROWS = 256;
    /** Tuiles du cache ; la premiere ne sert pas : 0 veut dire aucune tuile. */
    public static final int CAPACITY = ACROSS * ROWS;
    /** Blocs autour d'une surface qui lui cachent le ciel : ce qu'un bloc change y modifie, sans passer par un neon. */
    public static final float OPENING_REACH = 2.0f;
    private static final int CELL_MASK = LightCell.SECTION_CELLS - 1;
    private static final int FACE_SHIFT = 12;
    private static final int FACE_MASK = 7;
    private static final int MATERIAL_SHIFT = 16;
    private static final int PLACE_MASK = (1 << MATERIAL_SHIFT) - 1;
    private static final int BLOCK_MASK = 15;
    private static final int ROW_SHIFT = 4;
    private static final int LAYER_SHIFT = 8;
    private static final int SECTION = 16;
    private static final byte READY = 0;
    /** A recalculer : la tuile garde l'ancienne valeur, montree jusqu'a la nouvelle. */
    private static final byte STALE = 1;
    /** Pas encore calculee : l'indirection ne la montre pas. */
    private static final byte NEW = 2;
    /** Moitie de la diagonale d'une face : ce qu'un rayon peut s'ecarter du centre de sa tuile. */
    private static final float TILE_SPREAD = 0.75f;
    /** Ce qu'il faut ajouter a la portee, comptee depuis le bord d'une dalle, pour une distance entre centres : tuile, dalle et bloc. */
    private static final float REACH_MARGIN = 2.5f;
    private static final float CELL_CENTER = 0.5f;

    /** Les tuiles d'une section. */
    public static final class Job {

        private final long key;
        private final int baseX;
        private final int baseY;
        private final int baseZ;
        private int[] surfaces = new int[0];
        private int[] tiles = new int[0];
        private byte[] states = new byte[0];
        private int[] lights = new int[0];
        private int waiting;
        private int cursor;
        private boolean queued;
        private boolean moved;
        private boolean starved;

        private Job(long key) {
            this.key = key;
            this.baseX = ChunkSectionPos.unpackX(key) << ROW_SHIFT;
            this.baseY = ChunkSectionPos.unpackY(key) << ROW_SHIFT;
            this.baseZ = ChunkSectionPos.unpackZ(key) << ROW_SHIFT;
        }

        /** Clef de la section. */
        public long key() {
            return this.key;
        }

        /** Surfaces de la section qui ont une tuile, dans le format de {@link #surface}, dans l'ordre de leur place. */
        public int[] surfaces() {
            return this.surfaces;
        }

        /** Tuile de chaque surface. */
        public int[] tiles() {
            return this.tiles;
        }

        /** Neons a portee : quatre entiers chacun, position du bloc et face qui eclaire. */
        public int[] lights() {
            return this.lights;
        }

        /** Rang de la prochaine surface a calculer. */
        public int next() {
            while (this.cursor < this.states.length && this.states[this.cursor] == READY) {
                this.cursor++;
            }
            return this.cursor;
        }

        /** La surface de ce rang est calculee. */
        public void advance() {
            int rank = next();
            if (this.states[rank] == NEW) {
                this.moved = true;
            }
            this.states[rank] = READY;
            this.waiting--;
        }

        /** {@code true} si toutes les surfaces sont calculees. */
        public boolean done() {
            return this.waiting == 0;
        }

        /** {@code true} si cette surface n'a encore jamais ete calculee. */
        public boolean fresh(int rank) {
            return this.states[rank] == NEW;
        }

        /** {@code true} si cette surface a une valeur a montrer. */
        public boolean shown(int rank) {
            return this.states[rank] != NEW;
        }

        /** Surfaces a calculer. */
        public int waiting() {
            return this.waiting;
        }

        private int rank(int place) {
            int low = 0;
            int high = this.surfaces.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int at = this.surfaces[middle] & PLACE_MASK;
                if (at == place) {
                    return middle;
                }
                if (at < place) {
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return -low - 1;
        }

        private void stale(int rank) {
            if (this.states[rank] == READY) {
                this.states[rank] = STALE;
                this.waiting++;
                this.cursor = Math.min(this.cursor, rank);
            }
        }

        /** Recompte les surfaces a calculer, et repart de la premiere : les rangs ont pu bouger. */
        private void recount() {
            this.waiting = 0;
            this.cursor = 0;
            for (byte state : this.states) {
                if (state != READY) {
                    this.waiting++;
                }
            }
        }
    }

    private final IntArrayList free = new IntArrayList();
    private final Long2ObjectMap<Job> jobs = new Long2ObjectOpenHashMap<>();
    private final Deque<Job> pending = new ArrayDeque<>();
    private final List<Job> moved = new ArrayList<>();
    private int limit = CAPACITY;

    /** Un cache vide. */
    public SurfaceAtlas() {
        reset();
    }

    /** Toutes les tuiles sont libres, plus rien n'est rangee ni a calculer. */
    public void reset() {
        this.free.clear();
        for (int tile = this.limit - 1; tile > 0; tile--) {
            this.free.add(tile);
        }
        this.jobs.clear();
        this.pending.clear();
        this.moved.clear();
    }

    /**
     * Ramene le cache a ce nombre de tuiles, vide : un cache plein sans la salle qui le remplit (tests du client).
     *
     * @param tiles tuiles du cache, de 1 a {@link #CAPACITY}
     */
    public void limit(int tiles) {
        this.limit = tiles;
        reset();
    }

    /** Une surface, sur un entier : sa cellule dans la section, sa face et la matiere du bloc qui la porte. */
    public static int surface(int cell, int face, int material) {
        return cell | face << FACE_SHIFT | material << MATERIAL_SHIFT;
    }

    /** La cellule d'une surface dans sa section. */
    public static int cell(int surface) {
        return surface & CELL_MASK;
    }

    /** La face d'une surface. */
    public static int face(int surface) {
        return surface >> FACE_SHIFT & FACE_MASK;
    }

    /** La matiere du bloc qui porte une surface. */
    public static int material(int surface) {
        return surface >>> MATERIAL_SHIFT;
    }

    /**
     * Range une section cuite, a la place de ce qu'elle occupait : ce qui ne change pas garde sa tuile et sa valeur.
     *
     * @param key clef de la section
     * @param baseY Y du premier bloc de la section
     * @param surfaces ses surfaces, dans l'ordre de leur place
     * @param lights ses neons ; d'autres neons remettent toutes ses surfaces a calculer
     * @param minY premier Y retenu : seules les surfaces proches de la camera en hauteur ont une tuile
     * @param maxY dernier Y retenu
     */
    public void accept(long key, int baseY, int[] surfaces, int[] lights, int minY, int maxY) {
        Job job = this.jobs.get(key);
        if (job == null) {
            job = new Job(key);
            this.jobs.put(key, job);
        }
        boolean lit = !Arrays.equals(job.lights, lights);
        int[] wanted = new int[surfaces.length];
        int count = 0;
        for (int surface : surfaces) {
            int y = baseY + (cell(surface) >> ROW_SHIFT & BLOCK_MASK);
            if (y >= minY && y <= maxY) {
                wanted[count++] = surface;
            }
        }
        merge(job, wanted, count, lights, lit);
    }

    /** Pose les surfaces d'une section : celles qui partent rendent leur tuile, les autres la gardent, les nouvelles en prennent une. */
    private void merge(Job job, int[] wanted, int count, int[] lights, boolean lit) {
        int[] oldSurfaces = job.surfaces;
        int[] oldTiles = job.tiles;
        byte[] oldStates = job.states;
        int[] kept = new int[count];
        byte[] states = new byte[count];
        int from = 0;
        for (int i = 0; i < count; i++) {
            int place = wanted[i] & PLACE_MASK;
            while (from < oldSurfaces.length && (oldSurfaces[from] & PLACE_MASK) < place) {
                this.free.add(oldTiles[from++]);
            }
            if (from < oldSurfaces.length && (oldSurfaces[from] & PLACE_MASK) == place) {
                kept[i] = oldTiles[from];
                boolean other = oldSurfaces[from] != wanted[i];
                states[i] = oldStates[from] == READY && (lit || other) ? STALE : oldStates[from];
                from++;
            } else {
                states[i] = NEW;
            }
        }
        while (from < oldSurfaces.length) {
            this.free.add(oldTiles[from++]);
        }
        int held = 0;
        for (int i = 0; i < count; i++) {
            if (kept[i] == 0) {
                if (this.free.isEmpty()) {
                    continue;
                }
                kept[i] = this.free.removeInt(this.free.size() - 1);
            }
            wanted[held] = wanted[i];
            kept[held] = kept[i];
            states[held++] = states[i];
        }
        job.surfaces = Arrays.copyOf(wanted, held);
        job.tiles = Arrays.copyOf(kept, held);
        job.states = Arrays.copyOf(states, held);
        job.lights = lights;
        job.recount();
        job.starved = held < count;
        if (held != oldSurfaces.length || anyNew(job)) {
            job.moved = true;
        }
        enqueue(job, false);
    }

    private static boolean anyNew(Job job) {
        for (byte state : job.states) {
            if (state == NEW) {
                return true;
            }
        }
        return false;
    }

    private void enqueue(Job job, boolean first) {
        if (job.waiting == 0) {
            if (job.moved && !this.moved.contains(job)) {
                this.moved.add(job);
            }
            return;
        }
        if (job.queued) {
            if (first) {
                this.pending.remove(job);
                this.pending.addFirst(job);
            }
            return;
        }
        job.queued = true;
        if (first) {
            this.pending.addFirst(job);
        } else {
            this.pending.addLast(job);
        }
    }

    /** Rend les tuiles d'une section. */
    public void release(long key) {
        Job job = this.jobs.remove(key);
        if (job != null) {
            this.free.addElements(this.free.size(), job.tiles);
            this.pending.remove(job);
            this.moved.remove(job);
        }
    }

    /**
     * Un bloc vient de changer dans la copie du monde : ses faces et celles de ses voisins apparaissent ou s'en vont tout de
     * suite, sans attendre la cuisson, et seules les surfaces qu'il peut assombrir repartent a calculer.
     *
     * @param blocks copie du monde, a jour
     * @param x position du bloc
     * @param y position du bloc
     * @param z position du bloc
     * @param light {@code true} si le bloc emet ou emettait : tout ce qu'il atteint change
     * @param reach portee d'une dalle, en blocs
     * @param lightRadius ecart des rayons autour du centre d'une dalle, en blocs
     * @param minY premier Y retenu
     * @param maxY dernier Y retenu
     */
    public void blockChanged(LightBlocks blocks, int x, int y, int z, boolean light, float reach, float lightRadius, int minY, int maxY) {
        byte block = blocks.block(x, y, z);
        for (int face = 0; face < LightCell.FACES; face++) {
            // La face de la cellule (x, y, z) posee contre le bloc derriere elle : elle n'existe que si la cellule est de l'air.
            updateSurface(blocks, x, y, z, face, block == LightBlocks.AIR, blocks.block(x - LightCell.NORMAL_X[face], y - LightCell.NORMAL_Y[face], z - LightCell.NORMAL_Z[face]),
                    minY, maxY);
            // La face de la cellule voisine posee contre ce bloc.
            int cellX = x + LightCell.NORMAL_X[face];
            int cellY = y + LightCell.NORMAL_Y[face];
            int cellZ = z + LightCell.NORMAL_Z[face];
            updateSurface(blocks, cellX, cellY, cellZ, face, blocks.block(cellX, cellY, cellZ) == LightBlocks.AIR, block, minY, maxY);
        }
        float expand = lightRadius + TILE_SPREAD;
        for (Job job : this.jobs.values()) {
            if (touchable(job, x, y, z, reach)) {
                darken(job, x, y, z, light, reach, expand);
            }
        }
    }

    /** {@code true} si une section est assez proche d'un bloc pour qu'il change quelque chose a ses surfaces. */
    private static boolean touchable(Job job, int x, int y, int z, float reach) {
        float margin = reach + REACH_MARGIN;
        return x >= job.baseX - margin && x < job.baseX + SECTION + margin && y >= job.baseY - margin && y < job.baseY + SECTION + margin
                && z >= job.baseZ - margin && z < job.baseZ + SECTION + margin;
    }

    /** Pose l'etat d'une surface d'apres la copie du monde : presente si sa cellule est de l'air et son bloc opaque. */
    private void updateSurface(LightBlocks blocks, int x, int y, int z, int face, boolean air, byte behind, int minY, int maxY) {
        Job job = this.jobs.get(ChunkSectionPos.asLong(x >> ROW_SHIFT, y >> ROW_SHIFT, z >> ROW_SHIFT));
        if (job == null || y < minY || y > maxY) {
            return;
        }
        int cell = (z & BLOCK_MASK) << LAYER_SHIFT | (y & BLOCK_MASK) << ROW_SHIFT | x & BLOCK_MASK;
        int place = cell | face << FACE_SHIFT;
        int rank = job.rank(place);
        boolean present = air && behind != LightBlocks.AIR;
        if (present == rank >= 0) {
            if (present && material(job.surfaces[rank]) != (behind & 0xFF)) {
                job.surfaces[rank] = surface(cell, face, behind & 0xFF);
                job.stale(rank);
                enqueue(job, true);
            }
            return;
        }
        if (present) {
            insert(job, -rank - 1, surface(cell, face, behind & 0xFF));
        } else {
            remove(job, rank);
        }
        job.moved = true;
        enqueue(job, true);
    }

    private void insert(Job job, int rank, int surface) {
        if (this.free.isEmpty()) {
            job.starved = true;
            return;
        }
        int size = job.surfaces.length;
        int[] surfaces = Arrays.copyOf(job.surfaces, size + 1);
        int[] tiles = Arrays.copyOf(job.tiles, size + 1);
        byte[] states = Arrays.copyOf(job.states, size + 1);
        System.arraycopy(surfaces, rank, surfaces, rank + 1, size - rank);
        System.arraycopy(tiles, rank, tiles, rank + 1, size - rank);
        System.arraycopy(states, rank, states, rank + 1, size - rank);
        surfaces[rank] = surface;
        tiles[rank] = this.free.removeInt(this.free.size() - 1);
        states[rank] = NEW;
        job.surfaces = surfaces;
        job.tiles = tiles;
        job.states = states;
        job.recount();
    }

    private void remove(Job job, int rank) {
        this.free.add(job.tiles[rank]);
        int size = job.surfaces.length - 1;
        int[] surfaces = Arrays.copyOf(job.surfaces, size);
        int[] tiles = Arrays.copyOf(job.tiles, size);
        byte[] states = Arrays.copyOf(job.states, size);
        System.arraycopy(job.surfaces, rank + 1, surfaces, rank, size - rank);
        System.arraycopy(job.tiles, rank + 1, tiles, rank, size - rank);
        System.arraycopy(job.states, rank + 1, states, rank, size - rank);
        job.surfaces = surfaces;
        job.tiles = tiles;
        job.states = states;
        job.recount();
    }

    /** Remet a calculer les surfaces d'une section que ce bloc peut assombrir. */
    private void darken(Job job, int x, int y, int z, boolean light, float reach, float expand) {
        float blockX = x + CELL_CENTER;
        float blockY = y + CELL_CENTER;
        float blockZ = z + CELL_CENTER;
        float opening = (OPENING_REACH + 1.0f) * (OPENING_REACH + 1.0f);
        float reach2 = (reach + REACH_MARGIN) * (reach + REACH_MARGIN);
        int[] lights = nearBlock(job.lights, blockX, blockY, blockZ, reach2);
        int before = job.waiting;
        for (int rank = 0; rank < job.surfaces.length; rank++) {
            int surface = job.surfaces[rank];
            int cell = cell(surface);
            int face = face(surface);
            float px = job.baseX + (cell & BLOCK_MASK) + CELL_CENTER - LightCell.NORMAL_X[face] * CELL_CENTER;
            float py = job.baseY + (cell >> ROW_SHIFT & BLOCK_MASK) + CELL_CENTER - LightCell.NORMAL_Y[face] * CELL_CENTER;
            float pz = job.baseZ + (cell >> LAYER_SHIFT) + CELL_CENTER - LightCell.NORMAL_Z[face] * CELL_CENTER;
            float dx = px - blockX;
            float dy = py - blockY;
            float dz = pz - blockZ;
            float distance2 = dx * dx + dy * dy + dz * dz;
            if (distance2 <= opening || distance2 <= reach2 && (light || shades(lights, px, py, pz, x, y, z, expand))) {
                job.stale(rank);
            }
        }
        if (job.waiting != before) {
            enqueue(job, true);
        }
    }

    /** Les neons d'une liste assez proches d'un point pour qu'un rayon vers eux y passe. */
    private static int[] nearBlock(int[] lights, float x, float y, float z, float reach2) {
        int[] near = new int[lights.length];
        int count = 0;
        for (int i = 0; i < lights.length; i += SectionBaker.LIGHT_INTS) {
            float dx = lights[i] + CELL_CENTER - x;
            float dy = lights[i + 1] + CELL_CENTER - y;
            float dz = lights[i + 2] + CELL_CENTER - z;
            if (dx * dx + dy * dy + dz * dz <= reach2) {
                near[count] = lights[i];
                near[count + 1] = lights[i + 1];
                near[count + 2] = lights[i + 2];
                near[count + 3] = lights[i + 3];
                count += SectionBaker.LIGHT_INTS;
            }
        }
        return Arrays.copyOf(near, count);
    }

    /** {@code true} si un rayon d'une surface vers l'un de ces neons peut passer par ce bloc. */
    private static boolean shades(int[] lights, float px, float py, float pz, int x, int y, int z, float expand) {
        for (int i = 0; i < lights.length; i += SectionBaker.LIGHT_INTS) {
            int face = lights[i + 3];
            float lx = lights[i] + CELL_CENTER + LightCell.NORMAL_X[face] * CELL_CENTER;
            float ly = lights[i + 1] + CELL_CENTER + LightCell.NORMAL_Y[face] * CELL_CENTER;
            float lz = lights[i + 2] + CELL_CENTER + LightCell.NORMAL_Z[face] * CELL_CENTER;
            if (crosses(px, py, pz, lx, ly, lz, x - expand, y - expand, z - expand, x + 1.0f + expand, y + 1.0f + expand, z + 1.0f + expand)) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} si un segment traverse une boite. */
    static boolean crosses(float ax, float ay, float az, float bx, float by, float bz, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float[] span = {0.0f, 1.0f};
        return clip(ax, bx - ax, minX, maxX, span) && clip(ay, by - ay, minY, maxY, span) && clip(az, bz - az, minZ, maxZ, span);
    }

    /** Resserre l'intervalle du segment entre deux plans d'un axe ; {@code false} s'il n'en reste rien. */
    private static boolean clip(float from, float direction, float min, float max, float[] span) {
        if (direction == 0.0f) {
            return from >= min && from <= max;
        }
        float near = (min - from) / direction;
        float far = (max - from) / direction;
        span[0] = Math.max(span[0], Math.min(near, far));
        span[1] = Math.min(span[1], Math.max(near, far));
        return span[0] <= span[1];
    }

    /**
     * Une section entiere de la copie vient de changer (une colonne recue, trop de blocs d'un coup) : tout ce que ses blocs
     * peuvent atteindre repart a calculer. Les surfaces, elles, suivent a la prochaine cuisson.
     *
     * @param key clef de la section
     * @param reach portee d'une dalle, en blocs
     */
    public void sectionChanged(long key, float reach) {
        int x = ChunkSectionPos.unpackX(key) << ROW_SHIFT;
        int y = ChunkSectionPos.unpackY(key) << ROW_SHIFT;
        int z = ChunkSectionPos.unpackZ(key) << ROW_SHIFT;
        for (Job job : this.jobs.values()) {
            if (Math.abs(job.baseX - x) < SECTION + reach && Math.abs(job.baseY - y) < SECTION + reach && Math.abs(job.baseZ - z) < SECTION + reach) {
                for (int rank = 0; rank < job.surfaces.length; rank++) {
                    job.stale(rank);
                }
                enqueue(job, false);
            }
        }
    }

    /** La section en cours de calcul, ou {@code null} si tout est calcule. */
    public @Nullable Job current() {
        return this.pending.peekFirst();
    }

    /** La section en cours est calculee. */
    public void finish() {
        Job job = this.pending.removeFirst();
        job.queued = false;
        if (job.moved && !this.moved.contains(job)) {
            this.moved.add(job);
        }
    }

    /** Sections dont l'indirection doit suivre, a vider : leurs surfaces ont bouge, ou de nouvelles sont pretes. */
    public List<Job> takeMoved() {
        if (this.moved.isEmpty()) {
            return List.of();
        }
        List<Job> taken = new ArrayList<>(this.moved);
        this.moved.clear();
        for (Job job : taken) {
            job.moved = false;
        }
        return taken;
    }

    /** Tuiles libres. */
    public int freeTiles() {
        return this.free.size();
    }

    /** {@code true} si cette section a des tuiles. */
    public boolean holds(long key) {
        return this.jobs.containsKey(key);
    }

    /** {@code true} si cette section a des surfaces sans tuile : le cache n'en avait plus. */
    public boolean starved(long key) {
        Job job = this.jobs.get(key);
        return job != null && job.starved;
    }

    /** {@code true} si une section au moins attend des tuiles (tests du client). */
    public boolean starving() {
        for (Job job : this.jobs.values()) {
            if (job.starved) {
                return true;
            }
        }
        return false;
    }

    /** Les tuiles d'une section, ou {@code null}. */
    public @Nullable Job job(long key) {
        return this.jobs.get(key);
    }
}
