package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.Source;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticCache;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticEvaluator;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.AcousticResult;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.EmissionDimension;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.PerceivedDirection;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.PortalPlanes;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.ReverbPresets;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.SolidityGrid;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SourceAccessor;

/**
 * Le traitement de chaque son que le moteur de son joue, decide par la dimension ou il est emis
 * ({@link EmissionDimension}) : un son d'une dimension profilee recoit la reverb de sa dimension, un filtre d'occlusion
 * d'apres les blocs entre lui et l'auditeur et l'absorption de l'air ; un son d'une dimension vanilla n'est pas touche.
 * Quand le son part, son occlusion est celle d'un rayon droit lance sur le monde vivant ({@link OcclusionTrace}, sur le
 * thread de rendu) et sa reverb la derniere connue de sa cellule, ou celle de base : il n'attend rien. Dans une
 * dimension calculee par rayons, le thread de calcul ({@link AcousticWorker}) lui donne ensuite la reverb de l'espace
 * autour de sa source ({@link ReverbPresets}) et une occlusion par plusieurs chemins, revues toutes les dix ticks tant
 * qu'il joue ; ailleurs, le rayon droit est revu toutes les dix ticks. Un son rejoue au cadre d'un portail
 * ({@code FrameSoundPlayer}, le bourdonnement d'un neon de l'autre cote) garde la dimension de sa vraie source : son
 * occlusion se mesure en deux segments, de la source au cadre dans son monde (le point d'entree est dans son ancre,
 * {@link SoundAnchors}), puis du cadre a l'auditeur dans le monde du joueur ; et la reverb de la salle de l'auditeur
 * s'ajoute a la sienne par le second envoi, le cadre etant une seconde source dans cette salle. Un son dont l'auditeur
 * ou la source est sous l'eau perd en plus ce que le profil retire dans l'eau. Sans l'extension EFX, l'occlusion et le
 * milieu se rendent par le volume du son ({@link EfxBackend.Mode#GAIN}) ; apres une erreur OpenAL ou devant un autre
 * moteur acoustique, plus rien n'est traite. Quand le chemin droit est bouche et qu'un detour passe mieux, la source se
 * deplace vers l'ouverture, de la part que dit le profil (direction percue). La liaison son-image ({@link CueTracker})
 * ne depend d'aucun de ces modes.
 * Les appels OpenAL partent sur le thread du son par la source de chaque son.
 */
public final class AcousticRouter {

    /** Un son traite : son profil, le filtre qu'il tient, sa reverb, la salle de l'auditeur, son milieu. */
    private static final class Routed {

        private final AudioProfile profile;
        /** La source du moteur traitee : une meme instance rejouee en a une autre. */
        private final Channel.SourceManager manager;
        private final int filter;
        private final boolean raycast;
        /** Sans EFX : l'occlusion se rend par le volume. */
        private final boolean gainOnly;
        /** Source dans l'eau quand le son est parti. */
        private final boolean sourceInWater;
        private float occlusion;
        private int preset;
        private AudioProfile room;
        private RegistryKey<World> listener;
        /** Milieu applique au filtre : l'auditeur ou la source sous l'eau. */
        private boolean submerged;
        /** Decalage de la source pose pour la direction percue. */
        private double offsetX;
        private double offsetY;
        private double offsetZ;

        private Routed(AudioProfile profile, Channel.SourceManager manager, AudioProfile room, RegistryKey<World> listener, int filter,
                boolean gainOnly, boolean sourceInWater, boolean submerged, float occlusion, int preset) {
            this.profile = profile;
            this.manager = manager;
            this.room = room;
            this.listener = listener;
            this.filter = filter;
            this.gainOnly = gainOnly;
            this.sourceInWater = sourceInWater;
            this.submerged = submerged;
            this.occlusion = occlusion;
            this.preset = preset;
            this.raycast = computed(profile);
        }

        /** {@code true} si l'occlusion de ce son se rend (filtre ou volume). */
        private boolean rendered() {
            return this.filter >= 0 || this.gainOnly;
        }
    }

