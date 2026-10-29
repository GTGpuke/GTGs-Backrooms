package com.gtgpuke.gtgsbackrooms.core.portal.client.sound;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;

/**
 * Son qui traverse un portail : un son d'un monde distant (ou du monde du joueur, loin de lui) est rejoue au point du
 * cadre le plus proche de sa source, vue par un portail du monde du joueur, avec le volume qui lui reste apres le
 * trajet jusqu'au cadre ({@link PortalSound}). Le joueur l'entend donc venir du cadre par lequel il voit l'autre
 * cote, d'autant plus faible que la source en est loin, que ce portail ait un retour ou non (aller simple). Un son d'un
 * monde distant qu'aucun portail ouvert ne rapproche du joueur n'est pas joue : a ses coordonnees brutes, il sonnerait
 * n'importe ou. Un son peut passer deux cadres, par un portail vu a travers un autre (le monde intermediaire tel que le
 * client le tient). Le son rejoue garde sa vraie source ({@link SoundAnchors}) : si le joueur traverse pendant qu'il
 * joue, il est replace de l'autre cote.
 */
public final class FrameSoundPlayer {

    /** Un son du monde du joueur plus proche que cela est joue tel quel par vanilla. */
    private static final double NEAR_PLAYER_SQUARED = PortalSound.MAX_RADIUS * PortalSound.MAX_RADIUS;
    /** Au-dela de dix blocs, vanilla retarde un son selon sa distance (vitesse du son). */
    private static final double DELAY_THRESHOLD_SQUARED = 100.0;
    private static final double DELAY_BLOCKS_PER_TICK = 2.0;

    private FrameSoundPlayer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Joue un son par le portail le plus proche de sa source, parmi ceux par lesquels le joueur voit le monde du son.
     *
     * @param soundWorld monde ou le son est emis
     * @param x position de la source, X
     * @param y position de la source, Y
     * @param z position de la source, Z
     * @param event son joue
     * @param category categorie du son
     * @param volume volume a la source
     * @param pitch hauteur du son
     * @param useDistance {@code true} pour le retard de propagation de vanilla (sons lointains)
     * @param seed graine des variantes du son
     * @return {@code true} si vanilla ne doit pas jouer le son : il est passe par un portail, ou il vient d'un monde
     *         distant sans chemin jusqu'au joueur
     */
    public static boolean redirect(
            ClientWorld soundWorld,
            double x,
            double y,
            double z,
            SoundEvent event,
            SoundCategory category,
            float volume,
            float pitch,
            boolean useDistance,
            long seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!ModConfig.ENABLE_PORTAL_FEATURE || player == null) {
            return false;
        }
        World listenerWorld = player.getWorld();
        boolean remote = soundWorld != listenerWorld;
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        if (!remote && listener.squaredDistanceTo(x, y, z) < NEAR_PLAYER_SQUARED) {
            return false;
        }
        Vec3d source = new Vec3d(x, y, z);
        // Les portails du monde du joueur vers celui du son : ceux par ou il voit l'autre cote, qu'ils aient un retour ou non.
        PortalSound.Exit best = PortalSound.bestExit(ClientPortalIndex.portals(listenerWorld), FrameSoundPlayer::portalsOf, soundWorld.getRegistryKey(),
                source, listener, PortalSound.radius(volume));
        if (best == null) {
            return remote;
        }
        float through = PortalSound.volumeThroughPortal(volume, best.distance());
        if (through <= 0.0f) {
            return true;
        }
        Vec3d bestExit = best.point();
        PositionedSoundInstance sound = new PositionedSoundInstance(event, category, through, pitch, Random.create(seed), bestExit.x, bestExit.y, bestExit.z);
        SoundAnchors.anchor(sound, soundWorld.getRegistryKey(), source, volume, best.arrival());
        double squared = listener.squaredDistanceTo(bestExit);
        if (useDistance && squared > DELAY_THRESHOLD_SQUARED) {
            client.getSoundManager().play(sound, (int) (Math.sqrt(squared) / DELAY_BLOCKS_PER_TICK));
        } else {
            client.getSoundManager().play(sound);
        }
        return true;
    }

    /**
     * Les portails d'un monde que le client tient : le sien, ou un monde distant d'une vue ; aucun sinon. Le monde
     * intermediaire d'un son qui passe deux cadres.
     */
    public static List<BackroomsPortalEntity> portalsOf(RegistryKey<World> key) {
        ClientWorld main = MinecraftClient.getInstance().world;
        World world = main != null && main.getRegistryKey().equals(key) ? main : RemoteWorldRegistry.worldOf(key);
        return world == null ? List.of() : ClientPortalIndex.portals(world);
    }
}
