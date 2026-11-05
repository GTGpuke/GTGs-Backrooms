package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;

/**
 * Le thread de cuisson de la lumiere : il fait avancer la zone du joueur, une section a la fois, puis celle de la vue d'un portail
 * quand la premiere n'a plus rien a cuire, et attend quand tout est cuit. Une exception le coupe pour la session, journalisee une fois :
 * les sections non cuites restent a la lightmap.
 */
public final class LightWorker {

    private static final String THREAD_NAME = "GTG's Backrooms light bake";
    private static final long IDLE_MILLIS = 20L;
    private static volatile @Nullable LightZone zone;
    private static volatile @Nullable LightZone view;
    private static @Nullable Thread thread;
    private static volatile boolean failed;

    private LightWorker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** La zone du joueur a cuire, {@code null} pour aucune (thread de rendu). */
    public static void target(@Nullable LightZone next) {
        zone = next;
        start(next);
    }

    /** La zone d'une vue de portail a cuire, {@code null} pour aucune (thread de rendu). */
    public static void targetView(@Nullable LightZone next) {
        view = next;
        start(next);
    }

    private static void start(@Nullable LightZone next) {
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
                LightZone seen = view;
                worked = current != null && current.step() || seen != null && seen.step();
            } catch (RuntimeException exception) {
                failed = true;
                zone = null;
                view = null;
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
        view = null;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
