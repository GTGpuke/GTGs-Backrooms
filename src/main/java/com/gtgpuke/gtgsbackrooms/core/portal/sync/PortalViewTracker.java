package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.SharedConstants;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.DestinationLoader;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalRelevance;
import com.gtgpuke.gtgsbackrooms.mixin.EntityTrackerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.EntityTrackerEntryAccessor;

/**
 * Envoie a chaque joueur ce qu'il voit a travers ses portails, et le tient a jour : chunks, blocs et entities.
 *
 * <p>Vanilla ne synchronise qu'une dimension par joueur : celle ou il se trouve. Pour montrer l'autre
 * cote d'un portail, le serveur choisit pour chaque dimension distante un foyer (le portail a portee le
 * plus proche, devant le joueur ou dans son dos), envoie la fenetre de chunks autour de son arrivee, relaie les
 * changements de blocs, branche le joueur sur le suivi des entities de la fenetre, et retire tout ce qui n'est
 * plus a portee. Tout vit sur le thread serveur ; les structures sont reutilisees d'un tick a l'autre pour ne rien
 * allouer quand personne n'a de portail a portee.
 */
public final class PortalViewTracker {

    /** Intervalle de recalcul des fenetres voulues. */
    static final int UPDATE_INTERVAL_TICKS = 5;
    /** Chunks complets envoyes au plus par joueur et par recalcul, pour lisser la bande passante. */
    static final int MAX_CHUNKS_PER_UPDATE = 8;
    /** Delai avant de decharger une dimension dont le joueur n'a plus aucun portail a portee : un pas au-dela ne doit pas tout renvoyer. */
    static final int UNLOAD_GRACE_TICKS = 100;
    /**
     * Ticks sans aucun portail a portee vers une dimension avant de demander au client d'en liberer le monde distant
     * (chunks, entites, renderer), jamais moins que le delai de grace qui decharge sa fenetre. Un retour apres ce delai
     * voit la vue se reconstruire, le temps de renvoyer la fenetre (decision de l'auteur du 25 octobre 2026,
     * {@code docs/DECISIONS.md}).
     */
    static final int RELEASE_DELAY_TICKS =
            Math.max(UNLOAD_GRACE_TICKS, ModConfig.PORTAL_REMOTE_WORLD_RELEASE_SECONDS * SharedConstants.TICKS_PER_SECOND);
    private static final String PERF_SYNC_TICK = "portal.sync.tick";
    private static final String PERF_SYNC_CHUNKS = "portal.sync.chunks";
    private static final String PERF_SYNC_ENTITIES = "portal.sync.entities";

    private static final Map<UUID, PlayerView> VIEWS = new HashMap<>();
    private static final List<PlayerView> VIEW_LIST = new ArrayList<>();
    private static final List<BackroomsPortalEntity> RELEVANT_SCRATCH = new ArrayList<>();
    /** Portails d'un monde distant vus a travers un portail du joueur, qui menent ailleurs (un niveau d'imbrication). */
    private static final List<BackroomsPortalEntity> NESTED_SCRATCH = new ArrayList<>();
    /** Foyer de chaque dimension pour le joueur en cours : adressage ouvert, aucun noeud alloue par recalcul. */
    private static final Map<RegistryKey<World>, BackroomsPortalEntity> ANCHOR_SCRATCH = new Reference2ObjectOpenHashMap<>();
    private static final Map<RegistryKey<World>, LongSet> WANTED_SCRATCH = new HashMap<>();
    /** Memes ensembles que {@link #WANTED_SCRATCH}, pour les vider a chaque recalcul sans iterateur. */
    private static final List<LongSet> WANTED_LIST = new ArrayList<>();
    private static final List<RegistryKey<World>> DIMENSION_SCRATCH = new ArrayList<>();
    /**
     * Duree, en ticks serveur, pendant laquelle les chunks qu'un client tient apres une traversee valent : vanilla propose
     * les chunks proches en quelques ticks ; un chunk qu'il ne propose qu'apres (hors de la distance de vue, propose bien
     * plus tard) a pu etre oublie par le client entre-temps, et doit partir comme les autres.
     */
    public static final int HELD_CHUNKS_TTL_TICKS = 100;
    /**
     * Chunks de sa nouvelle dimension que le client d'un joueur tient deja, le temps que vanilla les lui propose. Peu
     * d'entrees (les joueurs passes depuis moins de {@link #HELD_CHUNKS_TTL_TICKS} ticks) : une liste, parcourue sans
     * iterateur a chaque changement de bloc.
     */
    private static final List<HeldChunks> HELD_AFTER_CROSSING = new ArrayList<>();
    /**
     * Joueurs passes par un portail depuis le dernier tick : leur vue repart des le tick suivant, sans attendre le passage
     * regulier. Leur client differe d'une dizaine de ses ticks le retrait des copies que la bascule efface, le temps que le
     * serveur les renvoie : un serveur lent (moins de dix ticks par seconde) les renverrait trop tard, et le portail de
     * retour juste derriere le joueur clignoterait.
     */
    private static final List<UUID> CHANGED_WORLD = new ArrayList<>();
    /** Nombre total de chunks distants presents chez les clients ; zero = rien a faire sur les changements de blocs. */
    private static int watchedChunkCount;
    private static boolean initialized;

