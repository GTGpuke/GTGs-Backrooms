package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.util.concurrent.ArrayBlockingQueue;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticCache;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticEvaluator;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticResult;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AudioQuality;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.PortalPlanes;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.RayTrace;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.ReverbPresets;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityGrid;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityWindow;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;

/**
 * Le thread de calcul acoustique : il recoit les demandes du thread de rendu (un son qui part, un son revu), lance les
 * rayons sur la copie du monde ({@link SolidityIndex}), cadres de portail compris ({@link PortalPlanes}), et rend ses
 * resultats, que le thread de rendu applique aux sources au tick suivant. Files bornees, demandes et reponses recyclees :
 * par demande, seul le resultat de l'evaluation (quatre flottants) est cree, sur ce thread ; une file pleine abandonne la
 * demande, que la revue suivante refera. Un seul evaluateur, de
 * la qualite choisie au premier calcul. Un resultat calcule avec trop de rayons perdus hors du monde copie
 * ({@link AcousticResult#reliable()}) n'entre pas dans le cache des cellules. Dans une dimension a forme generee
 * ({@link GeneratedAcoustics}), l'espace autour d'une source vient de la generation tant que la copie du monde autour
 * d'elle s'accorde avec elle, des rayons sinon ; l'option de comparaison ({@code gtgsbackrooms.audio.compareGenerated})
 * lance aussi les rayons et journalise l'accord des deux. Avec l'option de debogage
 * ({@code gtgsbackrooms.audio.debugRays}), les segments des rayons et des chemins des sons du monde de l'auditeur sont
 * enregistres et recopies sous verrou pour le rendu ({@link #copyTrace}).
 */
public final class AcousticWorker {

    /** Demandes et resultats en attente, au plus. */
    static final int QUEUE = 64;
    /** Ticks pendant lesquels l'acoustique d'une cellule reste bonne sans etre recalculee. */
    static final int CACHE_AGE_TICKS = 100;
    private static final String PERF_EVAL = "client.audio.eval";
    /** Comparaisons de la forme generee aux rayons entre deux bilans. */
    private static final int COMPARE_REPORT = 10;
    private static final String THREAD_NAME = "GTG's Backrooms acoustics";
    /** Segments d'un calcul : rayons de reverb (rebonds compris) et chemins d'occlusion. */
    private static final int TRACE_PER_EVALUATION = 256;
    /** Segments montres a la fois : ceux des derniers calculs (le dessin de debogage en tient autant). */
    static final int TRACE_SHOWN = 2048;

    /** Une demande : le son, sa source et sa copie du monde, les cadres autour, le point ou il joue, l'auditeur et sa copie. */
    private static final class Request {

        private final PortalPlanes planes = new PortalPlanes();
        private SoundInstance sound;
        private SolidityGrid sourceGrid;
        private AcousticCache cache;
        private GeneratedAcoustics.Shape shape;
        private long cell;
        private double sx;
        private double sy;
        private double sz;
        private SolidityGrid listenerGrid;
        private double px;
        private double py;
        private double pz;
        private double lx;
        private double ly;
        private double lz;
        private boolean throughFrame;
        private double ax;
        private double ay;
        private double az;
        private AudioProfile.Occlusion rule;
        private long tick;
        /** Demande de fin de partie : le thread de calcul oublie ses fenetres, rien n'est evalue. */
        private boolean forget;

        private void clear() {
            this.sound = null;
            this.sourceGrid = null;
            this.cache = null;
            this.shape = null;
            this.listenerGrid = null;
            this.rule = null;
            this.forget = false;
            this.planes.clear();
        }
    }

    /** Un resultat : le son et son acoustique, et le point de passage de son meilleur chemin d'occlusion. */
    public static final class Result {

        private SoundInstance sound;
        private AcousticResult result;
        private float direct;
        private double waypointX;
        private double waypointY;
        private double waypointZ;
        private boolean listenerTraced;

        /** Le son evalue. */
        public SoundInstance sound() {
            return this.sound;
        }

        /** Son acoustique. */
        public AcousticResult result() {
            return this.result;
        }

        /** Occlusion du chemin droit, du point ou il joue a l'auditeur. */
        public float direct() {
            return this.direct;
        }

        /** Point de passage du meilleur chemin, relatif au point ou il joue ; nul si le chemin droit l'emporte. */
        public double waypointX() {
            return this.waypointX;
        }

        /** Voir {@link #waypointX}. */
        public double waypointY() {
            return this.waypointY;
        }

        /** Voir {@link #waypointX}. */
        public double waypointZ() {
            return this.waypointZ;
        }

