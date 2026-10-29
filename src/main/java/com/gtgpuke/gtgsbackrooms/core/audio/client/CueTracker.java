package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.Source;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CameraShake;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.Cue;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueRegistry;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.Disturbances;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.Envelope;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.FlashLimiter;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalViewTransform;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SourceAccessor;

/**
 * Les sons lies ({@link Cue}) en train de jouer, et ce qu'ils font a l'image : chacun est une perturbation
 * ({@link Disturbances}) dont l'amplitude suit l'enveloppe du son a sa position de lecture reelle, lue sur la source
 * OpenAL ({@code AL_SEC_OFFSET}, sur le thread du son, chaque tick) et prolongee entre deux lectures par l'horloge. Le
 * plafond de clignotement ({@link FlashLimiter}) borne l'effet final de toutes les perturbations ensemble, sur tout
 * l'ecran : chaque vue de portail compte la charge de sa dimension a proportion de sa surface a l'ecran (relevee a
 * l'image precedente), le monde du joueur le reste. L'option de confort ({@code gtgsbackrooms.audio.reduceFlashes})
 * adoucit tout et coupe la pulsation de l'ecran et le tremblement de camera. Le rendu lit ici ce qu'il reste a chaque
 * neon, la pulsation et la baisse de lumiere de chaque dimension, et la camera du joueur son tremblement.
 */
public final class CueTracker {

    private static final int CAPACITY = Disturbances.CAPACITY;
    private static final String PERF_CUE = "client.audio.cue";
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final Disturbances DISTURBANCES = new Disturbances();
    private static final FlashLimiter LIMITER = new FlashLimiter(ModConfig.REDUCE_FLASHES);
    private static final SoundInstance[] SOUNDS = new SoundInstance[CAPACITY];
    private static final Envelope[] ENVELOPES = new Envelope[CAPACITY];
    /** Position de lecture lue sur le thread du son (bits d'un flottant) et instant de la lecture. */
    private static final AtomicIntegerArray OFFSETS = new AtomicIntegerArray(CAPACITY);
    private static final AtomicLongArray READ_AT = new AtomicLongArray(CAPACITY);
    private static final OffsetReader[] READERS = new OffsetReader[CAPACITY];
    /** Vues de portail dessinees a l'image en cours : leur dimension et leur part de l'ecran (plafond de clignotement). */
    private static final int MAX_VIEWS = 16;
    private static final Identifier[] VIEW_DIMENSIONS = new Identifier[MAX_VIEWS];
    private static final float[] VIEW_AREAS = new float[MAX_VIEWS];
    private static final float[] VIEW_LOADS = new float[MAX_VIEWS];
    private static int views;
    private static float scale = 1.0f;

    /** La lecture de la position d'une source, confiee au thread du son : une par emplacement, jamais reallouee. */
    private static final class OffsetReader implements Consumer<Source> {

        private final int slot;

        private OffsetReader(int slot) {
            this.slot = slot;
        }

        @Override
        public void accept(Source source) {
            OFFSETS.set(this.slot, Float.floatToIntBits(AL10.alGetSourcef(((SourceAccessor) source).backrooms$getPointer(), AL11.AL_SEC_OFFSET)));
            READ_AT.set(this.slot, System.nanoTime());
        }
    }

    static {
        for (int i = 0; i < CAPACITY; i++) {
            READERS[i] = new OffsetReader(i);
        }
    }

    private CueTracker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Un son emis dans une dimension profilee part : s'il est lie, sa perturbation commence.
     *
     * @param sound le son
     * @param dimension dimension d'emission
     * @param sx vraie source
     * @param sy vraie source
     * @param sz vraie source
     * @param tick tick du monde
     */
    public static void onPlay(SoundInstance sound, Identifier dimension, double sx, double sy, double sz, long tick) {
        Cue cue = CueRegistry.of(sound.getId());
        if (cue == null) {
            return;
        }
        int slot = DISTURBANCES.start(dimension, sx, sy, sz, cue, tick);
        SOUNDS[slot] = sound;
        ENVELOPES[slot] = cue.envelope() ? CueResources.envelope(sound.getSound().getLocation()) : null;
        OFFSETS.set(slot, Float.floatToIntBits(0.0f));
        READ_AT.set(slot, System.nanoTime());
    }

