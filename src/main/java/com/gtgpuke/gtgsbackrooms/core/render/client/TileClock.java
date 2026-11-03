package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import com.gtgpuke.gtgsbackrooms.core.render.light.TileBudget;

/**
 * Le temps d'une passe de tuiles, au processeur et a la carte graphique : deux horodatages de la carte autour de la passe
 * ({@code GL_TIMESTAMP}, qui ne s'ouvrent ni ne se ferment et ne genent donc aucune requete de {@link GpuTimer}), lus quelques images plus
 * tard sans jamais attendre, et le temps processeur de la boucle. Le plus long des deux nourrit le budget de la passe. Sans requetes de
 * temps, le processeur seul.
 */
final class TileClock {

    private static final int RING = 4;
    private static final float NANOS_PER_MILLISECOND = 1.0e6f;

    private final TileBudget budget;
    private final int[] tiles = new int[RING];
    private final float[] cpu = new float[RING];
    private final boolean[] pending = new boolean[RING];
    private int[] starts;
    private int[] ends;
    private int write;
    private int read;
    private long began;
    private boolean open;

    TileClock(TileBudget budget) {
        this.budget = budget;
    }

    /** Juste avant la passe. */
    void begin() {
        this.began = System.nanoTime();
        this.open = false;
        if (!GpuTimer.supported()) {
            return;
        }
        if (this.starts == null) {
            this.starts = new int[RING];
            this.ends = new int[RING];
            GL15.glGenQueries(this.starts);
            GL15.glGenQueries(this.ends);
        }
        if (this.pending[this.write]) {
            // Anneau plein : la carte est en retard de quatre images, cette passe n'est pas mesuree.
            return;
        }
        GL33.glQueryCounter(this.starts[this.write], GL33.GL_TIMESTAMP);
        this.open = true;
    }

    /**
     * Juste apres la passe.
     *
     * @param count tuiles calculees
     */
    void end(int count) {
        float spent = (System.nanoTime() - this.began) / NANOS_PER_MILLISECOND;
        if (this.open) {
            GL33.glQueryCounter(this.ends[this.write], GL33.GL_TIMESTAMP);
            this.tiles[this.write] = count;
            this.cpu[this.write] = spent;
            this.pending[this.write] = true;
            this.write = (this.write + 1) % RING;
            this.open = false;
        } else if (this.starts == null) {
            this.budget.measured(count, spent);
        }
        while (this.starts != null && this.pending[this.read]) {
            if (GL15.glGetQueryObjecti(this.ends[this.read], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                break;
            }
            long gpu = GL33.glGetQueryObjectui64(this.ends[this.read], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjectui64(this.starts[this.read], GL15.GL_QUERY_RESULT);
            this.budget.measured(this.tiles[this.read], Math.max(gpu / NANOS_PER_MILLISECOND, this.cpu[this.read]));
            this.pending[this.read] = false;
            this.read = (this.read + 1) % RING;
        }
    }
}
