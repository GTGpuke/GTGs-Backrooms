package com.gtgpuke.gtgsbackrooms.core.portal.client.sound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;

/**
 * Vraie source de chaque son positionne que le moteur de son joue : son monde, sa position et son volume d'origine. Un
 * son joue a ses coordonnees dans le monde du joueur ; apres une traversee, le joueur est dans un autre monde (ou a
 * saute ailleurs dans le sien) et les sons encore joues (un disque, une ambiance longue) sonneraient a des coordonnees
 * qui n'ont plus de sens pour lui : chacun est replace depuis sa vraie source, a sa place s'il est dans le monde du
 * joueur, au point du cadre par lequel le joueur voit le monde du son sinon ({@link PortalSound}), ou tu s'il n'y a plus
 * de chemin, sans l'arreter : revenu de l'autre cote, le joueur le retrouve la ou il en est. Les sons qui suivent une
 * entite (wagonnet, elytre) replacent eux-memes leur source a chaque tick et ne sont pas ancres.
 *
 * <p>Colle vers le moteur de son du client : les sources OpenAL sont deplacees en place, sans relance.
 */
public final class SoundAnchors {

    /**
     * Vraie source d'un son : son monde, sa position et son volume avant tout trajet par un cadre, et, pour un son
     * rejoue au cadre, le point de son monde par ou il entre dans le cadre (le point de sortie, vu de l'autre cote) :
     * le moteur audio y mesure le trajet de la source au cadre.
     *
     * @param world monde de la vraie source
     * @param source position de la source dans son monde
     * @param volume volume a la source
     * @param arrival point d'entree dans le cadre, dans le monde de la source ; {@code null} pour un son joue a sa place
     * @param played point ou le son joue maintenant, dans le monde du joueur : sa source au depart, le point du cadre
     *     pour un son rejoue au cadre, et celui ou il a ete replace apres une traversee (l'instance, elle, garde ses
     *     coordonnees de depart)
     */
    public record Anchor(RegistryKey<World> world, Vec3d source, float volume, @Nullable Vec3d arrival, Vec3d played) {
    }

    /** Les sons oublies par le moteur le sont ici aussi : cles faibles, identite des instances. */
    private static final Map<SoundInstance, Anchor> ANCHORS = new WeakHashMap<>();

    private SoundAnchors() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Un son rejoue au cadre par {@link FrameSoundPlayer} : sa vraie source est de l'autre cote.
     *
     * @param sound instance jouee au cadre
     * @param world monde de la source
     * @param source position de la source dans son monde
     * @param volume volume a la source
     * @param arrival point d'entree dans le cadre, dans le monde de la source
     */
    public static void anchor(SoundInstance sound, RegistryKey<World> world, Vec3d source, float volume, Vec3d arrival) {
        ANCHORS.put(sound, new Anchor(world, source, volume, arrival, new Vec3d(sound.getX(), sound.getY(), sound.getZ())));
    }

    /**
     * Tout son qui part au moteur : a defaut d'ancre deja connue, sa source est la ou il joue, dans le monde du joueur.
     * Les sons relatifs (interface, musique) n'ont pas de place dans un monde ; ceux qui suivent une entite se replacent
     * eux-memes. Une ancre par son joue : un evenement, comme l'instance que vanilla cree pour lui.
     */
    public static void onPlay(SoundInstance sound) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE || sound.isRelative() || sound instanceof TickableSoundInstance || ANCHORS.containsKey(sound)) {
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world != null) {
            Vec3d at = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
            ANCHORS.put(sound, new Anchor(world.getRegistryKey(), at, sound.getVolume(), null, at));
        }
    }

    /**
     * Apres une traversee, ou un portail de plus dans le monde du joueur : chaque son positionne encore joue est replace
     * depuis sa vraie source, pour le joueur la ou il est maintenant. Evenement rare : le parcours des sons et la tache
     * confiee a chaque source sont admis.
     *
     * @param client client du joueur, deja de l'autre cote
     */
    public static void refresh(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || ANCHORS.isEmpty()) {
            return;
        }
        SoundSystemAccessor system = (SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem();
        Vec3d listener = player.getEyePos();
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        for (Map.Entry<SoundInstance, Channel.SourceManager> playing : system.backrooms$getSources().entrySet()) {
            Anchor anchor = ANCHORS.get(playing.getKey());
            if (anchor == null) {
                continue;
            }
            Vec3d position = anchor.source();
            Vec3d arrival = null;
            float volume = 0.0f;
            if (anchor.world().equals(world.getRegistryKey())) {
                volume = anchor.volume();
            } else {
                PortalSound.Exit exit = PortalSound.bestExit(portals, FrameSoundPlayer::portalsOf, anchor.world(), anchor.source(), listener,
                        PortalSound.radius(anchor.volume()));
                if (exit != null) {
                    position = exit.point();
                    volume = PortalSound.volumeThroughPortal(anchor.volume(), exit.distance());
                    arrival = exit.arrival();
                }
            }
            if (!Objects.equals(arrival, anchor.arrival()) || !position.equals(anchor.played())) {
                // Le son entre par un autre cadre, ou plus par aucun, et joue ailleurs : son ancre le dit au moteur audio.
                ANCHORS.put(playing.getKey(), new Anchor(anchor.world(), anchor.source(), anchor.volume(), arrival, position));
            }
            float gain = system.backrooms$adjustedVolume(volume, playing.getKey().getCategory());
            Vec3d at = position;
            playing.getValue().run(source -> {
                source.setPosition(at);
                source.setVolume(gain);
            });
        }
    }

    /** Ancre d'un son, sa vraie source ; {@code null} pour un son sans place dans un monde ou qui suit une entite. */
    public static @Nullable Anchor anchorOf(SoundInstance sound) {
        return ANCHORS.get(sound);
    }

    /** Deconnexion : plus aucun son a replacer. */
    public static void clear() {
        ANCHORS.clear();
    }
}
