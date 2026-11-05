package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.mixin.EntityTrackerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.ServerChunkLoadingManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.WorldAccessor;

/**
 * Entities d'une dimension distante suivies pour un joueur.
 *
 * <p>Pour chaque entity de la fenetre, le joueur est ajoute aux auditeurs du suivi vanilla de cette
 * entity : il recoit alors les memes paquets que les joueurs presents dans cette dimension, enveloppes
 * avec la dimension par {@link #sendPacket}. Quand l'entity quitte la fenetre ou disparait, il en est
 * retire et son client la supprime.
 */
final class RemoteEntityView implements PlayerAssociatedNetworkHandler {

    private static final TypeFilter<Entity, Entity> ENTITY_FILTER = TypeFilter.instanceOf(Entity.class);

    private final ServerPlayerEntity viewer;
    private final RegistryKey<World> dimension;
    /** Index de la dimension chez ce joueur, celui de son annonce : un octet dans chaque paquet, au lieu de l'identifiant. */
    private final int dimensionIndex;
    private final IntOpenHashSet tracked = new IntOpenHashSet();
    private final IntOpenHashSet seen = new IntOpenHashSet();
    private final List<Entity> scratch = new ArrayList<>();
    /** Envoi enveloppe, cree une fois : le suivi vanilla le recoit pour chaque entity nouvellement suivie. */
    private final Consumer<Packet<ClientPlayPacketListener>> sender = this::sendPacket;
    /** Comme le suivi vanilla : un joueur spectateur n'est montre qu'aux spectateurs. Cree une fois. */
    private final Predicate<Entity> shown = entity -> entity != getPlayer() && entity.canBeSpectated(getPlayer());
    /** Boite des chunks synchronises, reconstruite seulement quand leurs bornes changent. */
    private @Nullable Box window;
    private int windowMinChunkX;
    private int windowMinChunkZ;
    private int windowMaxChunkX;
    private int windowMaxChunkZ;
    /** Pluie et orage deja envoyes pour cette dimension ; {@code NaN} avant le premier envoi. */
    private float sentRain = Float.NaN;
    private float sentThunder = Float.NaN;

    RemoteEntityView(ServerPlayerEntity viewer, RegistryKey<World> dimension, int dimensionIndex) {
        this.viewer = viewer;
        this.dimension = dimension;
        this.dimensionIndex = dimensionIndex;
    }

    RegistryKey<World> dimension() {
        return this.dimension;
    }

