package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;

/**
 * Garde charges les chunks d'arrivee des portails a portee d'un joueur (portee de rendu, quelle que soit la direction
 * de son regard).
 *
 * <p>Un ticket de chunk par fenetre d'arrivee, partage entre les portails qui menent au meme endroit
 * et retire dix secondes apres que plus aucun joueur n'est a portee : un pas au-dela ne decharge
 * rien, et la fenetre survit a celle du client, dechargee avant elle. Le chargement reste asynchrone : la synchronisation
 * vers le client envoie chaque chunk des qu'il est pret.
 */
public final class DestinationLoader {

    /** Intervalle de reevaluation des portails a portee. */
    private static final int CHECK_INTERVAL_TICKS = 10;
    /** A cette distance d'un portail, la traversee est imminente : fenetre synchronisee plus large, arrivee chargee comme si le joueur y etait. */
    private static final double NEAR_DISTANCE_BLOCKS = 8.0;
    /** Chunks ajoutes au rayon de la fenetre synchronisee quand le joueur est pres du portail. */
    private static final int NEAR_EXTRA_RADIUS = 2;
    /** Ticks pendant lesquels une fenetre reste chargee apres que plus personne n'est a portee de son portail : plus que la fenetre client. */
    private static final int RELEASE_GRACE_TICKS = 200;
    private static final String PERF_DESTINATION_TICK = "portal.destination.tick";

    private static final ChunkTicketType<ChunkPos> TICKET_TYPE =
            ChunkTicketType.create("gtgsbackrooms_portal", Comparator.comparingLong(ChunkPos::toLong));
    /** Cote quitte par un joueur qui change de dimension, tenu autant qu'une fenetre dont on s'eloigne. */
    private static final ChunkTicketType<ChunkPos> DEPARTURE_TICKET_TYPE =
            ChunkTicketType.create("gtgsbackrooms_portal_departure", Comparator.comparingLong(ChunkPos::toLong), RELEASE_GRACE_TICKS);
    /** Fenetre tenue par chaque portail precharge. */
    private static final Map<UUID, DestinationWindow> ACTIVE = new HashMap<>();
    /** Nombre de portails par fenetre : le ticket est pose au premier et retire au dernier. */
    private static final Map<DestinationWindow, Integer> LEASES = new HashMap<>();
    /** Rayon du ticket pose pour chaque fenetre, a retirer tel quel. */
    private static final Map<DestinationWindow, Integer> TICKET_RADIUS = new HashMap<>();
    private static final Map<DestinationWindow, Integer> WANTED_RADIUS_SCRATCH = new HashMap<>();
    private static final List<UUID> RELEASE_SCRATCH = new ArrayList<>();
    /** Tick ou chaque portail actif a cesse d'avoir un joueur a portee ; absent tant qu'il en a un. */
    private static final Object2LongMap<UUID> UNWATCHED_SINCE = new Object2LongOpenHashMap<>();
    private static boolean initialized;

    /** Dimension et chunk central d'une fenetre d'arrivee. */
    private record DestinationWindow(RegistryKey<World> dimension, ChunkPos center) {
    }

    private DestinationLoader() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche le tick serveur ; idempotent. */
    public static void init() {
        if (initialized) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(DestinationLoader::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> releaseAll());
        initialized = true;
    }

    /**
     * Garde charge le cote qu'un joueur quitte par un portail, comme une arrivee imminente : sur la distance de vue du
     * serveur, et au moins sur la fenetre synchronisee elargie. Vanilla retire ses tickets
     * des qu'il change de dimension : sans relais, les chunks qu'il voyait se dechargeaient en quelques ticks, avant que la
     * fenetre du portail de retour ne les reprenne, puis se rechargeaient ; leurs entities revenaient sous de nouveaux
     * identifiants, en double chez le client qui tenait encore les anciennes copies. Un nouveau depart du meme chunk
     * repousse l'echeance.
     *
     * @param origin monde quitte
     * @param position position du joueur au depart
     */
    public static void holdDeparture(ServerWorld origin, Vec3d position) {
        ChunkPos chunk = new ChunkPos(ChunkSectionPos.getSectionCoord(position.x), ChunkSectionPos.getSectionCoord(position.z));
        origin.getChunkManager().addTicket(DEPARTURE_TICKET_TYPE, chunk, ticketRadius(origin.getServer(), true), chunk);
    }

    /** Rayon, en chunks, de la fenetre chargee autour de chaque arrivee. */
    private static int preloadRadius() {
        return Math.max(0, ModConfig.PORTAL_PRELOAD_RADIUS_CHUNKS);
    }

    /** Rayon, en chunks, de la fenetre synchronisee vers le client : plus large quand le joueur est pres du portail. */
    public static int syncRadius(boolean near) {
        return near ? preloadRadius() + NEAR_EXTRA_RADIUS : preloadRadius();
    }

    /** {@code true} si le joueur est assez pres du portail pour le traverser sous peu. */
    public static boolean isNear(ServerPlayerEntity player, BackroomsPortalEntity portal) {
        return player.squaredDistanceTo(portal) <= NEAR_DISTANCE_BLOCKS * NEAR_DISTANCE_BLOCKS;
    }

    /** Rayon du ticket : la fenetre synchronisee, ou toute la distance de vue du serveur si un joueur est pres (l'arrivee est alors prete avant la traversee). */
    private static int ticketRadius(MinecraftServer server, boolean near) {
        return near ? Math.max(syncRadius(true), server.getPlayerManager().getViewDistance()) : syncRadius(false);
    }

