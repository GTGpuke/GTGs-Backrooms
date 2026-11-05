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
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
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
import com.gtgpuke.gtgsbackrooms.mixin.ServerChunkLoadingManagerAccessor;

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
    /** Chunks complets envoyes au plus par joueur, par dimension vue et par recalcul, pour lisser la bande passante. */
    static final int MAX_CHUNKS_PER_UPDATE = 8;
    /**
     * Chunks envoyes au plus par joueur, par dimension vue et par recalcul pres d'un cadre, ou le recalcul a lieu a chaque
     * tick tant que la fenetre de l'arrivee n'est pas complete : la distance de vue et son anneau (361 chunks a 8 de vue)
     * partent pendant les quelques secondes qu'il met a atteindre le plan en marchant, meme si le serveur tique moins
     * vite. Vanilla en envoie jusqu'a 64 par tick a un client qui suit.
     */
    static final int NEAR_CHUNKS_PER_TICK = 24;
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
    /** Distance a laquelle vanilla envoie particules et fissures d'un bloc aux joueurs de leur dimension, en blocs. */
    public static final double NEAR_RANGE = 32.0;
    /** Distance a laquelle vanilla envoie les particules de longue distance, en blocs. */
    public static final double LONG_PARTICLE_RANGE = 512.0;
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
    /**
     * Chunks que le client d'un joueur garde du monde qu'il vient de quitter par un portail, en attente de sa vue suivante.
     * Une liste parcourue par index : chaque changement de bloc la parcourt tant qu'elle n'est pas vide.
     */
    private static final List<Departure> DEPARTURES = new ArrayList<>();
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
        /** Chunks du monde quitte que le client garde, tant qu'un portail montre encore ce monde ; {@code null} sinon. */
        @Nullable Departure departure;
        /**
         * Pres du cadre d'un portail, des chunks voulus attendaient encore leur envoi a la derniere mise a jour : la vue se
         * met a jour a chaque tick jusqu'a les avoir tous envoyes, puis revient a un passage tous les {@link #UPDATE_INTERVAL_TICKS}.
         */
        boolean catchingUp;
        /** Tick serveur de la derniere mise a jour de cette vue. */
        long updatedAt = -1L;

        PlayerView(ServerPlayerEntity player) {
            this.player = player;
        }
    }

    /** Chunks tenus par le client d'un joueur apres une traversee, leur dimension, et tick serveur jusqu'auquel ils valent. */
    private record HeldChunks(UUID player, RegistryKey<World> dimension, LongSet chunks, long expiresAt) {
    }

    /** Chunks du monde quitte par un portail que le client d'un joueur garde : ce monde et leurs positions. */
    private record Departure(UUID player, ServerWorld world, LongSet chunks) {
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
            DEPARTURES.clear();
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
        if (!DEPARTURES.isEmpty()) {
            forgetChangedDeparture(world, ChunkPos.toLong(pos));
        }
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
        RegistryKey<World> elsewhere = RemoteKeys.elsewhere(dimension);
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            PlayerView view = VIEW_LIST.get(i);
            pendBlockUpdate(view, dimension, chunkKey, pos);
            pendBlockUpdate(view, elsewhere, chunkKey, pos);
        }
    }

    /** Un bloc change dans un chunk que la fenetre de cette clef a envoye : il part au prochain envoi. */
    private static void pendBlockUpdate(PlayerView view, RegistryKey<World> key, long chunkKey, BlockPos pos) {
        LongSet sent = view.sentChunks.get(key);
        if (sent != null && sent.contains(chunkKey)) {
            view.pendingBlockUpdates.computeIfAbsent(key, ignored -> new LongOpenHashSet()).add(pos.asLong());
            view.hasPendingBlockUpdates = true;
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
        removeDeparture(player.getUuid());
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
     * Le joueur quitte {@code origin} par un portail : son client garde le monde quitte, avec tous les chunks que vanilla
     * lui en avait envoyes. Sa vue suivante les tient pour envoyes : leurs changements lui sont relayes, ils ne repartent
     * pas s'il revient ({@link #keepHeldChunks}), et ne se dechargent chez lui qu'une fois que plus aucun portail ne
     * montre ce monde. Sans cela, vanilla lui renverrait au retour chaque chunk qu'il tient deja, et chacun se
     * reconstruirait sous ses yeux.
     *
     * <p>Vanilla n'envoie qu'un chunk post-traite, et un chunk qui attend son tour n'est pas encore chez le client : ni
     * l'un ni l'autre n'est garde. Les changements de blocs et de lumiere de ce tick ne partent qu'avec le passage des
     * chunks, en fin de tick, quand le joueur ne suivra plus ce monde : ils partent ici. A appeler avant d'annoncer la
     * bascule au client (ce qui la precede s'applique encore au monde quitte) et avant que vanilla ne retire le joueur de
     * {@code origin}. Evenement rare : les positions s'allouent.
     */
    public static void recordDeparture(ServerPlayerEntity player, ServerWorld origin) {
        LongSet chunks = new LongOpenHashSet();
        ServerChunkLoadingManager manager = origin.getChunkManager().chunkLoadingManager;
        ChunkDataSender sender = player.networkHandler.chunkDataSender;
        player.getChunkFilter().forEach(pos -> {
            long key = pos.toLong();
            WorldChunk chunk = manager.getPostProcessedChunk(key);
            if (chunk != null && !sender.isInNextBatch(key)) {
                ((ServerChunkLoadingManagerAccessor) manager).backrooms$getChunkHolder(key).flushUpdates(chunk);
                chunks.add(key);
            }
        });
        removeDeparture(player.getUuid());
        DEPARTURES.add(new Departure(player.getUuid(), origin, chunks));
    }

    /** Depart en attente de ce joueur, retire de la liste, ou {@code null} s'il n'en a pas. */
    private static @Nullable Departure removeDeparture(UUID player) {
        int index = departureIndex(player);
        return index >= 0 ? DEPARTURES.remove(index) : null;
    }

    private static int departureIndex(UUID player) {
        for (int i = 0; i < DEPARTURES.size(); i++) {
            if (DEPARTURES.get(i).player().equals(player)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Inscrit dans la vue le depart en attente (dimension annoncee, chunks tenus pour envoyes). Tant qu'un portail montre
     * encore le monde quitte, ses chunks restent voulus, ceux que la carte du client tient autour du foyer de ce monde (il
     * oublie les autres en la recentrant) ; sinon le depart est oublie, et ils se dechargent avec la dimension, apres le
     * delai de grace.
     */
    private static void keepDeparture(MinecraftServer server, ServerPlayerEntity player, PlayerView view) {
        Departure pending = removeDeparture(player.getUuid());
        if (pending != null) {
            RegistryKey<World> dimension = pending.world().getRegistryKey();
            announceDimension(player, view, pending.world().getRegistryKey(), pending.world());
            LongSet sent = view.sentChunks.computeIfAbsent(dimension, key -> new LongOpenHashSet());
            int before = sent.size();
            sent.addAll(pending.chunks());
            watchedChunkCount += sent.size() - before;
            // Le delai de grace part d'ici : sans portail vers ce monde, ses chunks se dechargeront ensuite chez le client.
            wantedFor(dimension);
            view.lastWantedTick.put(dimension, server.getTicks());
            view.departure = pending;
        }
        Departure departure = view.departure;
        if (departure != null) {
            RegistryKey<World> dimension = departure.world().getRegistryKey();
            LongSet wanted = wantedFor(dimension);
            if (wanted.isEmpty()) {
                view.departure = null;
                return;
            }
            // Une fenetre voulue a son foyer.
            Vec3d anchor = ANCHOR_SCRATCH.get(dimension).destinationPos();
            int anchorX = ChunkSectionPos.getSectionCoord(anchor.x);
            int anchorZ = ChunkSectionPos.getSectionCoord(anchor.z);
            LongIterator kept = departure.chunks().iterator();
            while (kept.hasNext()) {
                long key = kept.nextLong();
                if (RemoteChunkWindow.fitsAround(anchorX, anchorZ, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key), 0)) {
                    wanted.add(key);
                }
            }
        }
    }

    /**
     * Un bloc change avant la vue suivante d'un joueur qui vient de passer : dans le monde qu'il a quitte, ce chunk n'est
     * plus a jour chez lui. Il n'est plus tenu pour envoye ; la synchronisation l'enverra s'il le faut.
     */
    private static void forgetChangedDeparture(ServerWorld world, long chunk) {
        for (int i = 0; i < DEPARTURES.size(); i++) {
            Departure departure = DEPARTURES.get(i);
            if (departure.world() == world) {
                departure.chunks().remove(chunk);
            }
        }
    }

    /**
     * {@code true} si vanilla peut se dispenser d'envoyer ce chunk au joueur : son client le tient deja, depuis une
     * traversee recente. Vanilla propose un meme chunk deux fois, a l'arrivee du joueur puis quand son ticket le rend actif
     * (un monde deja visite, dont les chunks sont restes charges) : l'entree vaut pour chaque proposition, jusqu'a
     * {@link #HELD_CHUNKS_TTL_TICKS}, tant que le chunk ne change pas et que vanilla ne le retire pas au client.
     */
    public static boolean holdsChunk(ServerPlayerEntity player, ChunkPos pos) {
        int index = heldIndex(player.getUuid());
        return index >= 0 && HELD_AFTER_CROSSING.get(index).chunks().contains(pos.toLong());
    }

    /** Vanilla retire ce chunk au client du joueur, qui l'oublie : s'il le lui repropose, il doit partir. */
    public static void releaseHeldChunk(ServerPlayerEntity player, ChunkPos pos) {
        int index = heldIndex(player.getUuid());
        if (index >= 0) {
            HELD_AFTER_CROSSING.get(index).chunks().remove(pos.toLong());
        }
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
     * portail le son lui parvient et ou s'affichent les particules. La fenetre de l'ailleurs d'un joueur est dans son propre
     * monde : un joueur que vanilla a deja servi (a portee du point, sauf celui qu'il exclut), ou l'exclu quand l'envoi a l'auteur
     * d'une action a travers un cadre l'a servi, ne le recoit pas une seconde fois par elle ; sinon le son d'un bloc casse a
     * travers un cadre lointain du meme monde sortait deux fois.
     *
     * @param dimension dimension de l'envoi
     * @param x point
     * @param y point
     * @param z point
     * @param vanillaRange distance du point jusqu'a laquelle vanilla sert les joueurs de la dimension
     * @param excludedId identifiant de l'entity que vanilla exclut de l'envoi, -1 si aucune
     * @param excludedServed {@code true} si l'exclu a recu l'envoi autrement (l'auteur d'une action a travers un cadre)
     * @param packet son, evenement de monde, particules ou fissures
     * @return nombre de joueurs servis
     */
    public static int relayToViewers(RegistryKey<World> dimension, double x, double y, double z, double vanillaRange, int excludedId,
            boolean excludedServed, Packet<?> packet) {
        if (watchedChunkCount == 0 || !isRelayed(packet)) {
            return 0;
        }
        long chunk = ChunkPos.toLong(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
        RegistryKey<World> elsewhere = RemoteKeys.elsewhere(dimension);
        int served = 0;
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = viewerOf(VIEW_LIST.get(i), dimension, chunk);
            if (entities != null) {
                entities.sendPacket(packet);
                served++;
            }
            RemoteEntityView far = viewerOf(VIEW_LIST.get(i), elsewhere, chunk);
            if (far != null && !alreadyServed(VIEW_LIST.get(i).player, x, y, z, vanillaRange, excludedId, excludedServed)) {
                far.sendPacket(packet);
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
        RegistryKey<World> elsewhere = RemoteKeys.elsewhere(dimension);
        long chunk = entity.getChunkPos().toLong();
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = viewerOf(VIEW_LIST.get(i), dimension, chunk);
            if (entities != null) {
                entities.track(destination, entity);
            }
            RemoteEntityView far = viewerOf(VIEW_LIST.get(i), elsewhere, chunk);
            if (far != null) {
                far.track(destination, entity);
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
        RegistryKey<World> elsewhere = RemoteKeys.elsewhere(dimension);
        for (int i = 0; i < VIEW_LIST.size(); i++) {
            RemoteEntityView entities = VIEW_LIST.get(i).entities.get(dimension);
            if (entities != null) {
                entities.forget(entity.getId());
            }
            RemoteEntityView far = VIEW_LIST.get(i).entities.get(elsewhere);
            if (far != null) {
                far.forget(entity.getId());
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

    /**
     * {@code true} si un joueur de la dimension d'un envoi l'a deja recu : de vanilla, a portee du point, ou, exclu par vanilla, de
     * l'envoi a l'auteur d'une action. Un bloc de marge au bord de la portee : mieux vaut un son en double qu'un son perdu.
     */
    private static boolean alreadyServed(ServerPlayerEntity player, double x, double y, double z, double vanillaRange, int excludedId,
            boolean excludedServed) {
        if (player.getId() == excludedId) {
            return excludedServed;
        }
        double reach = Math.max(0.0, vanillaRange - 1.0);
        return player.squaredDistanceTo(x, y, z) < reach * reach;
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
                // Pres d'un cadre, la fenetre s'envoie a chaque tick, par petits paquets : elle est chez le client avant qu'il
                // passe, sans qu'une image en recoive beaucoup d'un coup.
                int views = VIEW_LIST.size();
                for (int i = 0; i < views; i++) {
                    PlayerView view = VIEW_LIST.get(i);
                    if (view.catchingUp && view.updatedAt != server.getTicks()) {
                        update(server, view.player);
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
        // Une vue existante a toujours au moins une dimension inscrite : elle reste a decharger meme sans portail regarde. Un
        // depart en attente en cree une : le monde quitte, garde par le client, sera decharge chez lui le moment venu.
        if (RELEVANT_SCRATCH.isEmpty() && view == null && !hasDeparture(player.getUuid())) {
            return;
        }
        if (view == null) {
            view = new PlayerView(player);
            VIEWS.put(player.getUuid(), view);
            VIEW_LIST.add(view);
        }
        computeWantedWindows(player);
        addNestedWindows(player);
        keepDeparture(server, player, view);
        long tick = server.getTicks();
        view.updatedAt = tick;
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
        boolean near = nearAnyPortal(player);
        int budget = near ? NEAR_CHUNKS_PER_TICK : MAX_CHUNKS_PER_UPDATE;
        boolean pending = false;
        for (int i = 0; i < DIMENSION_SCRATCH.size(); i++) {
            RegistryKey<World> dimension = DIMENSION_SCRATCH.get(i);
            pending |= syncDimension(server, player, view, dimension, WANTED_SCRATCH.get(dimension), budget);
        }
        view.catchingUp = pending && near;
    }

    /** {@code true} si ce joueur a un depart en attente. */
    private static boolean hasDeparture(UUID player) {
        return departureIndex(player) >= 0;
    }

    /** {@code true} si le joueur est pres du cadre d'un des portails releves pour lui. */
    private static boolean nearAnyPortal(ServerPlayerEntity player) {
        for (int i = 0; i < RELEVANT_SCRATCH.size(); i++) {
            if (DestinationLoader.isNear(player, RELEVANT_SCRATCH.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Portails du monde du joueur a sa portee de rendu, devant comme dans son dos, qui menent dans une autre dimension, ou ailleurs dans
     * la sienne, plus loin que ce que vanilla lui envoie autour de lui.
     */
    private static void collectRelevantPortals(ServerPlayerEntity player, ServerWorld world) {
        RELEVANT_SCRATCH.clear();
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.getWorld() != world || !relevant(portal, world.getRegistryKey(), player.getChunkFilter())) {
                continue;
            }
            if (PortalRelevance.isInRange(player, portal.getPos(), PortalRelevance.MAX_DISTANCE)) {
                RELEVANT_SCRATCH.add(portal);
            }
        }
    }

    /**
     * Rayon de la fenetre pres d'un cadre : celui de {@link DestinationLoader#syncRadius} pres du portail, etendu a ce que
     * vanilla envoie au joueur (sa distance de vue, bornee par celle du serveur, et l'anneau qui la borde : les dernieres
     * sections visibles ne se construisent qu'avec leurs chunks voisins) tant que cela tient dans la carte du client
     * autour du foyer.
     *
     * @param playerView distance de vue du joueur, en chunks
     * @param serverView distance de vue du serveur, en chunks
     * @param anchorDistance distance de l'arrivee au foyer de sa dimension, en chunks
     */
    static int nearRadius(int playerView, int serverView, int anchorDistance) {
        return Math.max(DestinationLoader.syncRadius(true), Math.min(Math.min(playerView, serverView) + 1, (RemoteChunkWindow.MAP_RADIUS - anchorDistance) / 2));
    }

    /**
     * {@code true} si un portail visible du monde du joueur a une fenetre a lui envoyer : il mene dans une autre dimension, ou ailleurs
     * dans la sienne, et la fenetre de son arrivee deborde alors de ce que vanilla lui envoie autour de lui.
     *
     * @param world monde du joueur, celui du portail
     * @param vanilla chunks que vanilla envoie au joueur
     */
    static boolean relevant(BackroomsPortalEntity portal, RegistryKey<World> world, ChunkFilter vanilla) {
        if (!portal.visible()) {
            return false;
        }
        Vec3d arrival = portal.destinationPos();
        return !portal.destinationDim().equals(world)
                || beyondVanilla(vanilla, ChunkSectionPos.getSectionCoord(arrival.x), ChunkSectionPos.getSectionCoord(arrival.z), DestinationLoader.syncRadius(false));
    }

    /**
     * {@code true} si une fenetre de ce rayon autour de cette arrivee deborde des chunks que vanilla envoie au joueur et que son client
     * dessine (l'anneau du bord exclu : ses sections attendent leurs voisins). Le filtre est un disque : la fenetre, un carre, y tient
     * si ses quatre coins y tiennent.
     *
     * @param vanilla chunks que vanilla envoie au joueur
     */
    static boolean beyondVanilla(ChunkFilter vanilla, int arrivalChunkX, int arrivalChunkZ, int radius) {
        return !vanilla.isWithinDistanceExcludingEdge(arrivalChunkX - radius, arrivalChunkZ - radius)
                || !vanilla.isWithinDistanceExcludingEdge(arrivalChunkX + radius, arrivalChunkZ - radius)
                || !vanilla.isWithinDistanceExcludingEdge(arrivalChunkX - radius, arrivalChunkZ + radius)
                || !vanilla.isWithinDistanceExcludingEdge(arrivalChunkX + radius, arrivalChunkZ + radius);
    }

    /** La clef de la fenetre d'un portail : sa dimension d'arrivee, ou l'ailleurs du monde du joueur s'il y mene. */
    private static RegistryKey<World> windowKey(ServerPlayerEntity player, BackroomsPortalEntity portal) {
        RegistryKey<World> destination = portal.destinationDim();
        return destination.equals(player.getWorld().getRegistryKey()) ? RemoteKeys.elsewhere(destination) : destination;
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
            RegistryKey<World> key = windowKey(player, portal);
            BackroomsPortalEntity anchor = ANCHOR_SCRATCH.get(key);
            if (anchor == null || player.squaredDistanceTo(portal) < player.squaredDistanceTo(anchor)) {
                ANCHOR_SCRATCH.put(key, portal);
            }
        }
        for (int i = 0; i < RELEVANT_SCRATCH.size(); i++) {
            BackroomsPortalEntity portal = RELEVANT_SCRATCH.get(i);
            // Pres du portail, la traversee est imminente : la fenetre couvre ce que le joueur verra en sortant, jusqu'a sa
            // distance de vue. Ses chunks seront chez lui avant le passage, et vanilla ne les lui renverra pas d'un coup.
            boolean near = DestinationLoader.isNear(player, portal);
            RegistryKey<World> dimension = windowKey(player, portal);
            Vec3d anchorPos = ANCHOR_SCRATCH.get(dimension).destinationPos();
            Vec3d destinationPos = portal.destinationPos();
            int anchorX = ChunkSectionPos.getSectionCoord(anchorPos.x);
            int anchorZ = ChunkSectionPos.getSectionCoord(anchorPos.z);
            int chunkX = ChunkSectionPos.getSectionCoord(destinationPos.x);
            int chunkZ = ChunkSectionPos.getSectionCoord(destinationPos.z);
            // Ailleurs dans le monde du joueur, une traversee le fait repartir de ce que vanilla lui envoie : la fenetre reste celle du cadre.
            int radius = near && !RemoteKeys.isElsewhere(dimension) ? nearRadius(player.getViewDistance(), player.getServer().getPlayerManager().getViewDistance(),
                    Math.max(Math.abs(chunkX - anchorX), Math.abs(chunkZ - anchorZ))) : DestinationLoader.syncRadius(near);
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
     * laissee, comme pour un portail du joueur. Un portail du monde du joueur se voit a travers un autre dans l'ailleurs de ce
     * monde : sa fenetre est rangee sous la clef de l'ailleurs.
     */
    private static void addNestedWindows(ServerPlayerEntity player) {
        RegistryKey<World> own = player.getWorld().getRegistryKey();
        RegistryKey<World> elsewhere = RemoteKeys.elsewhere(own);
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        NESTED_SCRATCH.clear();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            RegistryKey<World> world = portal.getWorld().getRegistryKey();
            if (seenThroughAnother(portal, own, WANTED_SCRATCH.get(world.equals(own) ? elsewhere : world))) {
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

    /**
     * Synchronise une dimension ; {@code true} si des chunks voulus attendent encore leur envoi, faute de budget.
     *
     * @param budget chunks envoyes au plus pour cette dimension
     */
    @SuppressWarnings("try")
    private static boolean syncDimension(
            MinecraftServer server,
            ServerPlayerEntity player,
            PlayerView view,
            RegistryKey<World> dimension,
            LongSet wanted,
            int budget) {
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
        ServerWorld destination = server.getWorld(RemoteKeys.dimension(dimension));
        if (destination == null) {
            return false;
        }
        boolean pending = false;
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_SYNC_CHUNKS)) {
            int left = budget;
            LongIterator wantedKeys = wanted.iterator();
            while (wantedKeys.hasNext()) {
                long key = wantedKeys.nextLong();
                // Chaque chunk voulu reste charge tant qu'il l'est : une fenetre en cone deborde du ticket de l'arrivee.
                DestinationLoader.holdWindowChunk(destination, key);
                if (sent.contains(key)) {
                    continue;
                }
                if (left == 0) {
                    pending = true;
                    continue;
                }
                WorldChunk chunk = destination.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
                if (chunk == null) {
                    // Pas encore charge : le ticket de DestinationLoader s'en occupe, on reessaie au prochain passage.
                    continue;
                }
                index = announceDimension(player, view, dimension, destination);
                PortalNetwork.send(player, RemoteChunkPayload.of(dimension, destination, chunk));
                sent.add(key);
                watchedChunkCount++;
                left--;
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
        return pending;
    }

    /**
     * Annonce la dimension au joueur si ce n'est pas deja fait, et rend son index chez lui : l'ordre des annonces de la vue.
     * Les payloads frequents (entities, blocs, dechargements) portent cet index, un octet, a la place de l'identifiant ;
     * le client le resout par la derniere annonce recue pour cet index, et l'ordre des paquets d'une connexion garantit
     * qu'une annonce precede tout ce qui s'en reclame. Une dimension dont le monde a ete libere ({@link #release}) est
     * annoncee de nouveau, sous le meme index, avant tout nouvel envoi.
     */
    private static int announceDimension(ServerPlayerEntity player, PlayerView view, RegistryKey<World> dimension, ServerWorld destination) {
        int index = view.announcedDimensions.indexOf(dimension);
        if (index >= 0 && !view.releasedDimensions.remove(dimension)) {
            return index;
        }
        if (index < 0) {
            index = view.announcedDimensions.size();
            view.announcedDimensions.add(dimension);
        }
        PortalNetwork.send(player, RemoteDimensionPayload.of(dimension, destination, index));
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
            entities.clear(server.getWorld(RemoteKeys.dimension(dimension)), true);
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
                ServerWorld world = server.getWorld(RemoteKeys.dimension(entry.getKey()));
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
            removeDeparture(playerUuid);
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
            entities.clear(server.getWorld(RemoteKeys.dimension(entities.dimension())), notifyClient);
        }
        if (notifyClient) {
            releaseElsewhere(view);
        }
    }

    /**
     * Un ailleurs ne vaut que pour le monde ou se trouve le joueur : passe dans un autre, plus rien ne le nourrit, et son client le
     * gardait fige jusqu'a la deconnexion, prefere au monde a jour pour un portail qui mene a l'endroit qu'il tenait. Il est libere
     * chez lui, comme une dimension que plus aucun portail ne montre (une seconde liberation n'y fait rien).
     */
    private static void releaseElsewhere(PlayerView view) {
        for (int i = 0; i < view.announcedDimensions.size(); i++) {
            if (RemoteKeys.isElsewhere(view.announcedDimensions.get(i))) {
                PortalNetwork.send(view.player, new RemoteDimensionReleasePayload(i));
            }
        }
    }
}