        /**
         * {@code true} si l'occlusion compte le trajet du point ou il joue a l'auditeur ; {@code false} si le monde de
         * l'auditeur n'est pas copie (un son entendu au cadre depuis un monde sans profil) : ce trajet reste a mesurer.
         */
        public boolean listenerTraced() {
            return this.listenerTraced;
        }
    }

    private static final ArrayBlockingQueue<Request> FREE_REQUESTS = new ArrayBlockingQueue<>(QUEUE);
    private static final ArrayBlockingQueue<Request> PENDING = new ArrayBlockingQueue<>(QUEUE);
    private static final ArrayBlockingQueue<Result> FREE_RESULTS = new ArrayBlockingQueue<>(QUEUE);
    private static final ArrayBlockingQueue<Result> RESULTS = new ArrayBlockingQueue<>(QUEUE);
    private static final SolidityWindow SOURCE_WINDOW = new SolidityWindow();
    private static final SolidityWindow LISTENER_WINDOW = new SolidityWindow();
    private static final SolidityWindow[] ARRIVAL_WINDOWS = new SolidityWindow[PortalPlanes.CAPACITY];
    private static final RayTrace WORK_TRACE = new RayTrace(TRACE_PER_EVALUATION);
    /** Les segments montres, ecrits par le thread de calcul et lus par le rendu, sous leur propre verrou. */
    private static final RayTrace SHOWN_TRACE = new RayTrace(TRACE_SHOWN);
    /** Une demande a ete faite depuis le dernier oubli : les fenetres du thread de calcul tiennent peut-etre des sections. */
    private static volatile boolean windowsHeld;
    /** Ecrit par le thread de calcul seul : un calcul a centre ses fenetres depuis le dernier oubli. */
    private static volatile boolean holdsWindows;
    private static @Nullable AcousticEvaluator evaluator;
    private static @Nullable Thread thread;
    /** Thread de calcul : comparaisons de la forme generee aux rayons, et accords (meme reglage, meme taille, meme ouverture). */
    private static int compared;
    private static int samePreset;
    private static int sameSize;
    private static int sameOpenness;

    static {
        for (int i = 0; i < QUEUE; i++) {
            FREE_REQUESTS.add(new Request());
            FREE_RESULTS.add(new Result());
        }
        for (int i = 0; i < ARRIVAL_WINDOWS.length; i++) {
            ARRIVAL_WINDOWS[i] = new SolidityWindow();
        }
    }