    /**
     * Chaque tick : les sons finis liberent leur perturbation, les autres relisent leur position de lecture, et ceux qui
     * suivent une entite leur place.
     */
    @SuppressWarnings("try")
    public static void onClientTick(MinecraftClient client) {
        if (DISTURBANCES.count() == 0) {
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_CUE)) {
            Map<SoundInstance, Channel.SourceManager> playing = sources(client);
            for (int slot = 0; slot < CAPACITY; slot++) {
                if (!DISTURBANCES.active(slot)) {
                    continue;
                }
                SoundInstance sound = SOUNDS[slot];
                Channel.SourceManager manager = playing.get(sound);
                if (manager == null || manager.isStopped()) {
                    end(slot);
                    continue;
                }
                if (ENVELOPES[slot] != null) {
                    manager.run(READERS[slot]);
                }
                // Seul un son qui suit une entite bouge : les autres restent a leur vraie source, posee au depart (un son
                // rejoue au cadre d'un portail joue ailleurs, dans le monde de l'auditeur, mais perturbe la ou il est emis).
                if (sound instanceof TickableSoundInstance) {
                    DISTURBANCES.move(slot, sound.getX(), sound.getY(), sound.getZ());
                }
            }
        }
    }

    /**
     * Une vue de portail se dessine : sa dimension et sa part de l'ecran comptent pour le plafond de l'image suivante.
     *
     * @param dimension dimension de la vue
     * @param area part de l'ecran que couvre son cadre, de 0 a 1
     */
    public static void viewSeen(Identifier dimension, float area) {
        if (views < MAX_VIEWS) {
            VIEW_DIMENSIONS[views] = dimension;
            VIEW_AREAS[views++] = area;
        }
    }

    /**
     * Debut d'image : l'amplitude de chaque perturbation a la position de lecture de son son, puis le plafond de
     * clignotement sur l'effet de toutes ensemble, chaque vue de l'image precedente a sa surface.
     *
     * @param nowMillis instant de l'image
     * @param player dimension du monde du joueur
     */
    public static void beginFrame(long nowMillis, @Nullable Identifier player) {
        long now = System.nanoTime();
        for (int slot = 0; slot < CAPACITY; slot++) {
            if (!DISTURBANCES.active(slot)) {
                continue;
            }
            float amplitude = 1.0f;
            Envelope envelope = ENVELOPES[slot];
            if (envelope != null) {
                float seconds = Float.intBitsToFloat(OFFSETS.get(slot)) + (float) ((now - READ_AT.get(slot)) / NANOS_PER_SECOND);
                amplitude = envelope.at(seconds);
            }
            DISTURBANCES.amplitude(slot, amplitude);
        }
        for (int i = 0; i < views; i++) {
            VIEW_LOADS[i] = DISTURBANCES.load(VIEW_DIMENSIONS[i]);
            VIEW_DIMENSIONS[i] = null;
        }
        float wanted = FlashLimiter.screenLoad(VIEW_AREAS, VIEW_LOADS, views, DISTURBANCES.load(player));
        views = 0;
        float limited = LIMITER.limit(wanted, nowMillis);
        scale = wanted > 0.0f ? Math.min(1.0f, limited / wanted) : 1.0f;
    }

    /**
     * Tremblement de camera du joueur, de 0 a 1 : le cri le plus fort a portee dans sa propre dimension (une dimension
     * vanilla n'a jamais de perturbation), plafond compris ; rien sans l'option qui l'autorise ou avec celle de confort.
     *
     * @param dimension dimension du monde du joueur
     * @param lx camera du joueur
     * @param ly camera du joueur
     * @param lz camera du joueur
     */
    public static float cameraShake(@Nullable Identifier dimension, double lx, double ly, double lz) {
        if (!ModConfig.AUDIO_CAMERA_SHAKE || ModConfig.REDUCE_FLASHES) {
            return 0.0f;
        }
        return DISTURBANCES.cameraShake(dimension, lx, ly, lz) * scale;
    }

    /**
     * Apres son orientation par vanilla, la camera du joueur tremble du cri le plus fort a portee dans sa dimension
     * ({@link CameraShake}, sur l'horloge commune), a l'echelle des effets d'ecran du jeu. Rien le plus souvent : aucune
     * perturbation, option coupee, ou dimension vanilla.
     *
     * @param camera camera du joueur
     * @param world monde du joueur
     * @param tickDelta fraction de tick de l'image
     * @param screenEffects echelle des effets d'ecran du jeu (option d'accessibilite), de 0 a 1
     */
    public static void shakeCamera(Camera camera, ClientWorld world, float tickDelta, double screenEffects) {
        Vec3d pos = camera.getPos();
        float amplitude = cameraShake(world.getRegistryKey().getValue(), pos.x, pos.y, pos.z) * (float) screenEffects;
        if (amplitude <= 0.0f) {
            return;
        }
        float seconds = CueClock.seconds(world.getTime(), tickDelta);
        camera.getRotation().rotateY((float) Math.toRadians(-CameraShake.yaw(seconds, amplitude)))
                .rotateX((float) Math.toRadians(CameraShake.pitch(seconds, amplitude)));
        PortalViewTransform.updatePlanes(camera);
    }

    /** Ce qu'il reste a un neon de son intensite, de 0 a 1, plafond compris. */
    public static float neonFactor(@Nullable Identifier dimension, double x, double y, double z) {
        return 1.0f - (1.0f - DISTURBANCES.neonFactor(dimension, x, y, z)) * scale;
    }

    /**
     * {@code true} si la perturbation de cet emplacement agit sur les neons de cette dimension : le rendu envoie alors sa
     * source, sa portee et sa force ({@link #neonForce}) au jumeau GLSL du calendrier, pour la face de chaque neon.
     */
    public static boolean disturbsNeons(int slot, @Nullable Identifier dimension) {
        return DISTURBANCES.disturbsNeons(slot, dimension);
    }

    /** Source d'une perturbation, axe x. */
    public static double disturbanceX(int slot) {
        return DISTURBANCES.x(slot);
    }

    /** Source d'une perturbation, axe y. */
    public static double disturbanceY(int slot) {
        return DISTURBANCES.y(slot);
    }

    /** Source d'une perturbation, axe z. */
    public static double disturbanceZ(int slot) {
        return DISTURBANCES.z(slot);
    }

    /** Portee d'une perturbation sur les neons. */
    public static float neonRadius(int slot) {
        return DISTURBANCES.neonRadius(slot);
    }

    /** Ce qu'une perturbation retire au plus a un neon a sa source, plafond de clignotement compris. */
    public static float neonForce(int slot) {
        return DISTURBANCES.neonForce(slot) * scale;
    }

    /** Pulsation de l'ecran d'une dimension, de 0 a 1 ; rien avec l'option de confort. */
    public static float screenPulse(@Nullable Identifier dimension, double lx, double ly, double lz) {
        return ModConfig.REDUCE_FLASHES ? 0.0f : DISTURBANCES.screenPulse(dimension, lx, ly, lz) * scale;
    }

    /** Baisse de la lumiere d'une dimension, de 0 a 1, plafond compris. */
    public static float lightmapFlicker(@Nullable Identifier dimension) {
        return DISTURBANCES.lightmapFlicker(dimension) * scale;
    }

    /** Diagnostic : les perturbations en cours (dimension, source, amplitude) et le plafond de clignotement. */
    public static void describe(StringBuilder out) {
        out.append("Perturbations en cours : ").append(DISTURBANCES.count()).append(", plafond de clignotement ").append(LIMITER.value()).append('\n');
        for (int slot = 0; slot < Disturbances.CAPACITY; slot++) {
            if (DISTURBANCES.active(slot)) {
                out.append("  ").append(slot).append(" : ").append(DISTURBANCES.dimension(slot)).append(" en (").append(DISTURBANCES.x(slot)).append(", ")
                        .append(DISTURBANCES.y(slot)).append(", ").append(DISTURBANCES.z(slot)).append("), amplitude ").append(DISTURBANCES.currentAmplitude(slot))
                        .append('\n');
            }
        }
    }

    /** Fin de partie, ou moteur de son ferme : plus aucune perturbation. */
    public static void clear() {
        for (int slot = 0; slot < CAPACITY; slot++) {
            end(slot);
        }
        LIMITER.reset();
        scale = 1.0f;
        views = 0;
    }

    private static void end(int slot) {
        DISTURBANCES.end(slot);
        SOUNDS[slot] = null;
        ENVELOPES[slot] = null;
    }

    private static Map<SoundInstance, Channel.SourceManager> sources(MinecraftClient client) {
        return ((SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem()).backrooms$getSources();
    }
}
