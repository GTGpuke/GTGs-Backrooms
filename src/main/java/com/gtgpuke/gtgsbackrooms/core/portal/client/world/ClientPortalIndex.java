package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;

import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;

/**
 * Portails charges dans chaque monde client, principal ou distant : pendant du registre serveur.
 *
 * <p>Les parcours par image (visibilite, prediction de traversee, visee a travers le cadre) lisent cette liste
 * au lieu de toutes les entities du monde. Les mondes sont compares par identite : un monde distant et le
 * monde principal de la meme dimension ont chacun leur liste, et un monde abandonne est oublie par le registre.
 * La collision a travers les portails y trouve aussi les portails du client et le monde ou chacun mene.
 *
 * <p>Dans le monde du joueur, {@link #portals} ne rend que les portails qui existent pour lui : une sortie fugace qu'il ne
 * regarde pas n'y figure pas, ni dessinee, ni traversee, ni heurtee, ni visee. {@link #allPortals} les rend tous, pour
 * juger ce que le joueur regarde et pour la lumiere transmise, qui ne depend pas de lui.
 *
 * <p>Un portail que le client attend du serveur sans l'avoir encore peut y etre predit ({@link #predict}) : il compte
 * des lors comme les autres, puis cede la place au vrai des son arrivee, ou disparait seul s'il ne vient pas.
 */
public final class ClientPortalIndex {

    private static final Map<World, List<BackroomsPortalEntity>> PORTALS = new IdentityHashMap<>();
    private static final List<BackroomsPortalEntity> NONE = List.of();
    /** Portails du monde du joueur qui existent pour lui, tenus a jour a chaque tick et a chaque chargement. */
    private static final List<BackroomsPortalEntity> SHOWN = new ObjectArrayList<>();
    private static @Nullable World shownWorld;
    /** Portails predits encore en attente du vrai, avec le tick client ou ils cessent de l'attendre. */
    private static final List<Predicted> PREDICTED = new ArrayList<>();
    private static long ticks;
    /** Un portail est arrive dans le monde du joueur depuis le dernier tick : les sons en cours se replacent en fin de tick. */
    private static boolean soundsToReplace;

    /** Portail predit dans un monde, en attente du vrai jusqu'au tick donne. */
    private record Predicted(World world, BackroomsPortalEntity portal, long expiresAt) {
    }