    @Override
    public ServerPlayerEntity getPlayer() {
        return this.viewer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void sendPacket(Packet<?> packet) {
        PortalNetwork.send(this.viewer, new RemoteEntityPacketPayload(this.dimensionIndex, (Packet<? super ClientPlayPacketListener>) packet));
    }

    /**
     * Aligne le suivi sur les entities presentes dans la fenetre.
     *
     * @param destination monde distant
     * @param chunks chunks envoyes au joueur, dont la boite englobante delimite la fenetre
     */
    void update(ServerWorld destination, LongSet chunks) {
        Box window = windowBox(destination.getBottomY(), destination.getTopY(), chunks);
        Int2ObjectMap<?> trackers = trackers(destination);
        this.scratch.clear();
        this.seen.clear();
        destination.collectEntitiesByType(ENTITY_FILTER, window, this.shown, this.scratch);
        for (int i = 0; i < this.scratch.size(); i++) {
            Entity entity = this.scratch.get(i);
            this.seen.add(entity.getId());
            startTracking(trackers, entity);
        }
        IntIterator iterator = this.tracked.iterator();
        while (iterator.hasNext()) {
            int id = iterator.nextInt();
            if (!this.seen.contains(id)) {
                iterator.remove();
                untrack(trackers, id, true);
            }
        }
        this.scratch.clear();
    }

    /**
     * Envoie la meteo de la dimension distante quand elle change : chaque dimension a la sienne cote serveur (seules celles
     * qui ont un ciel la font evoluer), et la vue doit montrer la pluie de l'autre cote, pas celle du monde du joueur.
     *
     * @param destination monde distant
     */
    void syncWeather(ServerWorld destination) {
        WorldAccessor weather = (WorldAccessor) destination;
        float rain = weather.backrooms$getRainGradient();
        if (rain != this.sentRain) {
            this.sentRain = rain;
            sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, rain));
        }
        float thunder = weather.backrooms$getThunderGradient();
        if (thunder != this.sentThunder) {
            this.sentThunder = thunder;
            sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED, thunder));
        }
    }

    /**
     * Une entity vient d'arriver dans la fenetre par un portail : suivie tout de suite, sans attendre le prochain
     * alignement, pour que sa copie parte au meme tick que la destruction de l'ancienne.
     *
     * @param destination monde distant ou elle est arrivee
     * @param entity entity arrivee
     */
    void track(ServerWorld destination, Entity entity) {
        if (entity.canBeSpectated(this.viewer)) {
            startTracking(trackers(destination), entity);
        }
    }

    /** Suit l'entity si elle ne l'est pas deja et qu'un suivi vanilla existe : ses paquets d'apparition partent aussitot. */
    private void startTracking(Int2ObjectMap<?> trackers, Entity entity) {
        int id = entity.getId();
        if (this.tracked.contains(id) || !(trackers.get(id) instanceof EntityTrackerAccessor tracker)) {
            return;
        }
        tracker.backrooms$getListeners().add(this);
        tracker.backrooms$getEntry().sendPackets(this.viewer, this.sender);
        this.tracked.add(id);
    }

    /**
     * Boite englobant les chunks synchronises, sur toute la hauteur du monde ; la meme instance tant que les bornes
     * tiennent. Visible du paquet pour les tests.
     */
    Box windowBox(int bottomY, int topY, LongSet chunks) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        LongIterator iterator = chunks.iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            int x = ChunkPos.getPackedX(key);
            int z = ChunkPos.getPackedZ(key);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        Box current = this.window;
        if (current == null || minX != this.windowMinChunkX || minZ != this.windowMinChunkZ
                || maxX != this.windowMaxChunkX || maxZ != this.windowMaxChunkZ) {
            current = new Box(minX * 16.0, bottomY, minZ * 16.0, (maxX + 1) * 16.0, topY, (maxZ + 1) * 16.0);
            this.window = current;
            this.windowMinChunkX = minX;
            this.windowMinChunkZ = minZ;
            this.windowMaxChunkX = maxX;
            this.windowMaxChunkZ = maxZ;
        }
        return current;
    }

    /**
     * La vue est oubliee : ses ecouteurs quittent les trackers de la dimension, et le client efface ses copies si demande.
     *
     * @param destination monde distant ; il existe tant que le serveur tourne
     * @param notifyClient {@code false} quand le client repart de zero (changement de monde, deconnexion)
     */
    void clear(ServerWorld destination, boolean notifyClient) {
        Int2ObjectMap<?> trackers = trackers(destination);
        IntIterator iterator = this.tracked.iterator();
        while (iterator.hasNext()) {
            untrack(trackers, iterator.nextInt(), notifyClient);
        }
        this.tracked.clear();
    }

    /** L'entity a quitte son monde : son suivi vanilla disparait avec elle, le client efface sa copie. */
    void forget(int id) {
        if (this.tracked.remove(id)) {
            sendPacket(new EntitiesDestroyS2CPacket(id));
        }
    }

    private void untrack(Int2ObjectMap<?> trackers, int id, boolean notifyClient) {
        // Un identifiant suivi a toujours son suivi vanilla : une entity qui le perd quitte son monde, et la vue l'a deja
        // oubliee (PortalViewTracker.onEntityUnloaded).
        ((EntityTrackerAccessor) trackers.get(id)).backrooms$getListeners().remove(this);
        if (notifyClient) {
            sendPacket(new EntitiesDestroyS2CPacket(id));
        }
    }

    /** Suivis vanilla des entities d'un monde, par identifiant. */
    static Int2ObjectMap<?> trackers(ServerWorld world) {
        return ((ServerChunkLoadingManagerAccessor) world.getChunkManager().chunkLoadingManager).backrooms$getEntityTrackers();
    }
}