    private static final int PERIOD_TICKS = 10;
    private static final float OCCLUSION_STEP = 0.05f;
    /** En deca, un nouveau decalage de la direction percue n'est pas repose. */
    private static final double OFFSET_STEP = 0.25;
    private static final double[] OFFSET = new double[3];
    private static final String PERF_ROUTE = "client.audio.route";
    private static final String PERF_TICK = "client.audio.tick";
    /**
     * Les sons traites, par instance (aucune implementation vanilla ne redefinit {@code equals} : identite, comme la table
     * du moteur). Cles fortes : avec des cles faibles, un son fini que le ramasse-miettes avait deja repris disparaissait
     * d'ici avant la revue, son filtre jamais rendu. La revue retire ce que la table du moteur n'a plus.
     */
    private static final Map<SoundInstance, Routed> ROUTED = new HashMap<>();
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();
    private static final OcclusionTrace.Solidity SOLIDITY = AcousticRouter::solidityAt;
    /** Les cadres de portail autour de la source d'une demande, remplis a chaque demande puis copies dans celle-ci. */
    private static final PortalPlanes PLANES = new PortalPlanes();
    private static @Nullable ClientWorld traceWorld;
    private static int ticks;

    private AcousticRouter() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Un son part au moteur de son, sa source creee : s'il est emis dans une dimension profilee, il est lie a l'image
     * s'il le doit, puis traite selon le mode de l'acoustique.
     */
    @SuppressWarnings("try")
    public static void onPlay(SoundInstance sound) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (!ModConfig.ENABLE_AUDIO || world == null) {
            return;
        }
        Routed previous = ROUTED.get(sound);
        if (previous != null) {
            Channel.SourceManager current = sources(client).get(sound);
            if (current == null || current == previous.manager) {
                return;
            }
            // La meme instance rejouee (son repete apres un delai) avant que la revue n'ait vu finir l'ancienne source :
            // l'ancien filtre revient a la reserve, la nouvelle source se traite.
            EfxBackend.releaseFilter(previous.filter);
            ROUTED.remove(sound);
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_ROUTE)) {
            RegistryKey<World> listener = world.getRegistryKey();
            SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(sound);
            RegistryKey<World> anchored = anchor == null ? null : anchor.world();
            AudioProfile profile = AudioProfiles.of(EmissionDimension.resolve(anchored, listener, sound.isRelative()));
            if (profile.vanilla()) {
                return;
            }
            // Emis dans une dimension profilee : s'il est lie a l'image, sa perturbation commence a sa vraie source, avec ou
            // sans EFX (la liaison son-image ne depend pas de l'acoustique).
            CueTracker.onPlay(sound, profile.dimension(), anchor == null ? sound.getX() : anchor.source().x, anchor == null ? sound.getY() : anchor.source().y,
                    anchor == null ? sound.getZ() : anchor.source().z, world.getTime());
            EfxBackend.Mode mode = EfxBackend.mode();
            if (mode == EfxBackend.Mode.OFF) {
                return;
            }
            Channel.SourceManager manager = sources(client).get(sound);
            if (manager == null) {
                return;
            }
            AudioProfile room = AudioProfiles.of(EmissionDimension.room(anchored, listener));
            float occlusion = occlusion(client, world, sound, anchor, profile);
            int preset = computed(profile) ? knownPreset(sound, anchor, world) : ReverbPresets.BASE;
            // L'option qui reduit les graves passe par le filtre de la source : toute source traitee en a un.
            boolean filtered = profile.occlusion().any() || profile.underwater().any() || ModConfig.AUDIO_REDUCE_BASS;
            boolean gainOnly = mode == EfxBackend.Mode.GAIN && filtered;
            int filter = mode == EfxBackend.Mode.EFX && filtered ? EfxBackend.takeFilter() : -1;
            boolean sourceInWater = profile.underwater().any() && sourceInWater(sound, anchor, world);
            boolean submerged = sourceInWater || cameraInWater(client);
            Routed routed = new Routed(profile, manager, room, listener, filter, gainOnly, sourceInWater, submerged, occlusion, preset);
            ROUTED.put(sound, routed);
            float gain = gain(routed, occlusion);
            float gainHf = gainHf(routed, occlusion);
            if (mode == EfxBackend.Mode.EFX) {
                manager.run(source -> EfxBackend.apply(pointer(source), profile, preset, room, filter, gain, gainHf));
            } else if (gainOnly) {
                float kept = OcclusionTrace.fallbackGain(gain, gainHf);
                manager.run(source -> EfxBackend.applyGain(source, kept));
            }
            if (routed.raycast) {
                request(client, world, sound, anchor, profile);
            }
        }
    }

    /**
     * Diagnostic : le mode du moteur et chaque son traite (dimension, reglage, occlusion, salle, filtre, milieu, decalage),
     * et "source perimee" si le son joue sur une autre source que celle qui a recu ses reglages.
     */
    public static void describe(StringBuilder out) {
        Map<SoundInstance, Channel.SourceManager> playing = sources(MinecraftClient.getInstance());
        out.append("Sons traites : ").append(ROUTED.size()).append(", mode ").append(EfxBackend.mode()).append('\n');
        for (Map.Entry<SoundInstance, Routed> entry : ROUTED.entrySet()) {
            Routed routed = entry.getValue();
            out.append("  ").append(entry.getKey().getId()).append(" : ").append(routed.profile.dimension()).append(", reglage ").append(routed.preset)
                    .append(", occlusion ").append(routed.occlusion).append(", salle ").append(routed.room.dimension()).append(", filtre ")
                    .append(routed.filter).append(routed.gainOnly ? ", volume seul" : "").append(routed.submerged ? ", sous l'eau" : "")
                    .append(", decalage (").append(routed.offsetX).append(", ").append(routed.offsetY).append(", ").append(routed.offsetZ).append(')')
                    .append(playing.get(entry.getKey()) == routed.manager ? "" : ", source perimee").append('\n');
        }
    }

    /**
     * Chaque tick : les resultats du thread de calcul sont appliques aux sons qui jouent encore ; toutes les dix ticks,
     * chaque son traite encore joue est revu (demande au thread de calcul, ou rayon droit : l'auditeur a bouge), la
     * salle de l'auditeur suit sa dimension (il a traverse), et les filtres des sons finis sont rendus a la reserve.
     */
    @SuppressWarnings("try")
    public static void onClientTick(MinecraftClient client) {
        // Reverbs creees d'avance, une fois un rechargement fini (moteur de son et profils a jour) : ecran titre compris.
        if (client.getOverlay() == null) {
            EfxBackend.prewarmIfDue(((SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem()).backrooms$getTaskQueue());
        }
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }
        for (AcousticWorker.Result result = AcousticWorker.poll(); result != null; result = AcousticWorker.poll()) {
            Routed routed = ROUTED.get(result.sound());
            if (routed != null) {
                Channel.SourceManager manager = sources(client).get(result.sound());
                if (manager != null && !manager.isStopped()) {
                    apply(client, world, manager, routed, result);
                    perceive(client, manager, routed, result);
                }
            }
            AcousticWorker.release(result);
        }
        if (ROUTED.isEmpty() || ++ticks % PERIOD_TICKS != 0) {
            return;
        }
        if (EfxBackend.mode() == EfxBackend.Mode.OFF) {
            // Erreur OpenAL ou autre moteur acoustique : plus rien n'est touche, la reserve est rendue.
            clear();
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_TICK)) {
            RegistryKey<World> listener = world.getRegistryKey();
            boolean cameraInWater = cameraInWater(client);
            boolean verified = false;
            Map<SoundInstance, Channel.SourceManager> playing = sources(client);
            Iterator<Map.Entry<SoundInstance, Routed>> entries = ROUTED.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<SoundInstance, Routed> entry = entries.next();
                SoundInstance sound = entry.getKey();
                Routed routed = entry.getValue();
                Channel.SourceManager manager = playing.get(sound);
                if (manager == null || manager.isStopped()) {
                    EfxBackend.releaseFilter(routed.filter);
                    entries.remove();
                    continue;
                }
                if (!verified && routed.filter >= 0) {
                    // Un son par revue : son filtre direct est-il encore le notre ? (Un autre moteur se reconnait la.)
                    verified = true;
                    manager.run(EfxBackend.VERIFIER);
                }
                boolean submerged = cameraInWater || routed.sourceInWater;
                boolean mediumChanged = routed.profile.underwater().any() && submerged != routed.submerged;
                routed.submerged = submerged;
                if (mediumChanged) {
                    renderOcclusion(manager, routed, routed.occlusion);
                }
                SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(sound);
                if (!listener.equals(routed.listener)) {
                    // Le joueur a change de monde : la reverb de sa salle suit (evenement rare, la tache est admise).
                    routed.listener = listener;
                    AudioProfile room = AudioProfiles.of(EmissionDimension.room(anchor == null ? null : anchor.world(), listener));
                    if (!room.equals(routed.room)) {
                        routed.room = room;
                        AudioProfile profile = routed.profile;
                        int preset = routed.preset;
                        manager.run(source -> EfxBackend.sends(pointer(source), profile, preset, room));
                    }
                }
                if (routed.raycast) {
                    request(client, world, sound, anchor, routed.profile);
                    continue;
                }
                if (routed.rendered()) {
                    updateFilter(manager, routed, occlusion(client, world, sound, anchor, routed.profile));
                }
            }
        }
    }

    /**
     * Le joueur a traverse un portail : la revue suivante (la salle de l'auditeur, son second envoi, l'occlusion depuis sa
     * nouvelle place) se fait au prochain tick plutot qu'a la fin de la periode.
     */
    public static void onWorldSwitched() {
        ticks = PERIOD_TICKS - 1;
    }

    /**
     * Fin de partie, ou moteur de son ferme (ses sources et ses filtres disparaissent avec son contexte) : plus aucun son
     * traite, la reserve de filtres rendue.
     */
    public static void clear() {
        ROUTED.clear();
        EfxBackend.releaseAllFilters();
        traceWorld = null;
    }

    /**
     * Le resultat du thread de calcul : la reverb de l'espace autour de la source (si assez de rayons sont restes en monde
     * copie, sinon le son garde la sienne), et l'occlusion par les chemins. Quand le monde de l'auditeur n'est pas copie
     * (un son du Level 0 entendu au cadre depuis l'Overworld), le calcul n'a mesure que le trajet de la source au cadre :
     * celui du cadre a l'auditeur se mesure ici, par le rayon droit sur le monde vivant, et les deux se combinent.
     */
    private static void apply(MinecraftClient client, ClientWorld world, Channel.SourceManager manager, Routed routed, AcousticWorker.Result computed) {
        AcousticResult result = computed.result();
        float occlusion = result.occlusion();
        AudioProfile.Occlusion rule = routed.profile.occlusion();
        if (!computed.listenerTraced()) {
            occlusion = OcclusionTrace.combine(listenerSegment(client, world, computed.sound(), SoundAnchors.anchorOf(computed.sound()), rule), occlusion, rule);
        }
        int preset = result.reliable() ? ReverbPresets.index(result) : routed.preset;
        if (preset != routed.preset) {
            routed.preset = preset;
            AudioProfile profile = routed.profile;
            AudioProfile room = routed.room;
            manager.run(source -> EfxBackend.sends(pointer(source), profile, preset, room));
        }
        if (routed.rendered()) {
            updateFilter(manager, routed, occlusion);
        }
    }

    /**
     * La direction percue : la source se deplace vers le point de passage du meilleur chemin d'occlusion, de la part du
     * profil ponderee par ce que ce chemin gagne sur le droit ; reposee si le decalage a assez change.
     */
    private static void perceive(MinecraftClient client, Channel.SourceManager manager, Routed routed, AcousticWorker.Result result) {
        float share = routed.profile.perceivedDirection();
        if (share <= 0.0f) {
            return;
        }
        float blend = PerceivedDirection.blend(share, result.direct(), result.result().occlusion(), routed.profile.occlusion().max());
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        SoundInstance sound = result.sound();
        SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(sound);
        PerceivedDirection.offset(playedX(sound, anchor), playedY(sound, anchor), playedZ(sound, anchor), listener.x, listener.y, listener.z,
                result.waypointX(), result.waypointY(), result.waypointZ(), blend, OFFSET);
        double dx = OFFSET[0];
        double dy = OFFSET[1];
        double dz = OFFSET[2];
        if (Math.abs(dx - routed.offsetX) < OFFSET_STEP && Math.abs(dy - routed.offsetY) < OFFSET_STEP && Math.abs(dz - routed.offsetZ) < OFFSET_STEP) {
            return;
        }
        routed.offsetX = dx;
        routed.offsetY = dy;
        routed.offsetZ = dz;
        manager.run(source -> EfxBackend.perceive(source, dx, dy, dz));
    }

    /** Le filtre (ou le volume) d'un son est repose si son occlusion a assez change. */
    private static void updateFilter(Channel.SourceManager manager, Routed routed, float occlusion) {
        if (Math.abs(occlusion - routed.occlusion) < OCCLUSION_STEP) {
            return;
        }
        renderOcclusion(manager, routed, occlusion);
    }

    /** Pose l'occlusion et le milieu d'un son : son filtre avec EFX, son volume sans. */
    private static void renderOcclusion(Channel.SourceManager manager, Routed routed, float occlusion) {
        routed.occlusion = occlusion;
        float gain = gain(routed, occlusion);
        float gainHf = gainHf(routed, occlusion);
        if (routed.gainOnly) {
            float kept = OcclusionTrace.fallbackGain(gain, gainHf);
            manager.run(source -> EfxBackend.applyGain(source, kept));
        } else if (routed.filter >= 0) {
            int filter = routed.filter;
            manager.run(source -> EfxBackend.filter(pointer(source), filter, gain, gainHf));
        }
    }

    private static float gain(Routed routed, float occlusion) {
        return OcclusionTrace.filterGain(occlusion, routed.submerged ? routed.profile.underwater().gain() : 1.0f);
    }

    private static float gainHf(Routed routed, float occlusion) {
        return OcclusionTrace.filterGainHf(occlusion, routed.submerged ? routed.profile.underwater().gainHf() : 1.0f);
    }

    /** {@code true} si la camera est sous l'eau. */
    private static boolean cameraInWater(MinecraftClient client) {
        return client.gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.WATER;
    }

    /** {@code true} si la vraie source d'un son est dans l'eau, dans son monde. */
    private static boolean sourceInWater(SoundInstance sound, @Nullable SoundAnchors.Anchor anchor, ClientWorld world) {
        ClientWorld sourceWorld = anchor == null ? world : RemoteWorldRegistry.worldOf(anchor.world());
        if (sourceWorld == null) {
            return false;
        }
        if (anchor == null) {
            CURSOR.set(sound.getX(), sound.getY(), sound.getZ());
        } else {
            CURSOR.set(anchor.source().x, anchor.source().y, anchor.source().z);
        }
        return sourceWorld.getFluidState(CURSOR).isIn(FluidTags.WATER);
    }

    /**
     * Demande au thread de calcul l'acoustique d'un son : la reverb autour de sa vraie source, dans la copie de son monde,
     * a travers les cadres de portail ouverts a portee des rayons (dont l'autre cote est copie), et son occlusion jusqu'a
     * l'auditeur. Rien tant que le monde de la source n'est pas copie (les premiers ticks d'une partie) : le son garde
     * ce qu'il a.
     */
    private static void request(MinecraftClient client, ClientWorld world, SoundInstance sound, @Nullable SoundAnchors.Anchor anchor, AudioProfile profile) {
        ClientWorld sourceWorld = anchor == null ? world : RemoteWorldRegistry.worldOf(anchor.world());
        if (sourceWorld == null) {
            return;
        }
        SolidityGrid sourceGrid = SolidityIndex.grid(sourceWorld);
        if (sourceGrid == null) {
            return;
        }
        AcousticWorker.ensureStarted();
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        double sx = anchor == null ? sound.getX() : anchor.source().x;
        double sy = anchor == null ? sound.getY() : anchor.source().y;
        double sz = anchor == null ? sound.getZ() : anchor.source().z;
        PLANES.clear();
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(sourceWorld);
        for (int i = 0; i < portals.size() && PLANES.count() < PortalPlanes.CAPACITY; i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (!portal.visible() || !portal.teleportable() || portal.squaredDistanceToFrame(sx, sy, sz) > AcousticEvaluator.MAX_RAY * AcousticEvaluator.MAX_RAY) {
                continue;
            }
            ClientWorld arrivalWorld = RemoteWorldRegistry.worldOf(portal.destinationDim());
            SolidityGrid arrivalGrid = arrivalWorld == null ? null : SolidityIndex.grid(arrivalWorld);
            if (arrivalGrid != null) {
                PLANES.add(portal.facingSource(), portal.getPos(), portal.width(), portal.height(), portal.facingDestination(), portal.destinationPos(), arrivalGrid);
            }
        }
        GeneratedAcoustics.Shape shape = profile.acoustics() == AudioProfile.Acoustics.GENERATED ? GeneratedAcoustics.of(profile.dimension()) : null;
        AcousticWorker.request(sound, profile.occlusion(), sourceGrid, SolidityIndex.cache(sourceWorld), shape, sx, sy, sz,
                PLANES.count() > 0 ? PLANES : null, SolidityIndex.grid(world), playedX(sound, anchor), playedY(sound, anchor), playedZ(sound, anchor), listener,
                anchor == null ? null : anchor.arrival(), world.getTime());
        PLANES.clear();
    }

    /** {@code true} si l'acoustique de ce profil se calcule hors du thread de rendu (rayons ou forme generee). */
    private static boolean computed(AudioProfile profile) {
        return profile.acoustics().computed();
    }

    /** La reverb connue de la cellule de la source d'un son qui part, ou celle de base. */
    private static int knownPreset(SoundInstance sound, @Nullable SoundAnchors.Anchor anchor, ClientWorld world) {
        ClientWorld sourceWorld = anchor == null ? world : RemoteWorldRegistry.worldOf(anchor.world());
        if (sourceWorld == null) {
            return ReverbPresets.BASE;
        }
        AcousticCache cache = SolidityIndex.cache(sourceWorld);
        AcousticResult known = anchor == null
                ? cache.get(AcousticCache.cellOf(sound.getX(), sound.getY(), sound.getZ()))
                : cache.get(AcousticCache.cellOf(anchor.source().x, anchor.source().y, anchor.source().z));
        return known == null ? ReverbPresets.BASE : ReverbPresets.index(known);
    }

    /**
     * Occlusion entre un son et la camera par un rayon droit sur le monde vivant : du point ou il joue a la camera, dans
     * le monde du joueur ; et, pour un son entre par un cadre, de sa vraie source au point d'entree dans le cadre, dans
     * son monde, les deux segments combines.
     */
    private static float occlusion(MinecraftClient client, ClientWorld world, SoundInstance sound, @Nullable SoundAnchors.Anchor anchor,
            AudioProfile profile) {
        AudioProfile.Occlusion rule = profile.occlusion();
        if (!rule.any()) {
            return 0.0f;
        }
        float total = listenerSegment(client, world, sound, anchor, rule);
        Vec3d arrival = anchor == null ? null : anchor.arrival();
        if (arrival != null) {
            ClientWorld sourceWorld = RemoteWorldRegistry.worldOf(anchor.world());
            if (sourceWorld != null) {
                Vec3d source = anchor.source();
                total = OcclusionTrace.combine(total, trace(sourceWorld, source.x, source.y, source.z, arrival.x, arrival.y, arrival.z, rule), rule);
            }
        }
        return total;
    }

    /** Occlusion du point ou un son joue (dans le monde du joueur) a la camera, par un rayon droit sur le monde vivant. */
    private static float listenerSegment(MinecraftClient client, ClientWorld world, SoundInstance sound, @Nullable SoundAnchors.Anchor anchor,
            AudioProfile.Occlusion rule) {
        if (!rule.any()) {
            return 0.0f;
        }
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        return trace(world, playedX(sound, anchor), playedY(sound, anchor), playedZ(sound, anchor), listener.x, listener.y, listener.z, rule);
    }

    /**
     * Le point ou un son joue maintenant, dans le monde du joueur : celui de son ancre (replace apres une traversee), ou
     * ses coordonnees s'il n'en a pas (un son qui suit une entite).
     */
    private static double playedX(SoundInstance sound, @Nullable SoundAnchors.Anchor anchor) {
        return anchor == null ? sound.getX() : anchor.played().x;
    }

    /** Voir {@link #playedX}. */
    private static double playedY(SoundInstance sound, @Nullable SoundAnchors.Anchor anchor) {
        return anchor == null ? sound.getY() : anchor.played().y;
    }

    /** Voir {@link #playedX}. */
    private static double playedZ(SoundInstance sound, @Nullable SoundAnchors.Anchor anchor) {
        return anchor == null ? sound.getZ() : anchor.played().z;
    }

    private static float trace(ClientWorld world, double sx, double sy, double sz, double lx, double ly, double lz, AudioProfile.Occlusion rule) {
        traceWorld = world;
        try {
            return OcclusionTrace.occlusion(sx, sy, sz, lx, ly, lz, SOLIDITY, rule);
        } finally {
            traceWorld = null;
        }
    }

    private static int solidityAt(int x, int y, int z) {
        ClientWorld world = traceWorld;
        if (world == null) {
            return OcclusionTrace.AIR;
        }
        CURSOR.set(x, y, z);
        BlockState state = world.getBlockState(CURSOR);
        return BlockSolidity.of(state, world, CURSOR);
    }

    private static Map<SoundInstance, Channel.SourceManager> sources(MinecraftClient client) {
        return ((SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem()).backrooms$getSources();
    }

    private static int pointer(Source source) {
        return ((SourceAccessor) source).backrooms$getPointer();
    }
}
