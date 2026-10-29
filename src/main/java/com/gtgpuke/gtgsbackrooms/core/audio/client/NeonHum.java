package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.PortalSound;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;

/**
 * Le bourdonnement des neons : les quelques tubes les plus proches de la camera, dans le monde du joueur, et, derriere
 * chaque portail visible, les tubes les plus proches de son arrivee dans l'autre monde, entendus au cadre comme tout son
 * de l'autre cote ({@link PortalSound}), jouent en boucle le son d'ambiance de leur dimension
 * ({@link AudioProfile.Ambience}), chacun a sa place ; un tube qui s'eloigne se tait, un qui s'approche se met a
 * bourdonner. Les neons viennent de l'index du moteur de shaders ({@link NeonIndex}, un par monde client), revu toutes
 * les dix ticks. Chaque bourdonnement suit l'etat de son tube ({@link NeonSchedule}, le meme que son image : une seule
 * source de verite, l'horloge du monde) : le volume de la source suit l'intensite, et un tube qui s'eteint ou se
 * rallume claque. Chaque son garde sa vraie source ({@link SoundAnchors}) : a la traversee du joueur, il est replace a
 * sa place ou a un autre cadre, et la revue suivante le retrouve tel quel. Les sons recoivent l'acoustique de leur
 * dimension comme les autres ({@link AcousticRouter}).
 */
public final class NeonHum {

    /** Tubes du monde du joueur qui bourdonnent a la fois. */
    static final int LOCAL = 4;
    /** Tubes entendus au cadre des portails qui bourdonnent a la fois. */
    static final int THROUGH_FRAMES = 4;
    static final int CAPACITY = LOCAL + THROUGH_FRAMES;
    /** Distance de la camera, ou de l'arrivee d'un portail, au-dela de laquelle un tube ne bourdonne pas. */
    private static final double REACH = 12.0;
    private static final int PERIOD_TICKS = 10;
    /** Ecart d'intensite a partir duquel le volume d'une source est repose. */
    private static final float STATE_STEP = 0.05f;
    /** Un tube est allume au-dessus de cette intensite : passer d'un cote a l'autre claque. */
    private static final float LIT = 0.5f;
    /** Un claquement est plus fort que le bourdonnement. */
    private static final float CLICK_GAIN = 2.0f;
    private static final NeonSelection SELECTION = new NeonSelection(LOCAL);
    private static final NeonSelection FRAME_SELECTION = new NeonSelection(THROUGH_FRAMES);
    /** Candidats de la revue en cours : dimension, position, portail par lequel on les entend (aucun pour le monde du joueur). */
    private static final Identifier[] CANDIDATE_DIMENSIONS = new Identifier[CAPACITY];
    private static final long[] CANDIDATE_POSITIONS = new long[CAPACITY];
    private static final BackroomsPortalEntity[] CANDIDATE_PORTALS = new BackroomsPortalEntity[CAPACITY];
    private static int candidates;
    /** Tubes qui bourdonnent : dimension, position, instance jouee, derniere intensite appliquee. */
    private static final Identifier[] HUM_DIMENSIONS = new Identifier[CAPACITY];
    private static final long[] HUMMING = new long[CAPACITY];
    private static final SoundInstance[] INSTANCES = new SoundInstance[CAPACITY];
    private static final float[] STATES = new float[CAPACITY];
    private static int count;
    private static int ticks;