    private AcousticWorker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} si les fenetres du thread de calcul tiennent encore des sections : un calcul fait depuis le dernier
     * oubli. Apres une fin de partie, il redevient faux une fois l'oubli traite ; lu par les tests du client.
     */
    public static boolean holdsWindows() {
        return holdsWindows;
    }

    /** Lance le thread s'il ne l'est pas, a la qualite de l'option ou du moteur de shaders ; appele du thread de rendu. */
    public static void ensureStarted() {
        windowsHeld = true;
        if (thread == null) {
            start(AudioQuality.choose(ModConfig.AUDIO_QUALITY, LookResources.quality()));
        }
    }

    /** Lance le thread, s'il ne l'est pas, avec cette qualite ; appele du thread de rendu. */
    private static void start(AudioQuality quality) {
        if (thread != null) {
            return;
        }
        evaluator = AcousticEvaluator.of(quality);
        Thread worker = new Thread(AcousticWorker::run, THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
        thread = worker;
        ModConstants.LOGGER.info("[Audio] Calcul acoustique par rayons, qualite {} ({} rayons, {} rebonds, {} chemin(s)).", quality, quality.rays(),
                quality.bounces(), quality.paths());
    }

    /**
     * Demande l'acoustique d'un son, depuis le thread de rendu.
     *
     * @param sound le son
     * @param rule occlusion de son profil
     * @param sourceGrid copie du monde de sa vraie source
     * @param cache cache des cellules de ce monde
     * @param shape forme des salles tiree de la generation de ce monde, {@code null} pour les rayons seuls
     * @param sx sa vraie source
     * @param sy sa vraie source
     * @param sz sa vraie source
     * @param planes cadres de portail ouverts autour de la source, {@code null} sans
     * @param listenerGrid copie du monde de l'auditeur, {@code null} si ce monde n'est pas copie
     * @param px point ou le son joue, dans le monde de l'auditeur
     * @param py point ou le son joue
     * @param pz point ou le son joue
     * @param listener l'auditeur
     * @param arrival point d'entree dans le cadre, dans le monde de la source, pour un son entre par un cadre ; {@code null} sinon
     * @param tick tick du monde
     * @return {@code false} si la file est pleine : la demande est abandonnee
     */
    public static boolean request(SoundInstance sound, AudioProfile.Occlusion rule, SolidityGrid sourceGrid, AcousticCache cache,
            @Nullable GeneratedAcoustics.Shape shape, double sx, double sy, double sz, @Nullable PortalPlanes planes, @Nullable SolidityGrid listenerGrid,
            double px, double py, double pz, Vec3d listener, @Nullable Vec3d arrival, long tick) {
        Request request = FREE_REQUESTS.poll();
        if (request == null) {
            return false;
        }
        request.sound = sound;
        request.rule = rule;
        request.sourceGrid = sourceGrid;
        request.cache = cache;
        request.shape = shape;
        request.cell = AcousticCache.cellOf(sx, sy, sz);
        request.sx = sx;
        request.sy = sy;
        request.sz = sz;
        if (planes != null) {
            request.planes.copyFrom(planes);
        }
        request.listenerGrid = listenerGrid;
        request.px = px;
        request.py = py;
        request.pz = pz;
        request.lx = listener.x;
        request.ly = listener.y;
        request.lz = listener.z;
        request.throughFrame = arrival != null;
        if (arrival != null) {
            request.ax = arrival.x;
            request.ay = arrival.y;
            request.az = arrival.z;
        }
        request.tick = tick;
        if (!PENDING.offer(request)) {
            request.clear();
            FREE_REQUESTS.offer(request);
            return false;
        }
        return true;
    }

    /** Un resultat pret, ou {@code null} ; a rendre par {@link #release} une fois lu. */
    public static @Nullable Result poll() {
        return RESULTS.poll();
    }

    /** Rend un resultat lu. */
    public static void release(Result result) {
        result.sound = null;
        result.result = null;
        FREE_RESULTS.offer(result);
    }

    /**
     * Recopie les segments montres (option de debogage), sous le verrou du thread de calcul.
     *
     * @param target recopie, videe d'abord
     */
    public static void copyTrace(RayTrace target) {
        synchronized (SHOWN_TRACE) {
            target.clear();
            target.append(SHOWN_TRACE);
        }
    }

    /**
     * Fin de partie : demandes et resultats en attente abandonnes (un calcul en cours rend un resultat que personne ne
     * lit), puis le thread de calcul oublie ses fenetres, seul a les toucher : il ne garde aucune section de la partie.
     */
    public static void clear() {
        for (Request request = PENDING.poll(); request != null; request = PENDING.poll()) {
            request.clear();
            FREE_REQUESTS.offer(request);
        }
        for (Result result = RESULTS.poll(); result != null; result = RESULTS.poll()) {
            release(result);
        }
        if (thread != null && windowsHeld) {
            Request forget = FREE_REQUESTS.poll();
            if (forget != null) {
                windowsHeld = false;
                forget.forget = true;
                PENDING.offer(forget);
            }
        }
    }

    /** Thread de calcul, demon : il attend les demandes tant que le client tourne. */
    private static void run() {
        AcousticEvaluator rays = evaluator;
        while (true) {
            Request request;
            try {
                request = PENDING.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (request.forget) {
                forgetWindows();
                holdsWindows = false;
                request.clear();
                FREE_REQUESTS.offer(request);
                continue;
            }
            holdsWindows = true;
            long start = System.nanoTime();
            // Debogage : seuls les sons du monde de l'auditeur montrent leurs rayons (ceux d'un autre monde y seraient mal places).
            boolean traced = ModConfig.AUDIO_DEBUG_RAYS && request.sourceGrid == request.listenerGrid && !request.throughFrame;
            WORK_TRACE.clear();
            rays.record(traced ? WORK_TRACE : null);
            evaluate(rays, request);
            if (traced) {
                synchronized (SHOWN_TRACE) {
                    if (SHOWN_TRACE.count() + WORK_TRACE.count() > TRACE_SHOWN) {
                        SHOWN_TRACE.clear();
                    }
                    SHOWN_TRACE.append(WORK_TRACE);
                }
            }
            request.clear();
            FREE_REQUESTS.offer(request);
            ModPerformanceMonitor.record(PERF_EVAL, System.nanoTime() - start);
        }
    }

    /**
     * L'acoustique d'une demande : l'espace autour de la source (le cache de sa cellule s'il est frais, les rayons
     * sinon, a travers les cadres de portail), puis l'occlusion, du point ou le son joue a l'auditeur dans le monde de
     * l'auditeur, et, pour un son entre par un cadre, de la source au point d'entree dans le monde de la source, les deux
     * combinees.
     */
    private static void evaluate(AcousticEvaluator rays, Request request) {
        AcousticResult space = request.cache.fresh(request.cell, request.tick, CACHE_AGE_TICKS) ? request.cache.get(request.cell) : null;
        boolean sourceWindowReady = false;
        if (space == null) {
            centerOn(request.sourceGrid, request.sx, request.sy, request.sz, SOURCE_WINDOW);
            sourceWindowReady = true;
            space = generated(request);
            if (space == null || ModConfig.AUDIO_COMPARE_GENERATED) {
                PortalPlanes planes = request.planes;
                for (int i = 0; i < planes.count(); i++) {
                    centerOn(planes.destination(i), planes.arrivalX(i), planes.arrivalY(i), planes.arrivalZ(i), ARRIVAL_WINDOWS[i]);
                }
                AcousticResult traced = rays.reverb(SOURCE_WINDOW, request.sx, request.sy, request.sz, planes.count() > 0 ? planes : null, ARRIVAL_WINDOWS);
                if (space != null) {
                    compare(space, traced);
                } else {
                    space = traced;
                }
            }
            if (space.reliable()) {
                request.cache.put(request.cell, space, request.tick);
            }
        }
        float occlusion = 0.0f;
        float direct = 0.0f;
        double waypointX = 0.0;
        double waypointY = 0.0;
        double waypointZ = 0.0;
        if (request.rule.any()) {
            if (request.listenerGrid != null) {
                centerOn(request.listenerGrid, request.lx, request.ly, request.lz, LISTENER_WINDOW);
                occlusion = rays.occlusion(LISTENER_WINDOW, request.px, request.py, request.pz, request.lx, request.ly, request.lz, request.rule);
                direct = rays.directOcclusion();
                waypointX = rays.waypointX();
                waypointY = rays.waypointY();
                waypointZ = rays.waypointZ();
            }
            if (request.throughFrame) {
                if (!sourceWindowReady) {
                    centerOn(request.sourceGrid, request.sx, request.sy, request.sz, SOURCE_WINDOW);
                }
                float toFrame = rays.occlusion(SOURCE_WINDOW, request.sx, request.sy, request.sz, request.ax, request.ay, request.az, request.rule);
                occlusion = OcclusionTrace.combine(occlusion, toFrame, request.rule);
            }
        }
        Result result = FREE_RESULTS.poll();
        if (result == null) {
            return;
        }
        result.sound = request.sound;
        result.result = space.withOcclusion(occlusion);
        result.direct = direct;
        result.waypointX = waypointX;
        result.waypointY = waypointY;
        result.waypointZ = waypointZ;
        result.listenerTraced = request.listenerGrid != null || !request.rule.any();
        RESULTS.offer(result);
    }

    /**
     * L'espace autour de la source d'apres la forme generee, si la dimension en a une et si la copie du monde autour de la
     * source s'accorde avec elle (la fenetre de la source est deja centree) ; {@code null} sinon : les rayons s'en
     * chargent.
     */
    private static @Nullable AcousticResult generated(Request request) {
        GeneratedAcoustics.Shape shape = request.shape;
        int x = (int) Math.floor(request.sx);
        int y = (int) Math.floor(request.sy);
        int z = (int) Math.floor(request.sz);
        if (shape == null || !shape.matches(SOURCE_WINDOW, x, y, z)) {
            return null;
        }
        return shape.estimate(x, y, z);
    }

    /** Option de comparaison : l'accord de la forme generee et des rayons, journalise tous les {@value #COMPARE_REPORT} sons. */
    private static void compare(AcousticResult generated, AcousticResult traced) {
        if (!traced.reliable()) {
            return;
        }
        compared++;
        if (ReverbPresets.index(generated) == ReverbPresets.index(traced)) {
            samePreset++;
        }
        if (ReverbPresets.sizeClass(generated.roomSize()) == ReverbPresets.sizeClass(traced.roomSize())) {
            sameSize++;
        }
        if (ReverbPresets.open(generated) == ReverbPresets.open(traced)) {
            sameOpenness++;
        }
        if (compared % COMPARE_REPORT == 0) {
            ModConstants.LOGGER.info("[Audio] Generation contre rayons : {} son(s), {} % meme reglage (taille {} %, ouverture {} %), derniers : "
                    + "generation {} blocs et {} ouvert, rayons {} blocs et {} ouvert.", compared, samePreset * 100 / compared, sameSize * 100 / compared,
                    sameOpenness * 100 / compared, generated.roomSize(), generated.openness(), traced.roomSize(), traced.openness());
        }
    }

    /** Thread de calcul : les fenetres lachent leurs sections, et les rayons montres sont oublies. */
    private static void forgetWindows() {
        SOURCE_WINDOW.center(0, 0, 0);
        LISTENER_WINDOW.center(0, 0, 0);
        for (SolidityWindow window : ARRIVAL_WINDOWS) {
            window.center(0, 0, 0);
        }
        WORK_TRACE.clear();
        synchronized (SHOWN_TRACE) {
            SHOWN_TRACE.clear();
        }
    }

    private static void centerOn(SolidityGrid grid, double x, double y, double z, SolidityWindow window) {
        grid.window(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(y), ChunkSectionPos.getSectionCoord(z), window);
    }
}
