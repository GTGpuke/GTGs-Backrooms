package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;

/**
 * Le thread de cuisson de la lumiere : il fait avancer la zone en cours, une section a la fois, et attend quand tout est
 * cuit. Une exception le coupe pour la session, journalisee une fois : les sections non cuites restent a la lightmap.
 */
public final class LightWorker {

    private static final String THREAD_NAME = "GTG's Backrooms light bake";
    private static final long IDLE_MILLIS = 20L;
    private static volatile @Nullable LightZone zone;
    private static @Nullable Thread thread;
    private static volatile boolean failed;

    private LightWorker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** La zone a cuire, {@code null} pour aucune (thread de rendu). */
    public static void target(@Nullable LightZone next) {
        zone = next;
        if (next != null && thread == null && !failed) {
            thread = new Thread(LightWorker::run, THREAD_NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }
    }

    private static void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean worked;
            try {
                LightZone current = zone;
                worked = current != null && current.step();
            } catch (RuntimeException exception) {
                failed = true;
                zone = null;
                ModConstants.LOGGER.error("[Look] Cuisson de la lumiere coupee pour la session.", exception);
                return;
            }
            if (!worked) {
                try {
                    Thread.sleep(IDLE_MILLIS);
                } catch (InterruptedException exception) {
                    return;
                }
            }
        }
    }

    /** Fin de partie : le thread s'arrete. */
    public static void close() {
        zone = null;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