    private ClientPortalIndex() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche les evenements de chargement d'entities client et la collision a travers les portails (point d'entree client). */
    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register(ClientPortalIndex::onEntityLoad);
        ClientEntityEvents.ENTITY_UNLOAD.register(ClientPortalIndex::onEntityUnload);
        // Le serveur integre tourne dans le meme processus : ses mondes gardent le registre serveur et le suivi du serveur.
        PortalCollision.setWorlds(new PortalCollision.Worlds() {
            @Override
            public List<BackroomsPortalEntity> portals(World world) {
                return world.isClient() ? ClientPortalIndex.portals(world) : PortalCollision.SERVER_WORLDS.portals(world);
            }

            @Override
            public @Nullable World destination(BackroomsPortalEntity portal) {
                return portal.getWorld().isClient()
                        ? RemoteWorldRegistry.worldOf(portal)
                        : PortalCollision.SERVER_WORLDS.destination(portal);
            }

            @Override
            public PortalCollision.Side side(World world) {
                return world.isClient() ? PortalCollision.Side.CLIENT : PortalCollision.Side.SERVER;
            }
        });
    }

    /**
     * Portails de ce monde qui existent pour le joueur : tous, sauf, dans son monde, les sorties fugaces qu'il ne regarde
     * pas ; liste vivante, a lire par index et a ne pas modifier.
     */
    public static List<BackroomsPortalEntity> portals(World world) {
        return world == shownWorld ? SHOWN : allPortals(world);
    }

    /** Tous les portails charges dans ce monde, sorties fugaces cachees comprises ; liste vivante, a ne pas modifier. */
    public static List<BackroomsPortalEntity> allPortals(World world) {
        List<BackroomsPortalEntity> portals = PORTALS.get(world);
        return portals == null ? NONE : portals;
    }

    /** Refait la liste des portails qui existent pour le joueur dans son monde, d'apres leur etat cache ou montre. */
    public static void refreshShown(World world) {
        shownWorld = world;
        SHOWN.clear();
        List<BackroomsPortalEntity> portals = allPortals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (!portal.hiddenFromPlayer()) {
                SHOWN.add(portal);
            }
        }
    }

    /**
     * Portail que le client attend du serveur sans l'avoir encore (le retour d'une sortie fugace que le serveur ne repose
     * qu'a l'annonce d'un passage deja fait chez le client) : il compte des maintenant, dessine comme les autres, et cede
     * la place au vrai des son arrivee (meme centre, meme orientation), ou disparait seul apres {@code lifetimeTicks}.
     */
    public static void predict(World world, BackroomsPortalEntity portal, int lifetimeTicks) {
        add(world, portal);
        PREDICTED.add(new Predicted(world, portal, ticks + lifetimeTicks));
    }

    /** Tick client : les portails predits que le serveur n'a pas confirmes a temps sont retires. */
    public static void tick() {
        ticks++;
        if (soundsToReplace) {
            soundsToReplace = false;
            SoundAnchors.refresh(MinecraftClient.getInstance());
        }
        for (int i = PREDICTED.size() - 1; i >= 0; i--) {
            Predicted predicted = PREDICTED.get(i);
            if (predicted.expiresAt() <= ticks) {
                remove(predicted.world(), predicted.portal());
                PREDICTED.remove(i);
            }
        }
    }

    /** Un monde est abandonne (monde distant libere, monde principal remplace par vanilla) : ses portails avec lui. */
    public static void forget(World world) {
        for (int i = PREDICTED.size() - 1; i >= 0; i--) {
            if (PREDICTED.get(i).world() == world) {
                PREDICTED.remove(i);
            }
        }
        PORTALS.remove(world);
        if (world == shownWorld) {
            shownWorld = null;
            SHOWN.clear();
        }
    }

    /** Deconnexion : plus aucun monde. */
    public static void clear() {
        soundsToReplace = false;
        PREDICTED.clear();
        PORTALS.clear();
        shownWorld = null;
        SHOWN.clear();
    }

    private static void onEntityLoad(Entity entity, ClientWorld world) {
        if (entity instanceof BackroomsPortalEntity portal) {
            add(world, portal);
            replacePredicted(world, portal);
        }
    }

    private static void onEntityUnload(Entity entity, ClientWorld world) {
        if (entity instanceof BackroomsPortalEntity portal) {
            remove(world, portal);
        }
    }

    private static void add(World world, BackroomsPortalEntity portal) {
        List<BackroomsPortalEntity> portals = PORTALS.computeIfAbsent(world, key -> new ObjectArrayList<>());
        if (!portals.contains(portal)) {
            portals.add(portal);
            if (world == shownWorld && !portal.hiddenFromPlayer()) {
                SHOWN.add(portal);
            }
            // Un portail de plus dans le monde du joueur : un son d'un autre monde, ou de loin dans celui-ci, a peut-etre un chemin
            // desormais (apres une bascule, les portails de l'arrivee peuvent arriver apres le joueur). Le replacement attend la fin du
            // tick : a son chargement, le portail n'a pas encore recu ses donnees (sa destination vaut l'origine du monde), et un
            // replacement immediat ramenait a sa place, a trois cents blocs, le son que le cadre de retour faisait encore entendre.
            if (world == MinecraftClient.getInstance().world) {
                soundsToReplace = true;
            }
        }
    }

    private static void remove(World world, BackroomsPortalEntity portal) {
        List<BackroomsPortalEntity> portals = PORTALS.get(world);
        if (portals != null) {
            portals.remove(portal);
        }
        if (world == shownWorld) {
            SHOWN.remove(portal);
        }
    }

    /** Le vrai portail arrive : celui predit a sa place (meme centre, meme orientation) s'efface. */
    private static void replacePredicted(World world, BackroomsPortalEntity portal) {
        for (int i = PREDICTED.size() - 1; i >= 0; i--) {
            Predicted predicted = PREDICTED.get(i);
            if (predicted.world() == world && predicted.portal().centeredAt(portal.getPos())
                    && predicted.portal().facingSource() == portal.facingSource()) {
                remove(world, predicted.portal());
                PREDICTED.remove(i);
            }
        }
    }
}