    @SuppressWarnings("try")
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_DESTINATION_TICK)) {
            List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
            for (int i = 0; i < portals.size(); i++) {
                BackroomsPortalEntity portal = portals.get(i);
                boolean watched = isWatched(portal);
                boolean active = ACTIVE.containsKey(portal.getUuid());
                if (watched) {
                    UNWATCHED_SINCE.removeLong(portal.getUuid());
                    if (!active) {
                        acquire(server, portal);
                    }
                } else if (active) {
                    long since = UNWATCHED_SINCE.getOrDefault(portal.getUuid(), -1L);
                    if (since < 0L) {
                        UNWATCHED_SINCE.put(portal.getUuid(), (long) server.getTicks());
                    } else if (server.getTicks() - since >= RELEASE_GRACE_TICKS) {
                        release(server, portal.getUuid());
                    }
                }
            }
            // Portails decharges ou supprimes depuis la derniere passe.
            RELEASE_SCRATCH.clear();
            for (UUID uuid : ACTIVE.keySet()) {
                if (!isLoaded(uuid, portals)) {
                    RELEASE_SCRATCH.add(uuid);
                }
            }
            for (int i = 0; i < RELEASE_SCRATCH.size(); i++) {
                release(server, RELEASE_SCRATCH.get(i));
            }
            resizeTickets(server, portals);
        }
    }

    /** Le ticket de chaque fenetre suit le joueur le plus proche de l'un de ses portails. */
    private static void resizeTickets(MinecraftServer server, List<BackroomsPortalEntity> portals) {
        WANTED_RADIUS_SCRATCH.clear();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            DestinationWindow window = ACTIVE.get(portal.getUuid());
            if (window != null) {
                WANTED_RADIUS_SCRATCH.merge(window, ticketRadius(server, isNearAnyPlayer(portal)), Math::max);
            }
        }
        for (Map.Entry<DestinationWindow, Integer> entry : WANTED_RADIUS_SCRATCH.entrySet()) {
            DestinationWindow window = entry.getKey();
            int wanted = entry.getValue();
            // Une fenetre active a toujours son ticket et son monde : acquire ne l'enregistre pas sinon.
            int current = TICKET_RADIUS.get(window);
            if (current == wanted) {
                continue;
            }
            ServerWorld destination = server.getWorld(window.dimension());
            destination.getChunkManager().addTicket(TICKET_TYPE, window.center(), wanted, window.center());
            destination.getChunkManager().removeTicket(TICKET_TYPE, window.center(), current, window.center());
            TICKET_RADIUS.put(window, wanted);
        }
    }

    private static boolean isNearAnyPlayer(BackroomsPortalEntity portal) {
        // Le registre ne tient que des portails serveur.
        ServerWorld world = (ServerWorld) portal.getWorld();
        List<ServerPlayerEntity> players = world.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            if (isNear(players.get(i), portal)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoaded(UUID uuid, List<BackroomsPortalEntity> portals) {
        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).getUuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWatched(BackroomsPortalEntity portal) {
        ServerWorld world = (ServerWorld) portal.getWorld();
        List<ServerPlayerEntity> players = world.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            if (PortalRelevance.isInRange(player, portal.getPos(), PortalRelevance.MAX_DISTANCE)) {
                return true;
            }
        }
        return false;
    }

    private static void acquire(MinecraftServer server, BackroomsPortalEntity portal) {
        ServerWorld destination = server.getWorld(portal.destinationDim());
        if (destination == null) {
            ModConstants.LOGGER.warn("[Portal] Prechargement impossible, dimension d'arrivee absente : {}.",
                    portal.destinationDim().getValue());
            return;
        }
        Vec3d destinationPos = portal.destinationPos();
        DestinationWindow window = new DestinationWindow(portal.destinationDim(), new ChunkPos(
                ChunkSectionPos.getSectionCoord(destinationPos.x),
                ChunkSectionPos.getSectionCoord(destinationPos.z)));
        ACTIVE.put(portal.getUuid(), window);
        if (LEASES.merge(window, 1, Integer::sum) == 1) {
            int radius = ticketRadius(server, isNearAnyPlayer(portal));
            destination.getChunkManager().addTicket(TICKET_TYPE, window.center(), radius, window.center());
            TICKET_RADIUS.put(window, radius);
            ModConstants.LOGGER.debug("[Portal] Fenetre d'arrivee chargee : {} autour de {}.", window.dimension().getValue(), window.center());
        }
    }

    /** Retire un portail actif ; le dernier portail d'une fenetre emporte son ticket. */
    private static void release(MinecraftServer server, UUID portalUuid) {
        UNWATCHED_SINCE.removeLong(portalUuid);
        DestinationWindow window = ACTIVE.remove(portalUuid);
        int remaining = LEASES.merge(window, -1, Integer::sum);
        if (remaining <= 0) {
            LEASES.remove(window);
            int radius = TICKET_RADIUS.remove(window);
            server.getWorld(window.dimension()).getChunkManager().removeTicket(TICKET_TYPE, window.center(), radius, window.center());
            ModConstants.LOGGER.debug("[Portal] Fenetre d'arrivee relachee : {} autour de {}.", window.dimension().getValue(), window.center());
        }
    }

    /** Arret du serveur : ses mondes disparaissent avec leurs tickets, on oublie tout. */
    private static void releaseAll() {
        UNWATCHED_SINCE.clear();
        ACTIVE.clear();
        LEASES.clear();
        TICKET_RADIUS.clear();
    }
}