    /**
     * Etat de synchronisation d'un joueur. La vue est oubliee a la deconnexion et a chaque changement de monde ou
     * reapparition (nouvel objet joueur) : le joueur qu'elle garde est toujours celui qui est connecte.
     */
    private static final class PlayerView {
        final ServerPlayerEntity player;
        final Map<RegistryKey<World>, LongSet> sentChunks = new HashMap<>();
        final Map<RegistryKey<World>, LongSet> pendingBlockUpdates = new HashMap<>();
        final Map<RegistryKey<World>, RemoteEntityView> entities = new HashMap<>();
        final List<RegistryKey<World>> announcedDimensions = new ArrayList<>();
        /** Dimensions annoncees dont le client a ete prie de liberer le monde : a annoncer de nouveau avant tout envoi. */
        final Set<RegistryKey<World>> releasedDimensions = new HashSet<>();
        /** Dernier tick ou chaque dimension etait voulue (zero si jamais). */
        final Object2LongMap<RegistryKey<World>> lastWantedTick = new Object2LongOpenHashMap<>();
        boolean hasPendingBlockUpdates;

        PlayerView(ServerPlayerEntity player) {
            this.player = player;
        }
    }

    /** Chunks tenus par le client d'un joueur apres une traversee, leur dimension, et tick serveur jusqu'auquel ils valent. */
    private record HeldChunks(UUID player, RegistryKey<World> dimension, LongSet chunks, long expiresAt) {
    }

