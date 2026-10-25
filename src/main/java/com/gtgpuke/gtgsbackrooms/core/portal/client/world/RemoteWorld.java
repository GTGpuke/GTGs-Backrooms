package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionType;

import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.FrameSoundPlayer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.EntityHandover;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.LazyVertexBuffers;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkWindow;
import com.gtgpuke.gtgsbackrooms.mixin.client.CameraAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.ClientPlayNetworkHandlerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.ClientWorldAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Une dimension vue a travers un portail : un {@link ClientWorld} et un {@link WorldRenderer} dedie, dessines
 * depuis une camera transposee par {@link RemoteViewRenderer}.
 *
 * <p>Deux formes. Le monde distant proprement dit est nourri par les payloads du serveur (chunks, blocs, paquets
 * d'entities, sons, evenements et particules) : il tique ses entities pour les interpoler et, tant qu'une vue le montre,
 * ses particules et les effets d'ambiance de ses blocs ; ses sons ressortent par le cadre ou le joueur voit ce monde
 * ({@link FrameSoundPlayer}). Une vue secondaire partage un monde que vanilla entretient deja (celui du joueur, ou un
 * monde distant qui l'a ete) et ne possede que son renderer : elle sert aux portails qui ramenent dans la dimension
 * courante, directement ou imbriques dans un autre portail, et reste liee a son monde quand il devient distant.
 */
public final class RemoteWorld {

    private static final int RENDER_BUFFER_COUNT = 2;
    private static final int SIMULATION_DISTANCE = 2;
    /**
     * Paquets vanilla qu'un monde distant applique avec le gestionnaire du client : ceux de ses entities et ses
     * particules. Les autres concernent le joueur lui-meme ou son monde.
     */
    private static final Set<Class<?>> HANDLED_PACKETS = Set.of(
            EntitySpawnS2CPacket.class,
            ExperienceOrbSpawnS2CPacket.class,
            EntityS2CPacket.MoveRelative.class,
            EntityS2CPacket.Rotate.class,
            EntityS2CPacket.RotateAndMoveRelative.class,
            EntityPositionS2CPacket.class,
            EntityVelocityUpdateS2CPacket.class,
            EntitySetHeadYawS2CPacket.class,
            EntityTrackerUpdateS2CPacket.class,
            EntityAttributesS2CPacket.class,
            EntityEquipmentUpdateS2CPacket.class,
            EntityPassengersSetS2CPacket.class,
            EntityAttachS2CPacket.class,
            EntityStatusEffectS2CPacket.class,
            RemoveEntityStatusEffectS2CPacket.class,
            EntityAnimationS2CPacket.class,
            EntityStatusS2CPacket.class,
            EntityDamageS2CPacket.class,
            ItemPickupAnimationS2CPacket.class,
            ParticleS2CPacket.class);
    /** Les particules ne s'ajoutent que pres de la camera de la vue, comme vanilla pres de celle du joueur. */
    private static final double PARTICLE_RANGE_SQUARED = 32.0 * 32.0;
    /** Une vue dessinee depuis moins longtemps que cela fait vivre ses particules et ses effets d'ambiance. */
    private static final long VIEW_RECENT_TICKS = 40L;
    /** Tirages par tick des effets d'ambiance des blocs (flammes, fumee, gouttes), comme vanilla autour du joueur. */
    private static final int DISPLAY_TICKS = 667;
    private static final int DISPLAY_NEAR_RADIUS = 16;
    private static final int DISPLAY_FAR_RADIUS = 32;

    /** Tampons de dessin des renderers crees par le mod, par renderer : vanilla ne libere jamais ceux d'un renderer. */
    private static final Map<WorldRenderer, BufferBuilderStorage> OWN_BUFFERS = new IdentityHashMap<>();
    /** Tampons d'un renderer libere, repris par le prochain renderer cree. */
    private static final List<BufferBuilderStorage> SPARE_BUFFERS = new ArrayList<>();
    /** Section du moniteur de performance : chargement d'un chunk recu du serveur. */
    private static final String PERF_CHUNK_LOAD = "client.portal.chunk_load";

    private final ClientWorld world;
    private final WorldRenderer renderer;
    private final RemoteViewRenderer viewRenderer;
    /**
     * Particules de ce monde ; celles de l'ancien monde principal le suivent a la traversee. Aucune pour la vue
     * secondaire : elle montre celles du gestionnaire, et n'est jamais nourrie de paquets ; seule son horloge tique.
     */
    private @Nullable RemoteParticles particles;
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    /** Camera de la derniere vue dessinee et heure du monde a ce moment : les particules naissent et vivent autour d'elle. */
    private @Nullable Vec3d lastViewCamera;
    private long lastViewTime = Long.MIN_VALUE;
    private final Random displayRandom = Random.create();
    private final BlockPos.Mutable displayPos = new BlockPos.Mutable();
    /** Camera posee a la camera de la derniere vue : vanilla tire autour d'une camera les eclaboussures de la pluie. */
    private final Camera rainCamera = new Camera();

    private RemoteWorld(ClientWorld world, WorldRenderer renderer, @Nullable RemoteParticles particles) {
        this.world = world;
        this.renderer = renderer;
        this.particles = particles;
        this.viewRenderer = new RemoteViewRenderer(this);
    }

    /** Cree un monde distant vide et son renderer pour une dimension annoncee par le serveur. */
    static RemoteWorld create(
            MinecraftClient client,
            ClientPlayNetworkHandler networkHandler,
            RegistryKey<World> dimension,
            RegistryEntry<DimensionType> dimensionType) {
        WorldRenderer renderer = newRenderer(client);
        ClientWorld world = new ClientWorld(
                networkHandler,
                new ClientWorld.Properties(Difficulty.NORMAL, false, false),
                dimension,
                dimensionType,
                RemoteChunkWindow.LOAD_DISTANCE,
                SIMULATION_DISTANCE,
                client::getProfiler,
                renderer,
                false,
                0L);
        bindWorld(renderer, world);
        return new RemoteWorld(world, renderer, new RemoteParticles(world));
    }

    /**
     * Adopte l'ancien monde principal et son renderer comme monde distant apres une traversee.
     *
     * @param world monde quitte par le joueur
     * @param renderer son renderer, qui garde ses sections construites
     * @param centerChunkX chunk du joueur au moment du passage
     * @param centerChunkZ chunk du joueur au moment du passage
     * @param particles ses particules, rendues par le gestionnaire au moment de la traversee
     * @return monde distant pret a etre rendu a travers le portail de retour
     */
    static RemoteWorld adopt(ClientWorld world, WorldRenderer renderer, int centerChunkX, int centerChunkZ, RemoteParticles particles) {
        RemoteWorld remote = new RemoteWorld(world, renderer, particles);
        remote.centerChunkX = centerChunkX;
        remote.centerChunkZ = centerChunkZ;
        // Meme carte de chunks que les mondes distants crees de zero : les chunks existants sont conserves.
        world.getChunkManager().updateLoadDistance(RemoteChunkWindow.LOAD_DISTANCE);
        world.getChunkManager().setChunkMapCenter(centerChunkX, centerChunkZ);
        return remote;
    }

    /** Vue secondaire d'un monde : un renderer de plus sur ce monde, gardee quand il devient distant. */
    static RemoteWorld mirror(MinecraftClient client, ClientWorld world) {
        WorldRenderer renderer = newRenderer(client);
        bindWorld(renderer, world);
        // La vue secondaire montre les particules du monde principal, celles du gestionnaire : elle n'en a pas a elle.
        return new RemoteWorld(world, renderer, null);
    }

    /**
     * Renderer d'une vue. Ses tampons de sommets, ceux du ciel comme ceux de sa grille de sections, ne reservent leurs
     * objets GL qu'au premier usage ({@link LazyVertexBuffers}) : la grille, a la distance de rendu du joueur, compte des
     * milliers de sections dont une vue n'en construit qu'une petite part.
     */
    private static WorldRenderer newRenderer(MinecraftClient client) {
        BufferBuilderStorage buffers = SPARE_BUFFERS.isEmpty()
                ? new BufferBuilderStorage(RENDER_BUFFER_COUNT)
                : SPARE_BUFFERS.remove(SPARE_BUFFERS.size() - 1);
        LazyVertexBuffers.setActive(true);
        try {
            WorldRenderer renderer = new WorldRenderer(client, client.getEntityRenderDispatcher(), client.getBlockEntityRenderDispatcher(), buffers);
            OWN_BUFFERS.put(renderer, buffers);
            return renderer;
        } finally {
            LazyVertexBuffers.setActive(false);
        }
    }

    /**
     * Recharge le renderer de la vue, comme vanilla celui du joueur : grille et sections refaites au format de sommets, aux
     * couches et aux shaders de terrain du moment. Tampons GL differes, comme a sa creation (voir {@link #newRenderer}).
     */
    void reloadRenderer() {
        LazyVertexBuffers.setActive(true);
        try {
            this.renderer.reload();
        } finally {
            LazyVertexBuffers.setActive(false);
        }
    }

    /** Lie un renderer de vue a son monde : sa grille de sections se cree, tampons GL differes (voir {@link #newRenderer}). */
    private static void bindWorld(WorldRenderer renderer, ClientWorld world) {
        LazyVertexBuffers.setActive(true);
        try {
            renderer.setWorld(world);
        } finally {
            LazyVertexBuffers.setActive(false);
        }
    }

    /**
     * Libere un renderer : son chunk builder s'arrete et, s'il a ete cree ici, ses tampons de dessin serviront au prochain.
     * Vanilla garde les siens toute la partie et ne les rend jamais : alloues hors du tas Java (une trentaine de Mio),
     * ceux d'un renderer jete fuiraient a chaque changement de monde. Les taches de construction encore en cours rendent
     * leurs tampons sur le fil de rendu, le seul qui touche a la reserve : la reprendre aussitot est sur.
     */
    static void release(WorldRenderer renderer) {
        renderer.setWorld(null);
        renderer.close();
        BufferBuilderStorage buffers = OWN_BUFFERS.remove(renderer);
        if (buffers != null) {
            SPARE_BUFFERS.add(buffers);
        }
    }

    /** Monde client de cette dimension : le monde distant, ou le monde que la vue secondaire partage. */
    public ClientWorld world() {
        return this.world;
    }

    /** Renderer dedie a ce monde. */
    public WorldRenderer renderer() {
        return this.renderer;
    }

    /** Dessin de ce monde depuis une camera transposee. */
    public RemoteViewRenderer viewRenderer() {
        return this.viewRenderer;
    }

    /**
     * Particules de ce monde ; {@code null} pour une vue secondaire, qui n'entre jamais dans la liste des mondes distants
     * (ni tiquee ni nourrie de paquets) : les methodes qui les touchent ne servent qu'aux mondes distants.
     */
    @Nullable RemoteParticles particles() {
        return this.particles;
    }

    /**
     * Rend les particules de ce monde, devenu principal a une traversee : elles partent avec l'ancien monde principal
     * ({@link RemoteWorldRegistry#swapMainWorld}), et ce monde ne les garde pas, sans quoi l'ancien resterait retenu par
     * elles une fois libere.
     */
    @Nullable RemoteParticles takeParticles() {
        RemoteParticles taken = this.particles;
        this.particles = null;
        return taken;
    }

    /**
     * Vide ce monde avant qu'il ne soit jete : chaque chunk charge est decharge comme a un dechargement du serveur (block
     * entities et lumiere), la lumiere aussitot mise a jour (le monde ne tique plus), ses entities et ses particules
     * retirees. Des objets hors du mod peuvent garder une reference au monde (le contexte de rendu que Fabric API ajoute
     * au renderer vanilla, l'etat de chargement vanilla du dernier changement de dimension) : ils ne gardent alors qu'un
     * monde vide. A appeler avant de detacher le renderer, que les dechargements previennent.
     */
    void clear() {
        if (this.centerChunkX != Integer.MIN_VALUE) {
            ClientChunkManager chunks = this.world.getChunkManager();
            for (int x = this.centerChunkX - RemoteChunkWindow.MAP_RADIUS; x <= this.centerChunkX + RemoteChunkWindow.MAP_RADIUS; x++) {
                for (int z = this.centerChunkZ - RemoteChunkWindow.MAP_RADIUS; z <= this.centerChunkZ + RemoteChunkWindow.MAP_RADIUS; z++) {
                    if (chunks.getWorldChunk(x, z) != null) {
                        unloadChunk(new ChunkPos(x, z));
                    }
                }
            }
        }
        this.world.getLightingProvider().doLightUpdates();
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : this.world.getEntities()) {
            entities.add(entity);
        }
        for (int i = 0; i < entities.size(); i++) {
            this.world.removeEntity(entities.get(i).getId(), Entity.RemovalReason.DISCARDED);
        }
        if (this.particles != null) {
            this.particles.clear();
        }
    }

    /** Une vue de ce monde vient d'etre dessinee depuis cette camera : particules et effets d'ambiance vivent autour d'elle. */
    void markViewed(Vec3d cameraPos) {
        this.lastViewCamera = cameraPos;
        this.lastViewTime = this.world.getTime();
    }

    /** {@code true} si une vue de ce monde a ete dessinee il y a peu. */
    private boolean recentlyViewed() {
        return this.lastViewCamera != null && this.world.getTime() - this.lastViewTime <= VIEW_RECENT_TICKS;
    }

    /**
     * Ajoute une particule de ce monde, avec les memes regles que vanilla autour du joueur (portee, reglage des
     * particules), autour de la camera de la derniere vue ; un monde que personne ne regarde n'en cree pas.
     */
    public void spawnParticle(
            MinecraftClient client,
            ParticleEffect effect,
            boolean alwaysSpawn,
            boolean important,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        if (!recentlyViewed()) {
            return;
        }
        if (!alwaysSpawn && (this.lastViewCamera.squaredDistanceTo(x, y, z) > PARTICLE_RANGE_SQUARED
                || particlesMode(client, important) == ParticlesMode.MINIMAL)) {
            return;
        }
        RemoteParticles previous = this.particles.enter(client.particleManager);
        try {
            client.particleManager.addParticle(effect, x, y, z, velocityX, velocityY, velocityZ);
        } finally {
            this.particles.exit(client.particleManager, previous);
        }
    }

    /** Reglage des particules, avec le tirage de vanilla : en reduit, une sur trois tombe ; en minimal, les importantes passent parfois. */
    private ParticlesMode particlesMode(MinecraftClient client, boolean canSpawnOnMinimal) {
        ParticlesMode mode = client.options.getParticles().getValue();
        if (canSpawnOnMinimal && mode == ParticlesMode.MINIMAL && this.world.random.nextInt(10) == 0) {
            mode = ParticlesMode.DECREASED;
        }
        if (mode == ParticlesMode.DECREASED && this.world.random.nextInt(3) == 0) {
            mode = ParticlesMode.MINIMAL;
        }
        return mode;
    }

    /** Dessine les particules de ce monde depuis la camera d'une vue, dans le contenu du gestionnaire. */
    void renderParticles(MinecraftClient client, LightmapTextureManager lightmap, Camera camera, float tickDelta) {
        if (this.particles == null) {
            client.particleManager.renderParticles(lightmap, camera, tickDelta);
            return;
        }
        RemoteParticles previous = this.particles.enter(client.particleManager);
        try {
            client.particleManager.renderParticles(lightmap, camera, tickDelta);
        } finally {
            this.particles.exit(client.particleManager, previous);
        }
    }

    /** Charge (ou remplace) un chunk recu du serveur, avec sa lumiere, et programme son rendu. */
    @SuppressWarnings("try")
    public void loadChunk(int chunkX, int chunkZ, ChunkData chunkData, LightData lightData) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_CHUNK_LOAD)) {
            loadChunkMeasured(chunkX, chunkZ, chunkData, lightData);
        }
    }

    private void loadChunkMeasured(int chunkX, int chunkZ, ChunkData chunkData, LightData lightData) {
        recenterIfNeeded(chunkX, chunkZ);
        ClientChunkManager chunks = this.world.getChunkManager();
        WorldChunk chunk = chunks.loadChunkFromPacket(
                chunkX, chunkZ, chunkData.getSectionsDataBuf(), chunkData.getHeightmap(), chunkData.getBlockEntities(chunkX, chunkZ));
        if (chunk == null) {
            return;
        }
        // La lumiere passe par le gestionnaire de paquets, comme celle d'un chunk vanilla : un mod qui suit l'arrivee des
        // chunks par la (un mod de rendu ne construit une section qu'apres sa lumiere) voit les chunks distants comme les
        // autres. Le gestionnaire lit son monde : le temps de l'appel, c'est celui-ci.
        ClientPlayNetworkHandler handler = ((ClientWorldAccessor) this.world).backrooms$getNetworkHandler();
        ClientPlayNetworkHandlerAccessor handlerAccessor = (ClientPlayNetworkHandlerAccessor) handler;
        ClientWorld handlerWorld = handler.getWorld();
        handlerAccessor.backrooms$setWorld(this.world);
        try {
            handlerAccessor.backrooms$readLightData(chunkX, chunkZ, lightData);
        } finally {
            handlerAccessor.backrooms$setWorld(handlerWorld);
        }
        LightingProvider lighting = chunks.getLightingProvider();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int index = 0; index < sections.length; index++) {
            int sectionY = this.world.sectionIndexToCoord(index);
            lighting.setSectionStatus(ChunkSectionPos.from(chunkX, sectionY, chunkZ), sections[index].isEmpty());
            this.world.scheduleBlockRenders(chunkX, sectionY, chunkZ);
        }
    }

    /** Oublie un chunk que le serveur ne synchronise plus. */
    public void unloadChunk(ChunkPos pos) {
        ClientChunkManager chunks = this.world.getChunkManager();
        WorldChunk chunk = chunks.getWorldChunk(pos.x, pos.z);
        if (chunk != null) {
            // Comme le gestionnaire de paquets vanilla : block entities et entities de la colonne cessent de ticker.
            this.world.unloadBlockEntities(chunk);
        }
        chunks.unload(pos);
        LightingProvider lighting = this.world.getLightingProvider();
        lighting.setColumnEnabled(pos, false);
        for (int sectionY = lighting.getBottomY(); sectionY < lighting.getTopY(); sectionY++) {
            ChunkSectionPos sectionPos = ChunkSectionPos.from(pos, sectionY);
            lighting.enqueueSectionData(LightType.BLOCK, sectionPos, null);
            lighting.enqueueSectionData(LightType.SKY, sectionPos, null);
        }
        for (int sectionY = this.world.getBottomSectionCoord(); sectionY < this.world.getTopSectionCoord(); sectionY++) {
            lighting.setSectionStatus(ChunkSectionPos.from(pos, sectionY), true);
            this.world.scheduleBlockRenders(pos.x, sectionY, pos.z);
        }
    }

    /** Applique un changement de bloc relaye par le serveur. */
    public void updateBlock(BlockPos pos, BlockState state) {
        this.world.handleBlockUpdate(pos, state, Block.NOTIFY_ALL | Block.FORCE_STATE);
    }

    /**
     * Applique un paquet d'entity vanilla a ce monde, avec le gestionnaire de paquets rebranche dessus le
     * temps du traitement : apparition, deplacement, donnees, equipement, passagers, effets, animations.
     */
    public void applyEntityPacket(MinecraftClient client, Packet<? super ClientPlayPacketListener> packet) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return;
        }
        if (packet instanceof EntitiesDestroyS2CPacket destroy) {
            // Une entity qui franchit un portail reste affichee jusqu'a l'arrivee de sa copie de l'autre cote.
            EntityHandover.applyDestroys(this.world, destroy.getEntityIds());
            return;
        }
        if (packet instanceof PlaySoundS2CPacket sound) {
            // Vanilla jouerait le son dans le monde du joueur : il passe par le portail qui le rapproche de lui.
            FrameSoundPlayer.redirect(this.world, sound.getX(), sound.getY(), sound.getZ(), sound.getSound().value(), sound.getCategory(),
                    sound.getVolume(), sound.getPitch(), false, sound.getSeed());
            return;
        }
        if (packet instanceof PlaySoundFromEntityS2CPacket sound) {
            Entity source = this.world.getEntityById(sound.getEntityId());
            if (source != null) {
                FrameSoundPlayer.redirect(this.world, source.getX(), source.getY(), source.getZ(), sound.getSound().value(), sound.getCategory(),
                        sound.getVolume(), sound.getPitch(), false, sound.getSeed());
            }
            return;
        }
        if (packet instanceof BlockBreakingProgressS2CPacket breaking) {
            // Fissures d'un bloc mine de l'autre cote, par quiconque : vanilla les poserait sur le renderer du joueur.
            this.world.setBlockBreakingInfo(breaking.getEntityId(), breaking.getPos(), breaking.getProgress());
            return;
        }
        if (packet instanceof BlockEntityUpdateS2CPacket update) {
            // Vanilla l'appliquerait a client.world : c'est le bloc de ce monde-ci qui change.
            BlockEntity blockEntity = this.world.getBlockEntity(update.getPos());
            if (blockEntity != null && blockEntity.getType() == update.getBlockEntityType() && !update.getNbt().isEmpty()) {
                blockEntity.read(update.getNbt(), handler.getRegistryManager());
            }
            return;
        }
        if (packet instanceof GameStateChangeS2CPacket weather) {
            // Meteo de la dimension distante : le serveur n'envoie par cette voie que la pluie et l'orage.
            if (weather.getReason() == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED) {
                this.world.setRainGradient(weather.getValue());
            } else if (weather.getReason() == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {
                this.world.setThunderGradient(weather.getValue());
            }
            return;
        }
        RemoteParticles previous = this.particles.enter(client.particleManager);
        try {
            if (packet instanceof WorldEventS2CPacket event) {
                // Bloc casse, porte, feu d'artifice : sons et particules de l'evenement, dans ce monde.
                if (!event.isGlobal()) {
                    this.world.syncWorldEvent(event.getEventId(), event.getPos(), event.getData());
                }
            } else if (HANDLED_PACKETS.contains(packet.getClass())) {
                applyWithHandler(handler, packet);
            }
        } finally {
            this.particles.exit(client.particleManager, previous);
        }
    }

    private void applyWithHandler(ClientPlayNetworkHandler handler, Packet<? super ClientPlayPacketListener> packet) {
        ClientPlayNetworkHandlerAccessor accessor = (ClientPlayNetworkHandlerAccessor) handler;
        ClientWorld bound = handler.getWorld();
        accessor.backrooms$setWorld(this.world);
        try {
            packet.apply(handler);
        } finally {
            accessor.backrooms$setWorld(bound);
        }
    }

    /**
     * L'heure est celle du serveur, commune a toutes ses dimensions : le monde distant suit le monde principal. La meteo,
     * propre a chaque dimension, arrive du serveur avec les paquets de ce monde ({@link #applyEntityPacket}).
     */
    public void syncEnvironment(ClientWorld main) {
        this.world.setTime(main.getTime());
        this.world.setTimeOfDay(main.getTimeOfDay());
    }

    /**
     * Tick client : interpolation et animations des entities et block entities recues du serveur, puis, si une vue de
     * ce monde a ete dessinee il y a peu, vie de ses particules et effets d'ambiance de ses blocs autour de la camera
     * de cette vue (flammes, fumee, gouttes, sons du feu). Tout se passe dans le contenu de particules de ce monde.
     */
    public void tick(MinecraftClient client) {
        tickClock(client.worldRenderer);
        RemoteParticles previous = this.particles.enter(client.particleManager);
        try {
            this.world.tickEntities();
            if (recentlyViewed()) {
                client.particleManager.tick();
                displayTicks();
                tickRain(client);
            }
        } finally {
            this.particles.exit(client.particleManager, previous);
        }
        if (!recentlyViewed()) {
            // Personne ne regarde plus : les particules restantes ne seraient jamais dessinees.
            this.particles.clear();
        }
    }

    /**
     * Horloge du renderer de la vue, qui anime la pluie et la neige et oublie les vieilles fissures, comme celle du renderer
     * du joueur : vanilla ne tique que celui-la. Seule a tiquer pour la vue secondaire. Elle reste a l'heure de celle du
     * renderer du joueur, deja tique a ce moment : les nuages, places selon elle, ne sautent pas quand ce renderer devient
     * celui du joueur a la traversee.
     *
     * @param main renderer du joueur
     */
    void tickClock(WorldRenderer main) {
        this.renderer.tick();
        ((WorldRendererAccessor) this.renderer).backrooms$setTicks(((WorldRendererAccessor) main).backrooms$getTicks());
    }

    /**
     * Eclaboussures et bruit de la pluie autour de la camera de la derniere vue, tires par vanilla comme autour du joueur :
     * les particules naissent dans ce monde, le son ressort par le portail.
     */
    private void tickRain(MinecraftClient client) {
        ((CameraAccessor) this.rainCamera).backrooms$setPos(this.lastViewCamera);
        ClientWorld main = client.world;
        client.world = this.world;
        try {
            this.renderer.tickRainSplashing(this.rainCamera);
        } finally {
            client.world = main;
        }
    }

    /** Effets d'ambiance des blocs autour de la camera de la derniere vue, tires comme vanilla autour du joueur. */
    private void displayTicks() {
        int x = MathHelper.floor(this.lastViewCamera.x);
        int y = MathHelper.floor(this.lastViewCamera.y);
        int z = MathHelper.floor(this.lastViewCamera.z);
        for (int i = 0; i < DISPLAY_TICKS; i++) {
            this.world.randomBlockDisplayTick(x, y, z, DISPLAY_NEAR_RADIUS, this.displayRandom, null, this.displayPos);
            this.world.randomBlockDisplayTick(x, y, z, DISPLAY_FAR_RADIUS, this.displayRandom, null, this.displayPos);
        }
    }

    /** Reconstruction de section demandee au renderer de ce monde, principal ou distant, repercutee sur la vue secondaire. */
    void scheduleChunkRender(int x, int y, int z, boolean important) {
        ((WorldRendererAccessor) this.renderer).backrooms$scheduleChunkRender(x, y, z, important);
    }

    /** Libere le renderer, son chunk builder et ses tampons de dessin (voir {@link #release}). */
    void dispose() {
        release(this.renderer);
    }

    /** Resume pour les logs de debug. */
    public String debugString() {
        return this.world.getChunkManager().getLoadedChunkCount() + " chunk(s) charge(s), "
                + this.world.getRegularEntityCount() + " entity(ies), renderer "
                + this.renderer.getChunksDebugString() + ", builder " + this.renderer.getChunkBuilder().getDebugString();
    }

    /** La carte de chunks du client est carree et centree : on la recentre sur le premier chunk hors de portee. */
    private void recenterIfNeeded(int chunkX, int chunkZ) {
        if (this.centerChunkX != Integer.MIN_VALUE
                && Math.abs(chunkX - this.centerChunkX) <= RemoteChunkWindow.MAP_RADIUS
                && Math.abs(chunkZ - this.centerChunkZ) <= RemoteChunkWindow.MAP_RADIUS) {
            return;
        }
        this.centerChunkX = chunkX;
        this.centerChunkZ = chunkZ;
        this.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ);
        this.renderer.scheduleTerrainUpdate();
    }

}
