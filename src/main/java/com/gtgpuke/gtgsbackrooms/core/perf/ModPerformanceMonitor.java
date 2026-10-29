package com.gtgpuke.gtgsbackrooms.core.perf;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Moniteur de performance opt-in ({@code -Dgtgsbackrooms.debugPerformanceMonitor=true}).
 *
 * <p>Publie periodiquement dans les logs le temps de tick serveur, le top des sections profilees, les paquets et octets
 * envoyes par type de payload du mod, la memoire JVM et la charge CPU ; cote client, les FPS, l'image la plus longue et le
 * nombre d'images lentes de l'intervalle, et le top des sections client (celles dont le nom commence par
 * {@value #CLIENT_SECTION_PREFIX}). Complete le F3. Desactive, chaque appel se reduit a un test de booleen.
 *
 * <p>La charge CPU vient d'un echantillonneur sur son propre thread ({@link CpuSampler}) : lue sur un thread de jeu, elle
 * bloquerait le thread de rendu ou le serveur de 50 a 60 ms a chaque resume, et le moniteur compterait lui-meme une image
 * lente et un pic de tick par resume.
 */
public final class ModPerformanceMonitor {

    /** Prefixe des sections mesurees sur le client : elles entrent dans le resume client, pas dans celui du serveur. */
    public static final String CLIENT_SECTION_PREFIX = "client.";
    private static final Scope NO_OP_SCOPE = new Scope(null, 0L);
    /** Sections du resume, les plus longues d'abord : huit, pour que les mesures GPU et audio y tiennent a cote des portails. */
    private static final int TOP_SECTIONS = 8;
    private static final ConcurrentHashMap<String, SectionStats> SECTION_STATS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, NetworkStats> NETWORK_STATS = new ConcurrentHashMap<>();
    private static final LongAdder SERVER_TICK_COUNT = new LongAdder();
    private static final LongAdder SERVER_TICK_TOTAL_NANOS = new LongAdder();
    private static final AtomicLong SERVER_TICK_MAX_NANOS = new AtomicLong();
    private static final AtomicLong LAST_SERVER_LOG_NANOS = new AtomicLong();
    private static final AtomicLong LAST_CLIENT_LOG_NANOS = new AtomicLong();
    private static final OperatingSystemMXBean OPERATING_SYSTEM_MX_BEAN = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    /** Charge CPU du processus et du systeme, relevee a part : les resumes la lisent sans attendre. */
    static final CpuSampler CPU_SAMPLER = new CpuSampler(OPERATING_SYSTEM_MX_BEAN);
    /** Octets alloues par le thread courant, pour compter ce qu'une section alloue ; absent sur une JVM qui ne le mesure pas. */
    private static final @Nullable ThreadMXBean THREAD_MX_BEAN = threadAllocationBean(ManagementFactory.getThreadMXBean());

    private static volatile boolean enabled = ModConfig.DEBUG_PERFORMANCE_MONITOR;
    private static volatile long currentServerTickStartNanos;
    private static volatile String lastServerSummary = "serveur: aucun echantillon";
    private static volatile String lastClientSummary = "client: aucun echantillon";
    private static volatile String lastCrossingSummary = "traversee: aucune";

    /** Images observees apres une bascule de monde, le temps que le nouveau monde se stabilise. */
    static final int CROSSING_FRAMES = 120;
    /** Au-dela de cette duree, une image compte comme un a-coup. */
    static final long SLOW_FRAME_NANOS = 50_000_000L;
    // Images du client depuis le dernier resume, et traversee en cours de mesure : thread de rendu seulement.
    private static long frameLastNanos;
    private static long frameMaxNanos;
    private static int slowFrames;
    private static long crossingStartNanos;
    private static long crossingSwitchNanos;
    private static String crossingDestination = "";
    private static int crossingFramesLeft;
    private static long crossingLastFrameNanos;
    private static long crossingMaxFrameNanos;
    private static int crossingSlowFrames;
    private static int crossingChunks;

    private ModPerformanceMonitor() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si le monitoring est actif. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Active ou desactive le monitoring a chaud (tests, commandes de debug) ; l'echantillonneur CPU suit. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            CPU_SAMPLER.start(intervalNanos());
        } else {
            CPU_SAMPLER.stop();
        }
    }

    /** Demarre l'echantillonneur CPU si le moniteur est actif au lancement (propriete systeme). */
    public static void init() {
        if (enabled) {
            CPU_SAMPLER.start(intervalNanos());
        }
    }

    /** Oublie la date du dernier resume : le prochain tick en publie un (tests). */
    static void resetLogTimers() {
        LAST_SERVER_LOG_NANOS.set(0L);
        LAST_CLIENT_LOG_NANOS.set(0L);
    }

    /**
     * Ouvre une section profilee ; a utiliser en try-with-resources.
     *
     * @param sectionName nom stable de la section (constante, pas de concatenation par appel)
     * @return scope auto-closeable, sans effet si le monitoring est inactif
     */
    public static Scope scope(String sectionName) {
        return isEnabled() ? new Scope(sectionName, System.nanoTime()) : NO_OP_SCOPE;
    }

    /**
     * Enregistre une duree mesuree ailleurs (temps GPU lu apres coup) dans une section, sans allocation comptee ;
     * rien si le monitoring est inactif.
     *
     * @param sectionName nom stable de la section
     * @param elapsedNanos duree mesuree, en nanosecondes
     */
    public static void record(String sectionName, long elapsedNanos) {
        if (isEnabled()) {
            SECTION_STATS.computeIfAbsent(sectionName, ignored -> new SectionStats()).record(elapsedNanos, 0L);
        }
    }

    /** Le compteur d'allocation par thread de ce bean, ou {@code null} si la JVM ne le propose pas. */
    static @Nullable ThreadMXBean threadAllocationBean(java.lang.management.ThreadMXBean bean) {
        return bean instanceof ThreadMXBean allocating && allocating.isThreadAllocatedMemorySupported() ? allocating : null;
    }

    /** Octets alloues jusqu'ici par le thread courant, ou zero si la JVM ne les compte pas. */
    static long allocatedBytes() {
        return allocatedBytes(THREAD_MX_BEAN);
    }

    static long allocatedBytes(@Nullable ThreadMXBean bean) {
        return bean == null ? 0L : bean.getCurrentThreadAllocatedBytes();
    }

    /** Debut de tick serveur. */
    public static void onServerTickStart() {
        if (isEnabled()) {
            currentServerTickStartNanos = System.nanoTime();
        }
    }

    /** Fin de tick serveur. */
    public static void onServerTickEnd() {
        if (!isEnabled() || currentServerTickStartNanos == 0L) {
            return;
        }
        long elapsedNanos = System.nanoTime() - currentServerTickStartNanos;
        currentServerTickStartNanos = 0L;
        SERVER_TICK_COUNT.increment();
        SERVER_TICK_TOTAL_NANOS.add(elapsedNanos);
        updateMax(SERVER_TICK_MAX_NANOS, elapsedNanos);
        if (shouldLog(LAST_SERVER_LOG_NANOS)) {
            logServerSummary();
        }
    }

    /**
     * Un payload du mod part vers un joueur ; compte pour le resume serveur. Sans effet si le monitoring est inactif.
     *
     * @param channel nom du payload (chemin de son identifiant, deja interne)
     * @param bytes taille encodee, en octets
     */
    public static void onPayloadSent(String channel, int bytes) {
        if (isEnabled()) {
            NETWORK_STATS.computeIfAbsent(channel, ignored -> new NetworkStats()).record(bytes);
        }
    }

    /** Paquets et octets envoyes par type de payload depuis le dernier resume serveur, sans les remettre a zero. */
    public static String networkSummary() {
        return formatNetwork(false);
    }

    /**
     * Echantillonne l'etat client en fin de tick.
     *
     * @param fps FPS courant
     */
    public static void onClientTick(int fps) {
        if (!isEnabled() || !shouldLog(LAST_CLIENT_LOG_NANOS)) {
            return;
        }
        Snapshot snapshot = Snapshot.capture(CPU_SAMPLER);
        lastClientSummary = String.format(Locale.ROOT, "client: fps=%d pic=%.1fms lentes=%d %s top=%s",
                fps, nanosToMillis(frameMaxNanos), slowFrames, snapshot.format(), formatTopSections(true));
        frameMaxNanos = 0L;
        slowFrames = 0;
        ModConstants.LOGGER.info("[perf] {}", lastClientSummary);
    }

    /**
     * Ajoute une synthese courte au F3.
     *
     * @param text lignes a completer
     */
    public static void appendDebugHudText(List<String> text) {
        if (!isEnabled()) {
            return;
        }
        text.add("Perf monitor : actif");
        text.add(lastServerSummary);
        text.add(lastClientSummary);
        text.add(lastCrossingSummary);
    }

    /** Dernier resume serveur publie. */
    public static String lastServerSummary() {
        return lastServerSummary;
    }

    /** Dernier resume client publie. */
    static String lastClientSummary() {
        return lastClientSummary;
    }

    /** Dernier bilan de traversee publie. */
    static String lastCrossingSummary() {
        return lastCrossingSummary;
    }

    /** Le client commence a basculer de monde : debut de la mesure de la traversee. */
    public static void onCrossingStart() {
        if (isEnabled()) {
            crossingStartNanos = System.nanoTime();
        }
    }

    /**
     * Le client a bascule vers {@code destination} : la duree de la bascule est retenue et les images qui suivent
     * sont observees, jusqu'au bilan.
     *
     * @param destination dimension d'arrivee
     */
    public static void onCrossingSwitched(Identifier destination) {
        if (!isEnabled() || crossingStartNanos == 0L) {
            return;
        }
        crossingSwitchNanos = System.nanoTime() - crossingStartNanos;
        crossingStartNanos = 0L;
        crossingDestination = destination.toString();
        crossingFramesLeft = CROSSING_FRAMES;
        crossingLastFrameNanos = 0L;
        crossingMaxFrameNanos = 0L;
        crossingSlowFrames = 0;
        crossingChunks = 0;
    }

    /**
     * Une image vient d'etre rendue : sa duree compte pour l'image la plus longue et les images lentes du resume client,
     * et pour la traversee en cours de mesure s'il y en a une. Inactif, cela ne coute qu'un test de booleen.
     */
    public static void onFrameRendered() {
        if (!isEnabled()) {
            crossingFramesLeft = 0;
            frameLastNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        if (frameLastNanos != 0L) {
            long frameNanos = now - frameLastNanos;
            if (frameNanos > frameMaxNanos) {
                frameMaxNanos = frameNanos;
            }
            if (frameNanos > SLOW_FRAME_NANOS) {
                slowFrames++;
            }
        }
        frameLastNanos = now;
        if (crossingFramesLeft <= 0) {
            return;
        }
        if (crossingLastFrameNanos != 0L) {
            long frameNanos = now - crossingLastFrameNanos;
            if (frameNanos > crossingMaxFrameNanos) {
                crossingMaxFrameNanos = frameNanos;
            }
            if (frameNanos > SLOW_FRAME_NANOS) {
                crossingSlowFrames++;
            }
        }
        crossingLastFrameNanos = now;
        if (--crossingFramesLeft == 0) {
            publishCrossingSummary();
        }
    }

    /** Un chunk, vanilla ou distant, arrive chez le client : compte pendant la fenetre d'observation. */
    public static void onChunkReceived() {
        if (crossingFramesLeft > 0) {
            crossingChunks++;
        }
    }

    private static void publishCrossingSummary() {
        lastCrossingSummary = String.format(
                Locale.ROOT,
                "traversee: vers %s bascule=%.3fms pic=%.3fms lentes=%d/%d chunks=%d",
                crossingDestination,
                nanosToMillis(crossingSwitchNanos),
                nanosToMillis(crossingMaxFrameNanos),
                crossingSlowFrames,
                CROSSING_FRAMES - 1,
                crossingChunks);
        ModConstants.LOGGER.info("[perf] {}", lastCrossingSummary);
    }

    private static boolean shouldLog(AtomicLong lastLogNanos) {
        long now = System.nanoTime();
        long last = lastLogNanos.get();
        if (!isDue(last, now, intervalNanos())) {
            return false;
        }
        return lastLogNanos.compareAndSet(last, now);
    }

    /** {@code true} si un resume est du : jamais publie ({@code 0}), ou publie il y a au moins un intervalle. */
    static boolean isDue(long lastNanos, long nowNanos, long intervalNanos) {
        return lastNanos == 0L || nowNanos - lastNanos >= intervalNanos;
    }

    private static void logServerSummary() {
        long tickCount = SERVER_TICK_COUNT.sumThenReset();
        long totalTickNanos = SERVER_TICK_TOTAL_NANOS.sumThenReset();
        long maxTickNanos = SERVER_TICK_MAX_NANOS.getAndSet(0L);
        // Toujours au moins le tick qui vient d'etre compte.
        double averageMspt = nanosToMillis(totalTickNanos) / tickCount;
        lastServerSummary = String.format(
                Locale.ROOT,
                "serveur: mspt=%.3f/%.3f %s top=%s reseau=%s",
                averageMspt,
                nanosToMillis(maxTickNanos),
                Snapshot.capture(CPU_SAMPLER).format(),
                formatTopSections(false),
                formatNetwork(true));
        ModConstants.LOGGER.info("[perf] {}", lastServerSummary);
    }

    /** Top des sections du client ({@code client}) ou du serveur, remises a zero une fois lues. */
    private static String formatTopSections(boolean client) {
        List<SectionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SectionStats> entry : SECTION_STATS.entrySet()) {
            if (entry.getKey().startsWith(CLIENT_SECTION_PREFIX) != client) {
                continue;
            }
            SectionSnapshot snapshot = entry.getValue().snapshotAndReset(entry.getKey());
            if (snapshot.calls() > 0L) {
                snapshots.add(snapshot);
            }
        }
        if (snapshots.isEmpty()) {
            return "aucune section";
        }
        snapshots.sort(Comparator.comparingLong(SectionSnapshot::totalNanos).reversed());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < Math.min(TOP_SECTIONS, snapshots.size()); index++) {
            SectionSnapshot snapshot = snapshots.get(index);
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(snapshot.name())
                    .append(':').append(snapshot.calls()).append("x/")
                    .append(String.format(Locale.ROOT, "%.3f", nanosToMillis(snapshot.totalNanos()))).append("ms/max ")
                    .append(String.format(Locale.ROOT, "%.3f", nanosToMillis(snapshot.maxNanos()))).append("ms/alloc ")
                    .append(String.format(Locale.ROOT, "%.1fKiB", snapshot.allocatedBytes() / 1024.0D));
        }
        return builder.toString();
    }

    /** Paquets et octets par type de payload, du plus lourd au plus leger ; remis a zero si demande. */
    private static String formatNetwork(boolean reset) {
        List<NetworkSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, NetworkStats> entry : NETWORK_STATS.entrySet()) {
            NetworkSnapshot snapshot = entry.getValue().snapshot(entry.getKey(), reset);
            if (snapshot.packets() > 0L) {
                snapshots.add(snapshot);
            }
        }
        if (snapshots.isEmpty()) {
            return "aucun";
        }
        snapshots.sort(Comparator.comparingLong(NetworkSnapshot::bytes).reversed());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < snapshots.size(); index++) {
            NetworkSnapshot snapshot = snapshots.get(index);
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(snapshot.name()).append(':').append(snapshot.packets()).append("x/")
                    .append(String.format(Locale.ROOT, "%.1fKiB", snapshot.bytes() / 1024.0D));
        }
        return builder.toString();
    }

    private static long intervalNanos() {
        return Math.max(1, ModConfig.PERFORMANCE_LOG_INTERVAL_SECONDS) * 1_000_000_000L;
    }

    private static void updateMax(AtomicLong max, long candidate) {
        max.accumulateAndGet(candidate, Math::max);
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    /** Scope de mesure auto-closeable : duree et octets alloues par le thread qui l'ouvre et le ferme. */
    public static final class Scope implements AutoCloseable {

        private final String sectionName;
        private final long startNanos;
        private final long startAllocatedBytes;

        private Scope(String sectionName, long startNanos) {
            this.sectionName = sectionName;
            this.startNanos = startNanos;
            this.startAllocatedBytes = sectionName == null ? 0L : allocatedBytes();
        }

        @Override
        public void close() {
            if (sectionName != null) {
                SECTION_STATS.computeIfAbsent(sectionName, ignored -> new SectionStats())
                        .record(System.nanoTime() - startNanos, allocatedBytes() - startAllocatedBytes);
            }
        }
    }

    private static final class SectionStats {

        private final LongAdder calls = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private final LongAdder allocatedBytes = new LongAdder();

        private void record(long elapsedNanos, long allocated) {
            calls.increment();
            totalNanos.add(elapsedNanos);
            updateMax(maxNanos, elapsedNanos);
            allocatedBytes.add(allocated);
        }

        private SectionSnapshot snapshotAndReset(String name) {
            return new SectionSnapshot(name, calls.sumThenReset(), totalNanos.sumThenReset(), maxNanos.getAndSet(0L), allocatedBytes.sumThenReset());
        }
    }

    private record SectionSnapshot(String name, long calls, long totalNanos, long maxNanos, long allocatedBytes) {
    }

    /** Paquets et octets d'un type de payload ; les compteurs se lisent avec ou sans remise a zero. */
    private static final class NetworkStats {

        private final LongAdder packets = new LongAdder();
        private final LongAdder bytes = new LongAdder();

        private void record(int size) {
            packets.increment();
            bytes.add(size);
        }

        private NetworkSnapshot snapshot(String name, boolean reset) {
            return reset
                    ? new NetworkSnapshot(name, packets.sumThenReset(), bytes.sumThenReset())
                    : new NetworkSnapshot(name, packets.sum(), bytes.sum());
        }
    }

    private record NetworkSnapshot(String name, long packets, long bytes) {
    }

    /**
     * Charge CPU du processus et du systeme, relevee sur un thread demon a chaque intervalle de resume. Les threads de jeu
     * ne lisent que les deux dernieres valeurs ; inconnues (negatives) tant qu'aucun releve n'a eu lieu, ou sans bean
     * (JVM sans extension com.sun).
     */
    static final class CpuSampler {

        /** Attente de la fin du thread a l'arret : un releve en cours dure quelques millisecondes. */
        private static final long STOP_WAIT_MILLIS = 1_000L;

        private final @Nullable OperatingSystemMXBean bean;
        private volatile double processCpuLoad = -1.0D;
        private volatile double systemCpuLoad = -1.0D;
        private volatile long samples;
        private @Nullable Thread thread;

        CpuSampler(@Nullable OperatingSystemMXBean bean) {
            this.bean = bean;
        }

        /** Un releve, maintenant, sur le thread appelant : celui de l'echantillonneur, ou d'un test. */
        void sample() {
            if (this.bean != null) {
                this.processCpuLoad = this.bean.getProcessCpuLoad();
                this.systemCpuLoad = this.bean.getCpuLoad();
            }
            this.samples++;
        }

        /** Nombre de releves depuis la creation (tests : la boucle tourne). */
        long samples() {
            return this.samples;
        }

        double processCpuLoad() {
            return this.processCpuLoad;
        }

        double systemCpuLoad() {
            return this.systemCpuLoad;
        }

        /** Lance le thread demon s'il ne tourne pas : un releve tout de suite, puis un par intervalle. */
        synchronized void start(long intervalNanos) {
            if (this.thread != null || this.bean == null) {
                return;
            }
            Thread sampler = new Thread(() -> run(intervalNanos), "gtgs-backrooms-cpu-sampler");
            sampler.setDaemon(true);
            this.thread = sampler;
            sampler.start();
        }

        /** Arrete le thread demon, s'il tourne, attend sa fin (bornee), et oublie les dernieres valeurs. */
        synchronized void stop() {
            Thread sampler = this.thread;
            this.thread = null;
            if (sampler != null) {
                sampler.interrupt();
                try {
                    sampler.join(STOP_WAIT_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            this.processCpuLoad = -1.0D;
            this.systemCpuLoad = -1.0D;
        }

        boolean running() {
            return this.thread != null;
        }

        private void run(long intervalNanos) {
            while (sampleThenWait(intervalNanos)) {
                // Un releve par intervalle, jusqu'a l'interruption.
            }
        }

        /** Un releve, puis l'attente de l'intervalle ; {@code false} des que le thread est interrompu. */
        boolean sampleThenWait(long intervalNanos) {
            sample();
            try {
                Thread.sleep(intervalNanos / 1_000_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return !Thread.currentThread().isInterrupted();
        }
    }

    /** Memoire et charge CPU a un instant, la charge etant la derniere relevee par l'echantillonneur. */
    record Snapshot(long heapUsedBytes, long heapMaxBytes, double processCpuLoad, double systemCpuLoad) {

        static Snapshot capture(CpuSampler sampler) {
            Runtime runtime = Runtime.getRuntime();
            return new Snapshot(
                    runtime.totalMemory() - runtime.freeMemory(),
                    runtime.maxMemory(),
                    sampler.processCpuLoad(),
                    sampler.systemCpuLoad());
        }

        String format() {
            return String.format(
                    Locale.ROOT,
                    "heap=%.1f/%.1f MiB cpuProc=%s cpuSys=%s",
                    heapUsedBytes / (1024.0D * 1024.0D),
                    heapMaxBytes / (1024.0D * 1024.0D),
                    formatPercent(processCpuLoad),
                    formatPercent(systemCpuLoad));
        }

        static String formatPercent(double load) {
            return load < 0.0D ? "n/a" : String.format(Locale.ROOT, "%.1f%%", load * 100.0D);
        }
    }
}