    private PortalViewTracker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche le tick serveur et les evenements de joueur ; idempotent. */
    public static void init() {
        if (initialized) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(PortalViewTracker::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(server, handler.player.getUuid(), false));
        // Le client repart d'un monde vide apres un changement de dimension vanilla ou un respawn : on renvoie tout, et il
        // ne tient plus rien d'une traversee recente. Une traversee par portail passe par onPlayerChangedWorld seul.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> onVanillaWorldChange(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onVanillaWorldChange(newPlayer));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> onEntityUnloaded(world, entity));
        // Les listes de travail tiennent des portails, donc leurs mondes : en solo, elles retiendraient la partie precedente.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VIEWS.clear();
            VIEW_LIST.clear();
            HELD_AFTER_CROSSING.clear();
            CHANGED_WORLD.clear();
            RELEVANT_SCRATCH.clear();
            ANCHOR_SCRATCH.clear();
            NESTED_SCRATCH.clear();
            watchedChunkCount = 0;
        });
        initialized = true;
    }

    /**
     * Signale un changement de bloc serveur ; appele par les mixins de {@code ServerChunkManager} (chunk actif) et de
     * {@code World} (chunk seulement charge, ou vanilla ne previent aucun ecouteur).
     * Sans joueur regardant un chunk distant ni passe a l'instant, le cout est deux tests.
     *
     * <p>Un chunk tenu par un client apres une traversee, et qui change, n'est plus a jour chez lui : vanilla, qui compte
     * le lui envoyer entier, ne lui envoie pas le changement tant que le chunk attend son tour ; il doit donc partir.
     *
     * @param world monde du bloc
     * @param pos position du bloc
     */
    public static void onBlockChanged(ServerWorld world, BlockPos pos) {
        if (watchedChunkCount == 0 && HELD_AFTER_CROSSING.isEmpty()) {
            return;
        }
        long chunkKey = ChunkPos.toLong(pos);
        RegistryKey<World> dimension = world.getRegistryKey();
        for (int i = 0; i < HELD_AFTER_CROSSING.size(); i++) {
            HeldChunks held = HELD_AFTER_CROSSING.get(i);
            if (held.dimension() == dimension) {
                held.chunks().remove(chunkKey);
            }
        }
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            PlayerView view = VIEW_LIST.get(i);
            LongSet sent = view.sentChunks.get(dimension);
            if (sent != null && sent.contains(chunkKey)) {
                view.pendingBlockUpdates.computeIfAbsent(dimension, key -> new LongOpenHashSet()).add(pos.asLong());
                view.hasPendingBlockUpdates = true;
            }
        }
    }

    /**
     * Le joueur vient de changer de monde (portail ou vanilla) : sa synchronisation repart de zero. Son client garde
     * ses mondes distants apres une traversee par portail : les entities qu'il y suivait lui sont retirees, celles de sa
     * nouvelle dimension comprises (copies distantes a effacer avant les apparitions vanilla, qui suivent).
     */
    public static void onPlayerChangedWorld(ServerPlayerEntity player) {
        forget(player.getServer(), player.getUuid(), true);
        CHANGED_WORLD.add(player.getUuid());
    }

    /** Changement de monde vanilla ou respawn : le client recree son monde, et ne tient plus aucun chunk. */
    private static void onVanillaWorldChange(ServerPlayerEntity player) {
        releaseHeldChunks(player.getUuid());
        onPlayerChangedWorld(player);
    }

    /**
     * Le joueur passe dans {@code destination} : les chunks de cette dimension que son client tient deja (a jour, deja
     * construits) ne lui seront pas renvoyes par vanilla, voir {@code MixinChunkDataSender}. A appeler avant
     * {@link #onPlayerChangedWorld}, qui oublie la vue.
     *
     * @return nombre de chunks de {@code destination} que le client tient deja
     */
    public static int keepHeldChunks(ServerPlayerEntity player, RegistryKey<World> destination) {
        releaseHeldChunks(player.getUuid());
        PlayerView view = VIEWS.get(player.getUuid());
        LongSet sent = view == null ? null : view.sentChunks.get(destination);
        if (sent == null) {
            return 0;
        }
        LongSet held = new LongOpenHashSet(sent);
        // Un changement pas encore relaye manque au client : son chunk part avec les autres.
        LongSet pending = view.pendingBlockUpdates.get(destination);
        if (pending != null) {
            for (LongIterator positions = pending.iterator(); positions.hasNext(); ) {
                long pos = positions.nextLong();
                held.remove(ChunkPos.toLong(BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4));
            }
        }
        HELD_AFTER_CROSSING.add(new HeldChunks(player.getUuid(), destination, held, player.getServer().getTicks() + HELD_CHUNKS_TTL_TICKS));
        return held.size();
    }

    /**
     * {@code true} si vanilla peut se dispenser d'envoyer ce chunk au joueur : son client le tient deja, depuis une
     * traversee recente. Consomme l'entree ; passe {@link #HELD_CHUNKS_TTL_TICKS}, plus rien n'est tenu.
     */
    public static boolean consumeHeldChunk(ServerPlayerEntity player, ChunkPos pos) {
        int index = heldIndex(player.getUuid());
        if (index < 0) {
            return false;
        }
        HeldChunks held = HELD_AFTER_CROSSING.get(index);
        if (!held.chunks().remove(pos.toLong())) {
            return false;
        }
        if (held.chunks().isEmpty()) {
            HELD_AFTER_CROSSING.remove(index);
        }
        return true;
    }

    private static int heldIndex(UUID player) {
        for (int i = 0; i < HELD_AFTER_CROSSING.size(); i++) {
            if (HELD_AFTER_CROSSING.get(i).player().equals(player)) {
                return i;
            }
        }
        return -1;
    }

    private static void releaseHeldChunks(UUID player) {
        int index = heldIndex(player);
        if (index >= 0) {
            HELD_AFTER_CROSSING.remove(index);
        }
    }

    /** Nombre de chunks distants actuellement presents chez tous les clients. */
    public static int watchedChunkCount() {
        return watchedChunkCount;
    }

    /**
     * Envoie un son, un evenement de monde, des particules ou des fissures d'une dimension aux joueurs qui la voient a travers un
     * portail, si le point tombe dans un chunk de leur fenetre ; appele par les mixins de {@code PlayerManager} et de
     * {@code ServerWorld}, apres l'envoi vanilla aux joueurs de la dimension. C'est le client qui decide par quel
     * portail le son lui parvient et ou s'affichent les particules.
     *
     * @return nombre de joueurs servis
     */
    public static int relayToViewers(RegistryKey<World> dimension, double x, double z, Packet<?> packet) {
        if (watchedChunkCount == 0 || !isRelayed(packet)) {
            return 0;
        }
        long chunk = ChunkPos.toLong(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
        int served = 0;
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = viewerOf(VIEW_LIST.get(i), dimension, chunk);
            if (entities != null) {
                entities.sendPacket(packet);
                served++;
            }
        }
        return served;
    }

    /**
     * Une entity vient d'arriver dans une dimension par un portail : les joueurs qui la voient a travers un portail
     * recoivent sa copie tout de suite, comme vanilla l'envoie aux joueurs de la dimension, et non au prochain
     * alignement de leur fenetre. Leur client recoit au meme tick la destruction de l'ancienne copie, de ce cote :
     * sans cela, celle-ci resterait figee dans le cadre jusqu'a cinq ticks avant que la nouvelle n'en sorte.
     *
     * @param destination monde ou l'entity est arrivee
     * @param entity entity arrivee
     */
    public static void onEntityArrived(ServerWorld destination, Entity entity) {
        if (watchedChunkCount == 0) {
            return;
        }
        RegistryKey<World> dimension = destination.getRegistryKey();
        long chunk = entity.getChunkPos().toLong();
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = viewerOf(VIEW_LIST.get(i), dimension, chunk);
            if (entities != null) {
                entities.track(destination, entity);
            }
        }
    }

    /**
     * Une entity quitte son monde (morte, retiree, chunk decharge, autre dimension). Vanilla envoie sa destruction aux
     * auditeurs de son suivi par leur connexion, sans enveloppe : le client l'appliquerait a son monde principal. Ceux qui
     * la voyaient par un portail recoivent la destruction de leur copie distante tout de suite, et non au prochain
     * alignement de leur fenetre. Fabric le signale juste avant que vanilla ne retire le suivi de l'entity : une vue ne
     * garde ainsi jamais d'identifiant sans suivi vanilla.
     *
     * @param world monde que l'entity quitte
     * @param entity entity retiree
     */
    static void onEntityUnloaded(ServerWorld world, Entity entity) {
        if (VIEW_LIST.isEmpty()) {
            return;
        }
        RegistryKey<World> dimension = world.getRegistryKey();
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = VIEW_LIST.get(i).entities.get(dimension);
            if (entities != null) {
                entities.forget(entity.getId());
            }
        }
    }

    /**
     * Un portail vient de deplacer une entity dans son propre monde : son prochain deplacement part aux clients en position
     * absolue, comme a la descente d'une monture. En dessous de huit blocs, vanilla enverrait un deplacement relatif, et les
     * clients la feraient glisser jusqu'a l'arrivee a travers ce qui les separe ; en position absolue, ils la font passer
     * par le portail. Une entity que vanilla ne suit pas (un marqueur) n'a rien a envoyer.
     *
     * @param world monde de l'entity
     * @param entity entity deplacee
     */
    public static void onEntityJumped(ServerWorld world, Entity entity) {
        if (RemoteEntityView.trackers(world).get(entity.getId()) instanceof EntityTrackerAccessor tracker) {
            ((EntityTrackerEntryAccessor) tracker.backrooms$getEntry()).backrooms$setHadVehicle(true);
        }
    }

    /** Vue d'entities d'un joueur pour cette dimension si le chunk est dans sa fenetre, sinon {@code null}. */
    private static @Nullable RemoteEntityView viewerOf(PlayerView view, RegistryKey<World> dimension, long chunk) {
        // Une vue d'entities n'existe que pour une dimension dont des chunks ont ete envoyes.
        RemoteEntityView entities = view.entities.get(dimension);
        return entities != null && view.sentChunks.get(dimension).contains(chunk) ? entities : null;
    }

    /** Paquets qu'un joueur recoit d'une dimension vue a travers un portail, en plus de ceux de ses entities. */
    static boolean isRelayed(Packet<?> packet) {
        return packet instanceof PlaySoundS2CPacket
                || packet instanceof PlaySoundFromEntityS2CPacket
                || packet instanceof WorldEventS2CPacket
                || packet instanceof ParticleS2CPacket
                || packet instanceof BlockBreakingProgressS2CPacket;
    }

    @SuppressWarnings("try")
    private static void tick(MinecraftServer server) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_SYNC_TICK)) {
            if (server.getTicks() % UPDATE_INTERVAL_TICKS == 0) {
                List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                for (int i = 0; i < players.size(); i++) {
                    update(server, players.get(i));
                }
            } else {
                for (int i = 0; i < CHANGED_WORLD.size(); i++) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(CHANGED_WORLD.get(i));
                    // Deconnecte depuis : plus rien a faire.
                    if (player != null) {
                        update(server, player);
                    }
                }
            }
            CHANGED_WORLD.clear();
            // Perimes, des chunks tenus ne valent plus rien : retires, ils ne coutent plus rien aux changements de blocs.
            for (int i = HELD_AFTER_CROSSING.size() - 1; i >= 0; i--) {
                if (server.getTicks() > HELD_AFTER_CROSSING.get(i).expiresAt()) {
                    HELD_AFTER_CROSSING.remove(i);
                }
            }
            flushBlockUpdates(server);
        }
    }

    private static void update(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        collectRelevantPortals(player, world);
        PlayerView view = VIEWS.get(player.getUuid());
        // Une vue existante a toujours au moins une dimension inscrite : elle reste a decharger meme sans portail regarde.
        if (RELEVANT_SCRATCH.isEmpty() && view == null) {
            return;
        }
        if (view == null) {
            view = new PlayerView(player);
            VIEWS.put(player.getUuid(), view);
            VIEW_LIST.add(view);
        }
        computeWantedWindows(player);
        addNestedWindows(player);
        long tick = server.getTicks();
        for (int i = 0; i < DIMENSION_SCRATCH.size(); i++) {
            view.lastWantedTick.put(DIMENSION_SCRATCH.get(i), tick);
        }
        for (Map.Entry<RegistryKey<World>, LongSet> entry : view.sentChunks.entrySet()) {
            // Toute dimension deja synchronisee a son ensemble voulu : les ensembles ne quittent jamais WANTED_SCRATCH.
            boolean unwanted = WANTED_SCRATCH.get(entry.getKey()).isEmpty();
            long unwantedFor = tick - view.lastWantedTick.getLong(entry.getKey());
            if (unwanted && unwantedFor > UNLOAD_GRACE_TICKS) {
                unloadAll(server, player, view, entry.getKey(), entry.getValue());
            }
            if (unwanted && unwantedFor > RELEASE_DELAY_TICKS) {
                release(server, player, view, entry.getKey(), entry.getValue());
            }
        }
        for (int i = 0; i < DIMENSION_SCRATCH.size(); i++) {
            RegistryKey<World> dimension = DIMENSION_SCRATCH.get(i);
            syncDimension(server, player, view, dimension, WANTED_SCRATCH.get(dimension));
        }
    }

    /** Portails du monde du joueur a sa portee de rendu, devant comme dans son dos, qui menent dans une autre dimension. */
    private static void collectRelevantPortals(ServerPlayerEntity player, ServerWorld world) {
        RELEVANT_SCRATCH.clear();
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.getWorld() != world || !portal.visible() || portal.destinationDim().equals(world.getRegistryKey())) {
                continue;
            }
            if (PortalRelevance.isInRange(player, portal.getPos(), PortalRelevance.MAX_DISTANCE)) {
                RELEVANT_SCRATCH.add(portal);
            }
        }
    }

    /** Par dimension distante : foyer = portail le plus proche ; fenetres de tous les portails qui tiennent autour. */
    private static void computeWantedWindows(ServerPlayerEntity player) {
        ANCHOR_SCRATCH.clear();
        DIMENSION_SCRATCH.clear();
        for (int i = 0; i < WANTED_LIST.size(); i++) {
            WANTED_LIST.get(i).clear();
        }
        for (int i = 0; i < RELEVANT_SCRATCH.size(); i++) {
            BackroomsPortalEntity portal = RELEVANT_SCRATCH.get(i);
            BackroomsPortalEntity anchor = ANCHOR_SCRATCH.get(portal.destinationDim());
            if (anchor == null || player.squaredDistanceTo(portal) < player.squaredDistanceTo(anchor)) {
                ANCHOR_SCRATCH.put(portal.destinationDim(), portal);
            }
        }
        for (int i = 0; i < RELEVANT_SCRATCH.size(); i++) {
            BackroomsPortalEntity portal = RELEVANT_SCRATCH.get(i);
            // Pres du portail, la traversee est imminente : fenetre plus large pour que l'arrivee soit deja construite.
            boolean near = DestinationLoader.isNear(player, portal);
            int radius = DestinationLoader.syncRadius(near);
            RegistryKey<World> dimension = portal.destinationDim();
            Vec3d anchorPos = ANCHOR_SCRATCH.get(dimension).destinationPos();
            Vec3d destinationPos = portal.destinationPos();
            int anchorX = ChunkSectionPos.getSectionCoord(anchorPos.x);
            int anchorZ = ChunkSectionPos.getSectionCoord(anchorPos.z);
            int chunkX = ChunkSectionPos.getSectionCoord(destinationPos.x);
            int chunkZ = ChunkSectionPos.getSectionCoord(destinationPos.z);
            if (!RemoteChunkWindow.fitsAround(anchorX, anchorZ, chunkX, chunkZ, radius)) {
                continue;
            }
            // Ensemble ordonne : les chunks partent du centre vers l'exterieur, et l'appartenance se teste sans parcours.
            LongSet wanted = wantedFor(dimension);
            boolean first = wanted.isEmpty();
            if (near) {
                RemoteChunkWindow.collect(chunkX, chunkZ, radius, wanted);
            } else if (!collectFar(player, portal, radius, Math.max(Math.abs(chunkX - anchorX), Math.abs(chunkZ - anchorZ)), wanted)) {
                continue;
            }
            if (first) {
                DIMENSION_SCRATCH.add(dimension);
            }
        }
    }

    /** Les chunks voulus d'une dimension pour le joueur en cours, ensemble ordonne garde d'un passage a l'autre. */
    private static LongSet wantedFor(RegistryKey<World> dimension) {
        LongSet wanted = WANTED_SCRATCH.get(dimension);
        if (wanted == null) {
            // Premiere fois qu'une dimension est voulue dans la partie : un ensemble de plus, garde ensuite.
            wanted = new LongLinkedOpenHashSet();
            WANTED_SCRATCH.put(dimension, wanted);
            WANTED_LIST.add(wanted);
        }
        return wanted;
    }

    /**
     * Un portail vu a travers un autre : dans la fenetre synchronisee d'un monde distant, le client le tient et le dessine
     * en vue imbriquee. S'il mene vers un troisieme monde, ou ailleurs dans le meme, la fenetre carree de son arrivee
     * rejoint les fenetres voulues : sans elle, sa vue n'aurait rien a montrer. Le foyer d'une dimension qu'aucun portail
     * du joueur n'atteint est le premier de ces portails ; une arrivee qui ne tient pas autour du foyer de sa dimension est
     * laissee, comme pour un portail du joueur.
     */
    private static void addNestedWindows(ServerPlayerEntity player) {
        RegistryKey<World> own = player.getWorld().getRegistryKey();
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        NESTED_SCRATCH.clear();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (seenThroughAnother(portal, own, WANTED_SCRATCH.get(portal.getWorld().getRegistryKey()))) {
                NESTED_SCRATCH.add(portal);
            }
        }
        int radius = DestinationLoader.syncRadius(false);
        for (int i = 0; i < NESTED_SCRATCH.size(); i++) {
            BackroomsPortalEntity portal = NESTED_SCRATCH.get(i);
            RegistryKey<World> dimension = portal.destinationDim();
            BackroomsPortalEntity anchor = ANCHOR_SCRATCH.get(dimension);
            if (anchor == null) {
                anchor = portal;
                ANCHOR_SCRATCH.put(dimension, portal);
            }
            Vec3d anchorPos = anchor.destinationPos();
            Vec3d destinationPos = portal.destinationPos();
            int chunkX = ChunkSectionPos.getSectionCoord(destinationPos.x);
            int chunkZ = ChunkSectionPos.getSectionCoord(destinationPos.z);
            if (!RemoteChunkWindow.fitsAround(ChunkSectionPos.getSectionCoord(anchorPos.x), ChunkSectionPos.getSectionCoord(anchorPos.z), chunkX, chunkZ,
                    radius)) {
                continue;
            }
            LongSet wanted = wantedFor(dimension);
            boolean first = wanted.isEmpty();
            RemoteChunkWindow.collect(chunkX, chunkZ, radius, wanted);
            if (first) {
                DIMENSION_SCRATCH.add(dimension);
            }
        }
    }

    /**
     * {@code true} si ce portail est vu a travers un portail du joueur et mene ailleurs que dans le monde du joueur (que le
     * client tient deja) : visible, dans la fenetre synchronisee de son propre monde.
     *
     * @param portal portail d'un monde quelconque
     * @param playerWorld monde du joueur
     * @param window chunks voulus du monde du portail pour ce joueur, ou {@code null} s'il n'en a aucun
     */
    static boolean seenThroughAnother(BackroomsPortalEntity portal, RegistryKey<World> playerWorld, @Nullable LongSet window) {
        return window != null && portal.visible() && !portal.destinationDim().equals(playerWorld)
                && window.contains(ChunkPos.toLong(ChunkSectionPos.getSectionCoord(portal.getX()), ChunkSectionPos.getSectionCoord(portal.getZ())));
    }

    /**
     * Fenetre d'un portail regarde de loin, ajoutee a {@code wanted} : le meme nombre de chunks que la fenetre carree, dans
     * l'axe de ce que montre un cadre d'arrivee debout ({@link RemoteChunkWindow#collectCone}), la fenetre carree pour une
     * arrivee couchee. Joueur a cote du cadre ou derriere : la vue ne se voit pas d'ou il est, aucune fenetre
     * ({@code false}). La portee du cone tient dans la carte du client avec la fenetre du foyer, et dans la distance de vue
     * du joueur, sans jamais descendre sous le rayon de la fenetre carree.
     *
     * @param anchorDistance ecart, en chunks, entre l'arrivee de ce portail et celle du foyer de sa dimension
     */
    private static boolean collectFar(ServerPlayerEntity player, BackroomsPortalEntity portal, int radius, int anchorDistance, LongSet wanted) {
        // La camera de la vue, image de l'oeil du joueur, est en deca du plan d'arrivee de la distance de l'oeil au portail.
        double eyeY = player.getEyeY();
        if (portal.signedDistance(player.getX(), eyeY, player.getZ()) < RemoteChunkWindow.MIN_VIEW_DEPTH) {
            return false;
        }
        Vec3d center = portal.destinationPos();
        PortalFacing arrival = portal.facingDestination();
        if (!arrival.direction.getAxis().isHorizontal()) {
            RemoteChunkWindow.collect(ChunkSectionPos.getSectionCoord(center.x), ChunkSectionPos.getSectionCoord(center.z), radius, wanted);
            return true;
        }
        int maxReach = Math.min((RemoteChunkWindow.MAP_RADIUS - anchorDistance) / 2, Math.max(radius, player.getViewDistance()));
        // Camera de la vue, comme le client place la sienne (PortalFacing.transformPoint), sans allocation.
        PortalFacing source = portal.facingSource();
        double apexX = PortalFacing.transformCoordinate(source, portal.getPos(), arrival, center, player.getX(), eyeY, player.getZ(), 0);
        double apexZ = PortalFacing.transformCoordinate(source, portal.getPos(), arrival, center, player.getX(), eyeY, player.getZ(), 2);
        RemoteChunkWindow.collectCone(apexX, apexZ, center.x, center.z, arrival.axisW.x, arrival.axisW.z, arrival.normal.x, arrival.normal.z,
                portal.width() / 2.0, radius, maxReach, wanted);
        return true;
    }

    @SuppressWarnings("try")
    private static void syncDimension(
            MinecraftServer server,
            ServerPlayerEntity player,
            PlayerView view,
            RegistryKey<World> dimension,
            LongSet wanted) {
        LongSet sent = view.sentChunks.computeIfAbsent(dimension, key -> new LongOpenHashSet());
        // Dechargements d'abord : si le foyer a saute, le client recentre sa carte sur le premier chunk suivant. Un chunk
        // envoye a eu son annonce : l'index de la dimension existe.
        int index = view.announcedDimensions.indexOf(dimension);
        LongIterator iterator = sent.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            if (!wanted.contains(key)) {
                iterator.remove();
                watchedChunkCount--;
                PortalNetwork.send(player, new RemoteChunkUnloadPayload(index, new ChunkPos(key)));
            }
        }
        ServerWorld destination = server.getWorld(dimension);
        if (destination == null) {
            return;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_SYNC_CHUNKS)) {
            int budget = MAX_CHUNKS_PER_UPDATE;
            LongIterator wantedKeys = wanted.iterator();
            while (wantedKeys.hasNext()) {
                long key = wantedKeys.nextLong();
                // Chaque chunk voulu reste charge tant qu'il l'est : une fenetre en cone deborde du ticket de l'arrivee.
                DestinationLoader.holdWindowChunk(destination, key);
                if (budget == 0 || sent.contains(key)) {
                    continue;
                }
                WorldChunk chunk = destination.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
                if (chunk == null) {
                    // Pas encore charge : le ticket de DestinationLoader s'en occupe, on reessaie au prochain passage.
                    continue;
                }
                index = announceDimension(player, view, destination);
                PortalNetwork.send(player, RemoteChunkPayload.of(destination, chunk));
                sent.add(key);
                watchedChunkCount++;
                budget--;
            }
        }
        if (!sent.isEmpty()) {
            try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_SYNC_ENTITIES)) {
                RemoteEntityView entities = view.entities.get(dimension);
                if (entities == null) {
                    entities = new RemoteEntityView(player, dimension, index);
                    view.entities.put(dimension, entities);
                }
                entities.update(destination, sent);
                entities.syncWeather(destination);
            }
        }
    }

    /**
     * Annonce la dimension au joueur si ce n'est pas deja fait, et rend son index chez lui : l'ordre des annonces de la vue.
     * Les payloads frequents (entities, blocs, dechargements) portent cet index, un octet, a la place de l'identifiant ;
     * le client le resout par la derniere annonce recue pour cet index, et l'ordre des paquets d'une connexion garantit
     * qu'une annonce precede tout ce qui s'en reclame. Une dimension dont le monde a ete libere ({@link #release}) est
     * annoncee de nouveau, sous le meme index, avant tout nouvel envoi.
     */
    private static int announceDimension(ServerPlayerEntity player, PlayerView view, ServerWorld destination) {
        RegistryKey<World> dimension = destination.getRegistryKey();
        int index = view.announcedDimensions.indexOf(dimension);
        if (index >= 0 && !view.releasedDimensions.remove(dimension)) {
            return index;
        }
        if (index < 0) {
            index = view.announcedDimensions.size();
            view.announcedDimensions.add(dimension);
        }
        PortalNetwork.send(player, RemoteDimensionPayload.of(destination, index));
        return index;
    }

    /**
     * Demande au client de liberer le monde distant d'une dimension que plus aucun portail a portee ne montre depuis
     * {@link #RELEASE_DELAY_TICKS} : sa fenetre, dechargee depuis le delai de grace, ne lui laissait que son monde, ses
     * entites et son renderer. Une fois par liberation ; la dimension garde son index et sera annoncee de nouveau avant
     * tout nouvel envoi.
     */
    private static void release(MinecraftServer server, ServerPlayerEntity player, PlayerView view, RegistryKey<World> dimension, LongSet sent) {
        if (!view.releasedDimensions.add(dimension)) {
            return;
        }
        unloadAll(server, player, view, dimension, sent);
        PortalNetwork.send(player, new RemoteDimensionReleasePayload(view.announcedDimensions.indexOf(dimension)));
    }

    private static void unloadAll(MinecraftServer server, ServerPlayerEntity player, PlayerView view, RegistryKey<World> dimension, LongSet sent) {
        RemoteEntityView entities = view.entities.remove(dimension);
        if (entities != null) {
            entities.clear(server.getWorld(dimension), true);
        }
        int index = view.announcedDimensions.indexOf(dimension);
        LongIterator iterator = sent.iterator();
        while (iterator.hasNext()) {
            PortalNetwork.send(player, new RemoteChunkUnloadPayload(index, new ChunkPos(iterator.nextLong())));
        }
        watchedChunkCount -= sent.size();
        sent.clear();
    }

    private static void flushBlockUpdates(MinecraftServer server) {
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            PlayerView view = VIEW_LIST.get(i);
            if (!view.hasPendingBlockUpdates) {
                continue;
            }
            view.hasPendingBlockUpdates = false;
            for (Map.Entry<RegistryKey<World>, LongSet> entry : view.pendingBlockUpdates.entrySet()) {
                LongSet pending = entry.getValue();
                // La dimension existe tant que le serveur tourne, et un chunk envoye a eu son annonce.
                ServerWorld world = server.getWorld(entry.getKey());
                int index = view.announcedDimensions.indexOf(entry.getKey());
                LongIterator iterator = pending.iterator();
                while (iterator.hasNext()) {
                    BlockPos pos = BlockPos.fromLong(iterator.nextLong());
                    PortalNetwork.send(view.player, new RemoteBlockUpdatePayload(index, pos, world.getBlockState(pos)));
                    // Comme vanilla apres un changement : les donnees de la block entity suivent l'etat (texte d'un
                    // panneau, motifs d'une banniere, tete d'un joueur), sinon la vue la montrerait vide.
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    Packet<ClientPlayPacketListener> update = blockEntity == null ? null : blockEntity.toUpdatePacket();
                    if (update != null) {
                        PortalNetwork.send(view.player, new RemoteEntityPacketPayload(index, update));
                    }
                }
                pending.clear();
            }
        }
    }

    private static void forget(MinecraftServer server, UUID playerUuid, boolean notifyClient) {
        if (!notifyClient) {
            releaseHeldChunks(playerUuid);
        }
        PlayerView view = VIEWS.remove(playerUuid);
        if (view == null) {
            return;
        }
        VIEW_LIST.remove(view);
        for (LongSet sent : view.sentChunks.values()) {
            watchedChunkCount -= sent.size();
        }
        for (RemoteEntityView entities : view.entities.values()) {
            entities.clear(server.getWorld(entities.dimension()), notifyClient);
        }
    }
}