    private NeonHum() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Chaque tick : toutes les dix ticks, les tubes qui bourdonnent sont ceux les plus proches de la camera et des cadres ;
     * a chaque tick, chaque bourdonnement suit l'etat de son tube.
     */
    public static void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ModConfig.ENABLE_AUDIO || world == null) {
            clear();
            return;
        }
        if (++ticks % PERIOD_TICKS == 0) {
            review(client, world);
        }
        follow(client, world);
    }

    /** Fin de partie, ou moteur de son ferme : les tubes sont oublies (le moteur de son arrete tout de lui-meme). */
    public static void clear() {
        for (int i = 0; i < count; i++) {
            HUM_DIMENSIONS[i] = null;
            INSTANCES[i] = null;
        }
        count = 0;
        forgetCandidates();
    }

    private static void review(MinecraftClient client, ClientWorld world) {
        // Volume general ou d'ambiance a zero : le moteur de son refuse chaque demande, inutile d'en refaire toutes les dix ticks.
        if (client.options.getSoundVolume(SoundCategory.MASTER) <= 0.0f || client.options.getSoundVolume(SoundCategory.AMBIENT) <= 0.0f) {
            stopAll(client);
            return;
        }
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        collectCandidates(world, listener);
        for (int i = count - 1; i >= 0; i--) {
            // Un tube hors de portee se tait ; un son que le moteur a lache (moteur de son relance, son arrete par vanilla)
            // est oublie, et le tube repart a cette revue s'il est encore proche.
            if (!candidate(HUM_DIMENSIONS[i], HUMMING[i]) || !client.getSoundManager().isPlaying(INSTANCES[i])) {
                client.getSoundManager().stop(INSTANCES[i]);
                count--;
                HUM_DIMENSIONS[i] = HUM_DIMENSIONS[count];
                HUMMING[i] = HUMMING[count];
                INSTANCES[i] = INSTANCES[count];
                STATES[i] = STATES[count];
                HUM_DIMENSIONS[count] = null;
                INSTANCES[count] = null;
            }
        }
        for (int i = 0; i < candidates && count < CAPACITY; i++) {
            if (humming(CANDIDATE_DIMENSIONS[i], CANDIDATE_POSITIONS[i])) {
                continue;
            }
            // Un tube qui se met a bourdonner : son instance est allouee a cet evenement seulement.
            BackroomsPortalEntity portal = CANDIDATE_PORTALS[i];
            SoundInstance instance = portal == null ? local(world, CANDIDATE_POSITIONS[i]) : throughFrame(portal, CANDIDATE_POSITIONS[i], listener);
            if (instance == null) {
                continue;
            }
            client.getSoundManager().play(instance);
            HUM_DIMENSIONS[count] = CANDIDATE_DIMENSIONS[i];
            HUMMING[count] = CANDIDATE_POSITIONS[i];
            INSTANCES[count] = instance;
            STATES[count++] = 1.0f;
        }
        forgetCandidates();
    }

    /**
     * Chaque bourdonnement suit l'etat de son tube : le volume de la source suit l'intensite du calendrier (le meme que
     * l'image lit), et un tube qui passe d'allume a eteint, ou l'inverse, claque. Un changement est un evenement : la
     * tache confiee au thread du son et le claquement sont alloues a ce moment seulement.
     */
    private static void follow(MinecraftClient client, ClientWorld world) {
        if (count == 0) {
            return;
        }
        long time = world.getTime();
        int tick = CueClock.tick(time);
        SoundSystemAccessor system = (SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem();
        Map<SoundInstance, Channel.SourceManager> playing = system.backrooms$getSources();
        for (int i = 0; i < count; i++) {
            LookProfile.Neons neons = LookProfiles.of(HUM_DIMENSIONS[i]).neons();
            float state = NeonSchedule.intensity(HUMMING[i], NeonSchedule.seed(HUM_DIMENSIONS[i]), tick, neons.flickering(), neons.dead());
            if (Math.abs(state - STATES[i]) < STATE_STEP) {
                continue;
            }
            boolean wasLit = STATES[i] >= LIT;
            STATES[i] = state;
            Channel.SourceManager manager = playing.get(INSTANCES[i]);
            if (manager == null) {
                continue;
            }
            float gain = system.backrooms$adjustedVolume(INSTANCES[i].getVolume() * state, SoundCategory.AMBIENT);
            manager.run(source -> source.setVolume(gain));
            if (wasLit != state >= LIT) {
                AudioProfile.Ambience ambience = AudioProfiles.of(HUM_DIMENSIONS[i]).ambience();
                click(client, INSTANCES[i], HUMMING[i] ^ time, state >= LIT ? ambience.clickOn() : ambience.clickOff(), ambience.humVolume());
            }
        }
    }

    /** Le claquement d'un tube, la ou son bourdonnement joue (a sa place, ou au cadre), avec la meme ancre. */
    private static void click(MinecraftClient client, SoundInstance hum, long seed, @Nullable Identifier sound, float humVolume) {
        if (sound == null) {
            return;
        }
        float volume = Math.min(1.0f, humVolume * CLICK_GAIN);
        // La ou le bourdonnement joue maintenant : replace apres une traversee, il n'est plus aux coordonnees de l'instance.
        SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(hum);
        Vec3d at = anchor == null ? new Vec3d(hum.getX(), hum.getY(), hum.getZ()) : anchor.played();
        SoundInstance click = new PositionedSoundInstance(sound, SoundCategory.AMBIENT, volume, 1.0f, Random.create(seed), false, 0,
                SoundInstance.AttenuationType.LINEAR, at.x, at.y, at.z, false);
        if (anchor != null && anchor.arrival() != null) {
            SoundAnchors.anchor(click, anchor.world(), anchor.source(), volume, anchor.arrival());
        }
        client.getSoundManager().play(click);
    }

    /**
     * Les tubes qui devraient bourdonner : ceux du monde du joueur a portee de la camera, puis, par portail visible et
     * ouvert vers un autre monde a ambiance, ceux de ce monde a portee de l'arrivee du portail, vus par le cadre et
     * audibles du joueur (devant le cadre, a portee du son). Rien n'est alloue : la geometrie se lit sans image.
     */
    private static void collectCandidates(ClientWorld world, Vec3d listener) {
        candidates = 0;
        RegistryKey<World> playerDimension = world.getRegistryKey();
        if (AudioProfiles.of(playerDimension).ambience().any()) {
            NeonIndex.collect(world, listener.x, listener.y, listener.z, REACH, SELECTION);
            for (int i = 0; i < SELECTION.count(); i++) {
                add(playerDimension.getValue(), SELECTION.position(i), null);
            }
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        int throughFrames = 0;
        for (int p = 0; p < portals.size() && throughFrames < THROUGH_FRAMES; p++) {
            BackroomsPortalEntity portal = portals.get(p);
            RegistryKey<World> destination = portal.destinationDim();
            if (!portal.visible() || !portal.teleportable() || destination.equals(playerDimension)) {
                continue;
            }
            AudioProfile.Ambience ambience = AudioProfiles.of(destination).ambience();
            if (!ambience.any()) {
                continue;
            }
            double radius = PortalSound.radius(ambience.humVolume());
            if (portal.signedDistance(listener) < -PortalSound.BEHIND_TOLERANCE
                    || portal.squaredDistanceToFrame(listener.x, listener.y, listener.z) > radius * radius) {
                continue;
            }
            ClientWorld remote = RemoteWorldRegistry.worldOf(destination);
            if (remote == null) {
                continue;
            }
            Vec3d arrival = portal.destinationPos();
            PortalFacing facing = portal.facingDestination();
            NeonIndex.collect(remote, arrival.x, arrival.y, arrival.z, REACH, FRAME_SELECTION);
            for (int i = 0; i < FRAME_SELECTION.count() && throughFrames < THROUGH_FRAMES; i++) {
                long packed = FRAME_SELECTION.position(i);
                // Un tube devant le cadre d'arrivee (du cote d'ou l'on arrive) n'est pas vu par le cadre : son image serait de ce cote-ci.
                double behind = facing.normal.x * (BlockPos.unpackLongX(packed) + 0.5 - arrival.x)
                        + facing.normal.y * (BlockPos.unpackLongY(packed) + 0.5 - arrival.y)
                        + facing.normal.z * (BlockPos.unpackLongZ(packed) + 0.5 - arrival.z);
                if (behind <= PortalSound.BEHIND_TOLERANCE && add(destination.getValue(), packed, portal)) {
                    throughFrames++;
                }
            }
        }
    }

    /** Ajoute un candidat, sauf s'il y est deja (un meme tube vu par deux cadres). */
    private static boolean add(Identifier dimension, long packed, @Nullable BackroomsPortalEntity portal) {
        if (candidates == CAPACITY || candidate(dimension, packed)) {
            return false;
        }
        CANDIDATE_DIMENSIONS[candidates] = dimension;
        CANDIDATE_POSITIONS[candidates] = packed;
        CANDIDATE_PORTALS[candidates++] = portal;
        return true;
    }

    private static void forgetCandidates() {
        for (int i = 0; i < candidates; i++) {
            CANDIDATE_DIMENSIONS[i] = null;
            CANDIDATE_PORTALS[i] = null;
        }
        candidates = 0;
    }

    /** Un tube du monde du joueur : son bourdonnement a sa place (son ancre, posee par le moteur de son, est sa place). */
    private static SoundInstance local(ClientWorld world, long packed) {
        AudioProfile.Ambience ambience = AudioProfiles.of(world.getRegistryKey()).ambience();
        return instance(ambience, packed, ambience.humVolume(), BlockPos.unpackLongX(packed) + 0.5, BlockPos.unpackLongY(packed) + 0.5,
                BlockPos.unpackLongZ(packed) + 0.5);
    }

    /**
     * Un tube de l'autre cote d'un portail : son bourdonnement ressort au point du cadre le plus pres de son image, avec le
     * volume qui lui reste apres le trajet jusqu'au cadre, ancre a sa vraie source.
     *
     * @return l'instance a jouer, ou {@code null} si ce cadre ne laisse pas passer le son
     */
    private static @Nullable SoundInstance throughFrame(BackroomsPortalEntity portal, long packed, Vec3d listener) {
        RegistryKey<World> destination = portal.destinationDim();
        AudioProfile.Ambience ambience = AudioProfiles.of(destination).ambience();
        float volume = ambience.humVolume();
        Vec3d source = new Vec3d(BlockPos.unpackLongX(packed) + 0.5, BlockPos.unpackLongY(packed) + 0.5, BlockPos.unpackLongZ(packed) + 0.5);
        Vec3d image = portal.fromDestination(source);
        Vec3d exit = PortalSound.exitPoint(portal, image, listener, PortalSound.radius(volume));
        if (exit == null) {
            return null;
        }
        float through = PortalSound.volumeThroughPortal(volume, Math.sqrt(exit.squaredDistanceTo(image)));
        if (through <= 0.0f) {
            return null;
        }
        SoundInstance instance = instance(ambience, packed, through, exit.x, exit.y, exit.z);
        SoundAnchors.anchor(instance, destination, source, volume, portal.toDestination(exit));
        return instance;
    }

    private static SoundInstance instance(AudioProfile.Ambience ambience, long packed, float volume, double x, double y, double z) {
        return new PositionedSoundInstance(ambience.hum(), SoundCategory.AMBIENT, volume, 1.0f, Random.create(packed), true, 0,
                SoundInstance.AttenuationType.LINEAR, x, y, z, false);
    }

    private static void stopAll(MinecraftClient client) {
        for (int i = 0; i < count; i++) {
            client.getSoundManager().stop(INSTANCES[i]);
            HUM_DIMENSIONS[i] = null;
            INSTANCES[i] = null;
        }
        count = 0;
    }

    private static boolean candidate(Identifier dimension, long packed) {
        for (int i = 0; i < candidates; i++) {
            if (CANDIDATE_POSITIONS[i] == packed && CANDIDATE_DIMENSIONS[i].equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean humming(Identifier dimension, long packed) {
        for (int i = 0; i < count; i++) {
            if (HUMMING[i] == packed && HUM_DIMENSIONS[i].equals(dimension)) {
                return true;
            }
        }
        return false;
    }
}
