package com.gtgpuke.gtgsbackrooms.core.render.light;

/**
 * Les neons les plus proches d'une camera parmi ceux qu'on lui propose : une liste bornee, triee par distance, qui ne
 * garde que les meilleurs, sans rien allouer apres sa creation. Une vue en remplit une par image.
 */
public final class NeonSelection {

    private final long[] positions;
    private final double[] squaredDistances;
    private int count;

    /**
     * @param capacity nombre de neons gardes au plus
     */
    public NeonSelection(int capacity) {
        this.positions = new long[capacity];
        this.squaredDistances = new double[capacity];
    }

    /** Vide la liste. */
    public void clear() {
        this.count = 0;
    }

    /**
     * Propose un neon : garde si la liste n'est pas pleine ou s'il est plus proche que le plus lointain garde.
     *
     * @param position position empaquetee
     * @param squaredDistance carre de sa distance a la camera
     */
    public void offer(long position, double squaredDistance) {
        int capacity = this.positions.length;
        if (this.count == capacity && squaredDistance >= this.squaredDistances[capacity - 1]) {
            return;
        }
        int at = this.count < capacity ? this.count : capacity - 1;
        while (at > 0 && this.squaredDistances[at - 1] > squaredDistance) {
            this.positions[at] = this.positions[at - 1];
            this.squaredDistances[at] = this.squaredDistances[at - 1];
            at--;
        }
        this.positions[at] = position;
        this.squaredDistances[at] = squaredDistance;
        if (this.count < capacity) {
            this.count++;
        }
    }

    /**
     * La part d'un neon dans ce qui ne compte que les plus proches : 1 pres de la camera, 0 au rang ou la liste s'arrete et a la distance
     * ou la recherche s'arrete. Elle suit la distance sans saut : un neon s'eteint avant de sortir, et son voisin s'allume en y entrant.
     *
     * @param index rang du neon
     * @param nearest neons qui comptent
     * @param fade distance, en blocs, sur laquelle la part passe de 1 a 0
     * @param searched distance de la recherche, en blocs
     * @return part, de 0 a 1
     */
    public float nearShare(int index, int nearest, float fade, float searched) {
        float distance = (float) Math.sqrt(this.squaredDistances[index]);
        float last = nearest <= this.count ? (float) Math.sqrt(this.squaredDistances[nearest - 1]) : searched;
        float room = Math.min(last, searched) - distance;
        return room <= 0.0f ? 0.0f : Math.min(room / fade, 1.0f);
    }

    /** Nombre de neons gardes. */
    public int count() {
        return this.count;
    }

    /** Capacite de la liste. */
    public int capacity() {
        return this.positions.length;
    }

    /** Position empaquetee du neon de ce rang, du plus proche au plus lointain. */
    public long position(int index) {
        return this.positions[index];
    }

    /** Carre de la distance du neon de ce rang. */
    public double squaredDistance(int index) {
        return this.squaredDistances[index];
    }
}
